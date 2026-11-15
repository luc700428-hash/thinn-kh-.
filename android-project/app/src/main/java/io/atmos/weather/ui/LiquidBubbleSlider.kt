package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import kotlin.math.abs

/** iOS 27 slider: a white knob that lifts into a clear magnifying lens while it is held. */
@Composable
internal fun LiquidBubbleSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier,
    valueRange:ClosedFloatingPointRange<Float>,steps:Int,onValueChangeFinished:(()->Unit)?,colors:SliderColors?,enabled:Boolean) {
    val opaque=rememberAppleReduceTransparency()
    val reduce=rememberAppleReduceMotion()
    val response=iosMotionResponse()
    val feedback=LocalHapticEngine.current
    val density=LocalDensity.current
    val low=valueRange.start.takeIf{it.isFinite()}?:0f
    val high=valueRange.endInclusive.takeIf{it.isFinite() && it>low}?:low+1f
    val fraction=BubbleSliderGeometry.fraction(value,low,high)
    val current by rememberUpdatedState(fraction)
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    var dragging by remember{mutableStateOf(false)}
    var pressed by remember{mutableStateOf(false)}
    val head by animateFloatAsState(fraction,if(dragging||reduce)tween(0) else spring(response.damping,response.stiffness),label="bubble head")
    val tail by animateFloatAsState(head,if(reduce)tween(0) else spring(FluidGlassStyle.TAIL_DAMPING,FluidGlassStyle.TAIL_STIFFNESS),label="adhering water tail")
    val lift by animateFloatAsState(if((dragging||pressed)&&!reduce)1f else 0f,if(reduce)tween(0) else spring(FluidGlassStyle.CONTACT_DAMPING,FluidGlassStyle.CONTACT_STIFFNESS),label="bubble rebound")
    val dark=MaterialTheme.colorScheme.background.red<.5f
    val active=colors?.activeTrackColor?:MaterialTheme.colorScheme.primary
    val inactive=colors?.inactiveTrackColor?:if(dark)brandColor(0xFF565C5A) else Color(0xFFC7C7CC)
    val solid=MaterialTheme.colorScheme.surface
    val opticalTail=tail.coerceIn(head-.12f,head+.12f)
    fun quantized(v:Float)=BubbleSliderGeometry.quantize(v,low,high,steps)
    Box(modifier.fillMaxWidth().heightIn(min=48.dp)
        .graphicsLayer {alpha=if(enabled)1f else .42f}
        .then(if(enabled)Modifier.pointerInput(low,high,steps) {
            val inset=(BubbleSliderGeometry.insetDp*density.density).coerceAtMost(size.width/2f)
            fun mapped(x:Float)=quantized(low+(high-low)*((x-inset)/(size.width-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f))
            detectTapGestures(onPress={pressed=true;try{tryAwaitRelease()}finally{pressed=false}},
                onTap={p->change(mapped(p.x));feedback?.play(HapticEngine.Event.TAP);finish?.invoke()})
        }.pointerInput(low,high,steps) {
            val inset=(BubbleSliderGeometry.insetDp*density.density).coerceAtMost(size.width/2f)
            var grab=0f
            detectHorizontalDragGestures(onDragStart={p->
                dragging=true
                val center=inset+(size.width-2*inset)*current
                grab=(p.x-center).takeIf{abs(it)<=inset}?:0f
            },onDragCancel={dragging=false;finish?.invoke()},onDragEnd={
                dragging=false;feedback?.play(HapticEngine.Event.RELEASE);finish?.invoke()
            },onHorizontalDrag={p,_->
                change(quantized(low+(high-low)*((p.position.x-grab-inset)/(size.width-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f)))
                p.consume()
            })
        } else Modifier)
        .semantics {
            progressBarRangeInfo=ProgressBarRangeInfo(low+(high-low)*fraction,low..high,steps.coerceIn(0,10_000))
            if(enabled)setProgress {proposed->change(quantized(proposed));finish?.invoke();true} else disabled()
        },contentAlignment=Alignment.Center) {
        val light=GlassLight.current(follow=!opaque)
        val knob=remember{Path()};val rim=remember{Path()}
        val optics=remember{if(android.os.Build.VERSION.SDK_INT>=33&&!opaque)runCatching{LiquidLensEffect()}.getOrNull() else null}
        class Knob(val rect:Rect,val clear:Float,val y:Float,val inset:Float)
        // One pure geometry for both layers, so the lensing and the lighting never drift apart.
        fun knobOf(w:Float,h:Float):Knob? {
            val g=BubbleSliderGeometry.lens(w,h,density.density,head,opticalTail,lift,reduce)?:return null
            val restW=38f*density.density;val restH=24f*density.density
            // The white resting knob grows into the clear lens; springs may overshoot either way.
            val grow=if(lift.isFinite())lift.coerceIn(-.12f,1.15f) else 0f
            val stretch=LiquidGlassKit.stretch((g.tail-g.head)/g.width)
            val kw=LiquidGlassKit.lerp(restW,g.width,grow)*(1f+stretch)
            val kh=LiquidGlassKit.lerp(restH,g.height,grow)*(1f-stretch*.35f)
            val cx=g.head+(g.tail-g.head)*.18f
            return Knob(Rect(cx-kw/2,g.y-kh/2,cx+kw/2,g.y+kh/2),BubbleSliderGeometry.unit(lift),g.y,
                (BubbleSliderGeometry.insetDp*density.density).coerceAtMost(w/2))
        }
        // Layer 1: track, shadow and what is seen through the glass. On Android 13+ the whole
        // layer is lensed: inside the knob the magnified track bends at the rim, as in iOS 27.
        Canvas(Modifier.matchParentSize().graphicsLayer {
            renderEffect=optics?.let{o->knobOf(size.width,size.height)?.let{k->
                o.effect(size.width,size.height,k.rect,k.rect.minDimension/2,k.clear,density.density)}}
        }) {
            val k=knobOf(size.width,size.height)?:return@Canvas
            val rect=k.rect;val clear=k.clear
            knob.reset();knob.addRoundRect(RoundRect(rect,CornerRadius(rect.minDimension/2)))
            val restW=38.dp.toPx()
            val start=k.inset-restW/2;val end=size.width-k.inset+restW/2
            val fill=k.inset+(size.width-2*k.inset)*head.coerceIn(0f,1f)
            val thickness=5.dp.toPx()
            fun track() {
                drawLine(inactive.copy(alpha=.60f),Offset(start,k.y),Offset(end,k.y),thickness,StrokeCap.Round)
                drawLine(active,Offset(start,k.y),Offset(fill,k.y),thickness,StrokeCap.Round)
            }
            // The track runs under the glass, so the knob cuts it out and shows it magnified.
            clipPath(knob,ClipOp.Difference) {track()}
            // Soft shadow from a few widening translucent capsules. No mask-filter blur: the knob
            // changes size every frame while it lifts, and a blurred mask would be re-rendered and
            // cached for every size (GPU memory churn that took down the CI emulator).
            val shade=.20f-.10f*clear
            for(n in 1..3) {
                val spread=n*1.6f*density.density
                drawRoundRect(Color.Black.copy(alpha=shade*.22f),Offset(rect.left-spread*.6f,rect.top+2.dp.toPx()-spread*.3f),
                    Size(rect.width+spread*1.2f,rect.height+spread),CornerRadius((rect.height+spread)/2))
            }
            clipPath(knob) {
                drawPath(knob,if(opaque)solid else Color.White.copy(alpha=if(dark).05f else .08f))
                val zoom=1f+(LiquidGlassKit.MAGNIFY-1f)*clear
                withTransform({scale(zoom,zoom*1.15f,rect.center)}) {track()}
            }
        }
        // Layer 2: light. Crisp specular rim from the sun or moon, and the white resting knob.
        Canvas(Modifier.matchParentSize()) {
            val k=knobOf(size.width,size.height)?:return@Canvas
            rim.reset();rim.addRoundRect(RoundRect(k.rect,CornerRadius(k.rect.minDimension/2)))
            val path=rim
            liquidGlassEdge(path,light,k.clear,dark,bounds=k.rect)
            // At rest iOS shows a solid white knob; it clears as the finger lifts the glass.
            val white=(1f-k.clear).coerceIn(0f,1f)
            if(white>.001f) {
                drawPath(path,Color.White.copy(alpha=white))
                drawPath(path,Color.Black.copy(alpha=.06f*white),style=Stroke(.6.dp.toPx()))
            }
        }
    }
}
