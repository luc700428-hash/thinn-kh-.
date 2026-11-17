package io.atmos.weather.earthquake

import io.atmos.weather.ui.glassTouchTracker
import io.atmos.weather.ui.liquidGlassSurface
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosJellyPress
import io.atmos.weather.ui.thinnBackdrop
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer

import io.atmos.weather.ui.brandColor

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
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import io.atmos.weather.maps.rememberTaiwanVectorAtlas
import io.atmos.weather.maps.drawTaiwanAtlasCredit
import io.atmos.weather.ui.thinnSpecularEdge
import io.atmos.weather.maps.NlscTerrain
import io.atmos.weather.maps.rememberNlscMap
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

/** A distinct map-first page. The NLSC offline atlas is shared; station and epicenter markers
 * are rendered ONLY if provider supplied valid geodetic coordinates.
 */
@Composable
fun SeismicLiveMapPage(
    onClose: () -> Unit,
    onSettings: () -> Unit,
    reports: List<EarthquakeReport>,
    reportSource: String = "ExpTech 第三方彙整報告",
    reportsLoading:Boolean=false,
    reportError:String?=null,
    onRefreshReports: () -> Unit
) {
    val ctx = LocalContext.current
    val palette=MaterialTheme.colorScheme
    val dark=palette.background.red<.5f
    val light=palette.onSurface
    val muted=palette.onSurfaceVariant
    val iceBlue=palette.primary
    val atlas=rememberTaiwanVectorAtlas()
    var diagnosticsExpanded by remember {mutableStateOf(false)}
    val reduceMotion = remember(ctx) {
        ctx.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("reduce_motion", false)
    }
    val live by ExpTechSeismicService.live.collectAsState()
    var layer by remember { mutableStateOf("綜合") }
    var simulation by remember { mutableStateOf<EewIncident?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<SeismicStation?>(null) }
    var details by remember { mutableStateOf<EarthquakeReport?>(null) }
    // DPIP-style sections: live monitor, report list, replay and drills.
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("監測") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
    var baseMapSize by remember { mutableStateOf(IntSize.Zero) }
    var previousMapSize by remember {mutableStateOf(IntSize.Zero)}
    LaunchedEffect(baseMapSize){
        val old=previousMapSize;val current=baseMapSize
        if(old.width>0&&old.height>0&&current.width>0&&current.height>0&&old!=current){
            panX=(panX*current.width/old.width).coerceIn(-current.width*zoom,current.width*zoom)
            panY=(panY*current.height/old.height).coerceIn(-current.height*zoom,current.height*zoom)
        }
        previousMapSize=current
    }
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
    // Re-checked every few seconds so the reminder disappears right after the user grants access.
    val missingSetup = remember(timestamp/5000) { SeismicReadiness.missing(ctx) }
    var overlay by remember { mutableStateOf<EewIncident?>(null) }
    var overlayReplay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
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
    val interruptingAlert by SeismicAlertDelivery.active.collectAsState()
    LaunchedEffect(interruptingAlert?.key,interruptingAlert?.incident?.serial){
        if(interruptingAlert!=null){simulation=null;overlay=null;replayPlaying=false}
    }
    // App-wide SeismicForegroundAlertHost owns validated live alerts, including on other pages.
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
    val waveNow=io.atmos.weather.ui.rememberDisplayWallClock(incidentRecent && !inReplay && !reduceMotion)
    val replayBase=replayReport?.origin?.toEpochMilli() ?: 0L
    val replayWaveTime=animateFloatAsState(((replayClock-replayBase)/1000.0).toFloat(),
        tween(if(reduceMotion)0 else 120,easing=LinearEasing),label="回放波圈顯示時間")
    val latestReport = reports.firstOrNull { SeismicGeo.visible(it.latitude,it.longitude) }
    val displayQuake = if(inReplay) replayIncident else quake.takeIf { incidentRecent }
    val displayReport = if(inReplay) replayReport else latestReport
    val waveLat = if(inReplay) replayReport?.latitude else displayQuake?.latitude
    val waveLon = if(inReplay) replayReport?.longitude else displayQuake?.longitude
    val originMillis = if(inReplay) replayReport?.origin?.toEpochMilli() else displayQuake?.originAtMs


    // Observations still update immediately. Only their confirmation ring is paced and softened.
    val stationFlash = remember { androidx.compose.animation.core.Animatable(0f) }
    val latestStationFrame by rememberUpdatedState(live.lastRtsAt)
    val canAnimateStations by rememberUpdatedState(live.lastRtsAt > 0L && stations.isNotEmpty() && fresh)
    LaunchedEffect(reduceMotion) {
        stationFlash.snapTo(0f)
        if(!reduceMotion)StationUpdateMotion.play(snapshotFlow{latestStationFrame},{canAnimateStations}) {
            stationFlash.snapTo(0f)
            stationFlash.animateTo(1f,animationSpec=tween(StationUpdateMotion.DURATION_MS,easing=LinearEasing))
        }
    }
    // Stop replay sounds when the user exits the page or the replay ends.
    DisposableEffect(Unit) { onDispose { SeismicSound.stop() } }
    LaunchedEffect(replayPlaying) { if (!replayPlaying) SeismicSound.stop() }

    // Show last provider-observed values steadily, but explicitly mark them stale
    // after freshness expires. Never relabel archived readings as live values.
    val currentStations = stations.filter {
        StationFreshness.state(it,timestamp,live.lastRtsAt)==StationViewState.LIVE
    }
    val expiredStationCount=stations.count{StationFreshness.state(it,timestamp,live.lastRtsAt)==StationViewState.EXPIRED}
    val hasObservedFrame = live.lastRtsAt > 0L
    val maxI = currentStations.mapNotNull { it.intensity }.maxOrNull()
    val maxPga = currentStations.mapNotNull { it.accelerationGal }.maxOrNull()
    val readingLabel = when {
        !hasObservedFrame -> "等待觀測"
        fresh -> "最新觀測"
        else -> "上次觀測，非即時"
    }
    val pulse=if(reduceMotion).55f else {
        val animatedPulse by rememberInfiniteTransition(label="seismic marker").animateFloat(
            initialValue=0.18f,targetValue=1f,
            animationSpec=infiniteRepeatable(tween(1900,easing=LinearEasing)),label="pulse")
        animatedPulse
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("地震",fontSize=34.sp,fontWeight=FontWeight.Bold,color=light,letterSpacing=(-1).sp)
                    Text(if(inReplay) "歷史回放 · 非即時" else if(fresh) "即時觀測更新中" else "等待有效觀測",fontSize=14.sp,
                        color=if(fresh)seismicHealthyInk else muted)
                }
                IconButton(onClick=onSettings) {Icon(Icons.Rounded.Tune,"地震設定",tint=light)}
            }
            SeismicSectionPicker(section,{section=it},Modifier.padding(horizontal=16.dp).padding(bottom=10.dp))
            // Like iOS paged tabs: a horizontal swipe on the page moves to the next / previous section.
            // The map handles its own pan first, so dragging the map never flips the page.
            val sections=listOf("監測","報告","工具")
            val swipeThreshold=with(androidx.compose.ui.platform.LocalDensity.current){72.dp.toPx()}
            SeismicAdaptivePanels(Modifier.weight(1f).navigationBarsPadding()
                .pointerInput(section){
                    var total=0f
                    detectHorizontalDragGestures(
                        onDragStart={total=0f},
                        onHorizontalDrag={change,amount->total+=amount;if(kotlin.math.abs(total)>12f)change.consume()},
                        onDragEnd={
                            val i=sections.indexOf(section)
                            if(total<-swipeThreshold&&i<sections.lastIndex)section=sections[i+1]
                            else if(total>swipeThreshold&&i>0)section=sections[i-1]
                        })
                },showLeading=section=="監測"||inReplay,
                overview={Column{if(section=="監測"&&missingSetup.isNotEmpty())Box(Modifier.padding(bottom=10.dp)){SeismicSetupReminder(missingSetup,onSettings)}
                    if(section=="監測")SeismicObservationSummary(if(hasObservedFrame)currentStations.size.toString() else "—",
                    maxI?.let {"%.1f".format(Locale.US,it)}?:"—",maxPga?.let {"%.1f".format(Locale.US,it)}?:"—",readingLabel)}},
                leading={mapHeight->
                MapGlass(Modifier.testTag("seismic-monitor-map-card")) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("臺灣與離島",fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=light,modifier=Modifier.weight(1f))
                        Text("TREM-Net",fontSize=14.sp,color=muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    SeismicLayerSelector(layer,{layer=it;selected=null})
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(19.dp))
                        .background(Brush.verticalGradient(if(dark)listOf(brandColor(0xFF0B201A),brandColor(0xFF102E26)) else listOf(brandColor(0xFFD8F1EA),brandColor(0xFFC5EADF))))) {
                        val pins=if(layer=="震央") emptyList() else stations.take(900)
                        val latestPins by rememberUpdatedState(pins)
                        Canvas(Modifier.fillMaxSize().onSizeChanged { baseMapSize=it }
                            .testTag("seismic-monitor-map").semantics{contentDescription="臺灣測站與震央地圖，可雙指縮放與點選測站";stateDescription="縮放 %.1f×".format(Locale.US,zoom)}
                            .pointerInput(Unit) {
                            detectTapGestures { clicked ->
                                val unscaledX = (clicked.x - panX - size.width*0.5f) / zoom + size.width*0.5f
                                val unscaledY = (clicked.y - panY - size.height*0.5f) / zoom + size.height*0.5f
                                selected = latestPins.minByOrNull { point ->
                                    val dx = SeismicGeo.screenX(point.longitude!!,size.width.toFloat(),size.height.toFloat()) - unscaledX
                                    val dy = SeismicGeo.screenY(point.latitude!!,size.width.toFloat(),size.height.toFloat()) - unscaledY
                                    hypot(dx,dy)
                                }?.takeIf { point ->
                                    hypot(SeismicGeo.screenX(point.longitude!!,size.width.toFloat(),size.height.toFloat())-unscaledX,
                                        SeismicGeo.screenY(point.latitude!!,size.width.toFloat(),size.height.toFloat())-unscaledY) < 32.dp.toPx()/zoom
                                }
                            }
                        }.pointerInput(Unit) {
                            detectTransformGestures {centroid,pan,zoomChange,_ ->
                                if(!zoomChange.isFinite()||zoomChange<=0||!centroid.x.isFinite()||!centroid.y.isFinite()||!pan.x.isFinite()||!pan.y.isFinite())return@detectTransformGestures
                                val next=(zoom*zoomChange).coerceIn(1f,32f)
                                val anchor=centroid-Offset(size.width/2f,size.height/2f)
                                val ratio=next/zoom
                                panX=(anchor.x+(panX-anchor.x)*ratio+pan.x).coerceIn(-size.width*next,size.width*next)
                                panY=(anchor.y+(panY-anchor.y)*ratio+pan.y).coerceIn(-size.height*next,size.height*next)
                                zoom=next
                            }
                        }) {
                            withTransform({ translate(panX,panY)
                                scale(zoom,zoom,pivot=Offset(size.width/2f,size.height/2f)) }) {
                                if(atlas!=null) atlas.draw(this,dark=dark) {lon,lat ->
                                    Offset(SeismicGeo.screenX(lon,size.width,size.height),SeismicGeo.screenY(lat,size.width,size.height))
                                }
                                val wavefront = if(SeismicGeo.visible(waveLat,waveLon) && originMillis!=null)
                                    SeismicWavePhysics.wavefront(originMillis,if(inReplay)replayBase+(replayWaveTime.value*1000).toLong() else waveNow.value,
                                        if(inReplay) (replayReport?.depthKm ?: 0.0) else (displayQuake?.depthKm ?: 0.0)) else null
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
                                    drawCircle(if(dark)Color(0xAAFFFFFF) else brandColor(0xFF297661),9.dp.toPx(),here,style=Stroke(2.dp.toPx()))
                                    drawCircle(if(dark)brandColor(0xFFBBE7DB) else brandColor(0xFF256956),4.dp.toPx(),here)
                                }
                                pins.forEach { station ->
                                    val p=Offset(SeismicGeo.screenX(station.longitude!!,size.width,size.height),SeismicGeo.screenY(station.latitude!!,size.width,size.height))
                                    val offline = station.availability == StationAvailability.OFFLINE ||
                                        station.availability == StationAvailability.MAINTENANCE
                                    val stationState=StationFreshness.state(station,timestamp,live.lastRtsAt)
                                    val hue=when {
                                        offline -> Color(0xFFFF7682)
                                        stationState!=StationViewState.LIVE -> Color(0xFF9BACBD)
                                        station.intensity==null -> iceBlue
                                        station.intensity>=5 -> Color(0xFFFF776F)
                                        station.intensity>=3 -> Color(0xFFFFB551)
                                        station.intensity>=1 -> Color(0xFF60CFFF)
                                        else -> Color(0xFF59E8AD)
                                    }
                                    drawCircle(hue.copy(alpha=.20f),6.dp.toPx(),p)
                                    drawCircle(hue,3.1.dp.toPx(),p)
                                    if (offline) {
                                        // Only provider-explicitly offline/maintenance stations glow red.
                                        drawCircle(Color(0xFFFF7682).copy(alpha=if(!fresh || reduceMotion) .55f else .25f+.6f*pulse),
                                            (5f+3f*pulse).dp.toPx(),p,style=Stroke(1.8.dp.toPx()))
                                    } else if(fresh && stationFlash.value>.001f && stationState==StationViewState.LIVE) {
                                        drawCircle(brandColor(0xFF85D4BE).copy(alpha=StationUpdateMotion.opacity(stationFlash.value)),
                                            StationUpdateMotion.radiusDp(stationFlash.value).dp.toPx(),p,
                                            style=Stroke(1.4.dp.toPx()))
                                    }
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
                        androidx.compose.foundation.text.BasicText("底圖 © 國土測繪中心",Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            style=androidx.compose.ui.text.TextStyle(color=if(dark)brandColor(0xFFB0D0C7) else brandColor(0xFF2B5045),fontSize=14.sp))
                        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                            Text("TREM-Net 測站",color=if(dark)Color.White else brandColor(0xFF163F34),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                            Text("${stations.size} 站 · ${currentStations.size} 站有效觀測",
                                color=if(dark)brandColor(0xFFBFE9DD) else brandColor(0xFF295E4F),fontSize=14.sp,
                                maxLines=1)
                        }
                        Column(Modifier.align(Alignment.TopEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            FilledTonalIconButton(onClick={zoom=(zoom*1.5f).coerceAtMost(32f)},modifier=Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Add,"放大")
                            }
                            FilledTonalIconButton(onClick={zoom=(zoom/1.5f).coerceAtLeast(1f)},modifier=Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Remove,"縮小")
                            }
                            FilledTonalIconButton(onClick={zoom=1f;panX=0f;panY=0f;selected=null},modifier=Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.CenterFocusStrong,"重置地圖")
                            }
                        }
                        if (!fresh || expiredStationCount>0 || (stations.isEmpty() && layer!="震央")) {
                            Text(when {
                                !live.enabled -> "可在監測面板開啟即時監測"
                                live.lastRtsAt>timestamp+2_000L -> "測站時間與手機不同步，等待確認"
                                rtsHealth==SeismicFeedState.STALE -> "超過 20 秒未更新 · 測站觀測暫時失效"
                                live.lastRtsAt<=0L -> "正在連接測站，等待第一筆觀測…"
                                expiredStationCount>0 -> "$expiredStationCount 站超過 20 秒未更新，已標示暫時失效"
                                else -> "尚未取得可定位的測站資料"
                            },
                                color=Color.White,modifier=Modifier.align(Alignment.BottomCenter).padding(12.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(brandColor(0xC80D251E)).padding(10.dp),fontSize=14.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    SeismicStationLegend()
                    Text("雙指縮放、點選測站 · 波圈為時間驅動示意",fontSize=14.sp,color=muted)
                    (stations.firstOrNull{it.id==selected?.id}?:selected)?.let {station ->
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color=palette.outlineVariant)
                        Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(station.label,color=light,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                                Text("測站 ${station.id} · ${StationFreshness.label(StationFreshness.state(station,timestamp,live.lastRtsAt))}",color=muted,fontSize=14.sp)
                            }
                            Text(if(StationFreshness.state(station,timestamp,live.lastRtsAt)==StationViewState.LIVE)station.intensity?.toString()?:"—" else "—",color=iceBlue,fontSize=30.sp,fontWeight=FontWeight.SemiBold)
                        }
                        Text("PGA ${station.accelerationGal?.let {"%.2f".format(Locale.US,it)} ?: "—"} gal · ${StationFreshness.label(StationFreshness.state(station,timestamp,live.lastRtsAt))}",color=muted,fontSize=14.sp)
                    }
                }
            },trailing={_->
                if(section=="監測") {
                MapGlass {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("即時監測",fontSize=19.sp,color=light,fontWeight=FontWeight.SemiBold)
                            Text(if(live.enabled) "背景接收第三方速報與測站" else "開啟後接收速報與測站",fontSize=14.sp,color=muted)
                        }
                        io.atmos.weather.ui.GlassSwitch(checked=live.enabled,onCheckedChange={enabled ->
                            if(enabled)ExpTechSeismicService.start(ctx) else ExpTechSeismicService.stop(ctx)
                        })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SeismicStatusMiniCard("速報線路",when(eewHealth) {
                            SeismicFeedState.LIVE->"已連線";SeismicFeedState.STOPPED->"未啟動";else->"重新連線中"
                        },eewHealth==SeismicFeedState.LIVE,Modifier.weight(1f))
                        SeismicStatusMiniCard("測站觀測",when(rtsHealth) {
                            SeismicFeedState.LIVE->"更新中";SeismicFeedState.STALE->"資料已過期";SeismicFeedState.STOPPED->"未啟動";else->"等待有效資料"
                        },fresh,Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("${devicePlace?.name ?: "尚未定位"} · 最後觀測 ${if(hasObservedFrame)formatted(live.lastRtsAt) else "—"}",fontSize=14.sp,color=muted)
                }
                if(incidentRecent || inReplay) MapGlass {
                    Text(if(inReplay)"歷史資料回放中" else "收到地震預警 · 第三方來源",color=seismicCautionInk,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Text(SeismicDisplayFormat.location(quake?.epicenter ?: replayReport?.location ?: "震央位置待來源確認"),fontSize=21.sp,color=light,fontWeight=FontWeight.SemiBold)
                    Text(if(inReplay)"過去事件的教育回放，並非目前警報。" else "立即趴下、掩護、穩住。",fontSize=14.sp,color=muted)
                }
                SeismicCountyMaxPanel(currentStations)
                SeismicLatestReportCard(reports.firstOrNull(),reportsLoading,{details=it},{section="報告"})
                }
                if(section=="報告") Box(Modifier.iosAppear()){SeismicReportsPanel(reports,reportsLoading,reportError,reportSource,onRefreshReports){details=it}}
                if(section=="工具") {
                MapGlass(Modifier.iosAppear(0)) {
                    if(inReplay||replayStatus=="回放完成（非真實預警）"&&replayReport!=null) SeismicReplayPlayer(
                        report=replayReport,serial=replayIncident?.serial,position=replayPosition,duration=replayDuration,
                        playing=replayPlaying,finished=!replayPlaying&&replayPosition>=replayDuration,speed=replaySpeed,
                        onSeek={replayPosition=it},
                        onToggle={
                            if(replayPosition>=replayDuration){replayPosition=0f;lastReplayShown="";replayStatus="回放中";replayPlaying=true}
                            else {replayStatus="回放中";replayPlaying=!replayPlaying}
                        },
                        onSpeed={replaySpeed=it},
                        onVoice=if(replayIncident!=null&&replayReport!=null) ({
                            val report=replayReport
                            val incident=replayIncident
                            if(report!=null && incident!=null) {
                                SeismicSpeech.speak(ctx,SeismicSpeech.replay(report,incident)) // 重播語音
                            }
                        }) else null,
                        onStop={replayPlaying=false;replayStatus="";replayFrames=emptyList();replayReport=null;replayIncident=null})
                    else SeismicReplayPicker(reports,replayStatus.takeUnless{it=="讀取中"||it=="回放中"}?:"",replayStatus=="讀取中",
                        onPick={beginReplay(it)},onRandom={beginReplay()})
                }
                MapGlass(Modifier.iosAppear(1)) {
                    SeismicDrillPanel(
                        onGeneral={simulation=SeismicSimulation.scenario(System.currentTimeMillis())},
                        onChiChi={simulation=SeismicSimulation.scenario(System.currentTimeMillis(),strong=true)})
                }
                MapGlass {
                    Row(Modifier.fillMaxWidth().clickable {diagnosticsExpanded=!diagnosticsExpanded},verticalAlignment=Alignment.CenterVertically) {
                        Text("資料來源與接收紀錄",color=light,fontSize=16.sp,modifier=Modifier.weight(1f))
                        Icon(if(diagnosticsExpanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null,tint=muted)
                    }
                    androidx.compose.animation.AnimatedVisibility(diagnosticsExpanded) {
                        Column(Modifier.padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("TREM-Net RTS / ExpTech EEW · 第三方串流",fontSize=14.sp,color=muted)
                            Text("震後報告：$reportSource",fontSize=14.sp,color=muted)
                            Text("測站位置：${live.stationMetadataStatus}",fontSize=14.sp,color=muted)
                            if(live.recentIncidents.isEmpty())Text("尚未收到新的速報",fontSize=14.sp,color=muted)
                            live.recentIncidents.take(6).forEach {event ->
                                Text("${formatted(event.issuedAtMs)} · ${event.epicenter} · 第 ${event.serial} 報 · ${when{event.test->"演練／測試";event.cancelled->"已取消";else->"速報"}}",fontSize=14.sp,color=muted)
                            }
                        }
                    }
                }
                }
                Text("速報可能受網路、資料時效與系統省電影響；請以官方災防告警為準。",color=muted,fontSize=14.sp,lineHeight=18.sp,modifier=Modifier.padding(horizontal=8.dp))
            })
        }
        overlay?.let { event ->
            SeismicWarningOverlay(event,overlayReplay,devicePlace?.name,onClose={overlay=null})
        }
        simulation?.let { event ->
            SeismicWarningOverlay(event,replay=true,userCounty=devicePlace?.name,
                onClose={simulation=null},simulation=true)
        }
        details?.let { report ->
            SeismicReportDetailPage(report,devicePlace,onClose={details=null},
                onReplay={target->details=null;section="工具";beginReplay(target)})
        }
    }
}

