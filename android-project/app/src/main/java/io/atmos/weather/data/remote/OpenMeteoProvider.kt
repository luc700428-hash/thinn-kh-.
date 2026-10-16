package io.atmos.weather.data.remote

import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

class OpenMeteoProvider(
    private val api: OpenMeteoApi,
    private val airQuality: OpenMeteoAirQualityApi,
    private val clock: Clock = Clock.systemUTC()
) : WeatherProvider {
    override val id = "open-meteo"

    override suspend fun get(city: WeatherCity): WeatherSnapshot {
        // Snow-type probabilities are not available from every model/location. Preserve
        // basic weather when an extended-parameter combination returns HTTP 400.
        val response = try {
            api.forecast(city.coordinate.latitude, city.coordinate.longitude)
        } catch (error: retrofit2.HttpException) {
            if (error.code() != 400) throw error
            api.forecast(
                city.coordinate.latitude, city.coordinate.longitude,
                current = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,surface_pressure,uv_index,wind_direction_10m,visibility",
                hourly = "temperature_2m,precipitation_probability,uv_index,relative_humidity_2m,wind_speed_10m,weather_code"
            )
        }
        val zone = ZoneId.of(response.timezone ?: "UTC")
        val current = requireNotNull(response.current) { "Missing current weather" }
        val now = requireNotNull(current.time) { "Missing observation timestamp" }.asInstant(zone)
        val temp = requireNotNull(current.temperature) { "Missing temperature" }
        val times = response.hourly?.time.orEmpty()
        val series = times.mapIndexedNotNull { index, timestamp ->
            runCatching {
                HourlyWeather(
                    at = timestamp.asInstant(zone),
                    temperatureC = response.hourly?.temperatures?.getOrNull(index),
                    precipitationProbabilityPct = response.hourly?.rainProbabilities?.getOrNull(index),
                    snowfallProbabilityPct = response.hourly?.snowProbabilities?.getOrNull(index),
                    snowfallCm = response.hourly?.snowfalls?.getOrNull(index),
                    freezingRainProbabilityPct = response.hourly?.freezingRainProbabilities?.getOrNull(index),
                    uvIndex = response.hourly?.uvIndices?.getOrNull(index),
                    humidityPct = response.hourly?.humidities?.getOrNull(index),
                    windKmh = response.hourly?.winds?.getOrNull(index),
                    weatherCode = response.hourly?.weatherCodes?.getOrNull(index)
                )
            }.getOrNull()
        }
        // Air quality comes from a different service: weather must still work if this fails.
        val air = try {
            airQuality.currentAir(city.coordinate.latitude, city.coordinate.longitude).current
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { null }
        return WeatherSnapshot(
            city = city,
            observedAt = now,
            fetchedAt = clock.instant(),
            sourceId = id,
            temperatureC = temp,
            feelsLikeC = current.feelsLike,
            humidityPct = current.humidity,
            windKmh = current.wind,
            pressureHpa = current.pressure,
            condition = characterizeWeather(
                conditionFromWmo(current.code), air?.dust, current.wind, current.visibility,
                air?.aerosolOpticalDepth
            ),
            isDay = current.isDay == 1,
            sunrise = response.daily?.sunrise?.firstOrNull()?.asInstant(zone),
            sunset = response.daily?.sunset?.firstOrNull()?.asInstant(zone),
            hourly = series,
            maxTempC = response.daily?.highs?.firstOrNull(),
            minTempC = response.daily?.lows?.firstOrNull(),
            currentUv = current.uv,
            weatherCode = current.code,
            windDirectionDeg = current.windDirection,
            visibilityM = current.visibility,
            usAqi = air?.usAqi,
            pm25 = air?.pm25,
            pm10 = air?.pm10,
            sulfurDioxide = air?.sulfurDioxide,
            nitrogenDioxide = air?.nitrogenDioxide,
            ozone = air?.ozone,
            carbonMonoxide = air?.carbonMonoxide,
            aerosolOpticalDepth = air?.aerosolOpticalDepth,
            airborneDust = air?.dust,
            snowfallCm = current.snowfall,
            snowDepthM = current.snowDepth,
            windGustKmh = current.gusts,
            timeZoneId = response.timezone
        )
    }
}

private fun String.asInstant(zone: ZoneId): Instant = LocalDateTime.parse(this).atZone(zone).toInstant()

/** Open-Meteo current weather_code uses WMO numeric codes. */
fun conditionFromWmo(code: Int?): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    56, 57, 66, 67 -> WeatherCondition.FREEZING_RAIN
    77 -> WeatherCondition.SLEET
    in 51..55, in 61..65, in 80..82 -> WeatherCondition.RAIN
    in 71..75, 85, 86 -> WeatherCondition.SNOW
    96, 99 -> WeatherCondition.HAIL
    95 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}

/** Model-based air-mass characterization, NOT an official dust-storm alert.
 * Mark as dust storm only when dust + reduced visibility + strong wind concur.
 * In ambiguous cases choose "dust/haze", never invent an official event.
 */
internal fun characterizeWeather(
    baseline: WeatherCondition, dust: Double?, windKmh: Double?, visibilityM: Double?,
    aerosol: Double?
): WeatherCondition {
    if (baseline !in setOf(WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY, WeatherCondition.FOG)) return baseline
    if (baseline == WeatherCondition.FOG) return baseline
    if (dust != null && dust >= 140.0 && (windKmh ?: 0.0) >= 45.0 && (visibilityM ?: 10000.0) <= 2500.0) return WeatherCondition.DUST_STORM
    if (dust != null && dust >= 65.0) return WeatherCondition.DUST
    if ((aerosol ?: 0.0) >= .5) return WeatherCondition.HAZE
    return baseline
}
