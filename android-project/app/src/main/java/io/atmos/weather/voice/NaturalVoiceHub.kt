package io.atmos.weather.voice

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.atmos.weather.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Optional, consent-based Azure TTS. The alarm and EEW sound services NEVER depend on this. */
object NaturalVoiceHub {
    enum class Priority(val rank:Int) { EARTHQUAKE(0), WEATHER_ALERT(1), DAILY(2), PERSONAL(3) }
    data class Request(val text:String,val priority:Priority,val dedupeKey:String,
                       val privacySensitive:Boolean=false,val cacheable:Boolean=true,
                       val alarmAudio:Boolean=false,val onFinished:(() -> Unit)?=null,
                       val onFailure:(() -> Unit)?=null)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Any()
    private val pending=mutableListOf<Request>()
    private val seen=mutableMapOf<String,Long>()
    private var running:Job?=null
    private var currentPriority:Priority?=null
    private var currentRequest:Request?=null
    private var player:MediaPlayer?=null
    private var fallback:TextToSpeech?=null
    private var earthquakeHoldUntil=0L
    private const val MAX_CHARS=240
    private const val MAX_BYTES=900_000
    private const val CACHE_TTL=7L*24*60*60*1000
    private const val PREVIEW="早安！我是 thinn-khì。今天的天氣還不錯，不過下午可能會下雨，出門記得帶把傘喔！"

