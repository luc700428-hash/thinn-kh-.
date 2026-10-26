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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.sin

/** Decoration only: no pointer handling, forecast generation or warning changes. */
enum class TkPrecipitationMood { CLEAR, RAIN, SNOW }
val TkCardWeatherMood=compositionLocalOf { TkPrecipitationMood.CLEAR }
val TkCardWeatherStrength=compositionLocalOf {.5f}
private val surfaceSeeds=java.util.concurrent.atomic.AtomicInteger(7349)
private val stillClock:State<Float> = mutableFloatStateOf(0f)
val TkCardWeatherClock=compositionLocalOf<State<Float>> {stillClock}

/** One elapsed-time clock, paused off-page and while the Activity is backgrounded. */
@Composable fun rememberTkWeatherClock(enabled:Boolean):State<Float> {
    val clock=remember {mutableFloatStateOf(0f)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->started=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(enabled,started) {
        if(enabled && started) {
            var last=withFrameNanos {it}
            while(true)withFrameNanos {now ->clock.floatValue+=(now-last).coerceIn(0,50_000_000)/1_000_000_000f;last=now}
        }
    }
    return clock
}

@Composable fun TkPrecipitationCardLens(mood:TkPrecipitationMood,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val reduceMotion=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val strength=TkCardWeatherStrength.current.coerceIn(0f,1f)
    val clock=TkCardWeatherClock.current
    val physics=remember {WeatherSurfacePhysics(surfaceSeeds.getAndIncrement())}
    val rain=animateFloatAsState(if(mood==TkPrecipitationMood.RAIN)1f else 0f,
        tween(if(reduceMotion)0 else 1600),label="卡片雨後乾燥")
    var snowTarget by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(mood,strength) {snowTarget=if(mood==TkPrecipitationMood.SNOW)strength.coerceAtLeast(.2f) else 0f}
    val deposit=animateFloatAsState(snowTarget,tween(if(reduceMotion)0 else if(mood==TkPrecipitationMood.SNOW)18000 else 24000),label="積雪與融雪")
    Canvas(modifier) {
        val w=size.width;val h=size.height
        if(w<=1f || h<=1f)return@Canvas
        val wet=rain.value
        if(wet>.001f) {
            physics.advance(if(reduceMotion)0f else clock.value,w/density,h/density,strength,mood==TkPrecipitationMood.RAIN)
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.28f*wet),Color.Transparent,
                Color(0xFFB4EDFF).copy(alpha=.12f*wet))),cornerRadius=CornerRadius(20.dp.toPx()),style=Stroke(.7.dp.toPx()))
            physics.drops.forEach {drop ->
                val x=drop.x*density;val y=drop.y*density;val r=drop.radius*density
                val stretch=1f+(drop.speed/220f).coerceIn(0f,.7f)
                // Keep the middle of the card readable while the edge beads catch more light.
                val edge=if(x<w*.16f || x>w*.84f || y<20.dp.toPx()).85f else .42f
                val alpha=wet*edge
                if(drop.speed>5f)drawLine(Color.White.copy(alpha=.12f*alpha),
                    Offset(x,y-r-drop.speed*density*.09f),Offset(x,y-r),r*.4f,StrokeCap.Round)
                drawOval(Brush.linearGradient(listOf(Color.White.copy(alpha=.50f*alpha),
                    Color(0xFF567A94).copy(alpha=.05f*alpha),Color.White.copy(alpha=.35f*alpha)),
                    Offset(x-r,y-r),Offset(x+r,y+r*stretch)),Offset(x-r,y-r),Size(r*2,r*2*stretch))
                drawOval(Color.White.copy(alpha=.32f*alpha),Offset(x-r,y-r),Size(r*2,r*2*stretch),style=Stroke(.45.dp.toPx()))
                drawCircle(Color.White.copy(alpha=.8f*alpha),r*.20f,Offset(x-r*.30f,y-r*.35f))
            }
            physics.splashes.forEach {drop ->
                drawCircle(Color.White.copy(alpha=(drop.life/.45f).coerceIn(0f,1f)*wet*.65f),
                    .9.dp.toPx(),Offset(drop.x*density,drop.y*density))
            }
        }
        val snow=deposit.value
        if(snow>.001f) {
            val depth=18.dp.toPx()*snow
            val inset=14.dp.toPx();val path=Path()
            // Mounds sit ON the rim, rising above the card rather than painting a flat white stripe.
            path.moveTo(inset,3.dp.toPx())
            for(i in 0..24) {
                val x=inset+(w-inset*2)*i/24f
                val crest=depth*(.63f+.22f*sin(i*1.7f)+.15f*sin(i*.57f))
                if(i==0)path.lineTo(x,-crest) else {
                    val prev=inset+(w-inset*2)*(i-1)/24f
                    path.cubicTo(prev+(x-prev)*.4f,-crest,x-(x-prev)*.3f,-crest,x,-crest)
                }
            }
            path.lineTo(w-inset,3.dp.toPx());path.quadraticTo(w*.5f,depth*.55f,inset,3.dp.toPx());path.close()
            drawPath(path,Brush.verticalGradient(listOf(Color(0xFFF9FDFF),Color(0xFFE2F1F9),Color(0xA6BACEDC)),
                startY=-depth,endY=depth*.65f),alpha=(snow*5).coerceIn(0f,1f))
            drawPath(path,Color.White.copy(alpha=.48f*snow),style=Stroke(.6.dp.toPx()))
            if(mood!=TkPrecipitationMood.SNOW) {
                repeat(4) {i ->val x=inset+(w-inset*2)*(i+1)/5f
                    drawOval(Color(0xA3E2F5FF),Offset(x,depth*.15f),Size(2.dp.toPx(),(3+7*snow).dp.toPx()))}
            }
        }
    }
}
