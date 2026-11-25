package io.atmos.weather.earthquake

import org.junit.Assert.*
import org.junit.Test

class SeismicCountyEstimateTest {
    /** 2026-11-25 16:47, M5.5, 21.88 N 121.46 E, 16.7 km: CWA observed 臺東 4, 屏東/高雄 2, 南投 1, no 臺北. */
    @Test fun estimatesMatchTheTaitungOffshoreQuake() {
        val v = SeismicCountyEstimate.estimate(5.5, 16.7, 21.88, 121.46)
        assertTrue(v.estimated)
        assertEquals("4", v.values["臺東縣"])
        assertTrue(v.values["屏東縣"]!!.toInt() in 2..3)
        assertTrue(v.values["高雄市"]!!.toInt() in 1..2)
        assertTrue(v.values["南投縣"]!!.toInt() in 1..2)
        assertNull(v.values["臺北市"])
        assertEquals("推算 4 級", SeismicCountyEstimate.label(v, "臺東縣", "4"))
    }

    @Test fun sourceValuesWinAndEstimatesFillOnlyWhenMissing() {
        val base = EewIncident("x", 1, "test", 0L, "臺東外海", 5.5, 16.7, 21.88, 121.46, false, false)
        val withSource = base.copy(localIntensityByCounty = mapOf("臺東縣" to "3"))
        assertFalse(SeismicCountyEstimate.view(withSource).estimated)
        assertEquals(mapOf("臺東縣" to "3"), SeismicCountyEstimate.view(withSource).values)
        val filled = SeismicCountyEstimate.view(base)
        assertTrue(filled.estimated); assertEquals("4", SeismicCountyEstimate.forPlace(filled, "臺東縣臺東市"))
        assertTrue(SeismicCountyEstimate.estimate(null, 10.0, 23.0, 121.0).values.isEmpty())
        // A great quake: PGA saturates, so the label says it may be stronger.
        val big = SeismicCountyEstimate.estimate(7.3, 8.0, 23.85, 120.82)
        assertTrue("南投縣" in big.atLeast); assertEquals("推算 4 級 以上", SeismicCountyEstimate.label(big, "南投縣", "4"))
    }
}
