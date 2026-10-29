package io.atmos.weather.earthquake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.atmos.weather.MainActivity
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.permission.PermissionGuide
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

/** User-started foreground connection. Android can still stop it; never promise guaranteed EEW. */
class ExpTechSeismicService : Service() {
    companion object {
        const val ACTION_STOP = "io.atmos.weather.STOP_EXPTECH_EEW"
        const val ACTION_START_LIVE = "io.atmos.weather.START_LIVE_MONITOR"
        const val ACTION_UNPIN_LIVE = "io.atmos.weather.UNPIN_LIVE_MONITOR"
        const val STATUS_CHANNEL = "thinn_seismic_stream_status_v15"
        const val ALERT_CHANNEL = "thinn_seismic_eew_urgent_v15"
        val live = MutableStateFlow(RealtimeDisplay())
        fun start(ctx: Context, promoteLive: Boolean = false) {
            val request = Intent(ctx,ExpTechSeismicService::class.java)
            if (promoteLive) request.action = ACTION_START_LIVE
            ContextCompat.startForegroundService(ctx,request)
        }
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx,ExpTechSeismicService::class.java).setAction(ACTION_STOP))
        }
    }
    private val supervisor = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeConnections=CopyOnWriteArrayList<HttpURLConnection>()
    private val hosts=listOf("api.lb-tpe1.exptech.dev","api.lb-khh1.exptech.dev")
    private var begun=false
    // Only a manual start is eligible for promoted, user-initiated monitoring.
    // Automatically started monitoring always remains a normal foreground notification.
    @Volatile private var liveUpdateRequested=false
    private var lastLiveRefreshAt=0L
    private val stationGate=StationDetectionGate()
    @Volatile private var stationMetadata: Map<String, SeismicStation> = emptyMap()
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags:Int,startId:Int):Int {
        if (intent?.action==ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action==ACTION_UNPIN_LIVE) {
            liveUpdateRequested=false
            if (begun) updateStatusNotification("即時地震監測持續進行；已取消置頂")
            return START_STICKY
        }
        if (intent?.action==ACTION_START_LIVE) {
            liveUpdateRequested=true
            if (begun) { updateStatusNotification("即時監測中，可從通知停止或取消置頂") ; return START_STICKY }
        }
        if (begun) return START_STICKY
        begun=true
        ensureChannels()
        val ongoing=ongoingNotification("正在連接 ExpTech 即時地震資料；非官方保證送達的預警")
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(29150,ongoing,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(29150,ongoing)
        live.update { it.copy(enabled=true,eewStatus="連線中",rtsStatus="連線中") }
        supervisor.launch {
            stationMetadata=runCatching { SeismicStationData.fetchMetadata() }.getOrDefault(emptyMap())
            live.update { it.copy(stationMetadataStatus=if(stationMetadata.isEmpty()) "測站座標暫未取得" else "已取得 ${stationMetadata.size} 筆位置資料") }
        }
        supervisor.launch { runFeed("EEW","/api/v2/eq/eew?sse=1&compress=1") }
        supervisor.launch { runFeed("RTS","/api/v1/trem/sse?topics=trem.rts.v1&mode=live") }
        return START_STICKY
    }
    override fun onDestroy() {
        activeConnections.forEach { it.disconnect() }
        supervisor.cancel()
        live.update { it.copy(enabled=false,eewStatus="已停止",rtsStatus="已停止") }
        super.onDestroy()
    }
    private fun ensureChannels() {
        val manager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(manager.getNotificationChannel(STATUS_CHANNEL)==null)
            manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL,"地震即時監測連線",NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null,null); description="經使用者在 App 內啟動的 ExpTech SSE 即時串流"
            })
        SeismicAlertPolicy.ensureChannels(this)

    }
    private fun openCenter():PendingIntent = PendingIntent.getActivity(this,29151,
        Intent(this,MainActivity::class.java).putExtra("open_earthquake_center",true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun ongoingNotification(detail:String):Notification {
        val stopAction=PendingIntent.getService(this,29153,
            Intent(this,ExpTechSeismicService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val unpinAction=PendingIntent.getService(this,29154,
            Intent(this,ExpTechSeismicService::class.java).setAction(ACTION_UNPIN_LIVE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val promoted=Build.VERSION.SDK_INT >= 36 && liveUpdateRequested
        val builder=NotificationCompat.Builder(this,STATUS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("thinn-khì · 地震即時監測中")
            .setContentText(detail).setContentIntent(openCenter()).setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止監測",stopAction)
        if (promoted) {
            builder.setRequestPromotedOngoing(true)
                .setShortCriticalText("監測中")
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,"取消置頂",unpinAction)
                .setDeleteIntent(unpinAction)
        }
        return builder.build()
    }
    private fun updateStatusNotification(detail:String) {
        if (!begun) return
        runCatching {
            val nm=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(29150,ongoingNotification(detail))
        }
    }
    private suspend fun runFeed(type:String,path:String) {
        var attempt=0
        while (supervisor.isActive) {
            val host=hosts[attempt % hosts.size]
            attempt++
            try {
                setStatus(type,"連接 $host")
                stream(host,path,type)
            } catch (err:Exception) {
                if (!supervisor.isActive) break
                setStatus(type,"連線中斷，等待重連")
            }
            delay(if(type=="RTS")minOf(5_000L,1000L*(1L shl minOf(attempt/2,3))) else minOf(30_000L,1500L*(1L shl minOf(attempt/2,4))))
        }
    }
    private fun setStatus(type:String,value:String) {
        live.update { if(type=="EEW") it.copy(eewStatus=value) else it.copy(rtsStatus=value) }
        // Rate-limit the active user session notification: no alert spam.
        if (liveUpdateRequested) {
            val now=android.os.SystemClock.elapsedRealtime()
            if (now-lastLiveRefreshAt>=30_000L) {
                lastLiveRefreshAt=now
                updateStatusNotification("${type}：${value.take(48)} · ExpTech 第三方監測")
            }
        }
    }
    private fun stream(host:String,path:String,type:String) {
        val connection=(URL("https://$host$path").openConnection() as HttpURLConnection).apply {
            connectTimeout=10_000
            readTimeout=if(type=="RTS")12_000 else 95_000 // Retry a silent station stream before the 20s expiry.
            setRequestProperty("Accept","text/event-stream")
            setRequestProperty("Cache-Control","no-cache")
            setRequestProperty("User-Agent","thinn-khi/21.0 (+Android; seismic public feed)")
            instanceFollowRedirects=false
        }
        activeConnections.add(connection)
        try {
            connection.connect()
            if(connection.responseCode!=200 || !connection.contentType.orEmpty().contains("text/event-stream",true))
                throw IllegalStateException("Unexpected SSE response ${connection.responseCode}")
            setStatus(type,"即時串流已連線 · $host")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var event="message"
                val lines=StringBuilder()
                while(supervisor.isActive) {
                    val line=reader.readLine() ?: break
                    if(line.isEmpty()) {
                        if (lines.isNotEmpty()) {
                            val raw=decodePayload(event,lines.toString())
                            if (raw!=null && (type=="EEW" && event in setOf("g","message") || type=="RTS" && event in setOf("trem.rts.v1","g","message"))) handleFrame(type,raw)
                        }
                        lines.setLength(0);event="message"
                    } else if(line.startsWith("event:")) event=line.substringAfter(':').trim()
                    else if(line.startsWith("data:")) {
                        if (lines.length + line.length < 750_000) lines.append(line.substringAfter(':').trim())
                    }
                }
            }
        } finally { activeConnections.remove(connection);connection.disconnect() }
    }
    private fun decodePayload(event:String,data:String):String? = try {
        if(data.trimStart().startsWith("{")||data.trimStart().startsWith("["))data
        else if (event=="g" || event=="trem.rts.v1") {
            val packed=Base64.decode(data,Base64.DEFAULT)
            GZIPInputStream(packed.inputStream()).use { stream ->
                val buffer=ByteArray(8192)
                val out=java.io.ByteArrayOutputStream()
                while (out.size()<750_000) {
                    val count=stream.read(buffer,0,minOf(buffer.size,750_000-out.size()))
                    if(count<0) break
                    out.write(buffer,0,count)
                }
                out.toString("UTF-8")
            }
        } else data
    } catch (_:Exception) { null }
    private fun handleFrame(type:String,raw:String) {
        val time=System.currentTimeMillis()
        if(type=="RTS") {
            val stations=SeismicStationData.parse(raw,stationMetadata,receivedAtMs=time)
            // Empty/unlocatable frames are not proof of a new valid observation.
            // Keep the last verified snapshot for labelled historical display;
            // importantly, do NOT advance lastRtsAt or send a detection notification.
            if (stations.any { it.intensity != null || it.accelerationGal != null }) {
                val current=stations.filter{StationFreshness.state(it,time)==StationViewState.LIVE}
                val newest=stations.mapNotNull{it.observedAtMs}.filter{it<=time+2_000L}.maxOrNull()
                live.update {it.copy(lastRtsAt=if(newest!=null)maxOf(it.lastRtsAt,newest) else it.lastRtsAt,
                    stationCount=ExpTechRealtimeParser.stationCount(raw),stations=StationFreshness.merge(it.stations,stations))}
                if(current.isNotEmpty())maybeNotifyStation(current,time)
            } else if(stations.any{it.availability!=StationAvailability.UNKNOWN}) {
                // Explicit provider maintenance/offline is useful even without a magnitude;
                // it must not renew the observation timestamp or trigger an alert.
                live.update{it.copy(stations=StationFreshness.merge(it.stations,stations))}
            }
            return
        }
        live.update { it.copy(lastEewAt=time) }
        ExpTechRealtimeParser.parseEew(raw).forEach { quake ->
            if(quake.cancelled){
                val current=live.value.incident
                if(!quake.test&&time-quake.issuedAtMs in -15_000L..120_000L) {
                    SeismicAlertDelivery.cancel(this,quake,time)
                    if(current!=null&&SeismicAlertDelivery.identity(current)==SeismicAlertDelivery.identity(quake)&&quake.serial>=current.serial)
                        live.update{it.copy(incident=quake)}
                }
                return@forEach
            }
            // In particular, Japanese EEW that does not affect Taiwan/islands must not
            // reach the alert, speech, focus-island or recent-event display pipeline.
            if (!TaiwanEewScope.relevant(quake)) return@forEach
            live.update { state ->
                state.copy(incident=quake, recentIncidents=(listOf(quake) + state.recentIncidents.filterNot {
                    it.id==quake.id && it.serial==quake.serial
                }).take(20))
            }
            if (!ExpTechRealtimeParser.isFresh(quake,time)) return@forEach
            val prefs=getSharedPreferences("thinn_seismic_v15",Context.MODE_PRIVATE)
            val previousIdentity = prefs.getString("last_realtime_eew_identity",null)
            // Different earthquakes can interleave. A single global (id, serial)
            // cursor makes the older event ring again when its frames are replayed.
            // Persist sequence by opaque SHA-256 key, never by a raw untrusted ID.
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(SeismicAlertDelivery.identity(quake).toByteArray(Charsets.UTF_8))
                .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val eventKey = "seen_eew_seq_$digest"
            val previousSerial = prefs.getInt(eventKey,
                if (previousIdentity == SeismicAlertDelivery.identity(quake)) prefs.getInt("last_realtime_eew_serial", -1) else -1)
            if (previousIdentity == SeismicAlertDelivery.identity(quake) && previousSerial < 0) {
                // A source-aware event cursor from an earlier build must not repeat an old alarm.
                prefs.edit().putInt(eventKey,quake.serial)
                    .putInt("last_realtime_eew_serial",quake.serial).apply()
                return@forEach
            }
            if (quake.serial <= previousSerial) return@forEach
            val scenario = when {
                quake.urgent -> SeismicAlertPolicy.Scenario.EEW_URGENT
                SeismicAlertDelivery.hasDelivered(this,quake) -> SeismicAlertPolicy.Scenario.EEW_UPDATE
                else -> SeismicAlertPolicy.Scenario.EEW_FIRST
            }
            // Persist event and report sequence even when a user disabled a sound or alert.
            prefs.edit().putString("last_realtime_eew_id",quake.id)
                .putString("last_realtime_eew_identity",SeismicAlertDelivery.identity(quake))
                .putInt("last_realtime_eew_serial",quake.serial)
                .putInt(eventKey,quake.serial).apply()
            if (SeismicAlertPolicy.alertEnabled(this,scenario) && SeismicAlertThreshold.accepts(this,quake)) {
                if(sendWarning(quake,scenario))live.update { it.copy(lastAlertAt=time) }
            }

        }
    }
    private fun maybeNotifyStation(stations:List<SeismicStation>,now:Long) {
        if(!PermissionGuide.canNotify(this)) return
        val mapping=mapOf(
            StationDetectionGate.Threshold.DETECT_WEAK to SeismicAlertPolicy.Scenario.DETECT_WEAK,
            StationDetectionGate.Threshold.DETECT_MEDIUM to SeismicAlertPolicy.Scenario.DETECT_MEDIUM,
            StationDetectionGate.Threshold.DETECT_STRONG to SeismicAlertPolicy.Scenario.DETECT_STRONG,
            StationDetectionGate.Threshold.GAL_MEDIUM to SeismicAlertPolicy.Scenario.GAL_MEDIUM,
            StationDetectionGate.Threshold.GAL_STRONG to SeismicAlertPolicy.Scenario.GAL_STRONG)
        val enabled=mapping.filterValues { SeismicAlertPolicy.alertEnabled(this,it) }.keys
        val trigger=stationGate.observe(stations.map {
            StationDetectionGate.Reading(it.id,it.intensity,it.accelerationGal)
        },now,enabled) ?: return
        val scenario=mapping[trigger.threshold] ?: return
        val detail="ExpTech 第三方測站 ${trigger.stationId}：${trigger.threshold.label}，已連續確認；非官方地震預警"
        val notice=NotificationCompat.Builder(this,SeismicAlertPolicy.channelId(this,scenario))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("地震監測 · 第三方測站檢知")
            .setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true)
            .setContentIntent(openCenter()).build()
        // Per-case channel honours the user's sound preference and OS notification policy.
        WeatherNotificationDispatcher.post(
            this,
            29180+scenario.ordinal,
            notice,
            "地震監測 · 第三方測站檢知",
            detail,
            XiaomiFocusBridge.Kind.STATION_DETECTION
        )
    }
    private fun sendWarning(quake:EewIncident, scenario:SeismicAlertPolicy.Scenario):Boolean =
        SeismicAlertNotifier.deliver(this,quake,scenario)
}
