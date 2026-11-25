package io.atmos.weather.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
        Color(0xD9F1F1F3), Color(0x8A8E8E93), brandColor(0xB02B2D2C), Color(0xB9B3B3B8)
    ))
    val opaque: Brush @Composable get() = Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.colorScheme.surface))
    val rim: Brush get() = Brush.linearGradient(listOf(
        Color.White.copy(alpha=.65f),Color.White.copy(alpha=.15f),Color.Transparent
    ))
    val luminousPill: Brush get() = Brush.linearGradient(listOf(
        brandColor(0xFFE0F4EF), brandColor(0xFF9DDDCB), brandColor(0xFF49BF9E)
    ))
    val subtlePill: Brush @Composable get() = Brush.linearGradient(listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.72f),MaterialTheme.colorScheme.surface.copy(alpha=.76f)))
    val selectedLens: Brush get() = Brush.verticalGradient(listOf(
        Color.White.copy(alpha=.34f), brandColor(0xFFBFE8DD).copy(alpha=.21f),
        Color.White.copy(alpha=.09f)
    ))
}

/**
 * The Liquid Glass specular rim (github.com/zakisheriff/Liquid-Glass): a hairline just inside the
 * edge, brightest on the corner facing the sun or moon and again on the opposite corner (light
 * reflected inside the glass), with a faint glow fading inward. Transparent over text.
 */
fun Modifier.thinnSpecularEdge(radius: Dp = 28.dp): Modifier = composed {
    val light = GlassLight.current(follow = true)
    val dark = MaterialTheme.colorScheme.background.red < .5f
    liquidRim(radius, light, dark)
}
