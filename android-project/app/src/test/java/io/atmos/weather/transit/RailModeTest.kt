package io.atmos.weather.transit

import org.junit.Assert.*
import org.junit.Test

class RailModeTest {
    /** Fixes every 15 s along the straight line between two stations at a steady speed. */
    private fun ride(a:RailMode.Station,b:RailMode.Station,kmh:Float,from:Float,to:Float,n:Int=6)=(0 until n).map{i->
        val t=from+(to-from)*i/(n-1)
        RailMode.Sample(a.lat+(b.lat-a.lat)*t,a.lon+(b.lon-a.lon)*t,kmh/3.6f,i*15_000L)
    }
    private fun station(list:List<RailMode.Station>,name:String)=list.first{it.name==name}

    @Test fun highSpeedSouthboundNamesTheNextStopAndTheEndOfTheLine() {
        val trip=RailMode.judge(ride(station(RailMode.hsr,"新竹"),station(RailMode.hsr,"苗栗"),280f,.2f,.5f))!!
        assertEquals(RailMode.Line.HSR,trip.line);assertTrue(trip.confident)
        assertEquals("苗栗",trip.next.name);assertEquals("左營",trip.terminal.name);assertEquals("往左營",trip.towardLabel)
        assertTrue(trip.nextMinutes in 1..10)
    }
    @Test fun northboundTraIsAskedToConfirm() {
        val trip=RailMode.judge(ride(station(RailMode.traWest,"彰化"),station(RailMode.traWest,"台中"),85f,.2f,.6f))!!
        assertEquals(RailMode.Line.TRA,trip.line);assertFalse(trip.confident)
        assertEquals("台中",trip.next.name);assertEquals("基隆",trip.terminal.name)
    }
    @Test fun walkingDrivingAcrossOrTooLittleDataIsNotATrain() {
        assertNull(RailMode.judge(ride(station(RailMode.hsr,"新竹"),station(RailMode.hsr,"苗栗"),5f,.2f,.201f)))
        assertNull(RailMode.judge(ride(station(RailMode.hsr,"新竹"),station(RailMode.hsr,"苗栗"),280f,.2f,.5f,n=2)))
        // Fast but far from any railway (central mountains).
        val far=(0 until 6).map{RailMode.Sample(23.5+it*.01,121.0,40f,it*15_000L)}
        assertNull(RailMode.judge(far))
    }
}
