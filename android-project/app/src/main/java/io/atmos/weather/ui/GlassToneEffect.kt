package io.atmos.weather.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/** Limit only the sampled material's brightness; foreground labels and official map data stay untouched. */
internal object GlassToneEffect {
 fun apply(input:RenderEffect,dark:Boolean):RenderEffect {
  return if(Build.VERSION.SDK_INT>=33)try{modern(input,dark)}catch(_:RuntimeException){fallback(input,dark)} else fallback(input,dark)
 }
 private fun fallback(input:RenderEffect,dark:Boolean):RenderEffect {
   val scale=if(dark).18f else .06f;val bias=if(dark)0f else 255f*.94f
   val matrix=ColorMatrix(floatArrayOf(scale,0f,0f,0f,bias,0f,scale,0f,0f,bias,0f,0f,scale,0f,bias,0f,0f,0f,1f,0f))
   return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix),input)
 }
 private val lightSoftware=software(false)
 private val darkSoftware=software(true)
 fun softwareColorFilter(dark:Boolean)=if(dark)darkSoftware else lightSoftware
 private fun software(dark:Boolean):androidx.compose.ui.graphics.ColorFilter {
  val scale=if(dark).18f else .06f;val bias=if(dark)0f else 255f*.94f
  return androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(
   floatArrayOf(scale,0f,0f,0f,bias,0f,scale,0f,0f,bias,0f,0f,scale,0f,bias,0f,0f,0f,1f,0f)))
 }
 @RequiresApi(33) private fun modern(input:RenderEffect,dark:Boolean):RenderEffect {
  val shader=RuntimeShader(SOURCE)
  shader.setFloatUniform("darkMaterial",if(dark)1f else 0f)
  return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"scene"),input)
 }
 private const val SOURCE="""
  uniform shader scene;
  uniform float darkMaterial;
  half4 main(float2 p) {
   half4 c=scene.eval(p);
   half3 rgb=c.rgb/max(c.a,half(0.001));
   // The working color space can vary by display; luminance math uses linear sRGB.
   float3 linear=toLinearSrgb(float3(rgb));
   float light=dot(linear,float3(0.2126,0.7152,0.0722));
   if(darkMaterial>0.5) linear*=min(1.0,0.027/max(light,0.001));
   else if(light<0.87) linear=mix(linear,float3(1.0),(0.87-light)/max(1.0-light,0.001));
   rgb=half3(fromLinearSrgb(linear));
   return half4(rgb*c.a,c.a);
  }
 """
}
