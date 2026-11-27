package io.atmos.weather.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.math.abs

/** Positions checked against astropy (builtin ephemeris) for 2026-11-27 12:00 UTC, Taipei. */
class StarSkyTest {
    private val at = Instant.parse("2026-11-27T12:00:00Z")
    private fun angle(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)

    @Test fun planetsMatchAReferenceEphemeris() {
        val expected = mapOf(StarSky.Planet.MERCURY to (224.82 to -15.0), StarSky.Planet.VENUS to (204.29 to -9.09),
            StarSky.Planet.MARS to (153.1 to 13.47), StarSky.Planet.JUPITER to (148.76 to 13.51), StarSky.Planet.SATURN to (8.12 to .67),
            StarSky.Planet.URANUS to (61.23 to 20.65), StarSky.Planet.NEPTUNE to (1.75 to -.77))
        for ((p, e) in expected) {
            val (ra, dec) = StarSky.raDec(StarSky.planet(p, at).first)
            assertTrue("${p.label} RA $ra vs ${e.first}", angle(ra, e.first) < .6)
            assertTrue("${p.label} Dec $dec vs ${e.second}", abs(dec - e.second) < .5)
        }
    }

    @Test fun starsLandWhereTheyShouldOverTaipei() {
        val m = StarSky.skyToHorizon(at, 25.03, 121.56)
        fun check(ra: Double, dec: Double, az: Double, alt: Double) {
            val v = StarSky.vec(ra, dec)
            val h = StarSky.apply(m, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
            val (a, h2) = StarSky.azAlt(h[0], h[1], h[2])
            assertTrue("az $a vs $az", angle(a, az) < .5 || alt > 85)
            assertEquals(alt, h2, .5)
        }
        check(279.235, 38.784, 305.68, 16.46)   // 織女星
        check(101.287, -16.716, 103.84, -10.17)  // 天狼星
        check(37.95, 89.264, .44, 25.51)         // 北極星
    }

    @Test fun catalogueHasTheWholeSky() {
        val dir = listOf(File("src/main/assets/sky"), File("app/src/main/assets/sky")).first { it.isDirectory }
        val c = StarSky.load { File(dir, it).readLines() }
        assertTrue(c.stars.size > 5000)
        assertEquals(88, c.constellations.size)
        assertTrue(c.figures.size >= 88)
        assertTrue(c.milkyWay.level.size > 3000)
        assertEquals("天狼星", c.stars.first().name)
        assertTrue(c.constellations.none { it.name.contains('鱼') })
    }
}
