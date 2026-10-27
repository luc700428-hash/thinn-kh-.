package io.atmos.weather.next

import org.junit.Assert.*
import org.junit.Test

class DockLiquidGeometryTest {
    @Test fun theTrailingLensKeepsTheFullSelectionSizeUnderTension() {
        val resting=DockLiquidGeometry.shape(360f,68f,1f,4,0f,0f)!!
        for(frame in 0..120) {
            val head=frame/40f
            val moving=DockLiquidGeometry.shape(360f,68f,1f,4,head,head-.8f)!!
            assertEquals(resting.width,moving.width,0f)
            assertEquals(resting.height,moving.height,0f)
            assertEquals(56f,moving.height,0f)
        }
    }
    @Test fun theBridgeThinsButTheTwoBodiesDoNotShrink() {
        val near=DockLiquidGeometry.shape(360f,68f,1f,4,1f,.5f)!!
        val stretched=DockLiquidGeometry.shape(360f,68f,1f,4,1.7f,.5f)!!
        assertTrue(near.connected && stretched.connected)
        assertTrue(stretched.neckHalfHeight<near.neckHalfHeight)
        assertEquals(near.width,stretched.width,0f)
        assertEquals(near.height,stretched.height,0f)
        assertFalse(DockLiquidGeometry.shape(360f,68f,1f,4,2.5f,0f)!!.connected)
    }
    @Test fun springOvershootNeverTakesTheLensOutsideTheDock() {
        val g=DockLiquidGeometry.shape(360f,68f,1f,4,-4f,8f)!!
        assertTrue(g.headX-g.width/2>=6f)
        assertTrue(g.tailX+g.width/2<=354f)
    }
    @Test fun densityScalesBothBodiesAndTheBridgeTogether() {
        val a=DockLiquidGeometry.shape(360f,68f,1f,4,1.8f,1f)!!
        val b=DockLiquidGeometry.shape(990f,187f,2.75f,4,1.8f,1f)!!
        assertEquals(a.width*2.75f,b.width,.001f)
        assertEquals(a.height*2.75f,b.height,.001f)
        assertEquals(a.neckHalfHeight*2.75f,b.neckHalfHeight,.001f)
    }
    @Test fun invalidOrUnmeasuredLayoutsAreNotPainted() {
        assertNull(DockLiquidGeometry.shape(0f,68f,1f,4,0f,0f))
        assertNull(DockLiquidGeometry.shape(360f,68f,1f,0,0f,0f))
        assertNull(DockLiquidGeometry.shape(360f,68f,1f,4,Float.NaN,0f))
    }
}
