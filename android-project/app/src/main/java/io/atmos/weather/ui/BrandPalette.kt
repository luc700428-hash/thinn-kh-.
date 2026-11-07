package io.atmos.weather.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Pastel surfaces, neutral ink and independently contrasted accents. */
object BrandPalette {
    val forest=Color(0xFF28674F)
    val deepForest=Color(0xFF203B31)
    val sage=Color(0xFF88B69E)
    val mint=Color(0xFFB8DFCA)
    val paleMint=Color(0xFFEBF6EF)
    val lightBackground=Color(0xFFF5F8F6)
    val darkBackground=Color(0xFF161C1A)
    val lightInk=Color(0xFF20352D)
    val darkInk=Color(0xFFF2F6F3)
    val selection get()=ThemeMotion.current.accent
    val primary get()=selection
    private fun build(theme:AppThemeColor,dark:Boolean):ColorScheme {
        val accent=Color(theme.primaryArgb);val pastel=Color(theme.swatchArgb)
        fun pale(amount:Float)=lerp(Color.White,pastel,amount)
        fun shade(amount:Float)=lerp(Color(0xFF171B1D),pastel,amount)
        val ink=Color(0xFF23312F);val soft=Color(0xFF50635D)
        return if(!dark)lightColorScheme(
            primary=accent,onPrimary=Color.White,primaryContainer=pale(.64f),onPrimaryContainer=ink,inversePrimary=pastel,
            secondary=accent,onSecondary=Color.White,secondaryContainer=pale(.40f),onSecondaryContainer=ink,
            tertiary=accent,onTertiary=Color.White,tertiaryContainer=pale(.50f),onTertiaryContainer=ink,
            background=pale(.12f),onBackground=ink,surface=Color.White,onSurface=ink,
            surfaceVariant=pale(.30f),onSurfaceVariant=soft,surfaceTint=accent,
            inverseSurface=shade(.08f),inverseOnSurface=darkInk,outline=Color(0xFF788A82),outlineVariant=pale(.75f),
            surfaceBright=Color.White,surfaceDim=pale(.52f),surfaceContainerLowest=Color.White,
            surfaceContainerLow=pale(.12f),surfaceContainer=pale(.22f),surfaceContainerHigh=pale(.34f),surfaceContainerHighest=pale(.46f),
            error=Color(0xFFB42318),onError=Color.White,errorContainer=Color(0xFFFFDAD6),onErrorContainer=Color(0xFF410002),scrim=Color.Black
        ) else darkColorScheme(
            primary=pastel,onPrimary=ink,primaryContainer=shade(.20f),onPrimaryContainer=darkInk,inversePrimary=accent,
            secondary=lerp(pastel,Color.White,.18f),onSecondary=ink,secondaryContainer=shade(.16f),onSecondaryContainer=darkInk,
            tertiary=lerp(pastel,Color.White,.25f),onTertiary=ink,tertiaryContainer=shade(.22f),onTertiaryContainer=darkInk,
            background=shade(.015f),onBackground=darkInk,surface=shade(.065f),onSurface=darkInk,
            surfaceVariant=shade(.14f),onSurfaceVariant=Color(0xFFCAD6D0),surfaceTint=pastel,
            inverseSurface=pale(.22f),inverseOnSurface=ink,outline=Color(0xFF9EAEA5),outlineVariant=shade(.28f),
            surfaceBright=shade(.20f),surfaceDim=shade(.015f),surfaceContainerLowest=Color(0xFF121617),
            surfaceContainerLow=shade(.045f),surfaceContainer=shade(.08f),surfaceContainerHigh=shade(.13f),surfaceContainerHighest=shade(.18f),
            error=Color(0xFFFF5A52),onError=Color(0xFF4C0002),errorContainer=Color(0xFF93000A),onErrorContainer=Color(0xFFFFDAD6),scrim=Color.Black
        )
    }
    private val lightThemes=AppThemeColor.entries.associateWith{build(it,false)}
    private val darkThemes=AppThemeColor.entries.associateWith{build(it,true)}
    internal fun scheme(theme:AppThemeColor,dark:Boolean)=(if(dark)darkThemes else lightThemes).getValue(theme)
    val light get()=ThemeMotion.current.scheme(false)
    val dark get()=ThemeMotion.current.scheme(true)
    internal val current get()=ThemeMotion.current.scheme()
}
internal fun colorContrast(a:Color,b:Color):Float {
    val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
}
internal fun ColorScheme.readable():ColorScheme {
    fun ink(foreground:Color,background:Color):Color {
        if(colorContrast(foreground,background)>=4.5f)return foreground
        val dark=Color(0xFF17231F);val light=Color(0xFFFAFCFB)
        val preferred=if(colorContrast(dark,background)>=colorContrast(light,background))dark else light
        if(colorContrast(preferred,background)>=4.5f)return preferred
        return if(colorContrast(Color.Black,background)>=colorContrast(Color.White,background))Color.Black else Color.White
    }
    return copy(onPrimary=ink(onPrimary,primary),onPrimaryContainer=ink(onPrimaryContainer,primaryContainer),
        onSecondary=ink(onSecondary,secondary),onSecondaryContainer=ink(onSecondaryContainer,secondaryContainer),
        onTertiary=ink(onTertiary,tertiary),onTertiaryContainer=ink(onTertiaryContainer,tertiaryContainer),
        onSurface=ink(onSurface,surface),onSurfaceVariant=ink(onSurfaceVariant,surfaceVariant),onBackground=ink(onBackground,background),
        inverseOnSurface=ink(inverseOnSurface,inverseSurface),onError=ink(onError,error),onErrorContainer=ink(onErrorContainer,errorContainer))
}
internal fun mixSchemes(from:ColorScheme,to:ColorScheme,fraction:Float)=from.copy(
    primary=lerp(from.primary,to.primary,fraction),
    onPrimary=lerp(from.onPrimary,to.onPrimary,fraction),
    primaryContainer=lerp(from.primaryContainer,to.primaryContainer,fraction),
    onPrimaryContainer=lerp(from.onPrimaryContainer,to.onPrimaryContainer,fraction),
    inversePrimary=lerp(from.inversePrimary,to.inversePrimary,fraction),
    secondary=lerp(from.secondary,to.secondary,fraction),
    onSecondary=lerp(from.onSecondary,to.onSecondary,fraction),
    secondaryContainer=lerp(from.secondaryContainer,to.secondaryContainer,fraction),
    onSecondaryContainer=lerp(from.onSecondaryContainer,to.onSecondaryContainer,fraction),
    tertiary=lerp(from.tertiary,to.tertiary,fraction),
    onTertiary=lerp(from.onTertiary,to.onTertiary,fraction),
    tertiaryContainer=lerp(from.tertiaryContainer,to.tertiaryContainer,fraction),
    onTertiaryContainer=lerp(from.onTertiaryContainer,to.onTertiaryContainer,fraction),
    background=lerp(from.background,to.background,fraction),
    onBackground=lerp(from.onBackground,to.onBackground,fraction),
    surface=lerp(from.surface,to.surface,fraction),
    onSurface=lerp(from.onSurface,to.onSurface,fraction),
    surfaceVariant=lerp(from.surfaceVariant,to.surfaceVariant,fraction),
    onSurfaceVariant=lerp(from.onSurfaceVariant,to.onSurfaceVariant,fraction),
    surfaceTint=lerp(from.surfaceTint,to.surfaceTint,fraction),
    inverseSurface=lerp(from.inverseSurface,to.inverseSurface,fraction),
    inverseOnSurface=lerp(from.inverseOnSurface,to.inverseOnSurface,fraction),
    outline=lerp(from.outline,to.outline,fraction),
    outlineVariant=lerp(from.outlineVariant,to.outlineVariant,fraction),
    surfaceBright=lerp(from.surfaceBright,to.surfaceBright,fraction),
    surfaceDim=lerp(from.surfaceDim,to.surfaceDim,fraction),
    surfaceContainerLowest=lerp(from.surfaceContainerLowest,to.surfaceContainerLowest,fraction),
    surfaceContainerLow=lerp(from.surfaceContainerLow,to.surfaceContainerLow,fraction),
    surfaceContainer=lerp(from.surfaceContainer,to.surfaceContainer,fraction),
    surfaceContainerHigh=lerp(from.surfaceContainerHigh,to.surfaceContainerHigh,fraction),
    surfaceContainerHighest=lerp(from.surfaceContainerHighest,to.surfaceContainerHighest,fraction),
    error=lerp(from.error,to.error,fraction),
    onError=lerp(from.onError,to.onError,fraction),
    errorContainer=lerp(from.errorContainer,to.errorContainer,fraction),
    onErrorContainer=lerp(from.onErrorContainer,to.onErrorContainer,fraction),
    scrim=lerp(from.scrim,to.scrim,fraction)
).readable()
