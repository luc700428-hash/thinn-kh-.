package io.atmos.weather.radar

import org.junit.Assert.*
import org.junit.Test

class RadarPlaybackTest {
    @Test fun fractionalCursorKeepsRealObservationEndpointsAndTime() {
        val frames=(0..3).map{RadarFrame(it*600L,"/v2/radar/$it")}
        val sample=RadarPlayback.sample(1.375f,frames.size)
        assertEquals(1,sample.lower);assertEquals(2,sample.upper);assertEquals(.375f,sample.fraction,0f)
        assertEquals(825L,sample.time(frames))
        val end=RadarPlayback.sample(3f,4);assertEquals(3,end.lower);assertEquals(3,end.upper);assertEquals(0f,end.fraction,0f)
    }
    @Test fun cursorIsBoundedAtEmptySingleAndNonFiniteInputs() {
        for(n in listOf(0,1,13))for(p in listOf(Float.NaN,Float.NEGATIVE_INFINITY,-100f,0f,100f)) {
            val s=RadarPlayback.sample(p,n)
            assertTrue(s.lower in 0..(n-1).coerceAtLeast(0));assertTrue(s.upper>=s.lower)
            assertTrue(s.fraction.isFinite()&&s.fraction in 0f..1f)
        }
    }
    @Test fun elapsedTimeHasTheSamePlaybackSpeedAt60Through144Hz() {
        for(rate in listOf(60,90,120,144)) {
            var position=0f
            repeat(rate){position=RadarPlayback.advance(position,1_000_000_000L/rate,13)}
            assertEquals("Refresh rate must not change weather playback speed",(1000.0/1100).toFloat(),position,.001f)
        }
        assertTrue(RadarPlayback.advance(0f,10_000_000_000L,13)<.1f)
        assertEquals(12f,RadarPlayback.advance(12f,100_000_000,13),0f)
    }
    @Test fun preloadStartsWithTheCurrentPairAndVisitsEveryFrameExactlyOnce() {
        for(p in listOf(0f,5.4f,12f)) {
            val order=RadarPlayback.loadOrder(13,p)
            assertEquals((0..12).toSet(),order.toSet());assertEquals(13,order.size)
            val pair=RadarPlayback.sample(p,13)
            assertEquals(pair.lower,order.first())
            if(pair.lower!=pair.upper)assertEquals(pair.upper,order[1])
        }
    }
    @Test fun skippedUnavailableObservationsRemainNavigable() {
        assertEquals(3f,RadarPlayback.step(0f,1,listOf(0,3,6)),0f)
        assertEquals(3f,RadarPlayback.step(3.8f,-1,listOf(0,3,6)),0f)
        assertEquals(6f,RadarPlayback.step(3.8f,1,listOf(0,3,6)),0f)
        assertEquals(6f,RadarPlayback.step(6f,1,listOf(0,3,6)),0f)
    }
    @Test fun residentSourceTileBudgetSurvivesWideWindowsAndWorldWrap() {
        for(lon in listOf(-179.99,0.0,120.95,179.99))for(zoom in listOf(2.0,6.5,7.0,16.0)) {
            val tiles=RadarPlayback.tiles(RadarCamera(24.0,lon,zoom),3500f,2200f,1f)
            assertTrue(tiles.isNotEmpty());assertTrue(tiles.size<=RadarTilePolicy.MAX_VISIBLE_TILES)
            assertTrue(tiles.distinctBy{Triple(it.z,it.x,it.y)}.size<=4)
            assertTrue(tiles.all{it.z<=7&&RadarTilePolicy.valid(it,7)})
        }
    }
    @Test fun requestBudgetCountsMissesOnlyAndRespectsTheRollingMinute() {
        val budget=RadarRequestBudget()
        repeat(90){assertEquals(0,budget.reserve(1000))}
        assertEquals(60000,budget.reserve(1000));assertEquals(1,budget.reserve(60999));assertEquals(0,budget.reserve(61000))
    }
    @Test fun gapsAndLoopRestartsNeverAcquireInventedMotion() {
        val a=RadarFrame(1000,"a")
        assertTrue(RadarPlayback.canInterpolate(a,RadarFrame(1600,"b")))
        assertFalse(RadarPlayback.canInterpolate(a,RadarFrame(1000,"b")))
        assertFalse(RadarPlayback.canInterpolate(a,RadarFrame(3000,"b")))
        assertFalse(RadarPlayback.canInterpolate(a,RadarFrame(400,"b")))
    }
}
