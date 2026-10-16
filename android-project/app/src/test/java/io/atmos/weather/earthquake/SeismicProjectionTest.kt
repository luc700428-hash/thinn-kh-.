package io.atmos.weather.earthquake

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SeismicProjectionTest {
    @Test fun preservesPhysicalAspectRegardlessOfCanvasShape() {
        val lat24=SeismicGeo.screenY(24.0,380f,650f)
        val lat25=SeismicGeo.screenY(25.0,380f,650f)
        val lon120=SeismicGeo.screenX(120.0,380f,650f)
        val lon121=SeismicGeo.screenX(121.0,380f,650f)
        val ratio=abs(lon121-lon120)/abs(lat25-lat24)
        assertEquals(0.9135,ratio.toDouble(),0.03)
    }
    @Test fun allNamedSmallIslandsRemainInMap() {
        for ((_,point) in SeismicGeo.smallerIslands) {
            assertTrue(SeismicGeo.visible(point.second,point.first))
            assertTrue(SeismicGeo.screenX(point.first,380f,650f) in 0f..380f)
            assertTrue(SeismicGeo.screenY(point.second,380f,650f) in 0f..650f)
        }
    }
}
