package io.atmos.weather.next

import io.atmos.weather.ui.brandColor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.runtime.derivedStateOf
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
import io.atmos.weather.ui.AppleLiquidDockLens
import io.atmos.weather.ui.FluidGlassStyle
import io.atmos.weather.ui.LiquidGlassKit
import io.atmos.weather.ui.AppleMotion
import io.atmos.weather.ui.thinnBackdrop
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
    fillAvailableHeight:Boolean = false,
    darkBackdrop:Boolean? = null,
    minimized:Boolean = false,
    onExpand:()->Unit = {},
    accessory:NextToolAction? = null,
    onAccessory:()->Unit = {}
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
    var touching by remember{mutableStateOf(false)}
    var pressAt by remember{mutableFloatStateOf(Float.NaN)}
    var scrubbing by remember { mutableStateOf(false) }
    var scrubX by remember { mutableFloatStateOf(0f) }
    var displayId by remember {mutableStateOf(selectedId)}
    LaunchedEffect(selectedId) {displayId=selectedId}
    val density = LocalDensity.current
    val lightTheme=!(darkBackdrop ?: (MaterialTheme.colorScheme.background.red < .5f))
    val palette=if(lightTheme)io.atmos.weather.ui.BrandPalette.light else io.atmos.weather.ui.BrandPalette.dark
    val activeInk=palette.primary
    val inactiveInk=palette.onSurface
    val dockInsetPx = with(density) { (if(vertical)5.dp else 6.dp).toPx() }
    val selectedHidden=hidden.firstOrNull{it.id==displayId}
    val slotCount=(visible.size+if(hidden.isNotEmpty())1 else 0).coerceAtLeast(1)
    val currentIndex = if(selectedHidden!=null)visible.size else visible.indexOfFirst { it.id == displayId }.coerceAtLeast(0)
    val geometry=DockScrubGeometry(dockWidthPx.toFloat(),dockInsetPx,slotCount)
    val slotPx=geometry.slot
    val cellHeight=if(vertical)((dockWidthPx/density.density-10f)/slotCount).takeIf{it>0}?:64f
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
    val activeDisplayIndex by remember(scrubbing,lightIndex,geometry){derivedStateOf{
        if(scrubbing&&visible.isNotEmpty())geometry.nearest(scrubX) else lightIndex
    }}
    val lightX=if(visible.isEmpty()) .5f else (activeDisplayIndex+.5f)/slotCount
    // iOS 27 lens: one position drives both the resting pill and the lifted glass. While held it
    // follows the finger continuously (interactive spring); on release it settles on the tab.
    val engagedNow=touching||scrubbing||draggingId!=null
    val fingerSlot=if(pressAt.isFinite()&&slotPx>0f)((pressAt-dockInsetPx)/slotPx-.5f).coerceIn(0f,slotCount-1f) else lightIndex.toFloat()
    val lensTarget=if((touching||scrubbing)&&draggingId==null&&!reduceMotion)fingerSlot else lightIndex.toFloat()
    val lensPosition by animateFloatAsState(lensTarget,if(reduceMotion)snap() else if(engagedNow)
        spring(LiquidGlassKit.FOLLOW_DAMPING,LiquidGlassKit.FOLLOW_STIFFNESS) else spring(FluidGlassStyle.POSITION_DAMPING,FluidGlassStyle.POSITION_STIFFNESS),label="dock-lens-position")
    val lensTail by animateFloatAsState(lensPosition,if(reduceMotion)snap() else spring(FluidGlassStyle.TAIL_DAMPING,FluidGlassStyle.TAIL_STIFFNESS),label="dock-lens-tail")
    val lensContact by animateFloatAsState(if(engagedNow&&!reduceMotion)1f else 0f,if(reduceMotion)snap() else
        spring(FluidGlassStyle.CONTACT_DAMPING,FluidGlassStyle.CONTACT_STIFFNESS),label="dock-lens-lift")
    val iconLayer=rememberGraphicsLayer()
    // A selection tick each time the lens crosses into another tab, like the iOS tab bar.
    LaunchedEffect(activeDisplayIndex,scrubbing){if(scrubbing)haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
    // iOS 27 tab-bar minimize: while the page scrolls down the bar condenses, as one piece of
    // glass, into a small capsule holding only the current tab; scrolling up or a tap restores it.
    val compact by animateFloatAsState(if(minimized&&!vertical)1f else 0f,
        if(reduceMotion)snap() else AppleMotion.glassMorph(),label="dock-minimize")
    val compactWidthPx=with(density){maxOf(68.dp.toPx(),dockHeightPx.toFloat())}
    Box(modifier,propagateMinConstraints=true) {
    NextOpticalSurface(modifier = Modifier.layout{measurable,constraints->
            val full=constraints.maxWidth
            val width=if(compact<=0f||full==androidx.compose.ui.unit.Constraints.Infinity)full
                else (full+(compactWidthPx-full)*compact.coerceIn(0f,1.05f)).roundToInt().coerceIn(1,full)
            val placeable=measurable.measure(if(width==full)constraints else constraints.copy(minWidth=width,maxWidth=width))
            layout(maxOf(placeable.width,constraints.minWidth).coerceAtMost(full),placeable.height){placeable.place(0,0)}
        }.shadow(18.dp,RoundedCornerShape(9999.dp),
        ambientColor=Color.Black.copy(alpha=.10f),spotColor=Color.Black.copy(alpha=.10f)),
        cornerRadiusDp = 9999f, useBackdrop = true, touchX = if(vertical).5f else lightX, touchY=if(vertical)lightX else .08f,calm = true,
        pressed = touching || scrubbing || draggingId != null, reduceTransparency = reduceTransparency,darkBackdrop=darkBackdrop,clearGlass=true) {
        if(compact>=.5f) {
            val current=selectedHidden?:visible.firstOrNull{it.id==displayId}?:visible.firstOrNull()
            Box((if(fillAvailableHeight)Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(with(density){dockHeightPx.toDp()}))
                .graphicsLayer{alpha=((compact-.5f)*2f).coerceIn(0f,1f)}
                .semantics{contentDescription="展開分頁列，目前："+(current?.label?:"")}
                .clickable(role=Role.Button){haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);onExpand()},
                contentAlignment=Alignment.Center) {
                if(current!=null)Image(current.icon,null,Modifier.size((iconSize+3f).dp),colorFilter=ColorFilter.tint(activeInk))
            }
        } else
        Box((if(fillAvailableHeight)Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .graphicsLayer{alpha=(1f-compact*2f).coerceIn(0f,1f)}
            .onSizeChanged { if(compact<=0f){dockWidthPx=if(vertical)it.height else it.width};dockHeightPx=it.height }
            .pointerInput(geometry,vertical){awaitEachGesture{
                val down=awaitFirstDown(requireUnconsumed=false);touching=true
                pressAt=geometry.clamp(if(vertical)down.position.y else down.position.x)
                try{do{val event=awaitPointerEvent(PointerEventPass.Final)
                    event.changes.firstOrNull{it.id==down.id&&it.pressed}?.let{pressAt=geometry.clamp(if(vertical)it.position.y else it.position.x)}
                }while(event.changes.any{it.pressed})}finally{touching=false}
            }}
            .pointerInput(visible.map { it.id }, dockWidthPx,vertical) {
                val start:(androidx.compose.ui.geometry.Offset)->Unit={point->
                    scrubbing=true;scrubX=geometry.clamp(if(vertical)point.y else point.x)
                }
                val drag:(androidx.compose.ui.input.pointer.PointerInputChange,Float)->Unit={change,_->
                    scrubX=geometry.clamp(if(vertical)change.position.y else change.position.x);change.consume()
                }
                val finish:()->Unit={
                    val preview=previewIndex();val destination=visible.getOrNull(preview);scrubbing=false
                    if(destination==null&&hidden.isNotEmpty()&&preview==visible.size)showOverflow=true
                    if(destination!=null){displayId=destination.id;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);latestOnSelected(destination.id)}
                }
                if(visible.isNotEmpty()) {
                    if(vertical)detectVerticalDragGestures(onDragStart=start,onDragEnd=finish,onDragCancel={scrubbing=false},onVerticalDrag=drag)
                    else detectHorizontalDragGestures(onDragStart=start,onDragEnd=finish,onDragCancel={scrubbing=false},onHorizontalDrag=drag)
                }
            }) {
            AppleLiquidDockPaint(selectedIndex=lightIndex,tabCount=slotCount,
                dragging=scrubbing || draggingId != null,
                dragOffsetPx=0f,
                dragOffset={if(scrubbing)scrubX-(dockInsetPx+(lightIndex+.5f)*slotPx) else dockDragOffsetPx},
                reduceMotion=reduceMotion,vertical=vertical,darkBackdrop=darkBackdrop,engaged=lensContact>.02f,reduceTransparency=reduceTransparency,
                position=if(draggingId==null)lensPosition else null)
        DockAxisLayout(vertical,fillAvailableHeight,Modifier.drawWithContent{
            iconLayer.record{this@drawWithContent.drawContent()}
            drawLayer(iconLayer)
        }) {
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
                            fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) activeInk else inactiveInk, maxLines = 1, modifier=Modifier.fillMaxWidth().height(captionHeight.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                val moreSelected=if(scrubbing)activeDisplayIndex==visible.size else selectedHidden!=null
                val moreLabel=selectedHidden?.label?:"更多"
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().semantics{contentDescription=moreLabel;selected=moreSelected}
                            .clickable(role=Role.Tab){showOverflow=!showOverflow}.padding(vertical=cellPadding.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(2.dp,Alignment.CenterVertically)
                    ) {
                        Image(Icons.Rounded.MoreHoriz,null,Modifier.size(iconSize.dp),
                            colorFilter=ColorFilter.tint(if(moreSelected)activeInk else inactiveInk))
                        Text(moreLabel,fontSize=14.sp,lineHeight=24.sp,color=if(moreSelected)activeInk else inactiveInk,
                            fontWeight=if(moreSelected)FontWeight.Bold else FontWeight.Normal,maxLines=1,
                            modifier=Modifier.fillMaxWidth().height(captionHeight.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
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
                                                color = inactiveInk, fontSize = 14.sp)
                                            // Accessible alternative to dragging a hidden action.
                                            if(allowReorder)Image(Icons.Rounded.KeyboardArrowUp, "移到工具列前方",
                                                Modifier.size(40.dp).clickable {
                                                    val updated = order.toMutableList()
                                                    updated.remove(action.id)
                                                    updated.add(0, action.id)
                                                    order = updated
                                                    latestOnOrderChanged(updated)
                                                    showOverflow = false
                                                }.padding(9.dp),
                                                colorFilter = ColorFilter.tint(brandColor(0xFFD2EFE7)))
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
    if(compact<.01f)AppleLiquidDockLens(position=lensPosition,tail=lensTail,contact=lensContact,tabCount=slotCount,icons=iconLayer,
        vertical=vertical,darkBackdrop=darkBackdrop,reduceTransparency=reduceTransparency,touch={pressAt})
    // A plain Box: the outer one propagates min constraints, which would stretch the bubble.
    if(accessory!=null&&!vertical&&compact>.01f)Box(Modifier.matchParentSize()){DockAccessoryBubble(accessory,compact,compactWidthPx,dockHeightPx.toFloat(),
        activeInk,reduceTransparency,darkBackdrop,onAccessory)}
    }
}

/**
 * iOS 27 glass splitting and merging: as the bar condenses, a separate glass bubble (for
 * example search) peels off its trailing end. While the two are close a liquid neck joins
 * them and thins out, exactly like neighbouring shapes in an iOS glass container; when the
 * bar expands again the bubble flows back in.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.DockAccessoryBubble(action:NextToolAction,compact:Float,
    compactWidthPx:Float,heightPx:Float,ink:Color,reduceTransparency:Boolean,darkBackdrop:Boolean?,onClick:()->Unit) {
    val density=LocalDensity.current
    var widthPx by remember{mutableIntStateOf(0)}
    Box(Modifier.matchParentSize().onSizeChanged{widthPx=it.width})
    if(widthPx<=0||heightPx<=0f)return
    val w=widthPx.toFloat();val h=heightPx
    val capsuleRight=w+(compactWidthPx-w)*compact.coerceIn(0f,1.05f)
    val bubble=h
    val gap=w-bubble-capsuleRight
    // Fades in as it emerges from inside the bar; fully solid once it has left it.
    val emerge=((gap+bubble*.5f)/(bubble*.5f)).coerceIn(0f,1f)
    if(emerge<=0f)return
    val spacing=with(density){26.dp.toPx()}
    val nav=io.atmos.weather.ui.FluidGlassStyle.navigation(if(darkBackdrop==true)io.atmos.weather.ui.BrandPalette.dark else io.atmos.weather.ui.BrandPalette.light,darkBackdrop==true)
    if(gap>0f&&gap<spacing) {
        // The liquid neck: a waist that narrows as the gap opens, then lets go.
        val pull=1f-gap/spacing
        val r=h/2;val cy=h/2
        val x1=capsuleRight-r*.35f;val x2=w-bubble+r*.35f
        val waist=r*(.15f+.6f*pull*pull)
        val neck=Path().apply {
            moveTo(x1,cy-r*.82f)
            quadraticTo((x1+x2)/2,cy-waist,x2,cy-r*.82f)
            lineTo(x2,cy+r*.82f)
            quadraticTo((x1+x2)/2,cy+waist,x1,cy+r*.82f)
            close()
        }
        androidx.compose.foundation.Canvas(Modifier.matchParentSize().graphicsLayer{alpha=emerge}
            .clip(androidx.compose.foundation.shape.GenericShape{_,_->addPath(neck)})
            .thinnBackdrop(reduceTransparency,9999.dp,.5f,frostScale=1f,refract=false)) {
            drawPath(neck,nav.underlay)
        }
    }
    val size=with(density){bubble.toDp()}
    Box(Modifier.align(Alignment.CenterEnd).size(size).graphicsLayer{
        alpha=emerge;val s=.72f+.28f*emerge;scaleX=s;scaleY=s
    }) {
        NextOpticalSurface(modifier=Modifier.fillMaxSize().shadow(14.dp,RoundedCornerShape(9999.dp),
            ambientColor=Color.Black.copy(alpha=.10f),spotColor=Color.Black.copy(alpha=.10f)),
            cornerRadiusDp=9999f,useBackdrop=true,calm=true,reduceTransparency=reduceTransparency,darkBackdrop=darkBackdrop,clearGlass=true) {
            Box(Modifier.fillMaxSize().semantics{contentDescription=action.label}.clickable(role=Role.Button){onClick()},
                contentAlignment=Alignment.Center) {
                Image(action.icon,null,Modifier.size(24.dp),colorFilter=ColorFilter.tint(ink))
            }
        }
    }
}


/** Fixed child slots; text stays upright when the dock changes axis. */
@Composable private fun DockAxisLayout(vertical:Boolean,stretch:Boolean,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    Layout(content=content,modifier=modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=5.dp)) {children,c->
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
