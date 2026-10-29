package io.atmos.weather.earthquake
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/** Non-exported. Wake flags are applied only after resolving a fresh accepted ticket. */
class SeismicAlertActivity:ComponentActivity() {
    private var current by mutableStateOf<SeismicAlertDelivery.Ticket?>(null)
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);if(!load(intent))return
        setContent{io.atmos.weather.ui.ThinnAppTheme{
            val published by SeismicAlertDelivery.active.collectAsState()
            LaunchedEffect(published){
                val next=published?.let{SeismicAlertDelivery.resolve(this@SeismicAlertActivity,it.key)}
                if(next!=null)current=next
                else if(current?.let{SeismicAlertDelivery.resolve(this@SeismicAlertActivity,it.key)}==null)finish()
            }
            val ticket=current
            LaunchedEffect(ticket){ticket?:return@LaunchedEffect;delay((SeismicAlertDelivery.expires(ticket.incident)-System.currentTimeMillis()).coerceAtLeast(0));close(ticket)}
            ticket?.let{t->SeismicWarningOverlay(t.incident,false,SeismicUserLocation.current(this)?.name,{close(t)})}
        }}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);load(intent)}
    private fun load(intent:Intent):Boolean {
        val t=SeismicAlertDelivery.resolve(this,intent.getStringExtra("ticket"))
        if(t==null){if(current?.let{SeismicAlertDelivery.resolve(this,it.key)}==null)finish();return false};current=t
        val allowed=SeismicAlertDelivery.mayUseFullScreen(this)
        setShowWhenLocked(allowed);setTurnScreenOn(allowed);return true
    }
    private fun close(t:SeismicAlertDelivery.Ticket){SeismicAlertDelivery.dismiss(this,t.key);finish()}
}
