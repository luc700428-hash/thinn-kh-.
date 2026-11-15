package io.atmos.weather.data.remote

import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherDataQuality
import io.atmos.weather.data.repository.DefaultWeatherDataRepository
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.repository.WeatherLoadResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** The fallback source must produce a complete, valid snapshot when Open-Meteo is unavailable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MetNorwayProviderTest {
    private val city=WeatherCity("chiayi","台灣 · 嘉義",GeoPoint(23.4801,120.4491))
    private fun sample()=JSONObject(javaClass.getResourceAsStream("/met/chiayi.json")!!.bufferedReader().readText())
    private val now=Instant.parse("2026-11-15T05:20:00Z")
    private val provider=MetNorwayProvider(OkHttpClient(),Clock.fixed(now,ZoneOffset.UTC),ZoneId.of("Asia/Taipei"))

    @Test fun realResponseBecomesACurrentSnapshot() {
        val s=provider.parse(sample(),city)
        assertEquals("met-norway",s.sourceId)
        assertTrue(s.temperatureC in -40.0..50.0)
        assertTrue(WeatherDataQuality.current(s,now))
        assertNotEquals(WeatherCondition.UNKNOWN,s.condition)
        assertTrue(s.hourly.size>=24)
        assertNotNull(s.maxTempC);assertNotNull(s.minTempC);assertNotNull(s.sunrise);assertNotNull(s.sunset)
        assertTrue(s.isDay)
        assertTrue(s.nextHours.isNotEmpty())
    }

    @Test fun symbolsMapOntoWmoCodes() {
        assertEquals(0,metSymbolToWmo("clearsky_day"));assertEquals(2,metSymbolToWmo("partlycloudy_night"))
        assertEquals(95,metSymbolToWmo("heavyrainshowersandthunder_day"));assertEquals(80,metSymbolToWmo("lightrainshowers_day"))
        assertEquals(65,metSymbolToWmo("heavyrain"));assertEquals(73,metSymbolToWmo("snow"));assertNull(metSymbolToWmo(null))
    }

    @Test fun repositoryFallsBackWhenTheFirstSourceTimesOut() = runBlocking {
        val failing=object:WeatherProvider{override val id="open-meteo"
            override suspend fun get(city:WeatherCity):WeatherSnapshot=throw java.net.SocketTimeoutException("Read timed out")}
        val backup=object:WeatherProvider{override val id="met-norway"
            override suspend fun get(city:WeatherCity)=provider.parse(sample(),city)}
        val result=DefaultWeatherDataRepository(listOf(failing,backup),Clock.fixed(now,ZoneOffset.UTC)).load(city,true)
        assertTrue(result is WeatherLoadResult.Success)
        assertEquals("met-norway",(result as WeatherLoadResult.Success).value.sourceId)
    }
}
