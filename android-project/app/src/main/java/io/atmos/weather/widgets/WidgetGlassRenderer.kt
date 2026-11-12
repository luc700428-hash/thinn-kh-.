package io.atmos.weather.widgets

import io.atmos.weather.ui.AppThemeColors
import io.atmos.weather.ui.BrandPalette
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import android.graphics.*
/** Original optical background. Launcher wallpaper cannot be sampled by an ordinary widget. */
object WidgetGlassRenderer {
 data class Palette(val top:Int,val bottom:Int,val ink:Int,val secondary:Int,val accent:Int)
 fun palette(index:Int,alert:Boolean=false)=paletteFor(AppThemeColors.saved,index,alert)
 internal fun paletteFor(theme:io.atmos.weather.ui.AppThemeColor,index:Int,alert:Boolean=false):Palette {
  if(alert)return Palette(0xFF96323D.toInt(),0xFF541E32.toInt(),Color.WHITE,0xFFFFE3E7.toInt(),0xFFFFD3A3.toInt())
  val p=BrandPalette.scheme(theme,false);val pastel=ComposeColor(theme.swatchArgb)
  val strength=listOf(.24f,.38f,.14f,.52f,.30f)[Math.floorMod(index,5)]
  return Palette(lerp(ComposeColor.White,pastel,strength*.45f).toArgb(),lerp(ComposeColor.White,pastel,strength).toArgb(),
   p.onSurface.toArgb(),p.onSurfaceVariant.toArgb(),p.primary.toArgb())
 }

 fun background(width:Int,height:Int,index:Int,edge:Float,opacity:Float,alert:Boolean=false,cornerRadiusPx:Float=minOf(width,height)*.16f):Bitmap{
  val scale=minOf(1f,640f/width.coerceAtLeast(1),640f/height.coerceAtLeast(1))
  val w=(width*scale).toInt().coerceIn(1,640);val h=(height*scale).toInt().coerceIn(1,640)
  val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(b);val p=Paint(Paint.ANTI_ALIAS_FLAG);val palette=palette(index,alert)
  val radius=(cornerRadiusPx.takeIf{it.isFinite()}?:28f).coerceAtLeast(0f)*scale
  val rect=RectF(1f,1f,w-1f,h-1f);p.shader=LinearGradient(0f,0f,w.toFloat(),h.toFloat(),palette.top,palette.bottom,Shader.TileMode.CLAMP);p.alpha=(215+40*opacity.coerceIn(0f,1f)).toInt();c.drawRoundRect(rect,radius,radius,p)
  p.alpha=255;p.shader=RadialGradient(w*.22f,0f,w*.9f,intArrayOf(0x45FFFFFF,0x04FFFFFF,Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawRoundRect(rect,radius,radius,p)
  c.save();c.clipPath(Path().apply {addRoundRect(rect,radius,radius,Path.Direction.CW)})
  p.shader=RadialGradient(w*.95f,h*.95f,maxOf(w,h)*.72f,intArrayOf((palette.accent and 0x00FFFFFF) or 0x28000000,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
  c.drawRect(rect,p)
  p.shader=null;p.color=0x0CFFFFFF;p.style=Paint.Style.STROKE;p.strokeWidth=maxOf(1f,w/250f)
  c.drawOval(RectF(-w*.30f,h*.48f,w*1.2f,h*1.60f),p);c.restore()
  p.style=Paint.Style.STROKE;p.strokeWidth=maxOf(1f,w/360f);p.alpha=(150+90*edge.coerceIn(0f,1f)).toInt();p.shader=LinearGradient(0f,0f,w.toFloat(),h.toFloat(),intArrayOf(0xCAFFFFFF.toInt(),0x20FFFFFF,0x00FFFFFF),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP);c.drawRoundRect(rect,radius,radius,p)
  return b
 }
}
