package io.atmos.weather.earthquake

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Independent interoperability parser; no TREM-Lite AGPL source has been copied.
 * Fail-closed: unknown, test, cancelled or stale frames NEVER become emergency notifications.
 */
data class EewIncident(
    val id: String, val serial: Int, val source: String, val issuedAtMs: Long,
    val epicenter: String, val magnitude: Double?, val depthKm: Double?,
    val latitude: Double?, val longitude: Double?, val cancelled: Boolean,
    val test: Boolean, val urgent: Boolean = false,
    val originAtMs: Long? = null,
    val localIntensityByCounty: Map<String,String> = emptyMap()
)

data class RealtimeDisplay(
    val enabled: Boolean = false,
    val eewStatus: String = "尚未開始監測",
    val rtsStatus: String = "尚未開始監測",
    val lastEewAt: Long = 0L,
    val lastRtsAt: Long = 0L,
    val stationCount: Int? = null,
    val stations: List<SeismicStation> = emptyList(),
    val stationMetadataStatus: String = "尚未讀取測站位置",
    val incident: EewIncident? = null,
    val recentIncidents: List<EewIncident> = emptyList(),
    val lastAlertAt: Long = 0L,
    val source: String = "ExpTech DPIP · 非官方第三方轉發"
)

object ExpTechRealtimeParser {
    fun parseEew(json: String): List<EewIncident> {
        val root: Any = try { if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json) }
            catch (_: Exception) { return emptyList() }
        val candidates = mutableListOf<JSONObject>()
        fun walk(item: Any?, depth: Int) {
            if (depth > 3 || candidates.size >= 50) return
            when (item) {
                is JSONArray -> for (i in 0 until minOf(item.length(), 50)) walk(item.opt(i), depth + 1)
                is JSONObject -> {
                    if (item.has("eq") || (item.has("id") && (item.has("magnitude") || item.has("mag")))) candidates.add(item)
                    else for (key in listOf("data", "eew", "events", "items", "list", "result")) if (item.has(key)) walk(item.opt(key), depth + 1)
                }
            }
        }
        walk(root, 0)
        return candidates.mapNotNull { item ->
            val eq = item.optJSONObject("eq") ?: item
            val id = firstString(item,"id","eventId","event_id","eewId") ?: return@mapNotNull null
            val timestamp = listOf(item.opt("timestamp"),item.opt("issueTime"),item.opt("reportTime"),item.opt("time"),
                item.opt("updated"),eq.opt("time"),eq.opt("originTime")).firstNotNullOfOrNull { parseEpoch(it) } ?: return@mapNotNull null
            val test = firstBoolean(item,"test","isTest","isTraining","replay","isReplay") ||
                firstString(item,"type","status")?.contains("test",ignoreCase=true)==true
            val cancel = firstBoolean(item,"cancel","cancelled","isCancel","isCancelled") ||
                firstString(item,"status")?.let { it.contains("cancel",true) || it.contains("取消") } == true
            EewIncident(id=id,serial=item.optInt("serial",item.optInt("number",0)),
                source=firstString(item,"author","source","provider") ?: "ExpTech EEW",
                issuedAtMs=timestamp,
                epicenter=firstString(eq,"location","loc","place","epicenter") ?: "震央待確認",
                magnitude=number(eq,"mag","magnitude"), depthKm=number(eq,"depth","depthKm"),
                latitude=number(eq,"lat","latitude"),longitude=number(eq,"lon","longitude","lng"),
                cancelled=cancel,test=test,
                urgent=firstBoolean(item,"emergency","isEmergency") ||
                    firstString(item,"severity","priority")?.lowercase()?.let { it in setOf("urgent","critical","emergency") } == true,
                originAtMs=listOf(eq.opt("originTime"),eq.opt("time"),item.opt("originTime")).firstNotNullOfOrNull(::parseEpoch),
                localIntensityByCounty=countyIntensity(item,eq))
        }
    }
    private fun countyIntensity(item:JSONObject, eq:JSONObject):Map<String,String> {
        // Only explicitly county-keyed intensity fields. A max/epicentral value is NOT local intensity.
        val areas=item.optJSONObject("countyIntensities") ?: item.optJSONObject("regions")
            ?: eq.optJSONObject("countyIntensities") ?: return emptyMap()
        val result=mutableMapOf<String,String>()
        for(key in areas.keys()) {
            if(!(key.contains("市") || key.contains("縣"))) continue
            val value=areas.opt(key)
            val raw=when(value) {
                is JSONObject -> value.optString("intensity",value.optString("int"))
                else -> value?.toString()
            }
            SeismicWavePhysics.intensityLabel(raw)?.let { result[key.replace("台","臺")]=it }
        }
        return result
    }
    fun stationCount(json: String): Int? = try {
        val root=JSONObject(json)
        val station= listOf("stations","station","data","list").firstNotNullOfOrNull { root.opt(it) }
        when (station) {
            is JSONObject -> station.length()
            is JSONArray -> station.length()
            else -> null
        }
    } catch (_: Exception) { null }
    fun isFresh(incident: EewIncident, nowMs: Long = System.currentTimeMillis()): Boolean {
        val age = nowMs - incident.issuedAtMs
        return !incident.test && !incident.cancelled && age >= -15_000L && age <= 120_000L
    }
    private fun firstString(o: JSONObject, vararg fields: String): String? = fields.firstNotNullOfOrNull { field ->
        o.opt(field)?.toString()?.takeIf { it.isNotBlank() && it!="null" && it!="0" }
    }
    private fun firstBoolean(o: JSONObject, vararg fields: String): Boolean = fields.any { name ->
        o.opt(name) == true || o.optString(name, "false").equals("true",true)
    }
    private fun number(o: JSONObject,vararg fields:String):Double? = fields.firstNotNullOfOrNull { name ->
        o.opt(name)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    private fun parseEpoch(value: Any?): Long? {
        if (value == null || value == JSONObject.NULL) return null
        val raw=value.toString().trim()
        raw.toDoubleOrNull()?.let { num -> if (num > 1e12) return num.toLong(); if (num > 1e9) return (num*1000).toLong() }
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(raw.replace(' ','T'),DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()
            }.getOrNull()
    }
}
