package io.atmos.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared optical layers. Content rests on quiet, readable neutral surfaces while
 * navigation and controls carry the pronounced glass/lens treatment.
 * Independently drawn Android Compose material; not an iOS or Apple shader.
 */
object ThinnOpticalMaterial {
    val page: Brush get() = Brush.verticalGradient(listOf(
        Color(0xFF0A1524), Color(0xFF1B324B), Color(0xFF24455C), Color(0xFF101B2D)
    ))
    // Quiet content material: avoids a thick translucent rectangle around every paragraph.
    val surface: Brush get() = Brush.linearGradient(listOf(
        Color(0xED273746), Color(0xEF202D3B), Color(0xF01C2937)
    ))
    // Floating lens for the navigation/interactive layer.
    val raised: Brush get() = Brush.linearGradient(listOf(
        Color(0xCEDDECF7), Color(0xA48AAEC3), Color(0xB039637F), Color(0xCB86AEC4)
    ))
    val opaque: Brush get() = Brush.verticalGradient(listOf(
        Color(0xFF324D65), Color(0xFF152D44)
    ))
    val rim: Brush get() = Brush.linearGradient(listOf(
        Color.White.copy(alpha=.70f), Color(0xFFD8EFFF).copy(alpha=.24f),
        Color.White.copy(alpha=.09f), Color(0xFFD5F3FF).copy(alpha=.40f)
    ))
    val luminousPill: Brush get() = Brush.linearGradient(listOf(
        Color(0xFFDBF4FF), Color(0xFF92D9FF), Color(0xFF55B5EF)
    ))
    val subtlePill: Brush get() = Brush.linearGradient(listOf(
        Color.White.copy(alpha=.30f), Color.White.copy(alpha=.095f), Color.White.copy(alpha=.16f)
    ))
    val selectedLens: Brush get() = Brush.verticalGradient(listOf(
        Color.White.copy(alpha=.34f), Color(0xFFB5E7FF).copy(alpha=.21f),
        Color.White.copy(alpha=.09f)
    ))
}

/**
 * A restrained dynamic-looking specular edge. It must remain transparent over text
 * and must not be mistaken for true background sampling/refraction.
 */
fun Modifier.thinnSpecularEdge(radius: Dp = 28.dp): Modifier = drawWithContent {
    drawContent()
    val inset=1.5.dp.toPx()
    val edgeRadius=radius.toPx()
    drawRoundRect(
        brush=Brush.linearGradient(
            0f to Color.White.copy(alpha=.46f),
            .32f to Color.White.copy(alpha=.10f),
            .66f to Color.Transparent,
            1f to Color.White.copy(alpha=.18f)
        ),
        topLeft=Offset(inset,inset),
        size=Size((size.width-inset*2).coerceAtLeast(0f),(size.height-inset*2).coerceAtLeast(0f)),
        cornerRadius=CornerRadius(edgeRadius,edgeRadius),
        style=Stroke(width=1.dp.toPx())
    )
}
