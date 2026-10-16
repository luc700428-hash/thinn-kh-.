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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.atmos.weather.MainActivity
import io.atmos.weather.R
import io.atmos.weather.widgets.WidgetSync

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
        notifyUser(e)
        WidgetSync.updateVisible(applicationContext)
    }
    private fun notifyUser(e:PushEnvelope) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val manager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel="thinn_seismic_push_high_v22"
        if(manager.getNotificationChannel(channel)==null) {
            manager.createNotificationChannel(NotificationChannel(channel,"地震速報 · 高優先通知",NotificationManager.IMPORTANCE_HIGH).apply {
                description="第三方即時地震事件提醒；可於系統設定停用，並非政府細胞廣播告警"
                enableVibration(true)
            })
        }
        val pi=PendingIntent.getActivity(this, e.eventId.hashCode(),
            Intent(this,MainActivity::class.java).apply {
                putExtra("open_earthquake_center",true)
                flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note=NotificationCompat.Builder(this,channel)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(e.title).setContentText(e.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.detail+"\n來源："+e.source+"｜非政府災防簡訊"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi).setAutoCancel(false).setOnlyAlertOnce(true).build()
        NotificationManagerCompat.from(this).notify(e.eventId.hashCode(),note)
    }
}
