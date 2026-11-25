package io.atmos.weather.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * iOS 27 clear Liquid Glass, shared by the Dock lens, the slider and switch knobs and the
 * glass buttons, so every control lifts, bends light and settles the same way.
 *
 * The look is built from four layers, in this order:
 *  1. what sits behind the glass, magnified (the lens);
 *  2. a darker refraction band just inside the edge, where light bends away from the eye;
 *  3. a crisp specular line on the edge facing the light (sun or moon, see [GlassLight]) and a
 *     weaker exit highlight on the opposite edge;
 *  4. while a finger is down, a soft bloom of light that spreads from the touch point.
 */
internal object LiquidGlassKit {
    /** The tab-bar lens rises this far (dp) past the bar's height (Liquid Glass "overhang"). */
    const val DOCK_OVERHANG_DP=14f
    /** Extra width, as a share of a slot, that the lifted tab-bar lens gains ("spread"). */
    const val DOCK_SPREAD=.3f
    /** Icons under the lifted tab-bar lens are magnified by this factor ("contentZoom"). */
    const val MAGNIFY=1.04f
    /** Liquid Glass "drag" spring (0.12 s, 0.92): follows the finger almost one to one. */
    const val FOLLOW_DAMPING=.92f
    const val FOLLOW_STIFFNESS=2741.6f
    /** How far the lens stretches along its motion for each slot (or knob width) of lag. */
    const val STRETCH_PER_LAG=.55f
    const val MAX_STRETCH=.34f

    fun lerp(a:Float,b:Float,t:Float)=a+(b-a)*t

    /** Android emulator images (goldfish / ranchu) draw through SwiftShader, not a real GPU. */
    val softwareRenderedDevice:Boolean by lazy {
        val hw=android.os.Build.HARDWARE.orEmpty().lowercase()
        hw=="ranchu"||hw=="goldfish"||android.os.Build.FINGERPRINT.orEmpty().startsWith("generic")
    }

    /** Squash and stretch from the distance between the lens and its lagging tail. */
    fun stretch(lag:Float):Float=if(lag.isFinite())(abs(lag)*STRETCH_PER_LAG).coerceAtMost(MAX_STRETCH) else 0f

    fun capsule(rect:Rect):Path=Path().apply {
        val r=min(rect.width,rect.height)/2f
        addRoundRect(RoundRect(rect,CornerRadius(r,r)))
    }
}

/**
 * Draws layers 2 and 3 onto [path], which fills this draw scope. The caller has already drawn
 * the lens content. [energy] is 0 at rest and 1 while held; springs may overshoot it.
 */
internal fun DrawScope.liquidGlassEdge(path:Path,light:GlassLight.Light,energy:Float,dark:Boolean,
    touch:Offset?=null,bounds:Rect=Rect(Offset.Zero,size),corner:Float=min(bounds.width,bounds.height)/2f) {
    val e=if(energy.isFinite())energy.coerceIn(0f,1.25f) else 0f
    val c=bounds.center
    val reach=max(bounds.width,bounds.height)*.5f
    val toward=Offset(c.x+light.x*reach,c.y+light.y*reach)
    val away=Offset(c.x-light.x*reach,c.y-light.y*reach)
    clipPath(path) {
        // Refraction band: the rim of real glass looks darker on bright scenes.
        drawPath(path,Color.Black.copy(alpha=if(dark).16f else .07f),style=Stroke(min(bounds.minDimension*.22f,7f*density)))
        // Light gathering on the inside of the lit edge.
        drawPath(path,Brush.radialGradient(listOf(Color.White.copy(alpha=(if(dark).07f else .11f)+.06f*e),Color.Transparent),
            Offset(c.x+light.x*reach*.72f,c.y+light.y*reach*.72f),reach*1.1f))
        if(touch!=null&&e>.01f) {
            drawPath(path,Brush.radialGradient(listOf(Color.White.copy(alpha=.16f*min(e,1f)),Color.White.copy(alpha=.05f*min(e,1f)),Color.Transparent),
                touch,max(bounds.width,bounds.height)*.75f))
        }
    }
    // Rim light computed from the glass's own shape (Android 13+), the same as every glass card;
    // older phones and emulators keep the painted bloom below.
    val shaded=GlassRim.draw(this,path,bounds,corner,light,.82f+.18f*min(e,1f))
    // Soft bloom along the rim, then the crisp specular line.
    if(!shaded)drawPath(path,Brush.linearGradient(0f to Color.White.copy(alpha=.30f+.18f*e),.38f to Color.White.copy(alpha=.04f),
        .62f to Color.White.copy(alpha=.03f),1f to Color.White.copy(alpha=.16f+.10f*e),start=toward,end=away),style=Stroke(2.6f*density))
    // iOS interactive glass: the rim nearest the finger catches the light while it is held.
    if(touch!=null&&touch.isSpecified&&e>.01f) {
        drawPath(path,Brush.radialGradient(listOf(Color.White.copy(alpha=.85f*min(e,1f)),Color.White.copy(alpha=.25f*min(e,1f)),Color.Transparent),
            touch,max(bounds.width,bounds.height)*.55f),style=Stroke(2.2f*density))
    }
    if(!shaded)drawPath(path,Brush.linearGradient(0f to Color.White.copy(alpha=.96f),.30f to Color.White.copy(alpha=.24f),
        .50f to Color.White.copy(alpha=.07f),.70f to Color.White.copy(alpha=.18f),1f to Color.White.copy(alpha=.66f),
        start=toward,end=away),style=Stroke(1.1f*density))
}

