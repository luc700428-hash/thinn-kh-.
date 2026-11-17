package io.atmos.weather.presentation.home

import io.atmos.weather.domain.model.*
import io.atmos.weather.lifestyle.LifestyleAdvisor
import io.atmos.weather.lifestyle.OutingMode
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeDetailTilesTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = ZonedDateTime.of(2026, 11, 17, 15, 0, 0, 0, zone).toInstant()
    private fun weather(pressure: Double? = 1012.0, visibility: Double? = 12_000.0, aqi: Int? = 77) =
        WeatherSnapshot(WeatherCity("t", "嘉義", GeoPoint(23.48, 120.45)), now, now, "test", 24.0, 25.0, 86, 8.0, pressure,
            WeatherCondition.CLOUDY, true, now.minusSeconds(9 * 3600), now.plusSeconds(2 * 3600),
            (0 until 24).map { HourlyWeather(now.plusSeconds(it * 3600L), 24.0 - it * .2, 10, .0, humidityPct = 80, windKmh = 8.0, weatherCode = 3, feelsLikeC = 25.0) },
            maxTempC = 25.0, minTempC = 19.0, currentUv = 5.0, windDirectionDeg = 0, visibilityM = visibility, usAqi = aqi, pm25 = 18.0, timeZoneId = "Asia/Taipei")

    @Test fun everyTileHasOneShortValueAndCaption() {
        val tiles = HomeDetailTiles.build(weather(), now)
        assertEquals(8, tiles.size)
        for (t in tiles) {
            assertTrue(t.label, t.value.isNotBlank() && t.value != "—" && t.value.length <= 9)
            assertTrue(t.label, t.caption.isNotBlank() && t.caption.length <= 18)
            assertTrue(t.label, t.fraction == null || t.fraction!!.isFinite())
        }
        assertEquals("北風", tiles.first { it.kind == HomeDetailTiles.Kind.WIND }.caption)
        assertEquals("日落", tiles.first { it.kind == HomeDetailTiles.Kind.SUN }.label)
    }
    @Test fun missingSourcesAreLeftOutInsteadOfShowingDashes() {
        val kinds = HomeDetailTiles.build(weather(pressure = null, visibility = null, aqi = null).copy(pm25 = null), now).map { it.kind }
        assertFalse(HomeDetailTiles.Kind.PRESSURE in kinds)
        assertFalse(HomeDetailTiles.Kind.VISIBILITY in kinds)
        assertFalse(HomeDetailTiles.Kind.AIR in kinds)
    }
    @Test fun dewPointIsSensible() {
        assertEquals(21.5, HomeDetailTiles.dewPoint(24.0, 86)!!, .6)
        assertNull(HomeDetailTiles.dewPoint(24.0, 0))
    }
    @Test fun todayGlanceFitsThreeTiles() {
        val w = weather()
        val g = TodayGlance.build(w, LifestyleAdvisor.recommend(w, OutingMode.entries.first(), false))
        for (value in listOf(g.wear, g.coat, g.rain)) assertTrue(value, value.length <= 3)
        assertEquals("體感 25°", g.wearNote)
        assertTrue(g.mood.length <= 2)
    }
}
