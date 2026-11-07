package io.atmos.weather.ui

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import io.atmos.weather.presentation.home.LocalAppearance
import kotlin.math.*

private data class TemperatureGlyph(val path:AndroidPath,val contour:CollisionContour)
private data class TemperatureGeometry(val parts:List<TemperatureGlyph>,val degreeX:Float,val degreeY:Float,val degreeSize:Float)

/** Drawing and collisions use the same native font outlines, including the holes in 0/8/9. */
@Composable internal fun WeatherTemperature(value:Int,ink:Color) {
 val density=LocalDensity.current;val appearance=LocalAppearance.current
 var pixels by remember{mutableStateOf(IntSize.Zero)}
 val face=remember(appearance.fontChoice,appearance.fontWeight){
  val name=when(appearance.fontChoice){1->"serif";2->"monospace";3->"cursive";else->"sans-serif"}
  Typeface.create(Typeface.create(name,Typeface.NORMAL),(appearance.fontWeight.toInt()-100).coerceIn(100,800),false)
 }
 val geometry=remember(value,pixels,density.density,density.fontScale,face) {
  temperatureGeometry(value.toString(),pixels.width.toFloat(),pixels.height.toFloat(),density.density,with(density){94.sp.toPx()},face)
 }
 val paint=remember(face){Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=face}}
 val contours=geometry.parts.map{it.contour}
 Canvas(Modifier.fillMaxWidth().height((126f*density.fontScale).dp).onSizeChanged{pixels=it}
  .weatherGlyphCollisionSurfaces(contours).semantics{
   contentDescription="$value 攝氏度"
  }) {
  paint.color=ink.toArgb()
  drawIntoCanvas {canvas->
   geometry.parts.forEach{canvas.nativeCanvas.drawPath(it.path,paint)}
   paint.textSize=geometry.degreeSize;canvas.nativeCanvas.drawText("°",geometry.degreeX,geometry.degreeY,paint)
  }
 }
}
private fun temperatureGeometry(text:String,w:Float,h:Float,density:Float,requested:Float,face:Typeface):TemperatureGeometry {
 if(w<=0||h<=0)return TemperatureGeometry(emptyList(),0f,0f,1f)
 val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=face;textSize=requested}
 val total=p.measureText(text)+p.measureText("°")*.5f+4*density
 if(total>w-12*density)p.textSize*=((w-12*density)/total).coerceIn(.1f,1f)
 val width=p.measureText(text);var x=(w-width)/2
 val baseline=h*.50f-(p.fontMetrics.ascent+p.fontMetrics.descent)/2
 val parts=ArrayList<TemperatureGlyph>()
 for(character in text) {
  val path=AndroidPath();p.getTextPath(character.toString(),0,1,x,baseline,path)
  val bounds=RectF();path.computeBounds(bounds,true)
  if(bounds.width()>0&&bounds.height()>0) {
   val region=Region();region.setPath(path,Region(floor(bounds.left).toInt(),floor(bounds.top).toInt(),ceil(bounds.right).toInt()+1,ceil(bounds.bottom).toInt()+1))
   val measure=PathMeasure(path,true);var longest=0f;var samples=emptyList<RainCollisionWorld.Point>()
   do{if(measure.length>longest){longest=measure.length;val pos=FloatArray(2)
    samples=(0 until 160).map{i->measure.getPosTan(longest*i/160,pos,null)
     RainCollisionWorld.Point((pos[0]-bounds.left)/density,(pos[1]-bounds.top)/density)}
   }}while(measure.nextContour())
   val dw=bounds.width()/density;val dh=bounds.height()/density
   val cols=ceil(dw*2).toInt().coerceIn(1,600);val rows=ceil(dh*2).toInt().coerceIn(1,600)
   val mask=BooleanArray(cols*rows){i->region.contains((bounds.left+(i%cols+.5f)/cols*bounds.width()).toInt(),(bounds.top+(i/cols+.5f)/rows*bounds.height()).toInt())}
   parts+=TemperatureGlyph(path,CollisionContour(samples,mask,cols,rows,dw,dh,bounds.left/density,bounds.top/density))
  }
  x+=p.measureText(character.toString())
 }
 return TemperatureGeometry(parts,(w+width)/2+3*density,baseline-p.textSize*.40f,p.textSize*.5f)
}
