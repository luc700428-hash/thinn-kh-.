package io.atmos.weather.haptics

import android.content.Context
import androidx.compose.runtime.compositionLocalOf

/**
 * 0..1 is a continuous *requested* level. Actual amplitude depends on device hardware.
 * Weather haptics default on for new installations; user preferences are always preserved.
 */
data class HapticSettings(
    val enabled: Boolean = true,
    val master: Float = 1f,
    val click: Float = 0.32f,
    val drag: Float = 0.22f,
    val spring: Float = 0.28f,
    val weather: Float = 0.15f,
    val alerts: Float = 0.60f,
    val rainDrops: Boolean = true,
    val thunder: Boolean = true,
    val respectSilentMode: Boolean = true,
    val reduceInPowerSaver: Boolean = false
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("haptics_enabled",enabled)
            .putFloat("haptic_master",master.coerceIn(0f,1f))
            .putFloat("haptic_click",click.coerceIn(0f,1f))
            .putFloat("haptic_drag",drag.coerceIn(0f,1f))
            .putFloat("haptic_spring",spring.coerceIn(0f,1f))
            .putFloat("haptic_weather",weather.coerceIn(0f,1f))
            .putFloat("haptic_alerts",alerts.coerceIn(0f,1f))
            .putBoolean("haptic_rain",rainDrops)
            .putBoolean("haptic_thunder",thunder)
            .putBoolean("haptic_silent",respectSilentMode)
            .putBoolean("haptic_power_save",reduceInPowerSaver)
            .apply()
    }
    companion object {
        private const val PREFS="thinn_settings"
        fun load(context: Context): HapticSettings {
            val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            return HapticSettings(
                enabled=p.getBoolean("haptics_enabled",true),
                master=if (p.contains("haptic_master")) {
                    // Old default was 58%; migrate only that legacy default to 100% once.
                    // Any deliberately customized value remains untouched.
                    val stored=p.getFloat("haptic_master",1f)
                    if(!p.getBoolean("haptic_v108_migrated",false)) {
                        p.edit().putBoolean("haptic_v108_migrated",true).apply()
                        if(kotlin.math.abs(stored-0.58f)<0.0001f) 1f else stored
                    } else stored
                } else 1f,
                click=p.getFloat("haptic_click",.32f).coerceIn(0f,1f),
                drag=p.getFloat("haptic_drag",.22f).coerceIn(0f,1f),
                spring=p.getFloat("haptic_spring",.28f).coerceIn(0f,1f),
                weather=p.getFloat("haptic_weather",.15f).coerceIn(0f,1f),
                alerts=p.getFloat("haptic_alerts",.60f).coerceIn(0f,1f),
                rainDrops=p.getBoolean("haptic_rain",true),
                thunder=p.getBoolean("haptic_thunder",true),
                respectSilentMode=p.getBoolean("haptic_silent",true),
                reduceInPowerSaver=p.getBoolean("haptic_power_save",false)
            )
        }
    }
}

val LocalHapticEngine=compositionLocalOf<HapticEngine?> { null }
