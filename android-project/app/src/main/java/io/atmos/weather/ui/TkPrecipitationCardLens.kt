package io.atmos.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Purely decorative, non-interactive lens. Forecast/alerts never depend on it. */
enum class TkPrecipitationMood { CLEAR, RAIN, SNOW }
val TkCardWeatherMood=compositionLocalOf { TkPrecipitationMood.CLEAR }

@Composable
fun TkPrecipitationCardLens(mood:TkPrecipitationMood, modifier:Modifier=Modifier) {
    if(mood==TkPrecipitationMood.CLEAR) return
    val context=LocalContext.current
    val reduceMotion=remember(context) {
        context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
            .getBoolean("reduce_motion",false)
    }
    val rainMotion=rememberInfiniteTransition(label="卡片雨滴流光")
    val rainOffset by rainMotion.animateFloat(
        initialValue=0f,targetValue=1f,
        animationSpec=infiniteRepeatable(animation=tween(2300,easing=LinearEasing),
            repeatMode=RepeatMode.Restart),label="雨滴位移")
    var depositTarget by remember(mood) { mutableFloatStateOf(0f) }
    LaunchedEffect(mood) { depositTarget=1f }
    val deposit by animateFloatAsState(
        targetValue=depositTarget,animationSpec=tween(14000,easing=FastOutSlowInEasing),
        label="卡片頂部積雪")
    Canvas(modifier) {
        val w=size.width
        val h=size.height
        if(w<=1f || h<=1f) return@Canvas
        if(mood==TkPrecipitationMood.RAIN) {
            // Short refractive glints on corners; content stays legible.
            val sheen=Brush.linearGradient(listOf(
                Color.White.copy(alpha=.20f),Color.Transparent,
                Color(0xFFB4EDFF).copy(alpha=.15f)),
                start=Offset(0f,0f),end=Offset(w,h))
            drawRoundRect(sheen,cornerRadius=androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                style=Stroke(width=.8.dp.toPx()))
            // Rain is attached to card perimeter; translucent streaks stay out
            // of critical weather labels. Motion can be disabled in accessibility.
            val phase=if(reduceMotion) 0f else rainOffset
            for(i in 0..9) {
                val x=(.08f+i*.091f)*w
                val y=(.025f+(i%4)*.012f+phase*.045f)*h
                drawCircle(Color(0xFFD8F3FF).copy(alpha=.09f+(i%3)*.03f),
                    radius=(1.1f+(i%4)*.6f).dp.toPx(),center=Offset(x,y))
            }
        } else if (mood==TkPrecipitationMood.SNOW) {
            // Snow rests ON upper edges of each content card, not over labels.
            // Animate snow accumulation only at the top rim of the card, without
            // covering content; do not change weather observation or forecast.
            val snowFraction=if(reduceMotion) 1f else deposit
            val height=((3f+8f*snowFraction).dp.toPx()).coerceAtMost(h*.075f)
            val path=Path().apply {
                moveTo(8.dp.toPx(),height)
                for(i in 0..24) {
                    val x=w*(i/24f)
                    val n=if(i%5==0) 1.4f else if(i%3==0) .7f else .3f
                    lineTo(x,height+n.dp.toPx())
                }
                lineTo(w,0f);lineTo(0f,0f);close()
            }
            drawPath(path,Brush.verticalGradient(listOf(
                Color(0xDDF8FDFF),Color(0x80D6EBF5),Color.Transparent)))
            drawLine(Color.White.copy(alpha=.39f),
                start=Offset(8.dp.toPx(),height*.34f),end=Offset(w-8.dp.toPx(),height*.34f),
                strokeWidth=.7.dp.toPx())
        }
    }
}
