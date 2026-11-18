package io.atmos.weather.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.*
import io.atmos.weather.presentation.home.LocalAppearance

data class IOSMotionResponse(val damping:Float,val stiffness:Float)
@Composable fun iosMotionResponse():IOSMotionResponse{
 val v=LocalAppearance.current.springiness.coerceIn(0f,1f)
 // Default springiness 0.7 gives damping 0.62 and stiffness 264: the response (0.387 s) and the
 // ~8% overshoot measured frame by frame on the iOS island (islandcn's recordings).
 return IOSMotionResponse(.9f-.4f*v,320f-80f*v)
}
/** Observes the existing click gesture; never steals scrolling or replaces a callback. */
fun Modifier.iosJellyPress():Modifier=composed{
 val reduce=rememberAppleReduceMotion();val response=iosMotionResponse();var down by remember{mutableStateOf(false)}
 val x by animateFloatAsState(if(down&&!reduce).975f else 1f,if(reduce)snap() else if(down)AppleMotion.interactive() else spring(response.damping,response.stiffness),label="果凍橫向回彈")
 val y by animateFloatAsState(if(down&&!reduce).95f else 1f,if(reduce)snap() else if(down)AppleMotion.interactive() else spring(response.damping,response.stiffness),label="果凍縱向回彈")
 this.graphicsLayer{scaleX=x;scaleY=y;alpha=if(down&&!reduce).92f else 1f}.pointerInput(Unit){awaitEachGesture{awaitFirstDown(requireUnconsumed=false);down=true;try{var held=true;while(held){val event=awaitPointerEvent(PointerEventPass.Final);held=event.changes.any{it.pressed}}}finally{down=false}}}
}

/**
 * iOS 27 content entrance: rises 14 dp and fades in on the fluid curve, staggered by [index]
 * (40 ms apart, capped) so lists settle like a waterfall. Runs once per composition; Reduce Motion
 * shows content immediately.
 */
fun Modifier.iosAppear(index:Int=0):Modifier=if(LiquidGlassKit.softwareRenderedDevice)this else composed{
    // Emulators draw through SwiftShader: twenty tiles fading at once (each faded layer is its own
    // offscreen buffer per frame) can crash the emulator's host renderer, so they appear at once.
    val reduce=rememberAppleReduceMotion()
    val progress=remember{androidx.compose.animation.core.Animatable(if(reduce)1f else 0f)}
    LaunchedEffect(reduce){
        if(reduce)progress.snapTo(1f)
        else progress.animateTo(1f,androidx.compose.animation.core.tween(420,delayMillis=(index.coerceIn(0,8))*40,easing=AppleMotion.fluid))
    }
    graphicsLayer{
        val p=progress.value
        alpha=p;translationY=(1f-p)*14.dp.toPx()
        val s=.985f+.015f*p;scaleX=s;scaleY=s
    }
}

/** A gentle once-per-second heartbeat for live countdown numbers (off with Reduce Motion). */
fun Modifier.iosHeartbeat(key:Any?,strong:Boolean=false):Modifier=composed{
    val reduce=rememberAppleReduceMotion()
    val beat=remember{androidx.compose.animation.core.Animatable(1f)}
    LaunchedEffect(key,reduce){
        if(reduce)return@LaunchedEffect
        beat.snapTo(if(strong)1.08f else 1.04f)
        beat.animateTo(1f,androidx.compose.animation.core.spring(.55f,420f))
    }
    graphicsLayer{scaleX=beat.value;scaleY=beat.value}
}

/** Hands out 0, 1, 2… to the cards of one page so they settle in one after another. */
class AppearSequence { private var next = 0; fun take(): Int = next++ }
val LocalAppearSequence = staticCompositionLocalOf<AppearSequence?> { null }

/**
 * [iosAppear] with the position taken from the page's [AppearSequence]: the first card rises
 * first, the next 40 ms later, and so on. Outside a sequence everything appears together.
 */
fun Modifier.iosAppearNext(): Modifier = composed {
    val sequence = LocalAppearSequence.current
    val index = remember { sequence?.take() ?: 0 }
    iosAppear(index)
}

/** Starts a fresh sequence for one page or sheet. */
@Composable fun AppearSequenceScope(content: @Composable () -> Unit) {
    val sequence = remember { AppearSequence() }
    CompositionLocalProvider(LocalAppearSequence provides sequence, content = content)
}
