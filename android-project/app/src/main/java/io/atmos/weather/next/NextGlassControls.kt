package io.atmos.weather.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** NEXT navigation button, backed by the same optical material as the floating tool dock. */
@Composable
fun NextGlassAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) NextMotionTokens.PRESS_SCALE else 1f,
        animationSpec = NextMotionTokens.fastSpring(),
        label = "next-glass-press"
    )
    NextOpticalSurface(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else .45f)
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        cornerRadiusDp = 26f,
        pressed = pressed,
        reduceTransparency = reduceTransparency
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
    }
}
