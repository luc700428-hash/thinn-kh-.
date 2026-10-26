package io.atmos.weather.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.atmos.weather.MainActivity

/** Typed input adapters only. Nothing is scheduled or guessed here; real producers must call them. */
object IslandEventPublisher {
    /** A provider-issued precipitation estimate, not a made-up countdown from an hourly forecast. */
    data class VerifiedRainEvidence(
        val source: Source,
        val issuedAtMillis: Long,
        val minutesUntilRain: Int,
        val locality: String,
        val expiresAtMillis: Long
    ) {
        enum class Source { CWA_MINUTE_NOWCAST, VERIFIED_RADAR_NOWCAST }
    }

    internal fun verifiedRainText(value: VerifiedRainEvidence, now: Long): String? {
        if (now - value.issuedAtMillis !in 0L..(10 * 60_000L)) return null
        if (value.expiresAtMillis < now || value.minutesUntilRain !in 1..30) return null
        if (value.locality.isBlank()) return null
        return when (value.minutesUntilRain) {
            in 1..5 -> "預計 5 分鐘內下雨"
            in 6..15 -> "預計 15 分鐘內下雨"
            else -> "30 分鐘內可能降雨"
        }
    }

    private fun openApp(ctx: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        ctx, id, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Never called from an ordinary hourly probability forecast. */
    fun publishVerifiedRain(ctx: Context, evidence: VerifiedRainEvidence): Boolean {
        val now = System.currentTimeMillis()
        val text = verifiedRainText(evidence, now) ?: return false
        ThinnNotificationChannels.ensure(ctx)
        val id = 650301
        val message = NotificationCompat.Builder(ctx,
            ThinnNotificationChannels.channelId(ctx, ThinnNotificationChannels.Type.WARNINGS))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(text)
            .setContentText("${evidence.locality} · 經驗證的短時降雨預測")
            .setContentIntent(openApp(ctx, id))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setTimeoutAfter((evidence.expiresAtMillis - now).coerceAtMost(30 * 60_000L))
            .setOnlyAlertOnce(true).setAutoCancel(true).build()
        return WeatherNotificationDispatcher.post(ctx,id,message,text,evidence.locality,
            XiaomiFocusBridge.Kind.VERIFIED_MINUTE_RAIN)
    }

    /** Connect only to a real timer state owner. Timer scheduling is outside this module. */
    fun publishRunningTimer(ctx: Context, remainingSeconds: Long): Boolean {
        if (remainingSeconds !in 1L..86_400L) return false
        val id = 650302
        ThinnNotificationChannels.ensure(ctx)
        val text = "計時器剩餘 ${remainingSeconds / 60} 分 ${remainingSeconds % 60} 秒"
        val message = NotificationCompat.Builder(ctx,
            ThinnNotificationChannels.channelId(ctx, ThinnNotificationChannels.Type.BRIEFINGS))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("計時器進行中").setContentText(text)
            .setContentIntent(openApp(ctx,id)).setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis()+remainingSeconds*1000L).setUsesChronometer(true).setChronometerCountDown(true)
            .addExtras(android.os.Bundle().apply{putLong("thinn.island.deadline",System.currentTimeMillis()+remainingSeconds*1000L)})
            .setTimeoutAfter(remainingSeconds * 1000L)
            .build()
        return WeatherNotificationDispatcher.post(ctx,id,message,"計時器",text,
            XiaomiFocusBridge.Kind.TIMER_RUNNING)
    }

    /** P4 is short-lived, event-driven and data-derived. Never a permanent clock island. */
    fun publishObservedWeather(ctx: Context, locality: String, temperatureC: Int,
        condition: String, observedAtMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        if (now - observedAtMillis !in 0L..(30 * 60_000L)) return false
        if (locality.isBlank() || condition.isBlank() || temperatureC !in -45..60) return false
        val id = 650304
        ThinnNotificationChannels.ensure(ctx)
        val heading = "${locality.take(12)} ${temperatureC}°C｜${condition.take(12)}"
        val note = NotificationCompat.Builder(ctx,
            ThinnNotificationChannels.channelId(ctx, ThinnNotificationChannels.Type.BRIEFINGS))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("即時天氣").setContentText(heading)
            .addExtras(android.os.Bundle().apply{putString("thinn.island.primary","${temperatureC}°")})
            .setContentIntent(openApp(ctx,id)).setOnlyAlertOnce(true)
            .setAutoCancel(true).setTimeoutAfter(20_000L).build()
        return WeatherNotificationDispatcher.post(ctx,id,note,"即時天氣",heading,
            XiaomiFocusBridge.Kind.AMBIENT_WEATHER)
    }
}
