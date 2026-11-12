package io.atmos.weather.ui

import kotlin.math.floor

/** Seeded continuous noise: independent motion without per-frame random jitter or synchronized sine loops. */
internal object MotionNoise {
 private fun point(cell:Int,seed:Int):Float {
  var x=cell xor (seed*0x45d9f3b)
  x=(x xor (x ushr 16))*0x45d9f3b
  x=(x xor (x ushr 16))*0x45d9f3b
  return ((x xor (x ushr 16)) ushr 8)/16777215f*2f-1f
 }
 fun value(time:Float,seed:Int,frequency:Float=1f):Float {
  if(!time.isFinite()||!frequency.isFinite())return 0f
  val t=(time.toDouble()*frequency).coerceIn(-1e8,1e8)
  val n=floor(t).toInt();val f=(t-floor(t)).toFloat();val smooth=f*f*(3f-2f*f)
  return point(n,seed)*(1-smooth)+point(n+1,seed)*smooth
 }
}
