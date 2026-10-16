package io.atmos.weather.data.remote

import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherSnapshot

/** Adapter contract: CWA, QWeather, WeatherAPI etc. can be added later. */
interface WeatherProvider {
    val id: String
    suspend fun get(city: WeatherCity): WeatherSnapshot
}
