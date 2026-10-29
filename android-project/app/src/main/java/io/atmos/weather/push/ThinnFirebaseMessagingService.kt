package io.atmos.weather.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.atmos.weather.MainActivity
import io.atmos.weather.R
import io.atmos.weather.widgets.WidgetSync
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.XiaomiFocusBridge

/** FCM data messages supplement foreground SSE; they cannot guarantee delivery or bypass DND. */
class ThinnFirebaseMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token:String) {
        if(SeismicPushRegistration.optedIn(this)) SeismicPushRegistration.register(this, token)
    }
    override fun onMessageReceived(message:RemoteMessage) {
        if(!SeismicPushRegistration.optedIn(this)) return
        val d=message.data
        if(d["kind"] != "EEW") return // no accidental critical notifications from routine push
        val e=PushEnvelope(
            eventId=d["eventId"].orEmpty(), revision=d["revision"]?.toIntOrNull() ?: -1,
            issuedAtMillis=d["issuedAtMillis"]?.toLongOrNull() ?: 0L,
            expiresAtMillis=d["expiresAtMillis"]?.toLongOrNull() ?: 0L,
            region=d["region"].orEmpty(), source=d["source"].orEmpty(),
            magnitude=d["magnitude"]?.toDoubleOrNull(), title=d["title"].orEmpty(),
            detail=d["detail"].orEmpty())
        val now=System.currentTimeMillis()
        val pref=getSharedPreferences("thinn_push_alerts",Context.MODE_PRIVATE)
        val key="event_"+e.eventId
        val decision=SeismicPushPolicy.check(e,now,pref.getInt(key,-1).takeIf{it>=0})
        if(decision != PushDecision.ACCEPT) return
        // Commit revision before notification so duplicate revisions from SSE/FCM can be filtered.
        if(!pref.edit().putInt(key,e.revision).commit()) return
        getSharedPreferences("thinn_push_widget",Context.MODE_PRIVATE).edit()
            .putString("title",e.title).putString("detail",e.detail)
            .putLong("issued",now).apply()
        val quake=io.atmos.weather.earthquake.ExpTechRealtimeParser.parseEew(org.json.JSONObject().apply{
            put("cancelled",d["cancelled"]=="true");put("test",d["test"]=="true"||d["replay"]=="true");put("id",e.eventId);put("serial",e.revision);put("source",d["agency"]?.takeIf{it in setOf("cwa","trem","nied")}?:e.source);put("timestamp",e.issuedAtMillis);put("expiresAtMillis",e.expiresAtMillis)
            put("location",d["epicenter"]?.take(160)?:e.title);put("magnitude",e.magnitude);put("emergency",d["emergency"]=="true")
            listOf("depth","lat","lon","originTime","maximumIntensity").forEach{k->d[k]?.let{put(k,it)}}
            listOf("countyIntensities","countyArrivalTimes").forEach{k->d[k]?.takeIf{it.length<=2500}?.let{value->runCatching{put(k,org.json.JSONObject(value))}}}
        }.toString()).singleOrNull()?:return
        if(quake.cancelled){
            io.atmos.weather.earthquake.SeismicAlertDelivery.cancel(this,quake)
            WidgetSync.updateVisible(applicationContext);return
        }
        // Push policy has checked authenticated transport, source, region, expiry and revision.
        io.atmos.weather.earthquake.SeismicAlertNotifier.deliver(this,quake,
            if(quake.urgent)io.atmos.weather.earthquake.SeismicAlertPolicy.Scenario.EEW_URGENT
            else if(e.revision>1)io.atmos.weather.earthquake.SeismicAlertPolicy.Scenario.EEW_UPDATE
            else io.atmos.weather.earthquake.SeismicAlertPolicy.Scenario.EEW_FIRST)
        WidgetSync.updateVisible(applicationContext)
    }
}
