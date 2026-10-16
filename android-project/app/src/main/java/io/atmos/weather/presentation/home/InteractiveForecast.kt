package io.atmos.weather.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.haptics.LocalHapticEngine
import io.atmos.weather.haptics.HapticEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Scrollable Xiaomi-style timeline: hourly temperature, sun/moon/weather glyph,
 * wind, sunrise/sunset and full-width scrub selection. Vertical scrolling remains
 * with the parent page because only HORIZONTAL movement is consumed by this chart.
 */
@Composable
fun InteractiveForecast(
    hours: List<HourlyWeather>, timeZoneId: String? = null,
    modifier: Modifier = Modifier, sunrise: Instant? = null, sunset: Instant? = null
) {
    val readings = remember(hours) { hours.take(24) }
    if (readings.size < 2) return
    val zone = remember(timeZoneId) {
        runCatching { ZoneId.of(timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei"))
    }
    val scroll=rememberScrollState()
    val selected by remember(readings,scroll) {
        derivedStateOf {
            if (scroll.maxValue <= 0) 0 else
                ((scroll.value.toFloat()/scroll.maxValue)*readings.lastIndex).roundToInt().coerceIn(0,readings.lastIndex)
        }
    }
    val haptic=LocalHapticEngine.current
    var lastFeedback by remember(readings) { mutableIntStateOf(0) }
    LaunchedEffect(selected) {
        if(selected!=lastFeedback) haptic?.play(HapticEngine.Event.DRAG,variation=.12f)
        lastFeedback=selected
    }
    val density = LocalDensity.current
    val columnWidth = 76.dp
    val widthPx = with(density) { columnWidth.toPx() }
    val fullWidth = (columnWidth.value * readings.size).dp
    val pointHalf = widthPx / 2f
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val hour = readings[selected]
    val local = hour.at.atZone(zone)
    val temps = readings.map { it.temperatureC ?: Double.NaN }.filter { it.isFinite() }
    val bottom = (temps.minOrNull() ?: 10.0) - 2.0
    val top = (temps.maxOrNull() ?: 30.0) + 2.0
    val range = (top-bottom).coerceAtLeast(4.0)
    fun yDp(t:Double): Float = 42f + ((top-t)/range * 62).toFloat()

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically) {
            Text("24 小時預報",fontSize=21.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
            Text("${local.format(clock)}  ${hour.temperatureC?.roundToInt()?.let { "$it°" } ?: "—"}",
                color=Color(0xFFFFD68A),fontSize=16.sp,style=TextStyle(fontFeatureSettings="tnum"))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(weatherVector(hour.weatherCode,local.hour),null,tint=Color(0xFFFFDB92),modifier=Modifier.size(22.dp))
            Spacer(Modifier.width(7.dp))
            Text(hourlyWeatherName(hour.weatherCode,local.hour),color=Color.White,fontSize=16.sp,
                fontWeight=FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("降雨 ${hour.precipitationProbabilityPct?.let { "$it%" } ?: "—"}",
                color=Color.White.copy(alpha=.95f),fontSize=16.sp)
        }
        Spacer(Modifier.height(4.dp))
        // One scrollable content surface keeps all four rows perfectly aligned.
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Column(Modifier.width(fullWidth)) {
                Box(Modifier.fillMaxWidth().height(127.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val coords = readings.mapIndexed { index, h ->
                            val temp = (h.temperatureC ?: temps.firstOrNull() ?: 20.0)
                            Offset(pointHalf + index*widthPx, yDp(temp).dp.toPx())
                        }
                        listOf(54f,84f,114f).forEach { dy ->
                            drawLine(Color.White.copy(alpha=.09f),Offset(0f,dy.dp.toPx()),
                                Offset(size.width,dy.dp.toPx()),1.dp.toPx())
                        }
                        val path=Path().apply {
                            moveTo(coords.first().x,coords.first().y)
                            for (index in 1 until coords.size) {
                                val a=coords[index-1];val b=coords[index]
                                val mid=(a.x+b.x)/2f
                                cubicTo(mid,a.y,mid,b.y,b.x,b.y)
                            }
                        }
                        drawPath(path,Brush.horizontalGradient(listOf(Color(0xFFFFD38C),Color(0xFFFFAD53))),
                            style=Stroke(width=3.dp.toPx(),cap=StrokeCap.Round))
                        val at=coords[selected]
                        drawLine(Color.White.copy(alpha=.60f),Offset(at.x,0f),Offset(at.x,size.height),1.1.dp.toPx())
                        drawCircle(Color(0xFFFFCF85),5.8.dp.toPx(),at)
                        drawCircle(Color.White,2.8.dp.toPx(),at)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        readings.forEach { reading ->
                            val value=reading.temperatureC ?: temps.firstOrNull() ?: 20.0
                            Box(Modifier.width(columnWidth).fillMaxHeight()) {
                                Text("${value.roundToInt()}°",modifier=Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y=(yDp(value)-34f).coerceAtLeast(0f).dp),
                                    color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Medium,
                                    style=TextStyle(fontFeatureSettings="tnum"))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    readings.forEachIndexed { index, reading ->
                        val t=reading.at.atZone(zone)
                        val isSunrise=sunrise?.atZone(zone)?.let { it.hour == t.hour && it.toLocalDate() == t.toLocalDate() } == true
                        val isSunset=sunset?.atZone(zone)?.let { it.hour == t.hour && it.toLocalDate() == t.toLocalDate() } == true
                        Column(Modifier.width(columnWidth),horizontalAlignment=Alignment.CenterHorizontally) {
                            if (isSunrise || isSunset) {
                                Text(if (isSunrise) "日出 ${sunrise?.atZone(zone)?.format(clock).orEmpty()}"
                                    else "日落 ${sunset?.atZone(zone)?.format(clock).orEmpty()}",
                                    fontSize=14.sp,color=Color(0xFFFFD28B),maxLines=1)
                            } else Spacer(Modifier.height(17.dp))
                            Icon(if (isSunrise) Icons.Rounded.WbTwilight
                                else if (isSunset) Icons.Rounded.NightsStay
                                else weatherVector(reading.weatherCode,t.hour),
                                contentDescription=hourlyWeatherName(reading.weatherCode,t.hour),
                                tint=if(t.hour < 6 || t.hour >=18) Color(0xFFFFE1B8) else Color(0xFFFFCF75),
                                modifier=Modifier.size(25.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(reading.windKmh?.let { "%.1f m/s".format(java.util.Locale.US,it / 3.6) } ?: "—",
                                color=Color.White.copy(alpha=.82f),fontSize=14.sp,
                                style=TextStyle(fontFeatureSettings="tnum"),maxLines=1)
                            Spacer(Modifier.height(9.dp))
                            Text(if(index==0) "現在" else t.format(clock),color=Color.White,
                                fontSize=14.sp,style=TextStyle(fontFeatureSettings="tnum"),maxLines=1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("左右滑動預報，曲線游標與上方資訊同步移動 · ${local.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))} · ${hourlyWeatherName(hour.weatherCode,local.hour)}",
            color=Color.White.copy(alpha=.73f),fontSize=14.sp)
    }
}

/** WMO code interpretation. These labels refer to forecast conditions, not warnings. */
fun hourlyWeatherName(code: Int?, hour: Int): String = when(code) {
    0 -> if(hour < 6 || hour >= 18) "晴朗夜晚" else "晴天"
    1 -> if(hour < 6 || hour >= 18) "夜間晴時多雲" else "晴時多雲"
    2 -> "局部多雲"
    3 -> "陰天"
    45,48 -> "起霧"
    51,53,55,56,57 -> "毛毛雨"
    61,66 -> "小雨"
    63 -> "中雨"
    65,67,82 -> "大雨"
    80 -> "短暫小雨"
    81 -> "短暫中雨"
    71,77,85 -> "小雪"
    73 -> "中雪"
    75,86 -> "大雪"
    95 -> "雷雨"
    96,99 -> "強雷雨"
    else -> "天氣變化"
}
fun weatherVector(code:Int?,hour:Int):androidx.compose.ui.graphics.vector.ImageVector = when(code) {
    0,1 -> if(hour < 6 || hour >= 18) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny
    2,3 -> Icons.Rounded.Cloud
    45,48 -> Icons.Rounded.Dehaze
    51,53,55,56,57,61,63,65,66,67,80,81,82 -> Icons.Rounded.Grain
    71,73,75,77,85,86 -> Icons.Rounded.AcUnit
    95,96,99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.CloudQueue
}
