package io.atmos.weather.next

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

/** Full weather hero: meaningful condition, city-independent forecast values and native
 * real-time optical shader approximation. Pure Compose; not an iOS private effect.
 */
@Composable
fun V25HomeHero(
    temperature: Int,
    condition: String?,
    conditionLabel: String,
    high: Int?, low: Int?, feelsLike: Int?, isNight: Boolean,
    rainProbability: Int? = null,
    modifier: Modifier = Modifier
) {
    val scene=V25WeatherPresentation.from(condition,isNight,rainProbability)
    val ctx=LocalContext.current
    val settings=ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
    val reduceMotion=settings.getBoolean("reduce_motion",false)
    val reduceTransparency=settings.getBoolean("reduce_transparency",false)
    val phase=if(reduceMotion) .45f else {
        val transition=rememberInfiniteTransition(label="v25-weather-optical")
        val animated by transition.animateFloat(
            initialValue=.24f,targetValue=.82f,
            animationSpec=infiniteRepeatable(tween(6400),RepeatMode.Reverse),
            label="v25-weather-glow")
        animated
    }
    val glow=Color(scene.glowArgb.toInt())
    val radius=RoundedCornerShape(38.dp)
    Column(modifier.fillMaxWidth().clip(radius)
        .background(Brush.verticalGradient(if(reduceTransparency)
            listOf(Color(0xFF172A3E),Color(0xFF13273B))
            else listOf(Color(0xC8406177),Color(0x9D233D55),Color(0xC61A2A3E))))
        .border(1.dp,Color.White.copy(alpha=if(reduceTransparency) .15f else .4f),radius),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().heightIn(min=226.dp).padding(vertical=12.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                if(!reduceTransparency) {
                    drawCircle(brush=Brush.radialGradient(
                        colors=listOf(glow.copy(alpha=.36f*phase),glow.copy(alpha=.08f),Color.Transparent),
                        center=Offset(size.width*.72f,size.height*.3f),
                        radius=size.width*.77f),radius=size.width*.77f,
                        center=Offset(size.width*.72f,size.height*.3f))
                    drawCircle(Color.White.copy(alpha=.055f*phase),size.width*.32f,
                        Offset(size.width*.85f,size.height*.86f))
                }
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("${scene.emblem}   現在的天氣",fontSize=15.sp,
                    color=Color(0xFFE4F6FF),fontWeight=FontWeight.Medium)
                Row(verticalAlignment=Alignment.Top) {
                    Text("$temperature",fontSize=94.sp,lineHeight=111.sp,
                        fontWeight=FontWeight.Light,color=Color.White)
                    Text("°",fontSize=47.sp,lineHeight=61.sp,fontWeight=FontWeight.Light,color=Color.White)
                }
                Text(conditionLabel.ifBlank {scene.name},fontSize=22.sp,
                    fontWeight=FontWeight.SemiBold,color=Color.White)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            V25HeroStat("最高",high?.let {"$it°"}?:"—",Modifier.weight(1f))
            V25HeroStat("最低",low?.let {"$it°"}?:"—",Modifier.weight(1f))
            V25HeroStat("體感估算",feelsLike?.let {"$it°"}?:"—",Modifier.weight(1f))
        }
        Text(scene.advice,color=Color(0xFFE3EEF8),fontSize=14.sp,
            textAlign=TextAlign.Center,
            modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=18.dp))
    }
}

@Composable
private fun V25HeroStat(title:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp))
        .background(Color.White.copy(alpha=.085f))
        .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(title,color=Color(0xFFE4EDF7),fontSize=14.sp)
        Text(value,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Medium)
    }
}
