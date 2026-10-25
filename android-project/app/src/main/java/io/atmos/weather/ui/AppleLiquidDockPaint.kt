package io.atmos.weather.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * A purpose-built Compose equivalent of the requested four-cubic liquid selection.
 * Renders BEHIND tab icons and never intercepts selection or reorder gestures.
 * Source of truth remains NextReorderableToolDock.selectedId/onSelected/onOrderChanged.
 */
@Composable
fun BoxScope.AppleLiquidDockPaint(
    selectedIndex: Int,
    tabCount: Int,
    dragging: Boolean,
    dragOffsetPx: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    if (tabCount < 1) return
    val isLight=MaterialTheme.colorScheme.background.red > .5f
    var originIndex by remember { mutableIntStateOf(selectedIndex) }
    val slide by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = .68f, stiffness = 280f),
        label = "liquid dock spring 0.68 280"
    )
    LaunchedEffect(selectedIndex, reduceMotion) {
        if (!reduceMotion) delay(350L)
        originIndex = selectedIndex
    }
    Canvas(modifier.matchParentSize()) {
        // Dock content is inset by 6dp in the existing Row. Keep coordinates aligned.
        val inset = 6.dp.toPx()
        val step = ((size.width - 2f * inset) / tabCount).coerceAtLeast(1f)
        val originCenter = inset + (originIndex + .5f) * step
        val centerX = inset + (if (reduceMotion) selectedIndex.toFloat() else slide) * step + step / 2f +
            if (dragging && !reduceMotion) dragOffsetPx else 0f
        val centerY = size.height / 2f
        val width = (step - 8.dp.toPx()).coerceAtLeast(28.dp.toPx())
        val height = 56.dp.toPx()
        val halfW = width / 2f
        val halfH = height / 2f
        val deltaX = inset + (selectedIndex + .5f) * step - centerX
        // Surface-tension lag requested: 0.38*delta, saturated at 45dp.
        val lag = (deltaX * .38f).coerceIn(-45.dp.toPx(), 45.dp.toPx())
        val direction = when { deltaX > 1f -> 1f; deltaX < -1f -> -1f; else -> 0f }
        val stretch = (abs(lag) / 45.dp.toPx()).coerceIn(0f, 1f)
        val headH = halfH * (1f - .08f * stretch)
        val tailH = halfH * (1f - .15f * stretch)
        val left = centerX - halfW - if (direction > 0) abs(lag) else 0f
        val right = centerX + halfW + if (direction < 0) abs(lag) else 0f
        val frontX = if (direction >= 0) right else left
        val yTop = centerY - headH
        val yBottom = centerY + headH
        // Four cubic Bezier arcs. Their handles vary with the direction of travel,
        // unlike moving a fixed rounded rectangle across the dock.
        val contour = Path().apply {
            moveTo(centerX, yTop)
            cubicTo(centerX + (right-centerX)*.66f,yTop, right,centerY-headH*.58f,right,centerY)
            cubicTo(right,centerY+headH*.58f,centerX+(right-centerX)*.66f,yBottom,centerX,yBottom)
            cubicTo(centerX+(left-centerX)*.66f,centerY+tailH,left,centerY+tailH*.58f,left,centerY)
            cubicTo(left,centerY-tailH*.58f,centerX+(left-centerX)*.66f,centerY-tailH,centerX,yTop)
            close()
        }
        val glowColor = Color(0xFF5AC8FA)
        if (!reduceMotion) {
            // Android native blur here is deliberately limited to a tiny dock layer.
            drawIntoCanvas { canvas ->
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(46, 90, 200, 250)
                    maskFilter = BlurMaskFilter(18.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawOval(
                    centerX-halfW,centerY+halfH*.52f,centerX+halfW,centerY+halfH*1.25f,paint
                )
            }
        }
        if (!reduceMotion && originIndex != selectedIndex) {
            val distance = abs(centerX-originCenter)
            if (distance in 1f..50.dp.toPx()) {
                val narrow = (1f-distance/50.dp.toPx()) * 11.dp.toPx()
                val bridge = Path().apply {
                    moveTo(originCenter,centerY-narrow)
                    cubicTo(originCenter+distance*.3f,centerY-narrow*.35f,
                        centerX-distance*.3f,centerY-narrow*.35f,centerX,centerY-narrow)
                    lineTo(centerX,centerY+narrow)
                    cubicTo(centerX-distance*.3f,centerY+narrow*.35f,
                        originCenter+distance*.3f,centerY+narrow*.35f,originCenter,centerY+narrow)
                    close()
                }
                drawPath(bridge,Color(0x994CA9D1))
            }
        }
        drawPath(contour,
            brush = Brush.verticalGradient(if(isLight) listOf(
                Color(0xF5FFFFFF), Color(0xCCBFE7FF), Color(0xDAE8F6FF)) else listOf(
                Color(0xD6D5E9F5), Color(0xA97996AD), Color(0xB03B5366))),
            alpha = if (dragging) .93f else .81f)
        drawPath(contour,
            brush = Brush.sweepGradient(listOf(Color.White.copy(alpha=.85f),
                Color(0xFF5AC8FA).copy(alpha=.6f),Color.White.copy(alpha=.14f),
                Color.White.copy(alpha=.85f)),center=Offset(centerX,centerY)),
            style = Stroke(width=1.2.dp.toPx()))
        drawOval(
            brush=Brush.radialGradient(listOf(Color.White.copy(alpha=.32f),Color.Transparent),
                center=Offset(frontX,centerY-headH*.72f),radius=halfW),
            topLeft=Offset(centerX-halfW,centerY-halfH),size=Size(width,height))
    }
}
