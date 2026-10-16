package io.atmos.weather.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import kotlin.math.roundToInt

private val white=Color.White
private val dim=Color.White.copy(alpha=.70f)

@Composable
fun WeatherDial(title:String, value:String, description:String, fraction:Float, tint:Color, modifier:Modifier=Modifier) {
    val shape=RoundedCornerShape(24.dp)
    Column(modifier.background(white.copy(alpha=.10f),shape)
        .border(1.dp,white.copy(alpha=.15f),shape).padding(15.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            val graphic = when {
                title.contains("紫外線") -> Icons.Rounded.WbSunny
                title.contains("濕度") -> Icons.Rounded.WaterDrop
                title.contains("體感") -> Icons.Rounded.Thermostat
                title.contains("氣壓") -> Icons.Rounded.Speed
                title.contains("日出") -> Icons.Rounded.WbTwilight
                else -> Icons.Rounded.Air
            }
            androidx.compose.material3.Icon(graphic,contentDescription=title,
                modifier=Modifier.size(17.dp),tint=tint)
            Text(title,color=dim,fontSize=15.sp,maxLines=1)
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(value,color=white,fontSize=23.sp,fontWeight=FontWeight.Medium,
                    style=TextStyle(fontFeatureSettings="tnum"))
                Text(description,color=dim,fontSize=14.sp,maxLines=2)
            }
            Box(Modifier.size(65.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw=5.dp.toPx()
                    drawArc(Color.White.copy(alpha=.14f),startAngle=135f,sweepAngle=270f,useCenter=false,
                        style=Stroke(width=sw,cap=StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(tint.copy(alpha=.72f),tint)),startAngle=135f,
                        sweepAngle=270f*fraction.coerceIn(0f,1f),useCenter=false,
                        style=Stroke(width=sw,cap=StrokeCap.Round))
                }
            }
        }
    }
}

fun windDirectionLabel(degrees: Int?):String {
    if (degrees == null) return "風向未提供"
    val directions=listOf("北","東北","東","東南","南","西南","西","西北")
    return "${directions[((degrees+22)%360)/45]}風"
}

/** Six matching cards including pressure and sunrise/sunset. Normal swipes scroll the
 * page; after a long press the tile follows the finger and springs home. The order
 * is saved, so a rearrangement is restored on the next app launch.
 */
@Composable
fun WeatherIndicatorGrid(weather:WeatherSnapshot) {
    val ctx=LocalContext.current
    val haptic=LocalHapticEngine.current
    val pref=remember(ctx) { ctx.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE) }
    val valid=remember(pref) {
        val new=pref.getString("v105_metric_order",null)
        val legacy=pref.getString("v8_metric_order", "0,1,2,3").orEmpty()
        val raw=(new ?: legacy).split(',').mapNotNull { it.toIntOrNull() }
        if(raw.toSet()==(0..5).toSet() && raw.size==6) raw
        else (raw.filter { it in 0..5 }.distinct() + (0..5).filterNot {it in raw}).take(6)
    }
    val order=remember { mutableStateListOf<Int>().apply { addAll(valid) } }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        for(rowIndex in 0..2) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                for(colIndex in 0..1) {
                    val position=rowIndex*2+colIndex
                    val id=order[position]
                    key(id) {
                        MetricTile(id,position,weather,Modifier.weight(1f)) { from,to ->
                            val current=order.indexOf(from)
                            if(current>=0 && to in order.indices && current!=to) {
                                order.removeAt(current)
                                order.add(to,from)
                                pref.edit().putString("v105_metric_order",order.joinToString(",")).apply()
                                haptic?.play(HapticEngine.Event.RELEASE)
                            }
                        }
                    }
                }
            }
            if (rowIndex!=2) Spacer(Modifier.height(12.dp))
        }
        Text("長按卡片即可拿起；跟隨手指拖曳到新位置，放開後彈性排列",
            color=dim,fontSize=14.sp,modifier=Modifier.padding(top=8.dp))
    }
}

