package io.atmos.weather.next

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Tunable motion vocabulary for native Android Compose.
 * Values are design-system APPROXIMATIONS, not undocumented Apple physics.
 * Interactive drags always use direct pointer positions; springs take over on release.
 */
object NextMotionTokens {
    const val PRESS_SCALE = 0.965f
    const val ELEVATED_DRAG_SCALE = 1.055f
    const val SNAP_STIFFNESS = 550f
    const val SNAP_DAMPING = 0.82f
    const val GENTLE_STIFFNESS = 240f
    const val GENTLE_DAMPING = 0.86f

    fun fastSpring(): SpringSpec<Float> = spring(
        dampingRatio = SNAP_DAMPING,
        stiffness = SNAP_STIFFNESS
    )
    fun gentleSpring(): SpringSpec<Float> = spring(
        dampingRatio = GENTLE_DAMPING,
        stiffness = GENTLE_STIFFNESS
    )
    fun clampedTouchX(x: Float): Float = if (x.isFinite()) x.coerceIn(0f, 1f) else .5f
}
