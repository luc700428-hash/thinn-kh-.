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
    val light=GlassLight.current(follow=!reduceTransparency)
    Box(modifier.fillMaxWidth().weatherCollisionSurface(ThinnDesignTokens.radiusCard).clip(shape).thinnGlass(reduceTransparency,ThinnDesignTokens.radiusCard,
            cards?.fill ?: if(reduceTransparency) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,refract=false,frostScale=1.5f)
        .border(1.dp, ThinnOpticalMaterial.rim, shape)) {
        if(!reduceTransparency) Canvas(Modifier.matchParentSize()) {
            // The soft glow sits on the side the sun (or moon) is shining from.
            val center=Offset(size.width*(.5f+.36f*light.x), size.height*(.04f+.1f*(1f+light.y)))
            val radius=size.width * .76f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha=.11f),glow.copy(alpha=.035f),Color.Transparent),
                center=center,radius=radius),radius=radius,center=center)
            for(i in 0..2) {
                drawCircle(Color.White.copy(alpha=.065f-i*.014f),
                    radius=size.width*(.27f+i*.12f),center=Offset(center.x,size.height*.15f),
                    style=Stroke(.8.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxWidth(),content=content)
    }
}
