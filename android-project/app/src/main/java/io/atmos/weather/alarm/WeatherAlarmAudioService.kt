package io.atmos.weather.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

/** Real ringing is owned by a service rather than a notification Activity.
 * A locked phone may suppress the fullscreen activity, but the scheduled alarm
 * can still play when Android permits foreground-service starts.
 */
class WeatherAlarmAudioService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_RING = "io.atmos.weather.ALARM_AUDIO_RING"
        const val ACTION_STOP = "io.atmos.weather.ALARM_AUDIO_STOP"
        const val ACTION_SNOOZE = "io.atmos.weather.ALARM_AUDIO_SNOOZE"
        private const val CHANNEL = "thinn_weather_alarm_v24"
        private const val BASE_NOTIFICATION_ID = 740000

        private fun openIntent(context: Context, id:Int): PendingIntent =
            PendingIntent.getActivity(context,id,
                Intent(context,WeatherAlarmRingActivity::class.java)
                    .putExtra("alarm_id",id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun controlIntent(context: Context,id:Int,action:String):PendingIntent =
            PendingIntent.getService(context,id + (if(action==ACTION_SNOOZE)100000 else 200000),
                Intent(context,WeatherAlarmAudioService::class.java).setAction(action).putExtra("alarm_id",id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun createChannel(ctx:Context) {
            val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if(nm.getNotificationChannel(CHANNEL)==null) nm.createNotificationChannel(
                NotificationChannel(CHANNEL,"天氣鬧鐘響鈴",NotificationManager.IMPORTANCE_HIGH).apply {
                    description="使用者設定的鬧鐘。鈴聲由 Android 鬧鐘服務播放。"
                    setSound(null,null) // service plays the user-selected ringtone: never ring twice
                    enableVibration(false)
                    lockscreenVisibility=Notification.VISIBILITY_PUBLIC
                })
        }

        private fun notification(ctx:Context,alarm:WeatherAlarm):Notification {
            createChannel(ctx)
            val b=NotificationCompat.Builder(ctx,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(alarm.title.ifBlank { "天氣鬧鐘" })
                .setContentText(String.format(Locale.TAIWAN,"%02d:%02d · 輕觸開啟響鈴畫面",alarm.hour,alarm.minute))
                .setContentIntent(openIntent(ctx,alarm.id))
                .setFullScreenIntent(openIntent(ctx,alarm.id),true)
                .setOngoing(true).setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止",controlIntent(ctx,alarm.id,ACTION_STOP))
            if(alarm.snooze) b.addAction(android.R.drawable.ic_popup_reminder,"10 分鐘後",controlIntent(ctx,alarm.id,ACTION_SNOOZE))
            return b.build()
        }

        /** Safe fallback if Android blocks foreground-service launch. Does NOT claim sound is active. */
        fun postFallbackNotification(ctx:Context,alarm:WeatherAlarm) {
            val manager=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.notify(BASE_NOTIFICATION_ID+alarm.id,notification(ctx,alarm)) }
        }
    }

    private var currentId:Int=-1
    private var player:MediaPlayer?=null
    private var vibrator:Vibrator?=null
    private var tts:TextToSpeech?=null

    override fun onBind(intent:Intent?):IBinder?=null

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val id=intent?.getIntExtra("alarm_id",-1) ?: -1
        when(intent?.action) {
            ACTION_STOP, ACTION_SNOOZE -> {
                // Never allow a stale notification from a different alarm to silence this one.
                if(id==currentId) {
                    if(intent?.action==ACTION_SNOOZE) WeatherAlarmScheduler.scheduleSnooze(this,id)
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .cancel(BASE_NOTIFICATION_ID+id)
                    stopSelf()
                } else if(id>=0) {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .cancel(BASE_NOTIFICATION_ID+id)
                }
                return START_NOT_STICKY
            }
            ACTION_RING -> {
                if(currentId==id && player?.isPlaying==true) return START_NOT_STICKY
                val alarm=WeatherAlarmStore.load(this).firstOrNull { it.id==id }
                    ?: run {stopSelf();return START_NOT_STICKY}
                stopAudio()
                currentId=id
                val note=notification(this,alarm)
                try {
                    if(Build.VERSION.SDK_INT>=29)
                        startForeground(BASE_NOTIFICATION_ID+id,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    else startForeground(BASE_NOTIFICATION_ID+id,note)
                } catch(ex:Exception) {
                    Log.w("thinn-alarm","Foreground alarm notification unavailable",ex)
                    postFallbackNotification(this,alarm)
                    stopSelf()
                    return START_NOT_STICKY
                }
                beginAudio(alarm)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun beginAudio(alarm:WeatherAlarm) {
        val selected=runCatching { alarm.ringtoneUri?.let(android.net.Uri::parse) }.getOrNull()
        val default=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        for(uri in listOfNotNull(selected,default).distinct()) {
            val next=runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setDataSource(this@WeatherAlarmAudioService,uri)
                    isLooping=true
                    prepare()
                    start()
                }
            }.getOrNull()
            if(next!=null) {player=next;break}
        }
        runCatching {
            vibrator=if(Build.VERSION.SDK_INT>=31)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                else (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,450,380),0))
        }
        if(alarm.weatherVoice) tts=TextToSpeech(this,this)
    }

    override fun onInit(status:Int) {
        if(status!=TextToSpeech.SUCCESS || currentId<0) return
        tts?.language=Locale.TAIWAN
        val prefs=getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE)
        val savedAt=prefs.getLong("latest_weather_at",0L)
        val fresh=System.currentTimeMillis()-savedAt in 0L..(3*60*60_000L)
        val summary=if(fresh) prefs.getString("latest_weather_summary",null)
            ?:"早安，請查看最新的天氣資訊。"
            else "早安。天氣資料尚未更新，請開啟天氣 App 查看最新預報及警特報。"
        tts?.speak(summary,TextToSpeech.QUEUE_FLUSH,null,"alarm_weather_$currentId")
    }

    private fun stopAudio() {
        runCatching {player?.stop();player?.release()};player=null
        runCatching {tts?.stop();tts?.shutdown()};tts=null
        runCatching {vibrator?.cancel()};vibrator=null
    }

    override fun onDestroy() {
        stopAudio()
        currentId=-1
        super.onDestroy()
    }
}
