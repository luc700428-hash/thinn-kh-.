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
        else listOf(androidx.compose.material3.MaterialTheme.colorScheme.primary,
            androidx.compose.material3.MaterialTheme.colorScheme.primary)
    val labelColor=colors?.let {if(enabled) it.contentColor else it.disabledContentColor}
        ?: if(outlined) androidx.compose.material3.MaterialTheme.colorScheme.primary
        else androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
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
        if(checked) 20.dp else 0.dp,
        animationSpec=if(reduce) tween(0) else spring(dampingRatio=.78f,stiffness=480f),
        label="iOS 視覺開關滑塊"
    )
    val active=enabled && onCheckedChange!=null
    val tint=if(checked) colors?.checkedTrackColor ?: ThinnDesignTokens.positive else colors?.uncheckedTrackColor ?: androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(width=51.dp,height=48.dp)
        .then(if(onCheckedChange!=null)
            Modifier.toggleable(value=checked,enabled=enabled,role=androidx.compose.ui.semantics.Role.Switch,
                onValueChange={ next ->
                    haptics?.play(HapticEngine.Event.TAP);onCheckedChange(next)
                }) else Modifier)
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
    val currentFraction by rememberUpdatedState(fraction)
    var tracking by remember { mutableStateOf(false) }
    val smooth by animateFloatAsState(fraction,animationSpec=tween(if(reduce || tracking) 0 else 115),label="膠囊滑桿進度")
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    fun quantized(proposed:Float):Float {
        val f=((proposed-low)/span).coerceIn(0f,1f)
        val detents=(steps+1).coerceAtLeast(1)
        return low+span*if(steps>0) (f*detents).roundToInt().toFloat()/detents else f
    }
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min=48.dp)
        .then(if(enabled) Modifier
            .pointerInput(low,span,steps) {
                val halfThumb=27.dp.toPx()
                fun mapped(x:Float)=quantized(low+span*((x-halfThumb)/(size.width-2f*halfThumb).coerceAtLeast(1f)).coerceIn(0f,1f))
                detectTapGestures { offset ->
                    change(mapped(offset.x))
                    haptics?.play(HapticEngine.Event.TAP)
                    finish?.invoke()
                }
            }
            .pointerInput(low,span,steps) {
                val halfThumb=27.dp.toPx()
                var grabOffset=0f
                detectDragGestures(
                    onDragStart={position ->
                        tracking=true
                        val center=halfThumb+(size.width-2*halfThumb)*currentFraction
                        grabOffset=(position.x-center).takeIf {kotlin.math.abs(it)<=halfThumb} ?: 0f
                    },
                    onDragCancel={tracking=false;finish?.invoke()},
                    onDragEnd={tracking=false;haptics?.play(HapticEngine.Event.RELEASE);finish?.invoke()},
                    onDrag={changePointer,_->
                        change(quantized(low+span*((changePointer.position.x-grabOffset-halfThumb)/(size.width-2f*halfThumb).coerceAtLeast(1f)).coerceIn(0f,1f)))
                        changePointer.consume()
                    }
                )
            } else Modifier)
        .semantics {
            progressBarRangeInfo=androidx.compose.ui.semantics.ProgressBarRangeInfo(
                value.coerceIn(valueRange.start,valueRange.endInclusive),valueRange,steps)
            if(enabled) setProgress { proposed ->
                change(quantized(proposed))
                finish?.invoke()
                true
            }
        },contentAlignment=Alignment.CenterStart) {
        val thumbWidth=54.dp
        val thumbHeight=34.dp
        val travel=(maxWidth-thumbWidth).coerceAtLeast(0.dp)
        val trackWidth=(maxWidth-thumbWidth).coerceAtLeast(0.dp)
        val extension by animateFloatAsState(if(tracking && !reduce)1.12f else 1f,
            if(reduce)tween(0) else spring(dampingRatio=.74f,stiffness=420f),label="光學滑塊形變")
        val dark=androidx.compose.material3.MaterialTheme.colorScheme.background.red<.5f
        val blue=colors?.activeTrackColor ?: if(dark)Color(0xFF0A84FF) else Color(0xFF007AFF)
        val gray=colors?.inactiveTrackColor ?: if(dark)Color(0xFF48484A) else Color(0xFFD1D1D6)
        val transparency=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
            .getBoolean("reduce_transparency",false)
        Box(Modifier.padding(horizontal=thumbWidth/2).fillMaxWidth().height(5.dp).clip(CircleShape)
            .background(gray.copy(alpha=if(enabled) .65f else .3f)))
        Box(Modifier.offset(x=thumbWidth/2).width(trackWidth*smooth).height(5.dp)
            .clip(CircleShape).background(blue.copy(alpha=if(enabled)1f else .3f)))
        Box(Modifier.offset(x=travel*smooth).size(thumbWidth,thumbHeight)
            .graphicsLayer {scaleX=extension;scaleY=if(tracking && !reduce)1.03f else 1f;alpha=if(enabled)1f else .45f}
            .shadow(if(tracking)14.dp else 8.dp,CircleShape,ambientColor=Color.Black.copy(alpha=.22f),spotColor=Color.Black.copy(alpha=.24f))
            .clip(CircleShape)
            // This control may live inside the captured page; use its own optical band, avoiding self-sampling.
            .background(if(transparency)Brush.linearGradient(listOf(Color(0xFFF2F2F7),Color(0xFFE5E5EA)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark).34f else .72f),
                    Color.White.copy(alpha=if(dark).09f else .22f),Color.White.copy(alpha=if(dark).24f else .56f))))
            .drawWithContent {
                drawContent()
                // Refracted section of the track bends and widens inside the transparent lens.
                val cy=size.height/2
                drawLine(blue.copy(alpha=.80f),Offset(2.dp.toPx(),cy),Offset(size.width*.50f,cy),
                    7.dp.toPx(),StrokeCap.Round)
                drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha=.40f),Color.Transparent),
                    center=Offset(size.width*.28f,size.height*.18f),radius=size.width*.5f),
                    Offset(2.dp.toPx(),1.dp.toPx()),Size(size.width-4.dp.toPx(),size.height*.54f))
                drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.92f),Color.White.copy(alpha=.12f),
                    Color.White.copy(alpha=.55f))),cornerRadius=CornerRadius(size.height/2),style=Stroke(.75.dp.toPx()))
            }.thinnSpecularEdge(999.dp))
    }
}
