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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
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
    // iOS 26/27 glass: the control swells and lights up under the finger, then springs back.
    val pressScale by animateFloatAsState(if(pressed && !reduce) AppleMotion.pressedSwell else 1f,
        animationSpec=if(reduce) tween(0) else if(pressed) AppleMotion.interactive() else spring(dampingRatio=response.damping,stiffness=response.stiffness),label="玻璃按鈕按壓")
    val glow by animateFloatAsState(if(pressed && !reduce) 1f else 0f,
        animationSpec=if(reduce) tween(0) else if(pressed) AppleMotion.interactive() else AppleMotion.smooth(),label="玻璃按鈕高光")
    val appear=1f
    val touch=remember{GlassTouch()}
    val light=GlassLight.current(follow=!opaque)
    val lean by androidx.compose.animation.core.animateOffsetAsState(if(pressed&&!reduce)touch.lean(with(LocalDensity.current){8.dp.toPx()}) else androidx.compose.ui.geometry.Offset.Zero,
        if(pressed)AppleMotion.interactive() else spring(dampingRatio=response.damping,stiffness=response.stiffness),label="玻璃按鈕傾向手指")
    val palette=androidx.compose.material3.MaterialTheme.colorScheme
    val weatherCards=LocalWeatherCardPalette.current
    val fill=if(colors!=null) {
        val chosen=if(enabled) colors.containerColor else colors.disabledContainerColor
        listOf(chosen,chosen)
    } else if(outlined) {
        val neutral=weatherCards?.fill?:palette.surfaceVariant
        listOf(neutral,neutral)
    } else listOf(BrandPalette.selection,BrandPalette.selection)
    val labelColor=colors?.let {if(enabled) it.contentColor else it.disabledContentColor}
        ?: if(outlined) weatherCards?.accent?:palette.primary
        else Color.White
    Row(modifier.defaultMinSize(minHeight=48.dp)
        .graphicsLayer { scaleX=pressScale*appear;scaleY=(1f+(pressScale-1f)*.75f)*appear;alpha=if(enabled) appear else appear*.45f
            translationX=lean.x;translationY=lean.y }
        .glassTouchTracker(touch)
        .clip(corner)
        .thinnBackdrop(opaque,ThinnDesignTokens.radiusControl)
        .background(Brush.linearGradient(fill.map{it.copy(alpha=it.alpha*(if(opaque)1f else if(!outlined&&colors==null).92f else if(pressed).80f else .93f))}))
        .border(border ?: androidx.compose.foundation.BorderStroke(1.dp,edge),corner)
        .liquidGlassSurface(ThinnDesignTokens.radiusControl,{glow},touch,palette.background.red<.5f,light)
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
    // Small glass buttons swell more than wide ones, as on iOS 27.
    val scale by animateFloatAsState(if(pressed && !reduce)AppleMotion.pressedSwell+.06f else 1f,
        animationSpec=if(reduce)tween(0) else if(pressed)AppleMotion.interactive() else spring(dampingRatio=response.damping,stiffness=response.stiffness),label="玻璃圖示按鈕回饋")
    val glow by animateFloatAsState(if(pressed && !reduce) 1f else 0f,
        animationSpec=if(reduce) tween(0) else if(pressed) AppleMotion.interactive() else AppleMotion.smooth(),label="玻璃圖示按鈕高光")
    val touch=remember{GlassTouch()}
    val light=GlassLight.current(follow=!opaque)
    val lean by androidx.compose.animation.core.animateOffsetAsState(if(pressed&&!reduce)touch.lean(with(LocalDensity.current){7.dp.toPx()}) else androidx.compose.ui.geometry.Offset.Zero,
        if(pressed)AppleMotion.interactive() else spring(dampingRatio=response.damping,stiffness=response.stiffness),label="玻璃圖示按鈕傾向手指")
    Box(modifier.defaultMinSize(48.dp,48.dp)
        .graphicsLayer { scaleX=scale;scaleY=scale;alpha=if(!enabled) .45f else 1f;translationX=lean.x;translationY=lean.y }
        .glassTouchTracker(touch)
        .clip(CircleShape)
        .thinnBackdrop(opaque,24.dp)
        .background(if(opaque)ThinnOpticalMaterial.opaque else ThinnOpticalMaterial.subtlePill)
        .border(1.dp,ThinnOpticalMaterial.rim,CircleShape)
        .liquidGlassSurface(24.dp,{glow},touch,androidx.compose.material3.MaterialTheme.colorScheme.background.red<.5f,light)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,onClick={
            haptics?.play(HapticEngine.Event.TAP); onClick()
        }),contentAlignment=Alignment.Center){
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides
            (LocalWeatherCardPalette.current?.ink?:androidx.compose.material3.MaterialTheme.colorScheme.onSurface)){content()}
    }
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
    // iOS 27: while held the white knob lifts into a clear lens wider and taller than the track.
    val lift by animateFloatAsState(if((pressed||dragging)&&active&&!reduce)1f else 0f,
        if(reduce)tween(0) else spring(FluidGlassStyle.CONTACT_DAMPING,FluidGlassStyle.CONTACT_STIFFNESS),label="開關玻璃抬起")
    val lag by animateFloatAsState(movement.value,if(reduce)tween(0) else spring(FluidGlassStyle.TAIL_DAMPING,FluidGlassStyle.TAIL_STIFFNESS),label="開關果凍形變")
    val light=GlassLight.current(follow=!opaque)
    val dark=androidx.compose.material3.MaterialTheme.colorScheme.background.red<.5f
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
        val trackColor=Brush.linearGradient(listOf(tint,tint.copy(alpha=.78f)))
        Box(Modifier.fillMaxWidth().height(31.dp).clip(CircleShape).background(trackColor)
            .border(1.dp,Color.White.copy(alpha=if(checked).24f else .15f),CircleShape))
        val knob=remember{Path()}
        val optics=remember{liquidLensEffectOrNull(opaque)}
        fun knobRect(w:Float,h:Float,unit:Float):Rect {
            val grow=if(lift.isFinite())lift.coerceIn(-.12f,1.15f) else 0f
            val stretch=LiquidGlassKit.stretch((lag-movement.value)/27f)
            val kw=LiquidGlassKit.lerp(27f*unit,39f*unit,grow)*(1f+stretch)
            val kh=LiquidGlassKit.lerp(27f*unit,35f*unit,grow)*(1f-stretch*.35f)
            val cx=(2f+13.5f+movement.value)*unit+(lag-movement.value)*unit*.18f
            return Rect(cx-kw/2,h/2-kh/2,cx+kw/2,h/2+kh/2)
        }
        // Lensing layer (Android 13+): the track colour inside the knob bends at its rim.
        Canvas(Modifier.fillMaxWidth().height(31.dp).graphicsLayer {
            renderEffect=if(android.os.Build.VERSION.SDK_INT<33)null else optics?.let{o->val r=knobRect(size.width,size.height,density.density)
                o.effect(size.width,size.height,r,r.minDimension/2,lift.coerceIn(0f,1f),density.density)}
        }) {
            val r=knobRect(size.width,size.height,density.density)
            clipPath(Path().apply{addRoundRect(RoundRect(r,CornerRadius(r.minDimension/2)))}) {
                drawRoundRect(if(opaque)tint else tint.copy(alpha=.70f),Offset.Zero,size,CornerRadius(size.height/2))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(31.dp)) {
            val grow=if(lift.isFinite())lift.coerceIn(-.12f,1.15f) else 0f
            val clear=grow.coerceIn(0f,1f)
            val stretch=LiquidGlassKit.stretch((lag-movement.value)/27f)
            val w=LiquidGlassKit.lerp(27.dp.toPx(),39.dp.toPx(),grow)*(1f+stretch)
            val h=LiquidGlassKit.lerp(27.dp.toPx(),35.dp.toPx(),grow)*(1f-stretch*.35f)
            val cx=(2f+13.5f+movement.value).dp.toPx()+(lag-movement.value).dp.toPx()*.18f
            val rect=Rect(cx-w/2,size.height/2-h/2,cx+w/2,size.height/2+h/2)
            knob.reset();knob.addRoundRect(RoundRect(rect,CornerRadius(minOf(w,h)/2)))
            drawRoundRect(Color.Black.copy(alpha=.10f*(1f-clear)),rect.topLeft+Offset(0f,1.5.dp.toPx()),rect.size,CornerRadius(minOf(w,h)/2))
            clipPath(knob) {
                // The lens shows the track colour beneath, brighter and magnified.
                drawPath(knob,Brush.verticalGradient(listOf(Color.White.copy(alpha=if(dark).16f else .26f),Color.Transparent),rect.top,rect.bottom))
            }
            liquidGlassEdge(knob,light,clear,dark,bounds=rect)
            val white=(1f-clear).coerceIn(0f,1f)
            if(white>.001f) {
                drawPath(knob,Color.White.copy(alpha=white))
                drawPath(knob,Color.Black.copy(alpha=.05f*white),style=Stroke(.6.dp.toPx()))
            }
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
