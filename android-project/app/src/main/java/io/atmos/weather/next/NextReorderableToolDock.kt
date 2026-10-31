package io.atmos.weather.next

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
 * Fixed container; only the selection lens follows scrubbing on the active axis.
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
    allowReorder: Boolean = false,
    vertical:Boolean = false,
    fillAvailableHeight:Boolean = false
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
    var dockHeightPx by remember {mutableIntStateOf(0)}
    var scrubbing by remember { mutableStateOf(false) }
    var scrubX by remember { mutableFloatStateOf(0f) }
    var displayId by remember {mutableStateOf(selectedId)}
    LaunchedEffect(selectedId) {displayId=selectedId}
    val density = LocalDensity.current
    val lightTheme=MaterialTheme.colorScheme.background.red > .5f
    val activeInk=if(lightTheme) Color(0xFF0059B9) else Color(0xFF0A84FF)
    val inactiveInk=if(lightTheme) Color(0xFF2C2C2E) else Color(0xFFEBEBF0)
    val dockInsetPx = with(density) { 6.dp.toPx() }
    val currentIndex = visible.indexOfFirst { it.id == displayId }.coerceAtLeast(0)
    val geometry=DockScrubGeometry(dockWidthPx.toFloat(),dockInsetPx,visible.size.coerceAtLeast(1))
    val slotPx=geometry.slot
    val cellHeight=if(vertical)((dockWidthPx/density.density-10f)/visible.size.coerceAtLeast(1)).takeIf{it>0}?:64f
        else (dockHeightPx/density.density-10f).takeIf{it>0}?:58f
    val captionHeight=24f*density.fontScale+4f
    val cellPadding=((cellHeight-captionHeight-27f)/2f).coerceIn(2f,6f)
    val iconSize=(cellHeight-captionHeight-cellPadding*2-2f).coerceIn(18f,25f)

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
        cornerRadiusDp = 9999f, useBackdrop = true, touchX = if(vertical).5f else lightX, touchY=if(vertical)lightX else .08f,calm = true,
        pressed = scrubbing || draggingId != null, reduceTransparency = reduceTransparency) {
        Box((if(fillAvailableHeight)Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .onSizeChanged { dockWidthPx=if(vertical)it.height else it.width;dockHeightPx=it.height }
            .pointerInput(visible.map { it.id }, dockWidthPx,vertical) {
                val start:(androidx.compose.ui.geometry.Offset)->Unit={point->
                    scrubbing=true;scrubX=geometry.clamp(if(vertical)point.y else point.x)
                }
                val drag:(androidx.compose.ui.input.pointer.PointerInputChange,Float)->Unit={change,_->
                    scrubX=geometry.clamp(if(vertical)change.position.y else change.position.x);change.consume()
                }
                val finish:()->Unit={
                    val destination=visible.getOrNull(previewIndex());scrubbing=false
                    if(destination!=null){displayId=destination.id;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);latestOnSelected(destination.id)}
                }
                if(visible.isNotEmpty()) {
                    if(vertical)detectVerticalDragGestures(onDragStart=start,onDragEnd=finish,onDragCancel={scrubbing=false},onVerticalDrag=drag)
                    else detectHorizontalDragGestures(onDragStart=start,onDragEnd=finish,onDragCancel={scrubbing=false},onHorizontalDrag=drag)
                }
            }) {
            AppleLiquidDockPaint(selectedIndex=lightIndex,tabCount=visible.size.coerceAtLeast(1),
                dragging=scrubbing || draggingId != null,
                dragOffsetPx=if(scrubbing) scrubX - (dockInsetPx+(lightIndex+.5f)*slotPx) else dockDragOffsetPx,
                reduceMotion=reduceMotion,vertical=vertical)
        DockAxisLayout(vertical,fillAvailableHeight) {
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
                        modifier = Modifier.fillMaxSize()
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
                            ).padding(vertical = cellPadding.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp,Alignment.CenterVertically)
                    ) {
                        Image(action.icon, null, modifier = Modifier.size(iconSize.dp),
                            colorFilter = ColorFilter.tint(if (selected) activeInk else inactiveInk))
                        Text(action.label, fontSize = 14.sp,lineHeight=24.sp,
                            fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) activeInk else inactiveInk, maxLines = 1, modifier=Modifier.fillMaxWidth().height(captionHeight.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
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


/** Fixed child slots; text stays upright when the dock changes axis. */
@Composable private fun DockAxisLayout(vertical:Boolean,stretch:Boolean,content:@Composable ()->Unit) {
    Layout(content=content,modifier=Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=5.dp)) {children,c->
        val width=c.maxWidth
        val height=if((vertical||stretch)&&c.hasBoundedHeight)c.maxHeight else maxOf(58.dp.roundToPx(),(34f+24f*fontScale).dp.roundToPx()).coerceIn(c.minHeight,c.maxHeight)
        val count=children.size.coerceAtLeast(1)
        val cells=children.mapIndexed {i,child->
            val begin=if(vertical)height*i/count else width*i/count
            val end=if(vertical)height*(i+1)/count else width*(i+1)/count
            child.measure(if(vertical)Constraints.fixed(width,end-begin) else Constraints.fixed(end-begin,height))
        }
        layout(width,height){cells.forEachIndexed {i,cell->
            if(vertical)cell.placeRelative(0,height*i/count) else cell.placeRelative(width*i/count,0)
        }}
    }
}
