package io.atmos.weather.ui

import androidx.compose.material3.MaterialTheme
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Calm atmospheric content surface shared by Home and Seismic.
 * Glass-like highlights are decorative only; text remains on an opaque-enough surface.
 * This is original Compose rendering, not Apple's proprietary Liquid Glass shader.
 * Large panels blur what is behind them but skip the lens shader: on a card-sized surface the
 * bending is barely visible, while running it for every card on every frame made scrolling stutter.
 */
@Composable
fun ThinnAtmospherePanel(
    modifier: Modifier = Modifier,
    accent: Color = brandColor(0xFF94D9C6),
    content: @Composable ColumnScope.() -> Unit
) {
    val ctx = LocalContext.current
    val reduceTransparency = remember(ctx) {
        ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
            .getBoolean("reduce_transparency", false)
    }
    val shape = RoundedCornerShape(ThinnDesignTokens.radiusCard)
    val cards=LocalWeatherCardPalette.current
    val glow=cards?.accent?:accent
    val dark=MaterialTheme.colorScheme.background.red<.5f
    // The weather page's cards are frosted glass only: blur, a calm tint and a hairline edge.
    // No lens, no moving rim light and no rings, so the scene behind reads as soft and still.
    Box(modifier.fillMaxWidth().weatherCollisionSurface(ThinnDesignTokens.radiusCard).clip(shape).thinnGlass(reduceTransparency,ThinnDesignTokens.radiusCard,
            cards?.fill ?: if(reduceTransparency) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,refract=false,frostScale=1.5f,liquid=false)
        .border(.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha=if(dark).20f else .55f),Color.White.copy(alpha=if(dark).06f else .18f))), shape)) {
        if(!reduceTransparency) Canvas(Modifier.matchParentSize()) {
            // A still wash of the card's colour from the top, like light through frosted glass.
            drawRect(Brush.verticalGradient(listOf(glow.copy(alpha=.08f),Color.Transparent),endY=size.height*.45f))
        }
        Column(Modifier.fillMaxWidth(),content=content)
    }
}
