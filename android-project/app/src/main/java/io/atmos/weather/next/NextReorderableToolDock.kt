package io.atmos.weather.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Stable IDs ensure drag ordering can be saved across configuration changes. */
data class NextToolAction(val id: String, val label: String, val icon: ImageVector)

/**
 * Compact physical dock: long-press and drag left/right to reorder its visible actions.
 * Overflow exposes all remaining actions without silently dropping one.
 * The owner persists [onOrderChanged] (e.g. in SharedPreferences/DataStore).
 * Users can also reorder with accessible actions, not just precise touch gestures.
 */
@Composable
fun NextReorderableToolDock(
    actions: List<NextToolAction>,
    selectedId: String?,
    onSelected: (String) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false
) {
    require(maxVisible >= 1) { "At least one dock item must be visible" }
    val actionIds = actions.map { it.id }
    require(actionIds.distinct().size == actionIds.size) { "Tool IDs must be unique" }
    var order by remember(actionIds) { mutableStateOf(actionIds) }
    // Local state is for immediate animation; outer owner remains the persistent source.
    val sorted = order.mapNotNull { id -> actions.firstOrNull { it.id == id } }
    val visibleLimit = if (sorted.size > maxVisible) maxVisible - 1 else maxVisible
    val visible = sorted.take(visibleLimit.coerceAtLeast(0))
    val hidden = sorted.drop(visible.size)
    val latestOnOrderChanged by rememberUpdatedState(onOrderChanged)
    val latestOnSelected by rememberUpdatedState(onSelected)
    val haptics = LocalHapticFeedback.current
    val itemWidthPx = with(LocalDensity.current) { 66.dp.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var showOverflow by remember { mutableStateOf(false) }

    fun move(id: String, delta: Int): Boolean {
        val current = order.indexOf(id)
        val newIndex = current + delta
        if (current < 0 || newIndex !in order.indices || newIndex >= visible.size) return false
        val updated = order.toMutableList()
        updated.removeAt(current)
        updated.add(newIndex, id)
        order = updated
        latestOnOrderChanged(updated)
        return true
    }

    NextOpticalSurface(modifier = modifier, touchX = .5f,
        pressed = draggingId != null, reduceTransparency = reduceTransparency) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            visible.forEach { action ->
                key(action.id) {
                    var localX by remember { mutableFloatStateOf(0f) }
                    val activeDrag = draggingId == action.id
                    val interaction = remember { MutableInteractionSource() }
                    val isPressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = when {
                            activeDrag && !reduceMotion -> NextMotionTokens.ELEVATED_DRAG_SCALE
                            isPressed && !reduceMotion -> NextMotionTokens.PRESS_SCALE
                            else -> 1f
                        },
                        animationSpec = NextMotionTokens.fastSpring(),
                        label = "next-dock-scale"
                    )
                    val selected = action.id == selectedId
                    Column(
                        modifier = Modifier.width(65.dp).height(63.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = if (reduceMotion) 0f else localX)
                            .background(if (selected) Color(0x623C7C9D) else Color.Transparent, RoundedCornerShape(22.dp))
                            .pointerInput(action.id, itemWidthPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = action.id
                                        localX = 0f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        localX += delta.x
                                        if (localX >= itemWidthPx && move(action.id, 1)) {
                                            localX -= itemWidthPx
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (localX <= -itemWidthPx && move(action.id, -1)) {
                                            localX += itemWidthPx
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = { draggingId = null; localX = 0f },
                                    onDragCancel = { draggingId = null; localX = 0f }
                                )
                            }
                            .semantics {
                                contentDescription = action.label
                                customActions = listOf(
                                    CustomAccessibilityAction("將${action.label}往左移") { move(action.id, -1) },
                                    CustomAccessibilityAction("將${action.label}往右移") { move(action.id, 1) }
                                )
                            }
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                                onClick = { latestOnSelected(action.id) }
                            ).padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Image(action.icon, null, modifier = Modifier.size(22.dp),
                            colorFilter = ColorFilter.tint(if (selected) Color.White else Color(0xFFD1E4F2)))
                        Text(action.label, fontSize = 14.sp,
                            color = if (selected) Color.White else Color(0xFFCDDFEB), maxLines = 1)
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                Box {
                    Column(
                        Modifier.width(65.dp).height(63.dp).clickable { showOverflow = !showOverflow }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Image(Icons.Rounded.MoreHoriz, null, Modifier.size(22.dp),
                            colorFilter = ColorFilter.tint(Color.White))
                        Text("更多", fontSize = 14.sp, color = Color.White)
                    }
                    if (showOverflow) {
                        Popup(onDismissRequest = { showOverflow = false },
                            properties = PopupProperties(focusable = true)) {
                            NextOpticalSurface(modifier = Modifier.width(192.dp),
                                reduceTransparency = reduceTransparency) {
                                Column(Modifier.padding(8.dp)) {
                                    hidden.forEach { action ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(action.label, Modifier.weight(1f)
                                                .clickable { showOverflow = false; latestOnSelected(action.id) }
                                                .padding(horizontal = 14.dp, vertical = 13.dp),
                                                color = Color.White, fontSize = 14.sp)
                                            // Accessible alternative to dragging a hidden action.
                                            Image(Icons.Rounded.KeyboardArrowUp, "移到工具列前方",
                                                Modifier.size(40.dp).clickable {
                                                    val updated = order.toMutableList()
                                                    updated.remove(action.id)
                                                    updated.add(0, action.id)
                                                    order = updated
                                                    latestOnOrderChanged(updated)
                                                    showOverflow = false
                                                }.padding(9.dp),
                                                colorFilter = ColorFilter.tint(Color(0xFFD8ECF9)))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
