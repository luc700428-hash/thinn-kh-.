package io.atmos.weather.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AppThemeColorTest {
    private fun contrast(a:Color,b:Color):Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
    @Test fun missingOrUnknownPreferencesDefaultToForest() {
        for(value in listOf(null,"","old-blue","invalid"))assertEquals(AppThemeColor.FOREST,AppThemeColor.fromId(value))
        AppThemeColor.entries.forEach {assertEquals(it,AppThemeColor.fromId(it.id))}
    }
    @Test fun textRolesRemainReadableInEveryThemeAndAppearance() {
        for(theme in AppThemeColor.entries)for(dark in listOf(false,true)) {
            val p=BrandPalette.scheme(theme,dark)
            val pairs=listOf(p.onPrimary to p.primary,p.onPrimaryContainer to p.primaryContainer,
                p.onSecondary to p.secondary,p.onSecondaryContainer to p.secondaryContainer,
                p.onTertiary to p.tertiary,p.onTertiaryContainer to p.tertiaryContainer,
                p.onSurface to p.surface,p.onSurfaceVariant to p.surfaceVariant,
                p.onBackground to p.background,p.onError to p.error,p.onErrorContainer to p.errorContainer)
            pairs.forEach {(ink,fill)->assertTrue("Unreadable ${theme.id}, dark=$dark, $ink/$fill",contrast(ink,fill)>=4.5f)}
        }
    }
    @Test fun tintKeepsGlassAlphaAndMaterialLuminance() {
        val samples=listOf(0xED245B48,0x42182D23,0xFFA8D8BF,0xFFF7FAF7,0xFF173B31,0xFFFFFFFE)
        for(theme in AppThemeColor.entries)for(base in samples) {
            val result=ThemeColorTint.argb(base.toInt(),theme)
            assertEquals(base.toInt() ushr 24,result ushr 24)
            assertTrue(abs(Color(base).luminance()-Color(result).luminance())<.007f)
        }
    }
    @Test fun warningRolesDoNotChangeWithTheTheme() {
        for(theme in AppThemeColor.entries) {
            assertEquals(0xFFB42318.toInt(),BrandPalette.scheme(theme,false).error.toArgb())
            assertEquals(0xFFFF5A52.toInt(),BrandPalette.scheme(theme,true).error.toArgb())
        }
    }
    @Test fun allFilledSelectionsKeepWhiteTextReadable() {
        for(theme in AppThemeColor.entries)assertTrue(contrast(Color.White,Color(theme.primaryArgb))>=4.5f)
    }
}
