package io.atmos.weather.life

import io.atmos.weather.domain.model.DailyWeather
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LifeLogicTest {
    private val zone = ZoneId.of("Asia/Taipei")
    // Wednesday 2026-11-25 08:00 Taipei.
    private val now = LocalDate.of(2026, 11, 25).atTime(8, 0).atZone(zone).toInstant()

    private fun weather(temps: (Int) -> Double = { 20.0 + (it % 24) / 4.0 }, pop: (Int) -> Int = { 10 }, wind: Double = 8.0,
                        daily: List<DailyWeather> = emptyList(), aqi: Int? = 40) = WeatherSnapshot(
        WeatherCity("t", "臺北市大安區", GeoPoint(25.03, 121.54)), now, now, "test", temps(0), temps(0), 70, wind, 1012.0,
        WeatherCondition.PARTLY_CLOUDY, true, null, null,
        (0 until 72).map { HourlyWeather(now.plusSeconds(it * 3600L), temps(it), pop(it), 3.0, windKmh = wind) },
        maxTempC = 24.0, minTempC = 18.0, usAqi = aqi, daily = daily)

    @Test fun steadyPressureIsCalmAndAFastFallIsAWarning() {
        val calm = (-24..48).map { PressurePain.Point(now.plusSeconds(it * 3600L), 1013.0 + (it % 3) * .1) }
        assertEquals(PressurePain.Level.CALM, PressurePain.assess(calm, now)!!.level)
        // 1 hPa an hour for 6 hours from 10:00, a typhoon-like fall.
        val falling = (-24..48).map { h -> PressurePain.Point(now.plusSeconds(h * 3600L), 1012.0 - (h - 2).coerceIn(0, 6) * 1.2) }
        val o = PressurePain.assess(falling, now)!!
        assertEquals(PressurePain.Level.WARNING, o.level)
        assertEquals(3.6, o.drop3h, .01)
        assertEquals(7.2, o.drop24h, .01)
        assertNotNull(o.worstAt)
        assertNull("Too little data", PressurePain.assess(falling.take(3), now))
    }

    @Test fun gradesFollowTheFallRate() {
        assertEquals(PressurePain.Level.WATCH, PressurePain.grade(1.2, 2.0))
        assertEquals(PressurePain.Level.ALERT, PressurePain.grade(.5, 6.5))
        assertEquals(PressurePain.Level.WARNING, PressurePain.grade(3.1, 4.0))
    }

    @Test fun digestIsTwoPlainSentencesAboutRainSwingAndWarnings() {
        val wet = weather(pop = { if (it == 6) 70 else 10 })
        val text = DailyDigest.build(DailyDigest.Inputs(wet, listOf("大雨特報")), now, zone)
        assertTrue(text, text.startsWith("今天"))
        assertTrue(text, text.contains("下午 2 點起可能下雨"))
        assertTrue(text, text.contains("大雨特報"))
        assertEquals(2, text.count { it == '。' })
        val quiet = DailyDigest.build(DailyDigest.Inputs(weather()), now, zone)
        assertTrue(quiet, quiet.contains("大致不會下雨") && quiet.endsWith("沒有警特報，空氣與氣壓也都平穩。"))
        val evening = DailyDigest.build(DailyDigest.Inputs(weather()), now.plusSeconds(12 * 3600L), zone)
        assertTrue(evening, evening.startsWith("明天"))
    }

    @Test fun calibrationMovesHalfADegreeAndStaysWithinTwo() {
        var b = 0.0
        repeat(10) { b = ComfortCalibration.next(b, ComfortCalibration.Feeling.COLD) }
        assertEquals(-2.0, b, 0.0)
        b = ComfortCalibration.next(b, ComfortCalibration.Feeling.JUST_RIGHT)
        assertEquals(-1.5, b, 0.0)
        assertEquals(.5, ComfortCalibration.next(0.0, ComfortCalibration.Feeling.HOT), 0.0)
        assertTrue(ComfortCalibration.describe(-1.5).contains("怕冷"))
    }

    @Test fun weekendRuleFiresOnlyWhenEveryConditionHolds() {
        val rule = WeatherRules.templates().first()
        // Friday: the weekend is Saturday and Sunday.
        val friday = LocalDate.of(2026, 11, 27)
        assertEquals(listOf(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 29)), WeatherRules.days(WeatherRules.Period.WEEKEND, friday))
        assertEquals(listOf(LocalDate.of(2026, 11, 29)), WeatherRules.days(WeatherRules.Period.WEEKEND, LocalDate.of(2026, 11, 29)))
        val nice = weather(wind = 9.0)
        assertTrue(WeatherRules.evaluate(rule, nice, LocalDate.of(2026, 11, 25), zone).let { it.explanation.contains("這個週末") })
        val wedRule = rule.copy(period = WeatherRules.Period.TOMORROW)
        assertTrue(WeatherRules.evaluate(wedRule, nice, LocalDate.of(2026, 11, 25), zone).fires)
        val windy = weather(wind = 40.0)
        assertFalse(WeatherRules.evaluate(wedRule, windy, LocalDate.of(2026, 11, 25), zone).fires)
        assertEquals(3, WeatherRules.beaufort(15.0))
        assertEquals(6, WeatherRules.beaufort(45.0))
    }

    @Test fun familyIsWarnedOfASharpDropAndHeavyRain() {
        val p = FamilyCare.Person("1", "爸媽", "媽媽", "嘉義市", 23.48, 120.45)
        val tomorrow = LocalDate.of(2026, 11, 26)
        val cold = weather(temps = { if (it < 16) 22.0 else 11.0 }, daily = listOf(DailyWeather(tomorrow, 15.0, 10.0, 20, 3)))
        val c = FamilyCare.concerns(p, cold, emptyList(), now, zone)
        assertTrue(c.joinToString { it.title }, c.any { it.title.contains("驟降") })
        val rain = FamilyCare.concerns(p, weather(pop = { if (it == 5) 90 else 10 }), emptyList(), now, zone)
        assertTrue(rain.any { it.title.contains("大雨") })
        val warned = FamilyCare.concerns(p, weather(), listOf("豪雨特報"), now, zone)
        assertTrue(warned.first().title.contains("豪雨特報"))
        assertTrue(FamilyCare.careMessage(p, weather(), null).startsWith("媽媽，"))
    }
}