    private fun prefs(ctx:Context)=ctx.getSharedPreferences("thinn_natural_voice",Context.MODE_PRIVATE)
    fun enabled(ctx:Context)=prefs(ctx).getBoolean("enabled",false)
    fun voice(ctx:Context)=prefs(ctx).getString("voice","zh-TW-HsiaoChenNeural")
        ?.takeIf { it=="zh-TW-HsiaoChenNeural" || it=="zh-TW-HsiaoYuNeural" } ?: "zh-TW-HsiaoChenNeural"
    fun rate(ctx:Context)=prefs(ctx).getFloat("rate",.95f).coerceIn(.8f,1.15f)
    fun alertEnabled(ctx:Context)=prefs(ctx).getBoolean("alerts",true)
    fun volume(ctx:Context)=prefs(ctx).getFloat("volume",1f).coerceIn(.2f,1f)
    fun save(ctx:Context,enabled:Boolean,voice:String,rate:Float,volume:Float,daily:Boolean,personal:Boolean,alerts:Boolean=true) {
        require(voice=="zh-TW-HsiaoChenNeural" || voice=="zh-TW-HsiaoYuNeural")
        prefs(ctx).edit().putBoolean("enabled",enabled).putString("voice",voice)
            .putFloat("rate",rate.coerceIn(.8f,1.15f)).putFloat("volume",volume.coerceIn(.2f,1f))
            .putBoolean("daily",daily).putBoolean("personal",personal)
            .putBoolean("alerts",alerts).apply()
    }
    /** PREEMPT only: the existing EEW tone and offline Android TTS start separately, immediately. */
    fun interruptForEarthquake() {
        synchronized(lock) {
            earthquakeHoldUntil=System.currentTimeMillis()+20_000L
            pending.clear()
            running?.cancel(); running=null
            player?.runCatching { stop(); release() }; player=null
            fallback?.runCatching { stop(); shutdown() }; fallback=null
            currentPriority=Priority.EARTHQUAKE
            currentRequest=null
        }
    }
    /** Never sends sensitive personal reminders when the phone is locked. */
    fun offer(ctx:Context,req:Request):Boolean {
        if(!enabled(ctx) || req.text.isBlank() || req.text.length>MAX_CHARS) return false
        if(req.priority==Priority.EARTHQUAKE) return false // EEW uses immediate offline path.
        if(req.priority==Priority.WEATHER_ALERT && !alertEnabled(ctx)) return false
        if(req.priority==Priority.DAILY && !prefs(ctx).getBoolean("daily",false)) return false
        if(req.priority==Priority.PERSONAL && !prefs(ctx).getBoolean("personal",false)) return false
        if(req.privacySensitive && (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked) return false
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(nm.currentInterruptionFilter!=NotificationManager.INTERRUPTION_FILTER_ALL) return false
        val now=System.currentTimeMillis()
        synchronized(lock) {
            seen.entries.removeAll { now-it.value>120_000L }
            if(seen.containsKey(req.dedupeKey)) return false
            seen[req.dedupeKey]=now
            if(now<earthquakeHoldUntil) return false
            if(currentPriority!=null && NaturalVoicePriority.preempts(req.priority.rank,currentPriority!!.rank)) {
                running?.cancel();running=null
                player?.runCatching { stop();release() };player=null
                fallback?.runCatching {stop();shutdown()};fallback=null
            }
            pending.add(req)
            pending.sortBy { it.priority.rank }
            if(pending.size>12) pending.removeAt(pending.lastIndex)
            if(running==null) schedule(ctx.applicationContext)
        }
        return true
    }
    private fun schedule(ctx:Context) {
        running=scope.launch {
            val self=coroutineContext[Job]
            try {
                while(isActive) {
                    val req=synchronized(lock) {
                        if(pending.isEmpty()) null else pending.removeAt(0).also {currentPriority=it.priority;currentRequest=it}
                    } ?: break
                    if(System.currentTimeMillis()<earthquakeHoldUntil) break
                    if(req.privacySensitive && (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked) continue
                    val ok=runCatching { val file=resolveAudio(ctx,req.text,req.cacheable);playFile(ctx,file,req.alarmAudio) }.isSuccess
                    if(isActive) {
                        if(ok) {req.onFinished?.let { withContext(Dispatchers.Main) { it() } }}
                        else if(req.onFailure!=null) withContext(Dispatchers.Main) { req.onFailure.invoke() }
                        else runCatching {speakOffline(ctx,req.text)}
                    }
                }
            } finally {
                synchronized(lock) {
                    if(running===self) {
                        currentPriority=null
                        currentRequest=null
                        running=null
                        player?.runCatching {release()};player=null
                        fallback?.runCatching {shutdown()};fallback=null
                        if(pending.isNotEmpty() && System.currentTimeMillis()>=earthquakeHoldUntil) schedule(ctx)
                        else pending.clear()
                    }
                }
            }
        }
    }
    /** Preview NEVER substitutes the Android voice for a failed Azure request. */
    fun preview(ctx:Context,onResult:(Boolean,String)->Unit) {
        if(!enabled(ctx)) {onResult(false,"請先開啟自然語音並儲存");return}
        scope.launch {
            val result=runCatching { playFile(ctx.applicationContext,resolveAudio(ctx,PREVIEW,true)) }
            withContext(Dispatchers.Main) {
                onResult(result.isSuccess,if(result.isSuccess) "Azure 試聽播放完成" else "Azure 暫時無法試聽；沒有使用系統 TTS 冒充。請檢查後端、金鑰與網路。")
            }
        }
    }
    /** Prevent alarm speech from continuing after Stop or Snooze. */
    fun stopAlarm() {
        synchronized(lock) {
            pending.removeAll{it.alarmAudio}
            if(currentRequest?.alarmAudio==true && running!=null) {
                running?.cancel()
                player?.runCatching{stop();release()};player=null
            }
        }
    }
    fun clearCache(ctx:Context):Int {
        val root=File(ctx.cacheDir,"natural_voice_v1")
        val files=root.listFiles().orEmpty()
        files.forEach{it.delete()}
        return files.size
    }
    private fun digest(s:String)=MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
    private suspend fun authToken():String=suspendCancellableCoroutine { cont ->
        val auth=FirebaseAuth.getInstance()
        fun get()=auth.currentUser!!.getIdToken(false)
            .addOnSuccessListener { if(cont.isActive) cont.resume(it.token.orEmpty()) }
            .addOnFailureListener { if(cont.isActive) cont.resumeWithException(it) }
        if(auth.currentUser!=null) get() else auth.signInAnonymously()
            .addOnSuccessListener { get() }
            .addOnFailureListener { if(cont.isActive) cont.resumeWithException(it) }
    }
    private suspend fun resolveAudio(ctx:Context,text:String,cacheable:Boolean):File=withContext(Dispatchers.IO) {
        val endpoint=BuildConfig.THINN_AZURE_PROXY_URL.trim().trimEnd('/')
        require(endpoint.startsWith("https://") && !endpoint.contains('@') && endpoint.length<250) {"Azure 後端未設定 HTTPS"}
        val cache=File(ctx.cacheDir,"natural_voice_v1").apply {mkdirs()}
        val key=digest("v1|${voice(ctx)}|${rate(ctx)}|$text")
        val target=File(cache,"$key.mp3")
        if(cacheable && target.isFile && target.length() in 1000..MAX_BYTES.toLong() &&
            System.currentTimeMillis()-target.lastModified()<CACHE_TTL) return@withContext target
        val temp=File.createTempFile("voice-",".mp3",cache)
        try {
            val bearer=authToken()
            check(bearer.isNotBlank()) { "登入驗證失敗" }
            val request=JSONObject().put("text",text).put("voice",voice(ctx)).put("rate",rate(ctx).toDouble())
                .put("pitchSemitones",0).put("pauseMs",120)
            val conn=URL("$endpoint/v1/speech").openConnection() as HttpURLConnection
            try {
                conn.requestMethod="POST";conn.connectTimeout=3500;conn.readTimeout=6500
                conn.setRequestProperty("Authorization","Bearer $bearer")
                conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
                conn.doOutput=true
                conn.outputStream.use {it.write(request.toString().toByteArray(Charsets.UTF_8))}
                check(conn.responseCode==200 && conn.contentType.orEmpty().startsWith("audio/mpeg")) {"Azure 合成服務無法使用"}
                conn.inputStream.use { input ->
                    temp.outputStream().use { out ->
                        val buffer=ByteArray(8192);var total=0
                        while(true) {
                            val n=input.read(buffer);if(n<0)break
                            total+=n;check(total<=MAX_BYTES) {"語音檔過大"};out.write(buffer,0,n)
                        }
                    }
                }
                check(temp.length()>1000L) {"無效語音音訊"}
            } finally { conn.disconnect() }
            if(cacheable) {check(temp.renameTo(target));target} else temp
        } catch(e:Exception) {temp.delete();throw e}
    }
    private suspend fun playFile(ctx:Context,file:File,alarm:Boolean=false)=suspendCancellableCoroutine<Unit> { cont ->
        val focus=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs=AudioAttributes.Builder().setUsage(if(alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val focusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).build()
        val gotFocus=focus.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if(!gotFocus) {if(cont.isActive)cont.resumeWithException(IllegalStateException("無音訊焦點"));return@suspendCancellableCoroutine}
        val mp=MediaPlayer()
        synchronized(lock) {player=mp}
        fun cleanup() {
            runCatching {mp.release()}
            focus.abandonAudioFocusRequest(focusRequest)
            synchronized(lock) {if(player===mp)player=null}
            if(file.name.startsWith("voice-"))file.delete()
        }
        try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(file.absolutePath)
            val gain=if(alarm) 1f else volume(ctx) // alarm always follows system STREAM_ALARM
            mp.setVolume(gain,gain)
            mp.setOnCompletionListener {cleanup();if(cont.isActive)cont.resume(Unit)}
            mp.setOnErrorListener { _, _, _ ->cleanup();if(cont.isActive)cont.resumeWithException(Exception("播放器錯誤"));true }
            mp.prepare();mp.start()
            cont.invokeOnCancellation {runCatching{mp.stop()};cleanup()}
        } catch(e:Exception) {cleanup();if(cont.isActive)cont.resumeWithException(e)}
    }
    private suspend fun speakOffline(ctx:Context,text:String)=withContext(Dispatchers.Main) {
        withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine<Unit> {cont ->
                var tts:TextToSpeech?=null
                tts=TextToSpeech(ctx) { status ->
                    val e=tts
                    if(status!=TextToSpeech.SUCCESS || e==null) {
                        e?.shutdown();if(cont.isActive)cont.resume(Unit)
                    } else {
                        val compatible=e.voices.orEmpty().filter {
                            it.locale.language=="zh" && it.locale.country.equals("TW",true) && !it.isNetworkConnectionRequired
                        }.maxByOrNull {it.quality}
                        if(compatible==null || e.setVoice(compatible)!=TextToSpeech.SUCCESS) {
                            e.shutdown();if(cont.isActive)cont.resume(Unit)
                        } else {
                            synchronized(lock) {fallback=e}
                            e.setSpeechRate(rate(ctx));e.setAudioAttributes(AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            e.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                                override fun onStart(utteranceId:String?) {}
                                override fun onDone(utteranceId:String?) {e.shutdown();if(cont.isActive)cont.resume(Unit)}
                                @Deprecated("TTS") override fun onError(utteranceId:String?) {e.shutdown();if(cont.isActive)cont.resume(Unit)}
                            })
                            e.speak(text,TextToSpeech.QUEUE_FLUSH,null,"natural_fallback")
                        }
                    }
                }
                cont.invokeOnCancellation {runCatching{tts?.stop();tts?.shutdown()}}
            }
        }
    }
}
