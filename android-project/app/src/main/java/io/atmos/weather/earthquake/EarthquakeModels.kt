package io.atmos.weather.earthquake

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** CWA post-earthquake reports, NOT earth­quake early warnings. */
data class EarthquakeReport(
    val id: String,
    val origin: Instant,
    val location: String,
    val magnitude: Double,
    val depthKm: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val maxIntensity: String?,
    val reportText: String,
    val officialUrl: String,
    val category: String,
    val shakingAreas: List<Pair<String,String>> = emptyList()
)

object CwaEarthquakeParser {
    private val taipei = ZoneId.of("Asia/Taipei")
    fun parse(json: JSONObject, category: String): List<EarthquakeReport> {
        val records = json.optJSONObject("records") ?: return emptyList()
        val source = records.optJSONArray("Earthquake") ?: return emptyList()
        return (0 until source.length()).mapNotNull { index ->
            runCatching { parseOne(source.getJSONObject(index), category) }.getOrNull()
        }
    }
    private fun parseOne(o: JSONObject, category: String): EarthquakeReport? {
        val info = o.optJSONObject("EarthquakeInfo") ?: return null
        val origin = parseInstant(info.optString("OriginTime")) ?: return null
        val magnitudeObj = info.optJSONObject("EarthquakeMagnitude")
        val magnitude = magnitudeObj?.optDouble("MagnitudeValue") ?: Double.NaN
        if (!magnitude.isFinite() || magnitude !in 0.0..10.0) return null
        val epicenter = info.optJSONObject("Epicenter")
        val location = epicenter?.optString("Location")?.takeIf { it.isNotBlank() } ?: "臺灣周邊"
        val depth = info.optDouble("FocalDepth").takeIf { it.isFinite() && it >= 0.0 }
        val lat = epicenter?.optDouble("EpicenterLatitude")?.takeIf { it.isFinite() && it in -90.0..90.0 }
        val lon = epicenter?.optDouble("EpicenterLongitude")?.takeIf { it.isFinite() && it in -180.0..180.0 }
        val rawUrl = o.optString("Web")
        val officialUrl = rawUrl.takeIf { it.startsWith("https://www.cwa.gov.tw/") || it.startsWith("https://scweb.cwa.gov.tw/") }
            ?: "https://scweb.cwa.gov.tw/zh-tw/earthquake/data"
        val rawId = o.optString("EarthquakeNo")
        val id = "${category}_${rawId.ifBlank { origin.epochSecond.toString() }}_${origin.epochSecond}"
        val shakingAreas = o.optJSONObject("Intensity")?.optJSONArray("ShakingArea")
        val top = (0 until (shakingAreas?.length() ?: 0)).mapNotNull { n ->
            shakingAreas?.optJSONObject(n)?.optString("AreaIntensity")?.takeIf { it.isNotBlank() }
        }.maxByOrNull(::intensityRank)
        val intensityAreas = (0 until (shakingAreas?.length() ?: 0)).mapNotNull { n ->
            val area = shakingAreas?.optJSONObject(n) ?: return@mapNotNull null
            val name = area.optString("AreaDesc").ifBlank { area.optString("CountyName") }
            val intensity = area.optString("AreaIntensity")
            if (name.isBlank() || intensity.isBlank()) null else name to intensity
        }.sortedByDescending { intensityRank(it.second) }
        return EarthquakeReport(id, origin, location, magnitude, depth, lat, lon, top,
            o.optString("ReportContent").ifBlank { "${location}發生規模 ${magnitude} 地震" }, officialUrl, category, intensityAreas)
    }
    fun intensityRank(raw: String): Int = when {
        raw.contains("7") -> 9
        raw.contains("6強") -> 8
        raw.contains("6弱") -> 7
        raw.contains("5強") -> 6
        raw.contains("5弱") -> 5
        raw.contains("4") -> 4
        raw.contains("3") -> 3
        raw.contains("2") -> 2
        raw.contains("1") -> 1
        else -> 0
    }
    private fun parseInstant(s: String): Instant? {
        if (s.isBlank()) return null
        return runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).atZone(taipei).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")).atZone(taipei).toInstant() }.getOrNull()
    }
}
