package io.atmos.weather.typhoon

import org.junit.Assert.*
import org.junit.Test

class VerifiedTrackPlannerTest {
    private fun point(storm:String,lat:Double,lon:Double,time:String,forecast:Boolean=true)=
        DatedTyphoonPosition(storm,lat,lon,forecast,time)

    @Test fun timeZoneNormalizedToInstant() {
        assertEquals(VerifiedTrackPlanner.parseTime("2026-10-17T00:00:00Z"),
            VerifiedTrackPlanner.parseTime("2026-10-17 08:00:00"))
    }
    @Test fun neverMixStorms() {
        val values=listOf(point("A",19.0,124.0,"2026-10-17 08:00"),
            point("A",19.5,123.5,"2026-10-17 14:00"),
            point("B",20.0,122.0,"2026-10-17 20:00"))
        val segments=VerifiedTrackPlanner.verifiedForecastSegments(values)
        assertEquals(1,segments.size)
        assertEquals(2,segments.single().size)
    }
    @Test fun cannotDrawUnlabeledOrObservedTracks() {
        val data=listOf(point("",19.0,124.0,"2026-10-17 08:00"),
            point("",19.5,123.5,"2026-10-17 14:00"),
            point("A",18.0,126.0,"2026-10-17 08:00",false),
            point("A",18.5,125.5,"2026-10-17 14:00",false))
        assertTrue(VerifiedTrackPlanner.verifiedForecastSegments(data).isEmpty())
    }
    @Test fun rejectsImplausiblePointJump() {
        val a=point("A",19.0,124.0,"2026-10-17 08:00")
        val b=point("A",40.0,155.0,"2026-10-17 14:00")
        assertTrue(VerifiedTrackPlanner.verifiedForecastSegments(listOf(a,b)).isEmpty())
    }
    @Test fun rejectsMissingOrDuplicatedTimestamps() {
        val a=point("A",19.0,124.0,"??")
        val b=point("A",19.5,123.5,"2026-10-17 14:00")
        assertTrue(VerifiedTrackPlanner.verifiedForecastSegments(listOf(a,b)).isEmpty())
    }
}
