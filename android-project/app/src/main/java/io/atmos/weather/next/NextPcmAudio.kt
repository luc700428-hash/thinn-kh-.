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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.cos
import kotlin.math.sin

/** WAV PCM16 mono reader with explicit bounds checks; 22,050 Hz assets from this update. */
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
                check(out.state == AudioTrack.STATE_INITIALIZED)
                check(out.write(decoded.pcm,0,decoded.pcm.size,AudioTrack.WRITE_BLOCKING) == decoded.pcm.size)
                check(out.setLoopPoints(0,decoded.frames,-1) == AudioTrack.SUCCESS)
                NextLoopTrack(out)
            } catch (_: Exception) {
                runCatching { out?.release() }
                null
            }
        }
    }
    private var released = false
    val isPlaying get() = !released && track.playState == AudioTrack.PLAYSTATE_PLAYING
    fun start(volume: Float) { if(!released) {track.setVolume(volume.coerceIn(0f,1f)); track.play()} }
    fun volume(value: Float) { if(!released) track.setVolume(value.coerceIn(0f,1f)) }
    fun pause() { if(!released && isPlaying) track.pause() }
    fun resume() { if(!released && !isPlaying) track.play() }
    fun close() {
        if(released) return
        released=true
        runCatching {track.pause()}
        runCatching {track.flush()}
        runCatching {track.release()}
    }
}

/** Lifecycle bound weather ambience with equal-power crossfades; never runs in the background. */
internal class NextAmbienceController(private val context:Context) {
    private val lock = Mutex()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var track: NextLoopTrack? = null
    // A new track exists before the equal-power crossfade finishes; stop BOTH on background.
    private var transitioning: NextLoopTrack? = null
    private var scene: WeatherScene? = null
    private var enabled = false
    private var active = false
    private var disposed = false
    private var level = .15f
    private var thunder:MediaPlayer?=null
    private fun audible():Boolean = !disposed && NextAudioLifecyclePolicy.canPlay(
        active,enabled,audio.ringerMode == AudioManager.RINGER_MODE_NORMAL)

    fun foreground(on:Boolean) {
        if(disposed) return
        active=on
        if(!audible()) {
            track?.pause()
            transitioning?.pause()
        } else {
            // A track created while backgrounded was muted to zero; restore audible gain.
            if(transitioning==null) track?.volume(level)
            track?.resume()
            transitioning?.resume()
        }
    }
    suspend fun configure(next:WeatherScene?, on:Boolean, volume:Float) = lock.withLock {
        if(disposed) return@withLock
        enabled=on
        level=volume.coerceIn(0f,.55f)
        if(!on || next == null) {track?.pause();transitioning?.pause();return@withLock}
        if(scene == next && track != null) {
            track?.volume(if(audible()) level else 0f)
            foreground(active)
            return@withLock
        }
        val incoming=NextLoopTrack.open(context,WeatherScenes.ambienceAsset(next),AudioAttributes.USAGE_MEDIA)
            ?: return@withLock
        val old=track
        transitioning=incoming
        if(audible()) incoming.start(0f)
        try {
            // Re-check lifecycle and ringer mode on EVERY frame, not just on transition start.
            repeat(28) { i ->
                val p=(i+1)/28f
                val to=if(audible()) level else 0f
                incoming.volume((to*sin(p*Math.PI/2).toFloat()).coerceIn(0f,1f))
                old?.volume((to*cos(p*Math.PI/2).toFloat()).coerceIn(0f,1f))
                if(to<=0f) {incoming.pause();old?.pause()}
                else {incoming.resume();old?.resume()}
                delay(40)
            }
        } finally {
            old?.close()
            transitioning=null
            if(disposed) {
                incoming.close()
                track=null
            } else {
                track=incoming
                scene=next
                if(audible()) {incoming.volume(level);incoming.resume()}
                else {incoming.volume(0f);incoming.pause()}
            }
        }
    }
    fun thunderCue() {
        if(!active || !enabled || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching { thunder?.release() }
        thunder = MediaPlayer.create(context,R.raw.thunder_strike)?.apply {
            setVolume((level*.45f).coerceIn(0f,.24f),(level*.45f).coerceIn(0f,.24f))
            setOnCompletionListener { it.release();if(thunder===it) thunder=null }
            start()
        }
    }
    fun close() {
        disposed=true
        transitioning?.close();transitioning=null
        track?.close();track=null
        runCatching {thunder?.release()};thunder=null
    }
}

@Composable
fun NextWeatherAmbience(condition:WeatherCondition?,enabled:Boolean,volume:Float,
                        weatherCode:Int?=null, visibilityM:Double?=null, thunderCue:Int=0) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val controller=remember(context){NextAmbienceController(context)}
    val scene=WeatherScenes.classify(condition,weatherCode,visibilityM)
    // Unclassified conditions (overcast/unknown) continue using the original ambient audio,
    // not misleading sunny audio and never silence an already supported scene.
    if(scene == null) {
        WeatherAudio(condition,enabled,volume,weatherCode,thunderCue)
    }
    DisposableEffect(controller,owner) {
        val listener=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_START) controller.foreground(true)
            if(event==Lifecycle.Event.ON_STOP) controller.foreground(false)
        }
        owner.lifecycle.addObserver(listener)
        controller.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {owner.lifecycle.removeObserver(listener);controller.close()}
    }
    LaunchedEffect(controller,scene,enabled,volume) {controller.configure(scene,enabled && scene!=null,volume)}
    LaunchedEffect(controller,thunderCue) {
        if(thunderCue > 0 && enabled && scene in listOf(WeatherScene.THUNDERSTORM,WeatherScene.SEVERE_THUNDERSTORM))
            controller.thunderCue()
    }
}
