package io.atmos.weather.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** Background refraction and reflection on Android 13+, with the blur-only fallback on API 31/32.
 * Child shader is the captured BACKGROUND layer; icons and text are never displaced.
 *
 * Model: a convex squircle lens bends the backdrop inward along a band at the rim (low blur,
 * strong edge lensing), with slight dispersion. Light comes from one fixed upper-left
 * direction; the rim reflects it as a sharp specular highlight on the facing edge and a
 * softer catch-light on the opposite edge, scaled by a Schlick Fresnel term.
 */
@RequiresApi(33)
internal class LiquidOpticsEngine {
    private val shader=RuntimeShader(SOURCE)
    private var lastWidth=0f;private var lastHeight=0f;private var lastFocus=Float.NaN;private var lastRadius=0f
    private var cached:RenderEffect?=null
    private var lastInput:RenderEffect?=null
    private var lastShine=Float.NaN
    private var lastLightX=Float.NaN;private var lastLightY=Float.NaN;private var lastDensity=0f
    fun effect(width:Float,height:Float,radius:Float,focusX:Float,shine:Float,input:RenderEffect,
        lightX:Float=-.5f,lightY:Float=-.866f,density:Float=3f):RenderEffect {
        if(cached==null || input!==lastInput || shine!=lastShine || width!=lastWidth || height!=lastHeight || focusX!=lastFocus ||
            radius!=lastRadius || lightX!=lastLightX || lightY!=lastLightY || density!=lastDensity) {
            val d=density.coerceIn(1f,5f)
            val r=radius.coerceAtMost(minOf(width,height)/2)
            shader.setFloatUniform("bounds",width,height)
            shader.setFloatUniform("corner",r)
            shader.setFloatUniform("focus",width*focusX.coerceIn(0f,1f),height*.5f)
            shader.setFloatUniform("strength",.82f)
            shader.setFloatUniform("shine",shine.coerceIn(0f,1f))
            // Refraction band about 16 dp, displacement about 28 dp, never wider than half the shape.
            shader.setFloatUniform("band",minOf(16f*d,minOf(width,height)*.5f).coerceAtLeast(1f))
            shader.setFloatUniform("amount",minOf(28f*d,minOf(width,height)*.45f))
            shader.setFloatUniform("light",lightX,lightY)
            cached=RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"scene"),input)
            lastInput=input;lastShine=shine;lastLightX=lightX;lastLightY=lightY;lastDensity=density
            lastWidth=width;lastHeight=height;lastFocus=focusX;lastRadius=radius
        }
        return cached!!
    }
    companion object {
        const val SOURCE="""
            uniform shader scene;
            uniform float2 bounds;
            uniform float corner;
            uniform float2 focus;
            uniform float strength;
            uniform float shine;
            uniform float band;
            uniform float amount;
            uniform float2 light;
            float sdBox(float2 p,float2 c,float r){
                float2 q=abs(p-c)-(c-float2(r));
                return length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-r;
            }
            half4 main(float2 p) {
                float r=max(1.0,corner);
                float2 center=bounds*0.5;
                float sd=sdBox(p,center,r);
                // Surface normal from the distance field.
                float2 n=float2(sdBox(p+float2(1.0,0.0),center,r)-sdBox(p-float2(1.0,0.0),center,r),
                                sdBox(p+float2(0.0,1.0),center,r)-sdBox(p-float2(0.0,1.0),center,r));
                n=n/max(length(n),0.0001);
                // 1 at the rim, 0 once inside the band; squircle height h(x)=(1-(1-x)^4)^(1/4).
                float t=clamp(1.0+sd/max(band,1.0),0.0,1.0);
                float x=1.0-t;
                float h=pow(max(1.0-pow(1.0-x,4.0),0.0),0.25);
                float bend=1.0-h;
                float2 fp=(p-focus)/max(bounds.y,float(1.0));
                float lens=exp(-dot(fp*float2(1.15,1.8),fp*float2(1.15,1.8)));
                float2 uv=p-n*bend*amount*strength-(p-focus)*lens*strength*0.08;
                uv=clamp(uv,float2(0.0),bounds-float2(1.0));
                half4 c=scene.eval(uv);
                // Dispersion: red and blue separate a little where the glass bends most.
                float disp=bend*1.6;
                c.r=scene.eval(clamp(uv+n*disp,float2(0.0),bounds-float2(1.0))).r;
                c.b=scene.eval(clamp(uv-n*disp,float2(0.0),bounds-float2(1.0))).b;
                // Reflection: Fresnel at the rim, sharp lobe facing the light, softer lobe opposite.
                float fresnel=0.04+0.96*pow(t,5.0);
                float facing=max(dot(n,light),0.0);
                float behind=max(dot(n,-light),0.0);
                float rim=smoothstep(0.0,1.5,-sd)*(1.0-smoothstep(1.5,4.5,-sd));
                float spec=pow(facing,8.0)*(0.55*rim+0.30*fresnel)+pow(behind,6.0)*(0.22*rim+0.10*fresnel);
                c.rgb+=half3(spec*shine+lens*0.015*shine);
                return c;
            }
        """
    }
}
