package io.atmos.weather.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** thinn-khì identity: ink green, muted sage and warm white. */
object BrandPalette {
    val forest = Color(0xFF2F6858)
    val deepForest = Color(0xFF143B2D)
    val sage = Color(0xFF78A18F)
    val mint = Color(0xFFA8D8BF)
    val paleMint = Color(0xFFDDEDE3)
    val lightBackground = Color(0xFFF7FAF7)
    val darkBackground = Color(0xFF0B1712)
    val lightInk = Color(0xFF173B31)
    val darkInk = Color(0xFFEEF6EF)
    // A consistent filled selection with white text in either appearance.
    val selection get() = brandColor(0xFF2F6858)
    val primary get() = selection

    private val baseLight = lightColorScheme(
        primary=forest, onPrimary=Color.White,
        primaryContainer=paleMint, onPrimaryContainer=Color(0xFF143E30),
        inversePrimary=mint,
        secondary=Color(0xFF476D5B), onSecondary=Color.White,
        secondaryContainer=Color(0xFFE6EEE7), onSecondaryContainer=Color(0xFF284B3D),
        tertiary=Color(0xFF52705C), onTertiary=Color.White,
        tertiaryContainer=Color(0xFFE2ECD9), onTertiaryContainer=Color(0xFF30472D),
        background=lightBackground, onBackground=lightInk,
        surface=Color.White, onSurface=lightInk,
        surfaceVariant=Color(0xFFE7EFE9), onSurfaceVariant=Color(0xFF52685E),
        surfaceTint=forest,
        inverseSurface=Color(0xFF203D2F), inverseOnSurface=darkInk,
        outline=Color(0xFF7C9586), outlineVariant=Color(0xFFC3D3C7),
        surfaceBright=Color(0xFFFDFFFC), surfaceDim=Color(0xFFDCE6DD),
        surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFF0F5EF),
        surfaceContainer=Color(0xFFEBF2EB), surfaceContainerHigh=Color(0xFFE5EDE5),
        surfaceContainerHighest=Color(0xFFDEE8DF),
        error=Color(0xFFB42318), onError=Color.White,
        errorContainer=Color(0xFFFFDAD6), onErrorContainer=Color(0xFF410002),
        scrim=Color.Black
    )

    private val baseDark = darkColorScheme(
        primary=mint, onPrimary=Color(0xFF113A2A),
        primaryContainer=Color(0xFF254D3B), onPrimaryContainer=Color(0xFFDCF0E1),
        inversePrimary=forest,
        secondary=Color(0xFFBFD8C7), onSecondary=Color(0xFF1E3B2D),
        secondaryContainer=Color(0xFF314D3C), onSecondaryContainer=Color(0xFFDEECE1),
        tertiary=Color(0xFFC4D8B7), onTertiary=Color(0xFF2B3D25),
        tertiaryContainer=Color(0xFF405438), onTertiaryContainer=Color(0xFFE0ECCD),
        background=darkBackground, onBackground=darkInk,
        surface=Color(0xFF15291F), onSurface=darkInk,
        surfaceVariant=Color(0xFF243B2F), onSurfaceVariant=Color(0xFFBACDBF),
        surfaceTint=mint,
        inverseSurface=Color(0xFFEAF3EA), inverseOnSurface=lightInk,
        outline=Color(0xFF829C8A), outlineVariant=Color(0xFF3E5547),
        surfaceBright=Color(0xFF304A3A), surfaceDim=Color(0xFF0B1712),
        surfaceContainerLowest=Color(0xFF07120D), surfaceContainerLow=Color(0xFF11231A),
        surfaceContainer=Color(0xFF192E22), surfaceContainerHigh=Color(0xFF213A2B),
        surfaceContainerHighest=Color(0xFF2A4433),
        error=Color(0xFFFF5A52), onError=Color(0xFF4C0002),
        errorContainer=Color(0xFF93000A), onErrorContainer=Color(0xFFFFDAD6),
        scrim=Color.Black
    )

    private fun ColorScheme.withTheme(theme:AppThemeColor):ColorScheme {
        fun tint(color:Color)=Color(ThemeColorTint.argb(color.toArgb(),theme))
        return copy(
            primary=tint(primary),
            onPrimary=tint(onPrimary),
            primaryContainer=tint(primaryContainer),
            onPrimaryContainer=tint(onPrimaryContainer),
            inversePrimary=tint(inversePrimary),
            secondary=tint(secondary),
            onSecondary=tint(onSecondary),
            secondaryContainer=tint(secondaryContainer),
            onSecondaryContainer=tint(onSecondaryContainer),
            tertiary=tint(tertiary),
            onTertiary=tint(onTertiary),
            tertiaryContainer=tint(tertiaryContainer),
            onTertiaryContainer=tint(onTertiaryContainer),
            background=tint(background),
            onBackground=tint(onBackground),
            surface=tint(surface),
            onSurface=tint(onSurface),
            surfaceVariant=tint(surfaceVariant),
            onSurfaceVariant=tint(onSurfaceVariant),
            surfaceTint=tint(surfaceTint),
            inverseSurface=tint(inverseSurface),
            inverseOnSurface=tint(inverseOnSurface),
            outline=tint(outline),
            outlineVariant=tint(outlineVariant),
            surfaceBright=tint(surfaceBright),
            surfaceDim=tint(surfaceDim),
            surfaceContainerLowest=tint(surfaceContainerLowest),
            surfaceContainerLow=tint(surfaceContainerLow),
            surfaceContainer=tint(surfaceContainer),
            surfaceContainerHigh=tint(surfaceContainerHigh),
            surfaceContainerHighest=tint(surfaceContainerHighest)
        )
    }
    private val lightThemes=AppThemeColor.entries.associateWith{baseLight.withTheme(it)}
    private val darkThemes=AppThemeColor.entries.associateWith{baseDark.withTheme(it)}
    internal fun scheme(theme:AppThemeColor,dark:Boolean)=
        (if(dark)darkThemes else lightThemes).getValue(theme)
    val light get()=scheme(AppThemeColors.value,false)
    val dark get()=scheme(AppThemeColors.value,true)

}
