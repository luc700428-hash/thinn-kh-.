package io.atmos.weather.lifestyle

import android.content.Context
import io.atmos.weather.domain.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/** Optional Ollama-compatible, user-supplied HTTPS inference server.
 * It is NOT bundled in the APK: model hosting/download are the user's responsibility.
 * No names, addresses, coordinates, tracking IDs or notification content leave the device.
 */
data class LifestyleAiSettings(val enabled: Boolean = false,
                               val endpoint: String = "",
                               val model: String = "qwen2.5:1.5b") {
    companion object {
        private const val PREF = "thinn_lifestyle_ai"
        fun load(context: Context): LifestyleAiSettings {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            return LifestyleAiSettings(prefs.getBoolean("enabled", false),
                prefs.getString("endpoint", "").orEmpty(),
                prefs.getString("model", "qwen2.5:1.5b").orEmpty())
        }
    }
    fun save(context: Context) {
        context.getSharedPreferences("thinn_lifestyle_ai", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled).putString("endpoint", endpoint.trim())
            .putString("model", model.trim()).apply()
    }
    fun isConfigured(): Boolean = enabled && endpoint.isNotBlank() && model.isNotBlank()
}

object LifestyleAiClient {
    /** Non-negotiable HTTPS transport: never send forecast/profile data to a cleartext endpoint. */
    fun validatedEndpoint(value: String): URL {
        val input = value.trim().trimEnd('/')
        val uri = URI(input)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null) { "請填寫有效的 HTTPS Ollama 服務網址（不支援明文 HTTP）" }
        val path = uri.path.orEmpty().trimEnd('/')
        require(path.isEmpty() || path == "/api" || path == "/api/generate") {
            "服務網址只支援根目錄、/api 或 /api/generate"
        }
        return URI("https", null, uri.host, uri.port,
            "/api/generate", null, null).toURL()
    }

    suspend fun generate(settings: LifestyleAiSettings, weather: WeatherSnapshot,
                         plan: OutfitPlan, mode: OutingMode): String = withContext(Dispatchers.IO) {
        require(settings.isConfigured()) { "請先到設定頁啟用 AI 並填入服務網址與模型" }
        require(plan.weatherFresh) { "目前氣象資料過舊，請更新後再使用 AI" }
        val endpoint = validatedEndpoint(settings.endpoint)
        val maxRain = weather.hourly.filter { !it.at.isBefore(weather.observedAt) &&
            java.time.Duration.between(weather.observedAt, it.at).toMinutes() <= 360 }
            .mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        val rainText = maxRain?.let { "$it%" } ?: "未提供"
        val input = """請用繁體中文，根據以下資料提出 2 到 3 句實用、親切、不誇大的外出穿搭與雨具建議。
你只能使用給定的數值，不可自行編造天氣、地震預警、官方警報、地點、體感或新時段。
如果可能有雷雨，優先提醒安全。這是一般生活參考，不是氣象警報或醫療建議。
情境：${mode.title}
氣溫：${weather.temperatureC.roundToInt()}°C；體感：${(weather.feelsLikeC ?: weather.temperatureC).roundToInt()}°C
天氣：${weather.condition.name}；六小時降雨機率最高：${rainText}
平均風速：${weather.windKmh?.roundToInt()?.toString() ?: "未知"} km/h；UV：${weather.currentUv?.toString() ?: "未知"}
可追溯規則結果：上衣 ${plan.top}；褲子 ${plan.bottoms}；外搭 ${plan.layer}；鞋子 ${plan.shoes}；雨具 ${plan.umbrella}
請保持以上建議主旨，僅用更自然的方式補充提醒，最多 140 個繁體中文字。""".trimIndent()
        val request = JSONObject().put("model", settings.model.trim())
            .put("prompt", input).put("stream", false)
            .put("options", JSONObject().put("temperature", 0.2).put("num_predict", 220))
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 25_000
            doOutput = true
            instanceFollowRedirects = false // do not send data to another origin on redirect
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val bytes = request.toString().toByteArray(StandardCharsets.UTF_8)
            require(bytes.size < 12_000) { "輸入超過安全長度" }
            connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode != 200) throw IllegalStateException("AI 服務未成功回應（HTTP ${connection.responseCode}）")
            val responseText = connection.inputStream.bufferedReader().use { it.readText().take(32_768) }
            val text = JSONObject(responseText).optString("response").trim()
            require(text.isNotBlank()) { "AI 沒有提供回覆，請確認模型已下載" }
            text.take(500)
        } finally { connection.disconnect() }
    }
}
