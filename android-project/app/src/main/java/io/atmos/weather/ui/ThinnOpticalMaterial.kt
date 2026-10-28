package io.atmos.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
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
    val page: Brush @Composable get() = Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.background,MaterialTheme.colorScheme.background))
    val surface: Brush @Composable get() = Brush.linearGradient(listOf(
        MaterialTheme.colorScheme.surface,MaterialTheme.colorScheme.surface))
    // Floating lens for the navigation/interactive layer.
    val raised: Brush get() = Brush.linearGradient(listOf(
        Color(0xD9F1F1F3), Color(0x8A8E8E93), Color(0xB02C2C2E), Color(0xB9B3B3B8)
    ))
    val opaque: Brush @Composable get() = Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.colorScheme.surface))
    val rim: Brush get() = Brush.linearGradient(listOf(
        Color.White.copy(alpha=.65f),Color.White.copy(alpha=.15f),Color.Transparent
    ))
    val luminousPill: Brush get() = Brush.linearGradient(listOf(
        Color(0xFFDBF4FF), Color(0xFF92D9FF), Color(0xFF55B5EF)
    ))
    val subtlePill: Brush @Composable get() = Brush.linearGradient(listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.92f),MaterialTheme.colorScheme.surface.copy(alpha=.96f)))
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
    val edgeRadius=radius.toPx().coerceAtMost(size.minDimension/2f)
    drawRoundRect(
        brush=Brush.linearGradient(
            0f to Color.White.copy(alpha=.65f),
            .5f to Color.White.copy(alpha=.15f),
            1f to Color.Transparent,
            start=Offset.Zero,end=Offset((size.width+size.height)/2f,(size.width+size.height)/2f)
        ),
        topLeft=Offset(inset,inset),
        size=Size((size.width-inset*2).coerceAtLeast(0f),(size.height-inset*2).coerceAtLeast(0f)),
        cornerRadius=CornerRadius(edgeRadius,edgeRadius),
        style=Stroke(width=1.dp.toPx())
    )
}
