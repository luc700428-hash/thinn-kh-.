package io.atmos.weather.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/** Real background refraction on Android 13+, with the same blur-only fallback on API 31/32.
 * Child shader is the captured BACKGROUND layer; icons and text are never displaced.
 */
@RequiresApi(33)
internal class LiquidOpticsEngine {
    private val shader=RuntimeShader(SOURCE)
    private var lastWidth=0f;private var lastHeight=0f;private var lastFocus=Float.NaN;private var lastRadius=0f
    private var cached:RenderEffect?=null
    private var lastInput:RenderEffect?=null
    private var lastShine=Float.NaN
    fun effect(width:Float,height:Float,radius:Float,focusX:Float,shine:Float,input:RenderEffect):RenderEffect {
        if(cached==null || input!==lastInput || shine!=lastShine || width!=lastWidth || height!=lastHeight || focusX!=lastFocus || radius!=lastRadius) {
            shader.setFloatUniform("bounds",width,height)
            shader.setFloatUniform("corner",radius.coerceAtMost(minOf(width,height)/2))
            shader.setFloatUniform("focus",width*focusX.coerceIn(0f,1f),height*.5f)
            shader.setFloatUniform("strength",.82f)
            shader.setFloatUniform("shine",shine.coerceIn(0f,1f))
            cached=RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"scene"),input)
            lastInput=input;lastShine=shine
            lastWidth=width;lastHeight=height;lastFocus=focusX;lastRadius=radius
        }
        return cached!!
    }
    companion object {
        // Rounded-lens normal + local magnification + edge dispersion + top-left specular reflection.
        const val SOURCE="""
            uniform shader scene;
            uniform float2 bounds;
            uniform float corner;
            uniform float2 focus;
            uniform float strength;
            uniform float shine;
            half4 main(float2 p) {
                float r=max(1.0,corner);
                float2 center=bounds*0.5;
                float2 q=abs(p-center)-(center-float2(r));
                float sd=length(max(q,float2(0.0)))+min(max(q.x,q.y),0.0)-r;
                float2 nearest=clamp(p,float2(r),bounds-float2(r));
                float2 v=p-nearest;
                float2 n=v/max(length(v),0.001);
                float edge=1.0-smoothstep(0.0,12.0,abs(sd));
                float2 fp=(p-focus)/max(bounds.y,float(1.0));
                float lens=exp(-dot(fp*float2(1.15,1.8),fp*float2(1.15,1.8)));
                float2 uv=p-n*edge*(2.0+strength*6.0)-(p-focus)*lens*strength*0.12;
                uv=clamp(uv,float2(0.0),bounds-float2(1.0));
                half4 c=scene.eval(uv);
                c.r=scene.eval(clamp(uv+n*edge*0.6,float2(0.0),bounds-float2(1.0))).r;
                c.b=scene.eval(clamp(uv-n*edge*0.6,float2(0.0),bounds-float2(1.0))).b;
                float spec=pow(max(dot(n,float2(-0.7071,-0.7071)),0.0),5.0)*edge;
                c.rgb+=half3(spec*0.29*shine+lens*0.018*shine);
                return c;
            }
        """
    }
}
