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
    private val clock:Clock=Clock.systemUTC(),
    private val observations:CwaSurfaceObservations?=null
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
                hourly = "temperature_2m,apparent_temperature,precipitation_probability,uv_index,relative_humidity_2m,wind_speed_10m,weather_code"
            )
        }
        val zone = ZoneId.of(response.timezone ?: "UTC")
        val current = requireNotNull(response.current) { "Missing current weather" }
        val now = requireNotNull(current.time) { "Missing observation timestamp" }.asInstant(zone)
        val temp=requireNotNull(current.temperature?.takeIf{it.isFinite()&&it in -90.0..65.0}){"Missing temperature"}
        check(response.currentUnits?.get("temperature_2m") in listOf(null,"°C")){"Unexpected temperature unit"}
        check(java.time.Duration.between(now,clock.instant()).seconds in -120L..7200L){"Current forecast time is stale"}
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
                    windKmh = windKmh(response.hourly?.winds?.getOrNull(index),response.hourlyUnits?.get("wind_speed_10m")),
                    weatherCode = response.hourly?.weatherCodes?.getOrNull(index),
                    feelsLikeC=response.hourly?.feelsLike?.getOrNull(index)?.takeIf{it.isFinite()}
                )
            }.getOrNull()
        }
        // Air quality comes from a different service: weather must still work if this fails.
        val air = try {
            airQuality.currentAir(city.coordinate.latitude, city.coordinate.longitude).current
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { null }
        val observed=observations?.nearest(city,response.elevation,clock.instant())
        val description=observed?.description
        val observedCode=description?.let {when {
            "雷" in it->95;"雪" in it->71;"霧" in it->45
            "毛毛" in it->51;"大雨" in it->65;"中雨" in it->63;"小雨" in it->61;"雨" in it->null
            "陰" in it->3;"雲" in it->2;"晴" in it->0;else->null
        }}
        val observedCondition=description?.let {when {
            "雷" in it->WeatherCondition.THUNDERSTORM;"雪" in it->WeatherCondition.SNOW
            "雨" in it->WeatherCondition.RAIN;"霧" in it->WeatherCondition.FOG
            "陰" in it->WeatherCondition.CLOUDY;"雲" in it->WeatherCondition.PARTLY_CLOUDY
            "晴" in it->WeatherCondition.CLEAR;else->null
        }}
        return WeatherSnapshot(
            city = city,
            observedAt = observed?.at ?: now,
            fetchedAt = clock.instant(),
            sourceId = if(observed!=null)"cwa-observation+open-meteo" else id,
            temperatureC = observed?.temperature ?: temp,
            feelsLikeC = observed?.let{CwaSurfaceObservations.apparent(it.temperature,it.humidity,it.windMs)} ?: current.feelsLike?.takeIf{it.isFinite()},
            humidityPct = observed?.humidity ?: current.humidity?.takeIf{it in 0..100},
            windKmh = observed?.windMs?.times(3.6) ?: windKmh(current.wind,response.currentUnits?.get("wind_speed_10m")),
            pressureHpa = observed?.pressure ?: current.pressure,
            condition = characterizeWeather(
                observedCondition ?: conditionFromWmo(current.code), air?.dust, observed?.windMs?.times(3.6) ?: windKmh(current.wind,response.currentUnits?.get("wind_speed_10m")), current.visibility,
                air?.aerosolOpticalDepth
            ),
            isDay = current.isDay == 1,
            sunrise = response.daily?.sunrise?.firstOrNull()?.asInstant(zone),
            sunset = response.daily?.sunset?.firstOrNull()?.asInstant(zone),
            hourly = series,
            maxTempC = response.daily?.highs?.firstOrNull(),
            minTempC = response.daily?.lows?.firstOrNull(),
            currentUv = current.uv,
            weatherCode = if(observedCondition!=null)observedCode else current.code,
            windDirectionDeg = observed?.direction ?: current.windDirection,
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
            windGustKmh = windKmh(current.gusts,response.currentUnits?.get("wind_gusts_10m")),
            timeZoneId = response.timezone,
            observationStation=observed?.name,observationDistanceKm=observed?.distanceKm,
            observationDescription=description,
            feelsLikeMethod=if(observed!=null)"氣象署體感公式 · 附近測站實測溫濕度與風速" else "Open-Meteo 模式體感 · 風、濕度與輻射",
            forecastReferenceAt=now
        )
    }
}

internal fun windKmh(value:Double?,unit:String?):Double? {
    val x=value?.takeIf{it.isFinite()&&it>=0}?:return null
    return when(unit){"m/s"->x*3.6;"mph"->x*1.609344;"kn"->x*1.852;"km/h",null->x;else->null}
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
