package io.atmos.weather.ui

import org.junit.Assert.*
import org.junit.Test

class BubbleSliderGeometryTest {
    @Test fun pointerAndAccessibilityUseTheSameSteppedRange() {
        assertEquals(10f,BubbleSliderGeometry.quantize(-100f,10f,20f,3),0f)
        assertEquals(12.5f,BubbleSliderGeometry.quantize(13f,10f,20f,3),0f)
        assertEquals(20f,BubbleSliderGeometry.quantize(100f,10f,20f,3),0f)
        assertEquals(.3f,BubbleSliderGeometry.fraction(13f,10f,20f),.0001f)
    }
    @Test fun corruptedStoredValuesStayFinite() {
        for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            assertEquals(0f,BubbleSliderGeometry.fraction(v,0f,1f),0f)
            assertEquals(1f,BubbleSliderGeometry.quantize(v,1f,2f,0),0f)
        }
        assertEquals(0f,BubbleSliderGeometry.fraction(1f,2f,1f),0f)
    }
    @Test fun bothLensesRemainInsideTheControlEvenWithSpringOvershoot() {
        for(f in -20..20)for(t in -20..20) {
            val g=BubbleSliderGeometry.lens(360f,48f,1f,f/10f,t/10f,1f,false)!!
            assertTrue(g.head-g.width/2>=0f && g.head+g.width/2<=360f)
            assertTrue(g.tail-g.width/2>=0f && g.tail+g.width/2<=360f)
            assertTrue(g.y-g.height/2>=0f && g.y+g.height/2<=48f)
        }
    }
    @Test fun fastDraggingCreatesABoundedNeckWithoutShrinkingTheBody() {
        val resting=BubbleSliderGeometry.lens(360f,48f,1f,.8f,.8f,0f,false)!!
        val pulled=BubbleSliderGeometry.lens(360f,48f,1f,.8f,.1f,0f,false)!!
        assertEquals(resting.width,pulled.width,0f)
        assertEquals(resting.height,pulled.height,0f)
        assertTrue(pulled.distance<=pulled.width*1.4f+.01f)
        assertTrue(pulled.neck<resting.neck)
    }
    @Test fun reducedMotionRemovesTheTailAndDeformation() {
        val g=BubbleSliderGeometry.lens(360f,48f,1f,.9f,.1f,1f,true)!!
        assertEquals(g.head,g.tail,0f)
        assertEquals(56f,g.width,0f)
        assertEquals(34f,g.height,0f)
    }
    @Test fun layoutAndDensityChangesPreserveTheShape() {
        val a=BubbleSliderGeometry.lens(360f,48f,1f,.6f,.4f,.5f,false)!!
        val b=BubbleSliderGeometry.lens(1080f,144f,3f,.6f,.4f,.5f,false)!!
        assertEquals(a.head*3,b.head,.001f);assertEquals(a.tail*3,b.tail,.001f)
        assertNull(BubbleSliderGeometry.lens(0f,48f,1f,.5f,.4f,0f,false))
        assertNull(BubbleSliderGeometry.lens(360f,48f,1f,Float.NaN,.4f,0f,false))
    }
}
