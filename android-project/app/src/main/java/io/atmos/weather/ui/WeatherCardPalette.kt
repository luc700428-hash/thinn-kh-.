package io.atmos.weather.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** One readable sky-glass material for all ordinary Home tiles; alerts stay red. */
data class WeatherCardPalette(val fill:Color) {
    val brush=SolidColor(fill)
    val ink=Color.White
    val secondary get() =brandColor(0xFFD0EEE6)
    val accent get() =brandColor(0xFFA9E1D1)
    val edge=Color.White.copy(alpha=.24f)
}

object WeatherCardPalettes {
    val day get() =WeatherCardPalette(brandColor(0xED205A4A))
    val night get() =WeatherCardPalette(brandColor(0xF0123229))
    val alert=WeatherCardPalette(Color(0xFF92343E))
}

// Null outside the weather page: forms, maps and system settings retain their own theme.
val LocalWeatherCardPalette=staticCompositionLocalOf<WeatherCardPalette?> {null}

@Composable
fun rememberWeatherCardPalette():WeatherCardPalette {
    val base=if(MaterialTheme.colorScheme.background.red<.5f)WeatherCardPalettes.night else WeatherCardPalettes.day
    val opaque=rememberAppleReduceTransparency()
    return remember(base,opaque){if(opaque)WeatherCardPalette(base.fill.copy(alpha=1f)) else base}
}
