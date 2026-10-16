package io.atmos.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
    val radiusControl = 14.dp
    val radiusRow = 18.dp
    val radiusCard = 26.dp
    val radiusSheet = 28.dp
    val radiusNav = 28.dp
    val touchTarget = 48.dp
    val spacingTiny = 4.dp
    val spacingSmall = 8.dp
    val spacingMedium = 16.dp
    val spacingLarge = 24.dp
    val lightBackground = Color(0xFFF2F2F7)
    val darkBackground = Color(0xFF0C1829)
    val lightGlass = Color(0xECFFFFFF)
    val darkGlass = Color(0xD723344C)
    val accent = Color(0xFF63BCFF)
    val positive = Color(0xFF34C759)
    val caution = Color(0xFFFFC65A)
    val critical = Color(0xFFFF5A52)
    val primaryInkDark = Color.White
    val secondaryInkDark = Color(0xFFE1ECF6)
    const val shortMotionMillis = 150
    const val mediumMotionMillis = 260
    const val longMotionMillis = 390
    const val springDamping = .82f
    const val springStiffness = 420f
}

private val thinnScheme = darkColorScheme(
    primary = ThinnDesignTokens.accent,
    onPrimary = Color(0xFF061729),
    background = ThinnDesignTokens.darkBackground,
    onBackground = Color.White,
    surface = Color(0xFF213149),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF30425A),
    onSurfaceVariant = ThinnDesignTokens.secondaryInkDark,
    secondary = Color(0xFF9FD8FF),
    error = ThinnDesignTokens.critical,
    outline = Color(0xFF8098AE)
)

private val thinnTypography = Typography(
    displaySmall = TextStyle(fontSize=36.sp, lineHeight=44.sp, fontWeight=FontWeight.Bold),
    headlineMedium = TextStyle(fontSize=29.sp, lineHeight=38.sp, fontWeight=FontWeight.Bold),
    titleLarge = TextStyle(fontSize=22.sp, lineHeight=28.sp, fontWeight=FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize=18.sp, lineHeight=27.sp, fontWeight=FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize=18.sp, lineHeight=28.sp),
    bodyMedium = TextStyle(fontSize=16.sp, lineHeight=25.sp),
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
    MaterialTheme(colorScheme=thinnScheme, typography=thinnTypography, shapes=thinnShapes) {
        androidx.compose.material3.ProvideTextStyle(value=thinnTypography.bodyMedium) { content() }
    }
}
