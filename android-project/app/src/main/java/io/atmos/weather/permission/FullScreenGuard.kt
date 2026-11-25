package io.atmos.weather.permission

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.atmos.weather.MainActivity
import io.atmos.weather.R

/**
 * Samsung (One UI 6+, Android 14+) resets the app's "全螢幕通知" special access every time the app
 * is updated outside Google Play, so earthquake alerts and alarms silently lose their lock-screen
 * takeover. An app cannot grant itself that access; what it can do is notice the loss right after
 * the update and take the person straight back to the one switch, before an earthquake needs it.
 */
object FullScreenGuard {
    private const val PREFS = "thinn_settings"
    private const val HAD = "full_screen_intent_was_granted"
    private const val LOST = "full_screen_intent_lost_after_update"
    private const val CHANNEL = "thinn_permission_guard"
    private const val NOTICE_ID = 4417

    fun granted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return true
        return runCatching { nm.canUseFullScreenIntent() }.getOrDefault(true)
    }

    fun settingsIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Every time the app is in front: remember that access is on, and clear a pending notice. */
    fun onResume(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (granted(activity)) {
            prefs.edit().putBoolean(HAD, true).putBoolean(LOST, false).apply()
            activity.getSystemService(NotificationManager::class.java)?.cancel(NOTICE_ID)
            return false
        }
        // Lost after an update: say so once, with the button that opens the switch.
        return prefs.getBoolean(LOST, false)
    }

    /** Called once the person has seen the in-app prompt. */
    fun acknowledge(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(LOST, false).apply()
    }

    /** Right after an update: if access was on before and is off now, post a one-tap notice. */
    fun afterUpdate(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (granted(ctx)) { prefs.edit().putBoolean(HAD, true).apply(); return }
        if (!prefs.getBoolean(HAD, false)) return
        prefs.edit().putBoolean(LOST, true).apply()
        if (!PermissionGuide.canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "權限提醒", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, NOTICE_ID, settingsIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val app = PendingIntent.getActivity(ctx, NOTICE_ID + 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = "更新後系統關閉了「全螢幕通知」，鎖定時地震速報與鬧鐘不會直接亮起。點一下重新開啟。"
        nm.notify(NOTICE_ID, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle("請重新開啟全螢幕通知")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .addAction(0, "開啟設定", open)
            .addAction(0, "打開 thinn-khì", app)
            .setAutoCancel(true)
            .build())
    }
}

/** MY_PACKAGE_REPLACED: the moment Samsung has just reset the access. */
class FullScreenGuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) runCatching { FullScreenGuard.afterUpdate(context) }
    }
}
