package io.atmos.weather.earthquake

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.security.MessageDigest

/** Private bounded tickets survive process restart. Untrusted Intent extras never create an alert. */
object SeismicAlertDelivery {
    data class Ticket(val key:String,val incident:EewIncident)
    val active=MutableStateFlow<Ticket?>(null)
    private const val STORE="thinn_seismic_delivery_v27"
    fun identity(e:EewIncident):String {
        val source=when(e.source.trim().lowercase(java.util.Locale.ROOT)){
            "cwa_official","cwa"->"cwa";"exptech_verified","exptech eew"->"exptech";else->e.source.trim().lowercase(java.util.Locale.ROOT)}
        return "$source:${e.id}"
    }
    private fun key(id:String)=MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(16).joinToString(""){"%02x".format(it.toInt() and 255)}
    fun expires(e:EewIncident)=minOf(e.issuedAtMs+120_000,e.expiresAtMs?:Long.MAX_VALUE)
    internal fun valid(e:EewIncident,now:Long)=ExpTechRealtimeParser.isFresh(e,now) && now<expires(e) &&
        e.id.length in 1..128 && e.serial in 0..999 && e.source.length in 1..80 && e.epicenter.length in 1..160 &&
        (e.magnitude==null || e.magnitude.isFinite()&&e.magnitude in 0.0..10.0) &&
        (e.depthKm==null || e.depthKm.isFinite()&&e.depthKm in 0.0..700.0)
    fun hasDelivered(ctx:Context,e:EewIncident)=ctx.getSharedPreferences(STORE,0).contains(key(identity(e)))
    @Synchronized fun publish(ctx:Context,e:EewIncident,now:Long=System.currentTimeMillis(),scenario:SeismicAlertPolicy.Scenario=SeismicAlertPolicy.Scenario.EEW_FIRST):Ticket? {
        if(!valid(e,now)||!SeismicAlertThreshold.accepts(ctx,e))return null
        val prefs=ctx.getSharedPreferences(STORE,0);val id=key(identity(e))
        val previous=runCatching{JSONObject(prefs.getString(id,null)?:"{}").optInt("serial",-1)}.getOrDefault(-1)
        if(e.serial<=previous)return null
        val obj=JSONObject().put("id",e.id).put("serial",e.serial).put("source",e.source).put("timestamp",e.issuedAtMs)
            .put("location",e.epicenter).put("magnitude",e.magnitude?:JSONObject.NULL).put("depth",e.depthKm).put("lat",e.latitude).put("lon",e.longitude)
            .put("emergency",e.urgent).put("originTime",e.originAtMs).put("expiresAtMillis",e.expiresAtMs)
            .put("countyIntensities",JSONObject(e.localIntensityByCounty)).put("countyArrivalTimes",JSONObject(e.arrivalAtMsByCounty))
            .put("maximumIntensity",e.maximumIntensity).put("alertScenario",scenario.key)
        val editor=prefs.edit().putString(id,obj.toString())
        prefs.all.entries.filter{it.key!=id}.sortedByDescending{runCatching{JSONObject(it.value.toString()).optLong("timestamp")}.getOrDefault(0)}
            .drop(7).forEach{editor.remove(it.key)}
        if(!editor.commit())return null
        return Ticket(id,e).also{active.value=it}
    }
    fun scenario(ctx:Context,e:EewIncident):SeismicAlertPolicy.Scenario {
        val raw=ctx.getSharedPreferences(STORE,0).getString(key(identity(e)),null)
        val name=runCatching{JSONObject(raw?:"{}").optString("alertScenario")}.getOrNull()
        return SeismicAlertPolicy.Scenario.entries.firstOrNull{it.name.startsWith("EEW")&&it.key==name}
            ?:if(e.urgent)SeismicAlertPolicy.Scenario.EEW_URGENT else SeismicAlertPolicy.Scenario.EEW_FIRST
    }
    fun resolve(ctx:Context,key:String?,now:Long=System.currentTimeMillis()):Ticket? {
        if(key==null||!key.matches(Regex("[0-9a-f]{32}")))return null
        val raw=ctx.getSharedPreferences(STORE,0).getString(key,null)?:return null
        val e=ExpTechRealtimeParser.parseEew(raw).singleOrNull()?:return null
        return Ticket(key,e).takeIf{valid(e,now)&&SeismicAlertThreshold.accepts(ctx,e)&&SeismicAlertPolicy.alertEnabled(ctx,scenario(ctx,e))}
    }
    fun markNotification(ctx:Context,key:String){ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putString("latest_notification_ticket",key).apply()}
    @Synchronized fun dismiss(ctx:Context,key:String) {
        val prefs=ctx.getSharedPreferences(STORE,0)
        prefs.getString(key,null)?.let{raw->runCatching{val obj=JSONObject(raw).put("cancelled",true);prefs.edit().putString(key,obj.toString()).apply()}}
        val routing=ctx.getSharedPreferences("thinn_seismic_v15",0)
        if(routing.getString("latest_notification_ticket",null)==key){
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(29200)
            routing.edit().remove("latest_notification_ticket").apply()
        }
        if(active.value?.key==key){active.value=null;SeismicContinuousWarning.stop();SeismicSpeech.stop()}
    }
    fun cancel(ctx:Context,e:EewIncident,now:Long=System.currentTimeMillis()):Boolean {
        if(!e.cancelled||e.test||now-e.issuedAtMs !in -15_000L..120_000L)return false
        val ticketKey=key(identity(e));val raw=ctx.getSharedPreferences(STORE,0).getString(ticketKey,null)?:return false
        val serial=runCatching{JSONObject(raw).optInt("serial",-1)}.getOrDefault(-1)
        if(e.serial<serial)return false
        dismiss(ctx,ticketKey);return true
    }
    fun mayUseFullScreen(ctx:Context):Boolean {
        val enabled=ctx.getSharedPreferences("thinn_seismic_v15",0).getBoolean("allow_seismic_full_screen",false)
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return enabled&&(Build.VERSION.SDK_INT<34||runCatching{nm.canUseFullScreenIntent()}.getOrDefault(false))
    }
    fun pendingIntent(ctx:Context,ticket:Ticket):PendingIntent {
        val intent=Intent(ctx,SeismicAlertActivity::class.java).setData(android.net.Uri.parse("thinn-eew://${ticket.key}"))
            .putExtra("ticket",ticket.key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val options=if(Build.VERSION.SDK_INT>=35)ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }.toBundle() else null
        return PendingIntent.getActivity(ctx,ticket.key.hashCode(),intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,options)
    }
}
@Composable fun SeismicForegroundAlertHost() {
    val ctx=LocalContext.current;val owner=LocalLifecycleOwner.current
    var resumed by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,_->resumed=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    val ticket by SeismicAlertDelivery.active.collectAsState()
    var fresh by remember(ticket){mutableStateOf(ticket?.let{SeismicAlertDelivery.valid(it.incident,System.currentTimeMillis())}==true)}
    LaunchedEffect(ticket){val t=ticket?:return@LaunchedEffect;delay((SeismicAlertDelivery.expires(t.incident)-System.currentTimeMillis()).coerceAtLeast(0));fresh=false;SeismicAlertDelivery.dismiss(ctx,t.key)}
    if(resumed&&fresh)ticket?.let{t->SeismicWarningOverlay(t.incident,false,SeismicUserLocation.current(ctx)?.name,{SeismicAlertDelivery.dismiss(ctx,t.key)})}
}
