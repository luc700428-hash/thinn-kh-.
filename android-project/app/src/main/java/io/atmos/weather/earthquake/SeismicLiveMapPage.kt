package io.atmos.weather.earthquake

import io.atmos.weather.ui.GlassDivider as HorizontalDivider
import io.atmos.weather.ui.GlassIconButton as FilledTonalIconButton
import io.atmos.weather.ui.GlassAlertDialog as AlertDialog
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassTextButton as TextButton
import io.atmos.weather.ui.GlassIconButton as IconButton
import io.atmos.weather.ui.GlassSlider
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

private val iceBlue = Color(0xFF82E5FF)
private val light = Color.White
private val muted = Color(0xFFC5D6E4)

/** A distinct second page. The Taiwan outline is schematic; station and epicenter markers
 * are rendered ONLY if provider supplied valid geodetic coordinates.
 */
@Composable
fun SeismicLiveMapPage(
    onClose: () -> Unit,
    onSettings: () -> Unit,
    reports: List<EarthquakeReport>,
    reportSource: String = "ExpTech 第三方彙整報告",
    onRefreshReports: () -> Unit
) {
    val ctx = LocalContext.current
    val live by ExpTechSeismicService.live.collectAsState()
    var layer by remember { mutableStateOf("綜合") }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<SeismicStation?>(null) }
    var details by remember { mutableStateOf<EarthquakeReport?>(null) }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var waveNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var replayStatus by remember { mutableStateOf("") }
    var replayReport by remember { mutableStateOf<EarthquakeReport?>(null) }
    var replayIncident by remember { mutableStateOf<EewIncident?>(null) }
    var replayClock by remember { mutableLongStateOf(0L) }
    val replayScope = rememberCoroutineScope()
    var replayFrames by remember { mutableStateOf<List<EewIncident>>(emptyList()) }
    var replayPosition by remember { mutableFloatStateOf(0f) }
    var replayDuration by remember { mutableFloatStateOf(120f) }
    var replayPlaying by remember { mutableStateOf(false) }
    var replaySpeed by remember { mutableFloatStateOf(1f) }
    var replaySpoken by remember { mutableStateOf(false) }
    var lastReplayShown by remember { mutableStateOf("") }
    val inReplay = replayStatus == "回放中"

    // No fake history frames. Only replay archive EEW records actually matching a past report.
    fun beginReplay(target: EarthquakeReport? = null) {
        replayScope.launch {
            replayStatus = "讀取中"
            replayPlaying = false
            replayFrames = emptyList()
            replayReport = null
            replayIncident = null
            val eligible = if (target != null) listOf(target)
                else reports.take(25).filter { SeismicGeo.visible(it.latitude, it.longitude) }.shuffled().take(5)
            var found: Pair<EarthquakeReport, List<EewIncident>>? = null
            for (report in eligible) {
                val frames = try { SeismicHistoricalReplay.findFor(report) }
                    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (_: Exception) { emptyList() }
                if (frames.isNotEmpty()) { found = report to frames; break }
            }
            if (found == null) {
                replayStatus = "沒有可核對的歷史速報，無法回放"
                return@launch
            }
            val (report, frames) = found
            replayReport = report
            replaySpoken = false
            lastReplayShown = ""
            replayFrames = frames
            replayIncident = null // never show a past EEW bulletin before its actual issue time
            replayDuration = ((frames.last().issuedAtMs - report.origin.toEpochMilli()) / 1000f).coerceIn(15f, 180f)
            replayPosition = 0f
            replayClock = report.origin.toEpochMilli()
            replayStatus = "回放中"
            replayPlaying = true
            SeismicSound.play(ctx, SeismicSound.Kind.DRILL_START)
        }
    }
    LaunchedEffect(inReplay, replayPlaying, replaySpeed) {
        while (inReplay && replayPlaying) {
            delay(100)
            replayPosition = (replayPosition + .1f * replaySpeed).coerceAtMost(replayDuration)
            if (replayPosition >= replayDuration) {
                replayPlaying = false
                replayStatus = "回放完成（非真實預警）"
            }
        }
    }
    LaunchedEffect(replayPosition, replayFrames, inReplay) {
        if (inReplay && replayFrames.isNotEmpty()) {
            val at = (replayReport?.origin?.toEpochMilli() ?: 0L) + (replayPosition * 1000).toLong()
            replayClock = at
            replayIncident = replayFrames.lastOrNull { it.issuedAtMs <= at }
        }
    }
    val devicePlace = remember(timestamp) { SeismicUserLocation.current(ctx) }
    var overlay by remember { mutableStateOf<EewIncident?>(null) }
    var overlayReplay by remember { mutableStateOf(false) }
    var lastLiveShown by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            timestamp = System.currentTimeMillis()
        }
    }
    val eewHealth = SeismicFeedHealth.eew(live.enabled, live.eewStatus)
    val rtsHealth = SeismicFeedHealth.rts(live.enabled, live.rtsStatus, live.lastRtsAt, timestamp)
    val fresh = rtsHealth == SeismicFeedState.LIVE
    // Keep the latest provider-reported stations visible when the live feed becomes stale.
    // Stale measurements are labelled and must never be treated as current alerts.
    val stations = live.stations.filter { SeismicGeo.visible(it.latitude,it.longitude) }
    val quake = live.incident?.takeIf { SeismicGeo.visible(it.latitude,it.longitude) }
    val incidentRecent = quake != null && timestamp - quake.issuedAtMs in 0..120_000 && !quake.test && !quake.cancelled
    // Only verified fresh provider frames raise the foreground safety sheet.
    // Replay events have distinct state, wording and do not send real notifications.
    LaunchedEffect(quake?.id, quake?.serial, incidentRecent, inReplay) {
        if (!inReplay && incidentRecent && quake != null) {
            val key="${quake.id}:${quake.serial}"
            if (lastLiveShown != key) {
                lastLiveShown=key;overlayReplay=false;overlay=quake
            }
        }
    }
    LaunchedEffect(replayIncident?.id, replayIncident?.serial, replayPlaying) {
        val incident=replayIncident
        if (inReplay && replayPlaying && incident!=null) {
            val key="${incident.id}:${incident.serial}"
            if (lastReplayShown!=key) {
                lastReplayShown=key;overlayReplay=true;overlay=incident
                if(!replaySpoken && SeismicSpeech.enabled(ctx,"replay_voice_enabled")) {
                    replayReport?.let { report ->
                        SeismicSpeech.speak(ctx,SeismicSpeech.replay(report,incident))
                    }
                    replaySpoken=true
                }
            }
        }
    }
    LaunchedEffect(incidentRecent, inReplay) {
        while(incidentRecent && !inReplay) {
            waveNow=System.currentTimeMillis()
            delay(120) // Smooth P/S expanding circles without frequent GPS lookups.
        }
    }
    val latestReport = reports.firstOrNull { SeismicGeo.visible(it.latitude,it.longitude) }
    val displayQuake = if(inReplay) replayIncident else quake.takeIf { incidentRecent }
    val displayReport = if(inReplay) replayReport else latestReport
    val waveLat = if(inReplay) replayReport?.latitude else displayQuake?.latitude
    val waveLon = if(inReplay) replayReport?.longitude else displayQuake?.longitude
    val originMillis = if(inReplay) replayReport?.origin?.toEpochMilli() else displayQuake?.originAtMs
    val wavefront = if(SeismicGeo.visible(waveLat,waveLon) && originMillis!=null)
        SeismicWavePhysics.wavefront(originMillis,if(inReplay)replayClock else waveNow,
            if(inReplay) (replayReport?.depthKm ?: 0.0) else (displayQuake?.depthKm ?: 0.0)) else null

    // Real provider updates create one brief 260 ms highlight, not a simulated new event.
    val stationFlash = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(live.lastRtsAt) {
        if (live.lastRtsAt > 0L) {
            stationFlash.snapTo(1f)
            stationFlash.animateTo(0f, animationSpec=tween(260))
        }
    }
    // Stop replay sounds when the user exits the page or the replay ends.
    DisposableEffect(Unit) { onDispose { SeismicSound.stop() } }
    LaunchedEffect(replayPlaying) { if (!replayPlaying) SeismicSound.stop() }

    val maxI = stations.mapNotNull { it.intensity }.maxOrNull()
    val maxPga = stations.mapNotNull { it.accelerationGal }.maxOrNull()
    val pulse by rememberInfiniteTransition(label="seismic marker").animateFloat(
        initialValue=0.18f,targetValue=1f,
        animationSpec=infiniteRepeatable(tween(1900,easing=LinearEasing)),label="pulse")

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF06192D),Color(0xFF123B59),Color(0xFF0B223A)))).statusBarsPadding()) {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("地震監測",fontSize=28.sp,fontWeight=FontWeight.Bold,color=light)
                    Text("臺灣即時震度與地震速報",fontSize=14.sp,color=muted)
                }
                IconButton(onClick=onSettings) { Icon(Icons.Rounded.Settings,"地震設定",tint=iceBlue) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal=15.dp)) {
                MapGlass {
                    // Keep feed-status panel height stable when station reports arrive.
                    Row(Modifier.fillMaxWidth().height(102.dp), verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.RadioButtonChecked,null,tint=if(live.enabled)Color(0xFF58E0A6) else muted,modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if(live.enabled) "監測服務已啟動" else "即時監測未啟動",color=light,fontWeight=FontWeight.Bold)
                            Text("速報：${SeismicFeedHealth.eewLabel(eewHealth)}",fontSize=14.sp,
                                color=if(eewHealth==SeismicFeedState.LIVE) Color(0xFF7FE8BA) else Color(0xFFFFD09E))
                            Text("測站：${SeismicFeedHealth.rtsLabel(rtsHealth)}${if (!fresh && stations.isNotEmpty()) " · 上次觀測，非即時" else ""}",fontSize=14.sp,
                                color=if(fresh) Color(0xFF7FE8BA) else Color(0xFFFFD09E))
                        }
                        Button(onClick={if(live.enabled) ExpTechSeismicService.stop(ctx) else ExpTechSeismicService.start(ctx)}) {
                            Text(if(live.enabled)"停止" else "啟動")
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(if(fresh) "最後有效測站時間：${formatted(live.lastRtsAt)}（臺灣時間）"
                        else "測站數值僅在 15 秒內收到新資料才顯示；離線不沿用過期震度",
                        color=if(fresh)Color(0xFF7FE8BA) else Color(0xFFFFD09E),fontSize=14.sp)
                    // Always reserve the same space. Live feed changes must not resize
                    // the map card or move the user's scroll position every few seconds.
                    Text(if (live.enabled && eewHealth != SeismicFeedState.LIVE)
                            "速報連線狀態：${live.eewStatus}；請檢查網路與通知權限"
                        else if (live.enabled) "地震速報連線狀態正常" else "監測暫停，可隨時啟動",
                        color=if(live.enabled && eewHealth==SeismicFeedState.LIVE) iceBlue else Color(0xFFFFD09E),
                        fontSize=14.sp,lineHeight=18.sp,minLines=2,maxLines=2)
                    Text("所在地：${devicePlace?.name ?: "尚未定位"} · ${devicePlace?.source ?: "未指定"}",
                        color=iceBlue,fontSize=14.sp)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(153.dp)) {
                    Column {
                    if(inReplay) {
                        Text("歷史資料回放中 · 不是真實地震預警",color=Color(0xFFFFD09E),fontWeight=FontWeight.Bold)
                    } else if(incidentRecent) {
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF713B30).copy(alpha=.77f))
                            .border(1.dp,Color(0xFFFFBC8F).copy(alpha=.70f),RoundedCornerShape(18.dp))
                            .padding(13.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WarningAmber,null,tint=Color(0xFFFFD09E))
                                Spacer(Modifier.width(8.dp))
                                Text("收到第三方即時地震速報",color=light,fontWeight=FontWeight.Bold,fontSize=16.sp)
                            }
                            Text("${quake?.epicenter ?: "震央位置待確認"}  ·  ${quake?.magnitude?.let { "M${"%.1f".format(Locale.US,it)}" } ?: "規模未提供"}",
                                color=light,fontSize=16.sp,modifier=Modifier.padding(top=4.dp))
                            Text("發布時間：${quake?.issuedAtMs?.let(::formatted) ?: "未知"} · 第 ${quake?.serial ?: 0} 報",
                                color=Color(0xFFFFE5D0),fontSize=14.sp)
                            Text("立即趴下、掩護、穩住。第三方速報可能延遲，不保證預警秒數。",
                                color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=14.sp,
                                modifier=Modifier.padding(top=6.dp))
                        }
                    } else {
                        Text("目前沒有可顯示的有效即時速報；不代表沒有地震，也不代表所在地安全",fontSize=14.sp,color=muted)
                    }
                    }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MapGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("台澎金馬地震監測",color=light,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Text(if(fresh) "● 連線中" else "● 等待資料",fontSize=14.sp,
                            color=if(fresh)Color(0xFF65F3C6) else Color(0xFFFFD09E))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        listOf("綜合","測站","震央").forEach { mode ->
                            val active=mode==layer
                            Box(Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if(active)iceBlue.copy(alpha=.22f) else light.copy(alpha=.08f))
                                .border(1.dp,if(active)iceBlue.copy(alpha=.5f) else light.copy(alpha=.1f),RoundedCornerShape(12.dp))
                                .clickable { layer=mode;selected=null }.padding(horizontal=14.dp,vertical=8.dp)) {
                                Text(mode,color=light,fontSize=14.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(410.dp).clip(RoundedCornerShape(19.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0A1D34),Color(0xFF152A41))))) {
                        val pins=if(layer=="震央") emptyList() else stations.take(900)
                        Canvas(Modifier.fillMaxSize().pointerInput(pins,zoom,panX,panY) {
                            detectTapGestures { clicked ->
                                val unscaledX = (clicked.x - panX - size.width*0.5f) / zoom + size.width*0.5f
                                val unscaledY = (clicked.y - panY - size.height*0.5f) / zoom + size.height*0.5f
                                selected = pins.minByOrNull { point ->
                                    val dx = SeismicGeo.screenX(point.longitude!!,size.width.toFloat(),size.height.toFloat()) - unscaledX
                                    val dy = SeismicGeo.screenY(point.latitude!!,size.width.toFloat(),size.height.toFloat()) - unscaledY
                                    hypot(dx,dy)
                                }?.takeIf { point ->
                                    hypot(SeismicGeo.screenX(point.longitude!!,size.width.toFloat(),size.height.toFloat())-unscaledX,
                                        SeismicGeo.screenY(point.latitude!!,size.width.toFloat(),size.height.toFloat())-unscaledY) < 32.dp.toPx()/zoom
                                }
                            }
                        }.pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                zoom=(zoom*zoomChange).coerceIn(1f,4f)
                                panX=(panX+pan.x).coerceIn(-size.width*.7f,size.width*.7f)
                                panY=(panY+pan.y).coerceIn(-size.height*.7f,size.height*.7f)
                            }
                        }) {
                            withTransform({ translate(panX,panY)
                                scale(zoom,zoom,pivot=Offset(size.width/2f,size.height/2f)) }) {
                                for (i in 0..6) {
                                    val x=(i+.5f)/7f*size.width
                                    drawLine(iceBlue.copy(alpha=.08f),Offset(x,0f),Offset(x,size.height),1.dp.toPx())
                                }
                                for (i in 0..8) {
                                    val y=(i+.5f)/9f*size.height
                                    drawLine(iceBlue.copy(alpha=.08f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())
                                }
                                val path=Path()
                                SeismicGeo.island.forEachIndexed { index,(longitude,latitude) ->
                                    val xx=SeismicGeo.screenX(longitude,size.width,size.height)
                                    val yy=SeismicGeo.screenY(latitude,size.width,size.height)
                                    if(index==0) path.moveTo(xx,yy) else path.lineTo(xx,yy)
                                }
                                path.close()
                                drawPath(path,Color(0xFF184762).copy(alpha=.9f))
                                drawPath(path,iceBlue.copy(alpha=.60f),style=Stroke(1.6.dp.toPx()))
                                // Schematic coastlines, shared geographic coordinate system for all islands.
                                SeismicGeo.outlyingIslands.forEach { island ->
                                    val shore = Path()
                                    island.points.forEachIndexed { idx, (longitude,latitude) ->
                                        val x = SeismicGeo.screenX(longitude,size.width,size.height)
                                        val y = SeismicGeo.screenY(latitude,size.width,size.height)
                                        if (idx == 0) shore.moveTo(x,y) else shore.lineTo(x,y)
                                    }
                                    shore.close()
                                    drawPath(shore,Color(0xFF245873))
                                    drawPath(shore,iceBlue.copy(alpha=.76f),style=Stroke(1.5.dp.toPx()))
                                }
                                // Smaller islands shown as named schematic locations, not fabricated shapes.
                                SeismicGeo.smallerIslands.forEach { (_, coords) ->
                                    val here = Offset(
                                        SeismicGeo.screenX(coords.first,size.width,size.height),
                                        SeismicGeo.screenY(coords.second,size.width,size.height))
                                    drawCircle(Color(0xFF245873),5.dp.toPx(),here)
                                    drawCircle(iceBlue.copy(alpha=.83f),5.dp.toPx(),here,
                                        style=Stroke(1.4.dp.toPx()))
                                }
                                // Distinct P (cyan) and S (amber) travel wavefronts. Educational speeds only.
                                if(layer!="測站" && wavefront!=null && waveLat!=null && waveLon!=null) {
                                    val epicenter=Offset(SeismicGeo.screenX(waveLon,size.width,size.height),SeismicGeo.screenY(waveLat,size.width,size.height))
                                    listOf(wavefront.pSurfaceRadiusKm to Color(0xFF68E4FF),
                                        wavefront.sSurfaceRadiusKm to Color(0xFFFFB35B)).forEach { (radius,color) ->
                                        if(radius>0.0) {
                                            val rx=(radius/SeismicGeo.kmPerPixel(size.width,size.height)).toFloat()
                                            val ry=rx
                                            if(rx.isFinite() && ry.isFinite()) {
                                                drawOval(color.copy(alpha=.75f),
                                                    topLeft=Offset(epicenter.x-rx,epicenter.y-ry),
                                                    size=Size(rx*2f,ry*2f),style=Stroke(2.dp.toPx()))
                                            }
                                        }
                                    }
                                }
                                val userPlace=devicePlace
                                if(userPlace!=null && SeismicGeo.visible(userPlace.lat,userPlace.lon)) {
                                    val here=Offset(SeismicGeo.screenX(userPlace.lon,size.width,size.height),
                                        SeismicGeo.screenY(userPlace.lat,size.width,size.height))
                                    drawCircle(Color(0xAAFFFFFF),9.dp.toPx(),here,style=Stroke(2.dp.toPx()))
                                    drawCircle(Color(0xFF78EFFF),4.dp.toPx(),here)
                                }
                                pins.forEach { station ->
                                    val p=Offset(SeismicGeo.screenX(station.longitude!!,size.width,size.height),SeismicGeo.screenY(station.latitude!!,size.width,size.height))
                                    val hue=when {
                                        station.intensity==null -> iceBlue
                                        station.intensity>=5 -> Color(0xFFFF776F)
                                        station.intensity>=3 -> Color(0xFFFFB551)
                                        station.intensity>=1 -> Color(0xFF60CFFF)
                                        else -> Color(0xFF59E8AD)
                                    }
                                    drawCircle(hue.copy(alpha=.20f),6.dp.toPx(),p)
                                    drawCircle(hue,3.1.dp.toPx(),p)
                                }
                                if(layer!="測站") {
                                    val lat=if(inReplay) replayReport?.latitude else if(incidentRecent) quake?.latitude else latestReport?.latitude
                                    val lon=if(inReplay) replayReport?.longitude else if(incidentRecent) quake?.longitude else latestReport?.longitude
                                    if(SeismicGeo.visible(lat,lon)) {
                                        val p=Offset(SeismicGeo.screenX(lon!!,size.width,size.height),SeismicGeo.screenY(lat!!,size.width,size.height))
                                        val mark=if(incidentRecent)Color(0xFFFFAB62) else Color(0xFFD3B1FD)
                                        if(incidentRecent && !inReplay) drawCircle(mark.copy(alpha=.55f*(1f-pulse)),(11f+35f*pulse).dp.toPx(),p,style=Stroke(2.dp.toPx()))
                                        drawLine(mark,Offset(p.x-9.dp.toPx(),p.y),Offset(p.x+9.dp.toPx(),p.y),2.dp.toPx())
                                        drawLine(mark,Offset(p.x,p.y-9.dp.toPx()),Offset(p.x,p.y+9.dp.toPx()),2.dp.toPx())
                                        drawCircle(mark,4.dp.toPx(),p)
                                    }
                                }
                            }
                        }
                        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                            Text("TREM-Net 監測",color=light,fontSize=15.sp,fontWeight=FontWeight.Bold)
                            Text("${stations.size} 個${if (fresh) "即時" else "歷史"}有效座標測站",color=if (stationFlash.value > 0.1f) iceBlue else muted,fontSize=14.sp)
                        }
                        Column(Modifier.align(Alignment.TopEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            FilledTonalIconButton(onClick={zoom=(zoom+.25f).coerceAtMost(2.5f)},modifier=Modifier.size(37.dp)) {
                                Icon(Icons.Rounded.Add,"放大")
                            }
                            FilledTonalIconButton(onClick={zoom=(zoom-.25f).coerceAtLeast(1f)},modifier=Modifier.size(37.dp)) {
                                Icon(Icons.Rounded.Remove,"縮小")
                            }
                            FilledTonalIconButton(onClick={zoom=1f;panX=0f;panY=0f;selected=null},modifier=Modifier.size(37.dp)) {
                                Icon(Icons.Rounded.CenterFocusStrong,"重置地圖")
                            }
                        }
                        if (!fresh || (stations.isEmpty() && layer!="震央")) {
                            Text(if(!live.enabled) "點選上方「啟動」開始監測" else if(!fresh) "即時測站資料離線／過期" else "尚未取得可定位的測站資料",
                                color=light,modifier=Modifier.align(Alignment.BottomCenter).padding(12.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(Color(0xC80B223A)).padding(10.dp),fontSize=14.sp)
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("● 綠色 0　● 藍色 1–2　● 黃色 3–4　● 紅色 5+　✚ 震央",color=muted,fontSize=14.sp)
                    Text("雙指縮放 · 拖曳移動 · 點選測站",color=iceBlue,fontSize=14.sp)
                    Text("P 波（青）／S 波（琥珀）· 事件時間驅動的示意波圈",color=iceBlue,fontSize=14.sp)
                    Text("臺澎金馬 · 蘭嶼 · 綠島 · 小琉球 · 龜山島 · 釣魚臺位置示意｜等比例投影",color=muted,fontSize=14.sp)
                    Text("P／S 波圈為教育示意，不提供到達倒數；即時數值只顯示來源有效資料。",color=muted,fontSize=14.sp)
                    selected?.let { station ->
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color=light.copy(alpha=.15f))
                        Text(station.label,color=light,fontSize=15.sp,fontWeight=FontWeight.Bold)
                        Text("測站代碼 ${station.id}",color=muted,fontSize=14.sp)
                        Text("震度 ${station.intensity?.toString() ?: "未提供"} · 最大加速度 ${station.accelerationGal?.let { "${"%.2f".format(Locale.US,it)} gal" } ?: "未提供"}",color=iceBlue,fontSize=14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                MapGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History,null,tint=iceBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("歷史速報回放",color=light,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Button(enabled=reports.isNotEmpty() && replayStatus!="讀取中",
                            onClick={ if (inReplay) { replayPlaying=false;replayStatus="已結束回放";replayFrames=emptyList() }
                                      else beginReplay() }) {
                            Text(if(inReplay)"結束" else "隨機挑選")
                        }
                    }
                    Text("只使用可核對的歷史速報，不會發送真實警報；語音受手機靜音、勿擾與系統 TTS 引擎控制。",color=muted,fontSize=14.sp)
                    if(replayStatus.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(if(inReplay)"歷史回放中 · 非即時預警" else replayStatus,
                            color=if(inReplay)Color(0xFFFFD09E) else iceBlue,
                            fontSize=15.sp,fontWeight=FontWeight.Bold)
                    }
                    if(inReplay) {
                        Spacer(Modifier.height(10.dp))
                        Text("${replayReport?.location ?: "歷史地震"}  · ${replayIncident?.serial?.let { "第 $it 報" } ?: "尚未發布首報"}",
                            color=light,fontSize=15.sp)
                        GlassSlider(value=replayPosition,onValueChange={replayPosition=it},
                            valueRange=0f..replayDuration,modifier=Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("歷史事件語音（明確聲明非即時地震）",color=muted,
                                fontSize=14.sp,modifier=Modifier.weight(1f))
                            TextButton(enabled=replayIncident!=null && replayReport!=null,
                                onClick={
                                    val report=replayReport
                                    val incident=replayIncident
                                    if(report!=null && incident!=null) {
                                        SeismicSpeech.speak(ctx,SeismicSpeech.replay(report,incident))
                                    }
                                }) { Text("重播語音",color=iceBlue,fontSize=14.sp) }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("${replayPosition.toInt()} / ${replayDuration.toInt()} 秒",color=muted,fontSize=14.sp,
                                modifier=Modifier.weight(1f))
                            TextButton(onClick={ replayPlaying=!replayPlaying }) {
                                Text(if(replayPlaying)"暫停" else "繼續",color=light)
                            }
                            TextButton(onClick={replaySpeed=when(replaySpeed){1f->2f;2f->4f;else->1f}}) {
                                Text("${replaySpeed.toInt()}×",color=light)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MapGlass(modifier=Modifier.weight(1f)) {
                        Text("有效測站",color=muted,fontSize=14.sp)
                        Text(stations.size.toString(),color=light,fontWeight=FontWeight.Bold,fontSize=28.sp)
                    }
                    MapGlass(modifier=Modifier.weight(1f)) {
                        Text("最大測站震度",color=muted,fontSize=14.sp)
                        Text(maxI?.toString() ?: "—",color=light,fontWeight=FontWeight.Bold,fontSize=28.sp)
                    }
                    MapGlass(modifier=Modifier.weight(1f)) {
                        Text("最大 PGA",color=muted,fontSize=14.sp)
                        Text(maxPga?.let { "%.1f".format(Locale.US,it) } ?: "—",color=light,fontWeight=FontWeight.Bold,fontSize=25.sp)
                        Text("gal",color=muted,fontSize=14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                MapGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("最近地震報告",color=light,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        TextButton(onClick=onRefreshReports) { Text("更新",color=iceBlue) }
                    }
                    if(reports.isEmpty()) Text("尚未取得震後報告，請檢查網路連線",color=muted,fontSize=14.sp)
                    reports.take(8).forEach { report ->
                        HorizontalDivider(color=light.copy(alpha=.12f),modifier=Modifier.padding(vertical=7.dp))
                        Row(Modifier.fillMaxWidth().clickable { details=report }.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                                .background(Color(0xFF235C7C)),contentAlignment=Alignment.Center) {
                                Text("M${"%.1f".format(Locale.US,report.magnitude)}",color=light,fontWeight=FontWeight.Bold,fontSize=14.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(report.location,color=light,fontWeight=FontWeight.SemiBold,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text("${report.origin.atZone(java.time.ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))} · 最大震度 ${report.maxIntensity ?: "未提供"}",color=muted,fontSize=14.sp)
                            }
                            TextButton(onClick={beginReplay(report)}) { Text("回放",color=iceBlue,fontSize=14.sp) }
                            Icon(Icons.Rounded.ChevronRight,"查看詳情",tint=iceBlue)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MapGlass {
                    Text("速報接收紀錄",color=light,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("僅記錄這次啟動期間實際收到的第三方訊息；測試與取消事件不會觸發真實警報。",
                        color=muted,fontSize=14.sp,lineHeight=17.sp)
                    if(live.recentIncidents.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("尚未收到新的速報資料",color=muted,fontSize=14.sp)
                    }
                    live.recentIncidents.take(6).forEach { quake ->
                        HorizontalDivider(color=light.copy(alpha=.12f),modifier=Modifier.padding(vertical=8.dp))
                        Text("${quake.epicenter}  ·  ${quake.magnitude?.let { "M${"%.1f".format(Locale.US,it)}" } ?: "規模未提供"}",
                            color=light,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        Text("${formatted(quake.issuedAtMs)}  ·  ${quake.source}  ·  第 ${quake.serial} 報  ·  ${when { quake.test->"演練／測試"; quake.cancelled->"已取消"; else->"速報" }}",
                            color=muted,fontSize=14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                MapGlass {
                    Text("資料來源及使用提醒",color=light,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("TREM-Net RTS／ExpTech EEW 為第三方公開串流；震後報告來源：$reportSource。使用者無需輸入個人授權碼。",
                        color=muted,fontSize=14.sp,lineHeight=18.sp)
                    Text("離線、資料過期、Android 省電、第三方服務故障，都可能造成預警延遲或漏報；不可取代官方災防告警。地震來襲時，立即趴下、掩護、穩住。",
                        color=Color(0xFFFFD39B),fontSize=14.sp,lineHeight=18.sp)
                    Text("測站位置：${live.stationMetadataStatus}",color=muted,fontSize=14.sp)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        overlay?.let { event ->
            SeismicWarningOverlay(event,overlayReplay,devicePlace?.name,onClose={overlay=null})
        }
        details?.let { report ->
            AlertDialog(onDismissRequest={details=null},
                title={Text("地震報告 · M${"%.1f".format(Locale.US,report.magnitude)}")},
                text={Column {
                    Text(report.location)
                    Text("震源深度：${report.depthKm?.toString() ?: "未提供"} 公里")
                    Text("最大震度：${report.maxIntensity ?: "未提供"}")
                    Text("時間：${report.origin.atZone(java.time.ZoneId.of("Asia/Taipei"))}")
                    Text("此為震後報告，並非提前預警。")
                }},confirmButton={TextButton(onClick={details=null}) {Text("關閉")} })
        }
    }
}

@Composable
private fun MapGlass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    io.atmos.weather.ui.GlassSurface(modifier,content)
}

private fun formatted(ms:Long):String = SimpleDateFormat("HH:mm:ss",Locale.TAIWAN).format(Date(ms))
