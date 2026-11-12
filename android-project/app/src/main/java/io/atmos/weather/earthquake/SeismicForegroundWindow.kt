package io.atmos.weather.earthquake

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/** Only an actually resumed app window may launch directly. Background delivery stays OS-owned. */
internal object SeismicForegroundWindow {
    @Volatile private var resumed:WeakReference<Activity>?=null
    fun resumed(activity:Activity) {resumed=WeakReference(activity)}
    fun paused(activity:Activity) {if(resumed?.get()===activity)resumed=null}
    fun clear(){resumed=null}
    fun open(intent:Intent,valid:()->Boolean):Boolean {
        val activity=resumed?.get()?.takeUnless{it.isFinishing||it.isDestroyed}?:return false
        Handler(Looper.getMainLooper()).post {
            if(resumed?.get()===activity&&!activity.isFinishing&&!activity.isDestroyed&&valid()) {
                runCatching{activity.startActivity(intent)}
            }
        }
        return true
    }
    fun present(context:Context,ticket:SeismicAlertDelivery.Ticket) {
        val intent=Intent(context,SeismicAlertActivity::class.java)
            .putExtra("ticket",ticket.key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        open(intent){SeismicAlertDelivery.active.value?.key==ticket.key&&SeismicAlertDelivery.resolve(context,ticket.key)!=null}
    }
}
