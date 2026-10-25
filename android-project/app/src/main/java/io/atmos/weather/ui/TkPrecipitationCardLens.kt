package io.atmos.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Decoration only. No pointer handling, forecast generation, or warning changes. */
enum class TkPrecipitationMood { CLEAR, RAIN, SNOW }
val TkCardWeatherMood=compositionLocalOf { TkPrecipitationMood.CLEAR }
val TkCardWeatherStrength=compositionLocalOf {.5f}
private val stillClock:State<Float> = mutableFloatStateOf(0f)
val TkCardWeatherClock=compositionLocalOf<State<Float>> {stillClock}

/** One clock per weather page; individual cards read it only in their draw phase. */
@Composable
fun rememberTkWeatherClock(enabled:Boolean):State<Float> {
    if(!enabled) return remember {mutableFloatStateOf(0f)}
    return rememberInfiniteTransition(label="卡片共用天氣時鐘").animateFloat(0f,1f,
        infiniteRepeatable(tween(4600,easing=LinearEasing),RepeatMode.Restart),label="卡片雨滴流動")
}

@Composable
fun TkPrecipitationCardLens(mood:TkPrecipitationMood, modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val reduceMotion=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    val strength=TkCardWeatherStrength.current.coerceIn(0f,1f)
    val clock=TkCardWeatherClock.current
    val rain=animateFloatAsState(if(mood==TkPrecipitationMood.RAIN)1f else 0f,
        tween(if(reduceMotion)0 else 900),label="卡片雨後乾燥")
    var snowTarget by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(mood,strength) {snowTarget=if(mood==TkPrecipitationMood.SNOW) strength.coerceAtLeast(.2f) else 0f}
    val deposit=animateFloatAsState(snowTarget,
        tween(if(reduceMotion)0 else if(mood==TkPrecipitationMood.SNOW)14000 else 18000),label="積雪與融雪")
    Canvas(modifier) {
        val w=size.width;val h=size.height
        if(w<=1f || h<=1f) return@Canvas
        val rainAlpha=rain.value
        if(rainAlpha>.001f) {
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.20f*rainAlpha),
                Color.Transparent,Color(0xFFB4EDFF).copy(alpha=.15f*rainAlpha))),
                cornerRadius=CornerRadius(16.dp.toPx()),style=Stroke(.8.dp.toPx()))
            val phase=if(reduceMotion)0f else clock.value
            val count=(8+12*strength).toInt().coerceIn(8,20)
            repeat(count) {i ->
                val side=i%4==0
                val x=if(side) {if(i%8==0) 7.dp.toPx() else w-8.dp.toPx()} else w*(.06f+(i*.137f)% .88f)
                val slide=(phase+i*.173f)%1f
                val y=if(side) h*(.12f+slide*.73f) else (5f+(i%3)*2+slide*4).dp.toPx()
                val radius=(1.2f+(i%4)*.45f).dp.toPx()
                // A tiny lens body, a brighter upper-left rim and a quiet gravity tail.
                val body=Brush.linearGradient(listOf(Color.White.copy(alpha=.48f*rainAlpha),
                    Color(0xFFB4E4FA).copy(alpha=.10f*rainAlpha),Color.White.copy(alpha=.23f*rainAlpha)),
                    start=Offset(x-radius,y-radius),end=Offset(x+radius,y+radius*1.5f))
                drawLine(Color.White.copy(alpha=.13f*rainAlpha),Offset(x,y-radius*3.3f),
                    Offset(x,y-radius),radius*.5f,StrokeCap.Round)
                drawOval(body,Offset(x-radius,y-radius),Size(radius*2,radius*2.6f))
                drawCircle(Color.White.copy(alpha=.57f*rainAlpha),radius*.24f,Offset(x-radius*.3f,y-radius*.4f))
            }
        }
        val snowFraction=deposit.value
        if(snowFraction>.001f) {
            val height=(12.dp.toPx()*snowFraction).coerceAtMost(h*.075f)
            val path=Path().apply {
                moveTo(0f,0f);lineTo(w,0f);lineTo(w,height*.45f)
                for(i in 12 downTo 1) {
                    val right=w*i/12f;val left=w*(i-1)/12f
                    val mound=height*(if(i%3==0) 1f else .62f)
                    cubicTo(right-(right-left)*.2f,mound,left+(right-left)*.2f,mound,left,height*.5f)
                }
                close()
            }
            drawPath(path,Brush.verticalGradient(listOf(Color(0xEDF8FDFF),Color(0xA0D6EBF5),Color.Transparent),
                startY=0f,endY=height*1.5f),alpha=(snowFraction*3f).coerceIn(0f,1f))
            drawLine(Color.White.copy(alpha=.36f*snowFraction),Offset(8.dp.toPx(),height*.25f),
                Offset(w-8.dp.toPx(),height*.25f),.7.dp.toPx(),StrokeCap.Round)
        }
    }
}
