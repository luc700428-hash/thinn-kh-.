package io.atmos.weather.domain.visual

import android.content.Context
import org.json.JSONObject
import io.atmos.weather.domain.model.GeoPoint

/** V2 content catalog; a pending model must never be rendered as if a verified 3D landmark. */
data class LandmarkPlace(
    val id:String, val county:String, val displayRegion:String, val island:String?,
    val name:String, val model:String?, val preview:String?, val geometryStatus:String
) {
    val caption:String get() = "$displayRegion、$name"
    val hasModel:Boolean get() = geometryStatus == "original_prototype" && model != null && preview != null
}

class LandmarkRegistry private constructor(val places:List<LandmarkPlace>, val islands:List<IslandRegion>) {
    companion object {
        fun load(context:Context):LandmarkRegistry = context.assets.open("landmarks/catalog_v2.json").bufferedReader().use { reader ->
            val json=JSONObject(reader.readText())
            val sites=json.getJSONArray("landmarks")
            val places=buildList {
                for(i in 0 until sites.length()) {
                    val p=sites.getJSONObject(i)
                    add(LandmarkPlace(p.getString("id"),p.getString("county"),p.getString("display_region"),
                        p.optString("island").takeIf { it.isNotBlank() && it!="null" },
                        p.getString("name"),p.optString("model").takeIf { it.isNotBlank() && it!="null" },
                        p.optString("preview").takeIf { it.isNotBlank() && it!="null" },
                        p.getString("geometry_status")))
                }
            }
            val jIslands=json.getJSONArray("island_regions")
            val islands=buildList {
                for(i in 0 until jIslands.length()){
                    val x=jIslands.getJSONObject(i)
                    add(IslandRegion(x.getString("name"),x.getString("county"),
                        x.getDouble("latitude"),x.getDouble("longitude"),x.getDouble("radius_km")))
                }
            }
            LandmarkRegistry(places,islands)
        }
    }

    /** Island first, then county. Restrict island proximity to avoid placing visitors at sea. */
    fun islandFor(point:GeoPoint,label:String=""):IslandRegion? =
        LandmarkGeo.closestIsland(point.latitude,point.longitude,islands)

    fun inRegion(county:String,island:String?):List<LandmarkPlace> {
        val scoped=places.filter { it.county==county && it.island==island }
        return if(scoped.isNotEmpty()) scoped else places.filter { it.county==county && it.island==null }
    }
    fun forLocation(point:GeoPoint,label:String):List<LandmarkPlace> {
        val island=islandFor(point,label)
        val county=island?.county ?: TaiwanLandmarks.countyFor(label,point)
        if(county!=null) return inRegion(county,island?.name)
        return places.filter { label.contains(it.county) }
    }

    /** Choose locally modelled first; retain unmodelled candidates for a transparent catalog. */
    fun playable(point:GeoPoint,label:String):List<LandmarkPlace> = forLocation(point,label)
        .filter { it.hasModel }.ifEmpty { forLocation(point,label) }

}
