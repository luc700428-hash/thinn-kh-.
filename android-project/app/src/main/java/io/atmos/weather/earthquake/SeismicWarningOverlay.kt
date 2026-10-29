package io.atmos.weather.earthquake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import io.atmos.weather.maps.rememberTaiwanVectorAtlas
import io.atmos.weather.maps.drawTaiwanAtlasCredit
import io.atmos.weather.ui.thinnSpecularEdge
import io.atmos.weather.ui.GlassIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
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
import io.atmos.weather.ui.ThinnDialog as Dialog
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
    val atlas=rememberTaiwanVectorAtlas()
    val historical=simulation && ChiChi921.isScenario(incident)
    val place=remember(incident.id){SeismicUserLocation.current(context)}
    val estimate=remember(historical,place){if(historical)ChiChi921.estimate(place) else null}
    val reduce=context.getSharedPreferences("thinn_settings",0).getBoolean("reduce_motion",false)
    val accent=if(replay || simulation)Color(0xFFFF9F0A) else Color(0xFFFF6961)
    var entered by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {entered=true}
    val reveal by androidx.compose.animation.core.animateFloatAsState(if(entered)1f else .96f,
        androidx.compose.animation.core.tween(if(reduce)0 else 180,easing=io.atmos.weather.ui.AppleMotion.snap),label="速報光學入場")
    val county=userCounty.orEmpty().replace('台','臺')
    val supplied=remember(incident) { SeismicWarningCounties.fromProvider(incident.localIntensityByCounty) }
    val allCounties=remember(incident) { SeismicWarningCounties.allFromProvider(incident.localIntensityByCounty) }
    val providerLocal=supplied.firstOrNull { it.county==county || (county.isNotEmpty() && county.contains(it.county)) }?.intensity
    val local=if(historical)estimate?.intensity else providerLocal
    val eventKey="${incident.id}:${incident.serial}:$replay:$simulation"
    val frameNow=io.atmos.weather.ui.rememberDisplayWallClock(!reduce)
    // A true incident plays a bounded looping alert tone and one descriptive spoken warning.
    // Historical replay never uses the live alarm tone.
    DisposableEffect(context,eventKey) {
        if(!replay && !simulation) SeismicContinuousWarning.start(context,incident)
        onDispose { if(!replay && !simulation) SeismicContinuousWarning.stop() }
    }
    DisposableEffect(context,eventKey,simulation) {
        if(simulation) SeismicSound.practiceLoop(context)
        onDispose { if(simulation) SeismicSound.stop() }
    }
    DisposableEffect(context,eventKey,simulation) {
        val trainingVoice=if(simulation) SeismicTrainingVoice(context,incident,SeismicUserLocation.current(context)) else null
        onDispose { trainingVoice?.stop() }
    }
    LaunchedEffect(eventKey) {
        if(!replay && !simulation && SeismicSpeech.enabled(context,"eew_voice_enabled")) {
            io.atmos.weather.voice.NaturalVoiceHub.interruptForEarthquake()
            SeismicSpeech.speak(context,SeismicSpeech.warning(incident,SeismicUserLocation.current(context)))
        }
    }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val close by rememberUpdatedState(onClose)
    DisposableEffect(lifecycle,simulation) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(simulation && event==androidx.lifecycle.Lifecycle.Event.ON_STOP)close()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(eventKey) {if(simulation){delay(120_000);close()}}
    val origin=incident.originAtMs
    val waveAvailable=origin!=null && SeismicGeo.visible(incident.latitude,incident.longitude)
    Dialog(onDismissRequest=onClose,properties=DialogProperties(dismissOnClickOutside=false,usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            if(replay || simulation)Color(0xFF241B10) else Color(0xFF291114),Color(0xFF050506),Color.Black)))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp)
            .graphicsLayer {alpha=reveal;scaleX=reveal;scaleY=reveal}) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha=.18f))
                    .thinnSpecularEdge(999.dp),contentAlignment=Alignment.Center) {
                    Icon(Icons.Rounded.WarningAmber,null,tint=accent,modifier=Modifier.size(23.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if(historical)"臺灣 921 大地震演練" else if(simulation)"地震演練" else if(replay)"歷史地震演練" else "地震速報",fontSize=23.sp,lineHeight=31.sp,fontWeight=FontWeight.Bold,color=Color.White,modifier=Modifier.fillMaxWidth())
                    Text(if(historical)"1999/09/21 歷史情境 · 並非真實速報" else if(simulation)"虛構情境 · 並非真實速報" else if(replay)"過去事件回放 · 非即時警報" else "資料來源：${incident.source} · 第 ${incident.serial} 報",fontSize=14.sp,color=Color(0xFFAFAFB5))
                }
                GlassIconButton(onClick=onClose) {Icon(Icons.Rounded.Close,"關閉速報",tint=Color.White)}
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(simulation || replay) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha=.12f)).padding(12.dp)) {
                    Text(if(historical)"演練中，非即時警報。震源採官方歷史資料；當地預估為本機模型，並非當年的即時速報。" else if(simulation)"演練中：所有震央與震度均為虛構。" else "歷史回放中：不是目前發生的地震。",color=accent,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                }
                Text("收到地震速報 · 慎防強烈搖晃",color=Color.White,fontSize=21.sp,lineHeight=29.sp,
                    fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth())
                WarningPlate {
                    Text(if(county.isNotBlank())county else "所在地尚未設定",color=Color(0xFFC5C5CD),fontSize=15.sp,modifier=Modifier.fillMaxWidth())
                    Text("預估震度",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth())
                    Text(local?.let { if(historical && !it.contains("以上"))"$it 級" else it } ?: "—",
                        fontSize=if(historical)42.sp else 68.sp,lineHeight=if(historical)52.sp else 78.sp,
                        fontWeight=FontWeight.Bold,color=accent,modifier=Modifier.fillMaxWidth(),
                        style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"))
                    Text(if(historical)if(estimate==null)"請在地震設定選擇所在地" else "本機模型估算 · 僅供演練"
                        else if(local==null)"待來源提供" else "來源提供的所在地震度",color=Color(0xFFC5C5CD),fontSize=14.sp,
                        modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    Text(incident.epicenter,fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        WarningStatistic("規模",incident.magnitude?.let {"M %.1f".format(Locale.US,it)} ?: "—",Modifier.weight(1f))
                        WarningStatistic("深度",incident.depthKm?.let {"%.0f km".format(Locale.US,it)} ?: "—",Modifier.weight(1f))
                        WarningStatistic(if(historical)"最大震度" else "第幾報",if(historical)"7 級" else incident.serial.toString(),Modifier.weight(1f))
                    }
                }
                if(historical) {
                    Text("歷史發震：1999/09/21 01:47:15.9（臺灣時間）\n震央：23.85°N · 120.82°E",color=Color(0xFFE0E0E7),fontSize=14.sp,lineHeight=21.sp)
                    estimate?.let { localEstimate ->
                        Text("距震央約 ${"%.0f".format(Locale.US,localEstimate.epicentralKm)} 公里 · ${place?.source}",color=Color(0xFFE0E0E7),fontSize=14.sp)
                    }
                    Text("這是演練估算，無法精確到街道；實際搖晃會受地形、土壤與斷層影響。",color=Color(0xFFC5C5CD),fontSize=14.sp,lineHeight=21.sp)
                }
                Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF081B24)).thinnSpecularEdge(24.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val wave=if(waveAvailable)SeismicWavePhysics.wavefront(origin!!,frameNow.value,incident.depthKm ?: 0.0) else null
                        if(atlas!=null)atlas.draw(this) {lon,lat ->Offset(SeismicGeo.screenX(lon,size.width,size.height),SeismicGeo.screenY(lat,size.width,size.height))}
                        if(SeismicGeo.visible(incident.latitude,incident.longitude)) {
                            val epicenter=Offset(SeismicGeo.screenX(incident.longitude!!,size.width,size.height),SeismicGeo.screenY(incident.latitude!!,size.width,size.height))
                            if(wave!=null) {
                                val kmPerPx=SeismicGeo.kmPerPixel(size.width,size.height)
                                if(wave.pSurfaceRadiusKm>0)drawCircle(warningIce.copy(alpha=.75f),(wave.pSurfaceRadiusKm/kmPerPx).toFloat(),epicenter,style=Stroke(1.5.dp.toPx()))
                                if(wave.sSurfaceRadiusKm>0)drawCircle(warningAmber.copy(alpha=.8f),(wave.sSurfaceRadiusKm/kmPerPx).toFloat(),epicenter,style=Stroke(2.dp.toPx()))
                            }
                            drawCircle(accent.copy(alpha=.2f),16.dp.toPx(),epicenter)
                            drawCircle(accent,7.dp.toPx(),epicenter);drawCircle(Color.White,2.5.dp.toPx(),epicenter)
                        }
                        place?.takeIf{SeismicGeo.visible(it.lat,it.lon)}?.let { location ->
                            val here=Offset(SeismicGeo.screenX(location.lon,size.width,size.height),SeismicGeo.screenY(location.lat,size.width,size.height))
                            drawCircle(Color.White,6.dp.toPx(),here);drawCircle(warningIce,4.dp.toPx(),here)
                        }
                        drawTaiwanAtlasCredit()
                    }
                    Column(Modifier.padding(12.dp)) {
                        Text("震央與波圈 · 藍點為所在地",color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium)
                        Text(if(!waveAvailable)"來源資料不足，暫不繪製波圈" else "P／S 波為示意，非到達預測",color=Color(0xFFB2C9D1),fontSize=14.sp)
                    }
                }
                WarningPlate {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(historical)"921 歷史縣市最大震度（舊制）" else if(simulation)"演練情境震度（虛構）" else "各縣市預估震度",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Color.White,modifier=Modifier.weight(1f))
                        Text("${if(historical)ChiChi921.historicalCountyMaxima.size else supplied.size} / 22",fontSize=14.sp,color=Color(0xFFAEAEB2))
                    }
                    Spacer(Modifier.height(10.dp))
                    (if(historical)SeismicWarningCounties.counties.map {CountyIntensity(it,ChiChi921.historicalCountyMaxima[it])} else allCounties).chunked(2).forEach {row ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            row.forEach {entry ->
                                Row(Modifier.weight(1f).padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Text(entry.county,fontSize=14.sp,color=Color(0xFFD1D1D6),modifier=Modifier.weight(1f))
                                    Text(entry.intensity?.let {"${it}級"} ?: "—",fontSize=15.sp,fontWeight=FontWeight.SemiBold,
                                        color=if(entry.county==county)accent else Color.White)
                                }
                            }
                            if(row.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(if(historical)"歷史縣市最大值不是每個地點的震度；舊制 5、6 級不換算成現制強弱。— 為歷史報告未列示。" else "— 表示來源尚未提供，並非震度為零。",fontSize=14.sp,color=Color(0xFF8E8E93))
                }
            }
            Column(Modifier.fillMaxWidth().padding(top=14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("趴下 · 掩護 · 穩住",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Color.White,letterSpacing=1.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick=onClose,modifier=Modifier.fillMaxWidth().height(54.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color.Black)) {
                    Text(if(simulation)"結束模擬演練" else if(replay)"結束歷史回放" else "我已採取避難措施",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun WarningPlate(content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF28282C).copy(alpha=.94f),Color(0xFF1C1C1E).copy(alpha=.94f))))
        .thinnSpecularEdge(24.dp).padding(16.dp),content=content)
}
@Composable private fun WarningStatistic(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,color=Color(0xFFAEAEB2),fontSize=14.sp,modifier=Modifier.fillMaxWidth())
        Text(value,color=Color.White,fontSize=19.sp,lineHeight=26.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth())
    }
}
