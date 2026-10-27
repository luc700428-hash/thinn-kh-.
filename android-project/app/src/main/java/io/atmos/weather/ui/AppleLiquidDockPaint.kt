package io.atmos.weather.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.DockLiquidGeometry
import kotlin.math.abs

/** Equal-sized selection lenses adhere through one neck. Hit targets stay fixed. */
@Composable
fun BoxScope.AppleLiquidDockPaint(
    selectedIndex:Int,tabCount:Int,dragging:Boolean,dragOffsetPx:Float,
    reduceMotion:Boolean,modifier:Modifier=Modifier
) {
    if(tabCount<1)return
    val density=LocalDensity.current.density
    var widthPx by remember {mutableIntStateOf(0)}
    val inset=6f*density
    val step=((widthPx-2*inset)/tabCount).coerceAtLeast(1f)
    val target=(selectedIndex.toFloat()+if(dragging)dragOffsetPx/step else 0f).coerceIn(0f,tabCount-1f)
    val location by animateFloatAsState(target,
        animationSpec=if(dragging || reduceMotion)snap() else spring(dampingRatio=.82f,stiffness=280f),
        label="dock-continuous-position")
    val tail by animateFloatAsState(location,animationSpec=if(reduceMotion)snap()
        else spring(dampingRatio=.78f,stiffness=110f),label="dock-equal-liquid-tail")
    val light=MaterialTheme.colorScheme.background.red>.5f
    Canvas(modifier.matchParentSize().onSizeChanged {widthPx=it.width}) {
        val shape=DockLiquidGeometry.shape(size.width,size.height,density,tabCount,location,
            if(reduceMotion)location else tail) ?: return@Canvas
        val w=shape.width;val h=shape.height;val y=shape.centerY
        val x=shape.headX;val tx=shape.tailX
        fun body(center:Float)=Path().apply {
            addRoundRect(RoundRect(Rect(center-w/2,y-h/2,center+w/2,y+h/2),CornerRadius(minOf(w,h)/2)))
        }
        var liquid=body(x)
        if(!reduceMotion && abs(tx-x)>1.dp.toPx()) {
            liquid=Path.combine(PathOperation.Union,liquid,body(tx))
            if(shape.connected) {
                val left=minOf(x,tx);val right=maxOf(x,tx);val d=right-left
                val r=h/2;val mid=(left+right)/2;val neck=shape.neckHalfHeight
                val bridge=Path().apply {
                    moveTo(left,y-r)
                    cubicTo(left+d*.28f,y-r,mid-d*.12f,y-neck,mid,y-neck)
                    cubicTo(mid+d*.12f,y-neck,right-d*.28f,y-r,right,y-r)
                    lineTo(right,y+r)
                    cubicTo(right-d*.28f,y+r,mid+d*.12f,y+neck,mid,y+neck)
                    cubicTo(mid-d*.12f,y+neck,left+d*.28f,y+r,left,y+r);close()
                }
                liquid=Path.combine(PathOperation.Union,liquid,bridge)
            }
        }
        clipRect {
            drawPath(liquid,if(light)Color(0xFFE1E1E8).copy(alpha=.66f) else Color(0xFF9A9AA0).copy(alpha=.32f))
            drawPath(liquid,Brush.linearGradient(listOf(Color.White.copy(alpha=if(light).45f else .17f),
                Color.Transparent,Color.White.copy(alpha=.07f)),Offset(x-w/2,y-h/2),Offset(x+w/2,y+h/2)))
            drawPath(liquid,Brush.linearGradient(listOf(Color.White.copy(alpha=.65f),
                Color.White.copy(alpha=.15f),Color.Transparent),Offset(x-w/2,y-h/2),Offset(x+w/2,y+h/2)),style=Stroke(.65.dp.toPx()))
        }
    }
}
