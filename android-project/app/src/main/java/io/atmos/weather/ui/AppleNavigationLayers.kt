package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnPopup as Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/** Anchored, 230dp selection popup; onChoose is the caller's existing source of truth. */
@Composable
fun ApplePullDownMenu(
    label:String,
    choices:List<String>,
    selected:String,
    onChoose:(String)->Unit,
    modifier:Modifier=Modifier
){
    var expanded by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val reduce=rememberAppleReduceMotion()
    val scope=rememberCoroutineScope()
    val palette=androidx.compose.material3.MaterialTheme.colorScheme
    val density=LocalDensity.current
    val popupW=with(density){230.dp.roundToPx()}
    val feedback=LocalHapticFeedback.current
    fun dismiss() {
        if(closing) return
        closing=true
        scope.launch { if(!reduce) kotlinx.coroutines.delay(160);expanded=false;closing=false }
    }
    Box(modifier.onGloballyPositioned { anchor=it.boundsInWindow() }) {
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable {if(!expanded) expanded=true},
            verticalAlignment=Alignment.CenterVertically) {
            Text(label,fontSize=17.sp,color=palette.onSurface,modifier=Modifier.weight(1f))
            Text(selected,fontSize=17.sp,color=palette.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            SettingsChevron()
        }
        if(expanded){
            val positioner=remember(anchor,popupW) { object:PopupPositionProvider {
                override fun calculatePosition(anchorBounds:androidx.compose.ui.unit.IntRect,
                    windowSize:IntSize, layoutDirection:androidx.compose.ui.unit.LayoutDirection,
                    popupContentSize:IntSize):IntOffset {
                    val margin=with(density){8.dp.roundToPx()}
                    val left=(anchor.right.roundToInt()-popupContentSize.width).coerceIn(margin,
                        (windowSize.width-popupContentSize.width-margin).coerceAtLeast(margin))
                    val top=anchor.top.roundToInt().coerceIn(margin,
                        (windowSize.height-popupContentSize.height-margin).coerceAtLeast(margin))
                    return IntOffset(left,top)
                }
            }}
            Popup(popupPositionProvider=positioner,onDismissRequest={dismiss()},
                properties=PopupProperties(focusable=true)) {
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appeared=true }
                val shown=appeared && !closing
                val progress by animateFloatAsState(if(shown)1f else 0f,
                    animationSpec=tween(if(reduce)0 else if(closing)160 else 200,easing=AppleMotion.popover),
                    label="anchored-menu-origin")
                Column(Modifier.width(230.dp).graphicsLayer {
                    scaleX=.85f+.15f*progress;scaleY=scaleX;alpha=progress
                    transformOrigin=androidx.compose.ui.graphics.TransformOrigin(1f,0f)
                }.shadow(20.dp,RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)).thinnBackdrop(rememberAppleReduceTransparency(),14.dp)
                    .background(palette.surface.copy(alpha=if(rememberAppleReduceTransparency())1f else .65f))
                    .thinnSpecularEdge(14.dp).padding(6.dp)) {
                    choices.forEach { choice ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .clickable(enabled=!closing) {
                                feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChoose(choice);dismiss()
                            }.padding(horizontal=10.dp,vertical=13.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Text(if(choice==selected) "✓" else "  ",color=palette.primary,fontSize=16.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(choice,fontSize=17.sp,color=palette.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Container for nested settings screens. Keeps both screens composed so scroll/state
 * are preserved. Its back-swipe threshold is 28% width; BackHandler is the owner's
 * responsibility, allowing system/gesture navigation to continue working.
 */
@Composable
fun AppleParallaxStack(
    nested:Boolean,
    onBack:()->Unit,
    modifier:Modifier=Modifier,
    root:@Composable ()->Unit,
    detail:@Composable ()->Unit
){
    val reduce=rememberAppleReduceMotion()
    val progress by animateFloatAsState(if(nested)1f else 0f,
        animationSpec=if(reduce)tween(0) else tween(420,easing=AppleMotion.fluid),
        label="iOS-parallax-drilldown")
    var drag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier) {
        val width=constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX=-.30f*progress*width
            alpha=1f-.1f*progress
        }) { root() }
        if(nested||progress>.001f) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX=(1f-progress)*width+drag.coerceAtLeast(0f)
            }.pointerInput(nested) {
                detectHorizontalDragGestures(onHorizontalDrag={change,x->
                    if(nested&&change.position.x<100.dp.toPx()+drag) {
                        drag=(drag+x).coerceIn(0f,width);change.consume()
                    }
                },onDragEnd={
                    if(drag>width*.28f) onBack()
                    drag=0f
                },onDragCancel={drag=0f})
            }) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { detail() }
                Box(Modifier.fillMaxHeight().width(18.dp).align(Alignment.CenterStart)
                    .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha=.18f),Color.Transparent))))
            }
        }
    }
}

/** Adapter to the existing alarm's exact AM/PM+hour+minute implementation. */
@Composable
fun AppleAlarmTimeWheel(
    hour:Int, minute:Int, onHour:(Int)->Unit, onMinute:(Int)->Unit,
    modifier:Modifier=Modifier
){
    Box(modifier) {
        io.atmos.weather.alarm.AppleAlarmTimeSelector(
            hour=hour.coerceIn(0,23),minute=minute.coerceIn(0,59),
            onHour=onHour,onMinute=onMinute
        )
    }
}

/**
 * Optional stacked-sheet host for future nested settings entries. Root always stays
 * composed; a drag on the grabber restores depth in real time and can dismiss.
 * This is presentation-only: the owner manages open/onClose and domain state.
 */
@Composable
fun AppleDepthSheetHost(
    open:Boolean,
    onClose:()->Unit,
    modifier:Modifier=Modifier,
    root:@Composable ()->Unit,
    sheet:@Composable ()->Unit
){
    val reduced=rememberAppleReduceMotion()
    val p by animateFloatAsState(if(open)1f else 0f,
        animationSpec=if(reduced)tween(0) else tween(440,easing=AppleMotion.fluid),
        label="sheet-perspective-root")
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier) {
        val heightPx=constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX=1f-.08f*p; scaleY=1f-.08f*p
            translationY=12.dp.toPx()*p
            clip=p>.001f
            shape=RoundedCornerShape((16f*p).dp)
        }) { root() }
        if(open||p>.001f){
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.4f*p)))
            Box(Modifier.fillMaxSize().padding(top=44.dp)
                .graphicsLayer {
                    translationY=(1f-p)*heightPx+dismissDrag
                    clip=true;shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp)
                }
                .background(Color(0xFF1C1C1E))) {
                Column {
                    Box(Modifier.fillMaxWidth().height(40.dp)
                        .pointerInput(open) {
                            detectVerticalDragGestures(
                                onVerticalDrag={change,dy->
                                    dismissDrag=(dismissDrag+dy).coerceIn(0f,heightPx)
                                    change.consume()
                                },onDragEnd={
                                    if(dismissDrag>heightPx*.18f) onClose()
                                    dismissDrag=0f
                                },onDragCancel={dismissDrag=0f})
                        },contentAlignment=Alignment.Center) {
                        Box(Modifier.width(40.dp).height(5.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha=.5f)))
                    }
                    sheet()
                }
            }
        }
    }
}
