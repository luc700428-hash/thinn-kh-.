package io.atmos.weather.earthquake

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Official *reports*, published after earthquakes. Never interpret as real-time EEW. */
object CwaEarthquakeRepository {
    private const val base = "https://opendata.cwa.gov.tw/api/v1/rest/datastore/"
    private val datasets = listOf("E-A0015-001" to "顯著有感", "E-A0016-001" to "小區域有感")
    const val PAGE = 15

    private fun key(context: Context): String? =
        // A private key entered on the device first, then the app's CI-injected CWA key; only
        // without either does the request go through the Firebase proxy.
        EarthquakeSecurity.load(context)?.takeIf { it.isNotBlank() }
            ?: io.atmos.weather.BuildConfig.THINN_CWA_API_KEY.takeIf { it.isNotBlank() }

    /** One dataset query. CWA Open Data documents `Authorization` and `limit` as query parameters. */
    private suspend fun fetch(context: Context, id: String, key: String?, extra: String = ""): JSONObject {
        if (key.isNullOrBlank()) {
            // The Firebase proxy only serves the newest reports.
            if (extra.isNotEmpty()) return JSONObject()
            return CwaOfficialGateway.read(context,id)
        }
        val encodedKey = java.net.URLEncoder.encode(key, "UTF-8")
        val conn = URL("$base$id?Authorization=$encodedKey&format=JSON&limit=$PAGE$extra")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 16000
        conn.setRequestProperty("Accept", "application/json")
        val raw = try {
            if (conn.responseCode !in 200..299) error("官方查詢 HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
        return JSONObject(raw)
    }

    private suspend fun query(context: Context, extra: String): List<EarthquakeReport> = withContext(Dispatchers.IO) {
        val key = key(context)
        val all = mutableListOf<EarthquakeReport>()
        val errors = mutableListOf<String>()
        for ((id, category) in datasets) {
            try {
                val json = fetch(context, id, key, extra)
                if (json.optString("success").equals("false", true)) error("中央氣象署未接受授權或查詢")
                all += CwaEarthquakeParser.parse(json, category)
            } catch (cancel: CancellationException) { throw cancel }
              catch (ex: Exception) { errors += "${category}：${ex.message ?: "查詢失敗"}" }
        }
        if (all.isEmpty() && errors.isNotEmpty() && extra.isEmpty()) error(errors.joinToString("；"))
        all.distinctBy { it.id }.sortedByDescending { it.origin }
    }

    /** The newest reports from both datasets. */
    suspend fun latest(context: Context): List<EarthquakeReport> = query(context, "").take(30)

    /**
     * Reports that happened before [before] (the oldest one already shown), asked for by time
     * (`timeTo`). Empty when the service has nothing older, ignores the filter, or fails: the
     * caller then continues with another source instead of repeating the same page.
     */
    suspend fun olderThan(context: Context, before: java.time.Instant): List<EarthquakeReport> {
        val to = java.time.LocalDateTime.ofInstant(before.minusSeconds(1), java.time.ZoneId.of("Asia/Taipei"))
            .withNano(0).toString().let { if (it.length == 16) "$it:00" else it }
        return runCatching { query(context, "&timeTo=$to") }.getOrDefault(emptyList()).filter { it.origin.isBefore(before) }
    }
}
