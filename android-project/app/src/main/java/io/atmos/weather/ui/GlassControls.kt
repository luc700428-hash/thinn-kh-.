package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
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
    val haptics=LocalHapticEngine.current
    val corner=RoundedCornerShape(ThinnDesignTokens.radiusControl)
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered=true }
    val pressScale by animateFloatAsState(if(pressed && !reduce) AppleMotion.pressedScale else 1f,
        animationSpec=if(reduce) tween(0) else if(pressed) tween(100,easing=AppleMotion.fluid) else spring(dampingRatio=AppleMotion.springDamping,stiffness=AppleMotion.springStiffness),label="玻璃按鈕按壓")
    val appear by animateFloatAsState(if(entered) 1f else .94f,
        animationSpec=tween(if(reduce) 0 else 170,easing=AppleMotion.fluid),label="玻璃按鈕入場")
    val fill=if(colors!=null) {
        val chosen=if(enabled) colors.containerColor else colors.disabledContainerColor
        listOf(chosen,chosen)
    } else if(outlined) listOf(Color.White.copy(alpha=.24f),Color.White.copy(alpha=.105f),Color.White.copy(alpha=.17f))
        else listOf(Color(0xFFE5F7FF),Color(0xFFAFDDF6),Color(0xFF659FC7))
    Row(modifier.defaultMinSize(minHeight=48.dp)
        .graphicsLayer { scaleX=pressScale*appear;scaleY=pressScale*appear;translationY=if(pressed && !reduce) 2.dp.toPx() else 0f;alpha=if(enabled) appear*(if(pressed && !reduce) AppleMotion.pressedAlpha else 1f) else appear*.45f }
        .clip(corner)
        .background(Brush.linearGradient(fill))
        .border(border ?: androidx.compose.foundation.BorderStroke(1.dp,edge),corner)
        .thinnSpecularEdge(ThinnDesignTokens.radiusControl)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,onClick={
            haptics?.play(HapticEngine.Event.TAP); onClick()
        })
        .padding(padding), horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,
        content=content)
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
    val haptics=LocalHapticEngine.current
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val scale by animateFloatAsState(if(pressed && !reduce)AppleMotion.pressedScale else 1f,
        animationSpec=if(reduce)tween(0) else if(pressed)tween(100,easing=AppleMotion.fluid) else spring(dampingRatio=AppleMotion.springDamping,stiffness=AppleMotion.springStiffness),label="玻璃圖示按鈕回饋")
    Box(modifier.defaultMinSize(48.dp,48.dp)
        .graphicsLayer { scaleX=scale;scaleY=scale;alpha=if(!enabled) .45f else if(pressed && !reduce) AppleMotion.pressedAlpha else 1f }
        .clip(CircleShape)
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
    val haptics=LocalHapticEngine.current
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    val movement by androidx.compose.animation.core.animateDpAsState(
        if(checked) 22.dp else 0.dp,
        animationSpec=if(reduce) tween(0) else spring(dampingRatio=.78f,stiffness=480f),
        label="iOS 視覺開關滑塊"
    )
    val active=enabled && onCheckedChange!=null
    val tint=if(checked) ThinnDesignTokens.positive else Color(0xFF647688)
    Box(modifier.size(width=58.dp,height=48.dp)
        .then(if(onCheckedChange!=null)
            Modifier.toggleable(value=checked,enabled=enabled,role=androidx.compose.ui.semantics.Role.Switch,
                onValueChange={ next ->
                    haptics?.play(HapticEngine.Event.TAP);onCheckedChange(next)
                }) else Modifier)
        .graphicsLayer { alpha=if(active) 1f else .48f },
        contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(36.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint,tint.copy(alpha=.78f))))
            .border(1.dp,Color.White.copy(alpha=if(checked).24f else .15f),CircleShape)) {
            Box(Modifier.padding(3.dp).offset(x=movement).size(30.dp)
                .background(Color.White,CircleShape)
                .border(1.dp,Color.White.copy(alpha=.68f),CircleShape)
                .thinnSpecularEdge(15.dp))
        }
    }
}

/** Capsule track and a large optical thumb; full range & accessibility semantics are kept. */
@Composable
fun GlassSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps:Int=0,
    onValueChangeFinished:(()->Unit)?=null, colors:SliderColors?=null,enabled:Boolean=true) {
    val haptics=LocalHapticEngine.current
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    val low=valueRange.start
    val span=(valueRange.endInclusive-low).takeIf { it>0f } ?: 1f
    val fraction=((value-low)/span).coerceIn(0f,1f)
    val smooth by animateFloatAsState(fraction,animationSpec=tween(if(reduce) 0 else 115,easing=AppleMotion.fluid),label="膠囊滑桿進度")
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min=48.dp)
        .then(if(enabled) Modifier
            .pointerInput(low,span) {
                detectTapGestures { offset ->
                    onValueChange(low+span*(offset.x/size.width.toFloat()).coerceIn(0f,1f))
                    haptics?.play(HapticEngine.Event.TAP)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(low,span) {
                detectDragGestures(
                    onDragEnd={haptics?.play(HapticEngine.Event.RELEASE);onValueChangeFinished?.invoke()},
                    onDrag={change,_->
                        onValueChange(low+span*(change.position.x/size.width.toFloat()).coerceIn(0f,1f))
                        change.consume()
                    }
                )
            } else Modifier)
        .semantics {
            progressBarRangeInfo=androidx.compose.ui.semantics.ProgressBarRangeInfo(
                value.coerceIn(valueRange.start,valueRange.endInclusive),valueRange,steps)
            if(enabled) setProgress { proposed ->
                onValueChange(proposed.coerceIn(valueRange.start,valueRange.endInclusive))
                onValueChangeFinished?.invoke()
                true
            }
        },contentAlignment=Alignment.CenterStart) {
        val thumb=28.dp
        val travel=(maxWidth-thumb).coerceAtLeast(0.dp)
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
            .background(Color.White.copy(alpha=.24f)))
        Box(Modifier.fillMaxWidth(fraction=smooth.coerceAtLeast(.001f)).height(7.dp)
            .clip(CircleShape).background(ThinnOpticalMaterial.luminousPill))
        Box(Modifier.offset(x=travel*smooth).size(thumb)
            .clip(CircleShape).background(ThinnOpticalMaterial.opaque)
            .border(1.dp,ThinnOpticalMaterial.rim,CircleShape)
            .thinnSpecularEdge(14.dp))
    }
}
