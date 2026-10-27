package io.atmos.weather.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class WeatherCardPaletteTest {
    private fun contrast(a:Color,b:Color):Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun normalTextStaysReadableOnBrightAndDarkScenes() {
        for(p in listOf(WeatherCardPalettes.day,WeatherCardPalettes.night,WeatherCardPalettes.alert)) {
            for(scene in listOf(Color.White,Color.Black,Color(0xFF66B9E6))) {
                val bg=p.fill.compositeOver(scene)
                assertTrue("Main text lost contrast",contrast(p.ink,bg)>=4.5f)
                assertTrue("Secondary text lost contrast",contrast(p.secondary,bg)>=4.5f)
            }
        }
    }
    @Test fun onlyTheAlertMaterialUsesARedBase() {
        for(p in listOf(WeatherCardPalettes.day,WeatherCardPalettes.night))assertTrue(p.fill.blue>p.fill.red)
        val alert=WeatherCardPalettes.alert.fill
        assertTrue(alert.red>alert.green*1.8f && alert.red>alert.blue*1.8f)
    }
}
