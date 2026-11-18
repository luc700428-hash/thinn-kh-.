package io.atmos.weather.comfort

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 雨來了提醒: the next two hours in 15-minute steps (Open-Meteo forecast, not radar), so the
 * notification can say "in about 20 minutes" instead of "this hour". Pure parsing and decision
 * logic live here so they can be tested without a network.
 */
object RainSoon {
    data class Slot(val at: Instant, val mm: Double, val chance: Int?)
    data class Alert(val minutes: Int, val mmPerHour: Double, val chance: Int?) {
        val strength: String get() = when {
            mmPerHour >= 15 -> "大雨"
            mmPerHour >= 4 -> "明顯的雨"
            mmPerHour >= 1 -> "小雨"
            else -> "零星小雨"
        }
        fun text(): ComfortText {
            val whenText = if (minutes <= 5) "幾分鐘內" else "大約 $minutes 分鐘後"
            val chanceText = chance?.let { "，降雨機率 $it%" } ?: ""
            return ComfortText("雨來了", "$whenText 開始下$strength$chanceText",
                "$whenText 你所在的地方可能開始下$strength$chanceText。要出門記得帶傘，晾在外面的衣服可以先收。")
        }
    }

    /** Wet enough to notice: 0.2 mm in 15 minutes is about a light shower. */
    private const val WET_MM = 0.2

    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(java.util.Locale.US, lat)}" +
            "&longitude=${"%.3f".format(java.util.Locale.US, lon)}" +
            "&minutely_15=precipitation,precipitation_probability&forecast_minutely_15=9&timezone=GMT"

    fun parse(raw: String): List<Slot> {
        val m = JSONObject(raw).optJSONObject("minutely_15") ?: return emptyList()
        val times = m.optJSONArray("time") ?: return emptyList()
        val mm = m.optJSONArray("precipitation") ?: return emptyList()
        val chance = m.optJSONArray("precipitation_probability")
        return (0 until minOf(times.length(), mm.length())).mapNotNull { i ->
            val at = runCatching { LocalDateTime.parse(times.getString(i)).atZone(ZoneId.of("GMT")).toInstant() }.getOrNull() ?: return@mapNotNull null
            val v = mm.optDouble(i, Double.NaN).takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
            Slot(at, v, chance?.optInt(i, -1)?.takeIf { it in 0..100 })
        }
    }

    /**
     * Dry now and wet within the hour → when it starts. Nothing if it is already raining, if the
     * rain is more than an hour away, or if the forecast is too old to trust.
     */
    fun decide(slots: List<Slot>, now: Instant): Alert? {
        val ahead = slots.filter { !it.at.isBefore(now.minusSeconds(15 * 60)) }.sortedBy { it.at }
        val current = ahead.firstOrNull() ?: return null
        if (current.at.isAfter(now.plusSeconds(15 * 60))) return null
        if (current.mm >= WET_MM) return null
        val first = ahead.drop(1).firstOrNull { it.mm >= WET_MM } ?: return null
        val minutes = ((first.at.epochSecond - now.epochSecond) / 60).toInt()
        if (minutes > 60) return null
        return Alert(minutes.coerceAtLeast(0), first.mm * 4, first.chance)
    }

    suspend fun fetch(lat: Double, lon: Double): List<Slot>? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url(lat, lon)).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode !in 200..299) null else parse(c.inputStream.bufferedReader().use { it.readText() })
            } finally { c.disconnect() }
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
    }
}
