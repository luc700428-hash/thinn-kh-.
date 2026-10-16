package io.atmos.weather.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.atmos.weather.MainActivity
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.domain.model.GeoPoint
import java.util.concurrent.TimeUnit

class CwaAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val prefs = context.getSharedPreferences("atmos_settings", Context.MODE_PRIVATE)
        val cityName = prefs.getString("city_name", null) ?: return Result.success()
        if (!cityName.startsWith("台灣") && !cityName.startsWith("臺灣")) return Result.success()
        val point = if (prefs.contains("lat") && prefs.contains("lon"))
            GeoPoint(Double.fromBits(prefs.getLong("lat",0)),Double.fromBits(prefs.getLong("lon",0))) else null
        val fetched = CwaWarningFeed.fetch(cityName,point)
        val alerts = fetched.warnings
        context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).edit()
            .putLong("cwa_last_check",System.currentTimeMillis())
            .putInt("cwa_last_count",alerts.size)
            .putBoolean("cwa_fetch_ok",fetched.checkSucceeded)
            .putString("cwa_fetch_status",fetched.detail).apply()
        val settings = context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
        ThinnNotificationChannels.ensure(context)
        val action = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val now = java.time.Instant.now()
        val newAlerts = alerts.take(5).filter { alert ->
            val age = now.epochSecond - alert.issuedAt.epochSecond
            val keyId = "${alert.title}|${alert.issuedAt.epochSecond}".hashCode().toString()
            age in 0..(24 * 3600) && !alert.title.contains("解除") &&
                !settings.getBoolean("cwa_seen_$keyId", false)
        }
        if (newAlerts.isEmpty()) return Result.success()
        val groupKey = "thinn_khi_cwa_local_alerts"
        var sentCount = 0
        for (alert in newAlerts) {
            // Do not turn cancellation posts into new dangerous-weather alarm notifications.
            if (alert.title.contains("解除")) continue
            val key = "${alert.title}|${alert.issuedAt.epochSecond}"
            val keyId = key.hashCode().toString()
            if (settings.getBoolean("cwa_seen_$keyId",false)) continue
            val notification = NotificationCompat.Builder(context, ThinnNotificationChannels.channelId(context, ThinnNotificationChannels.Type.WARNINGS))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("中央氣象署：${alert.title}")
                .setContentText(alert.summary.take(120).ifBlank { "請查看官方最新警特報資訊。" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.summary.take(500)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setGroup(groupKey)
                .setAutoCancel(true).setContentIntent(action).build()
            if (WeatherNotificationDispatcher.post(
                    context, keyId.hashCode(), notification, alert.title, cityName,
                    XiaomiFocusBridge.Kind.WEATHER_WARNING
                )) {
                sentCount++
                settings.edit().putBoolean("cwa_seen_$keyId", true).apply()
            }
        }
        if (sentCount > 1) {
            val summaryStyle=NotificationCompat.InboxStyle().setBigContentTitle("中央氣象署 · ${sentCount} 則相關特報")
            newAlerts.forEach { summaryStyle.addLine(it.title) }
            val summary = NotificationCompat.Builder(context,ThinnNotificationChannels.channelId(context, ThinnNotificationChannels.Type.WARNINGS))
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("中央氣象署 · ${sentCount} 則特報")
                    .setContentText("點選查看不同警特報與發布區域")
                    .setStyle(summaryStyle).setGroup(groupKey).setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).setContentIntent(action).build()
            WeatherNotificationDispatcher.post(
                context, 601102, summary, "${sentCount} 則中央氣象署特報", cityName,
                XiaomiFocusBridge.Kind.WARNING_SUMMARY
            )
        }
        return Result.success()
    }
    companion object {
        fun checkNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<CwaAlertWorker>()
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("cwa_alert_manual_check",
                androidx.work.ExistingWorkPolicy.REPLACE, request)
        }
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CwaAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("cwa_alert_rss", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
