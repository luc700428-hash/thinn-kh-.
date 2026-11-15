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
    val shakingAreas: List<Pair<String,String>> = emptyList(),
    /** CWA report number (e.g. 115079); null for small regional reports and other sources. */
    val number: String? = null,
    val imageUrl: String? = null,
    val shakemapUrl: String? = null,
    val stations: List<ReportStation> = emptyList(),
    val magnitudeType: String? = null
) {
    /** "雲林縣古坑鄉" from "...(位於雲林縣古坑鄉)", otherwise the CWA location text. */
    val shortLocation: String get() =
        Regex("""位於([^)）]+)[)）]""").find(location)?.groupValues?.get(1)?.trim()?.takeIf{it.isNotBlank()}
            ?: location.substringBefore('(').trim().ifBlank{location}
    /** County -> highest intensity, for colouring maps (keys use 臺). */
    val countyIntensity: Map<String,String> get() = shakingAreas.groupBy({it.first.replace('台','臺').removeSuffix("地區")},{it.second})
        .mapValues{(_,v)->v.maxByOrNull(CwaEarthquakeParser::intensityRank)!!.removeSuffix("級")}
}

/** One seismic station reading inside a CWA report. */
data class ReportStation(val county:String,val name:String,val intensity:String,val distanceKm:Double?,
    val latitude:Double?,val longitude:Double?,val pgaGal:Double?)

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
        val intensityAreas = (0 until (shakingAreas?.length() ?: 0)).flatMap { n ->
            val area = shakingAreas?.optJSONObject(n) ?: return@flatMap emptyList()
            val intensity = area.optString("AreaIntensity")
            // CountyName may list several counties ("臺中市、彰化縣"); AreaDesc is a fallback.
            val names = area.optString("CountyName").split('、',',').map{it.trim()}.filter{it.isNotBlank()}
                .ifEmpty { listOf(area.optString("AreaDesc").removeSuffix("地區")).filter{it.isNotBlank()} }
            if (intensity.isBlank()) emptyList() else names.map { it to intensity }
        }.distinct().sortedByDescending { intensityRank(it.second) }
        val stations = (0 until (shakingAreas?.length() ?: 0)).flatMap { n ->
            val area = shakingAreas?.optJSONObject(n) ?: return@flatMap emptyList()
            val list = area.optJSONArray("EqStation") ?: return@flatMap emptyList()
            (0 until list.length()).mapNotNull { k ->
                val st = list.optJSONObject(k) ?: return@mapNotNull null
                val name = st.optString("StationName").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val grade = st.optString("SeismicIntensity").removeSuffix("級").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                fun d(o: JSONObject?, key: String) = o?.opt(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
                ReportStation(area.optString("CountyName").substringBefore('、'), name, grade,
                    d(st,"EpicenterDistance"), d(st,"StationLatitude"), d(st,"StationLongitude"), d(st.optJSONObject("pga"),"IntScaleValue"))
            }
        }.distinctBy { it.name }.sortedWith(compareByDescending<ReportStation>{ intensityRank(it.intensity) }.thenBy { it.distanceKm ?: 999.0 })
        fun cwaUrl(key: String) = o.optString(key).takeIf { it.startsWith("https://scweb.cwa.gov.tw/") || it.startsWith("https://www.cwa.gov.tw/") }
        val number = o.opt("EarthquakeNo")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        return EarthquakeReport(id, origin, location, magnitude, depth, lat, lon, top,
            o.optString("ReportContent").ifBlank { "${location}發生規模 ${magnitude} 地震" }, officialUrl, category, intensityAreas,
            number = number?.takeIf { it.isNotBlank() }, imageUrl = cwaUrl("ReportImageURI"), shakemapUrl = cwaUrl("ShakemapImageURI"),
            stations = stations, magnitudeType = magnitudeObj?.optString("MagnitudeType")?.takeIf { it.isNotBlank() })
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
