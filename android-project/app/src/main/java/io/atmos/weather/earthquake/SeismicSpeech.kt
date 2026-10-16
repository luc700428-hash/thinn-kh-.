package io.atmos.weather.earthquake

import android.content.Context
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** App-managed optional Mandarin speech, never claims official-origin predictions. */
object SeismicSpeech {
    private val main = Handler(Looper.getMainLooper())
    fun enabled(ctx:Context,key:String):Boolean = ctx.getSharedPreferences("thinn_seismic_v15",Context.MODE_PRIVATE)
        .getBoolean(key,true)
    fun setEnabled(ctx:Context,key:String,value:Boolean) {
        ctx.getSharedPreferences("thinn_seismic_v15",Context.MODE_PRIVATE).edit().putBoolean(key,value).apply()
    }
    fun warning(incident:EewIncident,place:SeismicPlace?):String {
        val local=place?.let { target ->
            incident.localIntensityByCounty.entries.firstOrNull { (name,_) ->
                target.name.contains(name.replace("台","臺")) || name.replace("台","臺").contains(target.name.removeSuffix("附近"))
            }?.value?.let { grade -> SeismicWavePhysics.intensityLabel(grade)?.let { safe -> "${target.name}預估震度${safe}級。" } }
        }.orEmpty()
        val magnitude=incident.magnitude?.let { "規模${String.format(Locale.TAIWAN,"%.1f",it)}。" }.orEmpty()
        return "地震速報，第三方資料。${local}震央${incident.epicenter}。${magnitude}請立即趴下、掩護、穩住。"
    }
    fun report(report:EarthquakeReport):String {
        val grade=SeismicWavePhysics.intensityLabel(report.maxIntensity)?.let { "最大觀測震度${it}級。" }.orEmpty()
        return "第三方彙整的震後地震報告，${report.location}，規模${String.format(Locale.TAIWAN,"%.1f",report.magnitude)}。${grade}請以中央氣象署最新正式資訊為準。"
    }
    fun speak(ctx:Context,message:String) {
        val app=ctx.applicationContext
        val audio=app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notice=app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Optional voice follows user silent/DND decisions; not a system emergency broadcast.
        if(audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if(notice.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        main.post {
            var tts:TextToSpeech?=null
            val stamp="thinn_seismic_${System.nanoTime()}"
            tts=TextToSpeech(app) { status ->
                if(status==TextToSpeech.SUCCESS) {
                    val engine=tts ?: return@TextToSpeech
                    engine.language=Locale.TAIWAN
                    engine.setSpeechRate(1.04f)
                    engine.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    engine.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                        override fun onStart(utteranceId:String?){}
                        override fun onDone(utteranceId:String?) { main.post { engine.shutdown() } }
                        @Deprecated("Deprecated in Java") override fun onError(utteranceId:String?) { main.post { engine.shutdown() } }
                    })
                    engine.speak(message,TextToSpeech.QUEUE_FLUSH,null,stamp)
                } else tts?.shutdown()
            }
            main.postDelayed({ tts?.stop();tts?.shutdown() },15_000L)
        }
    }
}
