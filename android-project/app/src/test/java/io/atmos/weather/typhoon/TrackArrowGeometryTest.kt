package io.atmos.weather.typhoon
import org.junit.Assert.*
import org.junit.Test
class TrackArrowGeometryTest {
    @Test fun arrowPointsInActualSegmentDirection() {
        val east=TrackArrowGeometry.head(0f,0f,100f,0f,4f)
        assertTrue(east[0].x>east[1].x && east[0].x>east[2].x)
        val north=TrackArrowGeometry.head(0f,100f,0f,0f,4f)
        assertTrue(north[0].y<north[1].y && north[0].y<north[2].y)
    }
    @Test fun tinySegmentsAndInvalidCoordinatesCannotProducePilesOfArrows() {
        assertTrue(TrackArrowGeometry.head(1f,1f,1f,1f,4f).isEmpty())
        assertTrue(TrackArrowGeometry.head(Float.NaN,1f,100f,1f,4f).isEmpty())
    }
}
