package io.atmos.weather.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
data class WeatherCardPalette(val fill:Color,val ink:Color=Color.White,val secondary:Color=Color(0xFFFFE8E5),val accent:Color=Color(0xFFFFD3A3)) {
 val brush=SolidColor(fill);val edge=ink.copy(alpha=.12f)
}
object WeatherCardPalettes {
 internal fun fromScheme(p:androidx.compose.material3.ColorScheme)=WeatherCardPalette(lerp(p.surface,p.primaryContainer,.22f).copy(alpha=.97f),p.onSurface,p.onSurfaceVariant,p.primary)
 val day get()=fromScheme(BrandPalette.light)
 val night get()=fromScheme(BrandPalette.dark)
 val current get()=fromScheme(BrandPalette.current)
 val alert=WeatherCardPalette(Color(0xFF92343E))
}
val LocalWeatherCardPalette=staticCompositionLocalOf<WeatherCardPalette?>{null}
val LocalWeatherSceneInk=staticCompositionLocalOf{Color(0xFF23312F)}
@Composable fun rememberWeatherCardPalette():WeatherCardPalette {
 val base=WeatherCardPalettes.fromScheme(MaterialTheme.colorScheme);val opaque=rememberAppleReduceTransparency()
 return remember(base,opaque){if(opaque)base.copy(fill=base.fill.copy(alpha=1f)) else base}
}
