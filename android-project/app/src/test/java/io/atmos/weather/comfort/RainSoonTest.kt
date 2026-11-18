package io.atmos.weather.comfort

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Robolectric: org.json is only a stub in plain JVM tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RainSoonTest {
    private val now = Instant.parse("2026-11-18T06:05:00Z")
    private fun json(mm: List<Double>, chance: List<Int> = mm.map { if (it > 0) 70 else 10 }): String {
        val start = java.time.LocalDateTime.parse("2026-11-18T06:00")
        val times = mm.indices.joinToString(",") { "\"${start.plusMinutes(it * 15L)}\"" }
        return """{"minutely_15":{"time":[$times],"precipitation":[${mm.joinToString(",")}],"precipitation_probability":[${chance.joinToString(",")}]}}"""
    }

    @Test fun parsesFifteenMinuteSlotsInUtc() {
        val slots = RainSoon.parse(json(listOf(0.0, 0.0, 0.6, 1.2)))
        assertEquals(4, slots.size)
        assertEquals(Instant.parse("2026-11-18T06:00:00Z"), slots[0].at)
        assertEquals(Instant.parse("2026-11-18T06:30:00Z"), slots[2].at)
        assertEquals(70, slots[2].chance)
        assertTrue(RainSoon.parse("{}").isEmpty())
        assertTrue(RainSoon.parse("""{"minutely_15":{"time":["x"],"precipitation":[1]}}""").isEmpty())
    }

    @Test fun dryNowAndWetWithinTheHourSaysWhen() {
        val a = RainSoon.decide(RainSoon.parse(json(listOf(0.0, 0.0, 0.6, 1.2, 0.0))), now)!!
        assertEquals(25, a.minutes)
        assertEquals("小雨", a.strength)
        val t = a.text()
        assertEquals("雨來了", t.title)
        assertTrue(t.summary.contains("大約 25 分鐘後"))
        assertTrue(t.body.contains("帶傘"))
    }

    @Test fun staysQuietWhenAlreadyRainingFarOffOrStale() {
        assertNull("already raining", RainSoon.decide(RainSoon.parse(json(listOf(0.8, 0.9, 1.0))), now))
        assertNull("dry", RainSoon.decide(RainSoon.parse(json(listOf(0.0, 0.0, 0.0, 0.0))), now))
        assertNull("too far", RainSoon.decide(RainSoon.parse(json(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.5))), now))
        assertNull("stale", RainSoon.decide(RainSoon.parse(json(listOf(0.0, 0.5))), now.plusSeconds(3 * 3600)))
    }

    @Test fun heavyRainIsNamed() {
        val a = RainSoon.decide(RainSoon.parse(json(listOf(0.0, 5.0))), now)!!
        assertEquals("大雨", a.strength)
        assertTrue(a.text().summary.startsWith("大約 10 分鐘後") || a.text().summary.startsWith("幾分鐘內"))
    }
}
