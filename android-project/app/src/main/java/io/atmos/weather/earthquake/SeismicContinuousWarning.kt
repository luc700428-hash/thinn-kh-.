package io.atmos.weather.earthquake

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/** Event-bounded attention tone; critical mode uses the permitted alarm route.
 * Global ringer, volume and DND settings remain under system/user control.
 */
object SeismicContinuousWarning {
    private val main=Handler(Looper.getMainLooper())
    private var player:MediaPlayer?=null
    private var eventKey:String?=null
    private val timeout=Runnable { stop() }
    @Synchronized fun start(ctx:Context,incident:EewIncident,scenario:SeismicAlertPolicy.Scenario=SeismicAlertDelivery.scenario(ctx,incident)) {
        val key="${incident.id}:${incident.serial}"
        if(eventKey==key && player?.isPlaying==true) return
        stop()
        if(!ExpTechRealtimeParser.isFresh(incident)||incident.test||incident.cancelled)return
        if(!SeismicAlertPolicy.alertEnabled(ctx,scenario) || !SeismicAlertThreshold.accepts(ctx,incident) ||
            !io.atmos.weather.permission.PermissionGuide.canNotify(ctx) || !SeismicAlertPolicy.soundEnabled(ctx,scenario)) return
        val notifications=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        SeismicAlertPolicy.ensureChannels(ctx)
        val channel=notifications.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,scenario))
        if(channel?.importance==NotificationManager.IMPORTANCE_NONE || channel?.sound==null)return
        val route=SeismicCriticalMode.route(ctx,scenario)?:return
        runCatching {
            val attributes=AudioAttributes.Builder().setUsage(route.usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            player=MediaPlayer.create(ctx.applicationContext,scenario.sound,attributes,AudioManager.AUDIO_SESSION_ID_GENERATE)?.apply {
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
