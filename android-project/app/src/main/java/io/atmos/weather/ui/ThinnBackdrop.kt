package io.atmos.weather.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import android.os.Build

/** Capture the page once; the floating dock samples it without recording itself. */
@Stable
class ThinnBackdrop internal constructor(internal val layer:GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var ready by mutableStateOf(false)
    // Drawing a sampled descendant into the same layer would form a render-cycle.
    internal var recording=false
}
val LocalThinnBackdrop=staticCompositionLocalOf<ThinnBackdrop?> {null}
@Composable fun rememberThinnBackdrop():ThinnBackdrop {
    val layer=rememberGraphicsLayer()
    return remember(layer) {ThinnBackdrop(layer)}
}
@Composable
fun Modifier.thinnBackdropSource(source:ThinnBackdrop):Modifier {
    val view=LocalView.current.rootView
    return onGloballyPositioned {
        val xy=IntArray(2);view.getLocationOnScreen(xy)
        source.origin=it.positionInWindow()+Offset(xy[0].toFloat(),xy[1].toFloat())
    }.drawWithContent {
        source.recording=true
        try {source.layer.record {this@drawWithContent.drawContent()}}
        finally {source.recording=false}
        drawLayer(source.layer)
        if(!source.ready) source.ready=true
    }
}

/** Only the dock-sized layer is blurred. Icons/text are drawn afterwards, sharply. */
@Composable
fun Modifier.thinnBackdrop(reduceTransparency:Boolean,cornerRadius:Dp=999.dp,focusX:Float=.5f):Modifier {
    val source=LocalThinnBackdrop.current
    val view=LocalView.current.rootView
    val glass=rememberGraphicsLayer()
    var origin by remember {mutableStateOf(Offset.Zero)}
    val appearance=io.atmos.weather.presentation.home.LocalAppearance.current
    val radius=with(LocalDensity.current) {(4f+26f*appearance.glassFrost.coerceIn(0f,1f)).dp.toPx()}
    val corner=with(LocalDensity.current){cornerRadius.toPx()}
    val optics=remember {if(Build.VERSION.SDK_INT>=33)runCatching {LiquidOpticsEngine()}.getOrNull() else null}
    val effect=remember(radius) {
        val blur=RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP)
        val saturation=ColorMatrixColorFilter(ColorMatrix().apply {setSaturation(1.8f)})
        RenderEffect.createColorFilterEffect(saturation,blur)
    }
    if(reduceTransparency || source==null) return this
    return onGloballyPositioned {
        val xy=IntArray(2);view.getLocationOnScreen(xy)
        origin=it.positionInWindow()+Offset(xy[0].toFloat(),xy[1].toFloat())
    }.drawWithContent {
        if(source.ready && !source.recording) {
            glass.renderEffect=if(Build.VERSION.SDK_INT>=33 && optics!=null)
                optics.effect(size.width,size.height,corner,focusX,appearance.edgeLight,effect).asComposeRenderEffect()
                else effect.asComposeRenderEffect()
            glass.record {
                translate(source.origin.x-origin.x,source.origin.y-origin.y) {drawLayer(source.layer)}
            }
            drawLayer(glass)
        }
        drawContent()
    }
}
