package io.atmos.weather.briefing

import android.content.Context
import io.atmos.weather.domain.model.WeatherBriefingSlot
import org.json.JSONArray
import org.json.JSONObject

/** Local-only user schedules. No account or credential required. */
object WeatherBriefingStore {
    private const val PREF="thinn_weather_briefings_v154"
    fun load(context:Context):List<WeatherBriefingSlot> {
        val json=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("slots","[]") ?: "[]"
        return runCatching {
            val array=JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj=array.optJSONObject(index) ?: return@mapNotNull null
                runCatching { WeatherBriefingSlot(
                    id=obj.getInt("id"),hour=obj.getInt("hour"),minute=obj.getInt("minute"),
                    weekdays=obj.getInt("weekdays"),enabled=obj.optBoolean("enabled",true)
                ) }.getOrNull()
            }.distinctBy {it.id}.take(5)
        }.getOrDefault(emptyList())
    }
    fun save(context:Context,slots:List<WeatherBriefingSlot>) {
        require(slots.size<=5 && slots.distinctBy{it.id}.size==slots.size)
        val arr=JSONArray()
        slots.forEach { s -> arr.put(JSONObject().put("id",s.id).put("hour",s.hour).put("minute",s.minute)
            .put("weekdays",s.weekdays).put("enabled",s.enabled)) }
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("slots",arr.toString()).apply()
        WeatherBriefingScheduler.reschedule(context)
    }
}
