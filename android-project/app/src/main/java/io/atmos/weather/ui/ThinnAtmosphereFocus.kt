package io.atmos.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** A weather-responsive atmospheric light stage, not a photo, 2D landmark or 3D model.
 * All displayed metrics come from WeatherSnapshot; no simulated live measurements.
 * Motion pauses entirely when the accessibility setting requests reduced motion.
 */
@Composable
fun ThinnAtmosphereFocus(snapshot: WeatherSnapshot, location: String, reducedMotion: Boolean) {
    val highlighted = when (snapshot.condition) {
        WeatherCondition.THUNDERSTORM -> Color(0xFFFFB497)
        WeatherCondition.RAIN, WeatherCondition.FREEZING_RAIN -> Color(0xFF90D9F9)
        WeatherCondition.SNOW, WeatherCondition.SLEET -> Color(0xFFE7F4FF)
        WeatherCondition.DUST, WeatherCondition.DUST_STORM, WeatherCondition.HAZE -> Color(0xFFFFD3A2)
        else -> if (snapshot.isDay) Color(0xFFFFE2A9) else Color(0xFFADC8FF)
    }
    val phase = remember { Animatable(.24f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) phase.snapTo(.24f) else while (isActive) {
            phase.animateTo(.73f, animationSpec=tween(4400, easing=FastOutSlowInEasing))
            phase.animateTo(.24f, animationSpec=tween(4400, easing=FastOutSlowInEasing))
        }
    }
    val context = LocalContext.current
    val opaque = remember(context) {
        context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
            .getBoolean("reduce_transparency",false)
    }
    val shape = RoundedCornerShape(ThinnDesignTokens.radiusCard)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(LocalWeatherCardPalette.current?.brush ?: if(opaque) ThinnOpticalMaterial.opaque else ThinnOpticalMaterial.surface)
        .border(1.dp, ThinnOpticalMaterial.rim, shape)
        .semantics { contentDescription = "${location} 的天氣光景、風速與濕度資訊" }) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val w=size.width
                val h=size.height
                val center=Offset(w*(.22f+phase.value*.65f),h*.42f)
                val haloRadius=w*.55f
                drawCircle(
                    brush=Brush.radialGradient(
                        colors=listOf(highlighted.copy(alpha=if(opaque) .12f else .34f), highlighted.copy(alpha=.065f), Color.Transparent),
                        center=center, radius=haloRadius),
                    radius=haloRadius,center=center
                )
                val secondaryCenter=Offset(w*.84f,h*.96f)
                drawCircle(
                    brush=Brush.radialGradient(
                        listOf(Color(0xFF80D7FF).copy(alpha=.28f),Color.Transparent),
                        center=secondaryCenter,radius=w*.42f),
                    radius=w*.42f,center=secondaryCenter)
                for (i in 0..3) {
                    val radius = w*(.21f+i*.11f)
                    drawCircle(color=Color.White.copy(alpha=.09f-i*.014f),
                        radius=radius,center=Offset(w*.74f,h*.93f),style=Stroke(width=1.dp.toPx()))
                }
                drawLine(brush=Brush.linearGradient(
                    listOf(Color.Transparent,Color.White.copy(alpha=.30f),Color.Transparent)),
                    start=Offset(w*.09f,h*.03f),end=Offset(w*.95f,h*.03f),strokeWidth=1.dp.toPx())
            }
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal=21.dp, vertical=22.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("現在的天氣光景",color=Color(0xFFE6F4FC),fontSize=14.sp,fontWeight=FontWeight.Medium)
                Text(when(snapshot.condition) {
                    WeatherCondition.CLEAR -> if(snapshot.isDay) "天空晴朗" else "夜色清朗"
                    WeatherCondition.PARTLY_CLOUDY -> "晴時多雲"
                    WeatherCondition.CLOUDY -> "雲層較多"
                    WeatherCondition.FOG -> "有霧，注意能見度"
                    WeatherCondition.RAIN, WeatherCondition.FREEZING_RAIN -> "雨勢變化中"
                    WeatherCondition.THUNDERSTORM -> "留意雷雨"
                    WeatherCondition.SNOW, WeatherCondition.SLEET -> "低溫降雪資訊"
                    WeatherCondition.HAIL -> "留意冰雹"
                    WeatherCondition.HAZE, WeatherCondition.DUST, WeatherCondition.DUST_STORM -> "天空較為混濁"
                    else -> "觀察現在的天空"
                },fontSize=27.sp,lineHeight=36.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                Text("依目前天氣狀態呈現的光影效果",fontSize=14.sp,color=Color(0xFFE0EAF3))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=15.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            FocusMetric("風速",snapshot.windKmh?.let { "${it.roundToInt()} km/h" } ?: "—",
                Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(33.dp).background(Color.White.copy(alpha=.18f)))
            FocusMetric("濕度",snapshot.humidityPct?.let { "$it%" } ?: "—",Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(33.dp).background(Color.White.copy(alpha=.18f)))
            FocusMetric("體感",snapshot.feelsLikeC?.let { "${it.roundToInt()}°" } ?: "—",Modifier.weight(1f))
        }
    }
}

@Composable
private fun FocusMetric(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier,horizontalAlignment=Alignment.Start,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,fontSize=14.sp,color=Color(0xFFC7DBEA))
        Text(value,fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Color.White,maxLines=1)
    }
}
