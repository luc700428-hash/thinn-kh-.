package io.atmos.weather.next
import kotlin.math.cos
object WeatherWakePolicy {
 val originalThemes=setOf("dawn","rain","wood","water","fire","metal")
 val paletteThemes=setOf("guitar","flute","rhodes","kalimba","strings","musicbox","synth","groove")
 val popThemes=setOf("citypop","tropical","indiepop","rnb","futurepop","daybreak")
 val themes=originalThemes+paletteThemes+popThemes
 fun paletteArrangement(scene:WeatherScene?):String=when(scene){
  WeatherScene.SUNNY->"clear"
  WeatherScene.DRIZZLE,WeatherScene.LIGHT_RAIN,WeatherScene.MODERATE_RAIN,WeatherScene.HEAVY_RAIN,
  WeatherScene.THUNDERSTORM,WeatherScene.SEVERE_THUNDERSTORM->"rain"
  WeatherScene.LIGHT_SNOW,WeatherScene.MODERATE_SNOW,WeatherScene.HEAVY_SNOW->"snow"
  else->"neutral"
 }
 fun theme(uri:String?):String?=when{uri.isNullOrBlank()->"dawn";uri.startsWith("thinn://alarm/")->uri.removePrefix("thinn://alarm/").takeIf{it in themes};else->null}
 fun gain(ms:Long):Float{val t=(ms.coerceAtLeast(0)/18_000f).coerceIn(0f,1f);val rise=.18f+.82f*t*t*(3-2*t);val contour=(if(ms<18_000)1f else .96f+.04f*cos((ms-18_000)*2.0*Math.PI/9000).toFloat()).coerceIn(.92f,1f);return(rise*contour).coerceIn(.18f,1f)}
 fun ambienceGain(scene:WeatherScene?):Float=when(scene){WeatherScene.THUNDERSTORM,WeatherScene.SEVERE_THUNDERSTORM->.1f;null->0f;else->.16f}
}
