package io.atmos.weather.earthquake

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes

internal data class SeismicAudioRoute(val usage:Int,val stream:Int)

/** Explicit opt-in; never changes global ringer, volume or interruption policy. */
internal object SeismicCriticalMode {
    const val KEY="eew_critical_mode_v1"
    fun enabled(ctx:Context)=ctx.getSharedPreferences("thinn_seismic_v15",0).getBoolean(KEY,false)
    fun setEnabled(ctx:Context,enabled:Boolean) {
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putBoolean(KEY,enabled).apply{
            if(enabled)putBoolean("allow_seismic_full_screen",true)
        }.apply()
        if(!enabled){SeismicCriticalTest.cancel(ctx);SeismicContinuousWarning.stop();SeismicSpeech.stop()}
        SeismicAlertPolicy.ensureChannels(ctx)
    }
    fun applies(ctx:Context,scenario:SeismicAlertPolicy.Scenario)=enabled(ctx)&&scenario.name.startsWith("EEW")
    fun route(ctx:Context,scenario:SeismicAlertPolicy.Scenario,forVoice:Boolean=false):SeismicAudioRoute? {
        val audio=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel=nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,scenario))?:return null
        if(channel.importance==NotificationManager.IMPORTANCE_NONE)return null
        if(channel.sound==null&&(!forVoice||SeismicAlertPolicy.soundEnabled(ctx,scenario)||channel.hasUserSetSound()))return null
        val filter=when(nm.currentInterruptionFilter){
            NotificationManager.INTERRUPTION_FILTER_ALL->SeismicInterruption.ALL
            NotificationManager.INTERRUPTION_FILTER_PRIORITY->SeismicInterruption.PRIORITY
            NotificationManager.INTERRUPTION_FILTER_ALARMS->SeismicInterruption.ALARMS
            NotificationManager.INTERRUPTION_FILTER_NONE->SeismicInterruption.NONE
            else->SeismicInterruption.UNKNOWN
        }
        return when(SeismicCriticalAudioPolicy.chooseRoute(applies(ctx,scenario),audio.ringerMode==AudioManager.RINGER_MODE_NORMAL,filter,
            nm.isNotificationPolicyAccessGranted,channel.canBypassDnd(),audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION),audio.getStreamVolume(AudioManager.STREAM_ALARM))){
            SeismicSoundRoute.ALARM->SeismicAudioRoute(AudioAttributes.USAGE_ALARM,AudioManager.STREAM_ALARM)
            SeismicSoundRoute.NOTIFICATION->SeismicAudioRoute(AudioAttributes.USAGE_NOTIFICATION_EVENT,AudioManager.STREAM_NOTIFICATION)
            null->null
        }
    }
}
