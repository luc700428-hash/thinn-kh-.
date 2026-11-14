package io.atmos.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.util.Date

/** Glass highlights follow the real sun: east on the left at sunrise, west on the right at sunset. */
class GlassLightTest {
    private fun taipei(text:String)=Date.from(ZonedDateTime.parse(text).toInstant())

    @Test fun skyPositionsMapEastLeftWestRightAndHighAtNoon() {
        val rising=GlassLight.fromSky(100.0,5.0)!!;assertTrue(rising.x<-.7f)
        val setting=GlassLight.fromSky(260.0,5.0)!!;assertTrue(setting.x>.7f)
        val noon=GlassLight.fromSky(180.0,80.0)!!;assertTrue(noon.y<-.95f&&kotlin.math.abs(noon.x)<.1f)
        for(l in listOf(rising,setting,noon))assertEquals(1f,l.x*l.x+l.y*l.y,.01f)
        assertNull(GlassLight.fromSky(180.0,-3.0))
        assertNull(GlassLight.fromSky(Double.NaN,20.0))
    }

    @Test fun taipeiMorningEveningAndNight() {
        val morning=GlassLight.at(25.033,121.5654,taipei("2026-11-15T07:30:00+08:00"))
        val evening=GlassLight.at(25.033,121.5654,taipei("2026-11-15T16:45:00+08:00"))
        assertTrue("morning light from the east (left): $morning",morning.x<-.3f)
        assertTrue("evening light from the west (right): $evening",evening.x>.3f)
        assertTrue(morning.y<0f&&evening.y<0f)
        // Invalid places fall back to the resting upper-left light.
        assertEquals(GlassLight.REST,GlassLight.at(Double.NaN,121.0,Date()))
    }
}
