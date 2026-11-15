package io.atmos.weather.data.remote

import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherDataQuality
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
import java.time.ZoneOffset

/** Real CWA township responses (嘉義市東區) become a complete, current snapshot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CwaForecastProviderTest {
    private val city=WeatherCity("gps","台灣 · 嘉義市 · 義教里",GeoPoint(23.4801,120.4491))
    private fun json(name:String)=JSONObject(javaClass.getResourceAsStream("/cwa/$name")!!.bufferedReader().readText())
    private val now=Instant.parse("2026-11-15T07:28:00Z")
    private val provider=CwaForecastProvider(OkHttpClient(),{"test"},clock=Clock.fixed(now,ZoneOffset.UTC))

    @Test fun townshipForecastAndWeekBecomeACurrentSnapshot() {
        val s=provider.parse(json("chiayi_east_3d.json"),json("chiayi_east_week.json"),city,null,now)
        assertEquals("cwa",s.sourceId)
        assertTrue(WeatherDataQuality.current(s,now))
        assertEquals(Instant.parse("2026-11-15T07:00:00Z"),s.forecastReferenceAt)
        assertTrue(s.temperatureC in 15.0..35.0)
        assertNotNull(s.humidityPct);assertNotNull(s.windKmh);assertNotNull(s.feelsLikeC);assertNotNull(s.currentUv)
        assertNotEquals(WeatherCondition.UNKNOWN,s.condition)
        assertTrue("High must cover the week forecast",s.maxTempC!!>=28.0)
        assertTrue(s.minTempC!!<=s.temperatureC)
        assertTrue(s.hourly.size>=24)
        assertTrue(s.hourly.take(12).all{it.precipitationProbabilityPct!=null&&it.weatherCode!=null})
        assertTrue(s.nextHours.isNotEmpty())
        assertTrue(s.isDay)
    }

    @Test fun nearbyStationOverridesTheCurrentReading() {
        val station=SurfaceObservation("467480","嘉義",120.4329,23.4959,26.9,Instant.parse("2026-11-15T07:20:00Z"),
            25.4,71,3.1,1011.5,350,"陰",distanceKm=2.4)
        val s=provider.parse(json("chiayi_east_3d.json"),null,city,station,now)
        assertEquals("cwa-observation+cwa",s.sourceId);assertEquals(25.4,s.temperatureC,0.0)
        assertEquals("嘉義",s.observationStation);assertEquals(WeatherCondition.CLOUDY,s.condition)
        assertTrue(WeatherDataQuality.current(s,now))
    }

    @Test fun missingKeyOrPlacesOutsideTaiwanHandOver() = runBlocking {
        val noKey=CwaForecastProvider(OkHttpClient(),{""},clock=Clock.fixed(now,ZoneOffset.UTC))
        assertTrue(runCatching{noKey.get(city)}.isFailure)
        val tokyo=WeatherCity("tokyo","東京",GeoPoint(35.68,139.76))
        assertTrue(runCatching{provider.get(tokyo)}.exceptionOrNull()?.message?.contains("範圍")==true)
    }

    @Test fun cwaPhenomenaMapOntoWmo() {
        assertEquals(0,cwaTextToWmo("晴"));assertEquals(2,cwaTextToWmo("晴時多雲"));assertEquals(3,cwaTextToWmo("多雲"))
        assertEquals(95,cwaTextToWmo("多雲午後短暫雷陣雨"));assertEquals(80,cwaTextToWmo("多雲短暫陣雨"))
        assertEquals(61,cwaTextToWmo("陰短暫雨"));assertEquals(45,cwaTextToWmo("晴有霧"));assertEquals(3,cwaTextToWmo("陰"))
        assertEquals(0,cwaDirection("偏北風"));assertEquals(225,cwaDirection("西南風"))
    }

    @Test fun everyTownshipResolvesToItsCountyForecast() {
        assertEquals(368,CwaTownships.all.size)
        val town=CwaTownships.resolve("台灣 · 嘉義市 · 義教里",23.4801,120.4491)!!
        assertEquals("F-D0047-057",town.dataset);assertEquals("東區",town.name)
        assertEquals("F-D0047-061",CwaTownships.resolve("台灣 · 臺北",25.0330,121.5654)!!.dataset)
        assertNull(CwaTownships.resolve("東京",35.68,139.76))
    }
}
