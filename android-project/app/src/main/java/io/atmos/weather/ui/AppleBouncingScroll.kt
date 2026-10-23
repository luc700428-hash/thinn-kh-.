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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * Optional rubber-band vertical scroll foundation.
 * Keep it opt-in while wheel, radar and map gestures are separately validated:
 * globally replacing their physics could break existing interaction logic.
 */
@Composable
fun Modifier.appleBouncingScroll(maxPull: Dp = 64.dp): Modifier {
    val reduced = rememberAppleReduceMotion()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pullLimit = with(density) { maxPull.toPx() }
    val overshoot = remember { Animatable(0f) }
    val connection = remember(reduced, pullLimit) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!reduced && source == NestedScrollSource.UserInput && available.y != 0f) {
                    scope.launch {
                        overshoot.snapTo((overshoot.value + available.y * .28f)
                            .coerceIn(-pullLimit, pullLimit))
                    }
                }
                // Never consume the event: child scroll state and pager receive original deltas.
                return Offset.Zero
            }
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                overshoot.animateTo(0f, spring(
                    dampingRatio = AppleMotion.springDamping,
                    stiffness = AppleMotion.springStiffness))
                return Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection).graphicsLayer {
        translationY = if (reduced) 0f else overshoot.value
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
