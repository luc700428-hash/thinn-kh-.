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
    /** The tab-bar lens grows this much taller than the bar's inner height, so it pokes out. */
    const val DOCK_LIFT=.34f
    /** Content under a lifted lens is magnified by this factor. */
    const val MAGNIFY=1.22f
    /** iOS "interactive" spring: follows the finger almost one to one. */
    const val FOLLOW_DAMPING=.86f
    const val FOLLOW_STIFFNESS=1754.6f
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

    /** False when the painted fallback should be used instead. */
    fun draw(scope:DrawScope,path:Path,bounds:Rect,corner:Float,light:GlassLight.Light,alpha:Float):Boolean = with(scope) {
        if(android.os.Build.VERSION.SDK_INT<33||bounds.width<2f||bounds.height<2f)return false
        // Software canvases (bitmap snapshots) cannot draw RuntimeShaders; they get the painted rim.
        if(!drawContext.canvas.nativeCanvas.isHardwareAccelerated)return false
        val s=obtain()?:return false
        return try {
            val r=corner.coerceIn(0f,bounds.minDimension/2f)
            s.setFloatUniform("size",bounds.width,bounds.height)
            s.setFloatUniform("cornerRadii",r,r,r,r)
            s.setColorUniform("color",android.graphics.Color.WHITE)
            s.setFloatUniform("angle",kotlin.math.atan2(light.y,light.x))
            s.setFloatUniform("falloff",1.6f)
            matrix.setTranslate(bounds.left,bounds.top);s.setLocalMatrix(matrix)
            val brush=androidx.compose.ui.graphics.ShaderBrush(s)
            val a=alpha.coerceIn(0f,1f)
            // A soft glow under a crisp line, both added onto what is behind (like real light).
            drawPath(path,brush,alpha=a*.32f,style=Stroke(3.2f*density),blendMode=androidx.compose.ui.graphics.BlendMode.Plus)
            drawPath(path,brush,alpha=a,style=Stroke(1.1f*density),blendMode=androidx.compose.ui.graphics.BlendMode.Plus)
            true
        } catch(_:RuntimeException) {broken=true;false}
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
    /** [lens] in surface pixels; [lift] 0 (no bending) .. 1 (fully lifted). */
    fun effect(width:Float,height:Float,lens:Rect,corner:Float,lift:Float,density:Float,zoom:Float=1f):androidx.compose.ui.graphics.RenderEffect? {
        val l=if(lift.isFinite())lift.coerceIn(0f,1.15f) else 0f
        if(l<.02f||lens.width<2f||lens.height<2f||width<1f||height<1f)return null
        val minor=minOf(lens.width,lens.height)
        shader.setFloatUniform("size",width,height)
        shader.setFloatUniform("lens",lens.left,lens.top,lens.right,lens.bottom)
        shader.setFloatUniform("corner",corner.coerceIn(0f,minor/2))
        // A thick dome: the band reaches most of the way in and bends hard at the rim.
        shader.setFloatUniform("band",minOf(16f*density,minor*.5f).coerceAtLeast(1f))
        shader.setFloatUniform("amount",minOf(16f*density,minor*.46f)*l)
        shader.setFloatUniform("zoom",1f+(zoom-1f)*l.coerceAtMost(1f))
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader,"content").asComposeRenderEffect()
    }
    companion object {
        const val SOURCE="""
            uniform shader content;
            uniform float2 size;
            uniform float4 lens;
            uniform float corner;
            uniform float band;
            uniform float amount;
            uniform float zoom;
            float sdRect(float2 p,float2 c,float2 h,float r){
                float2 q=abs(p-c)-h+float2(r);
                return length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-r;
            }
            half4 main(float2 p) {
                float2 c=(lens.xy+lens.zw)*0.5;
                float2 h=(lens.zw-lens.xy)*0.5;
                float r=min(corner,min(h.x,h.y));
                float sd=sdRect(p,c,h,r);
                if(sd>0.0) return content.eval(p);
                float2 n=float2(sdRect(p+float2(1.0,0.0),c,h,r)-sdRect(p-float2(1.0,0.0),c,h,r),
                                sdRect(p+float2(0.0,1.0),c,h,r)-sdRect(p-float2(0.0,1.0),c,h,r));
                n=n/max(length(n),0.0001);
                float t=clamp(1.0+sd/max(band,1.0),0.0,1.0);
                float x=1.0-t;
                float bend=1.0-pow(max(1.0-pow(1.0-x,4.0),0.0),0.25);
                float2 uv=c+(p-c)/max(zoom,0.5)-n*bend*amount;
                float2 hi=size-float2(1.0);
                uv=clamp(uv,float2(0.0),hi);
                half4 col=content.eval(uv);
                float disp=bend*amount*0.14;
                col.r=content.eval(clamp(uv+n*disp,float2(0.0),hi)).r;
                col.b=content.eval(clamp(uv-n*disp,float2(0.0),hi)).b;
                return col;
            }
        """
    }
}
