package io.atmos.weather.insights

import io.atmos.weather.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LifeIndexTest {
    private val now = Instant.parse("2026-11-17T01:00:00Z")
    private fun weather(pop: Int = 10, temp: (Int) -> Double = { 22.0 }, humidity: Int = 60, uv: Double? = 3.0, aqi: Int? = 40, hours: Int = 48) =
        WeatherSnapshot(WeatherCity("t", "臺北", GeoPoint(25.03, 121.56)), now, now, "test", temp(0), temp(0), humidity, 8.0, 1012.0,
            WeatherCondition.PARTLY_CLOUDY, true, null, null,
            (0 until hours).map { HourlyWeather(now.plusSeconds(it * 3600L), temp(it), pop, uv, humidityPct = humidity, windKmh = 8.0, weatherCode = 2, feelsLikeC = temp(it)) },
            currentUv = uv, usAqi = aqi, timeZoneId = "Asia/Taipei")
    private fun level(items: List<LifeIndex.Item>, title: String) = items.first { it.title == title }.level

    @Test fun aCalmDryDayIsGoodForEverything() {
        val items = LifeIndex.build(weather(), now)
        assertEquals(listOf("洗車", "晾衣", "戶外運動", "著涼風險", "防曬", "開窗通風"), items.map { it.title })
        assertTrue(items.all { it.level == LifeIndex.Level.GOOD })
        assertTrue(items.first().reason.contains("兩天"))
    }
    @Test fun rainHeatSwingsAndBadAirLowerTheRightIndices() {
        val wet = LifeIndex.build(weather(pop = 70), now)
        assertEquals(LifeIndex.Level.POOR, level(wet, "洗車")); assertEquals(LifeIndex.Level.POOR, level(wet, "晾衣")); assertEquals(LifeIndex.Level.POOR, level(wet, "戶外運動"))
        assertEquals(LifeIndex.Level.POOR, level(LifeIndex.build(weather(humidity = 90), now), "晾衣"))
        assertEquals(LifeIndex.Level.POOR, level(LifeIndex.build(weather(temp = { if (it < 6) 26.0 else 14.0 }), now), "著涼風險"))
        assertEquals(LifeIndex.Level.POOR, level(LifeIndex.build(weather(uv = 9.0), now), "防曬"))
        // A risk says how big it is, never "不建議".
        assertEquals("高", LifeIndex.build(weather(temp = { if (it < 6) 26.0 else 14.0 }), now).first { it.title == "著涼風險" }.word)
        assertEquals("必要", LifeIndex.build(weather(uv = 9.0), now).first { it.title == "防曬" }.word)
        val smog = LifeIndex.build(weather(aqi = 160), now)
        assertEquals(LifeIndex.Level.POOR, level(smog, "開窗通風")); assertEquals(LifeIndex.Level.POOR, level(smog, "戶外運動"))
    }
    @Test fun missingDataLeavesAnIndexOutInsteadOfGuessing() {
        val bare = LifeIndex.build(weather(uv = null, aqi = null, hours = 0), now)
        assertTrue(bare.none { it.title in listOf("洗車", "晾衣", "戶外運動", "防曬", "開窗通風") })
        // With only a short forecast the car-wash advice says so.
        assertTrue(LifeIndex.build(weather(hours = 12), now).first { it.title == "洗車" }.reason.contains("12 小時"))
    }
}
