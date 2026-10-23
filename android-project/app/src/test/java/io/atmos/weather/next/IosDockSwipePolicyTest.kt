package io.atmos.weather.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IosDockSwipePolicyTest {
    @Test fun boundedFiveTabPositions() {
        assertEquals(0, IosDockSwipePolicy.indexAt(-10f, 500f, 5))
        assertEquals(4, IosDockSwipePolicy.indexAt(499f, 500f, 5))
        assertEquals(4, IosDockSwipePolicy.indexAt(900f, 500f, 5))
        assertNull(IosDockSwipePolicy.indexAt(Float.NaN, 500f, 5))
    }
    @Test fun scrubThresholdAndTwoDirections() {
        assertNull(IosDockSwipePolicy.target(40f, 46f, 500f, 5, 14f))
        assertEquals(3, IosDockSwipePolicy.target(40f, 340f, 500f, 5, 14f))
        assertEquals(1, IosDockSwipePolicy.target(430f, 100f, 500f, 5, 14f))
    }
}
