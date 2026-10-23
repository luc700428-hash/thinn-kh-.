package io.atmos.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Optional rubber-band vertical scroll foundation.
 * Keep it opt-in while wheel, radar and map gestures are separately validated:
 * globally replacing their physics could break existing interaction logic.
 */
@Composable
fun Modifier.appleBouncingScroll(maxPull: Dp = 64.dp): Modifier {
    val reduced = rememberAppleReduceMotion()
    val density = LocalDensity.current
    val pullLimit = with(density) { maxPull.toPx() }
    // Rendering offset only: scroll state/data are never modified.
    var rubberBandY by remember { mutableFloatStateOf(0f) }
    val connection = remember(reduced, pullLimit) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!reduced && source == NestedScrollSource.UserInput && available.y != 0f) {
                    rubberBandY = (rubberBandY + available.y * .28f)
                        .coerceIn(-pullLimit, pullLimit)
                }
                // Never consume the event: child scroll state and pager receive original deltas.
                return Offset.Zero
            }
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                // Physical spring only after drag/fling (no async flood per frame).
                Animatable(rubberBandY).animateTo(0f, spring(
                    dampingRatio = AppleMotion.springDamping,
                    stiffness = AppleMotion.springStiffness)) { rubberBandY = value }
                return Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection).graphicsLayer {
        translationY = if (reduced) 0f else rubberBandY
    }
}

/** No Android overscroll stretch/glow, including descendants of this container. */
@Composable
fun AppleBouncingColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Column(modifier.appleBouncingScroll().verticalScroll(scrollState, overscrollEffect = null),
            content = content)
    }
}
