package io.atmos.weather.voice

/** No Android dependencies: safe deterministic event ordering. Smaller number is more urgent. */
object NaturalVoicePriority {
    const val EARTHQUAKE=0
    const val WEATHER_ALERT=1
    const val DAILY=2
    const val PERSONAL=3
    fun preempts(incoming:Int,playing:Int):Boolean = incoming<playing
    fun valid(value:Int):Boolean=value in EARTHQUAKE..PERSONAL
}
