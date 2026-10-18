package io.atmos.weather.earthquake

import android.content.Context
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** App-managed optional Mandarin speech, never claims official-origin predictions. */
object SeismicSpeech {
    private val main = Handler(Looper.getMainLooper())
    fun enabled(ctx:Context,key:String):Boolean = ctx.getSharedPreferences("thinn_seismic_v15",Context.MODE_PRIVATE)
        .getBoolean(key,true)
    fun setEnabled(ctx:Context,key:String,value:Boolean) {
        ctx.getSharedPreferences("thinn_seismic_v15",Context.MODE_PRIVATE).edit().putBoolean(key,value).apply()
    }
    fun warning(incident:EewIncident,place:SeismicPlace?):String =
        SeismicWarningNarration.build(
            incident.epicenter,incident.magnitude,incident.localIntensityByCounty,place?.name,incident.depthKm)
    fun replay(report:EarthquakeReport, incident:EewIncident):String {
        val magnitude=incident.magnitude ?: report.magnitude
        return "歷史地震回放，這不是現在發生的地震，也不是即時預警。" +
            "過去${report.location}發生規模${String.format(Locale.TAIWAN,"%.1f",magnitude)}地震。" +
            "以下是已取得的過去速報資料，請練習趴下、掩護、穩住。"
    }
    fun report(report:EarthquakeReport):String {
        val grade=SeismicWavePhysics.intensityLabel(report.maxIntensity)?.let { "最大觀測震度${it}級。" }.orEmpty()
        return "第三方彙整的震後地震報告，${report.location}，規模${String.format(Locale.TAIWAN,"%.1f",report.magnitude)}。${grade}請以中央氣象署最新正式資訊為準。"
    }
    // Reuse the Android TTS engine across notification and foreground alert. Creating
    // and destroying one engine per message caused race conditions and missing speech.
    private var engine:TextToSpeech?=null
    private var starting=false
    private var queued:String?=null
    private var lastSpoken=""
    private var lastAt=0L

    fun speak(ctx:Context,message:String) {
        val app=ctx.applicationContext
        val audio=app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notifications=app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Follow user silent/DND settings. This is not the government's PWS system.
        if(audio.ringerMode!=AudioManager.RINGER_MODE_NORMAL ||
            notifications.currentInterruptionFilter!=NotificationManager.INTERRUPTION_FILTER_ALL) return
        main.post {
            val now=System.currentTimeMillis()
            if(message==lastSpoken && now-lastAt<6_000L) return@post
            queued=message
            engine?.let { speakQueued(it);return@post }
            if(starting) return@post
            starting=true
            var pendingEngine:TextToSpeech?=null
            pendingEngine=TextToSpeech(app) { status ->
                main.post {
                    starting=false
                    val prepared=pendingEngine
                    if(status==TextToSpeech.SUCCESS && prepared!=null) {
                        val available=prepared.isLanguageAvailable(Locale.TAIWAN)
                        prepared.language=if(available>=TextToSpeech.LANG_AVAILABLE) Locale.TAIWAN else Locale.CHINESE
                        prepared.setSpeechRate(.97f)
                        prepared.setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        engine=prepared
                        speakQueued(prepared)
                    } else { prepared?.shutdown();queued=null }
                }
            }
        }
    }
    private fun speakQueued(tts:TextToSpeech) {
        val message=queued ?: return
        queued=null
        lastSpoken=message
        lastAt=System.currentTimeMillis()
        tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"thinn_eq_${System.nanoTime()}")
    }
}
