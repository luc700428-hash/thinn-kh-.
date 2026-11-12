package io.atmos.weather.ui

import io.atmos.weather.next.WeatherScene
import kotlin.math.*

/** WMO scene controls particle size/speed, not the probability of future rain. */
internal data class WeatherPhysicsStyle(val rain:Float=0f,val snow:Float=0f,
 val speed:Float=1f,val radius:Float=1f,val trail:Float=14f,val thunder:Boolean=false) {
 val active get()=rain>0f||snow>0f
 companion object {
  fun forScene(scene:WeatherScene?)=when(scene) {
   WeatherScene.DRIZZLE->WeatherPhysicsStyle(.10f,speed=.52f,radius=.48f,trail=3f)
   WeatherScene.LIGHT_RAIN->WeatherPhysicsStyle(.28f,speed=.76f,radius=.72f,trail=8f)
   WeatherScene.MODERATE_RAIN->WeatherPhysicsStyle(.50f,trail=15f)
   WeatherScene.HEAVY_RAIN->WeatherPhysicsStyle(.84f,speed=1.12f,radius=1.30f,trail=25f)
   WeatherScene.THUNDERSTORM->WeatherPhysicsStyle(.66f,speed=1.04f,radius=1.12f,trail=20f,thunder=true)
   WeatherScene.SEVERE_THUNDERSTORM->WeatherPhysicsStyle(1f,speed=1.20f,radius=1.45f,trail=31f,thunder=true)
   WeatherScene.LIGHT_SNOW->WeatherPhysicsStyle(snow=.22f)
   WeatherScene.MODERATE_SNOW->WeatherPhysicsStyle(snow=.52f)
   WeatherScene.HEAVY_SNOW->WeatherPhysicsStyle(snow=1f)
   else->WeatherPhysicsStyle()
  }
  fun windPixels(kmh:Float,strong:Boolean,time:Float):Float {
   val base=(if(kmh.isFinite())kmh else 0f).coerceIn(-190f,190f)*3.2f
   val gust=if(strong)1f+.24f*MotionNoise.value(time,731,.35f)+.16f*MotionNoise.value(time,293,1.4f) else 1f+.08f*MotionNoise.value(time,179,.18f)
   return (base*gust).coerceIn(-620f,620f)
  }
 }
}

/** Native scene parameters, independent of whether forms use light or dark appearance. */
internal enum class WeatherDayPhase { MORNING,DAY,EVENING,NIGHT;
 companion object {
  fun resolve(day:Boolean,hour:Int)=if(!day)NIGHT else when(hour){in 0..9->MORNING;in 16..23->EVENING;else->DAY}
 }
}
