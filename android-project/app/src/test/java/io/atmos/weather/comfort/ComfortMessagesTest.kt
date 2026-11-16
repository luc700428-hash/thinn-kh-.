package io.atmos.weather.comfort

import io.atmos.weather.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ComfortMessagesTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = ZonedDateTime.of(2026, 11, 17, 7, 0, 0, 0, zone).toInstant()
    private fun weather(pop: (Int) -> Int = { 10 }, temp: (Int) -> Double = { 20.0 + it % 6 }, uv: Double? = 2.0, aqi: Int? = 40) =
        WeatherSnapshot(WeatherCity("t", "臺北", GeoPoint(25.03, 121.56)), now, now, "test", 21.0, 21.0, 70, 10.0, 1012.0,
            WeatherCondition.PARTLY_CLOUDY, true, null, null,
            (0 until 48).map { HourlyWeather(now.plusSeconds(it * 3600L), temp(it), pop(it), uv, humidityPct = 70, windKmh = 10.0, weatherCode = 2, feelsLikeC = temp(it)) },
            maxTempC = 26.0, minTempC = 19.0, currentUv = uv, usAqi = aqi, timeZoneId = "Asia/Taipei")

    @Test fun morningSaysWhatToWearAndWhetherToTakeAnUmbrella() {
        val t = ComfortMessages.morning(weather(pop = { if (it == 6) 70 else 10 }), now, zone, "臺北")
        assertTrue(t.title.contains("臺北"))
        assertTrue(t.body.contains("19–26°"))
        assertTrue(t.body.contains("穿著："))
        assertTrue(t.body.contains("一定要帶傘"))
        assertTrue(t.body.contains("13:00 前後"))
        assertTrue(t.body.contains("空氣品質：良好"))
    }
    @Test fun eveningDescribesTomorrowOnlyWithEnoughHours() {
        val t = ComfortMessages.evening(weather(), now.plusSeconds(14 * 3600), zone, "臺北")!!
        assertTrue(t.body.startsWith("明天"))
        assertTrue(t.body.contains("明早出門"))
        val short = weather().copy(hourly = weather().hourly.take(10))
        assertNull(ComfortMessages.evening(short, now.plusSeconds(14 * 3600), zone, "臺北"))
    }
    @Test fun smartAlertsFireOnlyForRealSignals() {
        val all = ComfortMessages.Smart.entries.toSet()
        assertTrue(ComfortMessages.smart(weather(), now, zone, all).isEmpty())
        val rain = ComfortMessages.smart(weather(pop = { if (it >= 2) 80 else 10 }), now, zone, all)
        assertEquals(listOf(ComfortMessages.Smart.RAIN_SOON), rain.map { it.first })
        val cold = ComfortMessages.smart(weather(temp = { if (it >= 10) 12.0 else 21.0 }), now, zone, all)
        assertTrue(cold.any { it.first == ComfortMessages.Smart.TEMP_SWING })
        val uvAtNine = ComfortMessages.smart(weather(uv = 9.0), now.plusSeconds(2 * 3600), zone, all)
        assertTrue(uvAtNine.any { it.first == ComfortMessages.Smart.UV_HIGH })
        assertTrue(ComfortMessages.smart(weather(aqi = 140), now, zone, setOf(ComfortMessages.Smart.AIR_BAD)).single().second.title.contains("不健康"))
        // Disabled kinds never appear.
        assertTrue(ComfortMessages.smart(weather(aqi = 140), now, zone, emptySet()).isEmpty())
    }
    @Test fun quietHoursWrapMidnight() {
        val from = LocalTime.of(22, 0); val to = LocalTime.of(7, 0)
        assertTrue(ComfortMessages.quiet(LocalTime.of(23, 30), from, to))
        assertTrue(ComfortMessages.quiet(LocalTime.of(6, 59), from, to))
        assertFalse(ComfortMessages.quiet(LocalTime.of(7, 0), from, to))
        assertFalse(ComfortMessages.quiet(LocalTime.of(12, 0), from, to))
        assertTrue(ComfortMessages.quiet(LocalTime.of(13, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)))
        assertFalse(ComfortMessages.quiet(LocalTime.of(13, 0), LocalTime.of(9, 0), LocalTime.of(9, 0)))
    }
    @Test fun commuteNamesBothEndsAndUmbrella() {
        val t = ComfortMessages.commute(weather(pop = { 65 }), weather(), now.plusSeconds(3600), zone, "家", "公司", "上班通勤")
        assertEquals("上班通勤 · 家 → 公司", t.title)
        assertTrue(t.body.contains("一定要帶傘"))
        assertTrue(t.summary.contains("記得帶傘"))
    }
    @Test fun schedulerSkipsUnselectedWeekdays() {
        val monday = ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 11, 17), ComfortScheduler.next(monday, 8, 0, 31)!!.toLocalDate())
        val friday = ZonedDateTime.of(2026, 11, 20, 9, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 11, 23), ComfortScheduler.next(friday, 8, 0, 31)!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 20), ComfortScheduler.next(friday, 21, 0)!!.toLocalDate())
    }
}
