package io.atmos.weather.domain.model

import java.time.Duration
import java.time.Instant

/** Display freshness is checked against source time, never just the time a cache was opened. */
object WeatherDataQuality {
    fun current(weather:WeatherSnapshot,now:Instant):Boolean {
        if(!weather.temperatureC.isFinite()||weather.temperatureC !in -90.0..65.0)return false
        if(weather.feelsLikeC?.let{!it.isFinite()||it !in -150.0..100.0}==true)return false
        if(weather.humidityPct?.let{it !in 0..100}==true)return false
        val age=Duration.between(weather.observedAt,now).seconds
        val maximum=if(weather.observationStation!=null||weather.sourceId.contains("cwa-observation"))1800L else 7200L
        return age in -120L..maximum&&Duration.between(weather.fetchedAt,now).seconds in -120L..7200L
    }
    fun cacheFresh(weather:WeatherSnapshot,now:Instant,ttl:Duration):Boolean=
        current(weather,now)&&Duration.between(weather.fetchedAt,now).toMillis() in 0L until ttl.toMillis()
}
