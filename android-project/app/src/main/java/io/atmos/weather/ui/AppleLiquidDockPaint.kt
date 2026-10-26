package io.atmos.weather.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/** Direct finger lens + an inertial tail; their union forms a thinning liquid neck.
 * Only this paint moves. The dock container and all hit targets remain fixed.
 */
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
    val target=selectedIndex.toFloat()+if(dragging)dragOffsetPx/step else 0f
    val location by animateFloatAsState(target,
        animationSpec=if(dragging || reduceMotion)snap() else tween(380,easing=AppleMotion.snap),
        label="dock-continuous-position")
    // A second, slower body inherits motion instead of duplicating a static selected pill.
    var adhesionAnchor by remember {mutableFloatStateOf(selectedIndex.toFloat())}
    LaunchedEffect(dragging,location) {
        if(!dragging)adhesionAnchor=selectedIndex.toFloat()
        else if(kotlin.math.abs(location-adhesionAnchor)>1.12f)
            adhesionAnchor+=(if(location>adhesionAnchor)1f else -1f)
    }
    val tail by animateFloatAsState(if(dragging)adhesionAnchor else location,animationSpec=if(reduceMotion)snap()
        else spring(dampingRatio=.72f,stiffness=100f),label="dock-liquid-tail")
    val lift by animateFloatAsState(if(dragging && !reduceMotion)1.04f else 1f,
        tween(if(reduceMotion)0 else 180,easing=AppleMotion.snap),label="dock-lens-lift")
    val light=MaterialTheme.colorScheme.background.red>.5f
    Canvas(modifier.matchParentSize().onSizeChanged {widthPx=it.width}) {
        val slot=(size.width-2*inset)/tabCount
        val w=(slot-8.dp.toPx()).coerceAtLeast(1f)*lift
        val h=56.dp.toPx();val y=size.height/2
        val x=inset+(location+.5f)*slot
        val lag=((tail-location)*slot).coerceIn(-slot*1.35f,slot*1.35f)
        val distance=abs(lag)
        val pull=(distance/slot).coerceIn(0f,1.35f)
        val tailRadius=h*.46f*(1f-pull*.35f).coerceIn(.12f,1f)
        val head=Path().apply {addRoundRect(RoundRect(Rect(x-w/2,y-h/2,x+w/2,y+h/2),CornerRadius(h/2)))}
        var liquid=head
        if(!reduceMotion && distance>1.dp.toPx()) {
            val tx=x+lag
            val rear=Path().apply {addOval(Rect(tx-tailRadius,y-tailRadius,tx+tailRadius,y+tailRadius))}
            liquid=Path.combine(PathOperation.Union,liquid,rear)
            // The neck narrows under tension and detaches at excessive speed; both bodies rejoin.
            val adhesion=(1f-(pull-.94f).coerceAtLeast(0f)/.40f).coerceIn(0f,1f)
            if(adhesion>.01f) {
                val left=min(x,tx);val right=maxOf(x,tx);val d=right-left
                val r1=if(tx<x)tailRadius else h*.47f
                val r2=if(tx<x)h*.47f else tailRadius
                val mid=(left+right)/2;val neck=min(r1,r2)*(.72f-pull*.38f).coerceAtLeast(.10f)*adhesion
                val bridge=Path().apply {
                    moveTo(left,y-r1)
                    cubicTo(left+d*.28f,y-r1,mid-d*.12f,y-neck,mid,y-neck)
                    cubicTo(mid+d*.12f,y-neck,right-d*.28f,y-r2,right,y-r2)
                    lineTo(right,y+r2)
                    cubicTo(right-d*.28f,y+r2,mid+d*.12f,y+neck,mid,y+neck)
                    cubicTo(mid-d*.12f,y+neck,left+d*.28f,y+r1,left,y+r1);close()
                }
                liquid=Path.combine(PathOperation.Union,liquid,bridge)
            }
        }
        drawPath(liquid,if(light)Color(0xFFE1E1E8).copy(alpha=.66f) else Color(0xFF9A9AA0).copy(alpha=.32f))
        drawPath(liquid,Brush.linearGradient(listOf(Color.White.copy(alpha=if(light).45f else .17f),
            Color.Transparent,Color.White.copy(alpha=.07f)),Offset(x-w/2,y-h/2),Offset(x+w/2,y+h/2)))
        drawPath(liquid,Brush.linearGradient(listOf(Color.White.copy(alpha=.65f),
            Color.White.copy(alpha=.15f),Color.Transparent),Offset(x-w/2,y-h/2),Offset(x+w/2,y+h/2)),style=Stroke(.65.dp.toPx()))
    }
}
