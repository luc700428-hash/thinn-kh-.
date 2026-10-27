package io.atmos.weather.ui

import org.junit.Assert.*
import org.junit.Test

class BackdropSamplingTest {
    @Test fun onlyCardSizedPixelsAreCopiedFromTheFullScreenScene() {
        val sample=BackdropSampling.plan(1080,2400,960f,360f,-60f,-1500f)!!
        assertEquals(BackdropSample(0f,0f,960f,360f),sample)
    }
    @Test fun partiallyScrolledCardAllocatesOnlyItsVisibleIntersection() {
        val sample=BackdropSampling.plan(1080,2400,960f,1200f,-60f,600f,
            visibleTop=600f,visibleBottom=1200f)!!
        assertEquals(600,sample.height)
        assertEquals(600f,sample.top,0f)
    }
    @Test fun cardBelowTheViewportDoesNotAllocateOrDrawABackdrop() {
        assertNull(BackdropSampling.plan(1080,2400,960f,400f,-60f,-2500f))
        assertNull(BackdropSampling.plan(1080,2400,960f,400f,-60f,-100f,visibleBottom=0f))
    }
    @Test fun resizingAndFractionalPositionsStillRemainWithinTheSurface() {
        for(y in -2600..2600 step 17) {
            val sample=BackdropSampling.plan(691,1536,625.5f,375.25f,-32.5f,y+.25f) ?: continue
            assertTrue(sample.left>=0f && sample.top>=0f)
            assertTrue(sample.right<=625.5f && sample.bottom<=375.25f)
            assertTrue(sample.width<=626 && sample.height<=376)
        }
    }
    @Test fun invalidOrOversizedGpuInputsUseTheOrdinaryMaterial() {
        assertFalse(BackdropSampling.canCapture(0,2400))
        assertFalse(BackdropSampling.canCapture(1080,20000))
        assertFalse(BackdropSampling.canCapture(8192,8192))
        assertTrue(BackdropSampling.canCapture(1440,3200))
        assertNull(BackdropSampling.plan(1080,2400,Float.NaN,300f,0f,0f))
        assertNull(BackdropSampling.plan(1080,2400,300f,300f,Float.POSITIVE_INFINITY,0f))
    }
}