@Composable
private fun MetricTile(id:Int,position:Int,weather:WeatherSnapshot,modifier:Modifier,onReorder:(Int,Int)->Unit) {
    val haptics=LocalHapticEngine.current
    val density=LocalDensity.current
    var dragX by remember(id) { mutableFloatStateOf(0f) }
    var dragY by remember(id) { mutableFloatStateOf(0f) }
    var pressed by remember(id) { mutableStateOf(false) }
    val animatedX by androidx.compose.animation.core.animateFloatAsState(
        if(pressed) dragX else 0f,
        animationSpec=androidx.compose.animation.core.spring(
            dampingRatio=.72f,stiffness=400f),label="drag x")
    val animatedY by androidx.compose.animation.core.animateFloatAsState(
        if(pressed) dragY else 0f,
        animationSpec=androidx.compose.animation.core.spring(
            dampingRatio=.72f,stiffness=400f),label="drag y")
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if(pressed) 1.045f else 1f,label="lift tile")
    val gesture=Modifier.pointerInput(id,position) {
        detectDragGesturesAfterLongPress(
            onDragStart={ dragX=0f;dragY=0f;pressed=true;haptics?.play(HapticEngine.Event.TAP) },
            onDragEnd={
                val horizontal=(dragX/with(density){170.dp.toPx()}).roundToInt()
                val vertical=(dragY/with(density){132.dp.toPx()}).roundToInt()
                val row=position/2
                val col=position%2
                val targetRow=(row+vertical).coerceIn(0,2)
                val targetCol=(col+horizontal).coerceIn(0,1)
                pressed=false
                onReorder(id,targetRow*2+targetCol)
            },
            onDragCancel={ pressed=false }
        ) { change,amount ->
            change.consume()
            dragX+=amount.x
            dragY+=amount.y
        }
    }
    val applied=modifier.zIndex(if(pressed)2f else 0f)
        .graphicsLayer {
            translationX=animatedX;translationY=animatedY
            scaleX=scale;scaleY=scale
            shadowElevation=if(pressed) 17.dp.toPx() else 0f
        }.then(gesture)
    when(id) {
        0 -> WeatherDial("紫外線",weather.currentUv?.let { String.format("%.1f",it) } ?: "—",
            if((weather.currentUv ?: 0.0)>=8) "過量" else "目前紫外線",
            ((weather.currentUv ?: 0.0)/11.0).toFloat(),Color(0xFFFFBD71),applied)
        1 -> WeatherDial("濕度",weather.humidityPct?.let { "$it%" } ?: "—","相對濕度",
            (weather.humidityPct ?: 0)/100f,Color(0xFF46C4FF),applied)
        2 -> WeatherDial("體感",weather.feelsLikeC?.let { "${it.roundToInt()}°" } ?: "—","體感溫度",
            (((weather.feelsLikeC ?: 15.0)+10)/50).toFloat(),Color(0xFF80D1E7),applied)
        3 -> WeatherDial("風向 · ${windDirectionLabel(weather.windDirectionDeg)}",
            weather.windKmh?.let { "${it.roundToInt()}" } ?: "—","km/h",
            ((weather.windKmh ?: 0.0)/60).toFloat(),Color(0xFF78D6B4),applied)
        4 -> WeatherDial("氣壓",weather.pressureHpa?.let { "${it.roundToInt()}" } ?: "—",
            "hPa · 地表氣壓",((weather.pressureHpa ?: 980.0)-950.0).toFloat()/100f,
            Color(0xFF7DC9FA),applied)
        else -> {
            val zone=remember(weather.timeZoneId) {
                runCatching { ZoneId.of(weather.timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei"))
            }
            val clock=DateTimeFormatter.ofPattern("HH:mm")
            val sunrise=weather.sunrise?.atZone(zone)?.format(clock) ?: "—"
            val sunset=weather.sunset?.atZone(zone)?.format(clock) ?: "—"
            WeatherDial("日出／日落",sunrise,"日落 $sunset",.66f,
                Color(0xFFFFD18B),applied)
        }
    }
}

fun usAqiLabel(value:Int?):String = when {
    value==null -> "暫無資料"
    value<=50 -> "良好"
    value<=100 -> "普通"
    value<=150 -> "敏感族群注意"
    value<=200 -> "不健康"
    value<=300 -> "非常不健康"
    else -> "危害"
}
