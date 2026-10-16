package io.atmos.weather.typhoon

import org.json.JSONArray
import org.json.JSONObject

/** Conservative coordinate extractor. Uses only coordinates actually supplied in CWA's JSON.
 * Never fabricates forecast times, a cone, a current warning, or storm coordinates.
 */
data class TyphoonFix(val latitude:Double,val longitude:Double,val kind:Kind,val timestamp:String,val stormId:String="") {
    enum class Kind { OBSERVED, FORECAST, UNSPECIFIED }
}
object OfficialTyphoonTrack {
    private val latKeys=listOf("latitude","lat","Latitude","Lat")
    private val lonKeys=listOf("longitude","lon","lng","Longitude","Lon","Lng")
    private fun coord(obj:JSONObject,keys:List<String>):Double? = keys.firstNotNullOfOrNull { key ->
        if(!obj.has(key)) null else obj.optString(key).toDoubleOrNull()
    }
    fun extract(json:JSONObject,max:Int=240):List<TyphoonFix> {
        val results=mutableListOf<TyphoonFix>()
        fun visit(node:Any?,depth:Int,trail:String,inheritedStorm:String="") {
            if(depth>18 || results.size>=max) return
            when(node) {
                is JSONObject -> {
                    val storm=listOf("typhoonNo","TyphoonNo","typhoonID","TyphoonID","typhoonName","TyphoonName")
                        .firstNotNullOfOrNull { k -> node.optString(k).trim().takeIf { it.isNotBlank() && it.length<=64 } }
                        ?: inheritedStorm
                    val lat=coord(node,latKeys)
                    val lon=coord(node,lonKeys)
                    if(lat!=null && lon!=null && lat in 0.0..55.0 && lon in 100.0..180.0) {
                        val path=trail.lowercase()
                        val kind=when {
                            "forecast" in path || "預報" in trail || "prediction" in path -> TyphoonFix.Kind.FORECAST
                            "past" in path || "history" in path || "observ" in path || "實際" in trail -> TyphoonFix.Kind.OBSERVED
                            else -> TyphoonFix.Kind.UNSPECIFIED
                        }
                        val time=listOf("validTime","time","Time","dateTime","DateTime","forecastTime")
                            .firstNotNullOfOrNull { node.optString(it).takeIf(String::isNotBlank) }.orEmpty()
                        results+=TyphoonFix(lat,lon,kind,time.take(40),storm)
                    }
                    val iter=node.keys()
                    while(iter.hasNext() && results.size<max) {
                        val name=iter.next()
                        visit(node.opt(name),depth+1,"$trail/$name",storm)
                    }
                }
                is JSONArray -> for(i in 0 until minOf(node.length(),500)) {
                    if(results.size>=max) break
                    visit(node.opt(i),depth+1,trail,inheritedStorm)
                }
            }
        }
        visit(json,0,"")
        return results.distinctBy { listOf(it.latitude,it.longitude,it.kind,it.timestamp,it.stormId) }.take(max)
    }
}
