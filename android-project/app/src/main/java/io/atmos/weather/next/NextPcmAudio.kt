package io.atmos.weather.next

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.atmos.weather.R
import io.atmos.weather.audio.WeatherAudio
import io.atmos.weather.domain.model.WeatherCondition
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.cos
import kotlin.math.sin

/** WAV PCM16 mono reader with explicit bounds checks; supports the 44,100 Hz wake suite. */
internal object LoopWav {
    data class Decoded(val sampleRate: Int, val pcm: ByteArray) {
        val frames: Int get() = pcm.size / 2
    }
    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int) = (b[p].toInt() and 255).toLong() or
        ((b[p+1].toInt() and 255).toLong() shl 8) or
        ((b[p+2].toInt() and 255).toLong() shl 16) or ((b[p+3].toInt() and 255).toLong() shl 24)

    fun read(context: Context, path: String): Decoded? = runCatching {
        val bytes = context.assets.open(path).use { it.readBytes() }
        if(bytes.size !in 46..6_000_000 || String(bytes,0,4) != "RIFF" || String(bytes,8,4) != "WAVE") return null
        var pos = 12
        var rate = 0
        var data: ByteArray? = null
        while(pos + 8 <= bytes.size) {
            val chunkName = String(bytes,pos,4)
            val len = u32(bytes,pos+4)
            if(len < 0 || len > bytes.size - pos - 8) return null
            val payload = pos + 8
            when(chunkName) {
                "fmt " -> {
                    if(len < 16 || u16(bytes,payload) != 1 || u16(bytes,payload+2) != 1 ||
                        u16(bytes,payload+14) != 16) return null
                    rate = u32(bytes,payload+4).toInt()
                }
                "data" -> data = bytes.copyOfRange(payload,payload+len.toInt())
            }
            pos = payload + len.toInt() + (len.toInt() and 1)
        }
        val pcm = data ?: return null
        if(rate !in 16000..48000 || pcm.size % 2 != 0 || pcm.isEmpty()) return null
        Decoded(rate,pcm)
    }.getOrNull()
}

