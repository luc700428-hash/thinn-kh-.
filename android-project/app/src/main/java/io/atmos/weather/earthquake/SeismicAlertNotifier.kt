package io.atmos.weather.earthquake
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.permission.PermissionGuide

/** Authenticated FCM and SSE share filtering, deduplication, notification and foreground display. */
object SeismicAlertNotifier {
    fun mayAnnounce(ctx:Context,e:EewIncident):Boolean {
        val scenario=SeismicAlertDelivery.scenario(ctx,e)
        val channel=(ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(SeismicAlertPolicy.channelId(ctx,scenario))
        return PermissionGuide.canNotify(ctx)&&SeismicAlertPolicy.alertEnabled(ctx,scenario)&&SeismicAlertThreshold.accepts(ctx,e)&&channel?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    fun deliver(ctx:Context,e:EewIncident,requested:SeismicAlertPolicy.Scenario):Boolean {
        if(!requested.name.startsWith("EEW"))return false
        val scenario=if(requested==SeismicAlertPolicy.Scenario.EEW_UPDATE&&!SeismicAlertDelivery.hasDelivered(ctx,e))SeismicAlertPolicy.Scenario.EEW_FIRST else requested
        if(!SeismicAlertPolicy.alertEnabled(ctx,scenario)||!SeismicAlertThreshold.accepts(ctx,e))return false
        val ticket=SeismicAlertDelivery.publish(ctx,e,scenario=scenario)?:return false
        if(!PermissionGuide.canNotify(ctx))return true
        SeismicAlertPolicy.ensureChannels(ctx)
        val channel=SeismicAlertPolicy.channelId(ctx,scenario)
        if((ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(channel)?.importance==NotificationManager.IMPORTANCE_NONE)return true
        val local=SeismicAlertThreshold.local(e.localIntensityByCounty,SeismicUserLocation.current(ctx)?.name)
        val title="收到地震速報 · 慎防強烈搖晃"
        val detail="${SeismicDisplayFormat.location(e.epicenter)} · ${SeismicDisplayFormat.magnitude(e.magnitude)} · "+(local?.let{"所在地預估 ${SeismicDisplayFormat.symbol(it)} 級"}?:"所在地震度待來源提供")
        val pi=SeismicAlertDelivery.pendingIntent(ctx,ticket)
        val builder=NotificationCompat.Builder(ctx,channel).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText("$detail\n趴下、掩護、穩住 · ${e.source} 第 ${e.serial} 報\n第三方速報，非政府災防告警訊息"))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(pi)
            .setTimeoutAfter((SeismicAlertDelivery.expires(e)-System.currentTimeMillis()).coerceIn(1,120_000))
        if(SeismicAlertDelivery.mayUseFullScreen(ctx))builder.setFullScreenIntent(pi,true)
        val sent=runCatching{WeatherNotificationDispatcher.post(ctx,29200,builder.build(),title,detail,XiaomiFocusBridge.Kind.SEISMIC_EEW)}.isSuccess
        if(!sent)return true // Permission may have changed between checking and posting.
        SeismicAlertDelivery.markNotification(ctx,ticket.key)
        SeismicSound.stop() // Stop a local drill/replay before the real event tone.
        SeismicContinuousWarning.start(ctx,e,scenario)
        io.atmos.weather.voice.NaturalVoiceHub.interruptForEarthquake()
        if(SeismicSpeech.enabled(ctx,"eew_voice_enabled"))SeismicSpeech.speak(ctx,SeismicSpeech.warning(e,SeismicUserLocation.current(ctx)))
        return true
    }
}
