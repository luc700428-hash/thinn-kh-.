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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.PointerEventPass
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine

// MIT iOS-inspired dimensions; independently authored Compose implementation.
// Core controls are Foundation-only; Android system menus/notifications remain OS-owned.
private val edge: Brush get() = ThinnOpticalMaterial.rim

/**
 * Liquid Glass button (github.com/zakisheriff/Liquid-Glass, GlassButton): regular glass with a
 * 0.5 px edge, the specular rim and a light bloom under the finger; it swells, leans and stretches
 * toward a drag and wobbles as it settles ([liquidPress]). Primary buttons are the filled variant.
 */
@Composable
private fun GlassPill(onClick:()->Unit, modifier:Modifier, enabled:Boolean,
    padding:PaddingValues, outlined:Boolean, content:@Composable RowScope.()->Unit,
    colors:ButtonColors?=null, border:androidx.compose.foundation.BorderStroke?=null) {
    val opaque=rememberAppleReduceTransparency()
    val haptics=LocalHapticEngine.current
    val corner=RoundedCornerShape(ThinnDesignTokens.radiusControl)
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press=remember{LiquidPressState()}
    val palette=androidx.compose.material3.MaterialTheme.colorScheme
    val dark=palette.background.red<.5f
    val weatherCards=LocalWeatherCardPalette.current
    val filled=colors==null&&!outlined
    val base=if(colors!=null) {if(enabled) colors.containerColor else colors.disabledContainerColor}
        else if(outlined) weatherCards?.fill?:palette.surfaceVariant
        else BrandPalette.selection
    val clear=when{filled->.88f;colors!=null->if(pressed).55f else .62f;pressed->if(dark).55f else .92f;else->if(dark).5f else .66f}
    val labelColor=colors?.let {if(enabled) it.contentColor else it.disabledContentColor}
        ?: if(outlined) weatherCards?.accent?:palette.primary
        else Color.White
    val radiusPx=with(LocalDensity.current){ThinnDesignTokens.radiusControl.toPx()}
    Row(modifier.defaultMinSize(minHeight=48.dp)
        .liquidPress(enabled,press)
        .graphicsLayer { alpha=if(enabled) 1f else .45f }
        .clip(corner)
        .thinnBackdrop(opaque,ThinnDesignTokens.radiusControl)
        .background(glassTint(base,opaque,clear))
        .border(border ?: androidx.compose.foundation.BorderStroke(.5.dp,LiquidMaterial.edge(dark)),corner)
        .drawWithContent { drawContent(); liquidGlow(press,radiusPx,dark) }
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

/** A Liquid Glass circle button: small circles swell about 20% under the finger. */
@Composable
fun GlassIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    content:@Composable ()->Unit) {
    val opaque=rememberAppleReduceTransparency()
    val haptics=LocalHapticEngine.current
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press=remember{LiquidPressState()}
    val scheme=androidx.compose.material3.MaterialTheme.colorScheme
    val dark=scheme.background.red<.5f
    val fill=if(opaque)scheme.surfaceVariant else LocalWeatherCardPalette.current?.fill?:scheme.surface
    Box(modifier.defaultMinSize(48.dp,48.dp)
        .liquidPress(enabled,press,maxGrowDp=12f)
        .graphicsLayer { alpha=if(!enabled) .45f else 1f }
        .clip(CircleShape)
        .thinnBackdrop(opaque,24.dp)
        .background(glassTint(fill,opaque,if(pressed)(if(dark).55f else .92f) else if(dark).5f else .66f))
        .border(.5.dp,LiquidMaterial.edge(dark),CircleShape)
        .drawWithContent { drawContent(); liquidGlow(press,size.minDimension/2,dark) }
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,onClick={
            haptics?.play(HapticEngine.Event.TAP); onClick()
        }),contentAlignment=Alignment.Center){
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides
            (LocalWeatherCardPalette.current?.ink?:scheme.onSurface)){content()}
    }
}

/**
 * The Liquid Glass switch (github.com/zakisheriff/Liquid-Glass, GlassSwitch): a 64×28 track with a
 * 38×25 white pill. Touched, the pill inflates into a clear 58×38 lens hanging over the track,
 * which runs on through it thinner (the lens minifies it vertically) with a streaked rim. Drag it
 * with rubber-banding at the ends, or tap; the knob has to travel most of the way before it
 * flips, then drops back into a white pill with a gentle settle. TalkBack gets a real switch.
 */
