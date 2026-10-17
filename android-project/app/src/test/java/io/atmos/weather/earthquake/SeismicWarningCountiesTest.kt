package io.atmos.weather.earthquake

import org.junit.Assert.*
import org.junit.Test

class SeismicWarningCountiesTest {
    @Test fun missingProviderDataMustNotBecomeFakeIntensity() {
        val result=SeismicWarningCounties.allFromProvider(emptyMap())
        assertEquals(22,result.size)
        assertTrue(result.all { it.intensity == null })
    }
    @Test fun rejectsInvalidIntensityAndNormalizesCounty() {
        val real=SeismicWarningCounties.fromProvider(mapOf(
            "台北市" to "3", "嘉義縣" to "5弱", "高雄市" to "10", "臺中市" to "abc"))
        assertEquals(2,real.size)
        assertTrue(real.any { it.county == "臺北市" && it.intensity == "3" })
        assertTrue(real.any { it.county == "嘉義縣" && it.intensity == "5弱" })
    }
    @Test fun wavefrontIsIllustrativeAndNeedsOriginTime() {
        assertNull(SeismicWavePhysics.wavefront(0L,30000L,10.0))
        val waves=SeismicWavePhysics.wavefront(1000L,30000L,12.0)!!
        assertTrue(waves.pSurfaceRadiusKm>waves.sSurfaceRadiusKm)
    }
}
