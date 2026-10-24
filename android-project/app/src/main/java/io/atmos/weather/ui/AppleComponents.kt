package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cross-platform, iOS-inspired glass. Actual background sampling cannot be provided
 * by Compose's blur modifier alone. Callers may supply a dedicated backdrop layer;
 * absent one, this component uses a safe translucent material instead of pretending
 * to blur the live camera/map/weather behind it.
 *
 * Intentionally doesn't trap clicks or modify child state.
 */
@Composable
fun AppleGlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    blurSigma: Dp = AppleMotion.glassBlurDp.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val opaque = rememberAppleReduceTransparency()
    val shape = RoundedCornerShape(radius)
    val rim = Brush.linearGradient(
        listOf(Color.White.copy(alpha = .58f), Color.White.copy(alpha = .10f),
            Color.White.copy(alpha = .04f), Color.White.copy(alpha = .26f))
    )
    val darkSurface=MaterialTheme.colorScheme.background.red < .5f
    Box(modifier
        // Very soft shadow; no black hard edge.
        .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = .05f),
            spotColor = Color.Black.copy(alpha = .04f))
        .clip(shape)) {
        if (!opaque && backdrop != null) {
            Box(Modifier.matchParentSize().blur(blurSigma), content = backdrop)
        }
        Box(Modifier.matchParentSize().background(
            if (!darkSurface) Brush.linearGradient(listOf(
                if(opaque) Color.White else Color.White.copy(alpha=.78f),
                if(opaque) Color.White else Color.White.copy(alpha=.78f)))
            else if (opaque) Brush.linearGradient(listOf(Color(0xFF1C1C1E), Color(0xFF1C1C1E)))
            else Brush.linearGradient(listOf(Color(0xD93B3B41), Color(0xCC1C1C1E)))
        ))
        Column(Modifier.fillMaxWidth().border(BorderStroke(.75.dp, rim), shape)
            .padding(contentPadding), content = content)
    }
}

/**
 * Ripple-free pressable with stable child onPressed action, TalkBack button semantics,
 * 48dp target and distinct press-in and release spring animations.
 */
@Composable
fun AppleSpringButton(
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val reduceMotion = rememberAppleReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val feedback = LocalHapticEngine.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) AppleMotion.pressedScale else 1f,
        animationSpec = if (reduceMotion) tween(0) else if (pressed)
            tween(100, easing = AppleMotion.fluid)
        else spring(dampingRatio = AppleMotion.springDamping,
            stiffness = AppleMotion.springStiffness),
        label = "Apple press scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) AppleMotion.pressedAlpha else 1f,
        animationSpec = if (reduceMotion) tween(0) else if (pressed)
            tween(100, easing = AppleMotion.fluid)
        else spring(dampingRatio = AppleMotion.springDamping,
            stiffness = AppleMotion.springStiffness),
        label = "Apple press opacity"
    )
    Box(modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = if (enabled) alpha else .45f }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled,
            role = Role.Button) {
            feedback?.play(HapticEngine.Event.TAP)
            onPressed()
        }, contentAlignment = Alignment.Center, content = content)
}

/**
 * Slow rim illumination for non-emergency, emphasized UI only.
 * No overlay over alert text, tappable targets, or live maps.
 */
@Composable
fun AppleIntelligenceGlow(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val reduceMotion = rememberAppleReduceMotion()
    // Accessibility and battery: do not run an infinite transition offscreen
    // or when the user has explicitly requested reduced motion.
    val actualAngle = if (enabled && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "Apple glow")
        val angle by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(3200,
                easing = AppleMotion.fluid)),
            label = "Gradient cycle"
        )
        angle
    } else 0f
    Box(modifier.drawBehind {
        if (enabled) {
            val r = Math.toRadians(actualAngle.toDouble())
            val unit = Offset(cos(r).toFloat(), sin(r).toFloat())
            val c = Offset(size.width / 2f, size.height / 2f)
            val vector = Offset(size.width * unit.x / 2f, size.height * unit.y / 2f)
            val brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFF84D8), Color(0xFF83C9FF),
                    Color(0xFFC39BFF), Color.White, Color(0xFFFF84D8)),
                start = c - vector, end = c + vector
            )
            val rimWidth = 1.25.dp.toPx()
            val inset = 4.dp.toPx()
            val corner = CornerRadius(radius.toPx(), radius.toPx())
            drawRoundRect(brush = brush, topLeft = Offset(inset, inset),
                size = Size((size.width - 2 * inset).coerceAtLeast(0f),
                    (size.height - 2 * inset).coerceAtLeast(0f)),
                cornerRadius = corner, style = Stroke(width = 8.dp.toPx()), alpha = .055f)
            drawRoundRect(brush = brush, topLeft = Offset(rimWidth, rimWidth),
                size = Size((size.width - 2 * rimWidth).coerceAtLeast(0f),
                    (size.height - 2 * rimWidth).coerceAtLeast(0f)),
                cornerRadius = corner, style = Stroke(width = rimWidth), alpha = .87f)
        }
    }, content = content)
}
