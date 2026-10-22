package io.atmos.weather.next.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import io.atmos.weather.next.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

/** Fully separate lab alarm store: never touches the installed production app's private alarms. */
data class NextAlarm(val id:Int,val hour:Int,val minute:Int,val label:String="鬧鐘",
    val enabled:Boolean=true,val daysMask:Int=0,val snooze:Boolean=true) {
    init { require(hour in 0..23 && minute in 0..59 && daysMask in 0..127) }
}
object NextAlarmStore {
    private const val P="next_alarms_v1"
    fun all(context:Context):List<NextAlarm> = runCatching {
        val json=JSONArray(context.getSharedPreferences(P,Context.MODE_PRIVATE).getString("items","[]"))
        (0 until json.length()).mapNotNull { index ->runCatching {
            val a=json.getJSONObject(index)
            NextAlarm(a.getInt("id"),a.getInt("hour"),a.getInt("minute"),
                a.optString("label","鬧鐘").take(50),a.optBoolean("enabled",true),
                a.optInt("daysMask",0),a.optBoolean("snooze",true))
        }.getOrNull() }.distinctBy {it.id}.take(80)
    }.getOrDefault(emptyList())
    fun save(context:Context,alarms:List<NextAlarm>) {
        val json=JSONArray();alarms.forEach {a->json.put(JSONObject().apply {
            put("id",a.id);put("hour",a.hour);put("minute",a.minute);put("label",a.label)
            put("enabled",a.enabled);put("daysMask",a.daysMask);put("snooze",a.snooze)
        })}
        check(context.getSharedPreferences(P,Context.MODE_PRIVATE).edit().putString("items",json.toString()).commit())
            {"無法儲存鬧鐘"}
    }
    fun freshId():Int = UUID.randomUUID().hashCode().and(0x7fffffff)
    fun byId(ctx:Context,id:Int):NextAlarm? = all(ctx).firstOrNull{it.id==id}
}
/** Exact alarm only. No misleading silent/inexact fallback when permission is absent. */
object NextAlarmScheduler {
    fun canExact(ctx:Context):Boolean = (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        .let { Build.VERSION.SDK_INT<31 || it.canScheduleExactAlarms() }
    fun nextTime(a:NextAlarm,after:ZonedDateTime=ZonedDateTime.now()):ZonedDateTime =
        AlarmTimePolicy.nextOccurrence(a.hour,a.minute,a.daysMask,after)
    private fun alarmIntent(ctx:Context,id:Int) = PendingIntent.getBroadcast(ctx,id,
        Intent(ctx,NextAlarmReceiver::class.java).putExtra("alarm_id",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(ctx:Context,id:Int) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(ctx,id))
    }
    fun schedule(ctx:Context,a:NextAlarm):Boolean {
        if(!a.enabled) {cancel(ctx,a.id);return true}
        if(!canExact(ctx))return false
        val at=nextTime(a).toInstant().toEpochMilli()
        val show=PendingIntent.getActivity(ctx,a.id,Intent(ctx,NextAlarmRingActivity::class.java)
            .putExtra("alarm_id",a.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return runCatching {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAlarmClock(AlarmManager.AlarmClockInfo(at,show),alarmIntent(ctx,a.id))
            true
        }.getOrDefault(false)
    }
    fun rescheduleAll(ctx:Context) {NextAlarmStore.all(ctx).filter{it.enabled}.forEach{schedule(ctx,it)}}
}
class NextAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.getIntExtra("alarm_id",-1)
        val alarm=NextAlarmStore.byId(context,id)?:return
        val isSnooze=intent.getBooleanExtra("snooze",false)
        if(!alarm.enabled && !isSnooze)return
        val mgr=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel="next_alarm_ring_v1"
        if(mgr.getNotificationChannel(channel)==null) {
            mgr.createNotificationChannel(NotificationChannel(channel,"NEXT 鬧鐘",NotificationManager.IMPORTANCE_HIGH).apply {
                description="使用者設定的鬧鐘";enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            })
        }
        val launch=PendingIntent.getActivity(context,id,Intent(context,NextAlarmRingActivity::class.java)
            .putExtra("alarm_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,channel)
            .setSmallIcon(R.drawable.ic_next_app).setContentTitle(alarm.label.ifBlank{"鬧鐘"})
            .setContentText("鬧鐘時間到了").setCategory(Notification.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(launch).setFullScreenIntent(launch,true).setAutoCancel(false).build()
        val permission=Build.VERSION.SDK_INT<33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
        if(permission)runCatching {mgr.notify(id,notification)}
        val all=NextAlarmStore.all(context)
        if(!isSnooze) {
            if(alarm.daysMask==0)NextAlarmStore.save(context,all.map{if(it.id==id)it.copy(enabled=false)else it})
            else NextAlarmScheduler.schedule(context,alarm)
        }
    }
}
class NextAlarmBootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action==Intent.ACTION_BOOT_COMPLETED || intent.action==AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ||
            intent.action==Intent.ACTION_TIME_CHANGED || intent.action==Intent.ACTION_TIMEZONE_CHANGED)
            NextAlarmScheduler.rescheduleAll(context)
    }
}
