package io.atmos.weather.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.liquidRefraction
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.atan2

/**
 * The scene behind a group of glass pieces. [ThinnBackdropCapture] records it once per frame;
 * every [thinnBackdrop] reads that recording through the Backdrop liquid-glass engine
 * (com.kyant.backdrop, Apache 2.0), which bends it at the rim like a thick lens, brightens its
 * colours and lights the edge.
 */
@Stable
class ThinnBackdrop internal constructor(internal val layer:GraphicsLayer):Backdrop {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var coordinates:LayoutCoordinates?=null
    internal var pixels=IntSize.Zero
    internal var ready=false
    internal var active=true
    internal var failed=false

    override val isCoordinatesDependent:Boolean=true

    override fun DrawScope.drawBackdrop(density:Density,coordinates:LayoutCoordinates?,layerBlock:(GraphicsLayerScope.()->Unit)?) {
        if(!active||!ready||failed)return
        val glass=coordinates?.takeIf{it.isAttached}?:return
        val scene=this@ThinnBackdrop.coordinates?.takeIf{it.isAttached}?:return
        val offset=try {scene.localPositionOf(glass,Offset.Zero)} catch(_:Exception) {
            glass.positionInWindow()-scene.positionInWindow()
        }
        translate(-offset.x,-offset.y) {drawLayer(layer)}
    }
}

val LocalThinnBackdrop=staticCompositionLocalOf<ThinnBackdrop?> {null}
// Structural protection survives deferred RenderNode recording.
private val LocalBackdropAncestors=staticCompositionLocalOf<Set<ThinnBackdrop>> {emptySet()}

@Composable fun rememberThinnBackdrop():ThinnBackdrop {
    val layer=rememberGraphicsLayer()
    val source=remember(layer) {ThinnBackdrop(layer)}
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {com.kyant.backdrop.SharedRuntimeShaders.prewarm()}
    }
    DisposableEffect(source) {
        source.active=true
        onDispose {source.active=false;source.ready=false;source.pixels=IntSize.Zero;source.coordinates=null}
    }
    return source
}

/** Descendants can sample independent scenes, but never their own capture. */
@Composable fun ThinnBackdropCapture(source:ThinnBackdrop,modifier:Modifier=Modifier,
    enabled:Boolean=true,content:@Composable BoxScope.()->Unit) {
    val ancestors=LocalBackdropAncestors.current
    val capture=enabled&&!LiquidGlassKit.softwareRenderedDevice
    val protectedAncestors=remember(ancestors,source,capture){if(capture)ancestors+source else ancestors}
    CompositionLocalProvider(LocalBackdropAncestors provides protectedAncestors) {
        Box(if(capture)modifier.thinnBackdropSource(source) else modifier,content=content)
    }
}

@Composable
private fun Modifier.thinnBackdropSource(source:ThinnBackdrop):Modifier {
    val view=LocalView.current.rootView
    val context=LocalContext.current
    return onGloballyPositioned {
        val xy=IntArray(2);view.getLocationOnScreen(xy)
        source.origin=it.positionInWindow()+Offset(xy[0].toFloat(),xy[1].toFloat())
        source.coordinates=it
    }.drawWithContent {
        val pixels=IntSize(size.width.toInt(),size.height.toInt())
        var recorded=false
        if(source.active && !source.failed && BackdropSampling.canCapture(pixels.width,pixels.height)) {
            try {
                source.layer.record(size=pixels) {this@drawWithContent.drawContent()}
                source.pixels=pixels;source.ready=true;recorded=true
            } catch(error:RuntimeException) {
                source.failed=true;source.ready=false
                io.atmos.weather.CrashDiagnostics.graphicsFailure(context,"backdrop-capture",error)
            }
        } else source.ready=false
        // Show the page by replaying the display list just recorded, instead of drawing the
        // whole page a second time. This halves the cost of every frame on glass screens.
        if(recorded)drawLayer(source.layer) else drawContent()
    }
}

/** True when [thinnBackdrop] here would really paint the scene behind (not a no-op). */
@Composable fun thinnBackdropAvailable(reduceTransparency:Boolean):Boolean {
    val source=LocalThinnBackdrop.current
    return !reduceTransparency && source!=null && source !in LocalBackdropAncestors.current && !LiquidGlassKit.softwareRenderedDevice &&
        GlassQuality.current()!=GlassQuality.Level.LIGHT
}

/** iOS-style tint over glass: see-through where real glass is drawn, solid where it is not. */
@Composable fun glassTint(color:Color,reduceTransparency:Boolean,clear:Float):Color =
    if(thinnBackdropAvailable(reduceTransparency))color.copy(alpha=minOf(color.alpha,clear)) else color

