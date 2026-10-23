package io.atmos.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    val radiusControl = 24.dp
    val radiusRow = 16.dp
    val radiusCard = 16.dp
    val radiusSheet = 36.dp
    val radiusNav = 35.dp
    val touchTarget = 48.dp
    val spacingTiny = 4.dp
    val spacingSmall = 8.dp
    val spacingMedium = 16.dp
    val spacingLarge = 24.dp
    val lightBackground = Color(0xFFF2F2F7)
    val darkBackground = Color(0xFF000000)
    val lightGlass = Color(0xECFFFFFF)
    val darkGlass = Color(0xDB1C1C1E)
    val accent = Color(0xFF9FE3FF)
    val positive = Color(0xFF34C759)
    val caution = Color(0xFFFFC65A)
    val critical = Color(0xFFFF5A52)
    val primaryInkDark = Color.White
    val secondaryInkDark = Color(0xFFD3DDE8)
    const val shortMotionMillis = 180
    const val mediumMotionMillis = 320
    const val longMotionMillis = 480
    const val springDamping = .72f
    const val springStiffness = 190f
}

private val thinnScheme = darkColorScheme(
    primary = ThinnDesignTokens.accent,
    onPrimary = Color(0xFF061729),
    background = ThinnDesignTokens.darkBackground,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = ThinnDesignTokens.secondaryInkDark,
    secondary = Color(0xFF9FD8FF),
    error = ThinnDesignTokens.critical,
    outline = Color(0xFF8098AE)
)

private val thinnLightScheme = lightColorScheme(
    primary = Color(0xFF007AFF), onPrimary = Color.White,
    background = ThinnDesignTokens.lightBackground, onBackground = Color.Black,
    surface = Color.White, onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF3A3A3C),
    secondary = Color(0xFF007AFF), error = Color(0xFFFF3B30),
    outline = Color(0xFFC7C7CC)
)

private val thinnTypography = Typography(
    displaySmall = TextStyle(fontSize=36.sp, lineHeight=42.sp, fontWeight=FontWeight.Bold),
    headlineMedium = TextStyle(fontSize=29.sp, lineHeight=38.sp, fontWeight=FontWeight.Bold),
    titleLarge = TextStyle(fontSize=22.sp, lineHeight=29.sp, fontWeight=FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize=18.sp, lineHeight=27.sp, fontWeight=FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize=18.sp, lineHeight=28.sp),
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

@Composable
fun ThinnAppTheme(content:@Composable ()->Unit) {
    // Theme tokens never rewrite weather-data-driven sky colors or domain state.
    // Existing feature screens still hardcode white foreground; keep dark until
    // all light-screen text/colors pass contrast and device regression tests.
    MaterialTheme(colorScheme=thinnScheme,
        typography=thinnTypography, shapes=thinnShapes) {
        // Compose Foundation >=1.8: remove Android stretch/glow globally.
        // Add our own rubber-band effect ONLY to appropriate scroll surfaces.
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            androidx.compose.material3.ProvideTextStyle(value=thinnTypography.bodyMedium) { content() }
        }
    }
}
