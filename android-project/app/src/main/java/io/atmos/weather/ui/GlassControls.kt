package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.unit.dp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine

// MIT iOS-inspired dimensions; independently authored Compose implementation.
// Core controls are Foundation-only; Android system menus/notifications remain OS-owned.
private val edge=Brush.linearGradient(listOf(Color.White.copy(alpha=.72f),Color.White.copy(alpha=.08f),Color(0xFF86DBFB).copy(alpha=.46f)))

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
    val pressScale by animateFloatAsState(if(pressed && !reduce) .97f else 1f,
        animationSpec=tween(if(reduce) 0 else 140),label="玻璃按鈕按壓")
    val appear by animateFloatAsState(if(entered) 1f else .94f,
        animationSpec=tween(if(reduce) 0 else 170),label="玻璃按鈕入場")
    val fill=if(colors!=null) {
        val chosen=if(enabled) colors.containerColor else colors.disabledContainerColor
        listOf(chosen,chosen)
    } else if(outlined) listOf(Color.White.copy(alpha=.10f),Color.White.copy(alpha=.07f))
        else listOf(Color(0xFF337FAE),Color(0xFF21618F))
    Row(modifier.defaultMinSize(minHeight=48.dp)
        .graphicsLayer { scaleX=pressScale*appear;scaleY=pressScale*appear;alpha=if(enabled) appear else appear*.45f }
        .clip(corner)
        .background(Brush.linearGradient(fill))
        .border(border ?: androidx.compose.foundation.BorderStroke(1.dp,edge),corner)
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
    val scale by animateFloatAsState(if(pressed && !reduce).96f else 1f,
        animationSpec=tween(if(reduce)0 else 140),label="玻璃圖示按鈕回饋")
    Box(modifier.defaultMinSize(48.dp,48.dp)
        .graphicsLayer { scaleX=scale;scaleY=scale;alpha=if(enabled) 1f else .45f }
        .clip(CircleShape)
        .background(Color.White.copy(alpha=.11f))
        .border(1.dp,Color.White.copy(alpha=.25f),CircleShape)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,onClick={
            haptics?.play(HapticEngine.Event.TAP); onClick()
        }),contentAlignment=Alignment.Center){ content() }
}

/** Material3-backed controls retain Android TalkBack switch/slider semantics and gestures.
 * Glass accent colors are retained; the native implementation supplies keyboard and a11y actions.
 */
@Composable
fun GlassSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,
    enabled:Boolean=true,colors:SwitchColors?=null) {
    val haptics=LocalHapticEngine.current
    androidx.compose.material3.Switch(
        checked=checked,
        onCheckedChange=if(onCheckedChange==null) null else { value ->
            haptics?.play(HapticEngine.Event.TAP)
            onCheckedChange(value)
        },
        modifier=modifier.defaultMinSize(minWidth=52.dp,minHeight=48.dp),
        enabled=enabled && onCheckedChange!=null,
        colors=colors ?: androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor=Color.White, checkedTrackColor=ThinnDesignTokens.positive,
            uncheckedThumbColor=Color.White,uncheckedTrackColor=Color(0xFF6F8499))
    )
}

@Composable
fun GlassSlider(value:Float,onValueChange:(Float)->Unit,modifier:Modifier=Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps:Int=0,
    onValueChangeFinished:(()->Unit)?=null, colors:SliderColors?=null,enabled:Boolean=true) {
    val haptics=LocalHapticEngine.current
    androidx.compose.material3.Slider(
        value=value.coerceIn(valueRange.start,valueRange.endInclusive),
        onValueChange={ next ->
            onValueChange(next)
            haptics?.play(HapticEngine.Event.DRAG)
        },
        modifier=modifier.fillMaxWidth().heightIn(min=48.dp),
        enabled=enabled,
        valueRange=valueRange,
        steps=steps,
        onValueChangeFinished={
            haptics?.play(HapticEngine.Event.RELEASE)
            onValueChangeFinished?.invoke()
        },
        colors=colors ?: androidx.compose.material3.SliderDefaults.colors(
            thumbColor=Color.White, activeTrackColor=ThinnDesignTokens.accent,
            inactiveTrackColor=Color(0x889FB6C7))
    )
}
