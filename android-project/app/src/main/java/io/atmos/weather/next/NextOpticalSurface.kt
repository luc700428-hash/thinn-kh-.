package io.atmos.weather.next

import io.atmos.weather.ui.brandColor
import io.atmos.weather.ui.BrandPalette
import io.atmos.weather.ui.FluidGlassStyle

import io.atmos.weather.ui.thinnBackdrop
import io.atmos.weather.ui.thinnSpecularEdge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
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
 * Tool/navigation materials share edge glints and touch-directed light.
 * Backdrop-enabled surfaces sample an independent scene through thinnBackdrop;
 * Navigation uses a legible regular material with live blur and an opaque-accessibility mode.
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
    touchY:Float=.08f,
    darkBackdrop:Boolean?=null,
    clearGlass:Boolean=false,
    content: @Composable () -> Unit
) {
    val appearance=io.atmos.weather.presentation.home.LocalAppearance.current
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val lightTheme=!(darkBackdrop ?: (MaterialTheme.colorScheme.background.red < .5f))
    val reducedMotion=io.atmos.weather.ui.rememberAppleReduceMotion()
    // Background sampling stays live; idle controls do not run an unnecessary infinite animation.
    val opticalPhase by animateFloatAsState(if(pressed&&!reducedMotion&&!reduceTransparency)1f else .5f,
        if(reducedMotion)tween(0) else spring(.78f,360f),label="glass-contact-light")
    val x = NextMotionTokens.clampedTouchX(touchX)
    val nav=FluidGlassStyle.navigation(if(lightTheme)BrandPalette.light else BrandPalette.dark,!lightTheme)
    val glassTint=if(clearGlass)nav.underlay.alpha
        else if(calm)(if(lightTheme).22f else .38f)-.12f*appearance.glassOpacity.coerceIn(0f,1f)
        else (.62f-.30f*appearance.glassOpacity.coerceIn(0f,1f))
    val background = if (reduceTransparency) {
        if(lightTheme) Brush.verticalGradient(listOf(Color.White,brandColor(0xFFF7FAF7))) else Brush.verticalGradient(listOf(brandColor(0xFF1B1D1C),brandColor(0xFF1B1D1C)))
    } else if(clearGlass) {
        Brush.verticalGradient(listOf(nav.underlay,nav.underlay))
    } else if(lightTheme) {
        // Pale cool-tinted glass; no heavy dark blue fill on the white Settings page.
        Brush.verticalGradient(listOf(Color.White.copy(alpha=glassTint), Color.White.copy(alpha=glassTint), Color.White.copy(alpha=glassTint)))
    } else {
        Brush.verticalGradient(listOf(brandColor(0xFF1B1D1C).copy(alpha=glassTint), brandColor(0xFF1B1D1C).copy(alpha=glassTint), brandColor(0xFF1B1D1C).copy(alpha=glassTint)))
    }
    Box(modifier.clip(shape).thinnBackdrop(reduceTransparency || !useBackdrop,cornerRadiusDp.dp,x,
        frostScale=1f,refract=!clearGlass).background(background)
        .then(if(calm)Modifier else Modifier.thinnSpecularEdge(cornerRadiusDp.dp)).drawWithContent {
        val w = size.width
        val h = size.height
        // A small, localized lens flare on touch; never blanket-flood content.
        if (!reduceTransparency && (!calm || clearGlass)) {
            val light = Offset(w * x,h*touchY.coerceIn(0f,1f))
            val radius = w.coerceAtLeast(h) * .82f
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if(clearGlass) {if(pressed).055f else .018f} else if (pressed) .39f else .13f + opticalPhase*.08f),
                        brandColor(0xFFA9E1D1).copy(alpha = if(clearGlass).015f else if (pressed) .11f else .03f),
                        Color.Transparent
                    ), center = light, radius = radius
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusDp.dp.toPx())
            )
        }
        drawContent()
        if(clearGlass)drawRoundRect(nav.rim,style=Stroke(.65.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(cornerRadiusDp.dp.toPx()))
        else drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if(clearGlass) {if(pressed).32f else .22f} else if(calm) {if(pressed).22f else .12f} else if (pressed) .72f else .42f),
                    Color.White.copy(alpha = if(calm).03f else .13f),
                    Color.White.copy(alpha = if(calm) 0f else if(lightTheme) .56f else .24f)
                ), start = Offset(0f, 0f), end = Offset(w, h)
            ),
            style = Stroke(width = (if(calm).6f else 1.1f).dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusDp.dp.toPx())
        )
    }) { content() }
}
