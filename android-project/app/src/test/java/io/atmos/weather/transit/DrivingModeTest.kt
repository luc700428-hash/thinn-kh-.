package io.atmos.weather.transit

import org.junit.Assert.*
import org.junit.Test

class DrivingModeTest {
    private fun drive(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, kmh: Float, n: Int = 6) = (0 until n).map { i ->
        val t = i / (n - 1.0)
        RailMode.Sample(fromLat + (toLat - fromLat) * t, fromLon + (toLon - fromLon) * t, kmh / 3.6f, i * 15_000L)
    }

    @Test fun roadSpeedFarFromAnyRailwayIsACar() {
        // National Freeway 3 through Nantou, southbound, well away from the TRA and HSR lines.
        val d = DrivingMode.judge(drive(23.95, 120.76, 23.93, 120.755, 95f))!!
        assertEquals(95, d.speedKmh)
        assertEquals("南", DrivingMode.headingWord(d.headingDeg))
        val ahead = DrivingMode.aheads(d)
        assertEquals(listOf(15, 45), ahead.map { it.minutes })
        assertEquals(24, ahead[0].km); assertEquals(71, ahead[1].km)
        assertTrue("15 minutes south of the car", ahead[0].lat < d.lat)
    }

    @Test fun besideTheTrackItIsATrainUnlessThePersonSaysCar() {
        val a = RailMode.traWest.first { it.name == "彰化" }; val b = RailMode.traWest.first { it.name == "員林" }
        val samples = drive(a.lat + (b.lat - a.lat) * .2, a.lon + (b.lon - a.lon) * .2, a.lat + (b.lat - a.lat) * .6, a.lon + (b.lon - a.lon) * .6, 80f)
        assertNull(DrivingMode.judge(samples))
        assertNotNull(DrivingMode.judge(samples, saidCar = true))
        assertNotNull(RailMode.judge(samples))
    }

    @Test fun walkingParkedOrHighSpeedRailIsNotDriving() {
        assertNull(DrivingMode.judge(drive(23.95, 120.76, 23.9499, 120.7599, 4f)))
        assertNull(DrivingMode.judge(drive(23.95, 120.76, 23.93, 120.755, 95f, n = 2)))
        assertNull(DrivingMode.judge(drive(23.95, 120.76, 23.80, 120.70, 260f)))
    }

    @Test fun theCoastalAndSouthLinkRailwaysAreKnown() {
        val sea = RailMode.traSea
        val samples = (0 until 6).map { i ->
            val t = .2 + .4 * i / 5.0
            RailMode.Sample(sea[3].lat + (sea[4].lat - sea[3].lat) * t, sea[3].lon + (sea[4].lon - sea[3].lon) * t, 70 / 3.6f, i * 15_000L)
        }
        val trip = RailMode.judge(samples)!!
        assertEquals(RailMode.Line.TRA, trip.line)
        assertEquals("大甲", trip.next.name)
        assertTrue(RailMode.railOffKm(22.62, 121.006) < 1.0)
    }
}
