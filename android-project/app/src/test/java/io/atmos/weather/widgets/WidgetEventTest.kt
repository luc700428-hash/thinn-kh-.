package io.atmos.weather.widgets

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** Robolectric: org.json is only a stub in plain JVM tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WidgetEventTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val now = Instant.parse("2026-11-18T02:00:00Z").toEpochMilli()   // 10:00 in Taipei
    private fun one(t: String, until: Long) = JSONObject().put("t", t).put("d", "細節").put("u", until).toString()
    private fun warnings(vararg w: Triple<String, Long, Long>) = JSONArray().apply { w.forEach { put(JSONObject().put("t", it.first).put("s", it.second).put("e", it.third)) } }.toString()

    @Test fun aLiveWarningComesBeforeEverythingAndEndsByItself() {
        val w = warnings(Triple("陸上強風特報", now - 3600_000, now + 3600_000))
        val e = WidgetEvent.pick(one("地震速報 · 規模 6.1", now + 60_000), w, one("M 4.5", now + 60_000), now, zone)!!
        assertEquals(WidgetEvent.Kind.EEW, e.kind)
        val later = WidgetEvent.pick(one("地震速報", now + 60_000), w, null, now + 120_000, zone)!!
        assertEquals(WidgetEvent.Kind.WARNING, later.kind)
        assertEquals("有效至 11:00", later.detail)
    }

    @Test fun upcomingWarningsAreMarkedAndOldOnesDisappear() {
        val soon = WidgetEvent.pick(null, warnings(Triple("陸上強風特報", now + 2 * 3600_000, now + 10 * 3600_000)), null, now, zone)!!
        assertTrue(soon.detail.startsWith("預告 · 12:00 起生效"))
        assertNull(WidgetEvent.pick(null, warnings(Triple("大雨特報", now - 7200_000, now - 1)), null, now, zone))
        assertNull("too far ahead", WidgetEvent.pick(null, warnings(Triple("大雨特報", now + 13 * 3600_000, now + 20 * 3600_000)), null, now, zone))
        val two = WidgetEvent.pick(null, warnings(Triple("大雨特報", now - 1, now + 9_000), Triple("陸上強風特報", now - 1, now + 9_000)), null, now, zone)!!
        assertTrue(two.detail.endsWith("另有 1 則"))
    }

    @Test fun aQuakeReportShowsForHalfAnHourWhenNothingElseIsOn() {
        assertEquals(WidgetEvent.Kind.QUAKE, WidgetEvent.pick(null, null, one("M 4.5 · 花蓮縣", now + 1), now, zone)?.kind)
        assertNull(WidgetEvent.pick(null, "garbage", one("M 4.5", now - 1), now, zone))
        assertEquals(30 * 60_000L, WidgetEvent.QUAKE_MS)
    }
}
