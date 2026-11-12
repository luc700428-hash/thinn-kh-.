package io.atmos.weather.typhoon

import io.atmos.weather.ui.brandColor

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import io.atmos.weather.ui.AppText
import io.atmos.weather.maps.rememberRegionalVectorAtlas
import io.atmos.weather.maps.drawRegionalPlaceLabels
import io.atmos.weather.maps.rememberTaiwanVectorAtlas
import io.atmos.weather.maps.drawTaiwanAtlasCredit
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import io.atmos.weather.maps.rememberNlscMap
import io.atmos.weather.maps.NlscTerrain
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.conflate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val ink get() =brandColor(0xFF081713)
private val cyan get() =brandColor(0xFF9EDDCB)
private val cream get() =brandColor(0xFFEAF8F4)
private val muted get() =brandColor(0xFFB3DBD0)
private val taipei=ZoneId.of("Asia/Taipei")
private val timeFormatter=DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(taipei)

@Composable private fun StormText(text:String, size:Int, color:Color=cream, weight:FontWeight=FontWeight.Normal,
 modifier:Modifier=Modifier,align:TextAlign=TextAlign.Start) {
    AppText(text, modifier, color=color,fontSize=size.coerceAtLeast(14).sp,fontWeight=weight,
        lineHeight=(size.coerceAtLeast(14)+5).sp,textAlign=align)
}
@Composable private fun GlassPlate(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    io.atmos.weather.ui.GlassSurface(modifier=modifier,content=content)
}
@Composable private fun StormValue(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        StormText(label,14,MaterialTheme.colorScheme.onSurfaceVariant)
        StormText(value,21,MaterialTheme.colorScheme.onSurface,FontWeight.Medium)
    }
}

