package io.atmos.weather.ui
import org.junit.Assert.*
import org.junit.Test
class EverydayToolMathTest {
    @Test fun conversionReferenceValues() {
        val c=EverydayToolMath.categories
        assertEquals(32.0,EverydayToolMath.convert(0.0,c[0].units[0],c[0].units[1]),1e-10)
        assertEquals(-40.0,EverydayToolMath.convert(-40.0,c[0].units[1],c[0].units[0]),1e-10)
        assertEquals(273.15,EverydayToolMath.convert(0.0,c[0].units[0],c[0].units[2]),1e-10)
        assertEquals(3.6,EverydayToolMath.convert(1.0,c[1].units[0],c[1].units[1]),1e-10)
        assertEquals(25.4,EverydayToolMath.convert(1.0,c[2].units[2],c[2].units[0]),1e-10)
        assertEquals(1.609344,EverydayToolMath.convert(1.0,c[3].units[1],c[3].units[0]),1e-10)
        assertEquals(1013.25,EverydayToolMath.convert(101.325,c[4].units[1],c[4].units[0]),1e-9)
    }
    @Test fun pauseAndResumeCountOnlyRunningTime() {
        assertEquals(3000L,EverydayToolMath.elapsed(2000L,10_000L,11_000L))
        assertEquals(2000L,EverydayToolMath.elapsed(2000L,null,11_000L))
        assertEquals(2000L,EverydayToolMath.elapsed(2000L,10_000L,9000L))
    }
}
