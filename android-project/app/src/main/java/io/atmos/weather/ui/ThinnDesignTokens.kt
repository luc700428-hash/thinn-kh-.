package io.atmos.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
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
    val lightBackground = Color(0xFFF2F2F7)
    val darkBackground = Color(0xFF000000)
    val separatorLight = Color(0x2E3C3C43)
    val separatorDark = Color(0xA6545458)
    val lightGlass = Color(0xECFFFFFF)
    val darkGlass = Color(0xDB1C1C1E)
    val accent = Color(0xFF0A84FF)
    val positive = Color(0xFF34C759)
    val caution = Color(0xFFFFC65A)
    val critical = Color(0xFFFF5A52)
    val primaryInkDark = Color.White
    val secondaryInkDark = Color(0xFFC7C7CC)
    const val shortMotionMillis = 180
    const val mediumMotionMillis = 320
    const val longMotionMillis = 480
    const val springDamping = .72f
    const val springStiffness = 190f
}

private val thinnScheme = darkColorScheme(
    primary = Color(0xFF75BFFF),
    onPrimary = Color(0xFF081828),
    background = ThinnDesignTokens.darkBackground,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = ThinnDesignTokens.secondaryInkDark,
    secondary = Color(0xFF9FD8FF), onSecondary = Color(0xFF081828),
    primaryContainer = Color(0xFF183D62), onPrimaryContainer = Color(0xFFD9EDFF),
    secondaryContainer = Color(0xFF253442), onSecondaryContainer = Color(0xFFE3EDF5),
    surfaceContainer = Color(0xFF242426), surfaceContainerHigh = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFF2F2F7), inverseOnSurface = Color(0xFF1C1C1E),
    error = ThinnDesignTokens.critical,
    outline = Color(0xFF8098AE)
)

private val thinnLightScheme = lightColorScheme(
    primary = Color(0xFF0066CC), onPrimary = Color.White,
    background = ThinnDesignTokens.lightBackground, onBackground = Color.Black,
    surface = Color.White, onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE8EAF0), onSurfaceVariant = Color(0xFF5C5C64),
    secondary = Color(0xFF0066CC), onSecondary = Color.White,
    primaryContainer = Color(0xFFDCEBFF), onPrimaryContainer = Color(0xFF12395E),
    secondaryContainer = Color(0xFFE8EDF4), onSecondaryContainer = Color(0xFF243C50),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE8EAF0),
    inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color.White,
    error = Color(0xFFB42318),
    outline = Color(0xFFC7C7CC)
)

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
    val requested=IOS27AppearanceMode.value
    val dark=when(requested) {
        "light" -> false
        "auto" -> isSystemInDarkTheme()
        else -> true
    }
    val target=if(dark) thinnScheme else thinnLightScheme
    val bg by animateColorAsState(target.background,tween(300),label="theme-background")
    val surface by animateColorAsState(target.surface,tween(300),label="theme-surface")
    val secondary by animateColorAsState(target.surfaceVariant,tween(300),label="theme-secondary")
    val ink by animateColorAsState(target.onSurface,tween(300),label="theme-ink")
    val accent by animateColorAsState(target.primary,tween(300),label="theme-accent")
    val colors=target.copy(background=bg,surface=surface,surfaceVariant=secondary,
        onSurface=ink,primary=accent)
    MaterialTheme(colorScheme=colors,
        typography=thinnTypography, shapes=thinnShapes) {
        // Compose Foundation >=1.8: remove Android stretch/glow globally.
        // Add our own rubber-band effect ONLY to appropriate scroll surfaces.
        CompositionLocalProvider(
            LocalReadingBaseDensity provides baseDensity,
            io.atmos.weather.presentation.home.LocalAppearance provides reading,
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density,baseDensity.fontScale*reading.fontScale),
            LocalOverscrollFactory provides null,
            androidx.compose.foundation.LocalIndication provides IOSPressIndication) {
            androidx.compose.material3.ProvideTextStyle(value=thinnTypography.bodyMedium) { content() }
        }
    }
}
