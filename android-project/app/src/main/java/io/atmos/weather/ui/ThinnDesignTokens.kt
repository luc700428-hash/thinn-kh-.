package io.atmos.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One source of truth for the Android design system.
 * iOS-inspired hierarchy, not a use of Apple UI frameworks, fonts, or SF Symbols.
 */
object ThinnDesignTokens {
    val radiusControl = 9999.dp
    val radiusRow = 16.dp
    val radiusCard = 16.dp
    val radiusSheet = 32.dp
    val radiusNav = 35.dp
    val touchTarget = 48.dp
    val spacingTiny = 4.dp
    val spacingSmall = 8.dp
    val spacingMedium = 16.dp
    val spacingLarge = 24.dp
    val lightBackground get() = BrandPalette.light.background
    val darkBackground get() = BrandPalette.dark.background
    val separatorLight get() = brandColor(0x2E383E3C)
    val separatorDark get() = brandColor(0xA6515554)
    val lightGlass = Color(0xECFFFFFF)
    val darkGlass get() = brandColor(0xDB15291F)
    val accent get() = BrandPalette.primary
    val positive = Color(0xFF34C759)
    val caution = Color(0xFFFFC65A)
    val critical = Color(0xFFFF5A52)
    val primaryInkDark = Color.White
    val secondaryInkDark get() = BrandPalette.dark.onSurfaceVariant
    const val shortMotionMillis = 180
    const val mediumMotionMillis = 320
    const val longMotionMillis = 480
    const val springDamping = .72f
    const val springStiffness = 190f
}

private val thinnScheme get() = BrandPalette.dark
private val thinnLightScheme get() = BrandPalette.light

private val thinnTypography = Typography(
    displaySmall = TextStyle(fontSize=34.sp, lineHeight=42.sp, letterSpacing=(-.8).sp, fontWeight=FontWeight.Bold),
    headlineMedium = TextStyle(fontSize=29.sp, lineHeight=38.sp, fontWeight=FontWeight.Bold),
    titleLarge = TextStyle(fontSize=22.sp, lineHeight=29.sp, letterSpacing=(-.4).sp, fontWeight=FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize=18.sp, lineHeight=27.sp, fontWeight=FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize=17.sp, lineHeight=26.sp, letterSpacing=(-.2).sp),
    bodyMedium = TextStyle(fontSize=16.sp, lineHeight=24.sp),
    bodySmall = TextStyle(fontSize=16.sp, lineHeight=24.sp),
    labelLarge = TextStyle(fontSize=16.sp, lineHeight=24.sp, fontWeight=FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize=14.sp, lineHeight=17.sp)
)

private val thinnShapes = Shapes(
    small=RoundedCornerShape(10.dp),
    medium=RoundedCornerShape(14.dp),
    large=RoundedCornerShape(22.dp),
    extraLarge=RoundedCornerShape(28.dp)
)

private val LocalReadingBaseDensity=androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.unit.Density?> {null}

@Composable
fun ThinnAppTheme(content:@Composable ()->Unit) {
    HighRefreshHost()
    val reading=rememberAppAppearance()
    val baseDensity=LocalReadingBaseDensity.current?:androidx.compose.ui.platform.LocalDensity.current

    // Weather scenery has its own readable material. Forms and tools share this
    // complete light/dark palette, including selected controls and secondary copy.
    val context=androidx.compose.ui.platform.LocalContext.current
    IOS27AppearanceMode.initialize(context)
    AppThemeColors.initialize(context)
    val requested=IOS27AppearanceMode.value
    val dark=when(requested) {
        "light" -> false
        "auto" -> isSystemInDarkTheme()
        else -> true
    }
    if(!LocalThemeMotionHost.current)ThemeMotionHost(AppThemeColors.value,dark,rememberAppleReduceMotion())
    val colors=BrandPalette.current
    MaterialTheme(colorScheme=colors,
        typography=thinnTypography, shapes=thinnShapes) {
        // Compose Foundation >=1.8: remove Android stretch/glow globally.
        // Add our own rubber-band effect ONLY to appropriate scroll surfaces.
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides colors.onSurface,
            LocalThemeMotionHost provides true,
            LocalReadingBaseDensity provides baseDensity,
            io.atmos.weather.presentation.home.LocalAppearance provides reading,
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density,baseDensity.fontScale*reading.fontScale),
            LocalOverscrollFactory provides null,
            androidx.compose.foundation.LocalIndication provides IOSPressIndication) {
            androidx.compose.material3.ProvideTextStyle(value=thinnTypography.bodyMedium) { content() }
        }
    }
}
