package io.atmos.weather.domain.repository

import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherSnapshot

sealed interface WeatherLoadResult {
    data class Success(val value: WeatherSnapshot, val fromCache: Boolean, val stale: Boolean) : WeatherLoadResult
    data class Failure(val cause: String) : WeatherLoadResult
}

interface WeatherDataRepository {
    suspend fun load(city: WeatherCity, forceRefresh: Boolean = false): WeatherLoadResult
}
