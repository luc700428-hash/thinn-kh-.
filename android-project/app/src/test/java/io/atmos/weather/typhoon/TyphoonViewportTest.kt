package io.atmos.weather.typhoon

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TyphoonViewportTest {
    @Test fun isotropicAxisScale() {
        val viewport=TyphoonViewport.forPoints(listOf(23.5 to 121.0,27.0 to 129.0),420f,230f,20f)
        val at=viewport.project(121.0,23.5)
        val east=viewport.project(122.0,23.5)
        val north=viewport.project(121.0,24.5)
        assertEquals(viewport.cosCenter.toFloat(),(east.first-at.first)/(at.second-north.second),0.005f)
    }
    @Test fun pointsFitInView() {
        val positions=listOf(21.1 to 118.0, 28.2 to 136.0)
        val viewport=TyphoonViewport.forPoints(positions,420f,230f,20f)
        positions.forEach { (lat,lon)->
            val (x,y)=viewport.project(lon,lat)
            assertTrue(x in 0f..420f)
            assertTrue(y in 0f..230f)
        }
    }
    @Test fun emptyForecastNoCrash() {
        val viewport=TyphoonViewport.forPoints(emptyList(),300f,200f,16f)
        assertTrue(viewport.pxPerKm>0)
        val (x,y)=viewport.project(121.0,24.0)
        assertTrue(x in 0f..300f)
        assertTrue(y in 0f..200f)
    }
    @Test fun taiwanCameraKeepsAllFourIslandGroupsVisible() {
        for((width,height) in listOf(320f to 430f,430f to 315f)) {
            val view=TyphoonViewport.forTaiwan(width,height,20f)
            listOf(121.0 to 23.65,119.57 to 23.57,118.38 to 24.44,119.95 to 26.17).forEach {(lon,lat) ->
                val (x,y)=view.project(lon,lat)
                assertTrue(x in 0f..width && y in 0f..height)
            }
            val west=view.project(120.0,24.0);val east=view.project(122.0,24.0)
            assertTrue(east.first-west.first>width*.15f)
        }
    }
}
