package io.atmos.weather.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.atmos.weather.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Persist only user-created alarms; notification permission and Android exact-alarm access remain user controlled. */
object WeatherAlarmStore {
    private const val KEY = "alarms_v23"
    fun load(ctx: Context): List<WeatherAlarm> = runCatching {
        val array=JSONArray(ctx.getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE).getString(KEY,"[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val value=array.getJSONObject(index)
                val arr=value.optJSONArray("days") ?: JSONArray()
                WeatherAlarm(value.getInt("id"),value.getInt("hour"),value.getInt("minute"),
                    value.optString("title","鬧鐘"),
                    (0 until arr.length()).map { arr.getInt(it) }.filter { it in 1..7 }.toSet(),
                    value.optBoolean("enabled",true),value.optBoolean("weatherVoice",true),
                    value.optBoolean("snooze",true))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
    fun save(ctx: Context, alarms: List<WeatherAlarm>) {
        val array=JSONArray()
        alarms.forEach { a ->
            array.put(JSONObject().apply {
                put("id",a.id);put("hour",a.hour);put("minute",a.minute);put("title",a.title)
                put("days",JSONArray().apply { a.days.sorted().forEach {put(it)} })
                put("enabled",a.enabled);put("weatherVoice",a.weatherVoice);put("snooze",a.snooze)
            })
        }
        ctx.getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE).edit().putString(KEY,array.toString()).apply()
    }
}

/** Calendar-based recurrence accounts for day rollover and the device's current timezone / DST rules. */
object WeatherAlarmScheduler {
    private const val ACTION_RING="io.atmos.weather.WEATHER_ALARM_RING"
    private const val EXTRA_ID="alarm_id"
    private fun pending(ctx: Context,id:Int, snoozed:Boolean=false): PendingIntent {
        val intent=Intent(ctx,WeatherAlarmReceiver::class.java).setAction(ACTION_RING)
            .putExtra(EXTRA_ID,id).putExtra("snoozed",snoozed)
        return PendingIntent.getBroadcast(ctx,id+(if(snoozed)50000 else 0),intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun schedule(ctx: Context, alarm: WeatherAlarm) {
        // Do not clear an already-requested ten-minute snooze when the app restarts.
        val manager=ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pending(ctx,alarm.id))
        if (!alarm.enabled) return
        val at=WeatherAlarmTime.next(alarm,ZonedDateTime.now()) ?: return
        set(ctx,at.toEpochMilli(),pending(ctx,alarm.id))
    }
    private fun set(ctx: Context,at:Long,intent:PendingIntent) {
        val manager=ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact=Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        try {
            if (exact) manager.setAlarmClock(AlarmManager.AlarmClockInfo(at,null),intent)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent)
        } catch (_: SecurityException) {
            // Do not claim minute-perfect wakeup without special alarm permission.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent)
        }
    }
    fun scheduleSnooze(ctx:Context,id:Int) {
        set(ctx,System.currentTimeMillis()+10*60_000L,pending(ctx,id,true))
    }
    fun cancel(ctx: Context,id:Int) {
        val manager=ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pending(ctx,id))
        manager.cancel(pending(ctx,id,true))
    }
    fun reconcile(ctx:Context) { WeatherAlarmStore.load(ctx).forEach {schedule(ctx,it)} }
    fun canBeExact(ctx:Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
}

/** Only user alarms (not earthquake alerts) may request an alarm-style full-screen notification. */
class WeatherAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_TIME_CHANGED -> {
                WeatherAlarmScheduler.reconcile(ctx);return
            }
        }
        val id=intent.getIntExtra("alarm_id",-1)
        val snoozed=intent.getBooleanExtra("snoozed",false)
        val alarm=WeatherAlarmStore.load(ctx).firstOrNull {it.id==id && (it.enabled || snoozed)} ?:return
        if (!snoozed) {
            if (alarm.days.isEmpty()) {
                val updated=WeatherAlarmStore.load(ctx).map {if(it.id==id)it.copy(enabled=false) else it}
                WeatherAlarmStore.save(ctx,updated)
            } else WeatherAlarmScheduler.schedule(ctx,alarm)
        }
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel="thinn_weather_alarm_v23"
        nm.createNotificationChannel(NotificationChannel(channel,"天氣鬧鐘",NotificationManager.IMPORTANCE_HIGH).apply {
            description="由使用者建立的日常鬧鐘；提示音和全螢幕權限受 Android 設定控制"
            enableVibration(true)
        })
        val open=Intent(ctx,WeatherAlarmRingActivity::class.java).putExtra("alarm_id",id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val operation=PendingIntent.getActivity(ctx,id,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(ctx,channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.title.ifBlank {"天氣鬧鐘"})
            .setContentText("${"%02d".format(alarm.hour)}:${"%02d".format(alarm.minute)} · 點擊停止或稍後提醒")
            .setContentIntent(operation).setAutoCancel(false).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(operation,true).build()
        runCatching {nm.notify(740000+id,n)}
    }
}
