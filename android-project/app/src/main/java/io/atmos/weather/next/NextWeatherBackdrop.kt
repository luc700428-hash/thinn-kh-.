package io.atmos.weather.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.sin

/** Background extension: does NOT alter existing layout, pager or touch targets. */
@Composable
fun NextWeatherBackdrop(scene:WeatherScene?, phase:Float, night:Boolean, modifier:Modifier=Modifier.fillMaxSize(),precipitation:Boolean=true) {
    if(scene == null) return
    Canvas(modifier) {
        val w=size.width;val h=size.height
        val t=phase.coerceIn(0f,1f)
        val tau=(2*PI).toFloat()
        when(scene) {
            WeatherScene.SUNNY -> {
                if(!night) {
                    val sunlight=Offset(w*.77f,h*.13f)
                    drawCircle(Brush.radialGradient(listOf(Color(0x34FFF2B9),Color.Transparent),
                        center=sunlight,radius=w*.52f),w*.52f,sunlight)
                    for(i in 0 until 7) {
                        val a=i*tau/7f + t*.25f
                        val x=sunlight.x + kotlin.math.cos(a)*w*.2f
                        val y=sunlight.y + sin(a)*w*.2f
                        drawLine(Color(0x21FFF3C6),sunlight,Offset(x,y),strokeWidth=w*.007f)
                    }
                }
            }
            WeatherScene.DRIZZLE,WeatherScene.LIGHT_RAIN,WeatherScene.MODERATE_RAIN,
            WeatherScene.HEAVY_RAIN,WeatherScene.THUNDERSTORM,WeatherScene.SEVERE_THUNDERSTORM -> {
                if(!precipitation)return@Canvas
                val intensity=when(scene) {
                    WeatherScene.DRIZZLE -> 24
                    WeatherScene.LIGHT_RAIN -> 42
                    WeatherScene.MODERATE_RAIN -> 66
                    WeatherScene.HEAVY_RAIN -> 92
                    WeatherScene.THUNDERSTORM -> 103
                    else -> 126
                }
                val opacity=if(scene==WeatherScene.DRIZZLE) .085f else .12f
                val length=if(scene==WeatherScene.DRIZZLE) .016f else if(intensity>90) .045f else .028f
                for(i in 0 until intensity) {
                    val x=((i*.6180339f + sin(i*.33f)*.09f)%1f)*w
                    val speed=(.72f + (i%5)*.15f)
                    val y=((i*.13713f + t*speed)%1f)*h
                    drawLine(Color.White.copy(alpha=opacity),Offset(x,y),
                        Offset(x+w*.005f,y+h*length),strokeWidth=if(intensity>90)1.6f else 1.05f,
                        cap=StrokeCap.Round)
                }
            }
            WeatherScene.HAZE,WeatherScene.FOG,WeatherScene.DENSE_FOG -> {
                val strength=when(scene){WeatherScene.HAZE->.06f;WeatherScene.FOG->.10f;else->.17f}
                val tint=if(scene==WeatherScene.HAZE)Color(0xFFD8C4A2) else Color(0xFFDDEAF0)
                for(i in 0 until 7) {
                    val x=(i*.20f-.25f+sin(t*tau + i)*.06f)*w
                    val y=(.16f+i*.115f)*h
                    drawOval(Brush.horizontalGradient(listOf(Color.Transparent,tint.copy(alpha=strength),Color.Transparent)),
                        Offset(x,y),Size(w*.9f,h*.13f))
                }
            }
            WeatherScene.DUST_STORM -> {
                drawRect(Color(0x137C5A31))
                for(i in 0 until 88) {
                    val x=((i*.7548777f + t*.24f*(1+i%4))%1f)*w
                    val y=((i*.4142136f+sin(t*tau+i)*.018f)%1f)*h
                    drawCircle(Color(0x39E1C29A),(.7f+i%3*.38f),Offset(x,y))
                }
            }
            WeatherScene.LIGHT_SNOW,WeatherScene.MODERATE_SNOW,WeatherScene.HEAVY_SNOW -> {
                if(!precipitation)return@Canvas
                val count=when(scene){WeatherScene.LIGHT_SNOW->25;WeatherScene.MODERATE_SNOW->58;else->96}
                for(i in 0 until count) {
                    val x=((i*.61803f + sin(t*tau+i*.46f)*.028f)%1f)*w
                    val y=((i*.382f + t*(.1f+(i%5)*.045f))%1f)*h
                    drawCircle(Color.White.copy(alpha=.22f + (i%5)*.07f),
                        (1.1f + (i%4)*.63f),Offset(x,y))
                }
            }
        }
    }
}
