package io.atmos.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * The Liquid Glass engine of Zaki Sheriff's @zakisheriff/liquid-glass (MIT License,
 * https://github.com/zakisheriff/Liquid-Glass), ported from React/CSS/SVG to Compose:
 *  - [LiquidSprings]: its springs, tuned by eye against iOS 26 screen recordings;
 *  - [rubberband]: iOS overscroll banding;
 *  - [LiquidShape]: how deep and wide the bevel of a surface or a lens is;
 *  - [LiquidRim]: its baked specular rim (a hairline, hottest on both diagonal corners, an
 *    iridescent edge on lenses), drawn from an AGSL shader;
 *  - [LiquidMaterial]: its light and dark material tokens;
 *  - [liquidPress]: its touch response (swell, lean and stretch toward a drag, jelly release).
 * The refraction itself lives in com.kyant.backdrop.effects.liquidRefraction.
 */
internal object LiquidSprings {
    /** Apple-style (response, dampingFraction) to a Compose spring (mass 1, k = (2π/response)²). */
    fun <T> from(response: Float, dampingFraction: Float): SpringSpec<T> {
        val w = 2f * PI.toFloat() / response
        return spring(dampingRatio = dampingFraction, stiffness = w * w)
    }
    /** Finger down: quick, almost no overshoot. */
    fun <T> press() = from<T>(.22f, .86f)
    /** Finger up: soft settle with the gentle jelly wobble. */
    fun <T> release() = from<T>(.42f, .5f)
    /** Following the finger during a drag. */
    fun <T> drag() = from<T>(.12f, .92f)
    /** Sliding into a new slot after release. */
    fun <T> snap() = from<T>(.36f, .72f)
    /** A lens inflating out of a selection pill. */
    fun <T> lift() = from<T>(.26f, .78f)
    /** A lens deflating back into a pill: visibly wobbly. */
    fun <T> settle() = from<T>(.45f, .46f)
    /** Colour cross-fades. */
    fun <T> fade() = from<T>(.3f, 1f)
    /** A dragged lens's stretch: loose, so it wobbles when the drag stops. */
    fun <T> jelly() = from<T>(.3f, .3f)
    /** Finger-following feel: quick but never twitchy. */
    fun <T> follow() = from<T>(.2f, .82f)
    /** A switch knob dropping back: a short, gentle settle. */
    fun <T> knobSettle() = from<T>(.36f, .68f)
    /** A switch track's colour: even, about a third of a second. */
    fun <T> tint() = from<T>(.5f, 1f)
    /** A dragged tab or segment lens: looser and further than the default jelly. */
    fun <T> wobble() = from<T>(.3f, .24f)
}

/** iOS-style rubber banding: [offset] past an edge, eased so it never runs away. */
internal fun rubberband(offset: Float, dimension: Float, constant: Float = .55f): Float {
    if (dimension <= 0f) return 0f
    val x = abs(offset)
    return sign(offset) * (1f - 1f / (x * constant / dimension + 1f)) * dimension
}

/** Speed-driven stretch for a moving droplet: grows with speed (saturating), springs back past round. */
internal fun jellyTarget(velocity: Float, reach: Float, speed: Float): Float =
    reach * kotlin.math.tanh(abs(velocity) / speed.coerceAtLeast(1e-3f))

/** Bevel geometry in dp, resolved from the glass's own size exactly as the library does. */
internal data class LiquidShape(val lens: Boolean, val bezel: Float, val thickness: Float, val zoom: Float, val dispersion: Float) {
    companion object {
        fun surface(widthDp: Float, heightDp: Float, intensity: Float = 5f): LiquidShape {
            val short = min(widthDp, heightDp)
            return LiquidShape(false, (short * .2f).coerceIn(5f, 16f), (short * .32f).coerceIn(8f, 26f) * intensity / 5f, 1f, 0f)
        }
        fun lens(widthDp: Float, heightDp: Float, intensity: Float = 5f, zoom: Float = 1.16f, dispersion: Float = 1f): LiquidShape {
            val short = min(widthDp, heightDp)
            return LiquidShape(true, short * .32f, short * .55f * intensity / 5f, zoom, dispersion)
        }
    }
}