/**
 * The liquid-glass rim light of the Backdrop engine (com.kyant.backdrop), for glass drawn by hand
 * (knobs, the Dock lens): brightest where the edge faces the sun or moon, fading around the shape.
 * One shader is shared; each draw records its own copy of the uniforms.
 */
internal object GlassRim {
    private var shader:android.graphics.RuntimeShader?=null
    private var broken=false
    private val matrix=android.graphics.Matrix()

    private fun obtain():android.graphics.RuntimeShader? {
        if(broken||android.os.Build.VERSION.SDK_INT<33||LiquidGlassKit.softwareRenderedDevice)return null
        shader?.let{return it}
        return try {android.graphics.RuntimeShader(com.kyant.backdrop.DefaultHighlightShaderString).also{shader=it}}
            catch(_:RuntimeException) {broken=true;null}
    }

    /** False when the painted fallback should be used instead. Lenses get the iridescent rim. */
    fun draw(scope:DrawScope,path:Path,bounds:Rect,corner:Float,light:GlassLight.Light,alpha:Float):Boolean = with(scope) {
        if(android.os.Build.VERSION.SDK_INT<33||bounds.width<2f||bounds.height<2f)return false
        // Software canvases (bitmap snapshots) cannot draw RuntimeShaders; they get the painted rim.
        if(!drawContext.canvas.nativeCanvas.isHardwareAccelerated)return false
        if(obtain()==null)return false
        val shape=LiquidShape.lens(bounds.width/density,bounds.height/density)
        LiquidRim.draw(this,bounds,corner,shape.bezel*density,light,lens=true,dispersion=1f,opacity=alpha*1.15f)
        true
    }
}

/**
 * Where the finger is on a glass button, observed without consuming anything, so the light can
 * bloom from that point and the glass can lean toward it the way iOS interactive glass does.
 */
@androidx.compose.runtime.Stable
internal class GlassTouch {
    var point by androidx.compose.runtime.mutableStateOf(Offset.Unspecified)
    var size by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Size.Zero)
    /** Rubber-banded lean toward the finger, in px; grows slowly once the finger leaves the glass. */
    fun lean(limit:Float):Offset {
        if(!point.isSpecified||size.width<=0f||size.height<=0f)return Offset.Zero
        val c=Offset(size.width/2,size.height/2)
        fun axis(d:Float,half:Float):Float {
            val inside=d.coerceIn(-half,half)
            val beyond=d-inside
            return (inside*.045f+beyond*.22f).coerceIn(-limit,limit)
        }
        return Offset(axis(point.x-c.x,size.width/2),axis(point.y-c.y,size.height/2))
    }
}

internal fun Modifier.glassTouchTracker(touch:GlassTouch):Modifier=this
    .onSizeChanged{touch.size=androidx.compose.ui.geometry.Size(it.width.toFloat(),it.height.toFloat())}
    .pointerInput(touch) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            touch.point=down.position
            try {
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull{it.id==down.id}?.let{if(it.pressed)touch.point=it.position}
                } while(event.changes.any{it.pressed})
            } finally {touch.point=Offset.Unspecified}
        }
    }

/** Specular edge, refraction band and touch bloom for a glass control of the given corner. */
internal fun Modifier.liquidGlassSurface(corner:Dp,energy:()->Float,touch:GlassTouch?,dark:Boolean,
    light:GlassLight.Light):Modifier=drawWithCache {
    val r=corner.toPx().coerceAtMost(size.minDimension/2)
    val path=Path().apply{addRoundRect(RoundRect(Rect(Offset.Zero,size),CornerRadius(r,r)))}
    var last=Offset(size.width/2,size.height/2)
    onDrawWithContent {
        drawContent()
        val p=touch?.point
        if(p!=null&&p.isSpecified)last=p
        liquidGlassEdge(path,light,energy(),dark,last,corner=r)
    }
}

/**
 * The refracting lens shader, or null where it must not run: below Android 13, with Reduce
 * Transparency, and on emulators. Emulators render RuntimeShader effects in software
 * (SwiftShader); a lens animating on every frame there has taken the whole virtual device down
 * in CI, and it adds nothing a person would ever see. The glass still gets its rim lighting.
 */
