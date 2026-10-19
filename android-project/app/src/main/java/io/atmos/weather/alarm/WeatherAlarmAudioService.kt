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
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.time.ZonedDateTime
import io.atmos.weather.next.NextWeatherAlarmPlayer
import io.atmos.weather.AppContainer
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.repository.WeatherLoadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import androidx.core.app.NotificationCompat
import io.atmos.weather.notifications.XiaomiFocusBridge
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
                // On an unlocked, authorized HyperOS 3 device prefer the official island;
                // locked/unavailable devices retain the untouched full-screen alarm UI.
                .setOngoing(true).setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止",controlIntent(ctx,alarm.id,ACTION_STOP))
            if(alarm.snooze) b.addAction(android.R.drawable.ic_popup_reminder,"10 分鐘後",controlIntent(ctx,alarm.id,ACTION_SNOOZE))
            val islandPreferred = XiaomiFocusBridge.shouldUseAlarmIsland(ctx)
            if (!islandPreferred) b.setFullScreenIntent(openIntent(ctx,alarm.id),true)
            var note = b.build()
            if (islandPreferred) {
                val text = String.format(Locale.TAIWAN,"%02d:%02d 鬧鐘響鈴中",alarm.hour,alarm.minute)
                // If Xiaomi rejects the island (including a higher-priority EEW),
                // the same ringing service and standard heads-up notification remain.
                XiaomiFocusBridge.decorate(ctx,note,"鬧鐘",text,
                    XiaomiFocusBridge.Kind.ALARM_RINGING,BASE_NOTIFICATION_ID+alarm.id)
            }
            return note
        }

        /** Safe fallback if Android blocks foreground-service launch. Does NOT claim sound is active. */
        fun postFallbackNotification(ctx:Context,alarm:WeatherAlarm) {
            val manager=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.notify(BASE_NOTIFICATION_ID+alarm.id,notification(ctx,alarm)) }
        }
    }

    private var currentId:Int=-1
    private var player:MediaPlayer?=null
    private var nextPlayer:NextWeatherAlarmPlayer?=null
    private var vibrator:Vibrator?=null
    private var tts:TextToSpeech?=null
    private var samsungTtsSelected=false
    private val uiHandler=Handler(Looper.getMainLooper())
    private val speechScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var speechJob:Job?=null

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
                    stopAudio()
                    currentId=-1
                    XiaomiFocusBridge.finish(BASE_NOTIFICATION_ID+id)
                    stopSelf()
                } else if(id>=0) {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .cancel(BASE_NOTIFICATION_ID+id)
                }
                return START_NOT_STICKY
            }
            ACTION_RING -> {
                if(currentId==id && (player?.isPlaying==true || nextPlayer?.isPlaying==true)) return START_NOT_STICKY
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
        val tone=R10AlarmTones.resource(alarm.ringtoneUri,
            WeatherAlarmSpeechCache.load(this),System.currentTimeMillis())
        val selected=if(tone!=null) android.net.Uri.parse("android.resource://$packageName/$tone")
            else runCatching { alarm.ringtoneUri?.let(android.net.Uri::parse) }.getOrNull()
        val default=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // Only the first two bundled themes get the weather-aware sound engine.
        // If assets/cache are unavailable the existing MediaPlayer tone still works.
        nextPlayer=NextWeatherAlarmPlayer.start(this,alarm.ringtoneUri,WeatherAlarmSpeechCache.load(this))
        if(nextPlayer==null) for(uri in listOfNotNull(selected,default).distinct()) {
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
            val timings=longArrayOf(0,65,135,90,360,130,410)
            val amplitudes=intArrayOf(0,90,0,160,0,120,0)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings,amplitudes,0))
        }
        if(alarm.weatherVoice) {
            // An unavailable Samsung engine never silently becomes Google TTS.
            if (!SamsungTtsSelector.canStart(this)) {
                SamsungTtsSelector.record(this,"選定的播報引擎尚未安裝；鬧鐘鈴聲正常播放")
            } else {
                val chosen=SamsungTtsSelector.intended(this)
                samsungTtsSelected=chosen!=null && chosen.startsWith("com.samsung.",true)
                tts=if(chosen!=null) TextToSpeech(this,this,chosen)
                    else TextToSpeech(this,this) // explicitly/system-default on non-Samsung
            }
        }
    }

    private fun setAlarmVolume(v:Float) {
        runCatching {player?.setVolume(v,v)}
        runCatching {nextPlayer?.setMasterVolume(v)}
    }

    private fun ttsUnavailable(message:String) {
        Log.w("thinn-alarm",message)
        SamsungTtsSelector.record(this,message)
        runCatching { tts?.shutdown() }
        tts=null // ringtone and vibration MUST continue.
    }

    override fun onInit(status:Int) {
        if(currentId < 0) return
        if(status != TextToSpeech.SUCCESS) {
            ttsUnavailable("指定的語音引擎無法啟動；沒有自動切換 Google")
            return
        }
        val engine=tts ?: return
        // Public Android TextToSpeech has no current-engine accessor. Only the
        // requested package and successfully selected offline voice can be reported.
        val intended=SamsungTtsSelector.intended(this)
        val requestedLabel=if(intended==null) "系統預設引擎" else "指定引擎：$intended"
        val voiceName=SamsungTtsSelector.prepare(engine)
        if(voiceName==null) {
            ttsUnavailable("$requestedLabel 沒有可用的離線繁體中文聲線；App 未主動切換 Google")
            return
        }
        SamsungTtsSelector.record(this,"$requestedLabel；聲線：$voiceName（系統未提供實際引擎識別）")
        engine.setSpeechRate(0.94f)
        engine.setPitch(1.0f)
        engine.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        val id=currentId
        engine.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(utteranceId:String?) { }
            override fun onDone(utteranceId:String?) {
                uiHandler.post { if(currentId==id) setAlarmVolume(1f) }
            }
            @Deprecated("Deprecated in platform; still invoked by some TTS engines")
            override fun onError(utteranceId:String?) {
                uiHandler.post { if(currentId==id) setAlarmVolume(1f) }
            }
            override fun onError(utteranceId:String?, errorCode:Int) { onError(utteranceId) }
        })
        speechJob?.cancel()
        speechJob=speechScope.launch {
            // Alarm audio starts immediately. Network updates, when permitted, are bounded
            // and NEVER block the alarm; only saved location, no background GPS requests.
            val updated=withTimeoutOrNull(6_000L) {
                withContext(Dispatchers.IO) { runCatching { refreshAlarmWeather() }.getOrNull() }
            }
            if(currentId!=id) return@launch
            val reading=updated ?: WeatherAlarmSpeechCache.load(this@WeatherAlarmAudioService)
            val message=WeatherAlarmNarration.build(ZonedDateTime.now(),reading)
            // Keep ringtone audible at system alarm volume during cloud generation.
            val cloudAccepted=io.atmos.weather.voice.NaturalVoiceHub.offer(
                this@WeatherAlarmAudioService,
                io.atmos.weather.voice.NaturalVoiceHub.Request(
                    message,io.atmos.weather.voice.NaturalVoiceHub.Priority.DAILY,
                    "alarm-weather:$id:${System.currentTimeMillis()/60_000L}",
                    alarmAudio=true,
                    onFinished={ if(currentId==id) setAlarmVolume(1f) },
                    onFailure={ if(currentId==id) {
                        setAlarmVolume(.18f)
                        val result=engine.speak(message,TextToSpeech.QUEUE_FLUSH,null,"alarm_weather_$id")
                        if(result != TextToSpeech.SUCCESS) runCatching{player?.setVolume(1f,1f)}
                    } }
                ))
            if(!cloudAccepted) {
                setAlarmVolume(.18f)
                val result=engine.speak(message,TextToSpeech.QUEUE_FLUSH,null,"alarm_weather_$id")
                if(result != TextToSpeech.SUCCESS) runCatching{player?.setVolume(1f,1f)}
            }
            // OEM TTS can omit onDone. Never leave the ringing sound quiet indefinitely.
            uiHandler.postDelayed({ if(currentId==id) setAlarmVolume(1f) },120_000L)
        }
    }

    private suspend fun refreshAlarmWeather(): AlarmWeatherReading? {
        val prefs=getSharedPreferences("atmos_settings",Context.MODE_PRIVATE)
        val gps=prefs.contains("gps_lat") && prefs.contains("gps_lon")
        val lat=Double.fromBits(prefs.getLong(if(gps)"gps_lat" else "lat",0L))
        val lon=Double.fromBits(prefs.getLong(if(gps)"gps_lon" else "lon",0L))
        if(!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat==0.0 && lon==0.0)) return null
        val city=WeatherCity("alarm",if(gps) prefs.getString("gps_name","目前位置") ?: "目前位置"
            else prefs.getString("city_name","目前城市") ?: "目前城市",GeoPoint(lat,lon))
        return when(val result=AppContainer.repository(this).load(city,true)) {
            is WeatherLoadResult.Success -> if(!result.stale) {
                WeatherAlarmSpeechCache.save(this,result.value)
                WeatherAlarmSpeechCache.load(this)
            } else null
            is WeatherLoadResult.Failure -> null
        }
    }

    private fun stopAudio() {
        io.atmos.weather.voice.NaturalVoiceHub.stopAlarm()
        speechJob?.cancel();speechJob=null
        runCatching {nextPlayer?.close()};nextPlayer=null
        runCatching {player?.stop();player?.release()};player=null
        runCatching {tts?.stop();tts?.shutdown()};tts=null
        runCatching {vibrator?.cancel()};vibrator=null
    }

    override fun onDestroy() {
        stopAudio()
        currentId=-1
        speechScope.cancel()
        super.onDestroy()
    }
}
