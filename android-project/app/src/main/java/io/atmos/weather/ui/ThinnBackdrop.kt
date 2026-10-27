package io.atmos.weather.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Stable
class ThinnBackdrop internal constructor(internal val layer:GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var pixels=IntSize.Zero
    internal var ready=false
    internal var active=true
    internal var failed=false
}

val LocalThinnBackdrop=staticCompositionLocalOf<ThinnBackdrop?> {null}
// Structural protection survives deferred RenderNode recording.
private val LocalBackdropAncestors=staticCompositionLocalOf<Set<ThinnBackdrop>> {emptySet()}

@Composable fun rememberThinnBackdrop():ThinnBackdrop {
    val layer=rememberGraphicsLayer()
    val source=remember(layer) {ThinnBackdrop(layer)}
    DisposableEffect(source) {
        source.active=true
        onDispose {source.active=false;source.ready=false;source.pixels=IntSize.Zero}
    }
    return source
}

/** Descendants can sample independent scenes, but never their own capture. */
@Composable fun ThinnBackdropCapture(source:ThinnBackdrop,modifier:Modifier=Modifier,
    content:@Composable BoxScope.()->Unit) {
    val ancestors=LocalBackdropAncestors.current
    CompositionLocalProvider(LocalBackdropAncestors provides remember(ancestors,source) {ancestors+source}) {
        Box(modifier.thinnBackdropSource(source),content=content)
    }
}

@Composable
private fun Modifier.thinnBackdropSource(source:ThinnBackdrop):Modifier {
    val view=LocalView.current.rootView
    val context=LocalContext.current
    return onGloballyPositioned {
        val xy=IntArray(2);view.getLocationOnScreen(xy)
        source.origin=it.positionInWindow()+Offset(xy[0].toFloat(),xy[1].toFloat())
    }.drawWithContent {
        val pixels=IntSize(size.width.toInt(),size.height.toInt())
        if(source.active && !source.failed && BackdropSampling.canCapture(pixels.width,pixels.height)) {
            try {
                source.layer.record(size=pixels) {this@drawWithContent.drawContent()}
                source.pixels=pixels;source.ready=true
            } catch(error:RuntimeException) {
                source.failed=true;source.ready=false
                io.atmos.weather.CrashDiagnostics.graphicsFailure(context,"backdrop-capture",error)
            }
        } else source.ready=false
        // The foreground always follows the ordinary Compose drawing path.
        drawContent()
    }
}

/** Crop before filtering AND when painting; sampled pixels cannot cover siblings. */
@Composable
fun Modifier.thinnBackdrop(reduceTransparency:Boolean,cornerRadius:Dp=999.dp,focusX:Float=.5f):Modifier {
    val source=LocalThinnBackdrop.current
    if(reduceTransparency || source==null || source in LocalBackdropAncestors.current) return this
    val view=LocalView.current.rootView
    val context=LocalContext.current
    val glass=rememberGraphicsLayer()
    var origin by remember {mutableStateOf(Offset.Zero)}
    var visible by remember {mutableStateOf(Rect.Zero)}
    var failed by remember(source) {mutableStateOf(false)}
    val appearance=io.atmos.weather.presentation.home.LocalAppearance.current
    val radius=with(LocalDensity.current) {(4f+26f*appearance.glassFrost.coerceIn(0f,1f)).dp.toPx()}
    val corner=with(LocalDensity.current) {cornerRadius.toPx()}
    val optics=remember {if(Build.VERSION.SDK_INT>=33)try {LiquidOpticsEngine()} catch(_:RuntimeException) {null} else null}
    val effect=remember(radius) {
        val blur=RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP)
        val saturation=ColorMatrixColorFilter(ColorMatrix().apply {setSaturation(1.8f)})
        RenderEffect.createColorFilterEffect(saturation,blur)
    }
    return onGloballyPositioned {
        val xy=IntArray(2);view.getLocationOnScreen(xy)
        val position=it.positionInWindow()
        origin=position+Offset(xy[0].toFloat(),xy[1].toFloat())
        val bounds=it.boundsInWindow()
        visible=Rect(bounds.left-position.x,bounds.top-position.y,bounds.right-position.x,bounds.bottom-position.y)
    }.drawWithContent {
        val dx=source.origin.x-origin.x;val dy=source.origin.y-origin.y
        val sample=if(source.active && source.ready && !failed) BackdropSampling.plan(
            source.pixels.width,source.pixels.height,size.width,size.height,dx,dy,
            visible.left,visible.top,visible.right,visible.bottom) else null
        if(sample!=null) {
            try {
                glass.clip=true
                glass.renderEffect=if(Build.VERSION.SDK_INT>=33 && optics!=null)
                    optics.effect(sample.width.toFloat(),sample.height.toFloat(),corner,focusX,appearance.edgeLight,effect).asComposeRenderEffect()
                    else effect.asComposeRenderEffect()
                glass.record(size=IntSize(sample.width,sample.height)) {
                    clipRect {
                        translate(dx-sample.left,dy-sample.top) {drawLayer(source.layer)}
                    }
                }
                clipRect(sample.left,sample.top,sample.right,sample.bottom) {
                    translate(sample.left,sample.top) {drawLayer(glass)}
                }
            } catch(error:RuntimeException) {
                failed=true
                io.atmos.weather.CrashDiagnostics.graphicsFailure(context,"backdrop-sample",error)
            }
        }
        drawContent()
    }
}
