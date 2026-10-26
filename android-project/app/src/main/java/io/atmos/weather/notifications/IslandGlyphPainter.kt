package io.atmos.weather.notifications
import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
/** A shared original vector atlas, a neutral optical plate, and an AOD-only white glyph. */
object IslandGlyphPainter {
 internal fun bitmap(context:Context,scene:IslandIconScene,frame:Int,monochrome:Boolean=false):Bitmap{
  val b=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);val c=Canvas(b);val p=Paint(Paint.ANTI_ALIAS_FLAG)
  if(!monochrome){p.shader=LinearGradient(0f,0f,128f,128f,intArrayOf(0xA86C7D92.toInt(),0x3A273343,0x68202B3C),null,Shader.TileMode.CLAMP);c.drawRoundRect(RectF(3f,3f,125f,125f),32f,32f,p);p.style=Paint.Style.STROKE;p.strokeWidth=1.4f;p.shader=LinearGradient(0f,0f,128f,128f,0xCFFFFFFF.toInt(),0x00FFFFFF,Shader.TileMode.CLAMP);c.drawRoundRect(RectF(3f,3f,125f,125f),32f,32f,p)}
  ContextCompat.getDrawable(context,IslandMotionResources.drawable(scene,frame))?.mutate()?.let{d->if(monochrome)d.setTint(Color.WHITE);d.setBounds(13,13,115,115);d.draw(c)}
  return b
 }
}
