package io.atmos.weather.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Native Compose optical approximation for tool/navigation surfaces.
 * Edge glints, touch-directed internal light and accessible opaque-material mode.
 * No private Apple assets; no claim of GPU backdrop refraction.
 */
@Composable
fun NextOpticalSurface(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 28f,
    touchX: Float = .5f,
    pressed: Boolean = false,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val ctx=LocalContext.current
    val reducedMotion=ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    val opticalPhase=if(reducedMotion || reduceTransparency) 0.5f else {
        val transition=rememberInfiniteTransition(label="v25-control-light")
        val t by transition.animateFloat(initialValue=.36f,targetValue=.79f,
            animationSpec=infiniteRepeatable(tween(5200),RepeatMode.Reverse),
            label="v25-control-glimmer")
        t
    }
    val x = NextMotionTokens.clampedTouchX(touchX)
    val background = if (reduceTransparency) {
        Brush.verticalGradient(listOf(Color(0xFF263E51), Color(0xFF183044)))
    } else {
        Brush.verticalGradient(listOf(Color(0xC19EBED2), Color(0x9C506E86), Color(0xC22B4258)))
    }
    Box(modifier.clip(shape).background(background).drawWithContent {
        val w = size.width
        val h = size.height
        // A small, localized lens flare on touch; never blanket-flood content.
        if (!reduceTransparency) {
            val light = Offset(w * x, h * .08f)
            val radius = w.coerceAtLeast(h) * .82f
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (pressed) .31f else .08f + opticalPhase*.07f),
                        Color(0xFF9FDFF7).copy(alpha = if (pressed) .11f else .03f),
                        Color.Transparent
                    ), center = light, radius = radius
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusDp.dp.toPx())
            )
        }
        drawContent()
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (pressed) .84f else .48f),
                    Color.White.copy(alpha = .07f),
                    Color(0xFFBCEBFF).copy(alpha = .34f)
                ), start = Offset(0f, 0f), end = Offset(w, h)
            ),
            style = Stroke(width = 1.1.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusDp.dp.toPx())
        )
    }) { content() }
}
