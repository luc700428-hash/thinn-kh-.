package io.atmos.weather.earthquake

import io.atmos.weather.earthquake.SeismicReportPaging.Stage
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SeismicReportPagingTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")
    private fun r(id: String, minutesAgo: Long, m: Double) =
        EarthquakeReport(id, t0.minusSeconds(minutesAgo * 60), "花蓮縣近海", m, 10.0, 23.9, 121.6, "3級", "", "", "顯著有感")

    @Test fun mergeAddsOlderReportsWithoutRepeatingTheSameQuake() {
        val shown = listOf(r("cwa-1", 0, 4.5), r("cwa-2", 60, 3.8))
        // ExpTech names the same quakes differently (and times them a few seconds apart).
        val more = listOf(r("exp-a", 0, 4.6), r("exp-b", 61, 3.8), r("exp-c", 600, 5.1), r("cwa-2", 60, 3.8))
        val merged = SeismicReportPaging.merge(shown, more)
        assertEquals(listOf("cwa-1", "cwa-2", "exp-c"), merged.map { it.id })
        // Two different quakes a minute apart stay separate.
        assertEquals(2, SeismicReportPaging.merge(listOf(r("a", 0, 3.0)), listOf(r("b", 1, 4.5))).size)
    }

    @Test fun eachStepAddsMovesOnOrEndsSoItNeverRepeatsForever() {
        assertEquals(Stage.OFFICIAL, SeismicReportPaging.next(Stage.OFFICIAL, 12, 30))
        // Official service had nothing older (or ignored the time filter): continue with ExpTech.
        assertEquals(Stage.EXPTECH, SeismicReportPaging.next(Stage.OFFICIAL, 0, 30))
        assertEquals(Stage.EXPTECH, SeismicReportPaging.next(Stage.EXPTECH, 0, 70))
        assertEquals(Stage.EXPTECH, SeismicReportPaging.next(Stage.EXPTECH, 20, SeismicReportPaging.EXPTECH_MAX))
        assertEquals(Stage.DONE, SeismicReportPaging.next(Stage.EXPTECH, 0, SeismicReportPaging.EXPTECH_MAX))
        // From the first ExpTech size, ExpTech reaches its end within a bounded number of steps.
        var limit = 30; var steps = 0; var stage = Stage.EXPTECH
        while (stage != Stage.DONE) { limit = (limit + SeismicReportPaging.EXPTECH_STEP).coerceAtMost(SeismicReportPaging.EXPTECH_MAX); stage = SeismicReportPaging.next(stage, 0, limit); steps++ }
        assertTrue(steps <= 7)
    }
}
