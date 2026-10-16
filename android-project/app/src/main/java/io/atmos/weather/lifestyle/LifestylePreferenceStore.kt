package io.atmos.weather.lifestyle

import android.content.Context

/** Local only: preference values are never included in the AI network payload. */
object LifestylePreferenceStore {
    private const val PREF = "thinn_lifestyle_preferences"
    fun load(context: Context): LifestylePreferences {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val comfort = enumValues<TemperatureComfort>().firstOrNull {
            it.name == prefs.getString("comfort", TemperatureComfort.BALANCED.name)
        } ?: TemperatureComfort.BALANCED
        val style = enumValues<ClothingStyle>().firstOrNull {
            it.name == prefs.getString("style", ClothingStyle.CASUAL.name)
        } ?: ClothingStyle.CASUAL
        val threshold = prefs.getInt("umbrella_pct", 31).coerceIn(20, 90)
        return LifestylePreferences(comfort, style, threshold)
    }
    fun save(context: Context, preferences: LifestylePreferences) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("comfort", preferences.comfort.name)
            .putString("style", preferences.style.name)
            .putInt("umbrella_pct", preferences.umbrellaThresholdPct)
            .apply()
    }
}
