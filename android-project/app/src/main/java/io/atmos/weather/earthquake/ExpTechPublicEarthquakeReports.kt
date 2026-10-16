package io.atmos.weather.earthquake

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Public post-event earthquake list. No individual CWA key required.
 * Independent JSON adapter. Fail closed on unrecognized records and never present it as EEW.
 * Endpoint and schema must be verified against live ExpTech service before general deployment.
 */
object ExpTechPublicEarthquakeReports {
    private val hosts = listOf("api.core-tyo1.exptech.dev", "api.core-tnn1.exptech.dev")
    suspend fun latest(): List<EarthquakeReport> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (host in hosts) {
            try {
                val connection = URL("https://$host/api/v2/eq/report?limit=30&sort=time&order=desc")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout=8000
                connection.readTimeout=10000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "thinn-khi/15.1 Android earthquake reports")
                val response = try {
                    if(connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
                val reports = parse(response)
                if (reports.isNotEmpty()) return@withContext reports
                lastError = IllegalStateException("公開地震報告格式無法辨識")
            } catch (ex: CancellationException) { throw ex }
              catch (ex: Exception) { lastError=ex }
        }
        throw IllegalStateException("公開地震報告暫時不可使用：${lastError?.message ?: "資料為空"}")
    }

    internal fun parse(raw:String):List<EarthquakeReport> {
        val text=raw.trimStart()
        val array=if(text.startsWith("[")) JSONArray(raw) else {
            val root=JSONObject(raw)
            val inner=root.opt("data")
            when(inner) {
                is JSONArray -> inner
                is JSONObject -> inner.optJSONArray("reports") ?: inner.optJSONArray("items") ?: inner.optJSONArray("list") ?: inner.optJSONArray("data")
                else -> null
            } ?: root.optJSONArray("reports") ?: root.optJSONArray("items") ?: root.optJSONArray("list") ?: JSONArray()
        }
        val items=(0 until minOf(array.length(),100)).mapNotNull { i ->
            val item=array.optJSONObject(i) ?: return@mapNotNull null
            runCatching { toReport(item) }.getOrNull()
        }
        return items.distinctBy { it.id }.sortedByDescending { it.origin }.take(30)
    }
    private fun firstStr(obj: JSONObject, vararg fields:String):String? = fields.firstNotNullOfOrNull { key ->
        obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
    private fun dbl(obj: JSONObject,vararg names:String):Double? = names.firstNotNullOfOrNull { k ->
        obj.opt(k)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    private fun toReport(o:JSONObject):EarthquakeReport? {
        val info=o.optJSONObject("eq") ?: o
        val magnitude=dbl(info,"mag","magnitude","magnitudeValue") ?: return null
        if(magnitude !in 0.0..10.0) return null
        val rawTime=info.opt("time") ?: info.opt("originTime") ?: o.opt("time") ?: return null
        val time=parseTime(rawTime) ?: return null
        val id=firstStr(o,"id","identifier","reportId") ?: "${time.epochSecond}_${magnitude}"
        val place=firstStr(info,"loc","location","epicenter","place") ?: "臺灣附近"
        val latitude=dbl(info,"lat","latitude","epicenterLat")?.takeIf { it in -90.0..90.0 }
        val longitude=dbl(info,"lon","lng","longitude","epicenterLon")?.takeIf { it in -180.0..180.0 }
        val depth=dbl(info,"depth","depthKm")?.takeIf { it >= 0.0 }
        val locInfo=o.optJSONObject("list") ?: o.optJSONObject("intensity")
        val areas=mutableListOf<Pair<String,String>>()
        if(locInfo!=null) {
            for(key in locInfo.keys()) {
                val entry=locInfo.optJSONObject(key) ?: continue
                val num=entry.optInt("int",entry.optInt("intensity",0))
                if(num in 1..9) areas += key to intensityName(num)
            }
        }
        val top=areas.maxByOrNull { intensityRank(it.second) }?.second
            ?: dbl(o,"maxInt","maxIntensity","int")?.toInt()?.takeIf { it in 1..9 }?.let(::intensityName)
        return EarthquakeReport(id,time,place,magnitude,depth,latitude,longitude,top,
            "ExpTech 公開資料彙整的震後報告：${place}，地震規模 ${magnitude}。不是提前預警。",
            "https://scweb.cwa.gov.tw/zh-tw/earthquake/data", "第三方彙整之震後地震報告",areas.sortedByDescending { intensityRank(it.second) })
    }
    private fun intensityName(level:Int)=when(level) {
        1->"1";2->"2";3->"3";4->"4";5->"5弱";6->"5強";7->"6弱";8->"6強";9->"7";else->"未提供"
    }
    private fun intensityRank(s:String)=when(s){ "7"->9;"6強"->8;"6弱"->7;"5強"->6;"5弱"->5;else->s.toIntOrNull() ?: 0 }
    private fun parseTime(any:Any): Instant? {
        val raw=any.toString().trim()
        raw.toDoubleOrNull()?.let { n ->
            if(n>1e12) return Instant.ofEpochMilli(n.toLong())
            if(n>1e9) return Instant.ofEpochSecond(n.toLong())
        }
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw.replace(' ','T').replace('/','-')).atZone(ZoneId.of("Asia/Taipei")).toInstant() }.getOrNull()
    }
}
