package io.atmos.weather.life

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DisasterLogicTest {
    private fun resource(name: String) = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }

    @Test fun realNcdrFeedParsesEveryEntryWithCategoryAuthorAndExpiry() {
        val alerts = NcdrFeed.parse(resource("ncdr_feed_20261126.xml"))
        assertEquals(49, alerts.size)
        val quake = alerts.first { it.id == "CWA-EQ115081-2026-1125-164717" }
        assertEquals("地震", quake.category)
        assertEquals("中央氣象署", quake.author)
        assertTrue(quake.summary.contains("規模5.5"))
        assertEquals(LocalDateTime.of(2026, 11, 25, 17, 52), quake.expires!!.atZone(ZoneId.of("Asia/Taipei")).toLocalDateTime())
        assertTrue(alerts.any { it.category == "水庫放流" && it.author == "水利署" })
        // Newest first.
        assertTrue(alerts.zipWithNext().all { (a, b) -> !a.updated.isBefore(b.updated) })
        val wind = alerts.first { it.id == "CWA-Weather_strong-wind_202611252229001" }
        assertTrue(NcdrFeed.mentions(wind, "澎湖縣"))
        assertFalse(NcdrFeed.mentions(wind, "嘉義市"))
    }

    @Test fun capTimesReadTheChineseMorningAndAfternoon() {
        val z = ZoneId.of("Asia/Taipei")
        assertEquals(LocalDateTime.of(2026, 11, 26, 4, 0), NcdrFeed.capTime("2026/11/26 上午 04:00:00")!!.atZone(z).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 11, 25, 22, 27), NcdrFeed.capTime("2026/11/25 下午 10:27:00")!!.atZone(z).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0), NcdrFeed.capTime("2026/11/26 上午 12:00:00")!!.atZone(z).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 11, 26, 12, 30), NcdrFeed.capTime("2026/11/26 下午 12:30:00")!!.atZone(z).toLocalDateTime())
    }

    @Test fun closuresAreReadPerCountyFromAMultiCountyAnnouncement() {
        val now = Instant.parse("2026-08-01T12:00:00Z")
        val xml = """<feed><entry><id>DGPA-1</id><title>停班停課</title><updated>2026-08-01T19:30:00+08:00</updated>
            <author><name>行政院人事行政總處</name></author><link rel="alternate" href="https://example/cap" />
            <summary type="html">臺北市:明天停止上班、停止上課。新北市:明天停止上班、停止上課。嘉義市:明天照常上班、照常上課。</summary>
            <category term="停班停課" /><cap:msgType>Alert</cap:msgType></entry></feed>"""
        val st = WorkSchoolClosure.statuses(NcdrFeed.parse(xml), now)
        assertEquals(22, st.size)
        assertTrue(st.first { it.county == "臺北市" }.closed)
        assertTrue(st.first { it.county == "新北市" }.text.contains("停止上班"))
        val chiayi = st.first { it.county == "嘉義市" }
        assertFalse(chiayi.closed); assertTrue(chiayi.text.contains("照常"))
        assertEquals("尚未發布停班停課訊息", st.first { it.county == "花蓮縣" }.text)
    }

    @Test fun reservoirPercentIsStorageOverEffectiveCapacityLatestReadingWins() {
        val infos = listOf(Reservoirs.Info("10201", "石門水庫", "臺灣北區", 20441.1), Reservoirs.Info("20503", "集集攔河堰", "臺灣中區", 653.2),
            Reservoirs.Info("30502", "曾文水庫", "臺灣南區", 50479.0))
        val storage = listOf(Triple("10201", "20000", "2026-11-26T07:00:00"), Triple("10201", "20209.91", "2026-11-26T08:00:00"),
            Triple("30502", "", "2026-11-26T08:00:00"), Triple("20503", "0.0", "2026-11-26T08:00:00"))
        val levels = Reservoirs.join(infos, storage)
        assertEquals(listOf("石門水庫"), levels.map { it.info.name })
        assertEquals(98.9, levels.first().percent, .05)
        assertEquals("充足", Reservoirs.word(levels.first().percent))
        assertEquals(1234.5, Reservoirs.number("1,234.50")!!, 1e-9)
    }

    @Test fun bundledSheltersCoverTaiwanAndHoldNoPersonalContacts() {
        val file = listOf(File("src/main/assets/shelters/shelters.csv"), File("app/src/main/assets/shelters/shelters.csv")).first { it.exists() }
        val text = file.readText()
        assertFalse("Manager names and phones must not be bundled", text.lineSequence().first().contains("管理人"))
        val all = Shelters.parse(text)
        assertTrue(all.size > 5000)
        val near = Shelters.nearest(all, 25.0330, 121.5654)
        assertEquals(12, near.size)
        assertTrue(near.first().km < 2.0)
        assertTrue(near.zipWithNext().all { (a, b) -> a.km <= b.km })
        assertTrue(Shelters.nearest(all, 23.48, 120.45, hazard = "土石流").all { it.shelter.hazards.contains("土石流") })
        assertEquals(listOf("a", "b,c", "d\"e"), Shelters.fields("a,\"b,c\",\"d\"\"e\""))
        assertEquals("東", Shelters.bearingWord(25.0, 121.0, 25.0, 121.1))
    }

    @Test fun kitWarnsAboutWhatExpiresWithinAMonth() {
        val today = LocalDate.of(2026, 11, 26)
        val items = DisasterKit.defaults().map {
            when (it.id) { "water" -> it.copy(expires = today.plusDays(10)); "food" -> it.copy(expires = today.plusDays(90)); else -> it }
        }
        assertEquals(listOf("water"), DisasterKit.expiring(items, today).map { it.id })
        assertEquals("臺北市", LifeSources.county("台北市大安區"))
        assertEquals("嘉義市", LifeSources.county("嘉義市東區"))
    }
}
