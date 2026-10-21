package io.atmos.weather.presentation.home

/** Pure, Android-independent direction formatter; safe for any integer input. */
object WeatherWindDirection {
    private val directions=listOf("北","東北","東","東南","南","西南","西","西北")
    fun label(degrees: Int?):String {
        if(degrees==null) return "風向未提供"
        val normal=Math.floorMod(degrees,360)
        return "${directions[((normal+22)%360)/45]}風"
    }
}
