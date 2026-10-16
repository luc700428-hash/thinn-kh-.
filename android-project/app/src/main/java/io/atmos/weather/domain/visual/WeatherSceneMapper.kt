package io.atmos.weather.domain.visual

import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot

/** UI / Filament share a value object. No engine types leak into domain. */
enum class LandmarkId { TAIPEI_101, TOKYO_SKYTREE, EIFFEL_TOWER, TAIWAN_COUNTY, WEATHER_COTTAGE }

data class WeatherScene(
    val landmark: LandmarkId,
    val wetness: Float,
    val snowAccumulation: Float,
    val nightLight: Float,
    val precipitationIntensity: Float,
    val showLightning: Boolean,
    val county: String = "",
    val locality: String = ""
)

fun sceneFor(weather: WeatherSnapshot): WeatherScene {
    val lat = weather.city.coordinate.latitude
    val lon = weather.city.coordinate.longitude
    val county = TaiwanLandmarks.countyFor(weather.city.name, weather.city.coordinate)
    val landmark = when {
        county != null -> LandmarkId.TAIWAN_COUNTY
        lat in 35.50..35.85 && lon in 139.50..139.95 -> LandmarkId.TOKYO_SKYTREE
        lat in 48.75..48.95 && lon in 2.20..2.50 -> LandmarkId.EIFFEL_TOWER
        else -> LandmarkId.WEATHER_COTTAGE
    }
    val rain = weather.condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM, WeatherCondition.FREEZING_RAIN, WeatherCondition.HAIL)
    val snow = weather.condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET)
    return WeatherScene(
        landmark = landmark,
        wetness = if (rain) 0.8f else 0f,
        snowAccumulation = if (snow) 0.85f else 0f,
        nightLight = if (weather.isDay) 0f else 1f,
        precipitationIntensity = if (rain || snow) 0.65f else 0f,
        showLightning = weather.condition == WeatherCondition.THUNDERSTORM,
        county = county ?: "",
        locality = weather.city.name
    )
}