internal fun liquidLensEffectOrNull(opaque:Boolean=false):LiquidLensEffect? =
    if(android.os.Build.VERSION.SDK_INT>=33&&!opaque&&!LiquidGlassKit.softwareRenderedDevice)runCatching{LiquidLensEffect()}.getOrNull() else null

/**
 * iOS 27 lensing for a lens that moves inside a FIXED-size surface (Dock lens, slider and switch
 * knobs). Applied as the surface's RenderEffect on Android 13+: inside the lens outline the
 * content bends toward the centre along a band at the rim (squircle profile), red and blue
 * separate slightly where it bends most, and the centre can be magnified. Outside the outline
 * pixels pass through untouched. Lighting stays in [liquidGlassEdge] so it looks the same on
 * every Android version; Android 12 simply gets no bending.
 */
@androidx.annotation.RequiresApi(33)
internal class LiquidLensEffect {
    private val shader=android.graphics.RuntimeShader(SOURCE)
    /**
     * [lens] in surface pixels; [lift] 0 (no bending) .. 1 (fully lifted). The Liquid Glass lens
     * (github.com/zakisheriff/Liquid-Glass): a thick droplet whose squircle rim bends light outward
     * by Snell's law, magnifying by [zoom] ([zoomY] vertically, below 1 it thins what is under it)
     * and splitting colour at the rim; [stretch] smears the rim like a thick drop instead.
     */
    fun effect(width:Float,height:Float,lens:Rect,corner:Float,lift:Float,density:Float,zoom:Float=1.16f,
               zoomY:Float=zoom,dispersion:Float=1f,stretch:Float=0f):androidx.compose.ui.graphics.RenderEffect? {
        val l=if(lift.isFinite())lift.coerceIn(0f,1.15f) else 0f
        if(l<.02f||lens.width<2f||lens.height<2f||width<1f||height<1f)return null
        val minor=minOf(lens.width,lens.height)
        val k=l.coerceAtMost(1f)
        shader.setFloatUniform("size",width,height)
        shader.setFloatUniform("lens",lens.left,lens.top,lens.right,lens.bottom)
        shader.setFloatUniform("corner",corner.coerceIn(0f,minor/2))
        shader.setFloatUniform("bezel",(minor*.32f).coerceAtLeast(1f))
        shader.setFloatUniform("thickness",minor*.55f*k)
        shader.setFloatUniform("magnify",(1f-1f/(1f+(zoom-1f)*k).coerceAtLeast(.5f)),(1f-1f/(1f+(zoomY-1f)*k).coerceAtLeast(.5f)))
        shader.setFloatUniform("dispersion",dispersion.coerceIn(0f,1f)*k)
        shader.setFloatUniform("stretch",stretch.coerceIn(0f,1f)*k)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader,"content").asComposeRenderEffect()
    }
    companion object {
        const val SOURCE="""
            uniform shader content;
            uniform float2 size;
            uniform float4 lens;
            uniform float corner;
            uniform float bezel;
            uniform float thickness;
            uniform float2 magnify;
            uniform float dispersion;
            uniform float stretch;
            float sdRect(float2 p,float2 c,float2 h,float r){
                float2 q=abs(p-c)-h+float2(r);
                return length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-r;
            }
            float shiftAt(float d){
                if(d<0.0||d>=bezel) return 0.0;
                float t=max(0.002,d/bezel);float q=1.0-t;float q2=q*q;
                float base=1.0-q2*q2;float height=pow(base,0.25);
                float slope=q2*q/max(0.0001,pow(base,0.75));
                float incident=atan(slope);float refracted=asin(sin(incident)/1.5);
                return tan(incident-refracted)*thickness*(0.35+0.65*height);
            }
            half4 main(float2 p) {
                float2 c=(lens.xy+lens.zw)*0.5;
                float2 h=(lens.zw-lens.xy)*0.5;
                float r=min(corner,min(h.x,h.y));
                float sd=sdRect(p,c,h,r);
                if(sd>0.0) return content.eval(p);
                float d=-sd;
                float2 q=abs(p-c)-h+float2(r);
                float2 n=(q.x>=0.0&&q.y>=0.0)?sign(p-c)*normalize(q):(q.x>q.y?float2(sign(p.x-c.x),0.0):float2(0.0,sign(p.y-c.y)));
                float u=d/bezel;
                float s=stretch>0.0?(u<1.0?-bezel*stretch*0.5*(1.0-u)*(1.0-u):0.0):shiftAt(d);
                float2 hi=size-float2(1.0);
                float2 source=p-(p-c)*magnify;
                float2 edge=n*s;
                half4 col=content.eval(clamp(source+edge,float2(0.0),hi));
                if(dispersion>0.0&&abs(s)>0.001){
                    float spread=(stretch>0.0?0.12:0.16)*dispersion;
                    col.r=content.eval(clamp(source+edge*(1.0+spread),float2(0.0),hi)).r;
                    col.b=content.eval(clamp(source+edge*(1.0-spread),float2(0.0),hi)).b;
                }
                return col;
            }
        """
    }
}
