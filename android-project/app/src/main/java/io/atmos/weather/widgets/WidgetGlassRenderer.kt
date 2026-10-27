package io.atmos.weather.widgets
import android.graphics.*
/** Original optical background. Launcher wallpaper cannot be sampled by an ordinary widget. */
object WidgetGlassRenderer {
 data class Palette(val top:Int,val bottom:Int,val ink:Int,val secondary:Int,val accent:Int)
 fun palette(index:Int,alert:Boolean=false)=if(alert)Palette(0xFF96323D.toInt(),0xFF541E32.toInt(),Color.WHITE,0xFFFFE3E7.toInt(),0xFFFFD3A3.toInt()) else when(Math.floorMod(index,5)){
  0->Palette(0xFF2266A2.toInt(),0xFF112B50.toInt(),Color.WHITE,0xFFD8E8F5.toInt(),0xFF92DCFF.toInt())
  1->Palette(0xFF4C467F.toInt(),0xFF20223D.toInt(),Color.WHITE,0xFFE1DFF6.toInt(),0xFFD8C7FF.toInt())
  2->Palette(0xFF35363D.toInt(),0xFF141416.toInt(),Color.WHITE,0xFFD5D5DA.toInt(),0xFF85CCFF.toInt())
  3->Palette(0xFFF7FAFF.toInt(),0xFFDCE7F2.toInt(),0xFF16253A.toInt(),0xFF4A586B.toInt(),0xFF0064C8.toInt())
  else->Palette(0xFF705342.toInt(),0xFF302725.toInt(),Color.WHITE,0xFFF0E2D5.toInt(),0xFFFFD39A.toInt())
 }
 fun background(width:Int,height:Int,index:Int,edge:Float,opacity:Float,alert:Boolean=false):Bitmap{
  val w=width.coerceIn(1,640);val h=height.coerceIn(1,640);val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val c=Canvas(b);val p=Paint(Paint.ANTI_ALIAS_FLAG);val palette=palette(index,alert);val radius=minOf(w,h)*.16f
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
