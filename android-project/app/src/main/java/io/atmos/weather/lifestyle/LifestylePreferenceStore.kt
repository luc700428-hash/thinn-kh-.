package io.atmos.weather.lifestyle

import android.content.Context

/** Local only. Changes refresh advice immediately, including when returning from settings. */
object LifestylePreferenceStore {
    private const val PREF = "thinn_lifestyle_preferences"
    private val revision=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes:kotlinx.coroutines.flow.StateFlow<Long> = revision
    fun mode(context:Context):OutingMode {
        val saved=context.getSharedPreferences(PREF,0).getString("mode",null)
            ?: context.getSharedPreferences("thinn_lifestyle_ai",0).getString("mode","DAILY")
        return OutingMode.entries.firstOrNull{it.name==saved}?:OutingMode.DAILY
    }
    fun mode(context:Context,value:OutingMode) {
        context.getSharedPreferences(PREF,0).edit().putString("mode",value.name).apply()
        revision.value++
    }
    fun removeLegacyAi(context:Context) {
        val old=context.getSharedPreferences("thinn_lifestyle_ai",0)
        if(old.contains("endpoint")||old.contains("model")||old.contains("enabled"))
            old.edit().remove("endpoint").remove("model").remove("enabled").apply()
    }
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
        revision.value++
    }
}
