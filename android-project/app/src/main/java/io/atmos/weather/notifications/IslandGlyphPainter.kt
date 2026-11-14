package io.atmos.weather.notifications

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
/**
 * Island artwork from the shared original vector atlas.
 * The island is already a black pill, so its glyph has a transparent background and fills the
 * frame. The status-bar ticker has no pill of its own and gets the glyph on a dark chip, which
 * stays visible on a light or a dark bar. Always-on display gets a white-only glyph.
 */
object IslandGlyphPainter {
 private const val SIZE=144
 internal fun bitmap(context:Context,scene:IslandIconScene,frame:Int,monochrome:Boolean=false,chip:Boolean=false):Bitmap{
  val b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);val c=Canvas(b)
  if(chip&&!monochrome)c.drawCircle(SIZE/2f,SIZE/2f,SIZE/2f-2f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xFF1C1C1E.toInt()})
  val inset=if(chip&&!monochrome)26 else 8
  ContextCompat.getDrawable(context,IslandMotionResources.drawable(scene,frame))?.mutate()?.let{d->if(monochrome)d.setTint(Color.WHITE);d.setBounds(inset,inset,SIZE-inset,SIZE-inset);d.draw(c)}
  return b
 }
}