/** The library's material tokens. */
internal object LiquidMaterial {
    fun fill(dark: Boolean) = if (dark) Color(44, 44, 48).copy(alpha = .5f) else Color.White.copy(alpha = .66f)
    fun fillPressed(dark: Boolean) = if (dark) Color(90, 90, 96).copy(alpha = .55f) else Color.White.copy(alpha = .92f)
    fun edge(dark: Boolean) = if (dark) Color.White.copy(alpha = .09f) else Color.Black.copy(alpha = .06f)
    fun glow(dark: Boolean) = if (dark) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .55f)
    fun rimOpacity(dark: Boolean) = if (dark) .62f else 1f
    fun blurDp(dark: Boolean) = if (dark) 8f else 6f
    fun saturation(dark: Boolean) = if (dark) 1.6f else 1.9f
    /** Additive approximation of CSS brightness(1.04) / brightness(.96). */
    fun brightness(dark: Boolean) = if (dark) -.02f else .02f
    fun lensFill(dark: Boolean) = if (dark) Color(28, 28, 30).copy(alpha = .3f) else Color.White.copy(alpha = .1f)
    fun lensRing(dark: Boolean) = if (dark) Color.White.copy(alpha = .3f) else Color.Black.copy(alpha = .22f)
    fun track(dark: Boolean) = Color(120, 120, 128).copy(alpha = if (dark) .44f else .26f)
    fun rail(dark: Boolean) = Color(120, 120, 128).copy(alpha = if (dark) .4f else .2f)
    fun segmentSelection(dark: Boolean) = if (dark) Color.White.copy(alpha = .14f) else Color.Black.copy(alpha = .08f)
    fun selectionLensFill(dark: Boolean) = Color.White.copy(alpha = if (dark) .09f else .14f)
    fun selectionLensEdge(dark: Boolean) = if (dark) Color.White.copy(alpha = .25f) else Color(45, 48, 53).copy(alpha = .28f)
    fun panelFill(dark: Boolean) = if (dark) Color(30, 30, 33).copy(alpha = .62f) else Color.White.copy(alpha = .62f)
    fun menuFill(dark: Boolean) = if (dark) Color(38, 38, 42).copy(alpha = .94f) else Color(250, 250, 252).copy(alpha = .94f)
    fun green(dark: Boolean) = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    fun red(dark: Boolean) = if (dark) Color(0xFFFF4245) else Color(0xFFFF383C)
}

/**
 * The library's specular rim: a hairline just inside the edge plus a faint inner glow, lit from
 * the light (both diagonal corners catch it: direct and internally reflected), iridescent on a
 * lens. On software canvases (bitmap snapshots, tests) a painted equivalent is used.
 */
internal object LiquidRim {
    private var shader: android.graphics.RuntimeShader? = null
    private var broken = false
    private val matrix = android.graphics.Matrix()

    @androidx.annotation.RequiresApi(33)
    private fun obtain(): android.graphics.RuntimeShader? {
        if (broken || android.os.Build.VERSION.SDK_INT < 33 || LiquidGlassKit.softwareRenderedDevice) return null
        shader?.let { return it }
        return try { android.graphics.RuntimeShader(SOURCE).also { shader = it } } catch (_: RuntimeException) { broken = true; null }
    }

    /**
     * Draws the rim of the rounded rect [bounds] with corner [corner]. [bezelPx] is the shape's
     * bevel width, [light] points toward the light, [opacity] scales the whole rim.
     */
    fun draw(scope: DrawScope, bounds: Rect, corner: Float, bezelPx: Float, light: GlassLight.Light,
             lens: Boolean, dispersion: Float, opacity: Float) = with(scope) {
        if (bounds.width < 2f || bounds.height < 2f || opacity <= .001f) return@with
        val r = corner.coerceIn(0f, bounds.minDimension / 2f)
        if (android.os.Build.VERSION.SDK_INT >= 33 && drawContext.canvas.nativeCanvas.isHardwareAccelerated &&
            shade(this, bounds, r, bezelPx, light, lens, dispersion, opacity)) return@with
        // Painted rim: a bright hairline facing the light, a dimmer one on the opposite corner.
        paint(this, bounds, r, light, lens, opacity)
    }

