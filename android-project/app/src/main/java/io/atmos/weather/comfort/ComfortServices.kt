package io.atmos.weather.comfort

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.atmos.weather.AppContainer
import io.atmos.weather.MainActivity
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.permission.PermissionGuide
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** On-device settings for the r18 comfort features. No account, nothing leaves the phone. */
object ComfortStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("thinn_comfort_v18", Context.MODE_PRIVATE)
    data class Daily(val enabled: Boolean, val hour: Int, val minute: Int)
    data class Commute(val enabled: Boolean, val outHour: Int, val outMinute: Int, val backHour: Int, val backMinute: Int, val weekdays: Int)
    data class Place(val name: String, val lat: Double, val lon: Double) {
        fun city(id: String) = WeatherCity(id, name, GeoPoint(lat, lon))
    }

    fun morning(ctx: Context) = Daily(p(ctx).getBoolean("morning_on", false), p(ctx).getInt("morning_h", 7), p(ctx).getInt("morning_m", 0))
    fun evening(ctx: Context) = Daily(p(ctx).getBoolean("evening_on", false), p(ctx).getInt("evening_h", 21), p(ctx).getInt("evening_m", 0))
    fun setMorning(ctx: Context, d: Daily) { p(ctx).edit().putBoolean("morning_on", d.enabled).putInt("morning_h", d.hour).putInt("morning_m", d.minute).apply(); ComfortScheduler.reschedule(ctx) }
    fun setEvening(ctx: Context, d: Daily) { p(ctx).edit().putBoolean("evening_on", d.enabled).putInt("evening_h", d.hour).putInt("evening_m", d.minute).apply(); ComfortScheduler.reschedule(ctx) }

    /** r24: 雨來了 and 空氣品質 are on unless the user turned them off. */
    private val defaultOn = setOf(ComfortMessages.Smart.RAIN_SOON, ComfortMessages.Smart.AIR_BAD)
    fun smart(ctx: Context): Set<ComfortMessages.Smart> = ComfortMessages.Smart.entries.filter { p(ctx).getBoolean("smart_${it.name}", it in defaultOn) }.toSet()
    fun setSmart(ctx: Context, s: ComfortMessages.Smart, on: Boolean) { p(ctx).edit().putBoolean("smart_${s.name}", on).apply(); ComfortScheduler.reschedule(ctx) }
    fun quiet(ctx: Context): Pair<LocalTime, LocalTime> =
        LocalTime.of(p(ctx).getInt("quiet_from_h", 22), 0) to LocalTime.of(p(ctx).getInt("quiet_to_h", 7), 0)
    fun setQuiet(ctx: Context, fromHour: Int, toHour: Int) = p(ctx).edit().putInt("quiet_from_h", fromHour).putInt("quiet_to_h", toHour).apply()
    /** One alert of each kind per day, so nothing nags. */
    /** Rain can come and go several times a day: at most one 雨來了 every three hours. */
    fun claimRain(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        val last = p(ctx).getLong("rain_soon_at", 0L)
        if (last in 1..now && now - last < 3 * 3600_000L) return false
        p(ctx).edit().putLong("rain_soon_at", now).putString("smart_sent_RAIN_SOON", LocalDate.now().toString()).apply(); return true
    }

    fun claimSmart(ctx: Context, s: ComfortMessages.Smart, day: LocalDate): Boolean {
        val key = "smart_sent_${s.name}"
        if (p(ctx).getString(key, "") == day.toString()) return false
        p(ctx).edit().putString(key, day.toString()).apply(); return true
    }

    fun place(ctx: Context, key: String): Place? = runCatching {
        val o = JSONObject(p(ctx).getString("place_$key", null) ?: return null)
        Place(o.getString("name"), o.getDouble("lat"), o.getDouble("lon")).takeIf { it.lat.isFinite() && it.lon.isFinite() }
    }.getOrNull()
    fun setPlace(ctx: Context, key: String, place: Place?) {
        if (place == null) p(ctx).edit().remove("place_$key").apply()
        else p(ctx).edit().putString("place_$key", JSONObject().put("name", place.name).put("lat", place.lat).put("lon", place.lon).toString()).apply()
        ComfortScheduler.reschedule(ctx)
    }
    fun commute(ctx: Context) = Commute(p(ctx).getBoolean("commute_on", false), p(ctx).getInt("commute_out_h", 8), p(ctx).getInt("commute_out_m", 0),
        p(ctx).getInt("commute_back_h", 18), p(ctx).getInt("commute_back_m", 0), p(ctx).getInt("commute_days", 31))
    fun setCommute(ctx: Context, c: Commute) {
        p(ctx).edit().putBoolean("commute_on", c.enabled).putInt("commute_out_h", c.outHour).putInt("commute_out_m", c.outMinute)
            .putInt("commute_back_h", c.backHour).putInt("commute_back_m", c.backMinute).putInt("commute_days", c.weekdays).apply()
        ComfortScheduler.reschedule(ctx)
    }

    /** The phone's current place: last GPS fix, else the chosen city. */
    fun current(ctx: Context): WeatherCity? {
        val prefs = ctx.getSharedPreferences("atmos_settings", Context.MODE_PRIVATE)
        val gps = prefs.contains("gps_lat") && prefs.contains("gps_lon")
        val lat = Double.fromBits(prefs.getLong(if (gps) "gps_lat" else "lat", 0L))
        val lon = Double.fromBits(prefs.getLong(if (gps) "gps_lon" else "lon", 0L))
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat == 0.0 && lon == 0.0)) return null
        val name = (if (gps) prefs.getString("gps_name", "目前位置") else prefs.getString("city_name", "目前城市")) ?: "目前位置"
        return io.atmos.weather.location.LocationLabels.city(WeatherCity("comfort", name, GeoPoint(lat, lon)))
    }
}

