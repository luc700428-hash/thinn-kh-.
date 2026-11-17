package io.atmos.weather.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.atmos.weather.R

/**
 * Android 16 Live Updates for everything in thinn-khì that is ongoing and time-critical:
 * software download, earthquake early warning countdown, verified rain arrival, a running
 * timer and a snoozed alarm.
 *
 * A notification is only promoted (status-bar chip, top of the shade, lock screen) when it
 * meets the platform rules, so every producer goes through [promote]: it must be ongoing,
 * have a title, use a standard / big-text / progress style, not be colorized, and the app must
 * hold POST_PROMOTED_NOTIFICATIONS with the user's per-app switch on. Older Android versions
 * keep the same ongoing notification without the chip.
 */
object LiveUpdates {
    const val SNOOZE_ID=740900
    const val ACTION_DISMISS_SNOOZE="io.atmos.weather.LIVE_DISMISS_SNOOZE"

    /** True when Android will actually show our Live Updates (API 36 and the user allows it). */
    fun available(ctx:Context):Boolean {
        if(Build.VERSION.SDK_INT<36)return false
        return runCatching {(ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canPostPromotedNotifications()}
            .getOrDefault(false)
    }

    /** Where this device stands, in the order the user has to fix things. */
    enum class Status { NOTIFICATIONS_OFF, ON, OFF, ONGOING_ONLY }

    fun status(ctx:Context):Status {
        val manager=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(!runCatching{manager.areNotificationsEnabled()}.getOrDefault(true))return Status.NOTIFICATIONS_OFF
        if(Build.VERSION.SDK_INT<36)return Status.ONGOING_ONLY
        // Some Android 16 tablets and vendor ROMs ship without the promotion API wired up.
        return runCatching{if(manager.canPostPromotedNotifications())Status.ON else Status.OFF}.getOrDefault(Status.ONGOING_ONLY)
    }

    /**
     * Opens the best settings page this device really has: the Live Updates switch, then the
     * app's notification page, then the app's info page. Many vendor ROMs and tablets lack the
     * first one, which used to make the button do nothing. Returns false only if none opened.
     */
    fun openSettings(ctx:Context):Boolean {
        val pkg=ctx.packageName
        val candidates=buildList {
            if(Build.VERSION.SDK_INT>=36)add(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,pkg))
            add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,pkg))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",pkg,null)))
        }
        for(intent in candidates) {
            val opened=runCatching{ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true}.getOrDefault(false)
            if(opened)return true
        }
        return false
    }

    const val TEST_ID=740990

    /**
     * A one-minute sample Live Update, so the user can see what the chip and lock-screen card look
     * like on this device. Without promotion support it still appears as an ongoing notification.
     */
    fun sendTest(ctx:Context):Boolean {
        ThinnNotificationChannels.ensure(ctx)
        val end=System.currentTimeMillis()+60_000L
        val open=PendingIntent.getActivity(ctx,TEST_ID,Intent(ctx,io.atmos.weather.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b=NotificationCompat.Builder(ctx,ThinnNotificationChannels.channelId(ctx,ThinnNotificationChannels.Type.BRIEFINGS))
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle("即時動態測試")
            .setContentText("這是一分鐘的示範倒數，結束後自動消失")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setTimeoutAfter(61_000L)
        progress(b,0,60,0xFF328E74.toInt(),indeterminate=true)
        promote(b,null,end)
        return runCatching {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(TEST_ID,b.build());true
        }.getOrDefault(false)
    }

    /** Opens the system page where the user turns Live Updates on for this app. */
    fun settingsIntent(ctx:Context):Intent =
        if(Build.VERSION.SDK_INT>=36) Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Applies the promotion request. [chip] is the short text in the status-bar chip (at most
     * about 7 characters are shown); with [countdownTo] the chip counts down to that time instead.
     */
    fun promote(builder:NotificationCompat.Builder,chip:String?,countdownTo:Long?=null):NotificationCompat.Builder {
        builder.setOngoing(true).setColorized(false).setOnlyAlertOnce(true).setRequestPromotedOngoing(true)
        if(countdownTo!=null&&countdownTo>System.currentTimeMillis()) {
            builder.setWhen(countdownTo).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else if(!chip.isNullOrBlank()) builder.setShortCriticalText(chip.take(7))
        return builder
    }

    /**
     * Android 16 progress style: a segmented bar with an optional moving tracker icon. Pre-16
     * devices get the classic progress bar from the same call.
     */
    fun progress(builder:NotificationCompat.Builder,value:Int,max:Int,color:Int,indeterminate:Boolean=false,
        points:List<Int> = emptyList(),tracker:androidx.core.graphics.drawable.IconCompat?=null):NotificationCompat.Builder {
        val safeMax=max.coerceAtLeast(1);val safe=value.coerceIn(0,safeMax)
        if(Build.VERSION.SDK_INT>=36) {
            val style=NotificationCompat.ProgressStyle()
                .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(safeMax).setColor(color)))
                .setProgressIndeterminate(indeterminate).setStyledByProgress(true)
            points.filter{it in 1 until safeMax}.forEach{style.addProgressPoint(NotificationCompat.ProgressStyle.Point(it).setColor(color))}
            if(tracker!=null)style.setProgressTrackerIcon(tracker)
            if(!indeterminate)style.setProgress(safe)
            builder.setStyle(style)
        } else builder.setProgress(safeMax,safe,indeterminate)
        return builder
    }

    /** A snoozed alarm: the chip counts down to the next ring, with a way to cancel it. */
    fun publishSnooze(ctx:Context,alarmId:Int,title:String,ringAt:Long,minutes:Int):Boolean {
        val now=System.currentTimeMillis()
        if(ringAt<=now||minutes<=0)return false
        ThinnNotificationChannels.ensure(ctx)
        val dismiss=PendingIntent.getBroadcast(ctx,alarmId+300000,
            Intent(ctx,LiveUpdateActionReceiver::class.java).setAction(ACTION_DISMISS_SNOOZE).putExtra("alarm_id",alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(ctx,SNOOZE_ID,Intent(ctx,io.atmos.weather.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val clock=java.text.SimpleDateFormat("HH:mm",java.util.Locale.TAIWAN).format(java.util.Date(ringAt))
        val total=minutes*60
        val elapsed=(total-((ringAt-now)/1000L).toInt()).coerceIn(0,total)
        val b=NotificationCompat.Builder(ctx,ThinnNotificationChannels.channelId(ctx,ThinnNotificationChannels.Type.BRIEFINGS))
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title.ifBlank{"天氣鬧鐘"}+" · 稍後提醒")
            .setContentText("$clock 再次響鈴")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"關閉鬧鐘",dismiss)
            .setTimeoutAfter(ringAt-now+5_000L)
        progress(b,elapsed,total,0xFF328E74.toInt())
        promote(b,null,ringAt)
        return runCatching {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(SNOOZE_ID+alarmId,b.build());true
        }.getOrDefault(false)
    }

    fun clearSnooze(ctx:Context,alarmId:Int) {
        runCatching {(ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(SNOOZE_ID+alarmId)}
    }
}

/** Buttons on Live Updates. Not exported: only our own PendingIntents reach it. */
class LiveUpdateActionReceiver:BroadcastReceiver() {
    override fun onReceive(ctx:Context,intent:Intent) {
        if(intent.action==LiveUpdates.ACTION_DISMISS_SNOOZE) {
            val id=intent.getIntExtra("alarm_id",-1)
            if(id<0)return
            io.atmos.weather.alarm.WeatherAlarmScheduler.cancelSnooze(ctx,id)
            LiveUpdates.clearSnooze(ctx,id)
        }
    }
}
