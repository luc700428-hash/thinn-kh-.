package io.atmos.weather.domain.model

import java.time.Instant

data class GeoPoint(val latitude: Double, val longitude: Double)
data class WeatherCity(val id: String, val name: String, val coordinate: GeoPoint)
enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, HAZE, DUST, DUST_STORM, RAIN, FREEZING_RAIN, SLEET, SNOW, HAIL, THUNDERSTORM, UNKNOWN }
data class HourlyWeather(
    val at: Instant,
    val temperatureC: Double?,
    val precipitationProbabilityPct: Int?,
    val uvIndex: Double?,
    val snowfallProbabilityPct: Int? = null,
    val snowfallCm: Double? = null,
    val freezingRainProbabilityPct: Int? = null,
    val humidityPct: Int? = null,
    val windKmh: Double? = null,
    val weatherCode: Int? = null,
    val feelsLikeC: Double? = null
)
data class WeatherSnapshot(
    val city: WeatherCity,
    val observedAt: Instant,
    val fetchedAt: Instant,
    val sourceId: String,
    val temperatureC: Double,
    val feelsLikeC: Double?,
    val humidityPct: Int?,
    val windKmh: Double?,
    val pressureHpa: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val sunrise: Instant?,
    val sunset: Instant?,
    val hourly: List<HourlyWeather>,
    val maxTempC: Double? = null,
    val minTempC: Double? = null,
    val currentUv: Double? = null,
    val weatherCode: Int? = null,
    val windDirectionDeg: Int? = null,
    val visibilityM: Double? = null,
    val usAqi: Int? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val sulfurDioxide: Double? = null,
    val nitrogenDioxide: Double? = null,
    val ozone: Double? = null,
    val carbonMonoxide: Double? = null,
    val aerosolOpticalDepth: Double? = null,
    val airborneDust: Double? = null,
    val snowfallCm: Double? = null,
    val snowDepthM: Double? = null,
    val windGustKmh: Double? = null,
    val timeZoneId: String? = null,
    val observationStation: String? = null,
    val observationDistanceKm: Double? = null,
    val observationDescription: String? = null,
    val feelsLikeMethod: String? = null,
    val forecastReferenceAt: Instant? = null
) {
    val nextHourRainProbabilityPct: Int?
        get() = hourly.firstOrNull { !it.at.isBefore(forecastReferenceAt ?: observedAt) }?.precipitationProbabilityPct
    val nextHours: List<HourlyWeather>
        get() = hourly.filter { !it.at.isBefore(forecastReferenceAt ?: observedAt) }.take(24)
}
object SampleCities {
    val all = listOf(
        WeatherCity("taipei", "台灣 · 臺北", GeoPoint(25.0330, 121.5654)),
        WeatherCity("chiayi", "台灣 · 嘉義", GeoPoint(23.4801, 120.4491)),
        WeatherCity("taichung", "台灣 · 臺中", GeoPoint(24.1477, 120.6736)),
        WeatherCity("kaohsiung", "台灣 · 高雄", GeoPoint(22.6273, 120.3014)),
        WeatherCity("tokyo", "東京", GeoPoint(35.6812, 139.7671)),
        WeatherCity("paris", "巴黎", GeoPoint(48.8584, 2.2945)),
        WeatherCity("london", "倫敦", GeoPoint(51.5072, -0.1276)),
        WeatherCity("seoul", "首爾", GeoPoint(37.5665, 126.9780)),
        WeatherCity("new-york", "紐約", GeoPoint(40.7128, -74.0060))
    )
}
