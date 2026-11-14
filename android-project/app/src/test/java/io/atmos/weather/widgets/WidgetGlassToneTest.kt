package io.atmos.weather.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Glass colour follows the wallpaper facts the system publishes; opacity stays within readable bounds. */
class WidgetGlassToneTest {
    @Test fun wallpaperFactsChooseTheGlassAndMissingFactsFallBackToDayNight() {
        assertEquals(WidgetGlassTone.Tone(dark=false,knownBackdrop=true),WidgetGlassTone.decide(true,.1f,true))
        assertEquals(WidgetGlassTone.Tone(dark=true,knownBackdrop=true),WidgetGlassTone.decide(false,.2f,false))
        assertEquals(WidgetGlassTone.Tone(dark=false,knownBackdrop=true),WidgetGlassTone.decide(false,.9f,true))
        assertEquals(WidgetGlassTone.Tone(dark=true,knownBackdrop=false),WidgetGlassTone.decide(null,null,true))
        assertEquals(WidgetGlassTone.Tone(dark=false,knownBackdrop=false),WidgetGlassTone.decide(null,Float.NaN,false))
    }

    @Test fun opacityIsMonotonicBoundedAndKeepsAFloorWhenTheWallpaperIsUnknown() {
        for(dark in listOf(false,true))for(known in listOf(false,true)) {
            var before=0f
            for(step in 0..20) {
                val alpha=WidgetLiquidGlass.bodyAlpha(dark,known,step/20f)
                assertTrue(alpha>=before&&alpha>=.2f&&alpha<=.9401f)
                before=alpha
            }
            assertTrue(WidgetLiquidGlass.bodyAlpha(dark,false,0f)>=.6f)
            assertEquals(WidgetLiquidGlass.bodyAlpha(dark,known,.66f),WidgetLiquidGlass.bodyAlpha(dark,known,Float.NaN),0f)
        }
        // Known wallpaper lets the user's setting go clearer than the unknown-wallpaper floor.
        assertTrue(WidgetLiquidGlass.bodyAlpha(true,true,0f)<WidgetLiquidGlass.bodyAlpha(true,false,0f))
    }

    @Test fun darkGlassUsesWhiteInkAndLightGlassKeepsItsDarkInk() {
        val theme=io.atmos.weather.ui.AppThemeColor.FOREST
        val dark=WidgetGlassRenderer.paletteFor(theme,0,alert=false,dark=true)
        val light=WidgetGlassRenderer.paletteFor(theme,0)
        fun red(color:Int)=(color shr 16) and 0xFF
        assertEquals(0xFFFFFFFF.toInt(),dark.ink)
        assertTrue(red(light.ink)<90)
        assertTrue(red(dark.top)<90&&red(light.top)>200)
    }
}