/** Static PCM loop points are sample-accurate and avoid MediaPlayer decoder restarts. */
internal class NextLoopTrack private constructor(private val track: AudioTrack) {
    companion object {
        fun open(context: Context, path: String, usage: Int): NextLoopTrack? {
            val decoded = LoopWav.read(context,path) ?: return null
            val attr = AudioAttributes.Builder().setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val format = AudioFormat.Builder().setSampleRate(decoded.sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            var out: AudioTrack? = null
            return try {
                out = AudioTrack(attr,format,decoded.pcm.size,AudioTrack.MODE_STATIC,AudioManager.AUDIO_SESSION_ID_GENERATE)
                // MODE_STATIC is successfully allocated but has no data until its first write.
                check(out.state != AudioTrack.STATE_UNINITIALIZED)
                check(out.write(decoded.pcm,0,decoded.pcm.size,AudioTrack.WRITE_BLOCKING) == decoded.pcm.size)
                check(out.state == AudioTrack.STATE_INITIALIZED)
                check(out.setLoopPoints(0,decoded.frames,-1) == AudioTrack.SUCCESS)
                NextLoopTrack(out)
            } catch (_: Exception) {
                runCatching { out?.release() }
                null
            }
        }
    }
    val isPlaying get() = track.playState == AudioTrack.PLAYSTATE_PLAYING
    fun start(volume: Float) { track.setVolume(volume.coerceIn(0f,1f)); track.play() }
    fun volume(value: Float) { track.setVolume(value.coerceIn(0f,1f)) }
    fun pause() { if(isPlaying) track.pause() }
    fun resume() { if(!isPlaying) track.play() }
    fun close() { runCatching {track.pause()};runCatching {track.flush()}; track.release() }
}

data class AmbientPlaybackState(val code:String,val message:String)

/** Foreground-only ambience, observable errors and Android media-focus handling. */
internal class NextAmbienceController(private val context:Context) {
    private val lock=Mutex()
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val state=androidx.compose.runtime.mutableStateOf(AmbientPlaybackState("off","天氣環境音已關閉"))
    private var track:NextLoopTrack?=null;private var incomingTrack:NextLoopTrack?=null
    private var scene:String?=null;private var requested:String?=null
    private var enabled=false;private var active=false;private var level=.5f
    private var closed=false;private var focused=false;private var duck=1f;private var failed=false
    private var thunder:MediaPlayer?=null
    private val request=android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener({change->
            when(change) {
                AudioManager.AUDIOFOCUS_GAIN->{focused=true;duck=1f;if(active&&enabled&&!failed){track?.volume(level);track?.resume();incomingTrack?.resume()}}
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK->{duck=.2f;track?.volume(level*duck);incomingTrack?.volume(level*duck)}
                AudioManager.AUDIOFOCUS_LOSS,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT->{focused=false;track?.pause();incomingTrack?.pause()}
            };refreshStatus()
        },android.os.Handler(android.os.Looper.getMainLooper())).build()
    private fun acquire():Boolean {
        if(focused)return true
        focused=runCatching{audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED}.getOrDefault(false)
        return focused
    }
    private fun abandon() {runCatching{audio.abandonAudioFocusRequest(request)};focused=false;duck=1f}
    fun refreshStatus() {
        if(closed)return
        state.value=when {
            !enabled->AmbientPlaybackState("off","天氣環境音已關閉")
            requested==null->AmbientPlaybackState("waiting","等待天氣資料；可按試聽確認聲音")
            !active->AmbientPlaybackState("paused","離開 App 前景，已暫停環境音")
            level<=0f->AmbientPlaybackState("muted","環境音量為 0%，向右拖曳即可啟用")
            runCatching{audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0}.getOrDefault(false)->AmbientPlaybackState("system_muted","手機媒體音量為 0，請用音量鍵調高")
            failed->AmbientPlaybackState("unavailable","目前音效無法播放，請再試聽一次")
            !focused->AmbientPlaybackState("focus","其他音訊暫時佔用播放；返回 App 或按試聽重試")
            track?.isPlaying==true||incomingTrack?.isPlaying==true->AmbientPlaybackState("playing","正在播放目前天氣環境音")
            else->AmbientPlaybackState("loading","正在準備天氣環境音…")
        }
    }
    fun foreground(on:Boolean) {
        if(closed)return
        active=on
        if(!on) {
            track?.pause();incomingTrack?.pause();runCatching{thunder?.release()};thunder=null;abandon()
        } else if(enabled&&requested!=null&&!failed&&level>0&&acquire()) {
            track?.volume(level*duck);track?.resume();incomingTrack?.resume()
        }
        refreshStatus()
    }
    suspend fun configure(next:String?,on:Boolean,volume:Float,forceRestart:Boolean=false)=lock.withLock {
        if(closed)return@withLock
        enabled=on;requested=next
        level=volume.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.5f
        if(!on||next==null||level<=0) {
            track?.pause();incomingTrack?.pause();abandon();refreshStatus();return@withLock
        }
        if(!forceRestart&&scene==next&&track!=null) {failed=false;track?.volume(level*duck);foreground(active);return@withLock}
        val incoming=NextLoopTrack.open(context,next,AudioAttributes.USAGE_MEDIA)
        if(incoming==null){failed=true;track?.pause();refreshStatus();return@withLock}
        failed=false;incomingTrack=incoming;val old=track
        if(active&&acquire())incoming.start(0f)
        refreshStatus()
        try {
            repeat(28){i->
                if(closed)return@withLock
                val fraction=(i+1)/28f;val to=if(active&&enabled&&focused)level*duck else 0f
                incoming.volume(to*sin(fraction*Math.PI/2).toFloat())
                old?.volume(to*cos(fraction*Math.PI/2).toFloat());delay(40)
            }
        } finally {
            if(closed){track=null;incomingTrack=null}
            else {
                old?.close();track=incoming;incomingTrack=null;scene=next
                incoming.volume(if(active&&enabled&&focused)level*duck else 0f)
                if(!active||!enabled||!focused)incoming.pause()
                refreshStatus()
            }
        }
    }
    fun thunderCue() {
        if(closed||!active||!enabled||!focused)return
        runCatching{thunder?.release()}
        thunder=MediaPlayer.create(context,R.raw.thunder_strike)?.apply {
            setVolume((level*.45f).coerceIn(0f,.35f),(level*.45f).coerceIn(0f,.35f))
            setOnCompletionListener{it.release();if(thunder===it)thunder=null};start()
        }
    }
    fun close() {
        if(closed)return
        closed=true;active=false;track?.close();track=null;incomingTrack?.close();incomingTrack=null
        runCatching{thunder?.release()};thunder=null;abandon()
    }
}

@Composable
fun NextWeatherAmbience(condition:WeatherCondition?,enabled:Boolean,volume:Float,
    weatherCode:Int?=null,visibilityM:Double?=null,thunderCue:Int=0,isNight:Boolean=false,retryToken:Int=0):androidx.compose.runtime.State<AmbientPlaybackState> {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val controller=remember(context,owner){NextAmbienceController(context)}
    val scene=WeatherScenes.classify(condition,weatherCode,visibilityM)
    val path=AmbientSoundPolicy.path(condition,scene,isNight)
    DisposableEffect(controller,owner) {
        val listener=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_START||event==Lifecycle.Event.ON_RESUME)controller.foreground(true)
            if(event==Lifecycle.Event.ON_STOP)controller.foreground(false)
        }
        owner.lifecycle.addObserver(listener)
        controller.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose{owner.lifecycle.removeObserver(listener);controller.close()}
    }
    val handledRetry=remember(controller){intArrayOf(retryToken)}
    LaunchedEffect(controller,path,enabled,volume,retryToken) {
        val restart=handledRetry[0]!=retryToken
        handledRetry[0]=retryToken
        controller.configure(path,enabled,volume,forceRestart=restart)
    }
    LaunchedEffect(controller,owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(kotlinx.coroutines.currentCoroutineContext().isActive){controller.refreshStatus();delay(1500)}
        }
    }
    LaunchedEffect(controller,thunderCue) {
        if(thunderCue>0&&enabled&&scene in listOf(WeatherScene.THUNDERSTORM,WeatherScene.SEVERE_THUNDERSTORM))controller.thunderCue()
    }
    return controller.state
}
