package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.roundToInt
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine

// MIT iOS-inspired dimensions; independently authored Compose implementation.
// Core controls are Foundation-only; Android system menus/notifications remain OS-owned.
private val edge: Brush get() = ThinnOpticalMaterial.rim

@Composable
private fun GlassPill(onClick:()->Unit, modifier:Modifier, enabled:Boolean,
    padding:PaddingValues, outlined:Boolean, content:@Composable RowScope.()->Unit,
    colors:ButtonColors?=null, border:androidx.compose.foundation.BorderStroke?=null) {
    val opaque=rememberAppleReduceTransparency()
    val response=iosMotionResponse()
    val haptics=LocalHapticEngine.current
    val corner=RoundedCornerShape(ThinnDesignTokens.radiusControl)
    val context=LocalContext.current
    val reduce=rememberAppleReduceMotion()
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered=true }
    val pressScale by animateFloatAsState(if(pressed && !reduce) AppleMotion.pressedScale else 1f,
        animationSpec=if(reduce) tween(0) else if(pressed) tween(100,easing=AppleMotion.fluid) else spring(dampingRatio=response.damping,stiffness=response.stiffness),label="玻璃按鈕按壓")
    val appear by animateFloatAsState(if(entered) 1f else .94f,
        animationSpec=tween(if(reduce) 0 else 170,easing=AppleMotion.fluid),label="玻璃按鈕入場")
    val palette=androidx.compose.material3.MaterialTheme.colorScheme
    val weatherCards=LocalWeatherCardPalette.current
    val fill=if(colors!=null) {
        val chosen=if(enabled) colors.containerColor else colors.disabledContainerColor
        listOf(chosen,chosen)
    } else if(outlined) {
        val neutral=weatherCards?.fill?:palette.surfaceVariant
        listOf(neutral,neutral)
    } else listOf(palette.primary,palette.primary)
    val labelColor=colors?.let {if(enabled) it.contentColor else it.disabledContentColor}
        ?: if(outlined) weatherCards?.accent?:palette.primary
        else palette.onPrimary
    Row(modifier.defaultMinSize(minHeight=48.dp)
        .graphicsLayer { scaleX=pressScale*appear;scaleY=pressScale*appear;translationY=if(pressed && !reduce) 2.dp.toPx() else 0f;alpha=if(enabled) appear*(if(pressed && !reduce) AppleMotion.pressedAlpha else 1f) else appear*.45f }
        .clip(corner)
        .thinnBackdrop(opaque,ThinnDesignTokens.radiusControl)
        .background(Brush.linearGradient(fill.map{it.copy(alpha=it.alpha*(if(opaque)1f else if(pressed).80f else .93f))}))
        .border(border ?: androidx.compose.foundation.BorderStroke(1.dp,edge),corner)
        .thinnSpecularEdge(ThinnDesignTokens.radiusControl)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,role=androidx.compose.ui.semantics.Role.Button,onClick={
            haptics?.play(HapticEngine.Event.TAP); onClick()
        })
        .padding(padding), horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,
        content={
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides labelColor) {content()}
        })
}

@Composable
fun GlassButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    contentPadding:PaddingValues=PaddingValues(horizontal=18.dp,vertical=10.dp),
    colors:ButtonColors?=null,border:androidx.compose.foundation.BorderStroke?=null,
    content:@Composable RowScope.()->Unit) {
    GlassPill(onClick,modifier,enabled,contentPadding,false,content,colors,border)
}

@Composable
fun GlassOutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    contentPadding:PaddingValues=PaddingValues(horizontal=18.dp,vertical=10.dp),
    border:androidx.compose.foundation.BorderStroke?=null,
    colors:ButtonColors?=null,content:@Composable RowScope.()->Unit) {
    GlassPill(onClick,modifier,enabled,contentPadding,true,content,colors,border)
}

@Composable
fun GlassTextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    contentPadding:PaddingValues=PaddingValues(horizontal=11.dp,vertical=9.dp),
    content:@Composable RowScope.()->Unit) {
    GlassPill(onClick,modifier,enabled,contentPadding,true,content,null,null)
}

