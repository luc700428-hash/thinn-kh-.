package io.atmos.weather.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.*

/** Render-only particle field. Rain, ice, snow and sand share a monotonic timebase.
 * Continuous field envelopes prevent visual gaps on weather transitions.
 * This is a stylized, bounded particle simulation, not a fluid solver or a 3D engine.
 */
internal object WeatherSurface {
    enum class Kind { NONE, RAIN, SNOW, SAND }
    private val start = System.nanoTime()
    fun time(): Float = ((System.nanoTime() - start).coerceAtLeast(0L) / 1e9).toFloat()
    fun hash(i: Int): Float {
        var x = i * 0x45d9f3b
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return ((x ushr 8) and 0xFFFFFF) / 16777216f
    }
}

@Composable
internal fun WeatherSurfaceOverlay(kind: WeatherSurface.Kind, windKmh: Float, modifier: Modifier = Modifier) {
    // Keep old material layers fading out when crossing states, rather than hard switching.
    val rain by animateFloatAsState(if(kind == WeatherSurface.Kind.RAIN) 1f else 0f,
        animationSpec=tween(1650), label="rain surface")
    val snow by animateFloatAsState(if(kind == WeatherSurface.Kind.SNOW) 1f else 0f,
        animationSpec=tween(2200), label="snow surface")
    val sand by animateFloatAsState(if(kind == WeatherSurface.Kind.SAND) 1f else 0f,
        animationSpec=tween(1900), label="sand surface")
    // A single lifecycle-aware frame clock; no per-particle coroutines.
    var clock by remember { mutableFloatStateOf(WeatherSurface.time()) }
    LaunchedEffect(kind) {
        while (kind != WeatherSurface.Kind.NONE || rain > .01f || snow > .01f || sand > .01f) {
            withFrameNanos {clock=WeatherSurface.time()}
        }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if(w <= 0f || h <= 0f) return@Canvas
        val wind=windKmh.coerceIn(0f,120f)/120f
        if(rain > .01f) {
            val lineColor=Color(0xFFCAF5FF).copy(alpha=.22f*rain)
            // Droplets accelerate under gravity and bend under wind.
            for(i in 0 until 54) {
                val speed=(.27f+WeatherSurface.hash(i+83)*.45f)
                val t=(clock*speed+WeatherSurface.hash(i+110)*1.4f)
                val y=(t%1.4f-.18f)*h
                val x=((WeatherSurface.hash(i+511) + .01f*clock*wind)%1f)*w
                if (y >= 0f && y <= h) drawLine(lineColor, Offset(x,y),Offset(x-wind*10f,y+9f+speed*17f),strokeWidth=1.6f)
            }
            // Glass-top beading/meniscus, no fake puddle on an upright screen.
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFC6F2FF).copy(alpha=.36f*rain), Color.Transparent),0f,h*.036f),
                topLeft=Offset(0f,0f),size=androidx.compose.ui.geometry.Size(w,h*.036f),
                cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.01f))
            for(i in 0..25) {
                val x=(i+.5f)/26f*w
                val radius=(1.1f+WeatherSurface.hash(i+701)*2.8f)*rain
                drawCircle(Color(0xFFD9F8FF).copy(alpha=.43f*rain),radius,Offset(x,3f+radius))
            }
        }
        if(snow > .01f) {
            // White powder settles on the upper lip, uneven rather than a flat bar.
            val mound=(4.5f+min(5f,clock*.025f))*snow
            for(i in 0..(w/9f).toInt()) {
                val x=i*9f
                val radius=(mound*(.5f+WeatherSurface.hash(i+320)*.7f)).coerceAtLeast(.1f)
                drawCircle(Color(0xFFF3F9FF).copy(alpha=.87f*snow),radius,Offset(x,0f))
            }
            for(i in 0 until 36) {
                val t=clock*(.045f+WeatherSurface.hash(i+81)*.12f)+WeatherSurface.hash(i+30)
                val y=(t%1.12f)*h
                val x=((WeatherSurface.hash(i+901)+sin(clock*.44f+i)*.03f*wind)%1f+1f)%1f*w
                drawCircle(Color.White.copy(alpha=.42f*snow),1.5f+WeatherSurface.hash(i+400)*2.8f,Offset(x,y))
            }
        }
        if(sand > .01f) {
            // Windblown grains with a sandy deposited lip and layered haze.
            val shade=Color(0xFFEBC28A)
            val thickness=(3f+min(8f,clock*.025f))*sand
            for(i in 0..(w/11f).toInt()) {
                val x=i*11f
                val ridge=(.65f+WeatherSurface.hash(i+135)*.55f)*thickness
                drawCircle(shade.copy(alpha=.62f*sand),ridge,Offset(x,0f))
            }
            for(i in 0 until 66) {
                val velocity=.03f+wind*.22f+WeatherSurface.hash(i+76)*.15f
                val x=((WeatherSurface.hash(i+150)+clock*velocity)%1f)*w
                val y=WeatherSurface.hash(i+635)*h*.78f
                drawLine(shade.copy(alpha=.30f*sand),Offset(x,y),Offset(x+8f+wind*21f,y-2f),strokeWidth=1.2f)
            }
            drawRect(Color(0xFFCEAE7B).copy(alpha=.055f*sand))
        }
    }
}
