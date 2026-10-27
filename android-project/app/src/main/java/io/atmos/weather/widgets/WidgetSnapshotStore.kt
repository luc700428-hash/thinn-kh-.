package io.atmos.weather.widgets

import android.content.Context
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.visual.sceneFor

/** Exactly the same selected-location weather scene is read by the launcher and the main UI. */
object WidgetSnapshotStore {
    fun publish(context:Context, weather:WeatherSnapshot) {
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
    }
    fun clear(context:Context) {
        context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE).edit()
            .remove("widget_time").remove("widget_city_id").apply()
    }
}
