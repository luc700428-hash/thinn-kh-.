package io.atmos.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Liquid Glass segmented control (github.com/zakisheriff/Liquid-Glass, GlassSegmented and its
 * lens track). At rest the selection is a flat pill. Touch the control and it swells while the
 * pill inflates into a clear lens that rises 12 dp past the bar, follows the finger (stretching
 * with speed), magnifies and tints the label under it, then drops into the nearest segment with a
 * wobble. Every segment stays a selectable tab for TalkBack and keyboards.
 */
@Composable
internal fun LiquidSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    require(labels.isNotEmpty())
    val palette = MaterialTheme.colorScheme
    val dark = palette.background.red < .5f
    val reduce = rememberAppleReduceMotion() || LiquidGlassKit.softwareRenderedDevice
    val opaque = rememberAppleReduceTransparency()
    val haptics = LocalHapticEngine.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(selected.coerceIn(labels.indices))
    val x = remember { Animatable(selected.toFloat()) }          // lens position, in segments
    val lift = remember { Animatable(0f) }
    val swell = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { if (!dragging) x.animateTo(selected.toFloat(), if (reduce) tween(0) else LiquidSprings.snap()) }
    // Travel stretches the droplet along its path, more the faster it goes, and it jiggles round.
    LaunchedEffect(Unit) {
        snapshotFlow { x.velocity }.collect { v ->
            val target = if (reduce) 0f else jellyTarget(v, if (dragging) .17f else .13f, 6f)
            if (abs(target - squash.targetValue) > .004f) scope.launch { squash.animateTo(target, if (dragging) LiquidSprings.wobble() else LiquidSprings.jelly()) }
        }
    }
    val inset = 4.dp
    val swellPx = 14f
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp)
        .graphicsLayer {
            val grow = 1f + swellPx / (size.width / density).coerceAtLeast(1f) * swell.value
            scaleX = grow; scaleY = 1f + (grow - 1f) * .5f
        }
        .pointerInput(labels.size) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pad = inset.toPx()
                val slot = ((size.width - 2 * pad) / labels.size).coerceAtLeast(1f)
                fun slotAt(px: Float) = ((px - pad) / slot - .5f).coerceIn(0f, labels.size - 1f)
                val startedAt = System.currentTimeMillis()
                var moved = false
                if (!reduce) {
                    scope.launch { lift.animateTo(1f, LiquidSprings.lift()) }
                    scope.launch { swell.animateTo(1f, LiquidSprings.press()) }
                }
                var released = false
                var last = down.position.x
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) { released = true; last = c.position.x; break }
                        if (!moved && abs(c.position.x - down.position.x) < viewConfiguration.touchSlop) {
                            if (abs(c.position.y - down.position.y) > viewConfiguration.touchSlop * 1.5f) break
                            continue
                        }
                        moved = true; dragging = true; last = c.position.x
                        scope.launch { x.animateTo(slotAt(c.position.x), LiquidSprings.drag()) }
                    }
                } finally {
                    dragging = false
                    val pad2 = inset.toPx(); val slot2 = ((size.width - 2 * pad2) / labels.size).coerceAtLeast(1f)
                    val target = if (released) kotlin.math.round(((last - pad2) / slot2 - .5f).coerceIn(0f, labels.size - 1f)).toInt() else current
                    if (released && moved && target != current) { haptics?.play(HapticEngine.Event.TAP); select(target) }
                    scope.launch { x.animateTo(target.toFloat(), if (reduce) tween(0) else LiquidSprings.snap()) }
                    scope.launch { swell.animateTo(0f, LiquidSprings.release()) }
                    val wait = if (moved) 120L else (260L - (System.currentTimeMillis() - startedAt)).coerceAtLeast(60L)
                    scope.launch { delay(wait); lift.animateTo(0f, LiquidSprings.settle()) }
                }
            }
        }) {
        val shape = RoundedCornerShape(9999.dp)
        // The bar: regular glass.
        Box(Modifier.fillMaxSize().thinnGlass(opaque, 9999.dp, palette.surfaceVariant, clear = if (dark) .5f else .66f)
            .border(.5.dp, LiquidMaterial.edge(dark), shape))
        val optics = remember { liquidLensEffectOrNull(opaque) }
        fun lensRect(w: Float, h: Float): Rect {
            val pad = inset.value * density
            val slot = (w - 2 * pad) / labels.size
            val l = lift.value.coerceIn(0f, 1.08f)
            val inner = h - 2 * pad
            val fullH = h + 12f * density
            val fullW = maxOf(slot * 1.16f, slot + fullH - inner)
            val lw = (slot + (fullW - slot) * l) * (1f + squash.value)
            val lh = (inner + (fullH - inner) * l) * (1f - squash.value * .6f)
            val cx = pad + slot * (x.value + .5f)
            return Rect(cx - lw / 2, h / 2 - lh / 2, cx + lw / 2, h / 2 + lh / 2)
        }
        // The resting pill: a flat selection tint, giving way to the lens while held.
        Canvas(Modifier.fillMaxSize()) {
            val r = lensRect(size.width, size.height)
            val pillA = (1f - lift.value * 2f).coerceIn(0f, 1f)
            if (pillA > .01f) drawRoundRect(LiquidMaterial.segmentSelection(dark).copy(alpha = LiquidMaterial.segmentSelection(dark).alpha * pillA),
                r.topLeft, r.size, CornerRadius(r.minDimension / 2))
        }
        // Labels, refracted by the lifted lens on Android 13+.
        Row(Modifier.fillMaxSize().padding(inset).selectableGroup().graphicsLayer {
            renderEffect = if (android.os.Build.VERSION.SDK_INT < 33) null else optics?.let { o ->
                val r = lensRect(size.width + 2 * inset.toPx(), size.height + 2 * inset.toPx()).translate(-inset.toPx(), -inset.toPx())
                o.effect(size.width, size.height, r, r.minDimension / 2, lift.value.coerceIn(0f, 1f), density, zoom = 1.1f, dispersion = 1f, stretch = 1f)
            }
        }) {
            labels.forEachIndexed { index, label ->
                val chosen = index == selected
                val near = 1f - abs(x.value - index).coerceIn(0f, 1f)
                val lensOver = (lift.value.coerceIn(0f, 1f) * near)
                Box(Modifier.weight(1f).fillMaxHeight().selectable(selected = chosen, role = Role.Tab, onClick = {
                    if (!chosen) { haptics?.play(HapticEngine.Event.TAP); select(index) }
                }).graphicsLayer { val s = 1f + .035f * lensOver; scaleX = s; scaleY = s }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(label, color = if (lensOver > .5f) palette.primary else palette.onSurface, fontSize = 14.sp,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        // The lens body: clear fill, ring, shadow and the iridescent rim, drawn past the bar.
        val light = GlassLight.current(follow = !opaque)
        Canvas(Modifier.fillMaxSize()) {
            val l = lift.value.coerceIn(0f, 1f)
            if (l < .01f) return@Canvas
            val r = lensRect(size.width, size.height)
            val corner = CornerRadius(r.minDimension / 2)
            val path = Path().apply { addRoundRect(RoundRect(r, corner)) }
            for (n in 1..3) {
                val spread = n * 2.2f * density * l
                drawRoundRect(Color.Black.copy(alpha = (if (dark) .45f else .14f) * .18f * l), Offset(r.left - spread, r.top + 4f * density * l - spread * .4f),
                    Size(r.width + spread * 2, r.height + spread), CornerRadius((r.height + spread) / 2))
            }
            drawPath(path, LiquidMaterial.selectionLensFill(dark).copy(alpha = LiquidMaterial.selectionLensFill(dark).alpha * l))
            drawPath(path, LiquidMaterial.selectionLensEdge(dark).copy(alpha = LiquidMaterial.selectionLensEdge(dark).alpha * l), style = Stroke(.75f * density))
            LiquidRim.draw(this, r, r.minDimension / 2, 16f * density, light, lens = true, dispersion = 1f, opacity = l * LiquidMaterial.rimOpacity(dark))
        }
    }
}
