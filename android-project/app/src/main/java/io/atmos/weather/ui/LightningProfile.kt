package io.atmos.weather.ui

import kotlin.math.*
import kotlin.random.Random

/** Two or three irregular return strokes per event, followed by a dark recovery interval. */
internal data class LightningProfile(val pulses:List<Pulse>,val duration:Float) {
 data class Pulse(val start:Float,val duration:Float,val peak:Float)
 fun intensity(age:Float):Float {
  if(!age.isFinite()||age<0f||age>duration)return 0f
  var light=0f
  for(p in pulses){val t=age-p.start
   if(t in 0f..p.duration){val a=if(t<.014f)t/.014f else ((p.duration-t)/(p.duration-.014f)).coerceIn(0f,1f).pow(1.6f)
    light=max(light,p.peak*a)}
  }
  return if(light>0f)light else -.18f*(1f-age/duration)
 }
 companion object {
  fun fromSeed(seed:Int):LightningProfile {
   val r=Random(seed);val count=if(r.nextFloat()<.38f)3 else 2
   var at=.045f+r.nextFloat()*.055f
   val p=(0 until count).map{i->
    val pulse=Pulse(at,.075f+r.nextFloat()*.060f,if(i==0).88f+r.nextFloat()*.12f else .50f+r.nextFloat()*.50f)
    at+=.28f+r.nextFloat()*.22f;pulse
   }
   return LightningProfile(p,p.last().start+p.last().duration+.30f)
  }
 }
}
internal data class LightningGeometry(val trunk:List<RainCollisionWorld.Point>,val branches:List<List<RainCollisionWorld.Point>>) {
 companion object {
  fun create(seed:Int,width:Float,height:Float,endX:Float,endY:Float):LightningGeometry {
   val r=Random(seed);val end=RainCollisionWorld.Point(endX,endY)
   val start=RainCollisionWorld.Point((endX+(r.nextFloat()-.5f)*width*.46f).coerceIn(0f,width),-12f)
   fun split(a:RainCollisionWorld.Point,b:RainCollisionWorld.Point,displacement:Float,depth:Int):List<RainCollisionWorld.Point> {
    if(depth==0)return listOf(a,b)
    val dx=b.x-a.x;val dy=b.y-a.y;val length=hypot(dx,dy).coerceAtLeast(.01f)
    val t=.38f+r.nextFloat()*.24f;val shift=(r.nextFloat()-.5f)*displacement
    val m=RainCollisionWorld.Point(a.x+dx*t-dy/length*shift,a.y+dy*t+dx/length*shift)
    return split(a,m,displacement*.52f,depth-1).dropLast(1)+split(m,b,displacement*.52f,depth-1)
   }
   val trunk=split(start,end,min(width*.20f,(endY+12f)*.22f),5)
   val branches=(0 until (2+r.nextInt(3))).map{
    val a=trunk[5+r.nextInt(21)]
    val side=if(r.nextBoolean())1f else -1f
    val b=RainCollisionWorld.Point((a.x+side*width*(.05f+r.nextFloat()*.12f)).coerceIn(0f,width),
     (a.y+height*(.025f+r.nextFloat()*.10f)).coerceAtMost(endY*.96f))
    split(a,b,width*.045f,3)
   }
   return LightningGeometry(trunk,branches)
  }
 }
}
