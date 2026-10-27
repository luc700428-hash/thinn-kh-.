package io.atmos.weather.next
import io.atmos.weather.domain.model.WeatherCondition
object AmbientSoundPolicy {
    fun path(condition:WeatherCondition?,scene:WeatherScene?,night:Boolean):String? {
        if(condition==null||condition==WeatherCondition.UNKNOWN)return null
        val file=when {
            condition==WeatherCondition.CLOUDY->if(night)"cloudy_night" else "cloudy_day"
            scene==WeatherScene.SUNNY&&night->"clear_night"
            scene!=null->scene.name.lowercase()
            else->if(night)"cloudy_night" else "cloudy_day"
        }
        return "next_weather/ambience/$file.wav"
    }
}
