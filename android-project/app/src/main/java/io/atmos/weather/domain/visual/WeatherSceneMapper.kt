package io.atmos.weather.domain.visual

import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot

/** Weather-only scene metadata; 3D landmark catalog and model selection removed by request. */
data class WeatherScene(
    val wetness: Float,
    val snowAccumulation: Float,
    val nightLight: Float,
    val precipitationIntensity: Float,
    val showLightning: Boolean,
    val county: String = "",
    val locality: String = ""
)

fun sceneFor(weather: WeatherSnapshot): WeatherScene {
    val rain = weather.condition in setOf(
        WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM,
        WeatherCondition.FREEZING_RAIN, WeatherCondition.HAIL
    )
    val snow = weather.condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET)
    return WeatherScene(
        wetness = if (rain) .8f else 0f,
        snowAccumulation = if (snow) .85f else 0f,
        nightLight = if (weather.isDay) 0f else 1f,
        precipitationIntensity = if (rain || snow) .65f else 0f,
        showLightning = weather.condition == WeatherCondition.THUNDERSTORM,
        county = TaiwanLandmarks.countyFor(weather.city.name, weather.city.coordinate).orEmpty(),
        locality = weather.city.name
    )
}
