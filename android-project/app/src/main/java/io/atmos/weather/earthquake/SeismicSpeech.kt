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
    fun warning(incident:EewIncident,place:SeismicPlace?):String {
        val local=SeismicLocalPrediction.resolve(incident,place)
        return SeismicWarningNarration.build(
            incident.epicenter,incident.magnitude,incident.localIntensityByCounty,place?.name,incident.depthKm,
            modeledLocal=local.intensity.takeIf{local.intensityOrigin==PredictionOrigin.LOCAL_MODEL})
    }
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
    private data class Utterance(val text:String,val usage:Int,val permitted:()->Boolean)
    private var queued:Utterance?=null
    private var lastSpoken=""
    private var lastAt=0L

    fun speakWarning(ctx:Context,incident:EewIncident,place:SeismicPlace?) {
        if(!ExpTechRealtimeParser.isFresh(incident)||!SeismicAlertNotifier.mayAnnounce(ctx,incident))return
        val scenario=SeismicAlertDelivery.scenario(ctx,incident)
        val critical=SeismicCriticalMode.applies(ctx,scenario)
        val route=if(critical)SeismicCriticalMode.route(ctx,scenario,forVoice=true) else null
        if(critical&&route==null)return
        val permitted={
            ExpTechRealtimeParser.isFresh(incident)&&SeismicAlertNotifier.mayAnnounce(ctx,incident)&&enabled(ctx,"eew_voice_enabled")&&
                (!critical||SeismicCriticalMode.route(ctx,scenario,forVoice=true)!=null)
        }
        // When the shaking is close, there is no time for a long sentence: say the essentials,
        // then count down the last seconds out loud (as KNY / DPIP do).
        val arrival=SeismicLocalPrediction.resolve(incident,place).arrivalAtMs
        val left=arrival?.let{it-System.currentTimeMillis()}
        val local=SeismicLocalPrediction.resolve(incident,place).intensity
        val text=if(left!=null&&left in 1..20_000)"地震！"+(local?.let{"${place?.name?:"這裡"}預估${SeismicDisplayFormat.symbol(it).replace("−","弱").replace("+","強")}級，"}?:"")+"趴下、掩護、穩住！"
            else warning(incident,place)
        speakInternal(ctx,text,route,permitted)
        if(arrival!=null)countdown(ctx,arrival,route,permitted)
    }
    private var countdownTask:Runnable?=null
    /** Speaks 10…1 before the estimated arrival, then "搖晃抵達". Stopped by [stop]. */
    private fun countdown(ctx:Context,arrival:Long,route:SeismicAudioRoute?,permitted:()->Boolean) {
        countdownTask?.let{main.removeCallbacks(it)}
        val app=ctx.applicationContext
        val task=object:Runnable{
            var last=Int.MAX_VALUE
            override fun run() {
                val left=kotlin.math.ceil((arrival-System.currentTimeMillis())/1000.0).toInt()
                if(left<=-1||!permitted()){countdownTask=null;return}
                if(left in 0..10&&left<last){
                    last=left
                    speakNow(app,if(left==0)"搖晃抵達，穩住！" else "$left",route)
                }
                main.postDelayed(this,200)
            }
        }
        countdownTask=task
        val startIn=(arrival-10_500-System.currentTimeMillis()).coerceAtLeast(0)
        main.postDelayed(task,startIn)
    }
    /** Interrupts whatever is being said: used for the countdown digits. */
    private fun speakNow(app:Context,text:String,route:SeismicAudioRoute?) {
        val tts=engine?:return
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(route?.usage?:AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"thinn_eq_count_${System.nanoTime()}")
    }
    fun speak(ctx:Context,message:String)=speakInternal(ctx,message,null){true}
    private fun speakInternal(ctx:Context,message:String,route:SeismicAudioRoute?,permitted:()->Boolean) {
        val app=ctx.applicationContext
        val audio=app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notifications=app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Follow user silent/DND settings. This is not the government's PWS system.
        if(route==null&&(audio.ringerMode!=AudioManager.RINGER_MODE_NORMAL ||
            notifications.currentInterruptionFilter!=NotificationManager.INTERRUPTION_FILTER_ALL)) return
        main.post {
            val now=System.currentTimeMillis()
            if(message==lastSpoken && now-lastAt<6_000L) return@post
            queued=Utterance(message,route?.usage?:AudioAttributes.USAGE_NOTIFICATION_EVENT,permitted)
            engine?.let { speakQueued(it);return@post }
            if(starting) return@post
            starting=true
            var pendingEngine:TextToSpeech?=null
            pendingEngine=TextToSpeech(app) { status ->
                main.post {
                    starting=false
                    val prepared=pendingEngine
                    if(status==TextToSpeech.SUCCESS && prepared!=null) {
                        io.atmos.weather.voice.V25TaiwanVoice.load(app)
                        io.atmos.weather.voice.V25TaiwanVoice.configure(prepared)
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
    fun stop() {main.post{queued=null;countdownTask?.let{main.removeCallbacks(it)};countdownTask=null;engine?.stop()}}
    private fun speakQueued(tts:TextToSpeech) {
        val request=queued ?: return
        queued=null
        if(!request.permitted())return
        val message=request.text
        lastSpoken=message
        lastAt=System.currentTimeMillis()
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(request.usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts.speak(io.atmos.weather.voice.V25TaiwanVoice.humanize(message),TextToSpeech.QUEUE_FLUSH,null,"thinn_eq_${System.nanoTime()}")
    }
}