@Composable
private fun SeismicStatusMiniCard(title:String,value:String,active:Boolean,modifier:Modifier=Modifier) {
    Column(modifier.heightIn(min=72.dp).clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f))
        .border(1.dp,if(active) Color(0xFF8BEFBE).copy(alpha=.36f) else Color.White.copy(alpha=.14f),RoundedCornerShape(22.dp))
        .padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,maxLines=1)
        Text(value,color=if(active) seismicHealthyInk else seismicCautionInk,
            fontSize=14.sp,lineHeight=19.sp,fontWeight=FontWeight.SemiBold,
            maxLines=3,overflow=TextOverflow.Ellipsis)
    }
}

/** iOS 27 card: themed glass with the sun- or moon-lit specular rim shared by every glass control. */
@Composable
private fun MapGlass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette=MaterialTheme.colorScheme
    val opaque=io.atmos.weather.ui.rememberAppleReduceTransparency()
    val light=io.atmos.weather.ui.GlassLight.current(follow=!opaque)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(palette.surface)
        .liquidGlassSurface(26.dp,{0f},null,palette.background.red<.5f,light).padding(16.dp),content=content)
}

/** Shown on the monitor until every permission a warning needs to pop up has been granted. */
@Composable
private fun SeismicSetupReminder(missing:List<String>,onOpen:()->Unit) {
    val palette=MaterialTheme.colorScheme
    MapGlass(Modifier.testTag("seismic-setup-reminder").iosAppear().iosJellyPress().clickable(onClick=onOpen)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning,null,tint=seismicCautionInk)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("地震來時可能只會收到通知",color=palette.onSurface,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text("還差：${missing.joinToString("、")}",color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp)
            }
            Text("前往設定",color=palette.primary,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}

private fun formatted(ms:Long):String = SimpleDateFormat("HH:mm:ss",Locale.TAIWAN).format(Date(ms))

/** The map control samples its own themed surface, never a weather page behind the map. */
@Composable internal fun SeismicLayerSelector(selected:String,onSelect:(String)->Unit,modifier:Modifier=Modifier) {
    val p=MaterialTheme.colorScheme
    val scene=io.atmos.weather.ui.rememberThinnBackdrop()
    Box(modifier.widthIn(max=io.atmos.weather.ui.TabletDockGeometry.contentLength(3,androidx.compose.ui.platform.LocalDensity.current.fontScale).dp)
        .fillMaxWidth().clip(RoundedCornerShape(999.dp)).testTag("seismic-layer-selector")) {
        io.atmos.weather.ui.ThinnBackdropCapture(scene,Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().background(p.surface))
        }
        CompositionLocalProvider(io.atmos.weather.ui.LocalThinnBackdrop provides scene) {
            io.atmos.weather.next.NextReorderableToolDock(
                actions=listOf(
                    io.atmos.weather.next.NextToolAction("綜合","綜合",Icons.Rounded.Layers),
                    io.atmos.weather.next.NextToolAction("測站","測站",Icons.Rounded.Sensors),
                    io.atmos.weather.next.NextToolAction("震央","震央",Icons.Rounded.MyLocation)),
                selectedId=selected,onSelected=onSelect,onOrderChanged={},modifier=Modifier.fillMaxWidth(),maxVisible=3,
                darkBackdrop=p.background.red<.5f,
                reduceMotion=io.atmos.weather.ui.rememberAppleReduceMotion(),
                reduceTransparency=io.atmos.weather.ui.rememberAppleReduceTransparency())
        }
    }
}


