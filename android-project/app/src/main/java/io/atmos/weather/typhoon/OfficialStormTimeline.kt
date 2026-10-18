package io.atmos.weather.typhoon

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.*

/** Only source-issued, typed forecast fixes are admitted to the timeline. */
data class StormForecastFix(
    val latitude: Double, val longitude: Double, val time: Instant,
    val pressureHpa: Double? = null, val windMs: Double? = null,
    val gustMs: Double? = null, val radius7Km: Double? = null,
    val officialDirection: String? = null
)
data class StormTimeline(
    val stormId: String, val name: String, val typhoonNumber: String,
    val fixes: List<StormForecastFix>, val source: String = "中央氣象署",
) {
    val signature: String get() = "$stormId/${fixes.lastOrNull()?.time?.epochSecond ?: 0}"
}

object OfficialStormTimeline {
    private data class AttributeFix(
        val latitude: Double, val longitude: Double, val pressure: Double?,
        val wind: Double?, val gust: Double?, val radius7: Double?, val direction: String?,
        val chineseName: String, val number: String
    )
    private val latitudeKeys = listOf("latitude", "Latitude", "lat", "Lat")
    private val longitudeKeys = listOf("longitude", "Longitude", "lon", "Lon", "lng")

    private fun string(o: JSONObject, keys: List<String>): String = keys.firstNotNullOfOrNull {
        o.optString(it).trim().takeIf { text -> text.isNotBlank() && text != "null" }
    }.orEmpty()

    private fun number(o: JSONObject, keys: List<String>, depth: Int = 0): Double? {
        for (key in keys) {
            val raw = o.opt(key)
            when (raw) {
                is Number -> return raw.toDouble().takeIf { it.isFinite() }
                is String -> raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let { return it }
            }
        }
        if (depth >= 3) return null
        val children = o.keys()
        while (children.hasNext()) {
            val key = children.next()
            val child = o.opt(key)
            if (child is JSONObject && key !in listOf("forecast_data", "analysis_data", "records")) {
                number(child, keys, depth + 1)?.let { return it }
            }
        }
        return null
    }

    fun parse(json: JSONObject): List<StormTimeline> {
        val trackPoints = OfficialTyphoonTrack.extract(json)
        val segments = VerifiedTrackPlanner.verifiedForecastSegments(trackPoints.map {
            DatedTyphoonPosition(it.stormId, it.latitude, it.longitude,
                it.kind == TyphoonFix.Kind.FORECAST, it.timestamp)
        })
        // Metadata is optional, never invented. Values only attach to matching official coordinates.
        val attributes = mutableListOf<AttributeFix>()
        fun visit(node: Any?, depth: Int, name: String = "", number: String = "") {
            if (depth > 20 || attributes.size > 500) return
            when (node) {
                is JSONObject -> {
                    val localName = string(node,listOf("cwa_typhoon_name", "cwaTyphoonName", "cwaTyphoonNameZh", "typhoonNameZh"))
                        .ifBlank { name }
                    val localNumber = string(node,listOf("cwa_ty_no", "cwaTyNo", "typhoonNo", "TyphoonNo"))
                        .ifBlank { number }
                    // A coordinate MUST belong to this fix, not an arbitrary nested descendant.
                    val coordinate = node.optJSONObject("coordinate") ?: node
                    fun direct(keys: List<String>): Double? = keys.firstNotNullOfOrNull { key ->
                        coordinate.optString(key).toDoubleOrNull()?.takeIf { it.isFinite() }
                    }
                    val lat = direct(latitudeKeys)
                    val lon = direct(longitudeKeys)
                    if (lat != null && lon != null && lat in 0.0..55.0 && lon in 100.0..180.0) {
                        fun bounded(keys: List<String>, range: ClosedFloatingPointRange<Double>): Double? =
                            number(node, keys)?.takeIf { it in range }
                        attributes += AttributeFix(lat, lon,
                            bounded(listOf("pressure", "centralPressure", "pressure_hpa"), 800.0..1100.0),
                            bounded(listOf("max_wind_speed", "maxWindSpeed", "maxWind"), 0.0..120.0),
                            bounded(listOf("max_gust_speed", "maxGustSpeed", "maxGust"), 0.0..150.0),
                            bounded(listOf("circle_of_15ms", "radius7", "radius7Km", "sevenLevelRadius"), 0.0..900.0),
                            string(node,listOf("moving_direction", "movingDirection")).take(30).ifBlank { null },
                            localName.take(60), localNumber.take(24))
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) visit(node.opt(keys.next()),depth+1,localName,localNumber)
                }
                is JSONArray -> for (i in 0 until minOf(node.length(),300)) visit(node.opt(i),depth+1,name,number)
            }
        }
        visit(json,0)
        return segments.groupBy { it.first().stormId }.mapNotNull { (stormId, tracks) ->
            val ordered = tracks.flatten().distinctBy { it.issuedTime }
                .sortedBy { VerifiedTrackPlanner.parseTime(it.issuedTime) }
            val fixes = ordered.mapNotNull fix@ { item ->
                val time = VerifiedTrackPlanner.parseTime(item.issuedTime) ?: return@fix null
                val matching = attributes.firstOrNull {
                    abs(it.latitude-item.latitude) < 0.0001 && abs(it.longitude-item.longitude) < 0.0001
                }
                StormForecastFix(item.latitude,item.longitude,time,matching?.pressure,matching?.wind,
                    matching?.gust,matching?.radius7,matching?.direction)
            }
            if (fixes.size < 2) return@mapNotNull null
            val match = attributes.firstOrNull { att -> fixes.any { f ->
                abs(att.latitude-f.latitude)<0.0001 && abs(att.longitude-f.longitude)<0.0001 &&
                    (att.chineseName.isNotBlank() || att.number.isNotBlank())
            } }
            StormTimeline(stormId,match?.chineseName?.ifBlank { stormId } ?: stormId,
                match?.number.orEmpty(),fixes)
        }
    }
}

/** A conservative *display* trigger, not an official landfall prediction or warning. */
object TaiwanStormRelevance {
    // Taiwan and offshore islands: Taiwan, Penghu, Kinmen, Matsu.
    private val anchors = listOf(23.65 to 121.0, 23.57 to 119.57, 24.44 to 118.32, 26.15 to 119.95)
    private fun distanceKm(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double {
        val dLat=Math.toRadians(lat2-lat1); val dLon=Math.toRadians(lon2-lon1)
        val a=sin(dLat/2).pow(2) + cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(dLon/2).pow(2)
        return 12742.0*asin(sqrt(a.coerceIn(0.0,1.0)))
    }
    fun isMovingTowardTaiwan(storm:StormTimeline,now:Instant=Instant.now()):Boolean {
        val upcoming=storm.fixes.filter { it.time.epochSecond in (now.epochSecond-3600)..(now.epochSecond+8*86400) }
        if (upcoming.size < 2) return false
        fun distance(f:StormForecastFix) = anchors.minOf { distanceKm(it.first,it.second,f.latitude,f.longitude) }
        val first=distance(upcoming.first())
        val closest=upcoming.drop(1).minOf(::distance)
        // Notify only if the issued path approaches land/island centers materially and comes nearby.
        return closest <= 550.0 && closest < first-40.0
    }
}
