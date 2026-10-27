package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import kotlin.math.abs

private fun lensPath(g:BubbleSliderGeometry.Lens):Path {
    fun body(x:Float)=Path().apply {addRoundRect(RoundRect(x-g.width/2,g.y-g.height/2,
        x+g.width/2,g.y+g.height/2,CornerRadius(g.height/2)))}
    val front=body(g.head)
    if(g.distance<.25f)return front
    val left=minOf(g.head,g.tail);val right=maxOf(g.head,g.tail);val middle=(left+right)/2
    val shoulder=g.height*.38f;val neck=g.neck
    val bridge=Path().apply {
        moveTo(left,g.y-shoulder)
        cubicTo(middle,g.y-shoulder,middle,g.y-neck,right,g.y-shoulder)
        lineTo(right,g.y+shoulder)
        cubicTo(middle,g.y+neck,middle,g.y+shoulder,left,g.y+shoulder)
        close()
    }
    return Path.combine(PathOperation.Union,Path.combine(PathOperation.Union,front,body(g.tail)),bridge)
}

/** Single-canvas clear lens; drag rendering does not allocate or sample GPU layers. */
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
    val head by animateFloatAsState(fraction,if(dragging||reduce)tween(0) else spring(response.damping,response.stiffness),label="bubble head")
    val tail by animateFloatAsState(head,if(reduce)tween(0) else spring(.92f,95f),label="adhering water tail")
    val lift by animateFloatAsState(if(dragging&&!reduce)1f else 0f,if(reduce)tween(0) else spring(.62f,240f),label="bubble rebound")
    val dark=MaterialTheme.colorScheme.background.red<.5f
    val active=colors?.activeTrackColor?:MaterialTheme.colorScheme.primary
    val inactive=colors?.inactiveTrackColor?:if(dark)Color(0xFF5A5A60) else Color(0xFFC7C7CC)
    val solid=MaterialTheme.colorScheme.surface
    fun quantized(v:Float)=BubbleSliderGeometry.quantize(v,low,high,steps)
    Box(modifier.fillMaxWidth().heightIn(min=48.dp)
        .graphicsLayer {alpha=if(enabled)1f else .42f}
        .then(if(enabled)Modifier.pointerInput(low,high,steps) {
            val inset=(BubbleSliderGeometry.insetDp*density.density).coerceAtMost(size.width/2f)
            fun mapped(x:Float)=quantized(low+(high-low)*((x-inset)/(size.width-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f))
            detectTapGestures {p->change(mapped(p.x));feedback?.play(HapticEngine.Event.TAP);finish?.invoke()}
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
        Canvas(Modifier.matchParentSize()) {
                    val g=BubbleSliderGeometry.lens(size.width,size.height,density.density,head,tail,lift,reduce)?:return@Canvas
                    val inset=30.dp.toPx().coerceAtMost(size.width/2)
                    val end=size.width-inset
                    drawLine(inactive.copy(alpha=.60f),Offset(inset,g.y),Offset(end,g.y),4.dp.toPx(),StrokeCap.Round)
                    drawLine(active,Offset(inset,g.y),Offset(inset+(end-inset)*head.coerceIn(0f,1f),g.y),4.dp.toPx(),StrokeCap.Round)
                    val path=try {lensPath(g)} catch(_:RuntimeException) {Path().apply {addRoundRect(RoundRect(g.head-g.width/2,g.y-g.height/2,g.head+g.width/2,g.y+g.height/2,CornerRadius(g.height/2)))}}
                    // Refraction of the track is redrawn locally. No captured RenderNode,
                    // mutable RuntimeShader or changing clip layer survives a drag frame.
                    clipPath(path) {
                        drawLine(active.copy(alpha=.30f),Offset(inset,g.y+1.5.dp.toPx()),Offset(inset+(end-inset)*head.coerceIn(0f,1f),g.y+1.5.dp.toPx()),6.dp.toPx(),StrokeCap.Round)
                    }
                    // Clear body: the underlying track/background remain visible.
                    drawPath(path,if(opaque)solid else Color.White.copy(alpha=if(dark).065f else .035f))
                    val phase=head.coerceIn(0f,1f)*160f+lift*40f
                    val center=Offset((g.head+g.tail)/2,g.y)
                    val rainbow=(0..6).map {i->Color.hsv((phase+i*60f)%360f,.38f,.98f,if(i%2==0).82f else .48f)}
                    val rim=Brush.sweepGradient(rainbow,center)
                    drawPath(path,Brush.sweepGradient(rainbow.map{it.copy(alpha=.10f+lift*.045f)},center),style=Stroke(5.dp.toPx()))
                    drawPath(path,rim,style=Stroke(1.25.dp.toPx()))
                    drawPath(path,Brush.linearGradient(listOf(Color.White.copy(alpha=.82f),Color.Transparent,
                        Color.White.copy(alpha=.56f)),Offset(g.head-g.width/2,g.y-g.height/2),Offset(g.head+g.width/2,g.y+g.height/2)),style=Stroke(.65.dp.toPx()))
                    // Thin specular arc; no opaque blue fill or painted blue thumb.
                    drawArc(Color.White.copy(alpha=.50f),205f,85f,false,
                        Offset(g.head-g.width*.43f,g.y-g.height*.41f),Size(g.width*.86f,g.height*.82f),style=Stroke(.85.dp.toPx(),cap=StrokeCap.Round))
        }
    }
}
