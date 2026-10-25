package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/** Drag is direct. Release continues from that exact position into a quiet capsule lens. */
@Composable
fun BoxScope.AppleLiquidDockPaint(
    selectedIndex:Int,tabCount:Int,dragging:Boolean,dragOffsetPx:Float,
    reduceMotion:Boolean,modifier:Modifier=Modifier
) {
    if(tabCount<1)return
    val density=LocalDensity.current
    var widthPx by remember {mutableIntStateOf(0)}
    val inset=with(density){6.dp.toPx()}
    val step=((widthPx-2*inset)/tabCount).coerceAtLeast(1f)
    val location by animateFloatAsState(
        selectedIndex.toFloat()+if(dragging)dragOffsetPx/step else 0f,
        animationSpec=if(dragging || reduceMotion)snap() else tween(380,easing=AppleMotion.snap),
        label="dock-continuous-position")
    val lift by animateFloatAsState(if(dragging && !reduceMotion)1.04f else 1f,
        tween(if(reduceMotion)0 else 180,easing=AppleMotion.snap),label="dock-lens-lift")
    val light=MaterialTheme.colorScheme.background.red>.5f
    Canvas(modifier.matchParentSize().onSizeChanged {widthPx=it.width}) {
        val slot=(size.width-2*inset)/tabCount
        val w=(slot-8.dp.toPx()).coerceAtLeast(1f)*lift
        val h=56.dp.toPx()
        val x=inset+(location+.5f)*slot
        val topLeft=Offset(x-w/2,(size.height-h)/2)
        val bounds=Size(w,h)
        val radius=CornerRadius(h/2,h/2)
        drawRoundRect(if(light)Color(0xFFE1E1E8).copy(alpha=.80f)
            else Color(0xFF636366).copy(alpha=.48f),topLeft,bounds,radius)
        drawRoundRect(Brush.linearGradient(
            listOf(Color.White.copy(alpha=if(light).50f else .14f),Color.Transparent),
            start=topLeft,end=topLeft+Offset(w,h)),topLeft,bounds,radius)
        drawRoundRect(Brush.linearGradient(
            listOf(Color.White.copy(alpha=.65f),Color.White.copy(alpha=.15f),Color.Transparent),
            start=topLeft,end=topLeft+Offset(w,h)),topLeft,bounds,radius,style=Stroke(.65.dp.toPx()))
    }
}
