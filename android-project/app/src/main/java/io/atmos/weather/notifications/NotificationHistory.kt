package io.atmos.weather.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The last 60 notifications this app posted, for the in-app notification center. Local only. */
object NotificationHistory {
    data class Entry(val at: Long, val title: String, val detail: String, val kind: String)
    private const val PREF = "thinn_notification_history_v18"
    private const val MAX = 60

    @Synchronized fun record(ctx: Context, title: String, detail: String, kind: XiaomiFocusBridge.Kind, at: Long = System.currentTimeMillis()) {
        if (kind in setOf(XiaomiFocusBridge.Kind.USER_TEST, XiaomiFocusBridge.Kind.TIMER_RUNNING, XiaomiFocusBridge.Kind.AMBIENT_WEATHER)) return
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(p.getString("items", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray().put(JSONObject().put("at", at).put("title", title.take(120)).put("detail", detail.take(400)).put("kind", kind.name))
        for (i in 0 until minOf(old.length(), MAX - 1)) next.put(old.get(i))
        p.edit().putString("items", next.toString()).apply()
    }
    fun all(ctx: Context): List<Entry> = runCatching {
        val a = JSONArray(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("items", "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Entry(it.optLong("at"), it.optString("title"), it.optString("detail"), it.optString("kind")) } }
    }.getOrDefault(emptyList())
    fun clear(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("items").apply()
    /** iOS groups notifications by app section; these are ours. */
    fun group(kind: String) = when (kind) {
        "SEISMIC_EEW", "STATION_DETECTION", "EARTHQUAKE_REPORT" -> "地震"
        "ALARM_RINGING" -> "鬧鐘"
        "WEATHER_BRIEFING", "VERIFIED_MINUTE_RAIN" -> "天氣提醒"
        "WEATHER_WARNING", "WARNING_SUMMARY" -> "警特報"
        "TASK_REMINDER" -> "待辦"
        else -> "其他"
    }
}
