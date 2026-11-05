package io.atmos.weather.earthquake
import org.junit.Assert.*
import org.junit.Test

class SeismicPaneGeometryTest {
    @Test fun wideTabletsReserveReadableMapAndInspectorPanes() {
        for(critical in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            val plan=SeismicPaneGeometry.plan(1200f,720f,scale,critical)
            assertTrue(plan.sideBySide);assertTrue(plan.inspectorWidth>=300f)
            assertTrue(1200-plan.inspectorWidth-52>=360);assertTrue(plan.mapHeight>310f)
        }
    }
    @Test fun aSplitWindowUsesItsUsableWidthRatherThanTheDeviceName() {
        for(width in listOf(320f,400f,500f,600f)) {
            assertFalse(SeismicPaneGeometry.plan(width,900f,1f).sideBySide)
            assertFalse(SeismicPaneGeometry.plan(width,900f,1f,true).sideBySide)
        }
    }
    @Test fun largerTextStacksBeforeSqueezingCriticalInformation() {
        assertTrue(SeismicPaneGeometry.plan(780f,800f,1f).sideBySide)
        assertFalse(SeismicPaneGeometry.plan(780f,800f,1.5f,true).sideBySide)
    }
    @Test fun shortLandscapeAndTabletopPanesKeepTheMapReachable() {
        assertTrue(SeismicPaneGeometry.plan(1100f,300f,1f).sideBySide)
        assertFalse(SeismicPaneGeometry.plan(1100f,180f,1f).sideBySide)
        for(height in listOf(160f,260f,400f))assertTrue(SeismicPaneGeometry.plan(450f,height,1.5f).mapHeight in 180f..720f)
    }
    @Test fun invalidWindowMeasurementsCannotProduceInvalidLayoutConstraints() {
        for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,-10f,0f)) {
            val plan=SeismicPaneGeometry.plan(v,v,v)
            assertTrue(plan.mapHeight.isFinite());assertTrue(plan.inspectorWidth.isFinite());assertTrue(plan.mapHeight>0);assertFalse(plan.sideBySide)
        }
    }
}
