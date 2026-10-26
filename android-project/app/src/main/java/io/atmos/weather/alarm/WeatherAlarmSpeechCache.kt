package io.atmos.weather.alarm

import android.content.Context
import io.atmos.weather.domain.model.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONObject

/** Persist lightweight forecast facts only, never an AI-invented script or stale greeting. */
object WeatherAlarmSpeechCache {
    private const val PREF="thinn_alarms"
    private const val KEY="weather_voice_facts_r10"

    fun save(ctx:Context,w:WeatherSnapshot) {
        if(!w.temperatureC.isFinite() || w.fetchedAt.toEpochMilli()<=0 || w.observedAt.toEpochMilli()<=0) return
        val payload=JSONObject().apply {
            put("city",w.city.name)
            put("fetched",w.fetchedAt.toEpochMilli())
            put("observed",w.observedAt.toEpochMilli())
            put("condition",w.condition.name)
            put("temperature",w.temperatureC)
            put("feels",w.feelsLikeC?.takeIf(Double::isFinite))
            put("humidity",w.humidityPct)
            put("wind",w.windKmh?.takeIf(Double::isFinite))
            put("high",w.maxTempC?.takeIf(Double::isFinite))
            put("low",w.minTempC?.takeIf(Double::isFinite))
            put("aqi",w.usAqi)
            put("hours",JSONArray().apply {
                w.hourly.filter{!it.at.isBefore(w.observedAt.minusSeconds(3600))}.sortedBy{it.at}.take(48).forEach { h ->
                    put(JSONObject().apply {
                        put("at",h.at.toEpochMilli())
                        put("temp",h.temperatureC?.takeIf(Double::isFinite))
                        put("rain",h.precipitationProbabilityPct)
                        put("wind",h.windKmh?.takeIf(Double::isFinite))
                        put("uv",h.uvIndex?.takeIf(Double::isFinite))
                    })
                }
            })
        }
        ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString(KEY,payload.toString())
            .putFloat("next_weather_visibility",w.visibilityM?.takeIf{it.isFinite()}?.toFloat()?:-1f)
            .putInt("next_weather_code",w.weatherCode ?: -1)
            .putLong("next_weather_code_fetched",w.fetchedAt.toEpochMilli())
            .apply()
    }

    fun load(ctx:Context):AlarmWeatherReading? = runCatching {
        val text=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,null) ?: return null
        val j=JSONObject(text)
        val hours=j.optJSONArray("hours") ?: JSONArray()
        fun optNumber(key:String):Double?=if(j.isNull(key) || !j.has(key)) null else
            j.optDouble(key,Double.NaN).takeIf(Double::isFinite)
        AlarmWeatherReading(
            city=j.optString("city"),fetchedEpochMs=j.optLong("fetched"),
            observedEpochMs=j.optLong("observed"),condition=j.optString("condition"),
            temperatureC=j.optDouble("temperature",Double.NaN),
            feelsLikeC=optNumber("feels"),humidityPct=j.optInt("humidity",-1).takeIf{it in 0..100},
            windKmh=optNumber("wind"),highC=optNumber("high"),lowC=optNumber("low"),
            usAqi=j.optInt("aqi",-1).takeIf{it>=0},
            hours=(0 until hours.length()).mapNotNull { index ->
                runCatching {
                    val h=hours.getJSONObject(index)
                    fun number(key:String):Double?=if(!h.has(key) || h.isNull(key)) null else
                        h.optDouble(key,Double.NaN).takeIf(Double::isFinite)
                    AlarmForecastHour(h.optLong("at"),number("temp"),
                        h.optInt("rain",-1).takeIf{it in 0..100},number("wind"),number("uv"))
                }.getOrNull()
            }
        )
    }.getOrNull()
}
