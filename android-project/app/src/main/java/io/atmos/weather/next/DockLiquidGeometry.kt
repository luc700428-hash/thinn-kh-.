package io.atmos.weather.next
import kotlin.math.*
internal data class DockLiquidShape(val headX:Float,val tailX:Float,val width:Float,val height:Float,
 val centerY:Float,val neckHalfHeight:Float,val connected:Boolean,val cornerRadius:Float)
internal object DockLiquidGeometry {
 fun shape(width:Float,height:Float,density:Float,count:Int,head:Float,tail:Float,vertical:Boolean=false,engagement:Float=0f):DockLiquidShape? {
  if(count<1||listOf(width,height,density,head,tail,engagement).any{!it.isFinite()}||width<=0||height<=0||density<=0)return null
  val inset=min((if(vertical)5f else 6f)*density,width*.05f);val slot=(width-2*inset)/count
  val safeHead=head.coerceIn(0f,count-1f);val safeTail=tail.coerceIn(safeHead-.48f,safeHead+.48f).coerceIn(0f,count-1f)
  val pull=abs(safeHead-safeTail);val touch=engagement.coerceIn(-.18f,1.14f)
  val rest=(slot-4*density).coerceAtLeast(1f)
  val w=(rest*(1+touch*.018f+pull*.035f)).coerceAtMost(slot-density).coerceAtLeast(1f)
  val h=((height-(if(vertical)12f else 10f)*density)*(1+touch*.025f-pull*.035f)).coerceIn(1f,(height-6*density).coerceAtLeast(1f))
  return DockLiquidShape(inset+(safeHead+.5f)*slot,inset+(safeTail+.5f)*slot,w,h,height*.5f,h*.5f*(1-pull*.35f),true,min(w,h)*.5f)
 }
}
