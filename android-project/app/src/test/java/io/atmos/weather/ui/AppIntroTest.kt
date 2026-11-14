package io.atmos.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The entry film fits every window shape without distortion and never delays an alert or a deep link. */
class AppIntroTest {
    @Test fun everyWindowShapeGetsAnUndistortedFilmThatIsNeverCroppedBeyondTheSafeZoom() {
        val windows=listOf(1080 to 2400,1220 to 2712,1080 to 1920,720 to 1280,1600 to 2560,2560 to 1600,2400 to 1080,1812 to 2176,2176 to 1812,
            1080 to 1080,600 to 2000,2000 to 600,1 to 1,3000 to 900)
        for((w,h) in windows) {
            val landscape=AppIntro.useLandscape(w,h)
            assertEquals(w>h,landscape)
            val f=AppIntro.frame(w,h,landscape)
            val filmW=if(landscape)1920f else 1080f;val filmH=if(landscape)1080f else 1920f
            // Same scale on both axes: the artwork is never stretched.
            assertEquals("$w x $h",f.width/filmW,f.height/filmH,2f/minOf(filmW,filmH))
            val fit=minOf(w/filmW,h/filmH);val scale=f.width/filmW
            assertTrue("$w x $h shrank below a fit",scale>=fit-.002f)
            assertTrue("$w x $h cropped past the safe zoom",scale<=fit*AppIntro.MAX_ZOOM+.002f)
            // At least one axis is filled edge to edge unless the zoom limit stopped it.
            assertTrue("$w x $h",f.width>=w-1||f.height>=h-1)
        }
        // A window with the film's own shape is filled exactly.
        assertEquals(AppIntro.Frame(1080,1920),AppIntro.frame(1080,1920,false))
        assertEquals(AppIntro.Frame(1920,1080),AppIntro.frame(1920,1080,true))
        // Degenerate measurements cannot produce an empty or negative view.
        assertEquals(AppIntro.Frame(1,1),AppIntro.frame(0,-5,false).let{AppIntro.Frame(it.width.coerceAtMost(1),it.height.coerceAtMost(1))})
    }

    @Test fun onlyAnOrdinaryLauncherStartPlaysTheFilm() {
        fun wanted(action:String?="android.intent.action.MAIN",launcher:Boolean=true,keys:Set<String> =emptySet(),restored:Boolean=false,
            enabled:Boolean=true,reduced:Boolean=false,warning:Boolean=false)=AppIntro.wanted(action,launcher,keys,restored,enabled,reduced,warning)
        assertTrue(wanted())
        assertTrue(wanted(keys=setOf("profile")))
        assertFalse(wanted(keys=setOf("open_earthquake_center")))
        assertFalse(wanted(keys=setOf("open_weather_alarms")))
        assertFalse(wanted(action=null))
        assertFalse(wanted(action="android.intent.action.VIEW"))
        assertFalse(wanted(launcher=false))
        assertFalse(wanted(restored=true))
        assertFalse(wanted(enabled=false))
        assertFalse(wanted(reduced=true))
        assertFalse(wanted(warning=true))
    }
}
