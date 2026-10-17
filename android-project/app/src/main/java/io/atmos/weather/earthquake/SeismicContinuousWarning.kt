package io.atmos.weather.earthquake

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/** A foreground-only, event-bounded attention tone. Respects alert settings,
 * silent/DND and stops on dialog dismissal. Notification sounds are separate.
 */
object SeismicContinuousWarning {
    private val main=Handler(Looper.getMainLooper())
    private var player:MediaPlayer?=null
    private var eventKey:String?=null
    private val timeout=Runnable { stop() }
    @Synchronized fun start(ctx:Context,incident:EewIncident) {
        val key="${incident.id}:${incident.serial}"
        if(eventKey==key && player?.isPlaying==true) return
        stop()
        val scenario=if(incident.urgent) SeismicAlertPolicy.Scenario.EEW_URGENT
            else if(incident.serial>1) SeismicAlertPolicy.Scenario.EEW_UPDATE
            else SeismicAlertPolicy.Scenario.EEW_FIRST
        if(!SeismicAlertPolicy.soundEnabled(ctx,scenario)) return
        val audio=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notifications=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(audio.ringerMode!=AudioManager.RINGER_MODE_NORMAL ||
            notifications.currentInterruptionFilter!=NotificationManager.INTERRUPTION_FILTER_ALL) return
        runCatching {
            player=MediaPlayer.create(ctx.applicationContext,scenario.sound)?.apply {
                isLooping=true
                setVolume(.45f,.45f)
                start()
            }
            if(player!=null) { eventKey=key;main.postDelayed(timeout,35_000L) }
        }.onFailure { stop() }
    }
    @Synchronized fun stop() {
        main.removeCallbacks(timeout)
        player?.runCatching { if(isPlaying)stop();release() }
        player=null
        eventKey=null
    }
}
