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
    suspend fun latest(context: Context): List<EarthquakeReport> = withContext(Dispatchers.IO) {
        val deviceKey = EarthquakeSecurity.load(context)
        val all = mutableListOf<EarthquakeReport>()
        val errors = mutableListOf<String>()
        for ((id, category) in listOf("E-A0015-001" to "顯著有感", "E-A0016-001" to "小區域有感")) {
            try {
                // CWA Open Data APIs document the authorization code as the
                // `Authorization` query parameter (not only an HTTP header).
                // The user may opt to enter a private local key; otherwise use the
                // server-side Firebase Secret proxy. Never ship shared credentials.
                val json = if (deviceKey.isNullOrBlank()) {
                    CwaOfficialGateway.read(context,id)
                } else {
                    val encodedKey = java.net.URLEncoder.encode(deviceKey,"UTF-8")
                    val conn=URL("$base$id?Authorization=$encodedKey&format=JSON&limit=15")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout=12000; conn.readTimeout=16000
                    conn.setRequestProperty("Accept","application/json")
                    val raw=try {
                        if(conn.responseCode !in 200..299) error("官方查詢 HTTP ${conn.responseCode}")
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } finally {conn.disconnect()}
                    JSONObject(raw)
                }
                if (json.optString("success").equals("false",true)) error("中央氣象署未接受授權或查詢")
                all += CwaEarthquakeParser.parse(json, category)
            } catch (cancel: CancellationException) { throw cancel }
              catch (ex: Exception) { errors += "${category}：${ex.message ?: "查詢失敗"}" }
        }
        if (all.isEmpty() && errors.isNotEmpty()) error(errors.joinToString("；"))
        all.distinctBy { it.id }.sortedByDescending { it.origin }.take(30)
    }
}