/**
 * Real liquid glass under whatever this modifier wraps: the scene behind, lightly blurred and made
 * more vivid, bent toward the centre along the rim (Android 13+), with a rim highlight facing the
 * sun or moon. [refract] = false keeps the lens shallower for large, scrolling surfaces.
 */
@Composable
fun Modifier.thinnBackdrop(reduceTransparency:Boolean,cornerRadius:Dp=999.dp,focusX:Float=.5f,frostScale:Float=1f,refract:Boolean=true,materialDark:Boolean?=null):Modifier {
    val source=LocalThinnBackdrop.current
    // Emulators render in software (SwiftShader): re-recording a sampled layer for every glass
    // piece on every frame exhausts its GPU buffers and takes the virtual device down. They get
    // the same translucent material without sampling; real phones keep the full backdrop.
    if(reduceTransparency || source==null || source in LocalBackdropAncestors.current || LiquidGlassKit.softwareRenderedDevice) return this
    // Phones that cannot keep up get tinted glass without sampling (see GlassQuality).
    val quality=GlassQuality.current()
    if(quality==GlassQuality.Level.LIGHT) return this
    val appearance=io.atmos.weather.presentation.home.LocalAppearance.current
    val frost=appearance.glassFrost.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.5f
    val edge=appearance.edgeLight.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.5f
    val scale=frostScale.takeIf{it.isFinite()}?.coerceIn(.1f,2f)?:1f
    val light=GlassLight.current(follow=true)
    val shape=remember(cornerRadius) {RoundedCornerShape(cornerRadius)}
    val shapeOf=remember(shape) {{shape}}
    val dark=materialDark?:liquidDark()
    val effects=remember(frost,scale,refract,materialDark,quality,dark) {
        // Liquid Glass material (github.com/zakisheriff/Liquid-Glass): saturate 1.9 / 1.6,
        // brightness 1.04 / .96 and a light blur (6 / 8 px at the default frost), then the
        // Snell's-law squircle bevel bends the scene inward at the rim.
        val blurDp=LiquidMaterial.blurDp(dark)*(.5f+frost)*scale
        val block:com.kyant.backdrop.BackdropEffectScope.()->Unit={
            colorControls(brightness=LiquidMaterial.brightness(dark),saturation=LiquidMaterial.saturation(dark))
            blur(blurDp.dp.toPx())
            val minor=size.minDimension
            // BALANCED: only small controls bend light; large cards keep blur and colour.
            val bends=quality==GlassQuality.Level.FULL||minor<120.dp.toPx()
            if(minor>4f&&bends) {
                val shape=LiquidShape.surface(size.width/density,size.height/density)
                liquidRefraction(lens=false,bezel=shape.bezel.dp.toPx(),thickness=shape.thickness.dp.toPx(),
                    strength=if(refract)1f else .6f)
            }
            if(materialDark!=null&&Build.VERSION.SDK_INT>=31) renderEffect?.let{renderEffect=GlassToneEffect.apply(it,materialDark)}
        }
        block
    }
    val highlight=remember {
        // The rim is the library's baked specular hairline, drawn by [liquidRim] below.
        val f:()->Highlight?={null}
        f
    }
    // RenderEffects are ignored on software canvases (bitmap snapshots); keep the material's
    // brightness bounds there with a colour filter so labels stay readable over any scene.
    val onDrawBackdrop=remember(materialDark) {
        val f:DrawScope.(DrawScope.()->Unit)->Unit={draw->
            val canvas=drawContext.canvas
            if(materialDark!=null&&!canvas.nativeCanvas.isHardwareAccelerated) {
                canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero,size),
                    androidx.compose.ui.graphics.Paint().apply{colorFilter=GlassToneEffect.softwareColorFilter(materialDark)})
                draw();canvas.restore()
            } else draw()
        }
        f
    }
    return drawBackdrop(backdrop=source,shape=shapeOf,effects=effects,highlight=highlight,shadow=null,onDrawBackdrop=onDrawBackdrop)
        .liquidRim(cornerRadius,light,dark,strength=.55f+.9f*edge)
}

/** A glass card or control: the glass, then a tint that stays light enough to see through. */
@Composable
fun Modifier.thinnGlass(reduceTransparency:Boolean,cornerRadius:Dp,tint:Color,clear:Float=.42f,
    refract:Boolean=true,frostScale:Float=1f):Modifier =
    thinnBackdrop(reduceTransparency,cornerRadius,frostScale=frostScale,refract=refract)
        .background(glassTint(tint,reduceTransparency,clear),RoundedCornerShape(cornerRadius))
