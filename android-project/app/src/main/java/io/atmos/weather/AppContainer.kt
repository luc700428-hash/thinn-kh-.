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
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS).build()
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
            providers = listOf(OpenMeteoProvider(meteo, airQuality)),
            diskCache = WeatherDiskCache(context.applicationContext)
        ).also { instance = it }
    }
}