object ComfortScheduler {
    private const val SMART = "thinn_comfort_smart"
    /** Next wall-clock occurrence of [hour]:[minute] on one of [weekdays] (bit 0 = Monday). */
    fun next(now: ZonedDateTime, hour: Int, minute: Int, weekdays: Int = 127): ZonedDateTime? {
        for (d in 0..7) {
            val day = now.toLocalDate().plusDays(d.toLong())
            if (weekdays and (1 shl (day.dayOfWeek.value - 1)) == 0) continue
            val t = day.atTime(hour, minute).atZone(now.zone)
            if (t.isAfter(now.plusSeconds(30))) return t
        }
        return null
    }
    private fun once(ctx: Context, name: String, kind: String, at: ZonedDateTime?) {
        val wm = WorkManager.getInstance(ctx)
        if (at == null) { wm.cancelUniqueWork(name); return }
        val delay = (at.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1000L)
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ComfortWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("kind", kind).putLong("planned", at.toInstant().toEpochMilli()).build()).build())
    }
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        runCatching {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val m = ComfortStore.morning(app); val e = ComfortStore.evening(app); val c = ComfortStore.commute(app)
            once(app, "thinn_comfort_morning", "morning", if (m.enabled) next(now, m.hour, m.minute) else null)
            once(app, "thinn_comfort_evening", "evening", if (e.enabled) next(now, e.hour, e.minute) else null)
            val ready = c.enabled && ComfortStore.place(app, "home") != null && ComfortStore.place(app, "work") != null
            // Remind 15 minutes before leaving, so there is still time to grab an umbrella.
            once(app, "thinn_comfort_commute_out", "commute_out", if (ready) next(now.plusMinutes(15), c.outHour, c.outMinute, c.weekdays)?.minusMinutes(15) else null)
            once(app, "thinn_comfort_commute_back", "commute_back", if (ready) next(now.plusMinutes(15), c.backHour, c.backMinute, c.weekdays)?.minusMinutes(15) else null)
            val wm = WorkManager.getInstance(app)
            if (ComfortStore.smart(app).isEmpty()) wm.cancelUniqueWork(SMART)
            // Every 15 minutes (Android's shortest), so 雨來了 arrives before the rain does.
            else wm.enqueueUniquePeriodicWork(SMART, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ComfortWorker>(15, TimeUnit.MINUTES).setInputData(Data.Builder().putString("kind", "smart").build()).build())
        }
    }
}

class ComfortWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val kind = inputData.getString("kind") ?: return Result.success()
        val planned = inputData.getLong("planned", 0L)
        try {
            // A job Android held back for hours would describe the wrong part of the day.
            if (planned > 0 && System.currentTimeMillis() - planned > 90 * 60_000L) return Result.success()
            if (!PermissionGuide.canNotify(ctx)) return Result.success()
            val zone = ZoneId.systemDefault(); val now = Instant.now()
            when (kind) {
                "morning", "evening" -> {
                    val city = ComfortStore.current(ctx) ?: return Result.success()
                    val w = load(ctx, city) ?: return Result.success()
                    val plain = if (kind == "morning") ComfortMessages.morning(w, now, zone, city.name)
                        else ComfortMessages.evening(w, now, zone, city.name) ?: return Result.success()
                    // The two-sentence digest leads, so the lock screen tells the whole day at a glance.
                    val digest = runCatching { io.atmos.weather.life.LifeDigest.forNotification(ctx, w, city.name) }.getOrNull()
                    val text = if (digest == null) plain else plain.copy(body = digest + "\n\n" + plain.body)
                    post(ctx, if (kind == "morning") 39001 else 39002, ThinnNotificationChannels.Type.BRIEFINGS, text, XiaomiFocusBridge.Kind.WEATHER_BRIEFING)
                }
                "smart" -> {
                    val enabled = ComfortStore.smart(ctx); if (enabled.isEmpty()) return Result.success()
                    val (from, to) = ComfortStore.quiet(ctx)
                    if (ComfortMessages.quiet(now.atZone(zone).toLocalTime(), from, to)) return Result.success()
                    val city = ComfortStore.current(ctx) ?: return Result.success()
                    val w = load(ctx, city) ?: return Result.success()
                    val day = now.atZone(zone).toLocalDate()
                    // 雨來了 from the 15-minute forecast first; the hourly check below is the fallback.
                    if (ComfortMessages.Smart.RAIN_SOON in enabled) {
                        val slots = RainSoon.fetch(city.coordinate.latitude, city.coordinate.longitude)
                        val alert = slots?.let { RainSoon.decide(it, now) }
                        if (alert != null && ComfortStore.claimRain(ctx)) {
                            post(ctx, 39100 + ComfortMessages.Smart.RAIN_SOON.ordinal, ThinnNotificationChannels.Type.SMART, alert.text(), XiaomiFocusBridge.Kind.WEATHER_BRIEFING)
                            return Result.success()
                        }
                    }
                    ComfortMessages.smart(w, now, zone, enabled).firstOrNull { (type, _) ->
                        if (type == ComfortMessages.Smart.RAIN_SOON) ComfortStore.claimRain(ctx) else ComfortStore.claimSmart(ctx, type, day)
                    }?.let { (type, text) ->
                        post(ctx, 39100 + type.ordinal, ThinnNotificationChannels.Type.SMART, text, XiaomiFocusBridge.Kind.WEATHER_BRIEFING)
                    }
                }
                "commute_out", "commute_back" -> {
                    val home = ComfortStore.place(ctx, "home") ?: return Result.success()
                    val work = ComfortStore.place(ctx, "work") ?: return Result.success()
                    val (a, b) = if (kind == "commute_out") home to work else work to home
                    val wa = load(ctx, a.city("commute_a")) ?: return Result.success()
                    val wb = load(ctx, b.city("commute_b"))
                    val text = ComfortMessages.commute(wa, wb, now.plusSeconds(15 * 60), zone, a.name, b.name, if (kind == "commute_out") "上班通勤" else "下班通勤")
                    post(ctx, if (kind == "commute_out") 39201 else 39202, ThinnNotificationChannels.Type.COMMUTE, text, XiaomiFocusBridge.Kind.WEATHER_BRIEFING)
                }
            }
            return Result.success()
        } finally {
            if (kind != "smart") ComfortScheduler.reschedule(ctx)
        }
    }
    private suspend fun load(ctx: Context, city: WeatherCity): WeatherSnapshot? = when (val r = AppContainer.repository(ctx).load(city, true)) {
        is WeatherLoadResult.Success -> r.value.takeUnless { r.stale }
        is WeatherLoadResult.Failure -> null
    }
    companion object {
        fun post(ctx: Context, id: Int, type: ThinnNotificationChannels.Type, text: ComfortText, kind: XiaomiFocusBridge.Kind): Boolean {
            ThinnNotificationChannels.ensure(ctx)
            val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // iOS-style grouping: one stack per kind, a short title and summary, the details expanded.
            val n = NotificationCompat.Builder(ctx, ThinnNotificationChannels.channelId(ctx, type))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(text.title).setContentText(text.summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.body).setSummaryText(text.summary))
                .setGroup("thinn_${type.name.lowercase()}")
                .setAutoCancel(true).setContentIntent(open)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
            return WeatherNotificationDispatcher.post(ctx, id, n, text.title, text.summary, kind)
        }
    }
}
