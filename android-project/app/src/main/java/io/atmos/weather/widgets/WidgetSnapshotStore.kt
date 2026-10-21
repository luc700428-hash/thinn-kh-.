package io.atmos.weather.widgets

import android.content.Context
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.visual.sceneFor

/** Exactly the same selected-location weather scene is read by the launcher and the main UI. */
object WidgetSnapshotStore {
    fun publish(context:Context, weather:WeatherSnapshot) {
        val scene=sceneFor(weather)
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
            .putString("widget_county",scene.county)
            .putString("widget_locality",scene.locality)
            .putInt("widget_weather_code",weather.weatherCode ?: -1)
            .putLong("widget_time",weather.fetchedAt.toEpochMilli())
            .apply()
    }
    fun clear(context:Context) {
        context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE).edit()
            .remove("widget_time").remove("widget_city_id").apply()
    }
}