    @androidx.annotation.RequiresApi(33)
    private fun shade(scope: DrawScope, bounds: Rect, r: Float, bezelPx: Float, light: GlassLight.Light,
                      lens: Boolean, dispersion: Float, opacity: Float): Boolean = with(scope) {
        val s = obtain() ?: return false
            try {
                s.setFloatUniform("size", bounds.width, bounds.height)
                s.setFloatUniform("radius", r)
                s.setFloatUniform("bezel", bezelPx.coerceAtLeast(1f))
                s.setFloatUniform("pixel", 1f / density)
                val l = hypot(light.x, light.y).coerceAtLeast(1e-4f)
                s.setFloatUniform("light", light.x / l, light.y / l)
                s.setFloatUniform("lens", if (lens) 1f else 0f)
                s.setFloatUniform("dispersion", dispersion.coerceIn(0f, 1f))
                s.setFloatUniform("opacity", opacity.coerceIn(0f, 1.3f))
                matrix.setTranslate(bounds.left, bounds.top); s.setLocalMatrix(matrix)
                drawRoundRect(ShaderBrush(s), bounds.topLeft, bounds.size, CornerRadius(r))
                true
            } catch (_: RuntimeException) { broken = true; false }
    }

    private fun paint(scope: DrawScope, bounds: Rect, r: Float, light: GlassLight.Light, lens: Boolean, opacity: Float) = with(scope) {
        val c = bounds.center
        val reach = (bounds.width + bounds.height) / 4f
        val toward = Offset(c.x + light.x * reach, c.y + light.y * reach)
        val away = Offset(c.x - light.x * reach, c.y - light.y * reach)
        val a = opacity.coerceIn(0f, 1f) * (if (lens) .95f else .7f)
        val inset = .55f * density
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset),
                CornerRadius((r - inset).coerceAtLeast(0f))))
        }
        drawPath(path, Brush.linearGradient(0f to Color.White.copy(alpha = a), .35f to Color.White.copy(alpha = a * .22f),
            .65f to Color.White.copy(alpha = a * .22f), 1f to Color.White.copy(alpha = a * .62f), start = toward, end = away),
            style = Stroke(1f * density))
    }

    const val SOURCE = """
        uniform float2 size;
        uniform float radius;
        uniform float bezel;
        uniform float pixel;
        uniform float2 light;
        uniform float lens;
        uniform float dispersion;
        uniform float opacity;
        half4 main(float2 coord) {
            float2 h = size * 0.5;
            float2 p = coord - h;
            float2 q = abs(p) - (h - float2(radius));
            float d = -(length(max(q, 0.0)) - radius + min(max(q.x, q.y), 0.0));
            if (d > bezel * 1.7 + 2.0) return half4(0.0);
            float2 n = (q.x >= 0.0 && q.y >= 0.0) ? sign(p) * normalize(q) : (q.x > q.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y)));
            // Work in the library's CSS pixels (dp) so the hairline keeps its width on every screen.
            float dc = d * pixel;
            float bezelC = bezel * pixel;
            float facing = dot(n, light);
            float lit = 0.22 + 0.78 * pow(abs(facing), 2.2) * (facing > 0.0 ? 1.0 : 0.62);
            float hair = max(0.55, pixel * 0.75);
            float line = exp(-((dc - hair) / hair) * ((dc - hair) / hair));
            float inner = exp(-(dc / (bezelC * 0.55)) * (dc / (bezelC * 0.55))) * 0.14;
            float edgeAA = clamp(d + 1.0, 0.0, 1.0);
            float a = clamp((line * (lens > 0.5 ? 0.95 : 0.7) + inner) * lit * edgeAA, 0.0, 1.0) * opacity;
            float3 c = float3(1.0);
            if (lens > 0.5 && dispersion > 0.0) {
                float hue = atan(n.y, n.x);
                float k = 0.14 * dispersion;
                c = float3(1.0 - k + k * (0.5 + 0.5 * cos(hue)), 1.0 - k + k * (0.5 + 0.5 * cos(hue - 2.1)), 1.0 - k + k * (0.5 + 0.5 * cos(hue + 2.1)));
            }
            return half4(half3(c * a), half(a));
        }
    """
}

/** Draws the library rim over this element (rounded rect of [corner]); a surface by default. */
internal fun Modifier.liquidRim(corner: Dp, light: GlassLight.Light, dark: Boolean, lens: Boolean = false,
                                dispersion: Float = 0f, strength: Float = 1f): Modifier = drawWithCache {
    val wDp = size.width / density; val hDp = size.height / density
    val shape = if (lens) LiquidShape.lens(wDp, hDp) else LiquidShape.surface(wDp, hDp)
    val r = corner.toPx().coerceAtMost(size.minDimension / 2f)
    onDrawWithContent {
        drawContent()
        LiquidRim.draw(this, Rect(Offset.Zero, size), r, shape.bezel * density, light, lens, dispersion,
            LiquidMaterial.rimOpacity(dark) * strength)
    }
}

