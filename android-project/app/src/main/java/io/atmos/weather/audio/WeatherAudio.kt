package io.atmos.weather.audio

import android.content.Context
import android.media.AudioManager
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
import io.atmos.weather.domain.model.WeatherCondition
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Foreground-only seamless audio with a 1.1s cross-fade on condition changes. */
private class AtmosAudioMixer(private val context:Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val lock=Mutex()
    private var current:MediaPlayer?=null
    private var transientThunder:MediaPlayer?=null
    private var sourceId:Int=0
    private var active=false
    private var enabled=false
    private var level=.15f

    fun foreground(isForeground:Boolean) {
        active=isForeground
        if (!isForeground || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            runCatching { current?.pause() }
            runCatching { transientThunder?.pause() }
        } else if (enabled) {
            runCatching { if (current?.isPlaying == false) current?.start() }
        }
    }
    suspend fun configure(newSource:Int, turnOn:Boolean, volume:Float) = lock.withLock {
        level=volume.coerceIn(0f,1f)
        enabled=turnOn
        if (!turnOn) {
            runCatching { current?.pause() }
            return@withLock
        }
        if (sourceId == newSource && current != null) {
            runCatching { current?.setVolume(level,level) }
            if (active && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) foreground(true)
            return@withLock
        }
        val incoming = MediaPlayer.create(context,newSource) ?: return@withLock
        incoming.isLooping=true
        val old=current
        // Keep the old ambiance alive during the new track's gradual fade in.
        if (active && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            incoming.setVolume(0f,0f)
            incoming.start()
        }
        try {
            repeat(18) { index ->
                val t=(index+1)/18f
                val nowVol=if (active && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) level else 0f
                runCatching { incoming.setVolume(nowVol*t,nowVol*t) }
                runCatching { old?.setVolume(nowVol*(1f-t),nowVol*(1f-t)) }
                delay(60)
            }
        } finally {
            runCatching { old?.release() }
            current=incoming
            sourceId=newSource
            runCatching { incoming.setVolume(level,level) }
            if (!active || !enabled || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                runCatching { incoming.pause() }
            }
        }
    }
    fun triggerThunder() {
        if (!active || !enabled || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching { transientThunder?.release() }
        transientThunder=MediaPlayer.create(context,R.raw.thunder_strike)?.apply {
            setVolume((level*.73f).coerceIn(0f,.7f),(level*.73f).coerceIn(0f,.7f))
            setOnCompletionListener { player ->
                player.release()
                if (transientThunder === player) transientThunder=null
            }
            start()
        }
    }
    fun close() {
        runCatching { current?.release() };current=null
        runCatching { transientThunder?.release() };transientThunder=null
    }
}

@Composable
fun WeatherAudio(condition:WeatherCondition?,enabled:Boolean,volume:Float,
                 weatherCode:Int?=null, thunderCue:Int=0) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val mixer=remember(context) { AtmosAudioMixer(context) }
    val resource=when {
        condition == WeatherCondition.THUNDERSTORM || weatherCode in setOf(95,96,99) -> R.raw.heavy_rain
        weatherCode in setOf(65,67,82) -> R.raw.heavy_rain
        condition in setOf(WeatherCondition.RAIN, WeatherCondition.HAIL) -> R.raw.light_rain
        condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.FREEZING_RAIN,
            WeatherCondition.DUST, WeatherCondition.DUST_STORM) -> R.raw.snow_wind
        else -> R.raw.sunny_breeze
    }
    DisposableEffect(mixer,owner) {
        val listener=LifecycleEventObserver { _, event ->
            when(event) {
                Lifecycle.Event.ON_START -> mixer.foreground(true)
                Lifecycle.Event.ON_STOP -> mixer.foreground(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(listener)
        mixer.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(listener);mixer.close() }
    }
    LaunchedEffect(mixer,resource,enabled,volume) {
        mixer.configure(resource,enabled && condition!=null,volume)
    }
    LaunchedEffect(mixer,thunderCue) {
        if (thunderCue>0 && condition==WeatherCondition.THUNDERSTORM && enabled) {
            mixer.triggerThunder()
        }
    }
}
