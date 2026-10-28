package io.atmos.weather.earthquake

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class StationAvailability { UNKNOWN, ONLINE, OFFLINE, MAINTENANCE }

/** Metadata and live magnitudes are untrusted, independent third-party telemetry.
 * Never convert a single station's reading into a nationwide EEW alarm.
 */
data class SeismicStation(
    val id: String,
    val label: String,
    val latitude: Double?,
    val longitude: Double?,
    val intensity: Double?,
    val accelerationGal: Double?,
    val availability: StationAvailability = StationAvailability.UNKNOWN,
    val observedAtMs:Long?=null
)

object SeismicStationData {
    /** Provider can return a root map, nested keyed map, or array. Unknown frames stay empty. */
    fun parse(raw:String,metadata:Map<String,SeismicStation> = emptyMap(),receivedAtMs:Long?=null):List<SeismicStation> {
        val root = runCatching { if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONObject(raw) }.getOrNull()
            ?: return emptyList()
        val envelope=root as? JSONObject
        val frameTime=envelope?.let {o->listOf("ts","timestamp","time","updated").firstNotNullOfOrNull{ExpTechRealtimeParser.parseEpoch(o.opt(it))}}
        val seen = LinkedHashMap<String, SeismicStation>()
        fun number(o: JSONObject, vararg names: String): Double? = names.firstNotNullOfOrNull { key ->
            o.opt(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
        }
        fun walk(node: Any?, key: String?, depth: Int) {
            if (depth > 4 || seen.size >= 2500) return
            when (node) {
                is JSONArray -> for (i in 0 until minOf(node.length(), 2500)) walk(node.opt(i), null, depth + 1)
                is JSONObject -> {
                    val id = listOf("id", "stationId", "station_id", "code").firstNotNullOfOrNull { n ->
                        node.optString(n).takeIf { it.isNotBlank() }
                    } ?: key
                    val info = node.optJSONObject("info") ?: node.optJSONObject("position") ?: node.optJSONObject("location") ?: node
                    val lat = number(info, "lat", "latitude") ?: number(node,"lat","latitude")
                    val lon = number(info, "lon", "lng", "longitude") ?: number(node,"lon","lng","longitude")
                    val known = id?.let { metadata[it] }
                    val locLat = lat ?: known?.latitude
                    val locLon = lon ?: known?.longitude
                    // Read only explicitly supplied values; no synthetic or guessed station intensities.
                    val level = number(node, "i", "int", "intensity", "level")?.takeIf { it in 0.0..9.0 }
                    val pga = number(node, "pga", "gal", "acc", "acceleration")?.takeIf { it in 0.0..10000.0 }
                    if (id != null && id.length <= 100 && (locLat != null || known != null || level != null || pga != null)) {
                        val name = node.optString("name").ifBlank { node.optString("loc") }.ifBlank { known?.label ?: id }
                        // Only EXPLICIT per-station provider state can justify a red offline marker.
                        // A missing frame, missing intensity or a stale SSE never proves a station is broken.
                        val status = node.optString("status").lowercase()
                        val availability = when {
                            status in setOf("maintenance", "maintaining", "repair", "repairing", "維修") -> StationAvailability.MAINTENANCE
                            status in setOf("offline", "disconnected", "down", "離線") ||
                                (node.has("online") && node.opt("online") == false) -> StationAvailability.OFFLINE
                            status in setOf("online", "connected", "active", "正常") ||
                                (node.has("online") && node.opt("online") == true) -> StationAvailability.ONLINE
                            else -> StationAvailability.UNKNOWN
                        }
                        val point = SeismicStation(id, name.take(70),
                            locLat?.takeIf { it in 21.0..26.5 }, locLon?.takeIf { it in 118.0..123.5 },level,pga,availability,
                            if(level!=null||pga!=null)listOf("ts","timestamp","time","updated").firstNotNullOfOrNull{ExpTechRealtimeParser.parseEpoch(node.opt(it))}?:frameTime?:receivedAtMs else null)
                        if (point.latitude != null && point.longitude != null) seen[id] = point
                    }
                    if (id == null || (!node.has("i") && !node.has("pga") && !node.has("lat") && !node.has("intensity"))) {
                        // Container objects and API envelopes; traverse only expected shapes.
                        for (next in listOf("data", "station", "stations", "list", "items", "rts", "result", "network")) {
                            if (node.has(next)) walk(node.opt(next),null,depth+1)
                        }
                        // Keyed station dictionary: { "123": { ... }, ... }
                        if (depth <= 3) for (name in node.keys()) {
                            if (name in setOf("data", "station", "stations", "list", "items", "rts", "result", "network", "time", "timestamp")) continue
                            val child = node.opt(name)
                            if (child is JSONObject && (child.has("lat") || child.has("pga") || child.has("i") || metadata.containsKey(name)))
                                walk(child,name,depth+1)
                        }
                    }
                }
            }
        }
        walk(root,null,0)
        return seen.values.toList()
    }

    /** Station index uses the current public CSV directory (id is the hex device identifier).
     * Never guess coordinates for stations missing from the provider directory.
     */
    fun parseStationCsv(text: String): Map<String, SeismicStation> {
        val lines=text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return emptyMap()
        val heading=lines.next().trim().removePrefix("\uFEFF").split(',').map { it.trim().lowercase() }
        val idCol=heading.indexOf("id")
        val latCol=heading.indexOf("lat")
        val lonCol=heading.indexOf("lon")
        val locCol=heading.indexOf("loc_code")
        if (idCol<0 || latCol<0 || lonCol<0) return emptyMap()
        val result=LinkedHashMap<String,SeismicStation>()
        while (lines.hasNext() && result.size < 3000) {
            val row=lines.next().split(',')
            if (row.size <= maxOf(idCol,latCol,lonCol)) continue
            val id=row[idCol].trim().takeIf { it.matches(Regex("[0-9a-fA-F]{3,32}")) } ?: continue
            val lat=row[latCol].trim().toDoubleOrNull()?.takeIf { it in 21.0..26.5 } ?: continue
            val lon=row[lonCol].trim().toDoubleOrNull()?.takeIf { it in 118.0..123.5 } ?: continue
            val label=if (locCol>=0 && locCol<row.size) row[locCol].trim() else id
            result[id] = SeismicStation(id,label.ifBlank { id }.take(50),lat,lon,null,null)
        }
        return result
    }

    /** Try each documented static Core region, rather than the legacy api-1 endpoint. */
    fun fetchMetadata(): Map<String, SeismicStation> {
        for (host in listOf("static.core-tnn1.exptech.dev", "static.core-tyo1.exptech.dev")) {
            val result=runCatching {
                val conn=(URL("https://$host/resource/station").openConnection() as HttpURLConnection).apply {
                    connectTimeout=6500; readTimeout=9000
                    setRequestProperty("Accept","text/csv, */*")
                }
                try {
                    if (conn.responseCode!=200) emptyMap()
                    else parseStationCsv(conn.inputStream.bufferedReader().use { it.readText() })
                } finally {conn.disconnect()}
            }.getOrDefault(emptyMap())
            if(result.isNotEmpty()) return result
        }
        return emptyMap()
    }
}