/** Where a press is, so the glow blooms there; shared by the press and the glass that lights up. */
@Stable
internal class LiquidPressState {
    var point by mutableStateOf(Offset.Unspecified)
    var pressed by mutableStateOf(false)
    val glow = Animatable(0f)
}

/**
 * The library's touch response for any pressable thing: it swells under the finger (small
 * circles about 20%, wide pills about 7%, never more than [maxGrowDp] in absolute size), and if
 * the finger moves it is drawn out toward it like a droplet held by surface tension, leaning a
 * little that way; released, it settles with a soft jelly wobble. Observes touches without
 * consuming them; a scroll that takes the gesture over ends the press at once.
 */
internal fun Modifier.liquidPress(enabled: Boolean = true, state: LiquidPressState? = null, maxGrowDp: Float = 10f,
                                  lean: Dp = 10.dp, stretch: Float = .2f): Modifier = composed {
    val reduce = rememberAppleReduceMotion()
    // SwiftShader emulators (CI) keep presses still: swiping across many swelling glass pieces
    // has taken their host renderer down. Real phones get the full response.
    if (!enabled || reduce || LiquidGlassKit.softwareRenderedDevice) return@composed this
    val scope = rememberCoroutineScope()
    val sx = remember { Animatable(1f) }; val sy = remember { Animatable(1f) }
    val tx = remember { Animatable(0f) }; val ty = remember { Animatable(0f) }
    val own = remember { Animatable(0f) }
    val glow = state?.glow ?: own
    fun to(values: FloatArray, spec: SpringSpec<Float>) {
        listOf(sx, sy, tx, ty).forEachIndexed { i, a -> scope.launch { a.animateTo(values[i], spec) } }
    }
    this.graphicsLayer {
        scaleX = sx.value; scaleY = sy.value; translationX = tx.value; translationY = ty.value
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val w = size.width / density; val h = size.height / density
            val natural = (9f / sqrt((w * h).coerceAtLeast(1f))).coerceIn(.07f, .22f)
            val grow = min(natural, maxGrowDp / w.coerceAtLeast(1f))
            val base = 1f + grow
            state?.point = down.position; state?.pressed = true
            to(floatArrayOf(base, base, 0f, 0f), LiquidSprings.press())
            scope.launch { glow.animateTo(1f, androidx.compose.animation.core.tween(80)) }
            val leanPx = lean.toPx()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    // A list that scrolls takes the gesture over: the press lets go at once.
                    if (change.isConsumed && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    state?.point = change.position
                    val dx = change.position.x - down.position.x; val dy = change.position.y - down.position.y
                    val dist = hypot(dx, dy)
                    if (dist < 1f) continue
                    val cx = (dx / dist) * (dx / dist); val cy = (dy / dist) * (dy / dist)
                    val span = min(size.width * cx + size.height * cy, 64f * density)
                    val reach = rubberband(dist, span * stretch * 2f, .55f)
                    val ex = reach * cx / size.width.coerceAtLeast(1); val ey = reach * cy / size.height.coerceAtLeast(1)
                    val nx = sign(dx) * (reach * cx * base) / 2f + rubberband(dx, leanPx * .6f, .4f)
                    val ny = sign(dy) * (reach * cy * base) / 2f + rubberband(dy, leanPx * .6f, .4f)
                    to(floatArrayOf(base * (1f + ex - ey * .35f), base * (1f + ey - ex * .35f), nx, ny), LiquidSprings.follow())
                }
            } finally {
                state?.pressed = false
                to(floatArrayOf(1f, 1f, 0f, 0f), LiquidSprings.release())
                scope.launch { glow.animateTo(0f, androidx.compose.animation.core.tween(360)) }
            }
        }
    }
}

/** The library's light bloom under the finger, inside a rounded rect of [corner]. */
internal fun DrawScope.liquidGlow(state: LiquidPressState, corner: Float, dark: Boolean) {
    val g = state.glow.value
    if (g <= .01f) return
    val p = state.point.takeIf { it.x.isFinite() && it.y.isFinite() } ?: center
    val path = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner.coerceAtMost(size.minDimension / 2)))) }
    drawPath(path, Brush.radialGradient(listOf(LiquidMaterial.glow(dark), Color.Transparent), p,
        (maxOf(size.width, size.height) * .85f).coerceAtLeast(1f)), alpha = g.coerceIn(0f, 1f), blendMode = BlendMode.SrcOver)
}

/** Composable helper: the current material darkness (background luminance). */
@Composable internal fun liquidDark(): Boolean = androidx.compose.material3.MaterialTheme.colorScheme.background.red < .5f
