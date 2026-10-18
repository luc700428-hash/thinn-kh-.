package io.atmos.weather.typhoon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.net.ssl.HttpsURLConnection

/** Independent public fallback; NOT a Taiwan CWA typhoon warning/official forecast. */
data class GdacsCyclone(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Instant,
    val alertLevel: String
)

object GdacsCycloneFeed {
    private const val ENDPOINT = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/MAP?eventtypes=TC"
    private const val MAX_BYTES = 1_500_000

    private fun date(value: String?): Instant? {
        val text=value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(text.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    /** Decode a published GDACS GeoJSON event, not a forecast timeline. */
    fun parse(root: JSONObject, now: Instant = Instant.now()): List<GdacsCyclone> {
        if(root.optString("type") != "FeatureCollection") return emptyList()
        val features=root.optJSONArray("features") ?: return emptyList()
        val output=mutableListOf<GdacsCyclone>()
        for(i in 0 until minOf(features.length(),100)) {
            val feature=features.optJSONObject(i) ?: continue
            val p=feature.optJSONObject("properties") ?: continue
            val eventType=p.optString("eventtype",p.optString("eventType","TC"))
            if(!eventType.equals("TC",true)) continue
            val geometry=feature.optJSONObject("geometry") ?: continue
            if(geometry.optString("type")!="Point") continue
            val coords=geometry.optJSONArray("coordinates") ?: continue
            val lon=coords.optDouble(0,Double.NaN)
            val lat=coords.optDouble(1,Double.NaN)
            // Western North Pacific / South China Sea. A regional event is not a Taiwan warning.
            if(!lat.isFinite() || !lon.isFinite() || lat !in 6.0..40.0 || lon !in 106.0..150.0) continue
            val last=listOf("todate","toDate","datetime","date","lastupdate","lastUpdate")
                .firstNotNullOfOrNull { date(p.optString(it).takeIf(String::isNotBlank)) }
                ?: continue // never show undated archives as active storms
            val ageSec=now.epochSecond-last.epochSecond
            if(ageSec !in (-48L*3600L)..(96L*3600L)) continue
            val id=p.optLong("eventid", p.optLong("eventId",-1L))
            if(id <= 0) continue
            val name=listOf("name","eventname","eventName","tcname","cyclonename")
                .firstNotNullOfOrNull { p.optString(it).trim().takeIf { v->v.isNotEmpty() && v.length<=100 } }
                ?: "熱帶氣旋 #$id"
            output+=GdacsCyclone(id,name,lat,lon,last,p.optString("alertlevel", "").take(15))
        }
        return output.distinctBy(GdacsCyclone::id).sortedByDescending(GdacsCyclone::updatedAt).take(8)
    }

    /** No API key. Limits bandwidth, redirects and hangs; caller distinguishes failure from no active event. */
    suspend fun read(): List<GdacsCyclone> = withContext(Dispatchers.IO) {
        val c=URL(ENDPOINT).openConnection() as HttpsURLConnection
        c.connectTimeout=8_000
        c.readTimeout=10_000
        c.instanceFollowRedirects=false
        c.setRequestProperty("Accept","application/geo+json, application/json")
        c.setRequestProperty("User-Agent","thinn-khi-weather/23")
        try {
            check(c.responseCode==200) { "GDACS HTTP ${c.responseCode}" }
            check(c.contentLengthLong in -1L..MAX_BYTES.toLong()) { "GDACS response too large" }
            val buffer=ByteArrayOutputStream()
            c.inputStream.use { stream ->
                val part=ByteArray(8192)
                while(true) {
                    val n=stream.read(part)
                    if(n<0) break
                    if(buffer.size()+n>MAX_BYTES) error("GDACS payload exceeds limit")
                    buffer.write(part,0,n)
                }
            }
            parse(JSONObject(buffer.toString("UTF-8")))
        } finally { c.disconnect() }
    }
}
