package io.atmos.weather.earthquake

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import io.atmos.weather.comfort.ComfortStore

/**
 * Extra care around a real earthquake warning:
 *  - a strong, alarm-class vibration that is felt even with the sound off or under DND;
 *  - the estimated shaking at the user's own places (家 / 公司) shown in the warning;
 *  - once the warning ends, a short safety checklist with a one-tap 「報平安」 message.
 */
internal object SeismicCare {
    private var vibrator: Vibrator? = null

    fun vibrate(ctx: Context, local: String?) {
        val rank = local?.let { maxOf(CwaEarthquakeParser.intensityRank(it), it.firstOrNull()?.digitToIntOrNull() ?: 0) } ?: 0
        val v = runCatching {
            if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        }.getOrNull() ?: return
        // Long-short-short like an emergency alert, harder for stronger shaking; ~20 s at most.
        val strong = rank >= 4
        val timings = longArrayOf(0, 700, 220, 260, 160, 260, 600)
        val amps = intArrayOf(0, if (strong) 255 else 200, 0, if (strong) 255 else 170, 0, if (strong) 255 else 170, 0)
        val effect = VibrationEffect.createWaveform(timings, amps, 0)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        runCatching { @Suppress("DEPRECATION") v.vibrate(effect, attrs) }
        vibrator = v
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ stopVibration() }, 20_000)
    }
    fun stopVibration() { runCatching { vibrator?.cancel() }; vibrator = null }

    /** The user's saved places, as places the warning model understands. */
    fun places(ctx: Context): List<SeismicPlace> = listOfNotNull(
        ComfortStore.place(ctx, "home")?.let { SeismicPlace("家", it.lat, it.lon, "我的地點") },
        ComfortStore.place(ctx, "work")?.let { SeismicPlace("公司", it.lat, it.lon, "我的地點") })

    /** "家 3 級 · 12 秒｜公司 2 級 · 20 秒" for the warning screen; null when nothing to show. */
    fun placesLine(ctx: Context, incident: EewIncident, now: Long = System.currentTimeMillis()): String? {
        val parts = places(ctx).mapNotNull { p ->
            val pr = SeismicLocalPrediction.resolve(incident, p, now)
            val grade = pr.intensity?.let { "${SeismicDisplayFormat.symbol(it)} 級" } ?: return@mapNotNull null
            val left = pr.arrivalAtMs?.let { ((it - now) / 1000).toInt() }
            "${p.name} $grade" + (left?.let { if (it > 0) " · $it 秒" else " · 已抵達" } ?: "")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("｜")
    }

    /** After a real warning: what to check, and a ready-made "I'm safe" message to share. */
    fun afterQuake(ctx: Context, incident: EewIncident) {
        if (incident.test || incident.cancelled) return
        if (!io.atmos.weather.permission.PermissionGuide.canNotify(ctx)) return
        val message = "我平安。剛剛${SeismicDisplayFormat.location(incident.epicenter)}附近發生地震，我這裡沒事。（thinn-khì 報平安）"
        val share = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "報平安")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val sharePi = PendingIntent.getActivity(ctx, 29301, share, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        io.atmos.weather.notifications.ThinnNotificationChannels.ensure(ctx)
        val n = NotificationCompat.Builder(ctx, io.atmos.weather.notifications.ThinnNotificationChannels.channelId(ctx,
                io.atmos.weather.notifications.ThinnNotificationChannels.Type.EARTHQUAKES))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("搖晃過後，先確認安全")
            .setContentText("關瓦斯、檢查出口與家人，留意餘震")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "1. 關閉瓦斯與電源開關，不要點火\n2. 穿鞋，小心玻璃碎片\n3. 打開門確保出口，檢查家人與寵物\n4. 遠離窗戶、招牌與高牆，留意餘震\n5. 查看地震報告與官方訊息"))
            .addAction(android.R.drawable.ic_menu_share, "報平安", sharePi)
            .setAutoCancel(true).setTimeoutAfter(6 * 3600_000L)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(29300, n) }
    }
}
