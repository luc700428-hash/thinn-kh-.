package io.atmos.weather.alerts

import io.atmos.weather.location.LocationLabels
import io.atmos.weather.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Reads official CWA RSS titles, not model-generated predictions. No fake alerts. */
data class CwaWarning(val title: String, val summary: String, val link: String, val issuedAt: Instant)

data class CwaFetchResult(val warnings: List<CwaWarning>, val checkSucceeded: Boolean, val detail: String)

object CwaWarningFeed {
    const val OFFICIAL_URL = "https://www.cwa.gov.tw/V8/C/P/Warning/FIFOWS.html"
    private const val RSS_URL = "https://www.cwa.gov.tw/rss/Data/cwa_warning.xml"

    /** All-area official RSS for formal typhoon issuance. Do not county-filter sea warnings. */
    suspend fun fetchNational(): CwaFetchResult = withContext(Dispatchers.IO) {
        try {
            val conn=URL(RSS_URL).openConnection().apply {
                connectTimeout=9000
                readTimeout=9000
                setRequestProperty("User-Agent","thinn-khi-weather/2.0")
            }
            val result=conn.getInputStream().use { parse(it) }
            CwaFetchResult(result,true,"官方 RSS 已查詢；全臺警報需另核對正式發布標題")
        } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
          catch(e:Exception) {
            CwaFetchResult(emptyList(),false,"官方 RSS 無法讀取：${e.javaClass.simpleName}")
        }
    }

    suspend fun forLocation(placeLabel: String, point: GeoPoint? = null): List<CwaWarning> = fetch(placeLabel,point).warnings

    suspend fun fetch(placeLabel: String, point: GeoPoint? = null): CwaFetchResult = withContext(Dispatchers.IO) {
        try {
            val parts = LocationLabels.clean(placeLabel).split(" · ").map { it.trim() }
            val county = parts.firstOrNull { it.endsWith("縣") || it.endsWith("市") }
                ?.replace('臺','台') ?: point?.let(LocationLabels::countyFromPoint)
                ?: return@withContext CwaFetchResult(emptyList(), false, "無法確認所在地縣市；請重新定位")
            if (county.isBlank()) return@withContext CwaFetchResult(emptyList(), false, "尚無有效的縣市資料")
            val conn = URL(RSS_URL).openConnection().apply {
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("User-Agent", "thinn-khi-weather/2.0")
            }
            val warnings = conn.getInputStream().use { input -> parse(input) }
            val now = Instant.now()
            val matched = warnings.filter { warning ->
                val haystack = (warning.title + " " + warning.summary).replace('臺','台')
                (haystack.contains(county) ||
                    haystack.contains("全台") || haystack.contains("各地") || haystack.contains("全國") ||
                    haystack.contains("全臺") || haystack.contains("台灣各地")) &&
                    warning.issuedAt.isAfter(now.minusSeconds(24 * 3600)) &&
                    warning.issuedAt.isBefore(now.plusSeconds(600))
            }.take(5)
            CwaFetchResult(matched, true, "官方 RSS 已查詢；依 $county 比對 ${warnings.size} 筆近期公告（非完整有效警報清單）")
        } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
          catch (e: Exception) {
            CwaFetchResult(emptyList(), false, "目前無法讀取官方 RSS：${e.javaClass.simpleName}")
        }
    }

    fun parse(input: java.io.InputStream): List<CwaWarning> {
        val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(input, "UTF-8") }
        val output = mutableListOf<CwaWarning>()
        var inside = false
        var field: String? = null
        var title = ""; var summary = ""; var link = ""; var date = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "item" -> { inside = true; title = ""; summary = ""; link = ""; date = "" }
                    "title", "description", "link", "pubdate" -> if (inside) field = parser.name.lowercase()
                }
                XmlPullParser.TEXT -> if (inside) when (field) {
                    "title" -> title += parser.text
                    "description" -> summary += parser.text
                    "link" -> link += parser.text
                    "pubdate" -> date += parser.text
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true)) {
                        inside = false
                        val issuedAt = runCatching { ZonedDateTime.parse(date.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
                        if (issuedAt != null && title.isNotBlank() && CwaOfficialPages.allowed(link) &&
                            CwaWarningTitles.weatherNotice(title,link)) {
                            val plain=summary.replace(Regex("<[^>]*>"), " ").replace("\\n", "\n").trim()
                            output += CwaWarning(title.trim(), plain, link, issuedAt)
                        }
                    }
                    field = null
                }
            }
            parser.next()
        }
        return output
    }
}
