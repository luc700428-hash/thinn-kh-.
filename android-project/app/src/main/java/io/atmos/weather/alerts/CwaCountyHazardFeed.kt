package io.atmos.weather.alerts

import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.location.LocationLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection

/**
 * Official CWA W-C0033-001 published hazards, including an explicitly scheduled start.
 * Does not issue any CAP-level tsunami or typhoon warnings. No fake alerts on source failure.
 */
data class CwaCountyHazard(
    val title: String,
    val phenomenon: String,
    val county: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val startsAt: Instant = issuedAt
)

data class CwaCountyHazardsResult(
    val hazards: List<CwaCountyHazard>,
    val checkSucceeded: Boolean,
    val detail: String
)

object CwaCountyHazardFeed {
    private const val SOURCE = "https://opendata.cwa.gov.tw/fileapi/v1/opendataapi/W-C0033-001?Authorization=rdec-key-123-45678-011121314&format=JSON"
    private const val MAX_BYTES = 512_000
    const val OFFICIAL_URL = "https://www.cwa.gov.tw/V8/C/P/Warning/FIFOWS.html"
    private val taiwan = ZoneId.of("Asia/Taipei")
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun parseTime(raw: String): Instant? {
        val text = raw.trim()
        if(text.isEmpty() || text == "null") return null
        return runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text.replace('T',' '),timeFormat).atZone(taiwan).toInstant() }.getOrNull()
    }

    private fun asObjects(raw: Any?): List<JSONObject> = when(raw) {
        is JSONArray -> (0 until minOf(raw.length(),200)).mapNotNull { raw.optJSONObject(it) }
        is JSONObject -> listOf(raw)
        else -> emptyList()
    }

    private fun normalizedCounty(raw: String): String = raw.trim().replace('臺','台')

    /** Source data may include all counties with null hazardConditions: that's a valid no-alert response. */
    fun parse(json: JSONObject, place: String, point: GeoPoint?, now: Instant = Instant.now()): List<CwaCountyHazard> {
        val envelope = json.optJSONObject("cwaopendata") ?: error("非中央氣象署資料格式")
        val sent = parseTime(envelope.optString("sent",envelope.optString("Sent")))
            ?: error("警特報缺少官方發布時間")
        val age = now.epochSecond-sent.epochSecond
        // Do not turn historical feeds or future-dated records into active warnings.
        check(age in -600L..(7L*86400L)) { "中央氣象署警特報資料已過期" }
        val dataset = envelope.optJSONObject("dataset") ?: envelope.optJSONObject("Dataset")
            ?: error("中央氣象署資料欄位不存在")
        val locations = dataset.optJSONArray("location") ?: error("缺少縣市警特報清單")
        check(locations.length() == 22) { "縣市資料不完整，不能當作無警報" }
        val list = asObjects(locations)
        check(list.size == 22) { "縣市資料格式不完整" }
        val normPlace = LocationLabels.clean(place).replace('臺','台')
        val countyName = list.map { normalizedCounty(it.optString("locationName")) }
            .filter { it.isNotBlank() }
            .firstOrNull { normPlace.contains(it) }
            ?: point?.let(LocationLabels::countyFromPoint)?.let(::normalizedCounty)
            ?: return emptyList()
        val county = list.firstOrNull { normalizedCounty(it.optString("locationName")) == countyName }
            ?: return emptyList()
        val hazards = asObjects(county.optJSONObject("hazardConditions")?.opt("hazards"))
        return hazards.mapNotNull { hazard ->
            val info = hazard.optJSONObject("info") ?: return@mapNotNull null
            val validity = hazard.optJSONObject("validTime") ?: return@mapNotNull null
            val phenomenon = info.optString("phenomena").trim().take(50)
            val significance = info.optString("significance").trim().take(24)
            val start = parseTime(validity.optString("startTime")) ?: return@mapNotNull null
            val end = parseTime(validity.optString("endTime")) ?: return@mapNotNull null
            if(phenomenon.isBlank() || significance.isBlank() || !start.isBefore(end) ||
                !now.isBefore(end)) return@mapNotNull null
            // CWA typhoon and tsunami alerts have dedicated, verified state machines.
            if(listOf("颱風","海嘯","解除","取消","測試","演練").any {
                phenomenon.contains(it) || significance.contains(it)
            }) return@mapNotNull null
            CwaCountyHazard(phenomenon+significance,phenomenon,countyName,sent,end,start)
        }.distinctBy { it.title to it.county }.take(8)
    }

    suspend fun fetch(place: String, point: GeoPoint?): CwaCountyHazardsResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(SOURCE).openConnection() as HttpsURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            try {
                check(conn.responseCode == 200) { "中央氣象署 HTTP ${conn.responseCode}" }
                check(conn.contentLengthLong < 0 || conn.contentLengthLong <= MAX_BYTES) { "資料過大" }
                val bytes = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val chunk = ByteArray(8192)
                    while(true) {
                        val count = input.read(chunk)
                        if(count < 0) break
                        check(bytes.size()+count <= MAX_BYTES) { "資料過大" }
                        bytes.write(chunk,0,count)
                    }
                }
                val hazards = parse(JSONObject(bytes.toString("UTF-8")),place,point)
                CwaCountyHazardsResult(hazards,true,"中央氣象署縣市資料已確認；${hazards.size} 項已發布特報（含預告時段）")
            } finally { conn.disconnect() }
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
          catch(e: Exception) {
            CwaCountyHazardsResult(emptyList(),false,"中央氣象署縣市資料暫時無法驗證：${e.javaClass.simpleName}")
          }
    }
}
