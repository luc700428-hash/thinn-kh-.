package io.atmos.weather.widgets

import android.content.Context
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.visual.sceneFor

/** Exactly the same selected-location weather scene is read by the launcher and the main UI. */
data class WidgetTarget(val city:WeatherCity,val revision:Long)
object WidgetSnapshotStore {
    @Synchronized fun select(context:Context,city:WeatherCity):WidgetTarget {
        val prefs=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE)
        val current=readTarget(context)
        if(current!=null&&current.city.id==city.id&&current.city.coordinate==city.coordinate)return current
        val revision=(prefs.getLong("widget_target_revision",0)+1).coerceAtLeast(1)
        prefs.edit().putString("widget_target_id",city.id).putString("widget_target_name",city.name)
            .putLong("widget_target_lat",city.coordinate.latitude.toBits()).putLong("widget_target_lon",city.coordinate.longitude.toBits())
            .putLong("widget_target_revision",revision).putString("widget_city",city.name)
            .remove("widget_time").remove("widget_city_id").apply()
        return WidgetTarget(city,revision)
    }
    private fun readTarget(context:Context):WidgetTarget? {
        val p=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE)
        val id=p.getString("widget_target_id",null)?:return null
        return WidgetTarget(WeatherCity(id,p.getString("widget_target_name","目前位置")?:"目前位置",
            GeoPoint(Double.fromBits(p.getLong("widget_target_lat",0)),Double.fromBits(p.getLong("widget_target_lon",0)))),p.getLong("widget_target_revision",1))
    }
    @Synchronized fun target(context:Context):WidgetTarget {
        readTarget(context)?.let{return it}
        val p=context.getSharedPreferences("atmos_settings",Context.MODE_PRIVATE)
        val id=p.getString("city_id",null)
        val city=SampleCities.all.find{it.id==id}?:if(id!=null&&p.contains("lat"))
            WeatherCity(id,p.getString("city_name","目前位置")?:"目前位置",GeoPoint(Double.fromBits(p.getLong("lat",0)),Double.fromBits(p.getLong("lon",0))))
            else SampleCities.all.first()
        return select(context,city)
    }
    @Synchronized fun publish(context:Context, weather:WeatherSnapshot,expectedRevision:Long?=null):Boolean {
        val target=readTarget(context)?:select(context,weather.city)
        if(target.city.id!=weather.city.id||target.city.coordinate!=weather.city.coordinate||
            (expectedRevision!=null&&target.revision!=expectedRevision))return false
        val p=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE)
        if(p.getString("widget_city_id",null)==weather.city.id&&p.getLong("widget_time",0)>weather.fetchedAt.toEpochMilli())return false
        val scene=sceneFor(weather)
        val zone=runCatching{java.time.ZoneId.of(weather.timeZoneId)}.getOrDefault(java.time.ZoneId.systemDefault())
        val rise=weather.sunrise?.atZone(zone)?.toLocalTime()
        val set=weather.sunset?.atZone(zone)?.toLocalTime()
        val hours=org.json.JSONArray().apply{weather.nextHours.take(12).forEach{h->put(org.json.JSONObject().apply{
            put("at",h.at.toEpochMilli());put("temp",h.temperatureC);put("rain",h.precipitationProbabilityPct?:-1);put("code",h.weatherCode?:-1)
            // The day's observed solar window is reused for the short forecast strip.
            val local=h.at.atZone(zone).toLocalTime()
            put("day",if(rise!=null&&set!=null&&rise<set)local>=rise&&local<set else weather.isDay)
        })}}
        context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE).edit()
            .putString("widget_city",weather.city.name)
            .putString("widget_city_id",weather.city.id)
            .putInt("widget_temp",weather.temperatureC.toInt())
            .putString("widget_condition",weather.condition.name)
            .putInt("widget_rain",weather.nextHourRainProbabilityPct ?: -1)
            .putInt("widget_snow",weather.nextHours.firstOrNull()?.snowfallProbabilityPct ?: -1)
            .putInt("widget_air",weather.usAqi ?: -1)
            .putInt("widget_visibility",weather.visibilityM?.div(1000)?.toInt() ?: -1)
            .putBoolean("widget_day",weather.isDay)
            .putString("widget_landmark",scene.landmark.name)
            .putString("widget_county",scene.county)
            .putString("widget_locality",scene.locality)
            .putInt("widget_weather_code",weather.weatherCode ?: -1)
            .putString("widget_hours",hours.toString())
            .putString("widget_zone",weather.timeZoneId?:java.time.ZoneId.systemDefault().id)
            .putInt("widget_humidity",weather.humidityPct?:-1)
            .putFloat("widget_wind",weather.windKmh?.div(3.6)?.toFloat()?:-1f)
            .putFloat("widget_uv",weather.currentUv?.toFloat()?:-1f)
            .apply {if(weather.maxTempC!=null&&weather.minTempC!=null){putInt("widget_high",weather.maxTempC.toInt());putInt("widget_low",weather.minTempC.toInt())}else{remove("widget_high");remove("widget_low")}}
            .putLong("widget_observed_time",weather.observedAt.toEpochMilli())
            .putLong("widget_time",weather.fetchedAt.toEpochMilli())
            .apply()
        return true
    }
    fun clear(context:Context) {
        context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE).edit()
            .remove("widget_time").remove("widget_city_id").apply()
    }
}
