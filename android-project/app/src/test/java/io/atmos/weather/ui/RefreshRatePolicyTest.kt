package io.atmos.weather.ui
import org.junit.Assert.*
import org.junit.Test
class RefreshRatePolicyTest {
    private fun mode(id:Int,hz:Float,w:Int=1080)=RefreshRatePolicy.Mode(id,w,2400,hz)
    @Test fun prefers120RatherThanTheLargestRate() {
        assertEquals(2,RefreshRatePolicy.choose(listOf(mode(1,60f),mode(2,120f),mode(3,144f)),1080,2400)!!.id)
    }
    @Test fun doesNotChangeScreenResolutionToGetAHighRate() {
        assertEquals(1,RefreshRatePolicy.choose(listOf(mode(1,90f),mode(2,120f,720)),1080,2400)!!.id)
    }
    @Test fun accepts119Point88AndFallsBackOn90HzHardware() {
        assertEquals(119.88f,RefreshRatePolicy.choose(listOf(mode(1,60f),mode(2,119.88f)),1080,2400)!!.hz,.001f)
        assertEquals(90f,RefreshRatePolicy.choose(listOf(mode(1,60f),mode(2,90f)),1080,2400)!!.hz,.001f)
    }
}