@Composable
fun GlassIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    content:@Composable ()->Unit) {
    val opaque=rememberAppleReduceTransparency()
    val response=iosMotionResponse()
    val haptics=LocalHapticEngine.current
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val context=LocalContext.current
    val reduce=rememberAppleReduceMotion()
    val scale by animateFloatAsState(if(pressed && !reduce)AppleMotion.pressedScale else 1f,
        animationSpec=if(reduce)tween(0) else if(pressed)tween(100,easing=AppleMotion.fluid) else spring(dampingRatio=response.damping,stiffness=response.stiffness),label="玻璃圖示按鈕回饋")
    Box(modifier.defaultMinSize(48.dp,48.dp)
        .graphicsLayer { scaleX=scale;scaleY=scale;alpha=if(!enabled) .45f else if(pressed && !reduce) AppleMotion.pressedAlpha else 1f }
        .clip(CircleShape)
        .thinnBackdrop(opaque,24.dp)
        .background(ThinnOpticalMaterial.subtlePill)
        .border(1.dp,ThinnOpticalMaterial.rim,CircleShape)
        .thinnSpecularEdge(24.dp)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,onClick={
            haptics?.play(HapticEngine.Event.TAP); onClick()
        }),contentAlignment=Alignment.Center){ content() }
}

/** Own Compose switch visual. TalkBack receives a proper switch role and checked state. */
@Composable
fun GlassSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,
    enabled:Boolean=true,colors:SwitchColors?=null) {
    val opaque=rememberAppleReduceTransparency()
    val response=iosMotionResponse()
    val haptics=LocalHapticEngine.current
    val context=LocalContext.current
    val reduce=rememberAppleReduceMotion()
    val density=LocalDensity.current
    val presses=remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragDp by remember { mutableFloatStateOf(0f) }
    val change by rememberUpdatedState(onCheckedChange)
    val movement by androidx.compose.animation.core.animateDpAsState(
        (if(dragging) ((if(checked)20f else 0f)+dragDp).coerceIn(0f,20f) else if(checked)20f else 0f).dp,
        animationSpec=if(reduce||dragging) tween(0) else spring(dampingRatio=response.damping,stiffness=response.stiffness),
        label="iOS 視覺開關滑塊"
    )
    val active=enabled && onCheckedChange!=null
    val thumbStretch by animateFloatAsState(if((pressed||dragging)&&active&&!reduce)1.12f else 1f,
        if(reduce)tween(0) else spring(response.damping,response.stiffness),label="開關果凍形變")
    val tint=if(checked) colors?.checkedTrackColor ?: ThinnDesignTokens.positive else colors?.uncheckedTrackColor ?: androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(width=51.dp,height=48.dp)
        .then(if(onCheckedChange!=null)
            Modifier.toggleable(value=checked,enabled=enabled,role=androidx.compose.ui.semantics.Role.Switch,
                interactionSource=presses,indication=null,
                onValueChange={ next ->
                    haptics?.play(HapticEngine.Event.TAP);onCheckedChange(next)
                }) else Modifier)
        .then(if(active)Modifier.pointerInput(checked,density){
            detectHorizontalDragGestures(
                onDragStart={dragDp=0f;dragging=true},
                onHorizontalDrag={pointer,delta->pointer.consume();dragDp+=delta/density.density},
                onDragEnd={
                    val next=(if(checked)20f else 0f)+dragDp>=10f
                    dragging=false;dragDp=0f
                    if(next!=checked){haptics?.play(HapticEngine.Event.RELEASE);change?.invoke(next)}
                },
                onDragCancel={dragging=false;dragDp=0f}
            )
        }else Modifier)
        .graphicsLayer { alpha=if(active) 1f else .48f },
        contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(31.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint,tint.copy(alpha=.78f))))
            .border(1.dp,Color.White.copy(alpha=if(checked).24f else .15f),CircleShape)) {
            if(checked) Canvas(Modifier.matchParentSize()) {
                drawLine(Color.White,Offset(12.dp.toPx(),11.dp.toPx()),Offset(12.dp.toPx(),20.dp.toPx()),1.dp.toPx())
            }
            Box(Modifier.padding(2.dp).offset(x=movement).size(27.dp)
                .graphicsLayer{scaleX=thumbStretch;scaleY=if(pressed&&!reduce).97f else 1f}
                .clip(CircleShape).thinnBackdrop(opaque,15.dp)
                .background(Color.White.copy(alpha=if(opaque)1f else .88f),CircleShape)
                .border(1.dp,Color.White.copy(alpha=.68f),CircleShape)
                .thinnSpecularEdge(15.dp))
        }
    }
}

/** Shared clear bubble lens, adhering water tail and iridescent edge. */
@Composable
fun GlassSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps:Int=0,
    onValueChangeFinished:(()->Unit)?=null, colors:SliderColors?=null,enabled:Boolean=true) {
    LiquidBubbleSlider(value,onValueChange,modifier,valueRange,steps,onValueChangeFinished,colors,enabled)
}
