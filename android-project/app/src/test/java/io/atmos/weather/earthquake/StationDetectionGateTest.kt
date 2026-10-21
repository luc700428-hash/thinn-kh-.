package io.atmos.weather.earthquake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class StationDetectionGateTest {
    private val weak=StationDetectionGate.Reading("weak",1.0,0.4)
    private val strong=StationDetectionGate.Reading("strong",4.5,250.0)
    private val enabled=setOf(StationDetectionGate.Threshold.DETECT_WEAK,StationDetectionGate.Threshold.GAL_STRONG)

    @Test fun strongestStationWinsRegardlessOfDataOrder() {
        for (readings in listOf(listOf(weak,strong),listOf(strong,weak))) {
            val gate=StationDetectionGate()
            assertNull(gate.observe(readings,1_000L,enabled))
            assertNull(gate.observe(readings,2_000L,enabled))
            val triggered=gate.observe(readings,3_000L,enabled)
            assertNotNull(triggered)
            assertEquals(StationDetectionGate.Threshold.GAL_STRONG,triggered!!.threshold)
            assertEquals("strong",triggered.stationId)
        }
    }
    @Test fun replayedTimestampDoesNotCountAsConsecutiveFrame() {
        val gate=StationDetectionGate()
        val readings=listOf(strong)
        assertNull(gate.observe(readings,1_000L,enabled))
        assertNull(gate.observe(readings,1_000L,enabled))
        assertNull(gate.observe(readings,2_000L,enabled))
        assertNotNull(gate.observe(readings,3_000L,enabled))
    }
    @Test fun requiresSameStationAndThreeFreshFrames() {
        val gate=StationDetectionGate()
        assertNull(gate.observe(listOf(strong),1_000L,enabled))
        assertNull(gate.observe(listOf(weak),2_000L,enabled))
        assertNull(gate.observe(listOf(strong),3_000L,enabled))
        assertNull(gate.observe(listOf(strong),4_000L,enabled))
        assertEquals("strong",gate.observe(listOf(strong),5_000L,enabled)?.stationId)
    }
    @Test fun cooldownPreventsRepeatNotification() {
        val gate=StationDetectionGate()
        val readings=listOf(strong)
        gate.observe(readings,1_000L,enabled)
        gate.observe(readings,2_000L,enabled)
        assertNotNull(gate.observe(readings,3_000L,enabled))
        gate.observe(readings,4_000L,enabled)
        gate.observe(readings,5_000L,enabled)
        assertNull(gate.observe(readings,6_000L,enabled))
    }
}