@Composable
fun GlassSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier,
    enabled:Boolean=true,colors:SwitchColors?=null) {
    val opaque=rememberAppleReduceTransparency()
    val haptics=LocalHapticEngine.current
    val reduce=rememberAppleReduceMotion()
    val density=LocalDensity.current
    val scope=rememberCoroutineScope()
    val change by rememberUpdatedState(onCheckedChange)
    val latest by rememberUpdatedState(checked)
    val active=enabled && onCheckedChange!=null
    val presses=remember{MutableInteractionSource()}
    val dark=androidx.compose.material3.MaterialTheme.colorScheme.background.red<.5f
    val onColour=colors?.checkedTrackColor ?: LiquidMaterial.green(dark)
    val offColour=colors?.uncheckedTrackColor?.takeIf{it!=Color.Unspecified} ?: LiquidMaterial.track(dark)
    val position=remember{androidx.compose.animation.core.Animatable(if(checked)1f else 0f)}
    val lift=remember{androidx.compose.animation.core.Animatable(0f)}
    val tint=remember{androidx.compose.animation.core.Animatable(if(checked)1f else 0f)}
    val stretch=remember{androidx.compose.animation.core.Animatable(0f)}
    // How much the knob reads as glass: follows the lift up, but on the way down only falls, so
    // the lift's wobble below rest never flashes the lens back on.
    var glass by remember{mutableFloatStateOf(0f)}
    var dragging by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){
        snapshotFlow{lift.value}.collect{v->glass=if(lift.targetValue>.5f)v else minOf(glass,v)}
    }
    // The knob stretches with its speed and jiggles round when it stops.
    LaunchedEffect(Unit){
        snapshotFlow{position.velocity}.collect{v->
            val target=if(reduce)0f else jellyTarget(v,.14f,5f)
            if(kotlin.math.abs(target-stretch.targetValue)>.004f)scope.launch{stretch.animateTo(target,LiquidSprings.jelly())}
        }
    }
    LaunchedEffect(checked){
        if(!dragging){
            launch{position.animateTo(if(checked)1f else 0f,if(reduce)tween(0) else LiquidSprings.snap())}
            launch{tint.animateTo(if(checked)1f else 0f,if(reduce)tween(0) else LiquidSprings.tint())}
        }
    }
    val travel=22f
    fun flips(from:Float,raw:Float)=if(from>.5f)raw>.3f else raw>.7f
    Box(modifier.size(width=64.dp,height=48.dp)
        .then(if(active)Modifier.pointerInput(Unit){
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                val startedAt=System.currentTimeMillis()
                val from=if(latest)1f else 0f
                var moved=false;var lastRaw=from
                dragging=true
                if(!reduce)scope.launch{lift.animateTo(1f,LiquidSprings.lift())}
                var released=false
                try {
                    while(true){
                        val event=awaitPointerEvent(PointerEventPass.Initial)
                        val c=event.changes.firstOrNull{it.id==down.id}?:break
                        if(!c.pressed){released=true;break}
                        // A list scrolling under the finger takes the gesture: no toggle.
                        if(!moved&&c.isConsumed)break
                        val dx=(c.position.x-down.position.x)/density.density
                        if(!moved&&kotlin.math.abs(dx)<3f)continue
                        moved=true;c.consume()
                        val raw=from+dx/travel;lastRaw=raw
                        val over=when{raw<0f->rubberband(raw*travel,travel,.6f)/travel;raw>1f->1f+rubberband((raw-1f)*travel,travel,.6f)/travel;else->raw}
                        scope.launch{position.animateTo(over,LiquidSprings.drag())}
                        val on=flips(from,raw)
                        if((tint.targetValue>.5f)!=on){haptics?.play(HapticEngine.Event.TAP);scope.launch{tint.animateTo(if(on)1f else 0f,LiquidSprings.tint())}}
                    }
                } finally {
                    dragging=false
                    // A tap is toggled by the switch itself ([toggleable] below); a drag lands here.
                    if(moved&&released) {
                        val next=flips(from,lastRaw)
                        scope.launch{position.animateTo(if(next)1f else 0f,if(reduce)tween(0) else LiquidSprings.snap())}
                        scope.launch{tint.animateTo(if(next)1f else 0f,if(reduce)tween(0) else LiquidSprings.tint())}
                        if(next!=latest){haptics?.play(HapticEngine.Event.RELEASE);change?.invoke(next)}
                    } else if(!released) {
                        scope.launch{position.animateTo(if(latest)1f else 0f,if(reduce)tween(0) else LiquidSprings.snap())}
                        scope.launch{tint.animateTo(if(latest)1f else 0f,if(reduce)tween(0) else LiquidSprings.tint())}
                    }
                    // A quick tap keeps the lens up long enough to be seen crossing.
                    val wait=if(moved||!released)0L else (200L-(System.currentTimeMillis()-startedAt)).coerceAtLeast(0L)
                    scope.launch{kotlinx.coroutines.delay(wait);lift.animateTo(0f,LiquidSprings.knobSettle())}
                }
            }
        }else Modifier)
        // Taps (and TalkBack, keyboards) toggle through the real switch; drags are handled above.
        .then(if(onCheckedChange!=null)Modifier.toggleable(value=checked,enabled=enabled,role=androidx.compose.ui.semantics.Role.Switch,
            interactionSource=presses,indication=null,onValueChange={next->haptics?.play(HapticEngine.Event.TAP);change?.invoke(next)}) else Modifier)
        .graphicsLayer { alpha=if(active) 1f else .4f },
        contentAlignment=Alignment.Center) {
        val light=GlassLight.current(follow=!opaque)
        val optics=remember{liquidLensEffectOrNull(opaque)}
        fun knob(w:Float,h:Float,unit:Float):Rect {
            val l=lift.value
            val sc=.655f+(1f-.655f)*l
            val st=stretch.value
            val kw=58f*unit*sc*(1f+st); val kh=38f*unit*sc*(1f-st*.5f)
            val trackLeft=w/2-32f*unit
            val cx=trackLeft+(21f+position.value*travel)*unit
            return Rect(cx-kw/2,h/2-kh/2,cx+kw/2,h/2+kh/2)
        }
        // The track and its colour, on one layer that the lifted knob refracts (Android 13+): the
        // lens thins the track inside it and smears its rim, like iOS 26.
        Canvas(Modifier.matchParentSize().graphicsLayer {
            renderEffect=if(android.os.Build.VERSION.SDK_INT<33)null else optics?.let{o->val r=knob(size.width,size.height,density.density)
                o.effect(size.width,size.height,r,r.minDimension/2,glass.coerceIn(0f,1f),density.density,zoom=1f,zoomY=.72f,dispersion=0f,stretch=1f)}
        }) {
            val u=density.density
            val track=Rect(size.width/2-32f*u,size.height/2-14f*u,size.width/2+32f*u,size.height/2+14f*u)
            drawRoundRect(offColour,track.topLeft,track.size,CornerRadius(14f*u))
            drawRoundRect(onColour.copy(alpha=onColour.alpha*tint.value.coerceIn(0f,1f)),track.topLeft,track.size,CornerRadius(14f*u))
        }
        Canvas(Modifier.matchParentSize()) {
            val r=knob(size.width,size.height,density.density)
            val g=glass.coerceIn(0f,1f)
            val corner=CornerRadius(r.minDimension/2)
            val path=Path().apply{addRoundRect(RoundRect(r,corner))}
            // Shadow: 0 (2 + 8·lift) (6 + 20·lift) rgba(0,0,0,.12 + .08·lift), from widening capsules.
            for(n in 1..3) {
                val spread=n*(1.5f+5f*g)*density.density/3f
                drawRoundRect(Color.Black.copy(alpha=(.12f+.08f*g)*.3f),Offset(r.left-spread,r.top+(2f+8f*g)*density.density/2-spread*.5f),
                    Size(r.width+spread*2,r.height+spread),CornerRadius((r.height+spread)/2))
            }
            if(g>.01f) {
                drawPath(path,LiquidMaterial.lensFill(dark).copy(alpha=LiquidMaterial.lensFill(dark).alpha*g))
                drawPath(path,LiquidMaterial.lensRing(dark).copy(alpha=LiquidMaterial.lensRing(dark).alpha*g),style=Stroke(.75f*density.density))
                LiquidRim.draw(this,r,r.minDimension/2,LiquidShape.lens(r.width/density.density,r.height/density.density).bezel*density.density,
                    light,lens=true,dispersion=0f,opacity=(g*1.3f).coerceAtMost(1f)*LiquidMaterial.rimOpacity(dark))
            }
            // The white face, fading as the knob turns to glass.
            val face=(1f-g*(if(dark).92f else 1f)).coerceIn(0f,1f)
            if(face>.001f) {
                drawPath(path,(if(opaque)Color.White else Color.White).copy(alpha=face))
                drawPath(path,Color.Black.copy(alpha=.06f*face),style=Stroke(.5f*density.density))
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