/** No Material UI components. Forecast interpolation is VISUAL ONLY, never a new CWA fix. */
@Composable
fun StormTimelineOverlay(storm:StormTimeline,onDismiss:()->Unit) {
    if(storm.fixes.size < 2) return
    val context=LocalContext.current
    val palette=MaterialTheme.colorScheme
    val dark=palette.background.red<.5f
    val cyan=if(dark)brandColor(0xFF9EDDCB) else brandColor(0xFF246754)
    val cream=palette.onSurface
    val muted=palette.onSurfaceVariant
    val reduceMotion=io.atmos.weather.ui.rememberAppleReduceMotion()
    var progress by remember(storm.signature) { mutableFloatStateOf(0f) }
    val maxStep=(storm.fixes.size-1).toFloat()
    val smoothProgress = animateFloatAsState(progress,tween(durationMillis=if(reduceMotion) 0 else 190),label="storm timeline transition")
    val chosen=storm.fixes[progress.roundToInt().coerceIn(0,storm.fixes.lastIndex)]
    var terrainCanvasSize by remember { mutableStateOf(IntSize.Zero) }
    val terrainPoints=remember(storm){storm.fixes.map { it.latitude to it.longitude }+
        TyphoonGeoBackdrop.keyAnchors.map { it.second to it.first }}
    val terrainPad=with(LocalDensity.current) { 28.dp.toPx() }
    val atlas=rememberTaiwanVectorAtlas()
    val regional=rememberRegionalVectorAtlas()
    var zoom by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(Offset.Zero)}
    var fitTrack by remember {mutableStateOf(true)}
    val base=remember(fitTrack,terrainPoints,terrainCanvasSize,terrainPad){if(fitTrack) TyphoonViewport.forPoints(terrainPoints,terrainCanvasSize.width.toFloat().coerceAtLeast(1f),
        terrainCanvasSize.height.toFloat().coerceAtLeast(1f),terrainPad)
        else TyphoonViewport.forTaiwan(terrainCanvasSize.width.toFloat().coerceAtLeast(1f),
            terrainCanvasSize.height.toFloat().coerceAtLeast(1f),terrainPad)}
    val camera=remember(base,zoom,pan){
        val scale=base.pxPerKm*zoom
        base.copy(originXKm=base.originXKm-pan.x/scale,originYKm=base.originYKm+pan.y/scale,pxPerKm=scale)
    }
    val latestCamera by rememberUpdatedState(camera)
    var atlasRevision by remember{mutableIntStateOf(0)}
    LaunchedEffect(regional){
        val loaded=regional?:return@LaunchedEffect
        snapshotFlow{latestCamera}.conflate().collect{view->loaded.prepare(view);atlasRevision++}
    }
    val points=remember(storm,camera){storm.fixes.map{fix->camera.project(fix.longitude,fix.latitude).let{Offset(it.first,it.second)}}}
    val timeLabels=remember(storm){storm.fixes.map{timeFormatter.format(it.time)}}
    androidx.activity.compose.BackHandler(onBack=onDismiss)
    Box(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxSize()
            .padding(horizontal=8.dp).clip(RoundedCornerShape(34.dp))
            .background(palette.surface)
            .border(1.dp,palette.outlineVariant,RoundedCornerShape(34.dp))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start=16.dp,end=16.dp,top=16.dp,bottom=128.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StormText("中央氣象署 · 颱風路徑",14,cyan)
                        StormText(storm.name,31,cream,FontWeight.SemiBold)
                        StormText("颱風編號  ${storm.typhoonNumber.ifBlank { "官方未提供" }}",14,muted)
                    }
                    StormCloseButton(onDismiss)
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    StormText("未來動向 · 官方預報時間軸",17,cream,FontWeight.Medium,Modifier.weight(1f))
                    io.atmos.weather.ui.GlassTextButton(onClick={fitTrack=!fitTrack;zoom=1f;pan=Offset.Zero}) {
                        StormText(if(fitTrack) "臺灣" else "完整路徑",13,cyan)
                    }
                }
                Box(Modifier.fillMaxWidth().height(315.dp).clip(RoundedCornerShape(26.dp))
                    .background(if(dark)brandColor(0xFF0B201A) else brandColor(0xFFDDF3ED))) {
                    // The cursor reads animation state only in its own draw phase.
                    Box(Modifier.fillMaxSize().onSizeChanged{terrainCanvasSize=it}.testTag("storm-timeline-map").pointerInput(Unit){
                        detectTransformGestures{centroid,move,magnification,_->
                            if(!magnification.isFinite()||magnification<=0||!move.x.isFinite()||!move.y.isFinite())return@detectTransformGestures
                            val next=(zoom*magnification).coerceIn(1f,32f)
                            val anchor=centroid-Offset(size.width/2f,size.height/2f)
                            pan=anchor+(pan-anchor)*(next/zoom)+move;zoom=next
                        }
                    }){
                        Canvas(Modifier.matchParentSize().graphicsLayer()){
                            atlasRevision
                            regional?.draw(this,camera,dark,preparedOnly=true)
                            atlas?.draw(this){lon,lat->camera.project(lon,lat).let{Offset(it.first,it.second)}}
                            drawRegionalPlaceLabels(camera,dark)
                            for(i in 1..5)drawLine(cyan.copy(alpha=.09f),Offset(0f,size.height*i/6f),Offset(size.width,size.height*i/6f),1.dp.toPx())
                        }
                        Canvas(Modifier.matchParentSize().graphicsLayer()){
                            for(i in 0 until points.lastIndex)drawDirectedTrack(points[i],points[i+1],cyan)
                            val occupied=ArrayList<Offset>()
                            points.forEachIndexed{index,point->
                                if(occupied.none{(it-point).getDistance()<76.dp.toPx()}){drawTrackTime(timeLabels[index],point);occupied+=point}
                                drawCircle(cyan.copy(alpha=.23f),7.dp.toPx(),point);drawCircle(cyan,3.dp.toPx(),point)
                            }
                        }
                        Canvas(Modifier.matchParentSize().graphicsLayer()){
                            val cursor=smoothProgress.value
                            val idx=floor(cursor).toInt().coerceIn(0,points.lastIndex);val next=(idx+1).coerceAtMost(points.lastIndex)
                            val here=androidx.compose.ui.geometry.lerp(points[idx],points[next],(cursor-idx).coerceIn(0f,1f))
                            storm.fixes[idx].radius7Km?.let{radius->drawCircle(cyan.copy(alpha=.21f),radius.toFloat()*camera.pxPerKm.toFloat(),here,style=Stroke(1.5.dp.toPx()))}
                            drawCircle(Color(0xFFFFD39F),7.dp.toPx(),here);drawCircle(cream,3.dp.toPx(),here)
                        }
                    }
                    StormText("Natural Earth · 國土測繪中心",14,muted,
                        modifier=Modifier.align(Alignment.BottomStart).background(palette.surface.copy(alpha=.88f)).padding(8.dp))
                }
                GlassPlate(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically) {
                        StormText("${timeFormatter.format(chosen.time)}（臺灣時間）",17,cream,FontWeight.Medium)
                        StormText("${progress.roundToInt()+1} / ${storm.fixes.size}",14,cyan)
                    }
                    Spacer(Modifier.height(12.dp))
                    io.atmos.weather.ui.GlassSlider(value=progress,onValueChange={progress=it},
                        valueRange=0f..maxStep,steps=(storm.fixes.size-2).coerceAtLeast(0),
                        onValueChangeFinished={progress=progress.roundToInt().toFloat()},modifier=Modifier.fillMaxWidth())
                    StormText("左右拖動：查看官方各預報時刻；時間之間僅為動畫，非新增預報",12,muted)
                }
                GlassPlate(Modifier.fillMaxWidth()) {
                    StormText("中心強度與結構",18,cream,FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth()) {
                        StormValue("中心氣壓", chosen.pressureHpa?.let { "${it.roundToInt()} 百帕" } ?: "尚未提供",Modifier.weight(1f))
                        StormValue("最大風速", chosen.windMs?.let { "${it.roundToInt()} 公尺/秒" } ?: "尚未提供",Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        StormValue("最大瞬間陣風",chosen.gustMs?.let {"${it.roundToInt()} 公尺/秒"} ?: "尚未提供",Modifier.weight(1f))
                        StormValue("七級風平均暴風半徑",chosen.radius7Km?.let {"${it.roundToInt()} 公里"} ?: "尚未提供",Modifier.weight(1f))
                    }
                    StormValue("行進方向",TyphoonDirection.zh(chosen.officialDirection) ?: "官方資料未提供此時刻移向")
                }
                StormText("每 20 分鐘檢查資料；官方預報依發布時間更新。預報路徑有不確定性，警報以中央氣象署為準。",13,muted)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Independent glass button; does not use any Material Design widgets. */
@Composable private fun StormCloseButton(onClick:()->Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp,MaterialTheme.colorScheme.outlineVariant,CircleShape)
        .then(Modifier.clickable { onClick() }),contentAlignment=Alignment.Center) {
        StormText("×",27,MaterialTheme.colorScheme.onSurface)
    }
}