/**
 * iOS 27 segmented control in liquid glass: the selection is a clear glass capsule that glides
 * between sections with the glass-morph spring, stretches as it travels, swells and lights up
 * under the finger, and carries the same sun-lit specular rim as the Dock.
 */
@Composable internal fun SeismicSectionPicker(selected:String,onSelect:(String)->Unit,modifier:Modifier=Modifier) {
    val palette=MaterialTheme.colorScheme
    val names=listOf("監測","報告","工具")
    val labels=mapOf("監測" to "即時監測","報告" to "地震報告","工具" to "回放與演練")
    val index=names.indexOf(selected).coerceAtLeast(0)
    val reduce=io.atmos.weather.ui.rememberAppleReduceMotion()
    val opaque=io.atmos.weather.ui.rememberAppleReduceTransparency()
    val dark=palette.background.red<.5f
    var pressed by remember{mutableStateOf(false)}
    val position by animateFloatAsState(index.toFloat(),if(reduce)androidx.compose.animation.core.snap() else io.atmos.weather.ui.AppleMotion.glassMorph(),label="segment-lens")
    val tail by animateFloatAsState(position,if(reduce)androidx.compose.animation.core.snap() else
        androidx.compose.animation.core.spring(io.atmos.weather.ui.FluidGlassStyle.TAIL_DAMPING,io.atmos.weather.ui.FluidGlassStyle.TAIL_STIFFNESS),label="segment-tail")
    val lift by animateFloatAsState(if(pressed&&!reduce)1f else 0f,if(reduce)androidx.compose.animation.core.snap() else
        androidx.compose.animation.core.spring(io.atmos.weather.ui.FluidGlassStyle.CONTACT_DAMPING,io.atmos.weather.ui.FluidGlassStyle.CONTACT_STIFFNESS),label="segment-lift")
    val touch=remember{io.atmos.weather.ui.GlassTouch()}
    val light=io.atmos.weather.ui.GlassLight.current(follow=!opaque)
    val capsule=RoundedCornerShape(999.dp)
    var pickerWidth by remember{mutableIntStateOf(1)}
    val latestSelect by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier.fillMaxWidth().height(46.dp).testTag("seismic-section-picker")
        .onSizeChanged{pickerWidth=it.width.coerceAtLeast(1)}
        // Drag across the control to scrub between sections, as with iOS segmented controls.
        .pointerInput(Unit){
            fun at(x:Float)=names[(x/(pickerWidth.toFloat()/names.size)).toInt().coerceIn(0,names.lastIndex)]
            detectHorizontalDragGestures(
                onDragStart={o->latestSelect(at(o.x))},
                onHorizontalDrag={change,_->change.consume();latestSelect(at(change.position.x))})
        }
        .shadow(10.dp,capsule,ambientColor=Color.Black.copy(alpha=.08f),spotColor=Color.Black.copy(alpha=.08f))
        .clip(capsule).thinnBackdrop(opaque,999.dp)
        .background(if(opaque)palette.surface else palette.surface.copy(alpha=if(dark).42f else .58f))
        .border(.75.dp,Color.White.copy(alpha=if(dark).18f else .55f),capsule)
        .pointerInput(Unit){awaitEachGesture{
            awaitFirstDown(requireUnconsumed=false,pass=androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            pressed=true
            try{do{val e=awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)}while(e.changes.any{it.pressed})}finally{pressed=false}
        }}) {
        val segment=maxWidth/names.size
        // The glass lens, drawn under the labels.
        Box(Modifier.offset{IntOffset((position*segment.toPx()).roundToInt(),0)}.width(segment).fillMaxHeight().padding(3.dp)
            .graphicsLayer {
                val stretch=io.atmos.weather.ui.LiquidGlassKit.stretch(position-tail)
                scaleX=(1f+.07f*lift)*(1f+stretch);scaleY=(1f+.12f*lift)*(1f-stretch*.4f)
            }
            .glassTouchTracker(touch)
            .clip(capsule).thinnBackdrop(opaque,999.dp,frostScale=.35f)
            .background(if(opaque)palette.primaryContainer else Color.White.copy(alpha=if(dark).16f else .72f))
            .liquidGlassSurface(999.dp,{lift},touch,dark,light))
        Row(Modifier.fillMaxSize()) {
            names.forEach{name->
                val on=name==selected
                Box(Modifier.weight(1f).fillMaxHeight().clip(capsule)
                    .clickable(interactionSource=remember{androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null,
                        role=androidx.compose.ui.semantics.Role.Tab){onSelect(name)}
                    .semantics{this.selected=on;contentDescription="地震$name"},contentAlignment=Alignment.Center) {
                    Text(labels.getValue(name),color=if(on)palette.onSurface else palette.onSurfaceVariant,fontSize=15.sp,
                        fontWeight=if(on)FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}
