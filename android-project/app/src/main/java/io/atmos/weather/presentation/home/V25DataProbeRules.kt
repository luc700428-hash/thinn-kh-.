package io.atmos.weather.presentation.home

/** Data availability is not the same as a verified live response. */
internal object V25DataProbeRules {
    enum class WeatherResult { NETWORK_FRESH, CACHED, INVALID_TIME }
    fun classifyWeather(fromCache: Boolean, stale: Boolean, fetchedAtMillis: Long,
                        nowMillis: Long): WeatherResult {
        if (fromCache || stale) return WeatherResult.CACHED
        val age = nowMillis - fetchedAtMillis
        if (fetchedAtMillis <= 0L || age !in -300_000L..1_800_000L)
            return WeatherResult.INVALID_TIME
        return WeatherResult.NETWORK_FRESH
    }
}
