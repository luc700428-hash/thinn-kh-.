package io.atmos.weather.insights

import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.Instant

/**
 * 生活指數: everyday questions answered from the forecast alone, in three plain levels.
 * Every index needs real data for its window; without it the index is simply left out.
 */
internal object LifeIndex {
    enum class Level(val word: String) { GOOD("適合"), FAIR("普通"), POOR("不建議") }
    /**
     * [word] is what the tile says in big type. Activities read 適合 / 普通 / 不建議; a risk reads
     * 低 / 中 / 高 and sun protection 可免 / 建議 / 必要, so a red tile never says "不建議" about a risk.
     */
    data class Item(val title: String, val level: Level, val reason: String, val word: String = level.word)
    private fun risk(title: String, level: Level, reason: String) = Item(title, level, reason, when (level) { Level.GOOD -> "低"; Level.FAIR -> "中"; Level.POOR -> "高" })

    fun build(w: WeatherSnapshot, now: Instant): List<Item> {
        fun window(hours: Int) = w.hourly.filter { !it.at.isBefore(now.minusSeconds(1800)) && it.at.isBefore(now.plusSeconds(hours * 3600L)) }
        val day = window(12); val two = window(48)
        val pop12 = day.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        val pop48 = two.takeIf { it.size >= 36 }?.mapNotNull { it.precipitationProbabilityPct }?.maxOrNull()
        val temps = day.mapNotNull { it.feelsLikeC ?: it.temperatureC } + (w.feelsLikeC ?: w.temperatureC)
        val wind = (day.mapNotNull { it.windKmh } + listOfNotNull(w.windKmh)).maxOrNull()
        val humidity = (day.mapNotNull { it.humidityPct } + listOfNotNull(w.humidityPct)).let { if (it.isEmpty()) null else it.average() }
        val uv = (day.mapNotNull { it.uvIndex } + listOfNotNull(w.currentUv)).maxOrNull()
        val aqi = w.usAqi
        val out = mutableListOf<Item>()
        // 洗車: needs two dry days, or it is money down the drain.
        (pop48 ?: pop12)?.let { p ->
            val span = if (pop48 != null) "兩天" else "12 小時"
            out += when {
                p >= 60 -> Item("洗車", Level.POOR, "${span}內降雨機率 $p%")
                p >= 30 || (wind ?: 0.0) >= 35 -> Item("洗車", Level.FAIR, if (p >= 30) "${span}內可能下雨（$p%）" else "風大易沾塵")
                else -> Item("洗車", Level.GOOD, "${span}內不太會下雨")
            }
        }
        // 晾衣: dry air and no rain.
        if (pop12 != null && humidity != null) out += when {
            pop12 >= 50 -> Item("晾衣", Level.POOR, "降雨機率 $pop12%")
            humidity >= 85 -> Item("晾衣", Level.POOR, "濕度約 ${humidity.toInt()}%，不易乾")
            pop12 >= 30 || humidity >= 72 -> Item("晾衣", Level.FAIR, if (pop12 >= 30) "可能下雨，留意收衣" else "濕度偏高，乾得慢")
            else -> Item("晾衣", Level.GOOD, "乾燥少雨，乾得快")
        }
        // 戶外運動: rain, heat, cold and air quality.
        if (pop12 != null) {
            val hot = temps.max(); val cold = temps.min()
            out += when {
                (aqi ?: 0) > 150 -> Item("戶外運動", Level.POOR, "空氣品質不佳（AQI $aqi）")
                pop12 >= 60 -> Item("戶外運動", Level.POOR, "降雨機率 $pop12%")
                hot >= 35 -> Item("戶外運動", Level.POOR, "體感高達 ${hot.toInt()}°，小心中暑")
                (aqi ?: 0) > 100 || pop12 >= 30 || hot >= 31 || cold < 10 -> Item("戶外運動", Level.FAIR,
                    when { (aqi ?: 0) > 100 -> "敏感族群減少劇烈運動"; pop12 >= 30 -> "可能下雨"; hot >= 31 -> "偏熱，多補水"; else -> "偏冷，先暖身" })
                else -> Item("戶外運動", Level.GOOD, "天氣舒適")
            }
        }
        // 著涼: big swings between morning and afternoon, or a cold day, make it easy to catch a chill.
        if (temps.size >= 6) {
            val swing = temps.max() - temps.min()
            out += when {
                swing >= 10 || temps.min() < 10 -> risk("著涼風險", Level.POOR, if (swing >= 10) "早晚溫差 ${swing.toInt()}°，容易著涼，多帶件外套" else "體感低於 10°，出門穿暖")
                swing >= 7 || temps.min() < 15 -> risk("著涼風險", Level.FAIR, if (swing >= 7) "早晚溫差 ${swing.toInt()}°，帶件薄外套" else "偏涼，加件衣服就好")
                else -> risk("著涼風險", Level.GOOD, "溫差不大，照平常穿")
            }
        }
        // 防曬
        uv?.let { u ->
            out += when {
                u >= 8 -> Item("防曬", Level.POOR, "紫外線 ${u.toInt()}，擦防曬、戴帽子", "必要")
                u >= 5 -> Item("防曬", Level.FAIR, "紫外線 ${u.toInt()}，外出擦防曬", "建議")
                else -> Item("防曬", Level.GOOD, "紫外線弱", "可免")
            }
        }
        // 開窗通風
        if (aqi != null) out += when {
            aqi > 100 -> Item("開窗通風", Level.POOR, "AQI $aqi，建議關窗")
            aqi > 50 || (pop12 ?: 0) >= 60 -> Item("開窗通風", Level.FAIR, if (aqi > 50) "空氣普通" else "可能潑雨")
            else -> Item("開窗通風", Level.GOOD, "空氣良好")
        }
        return out
    }
}
