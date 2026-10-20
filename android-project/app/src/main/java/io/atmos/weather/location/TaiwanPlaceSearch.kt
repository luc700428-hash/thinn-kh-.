package io.atmos.weather.location

/** Search matching only: never invents coordinates or labels for missing places. */
object TaiwanPlaceSearch {
    private fun normalize(input:String):String = input.trim()
        .replace('臺','台').replace('　',' ')
        .replace(Regex("\\s+"), "")
        .replace("台灣", "").replace("台湾", "")
        .lowercase()

    fun matches(query:String, candidate:String):Boolean {
        val q=normalize(query)
        if(q.length < 2) return false
        return normalize(candidate).contains(q)
    }
}
