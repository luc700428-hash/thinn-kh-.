package io.atmos.weather.data.repository

import android.content.Context
import io.atmos.weather.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import io.atmos.weather.widgets.WidgetSnapshotStore

/** Persisted, limited forecast cache for offline access; deliberately excludes precise GPS history. */
class WeatherDiskCache(context: Context) {
    private val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    fun save(value: WeatherSnapshot) {
        val city = value.city
        val json = JSONObject().apply {
            put("city", city.name)
            put("lat", city.coordinate.latitude)
            put("lon", city.coordinate.longitude)
            put("observed", value.observedAt.toEpochMilli())
            put("fetched", value.fetchedAt.toEpochMilli())
            put("source", value.sourceId)
            put("temperature", value.temperatureC)
            put("feels", value.feelsLikeC)
            put("humidity", value.humidityPct)
            put("wind", value.windKmh)
            put("pressure", value.pressureHpa)
            put("condition", value.condition.name)
            put("day", value.isDay)
            put("sunrise", value.sunrise?.toEpochMilli())
            put("sunset", value.sunset?.toEpochMilli())
            put("high", value.maxTempC)
            put("low", value.minTempC)
            put("uv", value.currentUv)
            put("wmo_code", value.weatherCode)
            put("wind_direction", value.windDirectionDeg)
            put("visibility", value.visibilityM)
            put("us_aqi", value.usAqi)
            put("pm25", value.pm25)
            put("pm10", value.pm10)
            put("aerosol_optical_depth", value.aerosolOpticalDepth)
            put("timezone",value.timeZoneId)
            put("observation_station",value.observationStation);put("observation_distance",value.observationDistanceKm)
            put("observation_description",value.observationDescription);put("feels_method",value.feelsLikeMethod)
            put("forecast_reference",value.forecastReferenceAt?.toEpochMilli())
            put("hourly", JSONArray().also { items ->
                value.hourly.take(72).forEach { hour ->
                    items.put(JSONObject().apply {
                        put("at", hour.at.toEpochMilli())
                        put("temp", hour.temperatureC)
                        put("rain", hour.precipitationProbabilityPct)
                        put("uv", hour.uvIndex)
                        put("humidity", hour.humidityPct)
                        put("wind", hour.windKmh)
                        put("wmo", hour.weatherCode)
                        put("snow_prob",hour.snowfallProbabilityPct);put("snow",hour.snowfallCm)
                        put("freezing_rain_prob",hour.freezingRainProbabilityPct);put("feels",hour.feelsLikeC)
                    })
                }
            })
        }
        prefs.edit().putString("snapshot_${city.id}_${city.coordinate.latitude}_${city.coordinate.longitude}", json.toString()).apply()
        // Widget state is published only for the actively selected city by the ViewModel / Worker.

    }

    fun load(city: WeatherCity): WeatherSnapshot? {
        val raw = prefs.getString("snapshot_${city.id}_${city.coordinate.latitude}_${city.coordinate.longitude}", null) ?: return null
        return runCatching {
        val o = JSONObject(raw)
        val hours = o.optJSONArray("hourly") ?: JSONArray()
        WeatherSnapshot(
            city = city,
            observedAt = Instant.ofEpochMilli(o.getLong("observed")),
            fetchedAt = Instant.ofEpochMilli(o.getLong("fetched")),
            sourceId = o.optString("source", "open-meteo"),
            temperatureC = o.getDouble("temperature"),
            feelsLikeC = o.doubleOrNull("feels"),
            humidityPct = o.intOrNull("humidity"),
            windKmh = o.doubleOrNull("wind"),
            pressureHpa = o.doubleOrNull("pressure"),
            condition = WeatherCondition.valueOf(o.getString("condition")),
            isDay = o.getBoolean("day"),
            sunrise = o.longOrNull("sunrise")?.let(Instant::ofEpochMilli),
            sunset = o.longOrNull("sunset")?.let(Instant::ofEpochMilli),
            hourly = (0 until hours.length()).map { idx ->
                val h = hours.getJSONObject(idx)
                HourlyWeather(at=Instant.ofEpochMilli(h.getLong("at")),temperatureC=h.doubleOrNull("temp"),
                    precipitationProbabilityPct=h.intOrNull("rain"),uvIndex=h.doubleOrNull("uv"),
                    humidityPct=h.intOrNull("humidity"),windKmh=h.doubleOrNull("wind"),weatherCode=h.intOrNull("wmo"),
                    snowfallProbabilityPct=h.intOrNull("snow_prob"),snowfallCm=h.doubleOrNull("snow"),
                    freezingRainProbabilityPct=h.intOrNull("freezing_rain_prob"),feelsLikeC=h.doubleOrNull("feels"))
            },
            maxTempC = o.doubleOrNull("high"),
            minTempC = o.doubleOrNull("low"),
            currentUv = o.doubleOrNull("uv"),
            weatherCode = o.intOrNull("wmo_code"),
            windDirectionDeg = o.intOrNull("wind_direction"),
            visibilityM = o.doubleOrNull("visibility"),
            usAqi = o.intOrNull("us_aqi"),
            pm25 = o.doubleOrNull("pm25"),
            pm10 = o.doubleOrNull("pm10"),
            aerosolOpticalDepth = o.doubleOrNull("aerosol_optical_depth"),
            timeZoneId=o.optString("timezone","").ifBlank{null},
            observationStation=o.optString("observation_station","").takeUnless{it.isBlank()||it=="null"},
            observationDistanceKm=o.doubleOrNull("observation_distance"),
            observationDescription=o.optString("observation_description","").takeUnless{it.isBlank()||it=="null"},
            feelsLikeMethod=o.optString("feels_method","").takeUnless{it.isBlank()||it=="null"},
            forecastReferenceAt=o.longOrNull("forecast_reference")?.let(Instant::ofEpochMilli)
        )
    }.getOrNull()
    }

    private fun JSONObject.doubleOrNull(key: String) = if (isNull(key) || !has(key)) null else optDouble(key)
    private fun JSONObject.intOrNull(key: String) = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key) || !has(key)) null else optLong(key)
}
