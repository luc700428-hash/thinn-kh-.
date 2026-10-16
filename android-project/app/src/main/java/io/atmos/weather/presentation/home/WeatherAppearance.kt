package io.atmos.weather.presentation.home

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/** Normalized, UI-observable settings. Continuous effects use Float sliders, not presets. */
data class WeatherAppearance(
    val glassFrost: Float = 0.55f,
    val glassOpacity: Float = 0.66f,
    val edgeLight: Float = 0.65f,
    val liquidMotion: Float = 0.60f,
    val springiness: Float = 0.70f,
    val fontScale: Float = 1.00f,
    val fontWeight: Float = 400f,
    val fontChoice: Int = 0
) {
    val family: FontFamily get() = when (fontChoice) {
        1 -> FontFamily.Serif
        2 -> FontFamily.Monospace
        3 -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }

    fun persist(context: Context) {
        context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE).edit()
            .putFloat("v4_frost", glassFrost.coerceIn(0f, 1f))
            .putFloat("v4_opacity", glassOpacity.coerceIn(0f, 1f))
            .putFloat("v4_edge", edgeLight.coerceIn(0f, 1f))
            .putFloat("v4_liquid", liquidMotion.coerceIn(0f, 1f))
            .putFloat("v4_spring", springiness.coerceIn(0f, 1f))
            .putFloat("v4_fontscale", fontScale.coerceIn(1f, 1.5f))
            .putFloat("v4_fontweight", fontWeight.coerceIn(100f, 900f))
            .putInt("v4_fontchoice", fontChoice.coerceIn(0, 3))
            .apply()
    }
    companion object {
        fun from(context: Context): WeatherAppearance {
            val prefs = context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
            return WeatherAppearance(
                glassFrost = prefs.getFloat("v4_frost", 0.55f).coerceIn(0f, 1f),
                glassOpacity = prefs.getFloat("v4_opacity", 0.66f).coerceIn(0f, 1f),
                edgeLight = prefs.getFloat("v4_edge", 0.65f).coerceIn(0f, 1f),
                liquidMotion = prefs.getFloat("v4_liquid", 0.60f).coerceIn(0f, 1f),
                springiness = prefs.getFloat("v4_spring", 0.70f).coerceIn(0f, 1f),
                fontScale = prefs.getFloat("v4_fontscale", 1f).coerceIn(1f, 1.5f),
                fontWeight = prefs.getFloat("v4_fontweight", 400f).coerceIn(100f, 900f),
                fontChoice = prefs.getInt("v4_fontchoice", 0).coerceIn(0, 3)
            )
        }
    }
}

val LocalAppearance = compositionLocalOf { WeatherAppearance() }
