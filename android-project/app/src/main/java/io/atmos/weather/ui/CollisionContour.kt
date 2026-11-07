package io.atmos.weather.ui
import kotlin.math.*

/** A glyph's real outline and occupancy mask, sampled once when its font or digits change. */
internal class CollisionContour(val points:List<RainCollisionWorld.Point>,val mask:BooleanArray,
 val cellsWide:Int,val cellsHigh:Int,val width:Float,val height:Float,val offsetX:Float,val offsetY:Float) {
 private val lengths=FloatArray(points.size+1)
 init{for(i in points.indices){val a=points[i];val b=points[(i+1)%points.size];lengths[i+1]=lengths[i]+hypot(b.x-a.x,b.y-a.y)}}
 val length get()=lengths.last().coerceAtLeast(.001f)
 fun contains(x:Float,y:Float):Boolean {
  val a=(x/width*cellsWide).toInt();val b=(y/height*cellsHigh).toInt()
  return x>=0&&y>=0&&a in 0 until cellsWide&&b in 0 until cellsHigh&&mask[b*cellsWide+a]
 }
 fun roof(x:Float):Float {
  if(x<0||x>width)return Float.POSITIVE_INFINITY
  val col=(x/width*cellsWide).toInt().coerceIn(0,cellsWide-1)
  for(row in 0 until cellsHigh)if(mask[row*cellsWide+col])return row.toFloat()/cellsHigh*height
  return Float.POSITIVE_INFINITY
 }
 fun nearest(x:Float,y:Float):Float=points.indices.minByOrNull{val p=points[it];(p.x-x).pow(2)+(p.y-y).pow(2)}?.let{lengths[it]}?:0f
 fun point(distance:Float):RainCollisionWorld.Point {
  val d=((distance%length)+length)%length
  val index=(lengths.binarySearch(d).let{if(it>=0)it else -it-2}).coerceIn(0,points.lastIndex)
  val a=points[index];val b=points[(index+1)%points.size]
  val t=(d-lengths[index])/(lengths[index+1]-lengths[index]).coerceAtLeast(.00001f)
  return RainCollisionWorld.Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
 }
}
