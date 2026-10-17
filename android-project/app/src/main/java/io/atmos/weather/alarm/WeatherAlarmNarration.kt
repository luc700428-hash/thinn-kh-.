package io.atmos.weather.alarm

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Offline-safe, deterministic narration based only on time-stamped weather data.
 * The narration is assembled at alarm fire time, not when the weather screen was opened.
 * No synthetic temperatures, rainfall chances, warning status or earthquake data.
 */
data class AlarmForecastHour(
    val atEpochMs: Long,
    val temperatureC: Double?,
    val rainChancePct: Int?,
    val windKmh: Double?,
    val uvIndex: Double?
)

data class AlarmWeatherReading(
    val city: String,
    val fetchedEpochMs: Long,
    val observedEpochMs: Long,
    val condition: String,
    val temperatureC: Double,
    val feelsLikeC: Double?,
    val humidityPct: Int?,
    val windKmh: Double?,
    val highC: Double?,
    val lowC: Double?,
    val usAqi: Int?,
    val hours: List<AlarmForecastHour>
)

object WeatherAlarmNarration {
    // Weather readouts must not be refreshed by rereading a cached UI state.
    const val MAX_WEATHER_AGE_MS: Long = 2 * 60 * 60 * 1000L
    private val dateFormat = DateTimeFormatter.ofPattern("M月d日，EEEE", Locale.TAIWAN)

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "早安"
        in 12..17 -> "午安"
        else -> "晚上好"
    }

    fun build(now: ZonedDateTime, report: AlarmWeatherReading?, detailed: Boolean = true): String {
        val localHour = now.hour
        val timeText = "${when(localHour) { in 0..4 -> "凌晨"; in 5..10 -> "早上"; 11 -> "上午"; 12 -> "中午"; in 13..17 -> "下午"; else -> "晚上" }}${if(localHour % 12 == 0)12 else localHour % 12}點${if (now.minute == 0) "整" else "${now.minute}分"}"
        val pieces = mutableListOf(
            "${greeting(localHour)}。現在是${now.format(dateFormat)}，$timeText。",
            "接下來，陪你聽聽今天的天氣。"
        )
        val nowMs = now.toInstant().toEpochMilli()
        val usable = report != null && report.city.isNotBlank() && report.temperatureC.isFinite() &&
            report.fetchedEpochMs > 0 && report.observedEpochMs > 0 &&
            (nowMs - report.fetchedEpochMs) in 0L..MAX_WEATHER_AGE_MS &&
            (nowMs - report.observedEpochMs) in 0L..MAX_WEATHER_AGE_MS
        if (!usable) {
            pieces += "目前沒有足夠新的天氣資料，我不會把之前的預報當作現在的天氣。"
            pieces += "出門前，請打開 thinn-khì 更新預報，並留意中央氣象署發布的警特報。"
            pieces += "祝你今天順利，慢慢來就好。"
            return pieces.joinToString(" ")
        }
        val w = requireNotNull(report)
        val minSince = Duration.between(Instant.ofEpochMilli(w.fetchedEpochMs), now.toInstant()).toMinutes()
        val place = w.city.trim().take(55)
        pieces += if (minSince <= 30) "先看看${place}的天氣。" else
            "以下是${place}最近一次更新的天氣資料，更新時間約在${minSince}分鐘前。"
        pieces += buildString {
            append("氣溫約${w.temperatureC.roundToInt()}度")
            if (w.feelsLikeC?.isFinite() == true) append("，體感約${w.feelsLikeC.roundToInt()}度")
            val state = conditionDescription(w.condition)
            if (state != null) append("，天氣$state")
            append("。")
        }
        if (!detailed) {
            pieces += rainAndAdvice(w, nowMs)
            pieces += "祝你有個舒服的一天。"
            return pieces.joinToString(" ")
        }
        if (w.highC?.isFinite() == true && w.lowC?.isFinite() == true) {
            pieces += "這份預報的氣溫範圍，大約是${w.lowC.roundToInt()}度到${w.highC.roundToInt()}度。"
        }
        pieces += rainAndAdvice(w, nowMs)
        if (w.windKmh?.isFinite() == true) {
            val speed=w.windKmh.roundToInt()
            pieces += "最近的風速大約每小時${speed}公里。" +
                (if(speed>=30) "風勢偏強，外出請留意強風與交通安全。" else "")
        }
        if (w.humidityPct != null && w.humidityPct in 0..100 && w.humidityPct >= 80) {
            pieces += "濕度大約百分之${w.humidityPct}，體感可能比較悶或潮濕。"
        }
        val future = w.hours.filter { it.atEpochMs >= nowMs && it.atEpochMs < nowMs+6*60*60_000L }
        val uv = future.mapNotNull { it.uvIndex?.takeIf(Double::isFinite) }.maxOrNull()
        if (uv != null && uv >= 6.0) pieces += "未來幾小時紫外線預報偏高，外出記得防曬。"
        if (w.usAqi != null && w.usAqi >= 101) {
            pieces += "空氣品質的美國 AQI 模型估計約${w.usAqi}，這不是環境部官方 AQI。敏感族群請視情況調整戶外活動。"
        }
        val feels = w.feelsLikeC?.takeIf(Double::isFinite) ?: w.temperatureC
        pieces += when {
            feels >= 29 -> "穿著方面，建議選擇透氣衣物，適時補水，避免長時間曝曬。"
            feels <= 19 -> "穿著方面，建議帶一件外套，早晚留意保暖。"
            else -> "穿著方面，可以選擇輕便舒適的衣服，依你的活動需求調整。"
        }
        pieces += "提醒你，天氣預報可能會變動；若有官方警特報，請以最新公告為準。"
        pieces += when(localHour) {
            in 5..11 -> "願你今天有個自在的開始。"
            in 12..17 -> "祝你下午一切順利。"
            else -> "願你今晚平安、好好照顧自己。"
        }
        return pieces.joinToString(" ")
    }

    private fun rainAndAdvice(w: AlarmWeatherReading, nowMs:Long): String {
        val chance = w.hours.asSequence()
            .filter { it.atEpochMs>=nowMs && it.atEpochMs<nowMs+6*60*60_000L }
            .mapNotNull { it.rainChancePct?.takeIf { v -> v in 0..100 } }
            .maxOrNull()
        return when {
            chance == null -> "未來幾小時沒有足夠的降雨機率資料，出門前可以再查看最新預報。"
            chance >= 55 -> "接下來六小時的最高降雨機率約百分之$chance，記得帶把傘，行程也留點彈性。"
            chance >= 30 -> "接下來六小時最高降雨機率約百分之$chance，出門帶著輕便雨具會比較安心。"
            else -> "接下來六小時最高降雨機率約百分之$chance，模型預報降雨機會較低，但仍請留意天氣變化。"
        }
    }

    private fun conditionDescription(condition:String): String? = when(condition) {
        "CLEAR" -> "晴朗"
        "PARTLY_CLOUDY" -> "晴時多雲"
        "CLOUDY" -> "多雲"
        "FOG" -> "有霧"
        "HAZE" -> "有霾"
        "DUST", "DUST_STORM" -> "可能有沙塵"
        "RAIN" -> "有雨"
        "FREEZING_RAIN" -> "有凍雨"
        "SLEET" -> "有雨雪"
        "SNOW" -> "有雪"
        "HAIL" -> "有冰雹"
        "THUNDERSTORM" -> "有雷雨"
        else -> null
    }
}
