package io.atmos.weather.next

import org.junit.Assert.assertEquals
import org.junit.Test

class DockScrubGeometryTest {
    private val dock=DockScrubGeometry(360f,6f,4)
    @Test fun eachVisibleCenterSelectsItsOwnDestination() {
        listOf(49.5f,136.5f,223.5f,310.5f).forEachIndexed {index,x ->
            assertEquals(index,dock.nearest(x))
        }
    }
    @Test fun crossingSlotBoundariesSelectsTheNearestCenter() {
        assertEquals(0,dock.nearest(92.99f));assertEquals(1,dock.nearest(93f))
        assertEquals(1,dock.nearest(179.99f));assertEquals(2,dock.nearest(180f))
        assertEquals(2,dock.nearest(266.99f));assertEquals(3,dock.nearest(267f))
    }
    @Test fun draggingOutsideTheDockClampsToEndDestinations() {
        assertEquals(49.5f,dock.clamp(-200f),.001f)
        assertEquals(310.5f,dock.clamp(800f),.001f)
        assertEquals(0,dock.nearest(-200f));assertEquals(3,dock.nearest(800f))
    }
    @Test fun densityChangesDoNotChangeTheChosenDestination() {
        val scaled=DockScrubGeometry(990f,16.5f,4)
        for(x in listOf(-1f,49.5f,100f,179f,180f,240f,330f,500f))
            assertEquals(dock.nearest(x),scaled.nearest(x*2.75f))
    }
}
