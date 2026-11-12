package io.atmos.weather.ui
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal data class WeatherSkyPalette(val top:Color,val middle:Color,val bottom:Color) {
 val ink:Color get()=if(colorContrast(Color(0xFF23312F),top)>=4.5f)Color(0xFF23312F) else Color(0xFFF5FAFC)
 val secondary get()=ink.copy(alpha=.85f)
 val dark get()=colorContrast(Color.White,top)>colorContrast(Color.Black,top)
 companion object {
  fun create(phase:WeatherDayPhase,night:Float,cloud:Float):WeatherSkyPalette {
   val p=BrandPalette.light
   val warmth=when(phase){WeatherDayPhase.MORNING->.18f;WeatherDayPhase.EVENING->.30f;else->0f}
   val daylight=listOf(lerp(lerp(p.background,Color(0xFFFFEDD2),warmth),Color(0xFFAFBFCD),cloud*.48f),
    lerp(p.primaryContainer,Color(0xFFE7ECEF),cloud*.48f),lerp(p.background,Color(0xFFF1E7E0),warmth*.5f))
   val darkness=listOf(Color(0xFF101A29),Color(0xFF1F3042),Color(0xFF344554))
   return WeatherSkyPalette(lerp(daylight[0],darkness[0],night),lerp(daylight[1],darkness[1],night),lerp(daylight[2],darkness[2],night))
  }
 }
}
