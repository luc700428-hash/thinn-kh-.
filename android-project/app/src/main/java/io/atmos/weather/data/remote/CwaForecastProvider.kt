package io.atmos.weather.data.remote

import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.shredzone.commons.suncalc.SunTimes
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Primary source for places in Taiwan: the Central Weather Administration open-data platform.
 *
 *  - township forecast, 3 days (F-D0047-xxx): hourly temperature, humidity, apparent
 *    temperature, wind, 3-hour rain probability and the CWA weather phenomenon;
 *  - township forecast, 1 week (F-D0047-xxx+2): today's high and low and the UV index;
 *  - the nearest automatic weather station (O-A0001-001) for what is happening right now;
 *  - air quality stays with Open-Meteo, which CWA does not publish.
 *
 * Anything outside Taiwan, a missing authorization key or any CWA failure throws, and the
 * repository moves on to Open-Meteo and then MET Norway. The authorization key is never put
 * into an error message (they can reach the screen).
 */
class CwaForecastProvider(
    private val http:OkHttpClient,
    private val key:()->String?,
    private val observations:CwaSurfaceObservations?=null,
    private val airQuality:OpenMeteoAirQualityApi?=null,
    private val clock:Clock=Clock.systemUTC()
):WeatherProvider {
    override val id="cwa"
    private val zone=ZoneId.of("Asia/Taipei")

    override suspend fun get(city:WeatherCity):WeatherSnapshot {
        val authorization=key()?.trim().orEmpty()
        check(authorization.isNotEmpty()){"未設定中央氣象署授權碼"}
        val town=requireNotNull(CwaTownships.resolve(city.name,city.coordinate.latitude,city.coordinate.longitude)){"不在中央氣象署鄉鎮預報範圍"}
        val weekId=town.dataset.substringAfterLast('-').toInt().plus(2).toString().padStart(3,'0')
        return coroutineScope {
            val shortRange=async(Dispatchers.IO){fetch(town.dataset,town.name,authorization)}
            val week=async(Dispatchers.IO){
                try{fetch("F-D0047-$weekId",town.name,authorization,"最高溫度,最低溫度,紫外線指數,12小時降雨機率,天氣現象")}
                catch(cancel:CancellationException){throw cancel}catch(_:Exception){null}
            }
            val station=async{
                try{observations?.nearestStation(city,clock.instant())}
                catch(cancel:CancellationException){throw cancel}catch(_:Exception){null}
            }
            val air=async{
                try{airQuality?.currentAir(city.coordinate.latitude,city.coordinate.longitude)?.current}
                catch(cancel:CancellationException){throw cancel}catch(_:Exception){null}
            }
            parse(shortRange.await(),week.await(),city,station.await(),clock.instant()).let{s->
                air.await()?.let{a->s.copy(usAqi=a.usAqi,pm25=a.pm25,pm10=a.pm10,sulfurDioxide=a.sulfurDioxide,
                    nitrogenDioxide=a.nitrogenDioxide,ozone=a.ozone,carbonMonoxide=a.carbonMonoxide,
                    aerosolOpticalDepth=a.aerosolOpticalDepth,airborneDust=a.dust)}?:s
            }
        }
    }

    private suspend fun fetch(dataset:String,town:String,authorization:String,elements:String?=null):JSONObject=withContext(Dispatchers.IO) {
        val url="https://opendata.cwa.gov.tw/api/v1/rest/datastore/$dataset".toHttpUrl().newBuilder()
            .addQueryParameter("Authorization",authorization).addQueryParameter("format","JSON")
            .addQueryParameter("LocationName",town)
            .apply{if(elements!=null)addQueryParameter("ElementName",elements)}.build()
        val body=try {
            http.newCall(Request.Builder().url(url).header("Accept","application/json").build()).execute().use {r->
                check(r.code!=401&&r.code!=403){"中央氣象署授權碼無效或已停用"}
                check(r.isSuccessful){"中央氣象署 HTTP ${r.code}"}
                requireNotNull(r.body).string()
            }
        } catch(cancel:CancellationException){throw cancel}
          catch(error:java.io.IOException){throw java.io.IOException("中央氣象署連線失敗（${error.javaClass.simpleName}）")}
        val root=JSONObject(body)
        check(root.optString("success")=="true"){"中央氣象署回覆失敗"}
        root
    }

    internal fun parse(shortRange:JSONObject,week:JSONObject?,city:WeatherCity,observed:SurfaceObservation?,now:Instant):WeatherSnapshot {
        val elements=elementsOf(shortRange)
        fun points(name:String,field:String):List<Pair<Instant,String>> =
            elements[name]?.let{arr->(0 until arr.length()).mapNotNull{i->
                val t=arr.getJSONObject(i)
                val at=time(t.optString("DataTime").ifBlank{t.optString("StartTime")})?:return@mapNotNull null
                val v=t.optJSONArray("ElementValue")?.optJSONObject(0)?.optString(field)?.takeIf{it.isNotBlank()&&it!="-"}?:return@mapNotNull null
                at to v
            }}.orEmpty()
        data class Span(val start:Instant,val end:Instant,val value:JSONObject)
        fun spans(source:Map<String,JSONArray>,name:String):List<Span> =
            source[name]?.let{arr->(0 until arr.length()).mapNotNull{i->
                val t=arr.getJSONObject(i)
                val s=time(t.optString("StartTime"))?:return@mapNotNull null
                val e=time(t.optString("EndTime"))?:return@mapNotNull null
                Span(s,e,t.optJSONArray("ElementValue")?.optJSONObject(0)?:return@mapNotNull null)
            }}.orEmpty()
        fun <T> at(list:List<Pair<Instant,T>>,t:Instant)=list.lastOrNull{!it.first.isAfter(t)}?.second?:list.firstOrNull()?.second
        fun covering(list:List<Span>,t:Instant)=list.firstOrNull{!t.isBefore(it.start)&&t.isBefore(it.end)}?:list.lastOrNull{!it.start.isAfter(t)}?:list.firstOrNull()

        val temps=points("溫度","Temperature").mapNotNull{(t,v)->v.toDoubleOrNull()?.let{t to it}}
        require(temps.isNotEmpty()){"中央氣象署未提供溫度"}
        val humid=points("相對濕度","RelativeHumidity").mapNotNull{(t,v)->v.toIntOrNull()?.let{t to it}}
        val feels=points("體感溫度","ApparentTemperature").mapNotNull{(t,v)->v.toDoubleOrNull()?.let{t to it}}
        val wind=points("風速","WindSpeed").mapNotNull{(t,v)->v.toDoubleOrNull()?.let{t to it}}
        val direction=points("風向","WindDirection")
        val rain=spans(elements,"3小時降雨機率")
        val sky=spans(elements,"天氣現象")
        val forecastHour=temps.lastOrNull{!it.first.isAfter(now)}?.first?:temps.first().first
        val currentSky=covering(sky,now)?.value
        val forecastText=currentSky?.optString("Weather")
        val code=observed?.description?.let{cwaTextToWmo(it)}?:cwaTextToWmo(forecastText)
        val temperature=observed?.temperature?:requireNotNull(at(temps,now))
        val humidity=observed?.humidity?:at(humid,now)
        val windMs=observed?.windMs?:at(wind,now)
        val hourly=temps.filter{!it.first.isBefore(forecastHour)}.take(48).map{(t,v)->
            HourlyWeather(at=t,temperatureC=v,
                precipitationProbabilityPct=covering(rain,t)?.value?.optString("ProbabilityOfPrecipitation")?.toIntOrNull(),
                uvIndex=null,humidityPct=at(humid,t),windKmh=at(wind,t)?.times(3.6),
                weatherCode=cwaTextToWmo(covering(sky,t)?.value?.optString("Weather")),feelsLikeC=at(feels,t))
        }
        val weekly=week?.let{elementsOf(it)}.orEmpty()
        val today=now.atZone(zone).toLocalDate()
        fun todays(name:String,field:String)=spans(weekly,name).filter{it.start.atZone(zone).toLocalDate()==today}
            .mapNotNull{it.value.optString(field).toDoubleOrNull()}
        val highs=todays("最高溫度","MaxTemperature");val lows=todays("最低溫度","MinTemperature")
        val dayTemps=temps.filter{it.first.atZone(zone).toLocalDate()==today}.map{it.second}
        val uv=covering(spans(weekly,"紫外線指數"),now)?.value?.optString("UVIndex")?.toDoubleOrNull()
        // 1-week township forecast: 12-hour periods folded into calendar days.
        val weekHigh=spans(weekly,"最高溫度");val weekLow=spans(weekly,"最低溫度")
        val weekRain=spans(weekly,"12小時降雨機率");val weekSky=spans(weekly,"天氣現象")
        val days=(weekHigh.map{it.start}+weekLow.map{it.start}).map{it.atZone(zone).toLocalDate()}.distinct().sorted()
        val daily=days.map{d->
            fun on(list:List<Span>)=list.filter{it.start.atZone(zone).toLocalDate()==d}
            val dayTime=on(weekSky).firstOrNull{it.start.atZone(zone).hour in 6..12}?:on(weekSky).firstOrNull()
            io.atmos.weather.domain.model.DailyWeather(d,
                on(weekHigh).mapNotNull{it.value.optString("MaxTemperature").toDoubleOrNull()}.maxOrNull(),
                on(weekLow).mapNotNull{it.value.optString("MinTemperature").toDoubleOrNull()}.minOrNull(),
                on(weekRain).mapNotNull{it.value.optString("ProbabilityOfPrecipitation").toIntOrNull()}.maxOrNull(),
                cwaTextToWmo(dayTime?.value?.optString("Weather")))
        }.filter{it.highC!=null||it.lowC!=null}
        val lat=city.coordinate.latitude;val lon=city.coordinate.longitude
        val sun=runCatching{SunTimes.compute().on(today).at(lat,lon).timezone(zone).execute()}.getOrNull()
        val sunrise=sun?.rise?.toInstant();val sunset=sun?.set?.toInstant()
        val isDay=if(sunrise!=null&&sunset!=null)now.isAfter(sunrise)&&now.isBefore(sunset) else now.atZone(zone).hour in 6..17
        return WeatherSnapshot(
            city=city,observedAt=observed?.at?:forecastHour,fetchedAt=now,
            sourceId=if(observed!=null)"cwa-observation+cwa" else id,
            temperatureC=temperature,
            feelsLikeC=observed?.let{CwaSurfaceObservations.apparent(it.temperature,it.humidity,it.windMs)}?:at(feels,now),
            humidityPct=humidity,windKmh=windMs?.times(3.6),pressureHpa=observed?.pressure,
            condition=conditionFromWmo(code).takeIf{it!=WeatherCondition.UNKNOWN}?:WeatherCondition.CLOUDY,
            isDay=isDay,sunrise=sunrise,sunset=sunset,hourly=hourly,
            maxTempC=(highs+dayTemps+temperature).maxOrNull(),minTempC=(lows+dayTemps+temperature).minOrNull(),
            currentUv=observed?.uvIndex?:uv,weatherCode=code,visibilityM=observed?.visibilityKm?.times(1000),
            windDirectionDeg=observed?.direction?:cwaDirection(at(direction,now)),
            timeZoneId=zone.id,
            observationStation=observed?.name,observationDistanceKm=observed?.distanceKm,
            observationDescription=observed?.description?:forecastText,
            feelsLikeMethod=if(observed!=null)"氣象署體感公式 · 附近測站實測溫濕度與風速" else "中央氣象署鄉鎮預報體感溫度",
            forecastReferenceAt=forecastHour,
            daily=daily
        )
    }

    private fun elementsOf(root:JSONObject):Map<String,JSONArray> {
        val location=root.getJSONObject("records").getJSONArray("Locations").getJSONObject(0)
            .getJSONArray("Location").getJSONObject(0)
        val list=location.getJSONArray("WeatherElement")
        return (0 until list.length()).associate{i->val e=list.getJSONObject(i);e.getString("ElementName") to e.getJSONArray("Time")}
    }
    private fun time(text:String?):Instant?=text?.takeIf{it.isNotBlank()}?.let{runCatching{OffsetDateTime.parse(it).toInstant()}.getOrNull()}
}

/** CWA weather phenomenon text (forecast or station) mapped onto WMO codes. */
internal fun cwaTextToWmo(text:String?):Int? {
    val t=text?.takeIf{it.isNotBlank()}?:return null
    return when {
        "雷" in t->95
        "冰雹" in t->96
        "雪" in t->if("雨" in t)77 else 73
        "大雨" in t||"豪雨" in t->65
        "陣雨" in t->80
        "毛毛" in t->51
        "雨" in t->61
        "霧" in t->45
        "陰" in t->3
        "晴" in t&&("雲" in t)->2
        "晴" in t->0
        "雲" in t->3
        else->null
    }
}

/** "偏北風" style CWA wind directions to the degrees the wind comes from. */
internal fun cwaDirection(text:String?):Int? {
    val t=text?.removePrefix("偏")?.removeSuffix("風")?:return null
    return when(t) {
        "北"->0;"東北"->45;"東"->90;"東南"->135;"南"->180;"西南"->225;"西"->270;"西北"->315
        "北北東"->22;"東北東"->67;"東南東"->112;"南南東"->157;"南南西"->202;"西南西"->247;"西北西"->292;"北北西"->337
        else->null
    }
}
