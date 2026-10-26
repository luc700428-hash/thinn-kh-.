package io.atmos.weather.ui

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
 */
@Composable
fun ThinnAtmospherePanel(
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF8CD6F5),
    content: @Composable ColumnScope.() -> Unit
) {
    val ctx = LocalContext.current
    val reduceTransparency = remember(ctx) {
        ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
            .getBoolean("reduce_transparency", false)
    }
    val shape = RoundedCornerShape(ThinnDesignTokens.radiusCard)
    Box(modifier.fillMaxWidth().weatherCollisionSurface(ThinnDesignTokens.radiusCard).clip(shape).thinnBackdrop(reduceTransparency,ThinnDesignTokens.radiusCard)
        .background(if(reduceTransparency) ThinnOpticalMaterial.opaque else ThinnOpticalMaterial.surface)
        .border(1.dp, ThinnOpticalMaterial.rim, shape)) {
        if(!reduceTransparency) Canvas(Modifier.matchParentSize()) {
            val center=Offset(size.width * .84f, size.height * .04f)
            val radius=size.width * .76f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=.19f),accent.copy(alpha=.055f),Color.Transparent),
                center=center,radius=radius),radius=radius,center=center)
            for(i in 0..2) {
                drawCircle(Color.White.copy(alpha=.065f-i*.014f),
                    radius=size.width*(.27f+i*.12f),center=Offset(size.width*.82f,size.height*.15f),
                    style=Stroke(.8.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxWidth(),content=content)
    }
}
