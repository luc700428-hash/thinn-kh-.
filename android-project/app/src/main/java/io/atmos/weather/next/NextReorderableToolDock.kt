package io.atmos.weather.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import io.atmos.weather.ui.AppText as Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.atmos.weather.ui.AppleLiquidDockPaint
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnPopup as Popup
import androidx.compose.ui.window.PopupProperties

/** Stable IDs ensure drag ordering can be saved across configuration changes. */
data class NextToolAction(val id: String, val label: String, val icon: ImageVector)

/**
 * Fixed container; only the selection lens follows horizontal scrubbing.
 * Optional accessible ordering is reserved for non-navigation tool palettes.
 * The main app explicitly disables it and always uses the four canonical tabs.
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
    reduceTransparency: Boolean = false,
    allowReorder: Boolean = false
) {
    require(maxVisible >= 1) { "At least one dock item must be visible" }
    val actionIds = actions.map { it.id }
    require(actionIds.distinct().size == actionIds.size) { "Tool IDs must be unique" }
    var order by remember(actionIds) { mutableStateOf(actionIds) }
    // Local state is for immediate animation; outer owner remains the persistent source.
    val sorted = if(allowReorder) order.mapNotNull { id -> actions.firstOrNull { it.id == id } } else actions
    val visibleLimit = if (sorted.size > maxVisible) maxVisible - 1 else maxVisible
    val visible = sorted.take(visibleLimit.coerceAtLeast(0))
    val hidden = sorted.drop(visible.size)
    val latestOnOrderChanged by rememberUpdatedState(onOrderChanged)
    val latestOnSelected by rememberUpdatedState(onSelected)
    val haptics = LocalHapticFeedback.current
    val itemWidthPx = with(LocalDensity.current) { 66.dp.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dockDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var showOverflow by remember { mutableStateOf(false) }
    var dockWidthPx by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubX by remember { mutableFloatStateOf(0f) }
    var displayId by remember {mutableStateOf(selectedId)}
    LaunchedEffect(selectedId) {displayId=selectedId}
    val density = LocalDensity.current
    val lightTheme=MaterialTheme.colorScheme.background.red > .5f
    val activeInk=if(lightTheme) Color(0xFF007AFF) else Color(0xFF0A84FF)
    val inactiveInk=if(lightTheme) Color(0xFF2C2C2E) else Color(0xFFEBEBF0)
    val dockInsetPx = with(density) { 6.dp.toPx() }
    val currentIndex = visible.indexOfFirst { it.id == displayId }.coerceAtLeast(0)
    val geometry=DockScrubGeometry(dockWidthPx.toFloat(),dockInsetPx,visible.size.coerceAtLeast(1))
    val slotPx=geometry.slot
    fun previewIndex(): Int = geometry.nearest(scrubX)

    fun move(id: String, delta: Int): Boolean {
        if (!allowReorder) return false
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

    val lightIndex=visible.indexOfFirst {it.id == draggingId}.takeIf {it >= 0}
        ?: currentIndex
    val activeDisplayIndex=if(scrubbing && visible.isNotEmpty()) previewIndex() else lightIndex
    val lightX=if(scrubbing) (scrubX/dockWidthPx.coerceAtLeast(1)).coerceIn(0f,1f) else if(visible.isEmpty()) .5f else (activeDisplayIndex+.5f)/visible.size
    NextOpticalSurface(modifier = modifier.shadow(18.dp,RoundedCornerShape(9999.dp),
        ambientColor=Color.Black.copy(alpha=.10f),spotColor=Color.Black.copy(alpha=.10f)),
        cornerRadiusDp = 9999f, useBackdrop = true, touchX = lightX, calm = true,
        pressed = scrubbing || draggingId != null, reduceTransparency = reduceTransparency) {
        Box(Modifier.fillMaxWidth()
            .onSizeChanged { dockWidthPx=it.width }
            .pointerInput(visible.map { it.id }, dockWidthPx) {
                if(visible.isNotEmpty()) detectHorizontalDragGestures(
                    onDragStart={ start ->
                        scrubbing=true
                        scrubX=geometry.clamp(start.x)
                    },
                    onHorizontalDrag={ change,_ ->
                        scrubX=geometry.clamp(change.position.x)
                        change.consume()
                    },
                    onDragEnd={
                        val destination=visible.getOrNull(previewIndex())
                        scrubbing=false
                        if(destination != null) {
                            displayId=destination.id
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnSelected(destination.id)
                        }
                    },
                    onDragCancel={scrubbing=false}
                )
            }) {
            AppleLiquidDockPaint(selectedIndex=lightIndex,tabCount=visible.size.coerceAtLeast(1),
                dragging=scrubbing || draggingId != null,
                dragOffsetPx=if(scrubbing) scrubX - (dockInsetPx+(lightIndex+.5f)*slotPx) else dockDragOffsetPx,
                reduceMotion=reduceMotion)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
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
                    val selected = action.id == (if(scrubbing) visible.getOrNull(activeDisplayIndex)?.id else displayId)
                    Column(
                        modifier = Modifier.weight(1f).height(58.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = if (reduceMotion) 0f else localX)
                            .background(Color.Transparent, RoundedCornerShape(22.dp))
                            .semantics {
                                contentDescription = action.label
                                customActions = if(allowReorder) listOf(
                                    CustomAccessibilityAction("將${action.label}往左移") { move(action.id, -1) },
                                    CustomAccessibilityAction("將${action.label}往右移") { move(action.id, 1) }
                                ) else emptyList()
                                this.selected = selected
                            }
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                                onClick = { displayId=action.id;latestOnSelected(action.id) }
                            ).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Image(action.icon, null, modifier = Modifier.size(25.dp),
                            colorFilter = ColorFilter.tint(if (selected) activeInk else inactiveInk))
                        Text(action.label, fontSize = 11.sp,lineHeight=14.sp,
                            fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) activeInk else inactiveInk, maxLines = 1)
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                Box {
                    Column(
                        Modifier.width(65.dp).height(58.dp).clickable { showOverflow = !showOverflow }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
        } // AppleLiquidDockPaint Box wrapper
    }
}
