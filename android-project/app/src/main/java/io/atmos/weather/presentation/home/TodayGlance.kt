package io.atmos.weather.presentation.home

import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.lifestyle.OutfitPlan
import kotlin.math.roundToInt

/**
 * 今日概況 at a glance: three short answers (what to wear, coat, rain gear) that fit one tile
 * each, plus a one-word mood. The long sentences stay in the plan and open on demand.
 */
internal object TodayGlance {
    data class Glance(
        val mood: String,
        val wear: String, val wearNote: String,
        val coat: String, val coatNote: String,
        val rain: String, val rainNote: String,
    )

    fun build(w: WeatherSnapshot, plan: OutfitPlan): Glance {
        val feels = (w.feelsLikeC?.takeIf { it.isFinite() } ?: w.temperatureC)
        val next = w.nextHours.ifEmpty { w.hourly }.take(12)
        val low = (next.mapNotNull { it.temperatureC?.takeIf { t -> t.isFinite() } } + listOfNotNull(w.minTempC)).minOrNull()
        val pop = next.mapNotNull { it.precipitationProbabilityPct?.takeIf { p -> p in 0..100 } }.maxOrNull()
        val gust = w.windGustKmh?.takeIf { it.isFinite() } ?: 0.0
        // At most three characters, so the answer fits its tile on any phone.
        val wear = when {
            feels >= 26 -> "短袖"
            feels >= 21 -> "薄長袖"
            feels >= 16 -> "長袖"
            feels >= 11 -> "加外套"
            else -> "保暖"
        }
        val coat = when {
            plan.coatDecision.startsWith("要") -> if (feels < 12) "厚外套" else "要帶"
            plan.coatDecision.startsWith("帶") -> "薄外套"
            else -> "不用"
        }
        val rain = when {
            plan.umbrella.startsWith("風勢強") -> "雨衣"
            plan.umbrella.startsWith("要帶") -> "要帶"
            plan.umbrella.startsWith("建議帶") -> "建議帶"
            plan.umbrella.startsWith("可以帶") -> "備用"
            plan.umbrella.startsWith("尚缺") -> "未知"
            else -> "不用"
        }
        val mood = when {
            gust >= 50 || (w.windKmh ?: 0.0) >= 39 -> "風大"
            pop != null && pop >= 60 -> "有雨"
            feels >= 32 -> "炎熱"
            feels >= 28 -> "偏熱"
            feels < 12 -> "寒冷"
            feels < 18 -> "偏涼"
            else -> "舒適"
        }
        return Glance(
            mood = mood,
            wear = wear, wearNote = "體感 ${feels.roundToInt()}°",
            coat = coat, coatNote = low?.let { "最低 ${it.roundToInt()}°" } ?: "",
            rain = rain, rainNote = pop?.let { "降雨 $it%" } ?: "未提供機率",
        )
    }
}
