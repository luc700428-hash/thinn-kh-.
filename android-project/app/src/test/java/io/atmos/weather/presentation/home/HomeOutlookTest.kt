package io.atmos.weather.presentation.home

import io.atmos.weather.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeOutlookTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, zone).toInstant()
    private fun weather(condition: WeatherCondition = WeatherCondition.PARTLY_CLOUDY, pop: (Int) -> Int = { 10 }, temp: (Int) -> Double = { 22.0 }) =
        WeatherSnapshot(WeatherCity("t", "臺北", GeoPoint(25.03, 121.56)), now, now, "test", 22.0, 22.0, 70, 8.0, 1012.0,
            condition, true, now.minusSeconds(3 * 3600), now.plusSeconds(8 * 3600),
            (0 until 72).map { HourlyWeather(now.plusSeconds(it * 3600L), temp(it), pop(it), 3.0, humidityPct = 60, windKmh = 8.0, weatherCode = 2, feelsLikeC = temp(it)) },
            maxTempC = 25.0, minTempC = 19.0, usAqi = 42, timeZoneId = "Asia/Taipei")

    @Test fun dryDaySaysSoAndOffersThreeTiles() {
        val o = HomeOutlook.build(weather(), now)
        assertEquals("接下來 12 小時不太會下雨", o.rain)
        assertEquals(listOf("最佳出門", "日落", "空氣品質"), o.tiles.map { it.label })
        assertEquals("17:00", o.tiles[1].value)
        assertEquals("良好", o.tiles[2].caption)
    }
    @Test fun rainLaterNamesTheHourAndRainNowSaysWhenItEases() {
        assertEquals("預計 14:00 前後可能下雨（降雨機率 70%）", HomeOutlook.build(weather(pop = { if (it >= 5) 70 else 10 }), now).rain)
        assertEquals("正在下雨，預計 12:00 前後趨緩", HomeOutlook.build(weather(WeatherCondition.RAIN, pop = { if (it < 3) 80 else 10 }), now).rain)
        assertEquals("正在下雨，接下來 12 小時都可能有雨", HomeOutlook.build(weather(WeatherCondition.RAIN, pop = { 80 }), now).rain)
    }
    @Test fun tomorrowIsComparedWithTodayOnlyFromRealDays() {
        val cooler = HomeOutlook.build(weather(temp = { if (it >= 15) 16.0 else 22.0 }), now).tomorrow!!
        assertTrue(cooler, cooler.startsWith("明天 ") && cooler.contains("比今天涼"))
        assertNull(HomeOutlook.build(weather().copy(hourly = weather().hourly.take(6)), now).tomorrow)
    }
    @Test fun shareTextNeverContainsForbiddenLabels() {
        val text = HomeOutlook.shareText(weather(), "臺北", now)
        assertTrue(text.startsWith("臺北 現在 22°"))
        for (bad in listOf("中國", "中华", "中華台北", "China")) assertFalse(text.contains(bad))
    }
}
