package io.atmos.weather.earthquake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

private val warningIce = Color(0xFF8DEAFF)
private val warningAmber = Color(0xFFFFCE82)
private val warningRed = Color(0xFFE75D5B)

/** Native Android warning sheet. No copied DPIP source or assets.
 * County intensities are NEVER fabricated: only validated source fields are shown.
 * Animated P/S radii use illustrative constant speeds, NOT arrival predictions.
 */
@Composable
fun SeismicWarningOverlay(incident:EewIncident,replay:Boolean,userCounty:String?,onClose:()->Unit,
    simulation:Boolean=false) {
    val context=LocalContext.current
    val county=userCounty.orEmpty().replace('台','臺')
    val supplied=remember(incident) { SeismicWarningCounties.fromProvider(incident.localIntensityByCounty) }
    val allCounties=remember(incident) { SeismicWarningCounties.allFromProvider(incident.localIntensityByCounty) }
    val local=supplied.firstOrNull { it.county==county || (county.isNotEmpty() && county.contains(it.county)) }?.intensity
    val eventKey="${incident.id}:${incident.serial}:$replay:$simulation"
    var frameNow by remember(eventKey) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(eventKey) {
        while(true) { frameNow=System.currentTimeMillis(); delay(120) }
    }
    // A true incident plays a bounded looping alert tone and one descriptive spoken warning.
    // Historical replay never uses the live alarm tone.
    DisposableEffect(context,eventKey) {
        if(!replay) SeismicContinuousWarning.start(context,incident)
        onDispose { if(!replay) SeismicContinuousWarning.stop() }
    }
    DisposableEffect(context,eventKey,simulation) {
        if(simulation) SeismicSound.practiceLoop(context)
        onDispose { if(simulation) SeismicSound.stop() }
    }
    DisposableEffect(context,eventKey,simulation) {
        val trainingVoice=if(simulation) SeismicTrainingVoice(context) else null
        onDispose { trainingVoice?.stop() }
    }
    LaunchedEffect(eventKey) {
        if(!replay && SeismicSpeech.enabled(context,"eew_voice_enabled")) {
            SeismicSpeech.speak(context,SeismicSpeech.warning(incident,SeismicUserLocation.current(context)))
        }
    }
    val origin=incident.originAtMs
    val wave=if(origin!=null && SeismicGeo.visible(incident.latitude,incident.longitude))
        SeismicWavePhysics.wavefront(origin,frameNow,incident.depthKm ?: 0.0) else null
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            if(replay) Color(0xFF153651) else Color(0xFF48202C), Color(0xFF071A2E))))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal=18.dp,vertical=12.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(simulation) "地震預警・模擬演練" else if(replay)"歷史地震演練" else "地震預警",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
                    Text(if(simulation) "【演練】虛構震央與震度 · 並非真實速報" else if(replay) "過去事件回放 · 非即時警報" else "資料來源：${incident.source} · 第三方速報",
                        fontSize=14.sp,color=Color(0xFFE6F1F9))
                }
                TextButton(onClick=onClose) { Text("關閉",color=Color.White,fontSize=16.sp) }
            }
            if(simulation) {
                Text("● 演練中 — 此畫面與所有震度均為模擬，不代表目前有地震",
                    color=Color(0xFFFFE199),fontSize=15.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.fillMaxWidth().padding(vertical=6.dp),textAlign=TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.10f),RoundedCornerShape(19.dp))
                .padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(incident.epicenter,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=2)
                    Text("規模 ${incident.magnitude?.let { "M${"%.1f".format(Locale.US,it)}" } ?: "來源未提供"}  ·  深度 ${incident.depthKm?.let {"${"%.0f".format(Locale.US,it)} km"} ?: "來源未提供"}",
                        color=Color(0xFFE6F1F9),fontSize=15.sp)
                }
                Text(local?.let { "${it}級" } ?: "—",fontSize=23.sp,fontWeight=FontWeight.Bold,color=warningAmber)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF09243B),RoundedCornerShape(22.dp))
                .border(1.dp,Color.White.copy(alpha=.13f),RoundedCornerShape(22.dp))) {
                Canvas(Modifier.fillMaxSize().padding(9.dp)) {
                    val land=Path()
                    SeismicGeo.island.forEachIndexed { index,p ->
                        val pt=Offset(SeismicGeo.screenX(p.first,size.width,size.height),SeismicGeo.screenY(p.second,size.width,size.height))
                        if(index==0) land.moveTo(pt.x,pt.y) else land.lineTo(pt.x,pt.y)
                    }
                    land.close()
                    drawPath(land,Color(0xFF194D63))
                    drawPath(land,warningIce.copy(alpha=.8f),style=Stroke(1.8.dp.toPx()))
                    SeismicGeo.outlyingIslands.forEach { island ->
                        val path=Path()
                        island.points.forEachIndexed { i,(lon,lat)->
                            val pt=Offset(SeismicGeo.screenX(lon,size.width,size.height),SeismicGeo.screenY(lat,size.width,size.height))
                            if(i==0)path.moveTo(pt.x,pt.y) else path.lineTo(pt.x,pt.y)
                        }
                        path.close()
                        drawPath(path,Color(0xFF194D63));drawPath(path,warningIce.copy(alpha=.8f),style=Stroke(1.dp.toPx()))
                    }
                    SeismicGeo.smallerIslands.forEach { (_,coord) ->
                        val pt=Offset(SeismicGeo.screenX(coord.first,size.width,size.height),SeismicGeo.screenY(coord.second,size.width,size.height))
                        drawCircle(warningIce,2.dp.toPx(),pt)
                    }
                    if(SeismicGeo.visible(incident.latitude,incident.longitude)) {
                        val epicenter=Offset(SeismicGeo.screenX(incident.longitude!!,size.width,size.height),
                            SeismicGeo.screenY(incident.latitude!!,size.width,size.height))
                        // Only if origin timestamp is supplied and suitable for animation.
                        if(wave!=null) {
                            val kmPerPx=SeismicGeo.kmPerPixel(size.width,size.height)
                            if(wave.pSurfaceRadiusKm>0) drawCircle(warningIce.copy(alpha=.8f),
                                (wave.pSurfaceRadiusKm/kmPerPx).toFloat(),epicenter,style=Stroke(2.5.dp.toPx()))
                            if(wave.sSurfaceRadiusKm>0) drawCircle(warningAmber.copy(alpha=.85f),
                                (wave.sSurfaceRadiusKm/kmPerPx).toFloat(),epicenter,style=Stroke(3.dp.toPx()))
                        }
                        drawCircle(warningRed,9.dp.toPx(),epicenter)
                        drawCircle(Color.White,3.5.dp.toPx(),epicenter)
                    }
                }
                Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Text("臺灣及離島 · 地理示意",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                    Text(if(wave==null)"P／S 波資料不足，暫不繪製" else "P 波（青）  S 波（橘）· 速度示意，非到達預測",
                        fontSize=14.sp,color=Color(0xFFC7DDEA))
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().heightIn(min=82.dp,max=156.dp)
                .background(Color.White.copy(alpha=.075f),RoundedCornerShape(18.dp)).padding(12.dp)) {
                Text(if(simulation) "演練情境震度（虛構）· 22 縣市" else "各縣市預估震度 · 已取得 ${supplied.size}/22",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                Spacer(Modifier.height(5.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    allCounties.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical=2.dp)) {
                            row.forEach { entry ->
                                Text("${entry.county}  ${entry.intensity?.let { "${it}級" } ?: "待來源提供"}",fontSize=14.sp,
                                    color=if(entry.county==county) warningAmber else if(entry.intensity==null) Color(0xFFA6BBCC) else Color.White,
                                    modifier=Modifier.weight(1f))
                            }
                            if(row.size==1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("趴下 · 掩護 · 穩住",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,
                color=Color.White,textAlign=TextAlign.Center)
            Spacer(Modifier.height(9.dp))
            Button(onClick=onClose,modifier=Modifier.fillMaxWidth().height(54.dp)) {
                Text(if(simulation) "結束模擬演練" else if(replay)"結束歷史回放" else "我已採取避難措施",fontSize=18.sp,color=Color(0xFF082338),fontWeight=FontWeight.Bold)
            }
        }
    }
}
