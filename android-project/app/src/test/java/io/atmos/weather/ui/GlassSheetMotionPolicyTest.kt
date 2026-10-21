package io.atmos.weather.ui

import org.junit.Assert.*
import org.junit.Test

class GlassSheetMotionPolicyTest {
    @Test fun threePositionsAdaptToActualHeight() {
        assertEquals(0f, GlassSheetMotionPolicy.offset(GlassSheetMotionPolicy.Anchor.FULL, 600f), .001f)
        assertEquals(276f, GlassSheetMotionPolicy.offset(GlassSheetMotionPolicy.Anchor.HALF, 600f), .001f)
        assertEquals(600f, GlassSheetMotionPolicy.offset(GlassSheetMotionPolicy.Anchor.CLOSED, 600f), .001f)
    }
    @Test fun downDragHasHalfAndCloseThresholds() {
        assertEquals(GlassSheetMotionPolicy.Anchor.FULL, GlassSheetMotionPolicy.release(80f, 600f))
        assertEquals(GlassSheetMotionPolicy.Anchor.HALF, GlassSheetMotionPolicy.release(240f, 600f))
        assertEquals(GlassSheetMotionPolicy.Anchor.CLOSED, GlassSheetMotionPolicy.release(540f, 600f))
    }
    @Test fun upDragReopensAndCancelNeverDismisses() {
        assertEquals(GlassSheetMotionPolicy.Anchor.FULL, GlassSheetMotionPolicy.release(0f, 600f))
        assertEquals(GlassSheetMotionPolicy.Anchor.HALF, GlassSheetMotionPolicy.cancel(580f, 600f))
    }
    @Test fun invalidInputsCannotEscapeViewport() {
        assertEquals(0f, GlassSheetMotionPolicy.boundedDrag(Float.NaN, 600f), .001f)
        assertEquals(0f, GlassSheetMotionPolicy.boundedDrag(-400f, 600f), .001f)
        assertEquals(600f, GlassSheetMotionPolicy.boundedDrag(800f, 600f), .001f)
    }
}
