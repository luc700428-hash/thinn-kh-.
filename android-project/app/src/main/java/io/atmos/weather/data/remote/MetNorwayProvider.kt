package io.atmos.weather.data.remote

import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.shredzone.commons.suncalc.SunTimes
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Second, independent forecast source: the Norwegian Meteorological Institute (MET Norway)
 * Locationforecast 2.0, global coverage, CC BY 4.0. Used only when Open-Meteo cannot answer
 * (outage, regional routing problems, time-outs), so users still get current weather.
 */
class MetNorwayProvider(
    private val http:OkHttpClient,
    private val clock:Clock=Clock.systemUTC(),
    private val zone:ZoneId=ZoneId.systemDefault()
):WeatherProvider {
    override val id="met-norway"

    override suspend fun get(city:WeatherCity):WeatherSnapshot=withContext(Dispatchers.IO) {
        val lat=String.format(Locale.US,"%.4f",city.coordinate.latitude)
        val lon=String.format(Locale.US,"%.4f",city.coordinate.longitude)
        val request=Request.Builder()
            .url("https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=$lat&lon=$lon")
            // MET Norway's terms require an identifying User-Agent.
            .header("User-Agent","thinn-khi/25 github.com/luc700428-hash/thinn-kh-.")
            .build()
        val body=http.newCall(request).execute().use {response->
            check(response.isSuccessful){"MET Norway HTTP ${response.code}"}
            requireNotNull(response.body).string()
        }
        parse(JSONObject(body),city)
    }

    internal fun parse(root:JSONObject,city:WeatherCity):WeatherSnapshot {
        val now=clock.instant()
        val series=root.getJSONObject("properties").getJSONArray("timeseries")
        data class Step(val at:Instant,val instant:JSONObject,val next:JSONObject?,val symbol:String?)
        val steps=(0 until series.length()).mapNotNull {i->
            val item=series.getJSONObject(i)
            val at=runCatching{Instant.parse(item.getString("time"))}.getOrNull()?:return@mapNotNull null
            val data=item.getJSONObject("data")
            val next=data.optJSONObject("next_1_hours")?:data.optJSONObject("next_6_hours")
            Step(at,data.getJSONObject("instant").getJSONObject("details"),next,
                next?.optJSONObject("summary")?.optString("symbol_code")?.takeIf{it.isNotBlank()})
        }
        require(steps.isNotEmpty()){"MET Norway returned no forecast"}
        // The current hour: the latest step that has already started.
        val current=steps.lastOrNull{!it.at.isAfter(now)}?:steps.first()
        val details=current.instant
        fun JSONObject.num(key:String):Double?=if(has(key))optDouble(key).takeIf{it.isFinite()} else null
        val temp=requireNotNull(details.num("air_temperature")?.takeIf{it in -90.0..65.0}){"Missing temperature"}
        val humidity=details.num("relative_humidity")?.toInt()?.coerceIn(0,100)
        val windMs=details.num("wind_speed")
        val code=metSymbolToWmo(current.symbol)
        val lat=city.coordinate.latitude;val lon=city.coordinate.longitude
        val today=now.atZone(zone).toLocalDate()
        val sun=runCatching{SunTimes.compute().on(today).at(lat,lon).timezone(zone).execute()}.getOrNull()
        val sunrise=sun?.rise?.toInstant();val sunset=sun?.set?.toInstant()
        val isDay=when {
            current.symbol?.endsWith("_day")==true->true
            current.symbol?.endsWith("_night")==true->false
            sunrise!=null&&sunset!=null->now.isAfter(sunrise)&&now.isBefore(sunset)
            else->true
        }
        val hourly=steps.filter{!it.at.isBefore(current.at)}.take(48).map {s->
            HourlyWeather(at=s.at,temperatureC=s.instant.num("air_temperature"),
                precipitationProbabilityPct=s.next?.optJSONObject("details")?.num("probability_of_precipitation")?.toInt(),
                uvIndex=s.instant.num("ultraviolet_index_clear_sky"),
                humidityPct=s.instant.num("relative_humidity")?.toInt()?.coerceIn(0,100),
                windKmh=s.instant.num("wind_speed")?.times(3.6),
                weatherCode=metSymbolToWmo(s.symbol))
        }
        val todays=steps.filter{it.at.atZone(zone).toLocalDate()==today}.mapNotNull{it.instant.num("air_temperature")}
        return WeatherSnapshot(
            city=city,observedAt=current.at,fetchedAt=now,sourceId=id,
            temperatureC=temp,feelsLikeC=details.num("apparent_air_temperature")?.takeIf{it in -150.0..100.0},humidityPct=humidity,windKmh=windMs?.times(3.6),
            pressureHpa=details.num("air_pressure_at_sea_level"),
            condition=conditionFromWmo(code),isDay=isDay,sunrise=sunrise,sunset=sunset,hourly=hourly,
            maxTempC=todays.maxOrNull(),minTempC=todays.minOrNull(),
            currentUv=details.num("ultraviolet_index_clear_sky"),weatherCode=code,
            windDirectionDeg=details.num("wind_from_direction")?.toInt(),
            timeZoneId=zone.id,forecastReferenceAt=current.at
        )
    }
}

/** MET Norway symbol codes mapped onto the WMO codes the rest of the app understands. */
internal fun metSymbolToWmo(symbol:String?):Int? {
    val s=symbol?.substringBefore('_')?:return null
    return when {
        s.contains("thunder")->95
        s=="clearsky"->0
        s=="fair"->1
        s=="partlycloudy"->2
        s=="cloudy"->3
        s=="fog"->45
        s.contains("sleet")->77
        s.contains("snowshowers")->if(s.startsWith("heavy"))86 else 85
        s.contains("snow")->if(s.startsWith("light"))71 else if(s.startsWith("heavy"))75 else 73
        s.contains("rainshowers")->if(s.startsWith("light"))80 else if(s.startsWith("heavy"))82 else 81
        s.contains("rain")->if(s.startsWith("light"))61 else if(s.startsWith("heavy"))65 else 63
        else->null
    }
}
