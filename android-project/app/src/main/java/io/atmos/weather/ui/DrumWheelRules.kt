package io.atmos.weather.ui
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
object DrumWheelRules {
 data class Step(val value:Int,val offset:Float)
 fun move(value:Int,offset:Float,delta:Float,count:Int,wrap:Boolean):Step {
  require(count>0);val raw=(offset+delta).takeIf{it.isFinite()}?:0f;val step=raw.roundToInt()
  if(wrap)return Step(Math.floorMod(value-step,count),raw-step)
  val next=(value-step).coerceIn(0,count-1);val rest=raw-(value-next)
  return Step(next,if(abs(rest)>.5f)(if(rest<0)-1 else 1)*(.5f+.32f*(abs(rest)-.5f)/(1+abs(rest)-.5f)) else rest)
 }
 fun date(year:Int,month:Int,day:Int):LocalDate=LocalDate.of(year,month,1).let{it.withDayOfMonth(day.coerceIn(1,it.lengthOfMonth()))}
 fun hour24(hour12:Int,pm:Boolean)=Math.floorMod(hour12,12)+if(pm)12 else 0
}
