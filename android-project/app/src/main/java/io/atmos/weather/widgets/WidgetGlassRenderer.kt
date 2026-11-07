package io.atmos.weather.widgets

import io.atmos.weather.ui.brandArgb
import android.graphics.*
/** Original optical background. Launcher wallpaper cannot be sampled by an ordinary widget. */
object WidgetGlassRenderer {
 data class Palette(val top:Int,val bottom:Int,val ink:Int,val secondary:Int,val accent:Int)
 fun palette(index:Int,alert:Boolean=false)=if(alert)Palette(0xFF96323D.toInt(),0xFF541E32.toInt(),Color.WHITE,0xFFFFE3E7.toInt(),0xFFFFD3A3.toInt()) else when(Math.floorMod(index,5)){
  0->Palette(brandArgb(0xFF276F5A),brandArgb(0xFF113128),Color.WHITE,brandArgb(0xFFCCEDE4),brandArgb(0xFFA2DECD))
  1->Palette(brandArgb(0xFF2E544A),brandArgb(0xFF152823),Color.WHITE,brandArgb(0xFFC2EADF),brandArgb(0xFF9CDCCA))
  2->Palette(brandArgb(0xFF313836),brandArgb(0xFF131514),Color.WHITE,0xFFD5D5DA.toInt(),brandArgb(0xFF83D4BD))
  3->Palette(brandArgb(0xFFF7FAF7),brandArgb(0xFFCDECE4),brandArgb(0xFF102922),brandArgb(0xFF405D55),brandArgb(0xFF28725D))
  else->Palette(brandArgb(0xFF396156),brandArgb(0xFF212B28),Color.WHITE,brandArgb(0xFFC9ECE2),brandArgb(0xFFB3E4D7))
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
