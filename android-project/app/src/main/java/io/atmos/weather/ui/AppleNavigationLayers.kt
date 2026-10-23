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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Popup
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
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val density=LocalDensity.current
    val popupW=with(density){230.dp.roundToPx()}
    val feedback=LocalHapticFeedback.current
    Box(modifier.onGloballyPositioned { anchor=it.boundsInWindow() }) {
        AppleSpringButton(onPressed={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {
            Text("$label · $selected",fontSize=16.sp,color=Color.White)
        }
        if(expanded){
            val positioner=remember(anchor,popupW) { object:PopupPositionProvider {
                override fun calculatePosition(anchorBounds:androidx.compose.ui.unit.IntRect,
                    windowSize:IntSize, layoutDirection:androidx.compose.ui.unit.LayoutDirection,
                    popupContentSize:IntSize):IntOffset {
                    val left=anchor.left.roundToInt().coerceIn(8,(windowSize.width-popupContentSize.width-8).coerceAtLeast(8))
                    val below=anchor.bottom.roundToInt()+4
                    val top=if(below+popupContentSize.height>windowSize.height)
                        (anchor.top.roundToInt()-popupContentSize.height-4).coerceAtLeast(8) else below
                    return IntOffset(left,top)
                }
            }}
            Popup(popupPositionProvider=positioner,onDismissRequest={expanded=false},
                properties=PopupProperties(focusable=true)) {
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appeared=true }
                val scale by animateFloatAsState(if(appeared)1f else .15f,
                    animationSpec=spring(stiffness=260f,dampingRatio=.78f),label="anchored-menu-origin")
                Box(Modifier.width(230.dp).graphicsLayer {
                    scaleX=scale;scaleY=scale;alpha=((scale-.15f)/.85f).coerceIn(0f,1f)
                    transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)
                }) {
                    AppleGlassCard(radius=16.dp,contentPadding=PaddingValues(8.dp)) {
                        choices.forEach { choice ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onChoose(choice);expanded=false
                                }.padding(horizontal=10.dp,vertical=13.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text(if(choice==selected) "✓" else "  ",color=Color(0xFF8CD6FF),fontSize=15.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(choice,fontSize=15.sp,color=Color.White)
                            }
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
