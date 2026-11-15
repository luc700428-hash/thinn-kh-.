package io.atmos.weather

import android.content.Context
import io.atmos.weather.data.remote.GeoSearchApi
import io.atmos.weather.data.remote.OpenMeteoAirQualityApi
import io.atmos.weather.data.remote.OpenMeteoApi
import io.atmos.weather.data.remote.OpenMeteoProvider
import io.atmos.weather.data.repository.DefaultWeatherDataRepository
import io.atmos.weather.data.repository.WeatherDiskCache
import io.atmos.weather.domain.repository.WeatherDataRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Explicit app-scoped dependencies; no private location or secret leaves Weather APIs. */
object AppContainer {
    // Bounded per call: a slow or unreachable source must hand over to the next one quickly
    // instead of leaving the user on a blank screen.
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(16, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true).build()
    private fun retrofit(url: String) = Retrofit.Builder().baseUrl(url).client(http)
        .addConverterFactory(GsonConverterFactory.create()).build()
    private val airQuality = retrofit("https://air-quality-api.open-meteo.com/").create(OpenMeteoAirQualityApi::class.java)
    private val meteo = retrofit("https://api.open-meteo.com/").create(OpenMeteoApi::class.java)
    val geoApi: GeoSearchApi by lazy {
        retrofit("https://geocoding-api.open-meteo.com/").create(GeoSearchApi::class.java)
    }
    @Volatile private var instance: WeatherDataRepository? = null
    fun repository(context: Context): WeatherDataRepository = synchronized(this) {
        instance ?: DefaultWeatherDataRepository(
            // Taiwan: Central Weather Administration open data first. Elsewhere, or whenever CWA
            // cannot answer, Open-Meteo and then MET Norway take over.
            providers = run {
                val app=context.applicationContext
                val stations=io.atmos.weather.data.remote.CwaSurfaceObservations(app)
                listOf(io.atmos.weather.data.remote.CwaForecastProvider(http,
                        key={io.atmos.weather.earthquake.EarthquakeSecurity.load(app)?.takeIf{it.isNotBlank()}?:io.atmos.weather.BuildConfig.THINN_CWA_API_KEY},
                        observations=stations,airQuality=airQuality),
                    OpenMeteoProvider(meteo,airQuality,observations=stations),
                    io.atmos.weather.data.remote.MetNorwayProvider(http))
            },
            diskCache = WeatherDiskCache(context.applicationContext)
        ).also { instance = it }
    }
}
