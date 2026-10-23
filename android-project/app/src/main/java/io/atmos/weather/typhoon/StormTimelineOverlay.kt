package io.atmos.weather.typhoon

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val ink=Color(0xFF061527)
private val cyan=Color(0xFF76DEFC)
private val cream=Color(0xFFE8F7FF)
private val muted=Color(0xFFC1D6E2)
private val taipei=ZoneId.of("Asia/Taipei")
private val timeFormatter=DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(taipei)

@Composable private fun StormText(text:String, size:Int, color:Color=cream, weight:FontWeight=FontWeight.Normal,
 modifier:Modifier=Modifier,align:TextAlign=TextAlign.Start) {
    BasicText(text, modifier, style=TextStyle(color=color,fontSize=size.sp,fontWeight=weight,
        lineHeight=(size+5).sp,textAlign=align))
}
@Composable private fun GlassPlate(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF315675).copy(alpha=.76f),Color(0xFF122E4A).copy(alpha=.88f))))
        .border(1.dp,Color.White.copy(alpha=.28f),RoundedCornerShape(24.dp))
        .padding(16.dp),content=content)
}
@Composable private fun StormValue(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        StormText(label,13,muted)
        StormText(value,21,cream,FontWeight.Medium)
    }
}

/** No Material UI components. Forecast interpolation is VISUAL ONLY, never a new CWA fix. */
@Composable
fun StormTimelineOverlay(storm:StormTimeline,onDismiss:()->Unit) {
    if(storm.fixes.size < 2) return
    val context=LocalContext.current
    val reduceMotion=remember { context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false) }
    var progress by remember(storm.signature) { mutableFloatStateOf(0f) }
    val maxStep=(storm.fixes.size-1).toFloat()
    val smoothProgress by animateFloatAsState(progress,tween(durationMillis=if(reduceMotion) 0 else 190),label="storm timeline transition")
    val chosen=storm.fixes[progress.roundToInt().coerceIn(0,storm.fixes.lastIndex)]
    val light=rememberInfiniteTransition(label="optic glow")
    val breathing by light.animateFloat(.55f,1f,
        infiniteRepeatable(tween(2500),RepeatMode.Reverse),label="storm glow")
    var terrainCanvasSize by remember { mutableStateOf(IntSize.Zero) }
    val terrainPoints=storm.fixes.map { it.latitude to it.longitude }+
        TyphoonGeoBackdrop.keyAnchors.map { it.second to it.first }
    val terrainPad=with(LocalDensity.current) { 28.dp.toPx() }
    val terrainBounds=if(terrainCanvasSize.width>60 && terrainCanvasSize.height>60)
        TyphoonViewport.forPoints(terrainPoints,terrainCanvasSize.width.toFloat(),
            terrainCanvasSize.height.toFloat(),terrainPad).rasterBounds() else null
    val terrain=rememberNlscMap(terrainBounds,terrainCanvasSize.width,terrainCanvasSize.height)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.95f)
            .padding(horizontal=8.dp).clip(RoundedCornerShape(34.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF193F65),ink,Color(0xFF061321))))
            .border(1.dp,Color.White.copy(alpha=.23f),RoundedCornerShape(34.dp))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StormText("中央氣象署 · 颱風路徑",14,cyan)
                        StormText(storm.name,31,cream,FontWeight.SemiBold)
                        StormText("颱風編號  ${storm.typhoonNumber.ifBlank { "官方未提供" }}",14,muted)
                    }
                    StormCloseButton(onDismiss)
                }
                StormText("未來動向 · 官方預報時間軸",17,cream,FontWeight.Medium)
                Box(Modifier.fillMaxWidth().height(315.dp).clip(RoundedCornerShape(26.dp))
                    .background(Brush.radialGradient(listOf(Color(0xFF24587B),Color(0xFF081D34),Color(0xFF051426))))) {
                    Canvas(Modifier.fillMaxSize().onSizeChanged { terrainCanvasSize=it }) {
                        val pad=28.dp.toPx()
                        val viewport=TyphoonViewport.forPoints(terrainPoints,size.width,size.height,pad)
                        if(terrain!=null) {
                            drawImage(terrain,dstSize=IntSize(size.width.toInt(),size.height.toInt()))
                            with(NlscTerrain) { drawCredit() }
                        } else with(TyphoonGeoBackdrop) { draw(viewport) }
                        TyphoonGeoBackdrop.drawLabels(this,viewport)
                        val pts=storm.fixes.map {fix ->
                            val (x,y)=viewport.project(fix.longitude,fix.latitude)
                            Offset(x,y)
                        }
                        for (i in 1..5) {
                            val y=size.height*i/6f
                            drawLine(cyan.copy(alpha=.09f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())
                        }
                        // Precisely CWA forecast coordinates; no synthetic forecast beyond end points.
                        val path=Path().apply {
                            moveTo(pts.first().x,pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x,it.y) }
                        }
                        drawPath(path,cyan.copy(alpha=.15f),style=Stroke(width=9.dp.toPx()))
                        drawPath(path,cyan.copy(alpha=.88f),style=Stroke(width=2.6.dp.toPx()))
                        pts.forEachIndexed { index,pt ->
                            drawCircle(cyan.copy(alpha=.23f),7.dp.toPx(),pt)
                            drawCircle(if(index==progress.roundToInt()) Color.White else cyan,3.dp.toPx(),pt)
                        }
                        val idx=floor(smoothProgress).toInt().coerceIn(0,pts.lastIndex)
                        val following=(idx+1).coerceAtMost(pts.lastIndex)
                        val fraction=(smoothProgress-idx).coerceIn(0f,1f)
                        val here=Offset(pts[idx].x+(pts[following].x-pts[idx].x)*fraction,
                            pts[idx].y+(pts[following].y-pts[idx].y)*fraction)
                        val nearest=storm.fixes[idx]
                        nearest.radius7Km?.let { radius ->
                            drawCircle(cyan.copy(alpha=.21f),radius=radius.toFloat()*viewport.pxPerKm.toFloat(),center=here,
                                style=Stroke(width=1.5.dp.toPx()))
                        }
                        for(k in 1..3) drawCircle(cyan.copy(alpha=(.16f/k)*(if(reduceMotion) 1f else breathing)),
                            radius=(14+k*12).dp.toPx(),center=here,style=Stroke(width=1.dp.toPx()))
                        drawCircle(Color(0xFFFFD39F),7.dp.toPx(),here)
                        drawCircle(cream,3.dp.toPx(),here)
                        val (tx,ty)=viewport.project(121.0,23.65)
                        if(tx in pad..(size.width-pad) && ty in pad..(size.height-pad)) {
                            drawCircle(Color(0xFF83F4D1),4.dp.toPx(),Offset(tx,ty))
                            drawCircle(Color(0xFF83F4D1).copy(alpha=.35f),9.dp.toPx(),Offset(tx,ty),style=Stroke(1.dp.toPx()))
                        }
                    }
                    StormText("官方路徑座標 · 時間軸動畫為視覺插值",12,muted,
                        modifier=Modifier.align(Alignment.BottomStart).padding(14.dp))
                }
                GlassPlate(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically) {
                        StormText("${timeFormatter.format(chosen.time)}（臺灣時間）",17,cream,FontWeight.Medium)
                        StormText("${progress.roundToInt()+1} / ${storm.fixes.size}",14,cyan)
                    }
                    Spacer(Modifier.height(12.dp))
                    // Gesture track: no Material Slider, thumb follows scrubbing and snaps to official fix.
                    Box(Modifier.fillMaxWidth().height(45.dp).pointerInput(storm.signature) {
                        detectDragGestures(onDragStart={ start ->
                            progress=(start.x/size.width.toFloat()).coerceIn(0f,1f)*maxStep
                        },onDrag={ change,_ ->
                            progress=(change.position.x/size.width.toFloat()).coerceIn(0f,1f)*maxStep
                            change.consume()
                        },onDragEnd={progress=progress.roundToInt().toFloat()})
                    },contentAlignment=Alignment.Center) {
                        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                            val left=8.dp.toPx();val right=size.width-left
                            val x=left+(right-left)*(progress/maxStep)
                            drawLine(Color.White.copy(alpha=.18f),Offset(left,size.height/2),Offset(right,size.height/2),4.dp.toPx())
                            drawLine(cyan,Offset(left,size.height/2),Offset(x,size.height/2),4.dp.toPx())
                            storm.fixes.indices.forEach { k ->
                                val tx=left+(right-left)*k/maxStep
                                drawCircle(Color.White.copy(alpha=.63f),2.5.dp.toPx(),Offset(tx,size.height/2))
                            }
                            drawCircle(cyan.copy(alpha=.2f),15.dp.toPx(),Offset(x,size.height/2))
                            drawCircle(Color.White,7.dp.toPx(),Offset(x,size.height/2))
                        }
                    }
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
                    StormValue("行進方向",TyphoonDirection.zh(chosen.officialDirection)
                        ?: "官方資料未提供可辨識的中文移向")
                }
                StormText("每 20 分鐘檢查資料；官方預報依發布時間更新。預報路徑有不確定性，警報以中央氣象署為準。",13,muted)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Independent glass button; does not use any Material Design widgets. */
@Composable private fun StormCloseButton(onClick:()->Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha=.17f))
        .border(1.dp,Color.White.copy(alpha=.24f),CircleShape)
        .then(Modifier.clickable { onClick() }),contentAlignment=Alignment.Center) {
        StormText("×",27,cream)
    }
}
