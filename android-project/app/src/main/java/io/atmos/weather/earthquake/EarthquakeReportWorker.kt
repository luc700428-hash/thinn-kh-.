package io.atmos.weather.earthquake

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.atmos.weather.MainActivity
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.permission.PermissionGuide
import java.time.Instant
import java.util.concurrent.TimeUnit

/** 15-minute best-effort *report* notifications. These are NOT second-level earthquake alerts. */
class EarthquakeReportWorker(ctx: Context, params: WorkerParameters): CoroutineWorker(ctx,params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs=ctx.getSharedPreferences("thinn_seismic_v15",Context.MODE_PRIVATE)
        if (!prefs.getBoolean("earthquake_report_notify",false) || !SeismicAlertPolicy.alertEnabled(ctx,SeismicAlertPolicy.Scenario.REPORT)) return Result.success()
        val official = runCatching { CwaEarthquakeRepository.latest(ctx) }.getOrNull()
        val isOfficial = !official.isNullOrEmpty()
        val reports = if (isOfficial) official!! else try { ExpTechPublicEarthquakeReports.latest() } catch (_:Exception) { return Result.retry() }
        val latest = reports.firstOrNull() ?: return Result.success()
        val saved = prefs.getString("last_report_id",null)
        if (saved == null) {
            // First run must never fire old reports as breaking seismic alarms.
            prefs.edit().putString("last_report_id",latest.id).apply()
            return Result.success()
        }
        if (latest.id == saved) return Result.success()
        // Always update cursor so old events do not repeatedly notify.
        prefs.edit().putString("last_report_id",latest.id).apply()
        val age = Instant.now().epochSecond - latest.origin.epochSecond
        if (age !in 0L..3600L || !PermissionGuide.canNotify(ctx)) return Result.success()
        val minMagnitude = prefs.getFloat("report_min_magnitude",4.5f)
        if (latest.magnitude < minMagnitude) return Result.success()
        SeismicAlertPolicy.ensureChannels(ctx)
        val intent=PendingIntent.getActivity(ctx,18015,
            Intent(ctx,MainActivity::class.java).putExtra("open_earthquake_center",true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title="${if(isOfficial) "中央氣象署地震報告" else "第三方地震報告"} · 規模 ${"%.1f".format(java.util.Locale.US,latest.magnitude)}"
        val detail="${latest.location} · ${if(isOfficial) "官方震後報告" else "ExpTech 第三方彙整"}（非即時預警）"
        val notif=NotificationCompat.Builder(ctx,SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.REPORT))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(latest.reportText))
            .setAutoCancel(true).setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        WeatherNotificationDispatcher.post(ctx,18015,notif,title,detail,XiaomiFocusBridge.Kind.EARTHQUAKE_REPORT)
        // The origin label follows the dataset actually returned, never a guess.
        if(SeismicSpeech.enabled(ctx,"report_voice_enabled"))
            SeismicSpeech.speak(ctx, if(isOfficial) {
                "中央氣象署震後地震報告。${latest.location}，規模${"%.1f".format(java.util.Locale.TAIWAN,latest.magnitude)}。此為震後報告，非提前預警。"
            } else SeismicSpeech.report(latest))
        return Result.success()
    }
    companion object {
        fun schedule(context:Context) {
            val req=PeriodicWorkRequestBuilder<EarthquakeReportWorker>(15,TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("thinn_cwa_eq_reports_v15",ExistingPeriodicWorkPolicy.KEEP,req)
        }
    }
}
