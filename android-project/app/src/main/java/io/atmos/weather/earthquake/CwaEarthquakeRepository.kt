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

    /** One dataset page. CWA Open Data documents `Authorization`, `limit` and `offset` as query parameters. */
    private suspend fun fetch(context: Context, id: String, key: String?, offset: Int): JSONObject {
        if (key.isNullOrBlank()) {
            // The Firebase proxy only serves the newest page.
            if (offset > 0) return JSONObject()
            return CwaOfficialGateway.read(context,id)
        }
        val encodedKey = java.net.URLEncoder.encode(key, "UTF-8")
        val conn = URL("$base$id?Authorization=$encodedKey&format=JSON&limit=$PAGE&offset=$offset")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 12000; conn.readTimeout = 16000
        conn.setRequestProperty("Accept", "application/json")
        val raw = try {
            if (conn.responseCode !in 200..299) error("官方查詢 HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
        return JSONObject(raw)
    }

    /** Where paging stands: the next offset of each dataset, or absent once a dataset has no more. */
    data class Cursor(val next: Map<String, Int>) { val done get() = next.isEmpty() }
    val start = Cursor(datasets.associate { it.first to 0 })

    /** The page after [cursor] from both datasets, newest first, and the cursor after it. */
    suspend fun page(context: Context, cursor: Cursor): Pair<List<EarthquakeReport>, Cursor> = withContext(Dispatchers.IO) {
        val key = key(context)
        val all = mutableListOf<EarthquakeReport>()
        val errors = mutableListOf<String>()
        val next = mutableMapOf<String, Int>()
        for ((id, category) in datasets) {
            val offset = cursor.next[id] ?: continue
            try {
                val json = fetch(context, id, key, offset)
                if (json.optString("success").equals("false", true)) error("中央氣象署未接受授權或查詢")
                val got = CwaEarthquakeParser.parse(json, category)
                all += got
                // A short page means this dataset has nothing older; the proxy serves one page only.
                if (got.size >= PAGE && !key.isNullOrBlank()) next[id] = offset + PAGE
            } catch (cancel: CancellationException) { throw cancel }
              catch (ex: Exception) { errors += "${category}：${ex.message ?: "查詢失敗"}"; next[id] = offset }
        }
        if (all.isEmpty() && errors.isNotEmpty()) error(errors.joinToString("；"))
        all.distinctBy { it.id }.sortedByDescending { it.origin } to Cursor(next)
    }

    suspend fun latest(context: Context): List<EarthquakeReport> = page(context, start).first.take(30)
}
