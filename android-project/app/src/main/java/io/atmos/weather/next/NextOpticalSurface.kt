package io.atmos.weather.next

import io.atmos.weather.ui.thinnBackdrop
import io.atmos.weather.ui.thinnSpecularEdge

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
import androidx.compose.material3.MaterialTheme
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
    useBackdrop: Boolean = false,
    calm: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val lightTheme=MaterialTheme.colorScheme.background.red > .5f
    val ctx=LocalContext.current
    val reducedMotion=ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    val opticalPhase=if(reducedMotion || reduceTransparency || calm) 0.5f else {
        val transition=rememberInfiniteTransition(label="v25-control-light")
        val t by transition.animateFloat(initialValue=.36f,targetValue=.79f,
            animationSpec=infiniteRepeatable(tween(5200),RepeatMode.Reverse),
            label="v25-control-glimmer")
        t
    }
    val x = NextMotionTokens.clampedTouchX(touchX)
    val background = if (reduceTransparency) {
        if(lightTheme) Brush.verticalGradient(listOf(Color.White,Color(0xFFF2F2F7))) else Brush.verticalGradient(listOf(Color(0xFF1C1C1E),Color(0xFF1C1C1E)))
    } else if(lightTheme) {
        // Pale cool-tinted glass; no heavy dark blue fill on the white Settings page.
        Brush.verticalGradient(listOf(Color(0xA6FFFFFF), Color(0xA6FFFFFF), Color(0xA6FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xA61C1C1E), Color(0xA61C1C1E), Color(0xA61C1C1E)))
    }
    Box(modifier.clip(shape).thinnBackdrop(reduceTransparency || !useBackdrop).background(background)
        .thinnSpecularEdge(cornerRadiusDp.dp).drawWithContent {
        val w = size.width
        val h = size.height
        // A small, localized lens flare on touch; never blanket-flood content.
        if (!reduceTransparency && !calm) {
            val light = Offset(w * x, h * .08f)
            val radius = w.coerceAtLeast(h) * .82f
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (pressed) .39f else .13f + opticalPhase*.08f),
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
                    Color.White.copy(alpha = if (pressed) .72f else .42f),
                    Color.White.copy(alpha = .13f),
                    Color.White.copy(alpha = if(calm) 0f else if(lightTheme) .56f else .24f)
                ), start = Offset(0f, 0f), end = Offset(w, h)
            ),
            style = Stroke(width = (if(calm).6f else 1.1f).dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusDp.dp.toPx())
        )
    }) { content() }
}
