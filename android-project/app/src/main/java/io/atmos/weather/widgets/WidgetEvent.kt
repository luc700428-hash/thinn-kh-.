package io.atmos.weather.widgets

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * What every widget shows instead of its usual first lines while something important is going
 * on: a live earthquake warning, a weather warning in effect for the chosen county, or a fresh
 * earthquake report. Each has an end time, so widgets return to normal on their own; a refresh is
 * scheduled for the moment the shown event changes.
 */
object WidgetEvent {
    enum class Kind(val label: String) { EEW("地震速報"), WARNING("特報"), QUAKE("最新地震") }
    data class Event(val kind: Kind, val title: String, val detail: String, val until: Long)

    private const val STORE = "thinn_widget_event_v1"
    /** A quake report stays on the widgets this long after it arrives. */
    const val QUAKE_MS = 30 * 60_000L
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    private fun p(ctx: Context) = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** A live warning was raised or updated. It shows until it expires, plus a minute for the shaking. */
    fun eew(ctx: Context, title: String, detail: String, until: Long) {
        p(ctx).edit().putString("eew", JSONObject().put("t", title).put("d", detail).put("u", until).toString()).apply()
        changed(ctx)
    }
    fun endEew(ctx: Context) { if (p(ctx).contains("eew")) { p(ctx).edit().remove("eew").apply(); changed(ctx) } }

    fun quake(ctx: Context, title: String, detail: String, now: Long = System.currentTimeMillis()) {
        p(ctx).edit().putString("quake", JSONObject().put("t", title).put("d", detail).put("u", now + QUAKE_MS).toString()).apply()
        changed(ctx)
    }

    /** Warnings for the widgets' county: (title, startsAt, expiresAt). Replaces the previous list. */
    fun warnings(ctx: Context, items: List<Triple<String, Instant, Instant>>) {
        val a = JSONArray()
        items.sortedBy { it.second }.take(6).forEach { (t, s, e) -> a.put(JSONObject().put("t", t).put("s", s.toEpochMilli()).put("e", e.toEpochMilli())) }
        val text = a.toString()
        if (p(ctx).getString("warnings", "[]") == text) return
        p(ctx).edit().putString("warnings", text).apply()
        changed(ctx)
    }

    /** The one event to show now, most urgent first; null when there is none. */
    fun current(ctx: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Event? =
        pick(p(ctx).getString("eew", null), p(ctx).getString("warnings", null), p(ctx).getString("quake", null), now, zone)

    /** Every event showing now, most urgent first: the widgets rotate through them. */
    fun all(ctx: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<Event> =
        pickAll(p(ctx).getString("eew", null), p(ctx).getString("warnings", null), p(ctx).getString("quake", null), now, zone)

    internal fun pickAll(eew: String?, warnings: String?, quake: String?, now: Long, zone: ZoneId): List<Event> {
        fun single(raw: String?, kind: Kind): Event? = runCatching {
            val o = JSONObject(raw ?: return null)
            Event(kind, o.getString("t"), o.optString("d"), o.getLong("u")).takeIf { now < it.until }
        }.getOrNull()
        val out = ArrayList<Event>()
        single(eew, Kind.EEW)?.let { out += it }
        runCatching {
            val a = JSONArray(warnings ?: "[]")
            val list = (0 until a.length()).map { a.getJSONObject(it) }.filter { now < it.getLong("e") }
            val shown = list.filter { it.getLong("s") <= now } + list.filter { it.getLong("s") > now && it.getLong("s") - now <= 12 * 3600_000L }
            shown.distinctBy { it.getString("t") }.forEach { w ->
                val start = w.getLong("s"); val end = w.getLong("e")
                val detail = if (start > now) "預告 · ${hm.format(Instant.ofEpochMilli(start).atZone(zone))} 起生效"
                    else "有效至 ${hm.format(Instant.ofEpochMilli(end).atZone(zone))}"
                out += Event(Kind.WARNING, w.getString("t"), detail, if (start > now) start else end)
            }
        }
        single(quake, Kind.QUAKE)?.let { out += it }
        return out
    }

    internal fun pick(eew: String?, warnings: String?, quake: String?, now: Long, zone: ZoneId): Event? {
        fun single(raw: String?, kind: Kind): Event? = runCatching {
            val o = JSONObject(raw ?: return null)
            Event(kind, o.getString("t"), o.optString("d"), o.getLong("u")).takeIf { now < it.until }
        }.getOrNull()
        single(eew, Kind.EEW)?.let { return it }
        runCatching {
            val a = JSONArray(warnings ?: "[]")
            val list = (0 until a.length()).map { a.getJSONObject(it) }.filter { now < it.getLong("e") }
            // In effect first; otherwise the next one starting within 12 hours, marked 預告.
            val active = list.filter { it.getLong("s") <= now }
            val shown = active.firstOrNull() ?: list.filter { it.getLong("s") - now <= 12 * 3600_000L }.minByOrNull { it.getLong("s") }
            if (shown != null) {
                val start = shown.getLong("s"); val end = shown.getLong("e")
                val more = (active.size - 1).coerceAtLeast(0)
                val detail = (if (start > now) "預告 · ${hm.format(Instant.ofEpochMilli(start).atZone(zone))} 起生效"
                    else "有效至 ${hm.format(Instant.ofEpochMilli(end).atZone(zone))}") + (if (more > 0) " · 另有 $more 則" else "")
                return Event(Kind.WARNING, shown.getString("t"), detail, if (start > now) start else end)
            }
        }
        return single(quake, Kind.QUAKE)
    }

    /** Next moment the shown event changes, so the widgets can be refreshed right then. */
    private fun nextChange(ctx: Context, now: Long): Long? {
        val times = mutableListOf<Long>()
        listOf("eew", "quake").forEach { k -> p(ctx).getString(k, null)?.let { runCatching { times += JSONObject(it).getLong("u") } } }
        runCatching {
            val a = JSONArray(p(ctx).getString("warnings", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); times += o.getLong("s"); times += o.getLong("e") }
        }
        return times.filter { it > now }.minOrNull()
    }

    private fun changed(ctx: Context) {
        val app = ctx.applicationContext
        WidgetSync.refreshCached(app)
        val now = System.currentTimeMillis()
        val next = nextChange(app, now) ?: return
        runCatching {
            WorkManager.getInstance(app).enqueueUniqueWork("thinn-widget-event-change", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetEventWorker>().setInitialDelay((next - now + 1_000L).coerceAtLeast(1_000L), TimeUnit.MILLISECONDS).build())
        }
    }

    /** Called when the scheduled moment arrives: redraw, then plan the next change. */
    internal fun tick(ctx: Context) = changed(ctx)
}

class WidgetEventWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { WidgetEvent.tick(applicationContext); return Result.success() }
}
