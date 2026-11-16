package io.atmos.weather.comfort

import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** A notification ready to post: short title, one-line summary, and the expanded text. */
data class ComfortText(val title: String, val summary: String, val body: String)

/**
 * Plain-language weather messages for the morning / evening briefing, smart alerts and commute
 * reminders. Pure functions over a [WeatherSnapshot]: nothing here invents data, missing values
 * are simply left out.
 */
object ComfortMessages {
    private val hm = DateTimeFormatter.ofPattern("H:mm", Locale.TAIWAN)

    fun conditionWord(c: WeatherCondition) = when (c) {
        WeatherCondition.CLEAR -> "晴"
        WeatherCondition.PARTLY_CLOUDY -> "多雲時晴"
        WeatherCondition.CLOUDY -> "多雲"
        WeatherCondition.FOG -> "有霧"
        WeatherCondition.HAZE -> "霾"
        WeatherCondition.DUST, WeatherCondition.DUST_STORM -> "沙塵"
        WeatherCondition.RAIN -> "有雨"
        WeatherCondition.FREEZING_RAIN -> "凍雨"
        WeatherCondition.SLEET -> "雨夾雪"
        WeatherCondition.SNOW -> "下雪"
        WeatherCondition.HAIL -> "冰雹"
        WeatherCondition.THUNDERSTORM -> "雷雨"
        else -> "天氣多變"
    }
    private fun codeWord(code: Int?) = when (code) {
        0 -> "晴"; 1, 2 -> "多雲時晴"; 3 -> "多雲"; 45, 48 -> "有霧"
        in 51..67, in 80..82 -> "有雨"; in 71..77, 85, 86 -> "下雪"; 95, 96, 99 -> "雷雨"; else -> null
    }
    fun aqiWord(a: Int) = when { a <= 50 -> "良好"; a <= 100 -> "普通"; a <= 150 -> "對敏感族群不健康"; a <= 200 -> "不健康"; else -> "非常不健康" }
    fun uvWord(u: Double) = when { u < 3 -> "低"; u < 6 -> "中"; u < 8 -> "高"; u < 11 -> "過量"; else -> "危險" }

    /** Clothing in a few words from the coldest feel and the warmest temperature of a window. */
    fun outfit(coldest: Double, warmest: Double): String = when {
        coldest < 8 -> "厚外套、圍巾，注意保暖"
        coldest < 14 -> "保暖外套加長袖"
        coldest < 19 -> if (warmest - coldest >= 7) "長袖加一件可脫的外套" else "長袖或薄外套"
        coldest < 24 -> if (warmest - coldest >= 7) "短袖，早晚帶件薄外套" else "長袖薄衫或短袖"
        warmest >= 31 -> "透氣短袖，注意防曬補水"
        else -> "短袖就很舒服"
    }
    fun umbrella(maxPop: Int?): String? = when {
        maxPop == null -> null
        maxPop >= 60 -> "一定要帶傘"
        maxPop >= 30 -> "帶把折疊傘比較安心"
        else -> "不太需要帶傘"
    }

    private fun window(w: WeatherSnapshot, from: Instant, to: Instant) =
        w.hourly.filter { !it.at.isBefore(from) && it.at.isBefore(to) }.sortedBy { it.at }

    /** 早報: today from now until 22:00. */
    fun morning(w: WeatherSnapshot, now: Instant, zone: ZoneId, place: String): ComfortText {
        val day = now.atZone(zone).toLocalDate()
        val end = day.atTime(22, 0).atZone(zone).toInstant()
        val hours = window(w, now, end)
        val temps = hours.mapNotNull { it.temperatureC } + w.temperatureC
        val feels = hours.mapNotNull { it.feelsLikeC } + listOfNotNull(w.feelsLikeC)
        val low = (w.minTempC ?: temps.min()); val high = (w.maxTempC ?: temps.max())
        val pop = hours.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        val wet = hours.firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 50 }
        val uv = (hours.mapNotNull { it.uvIndex } + listOfNotNull(w.currentUv)).maxOrNull()
        val lines = mutableListOf<String>()
        lines += "今天 ${low.roundToInt()}–${high.roundToInt()}°，${conditionWord(w.condition)}" + (pop?.let { "，降雨機率最高 $it%" } ?: "")
        lines += "穿著：" + outfit((feels + temps).min(), high)
        umbrella(pop)?.let { lines += "雨具：$it" + (wet?.let { h -> "（${hm.format(h.at.atZone(zone))} 前後最可能下雨）" } ?: "") }
        w.usAqi?.let { lines += "空氣品質：${aqiWord(it)}" + if (it > 100) "，敏感族群戴口罩" else "" }
        uv?.takeIf { it >= 3 }?.let { lines += "紫外線：${uvWord(it)}" + if (it >= 6) "，記得防曬" else "" }
        if (high - low >= 10) lines += "早晚溫差 ${(high - low).roundToInt()}°，洋蔥式穿搭最方便"
        val summary = "${low.roundToInt()}–${high.roundToInt()}° · ${conditionWord(w.condition)}" + (pop?.let { " · 降雨 $it%" } ?: "")
        return ComfortText("早安，$place 今日天氣", summary, lines.joinToString("\n"))
    }

    /** 晚報: tomorrow 06:00–22:00 and the morning commute window. */
    fun evening(w: WeatherSnapshot, now: Instant, zone: ZoneId, place: String): ComfortText? {
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        val hours = window(w, tomorrow.atTime(6, 0).atZone(zone).toInstant(), tomorrow.atTime(22, 0).atZone(zone).toInstant())
        if (hours.size < 6) return null
        val temps = hours.mapNotNull { it.temperatureC }
        if (temps.isEmpty()) return null
        val low = temps.min(); val high = temps.max()
        val pop = hours.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        val morning = hours.filter { it.at.atZone(zone).hour in 7..9 }
        val word = hours.mapNotNull { codeWord(it.weatherCode) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val lines = mutableListOf<String>()
        lines += "明天 ${low.roundToInt()}–${high.roundToInt()}°" + (word?.let { "，$it" } ?: "") + (pop?.let { "，降雨機率最高 $it%" } ?: "")
        if (morning.isNotEmpty()) {
            val mt = morning.mapNotNull { it.temperatureC }; val mp = morning.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
            if (mt.isNotEmpty()) lines += "明早出門（7–9 點）約 ${mt.average().roundToInt()}°" + (mp?.let { "，降雨 $it%" } ?: "")
        }
        val todayHigh = w.maxTempC
        if (todayHigh != null && kotlin.math.abs(high - todayHigh) >= 4)
            lines += if (high < todayHigh) "比今天涼 ${(todayHigh - high).roundToInt()}°，衣服多帶一件" else "比今天熱 ${(high - todayHigh).roundToInt()}°"
        lines += "穿著：" + outfit((hours.mapNotNull { it.feelsLikeC } + temps).min(), high)
        umbrella(pop)?.let { lines += "雨具：$it" }
        val summary = "明天 ${low.roundToInt()}–${high.roundToInt()}°" + (pop?.let { " · 降雨 $it%" } ?: "")
        return ComfortText("晚安，$place 明日天氣", summary, lines.joinToString("\n"))
    }

    // ---------- smart alerts ----------
    enum class Smart { RAIN_SOON, TEMP_SWING, UV_HIGH, AIR_BAD }

    /** The most useful alerts for the next hours; the caller de-duplicates per day. */
    fun smart(w: WeatherSnapshot, now: Instant, zone: ZoneId, enabled: Set<Smart>): List<Pair<Smart, ComfortText>> {
        val out = mutableListOf<Pair<Smart, ComfortText>>()
        val next = window(w, now.minusSeconds(1800), now.plusSeconds(3 * 3600))
        if (Smart.RAIN_SOON in enabled && w.condition !in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM)) {
            val first = next.drop(1).firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 60 }
            val nowPop = next.firstOrNull()?.precipitationProbabilityPct ?: 0
            if (first != null && nowPop < 40) {
                val at = hm.format(first.at.atZone(zone))
                out += Smart.RAIN_SOON to ComfortText("快下雨了", "$at 前後降雨機率 ${first.precipitationProbabilityPct}%",
                    "大約 $at 開始可能下雨，降雨機率 ${first.precipitationProbabilityPct}%。要出門記得帶傘，晾在外面的衣服可以先收。")
            }
        }
        if (Smart.TEMP_SWING in enabled) {
            val day = now.atZone(zone).toLocalDate()
            val rest = window(w, now, day.atTime(23, 0).atZone(zone).toInstant()).mapNotNull { it.temperatureC }
            val drop = rest.minOrNull()?.let { w.temperatureC - it }
            if (drop != null && drop >= 7) out += Smart.TEMP_SWING to ComfortText("晚上會明顯變涼",
                "今晚最低約 ${rest.min().roundToInt()}°，比現在低 ${drop.roundToInt()}°",
                "現在 ${w.temperatureC.roundToInt()}°，今晚會降到約 ${rest.min().roundToInt()}°。晚點回家記得帶件外套。")
        }
        if (Smart.UV_HIGH in enabled) {
            val uv = (next.mapNotNull { it.uvIndex } + listOfNotNull(w.currentUv)).maxOrNull()
            val local = now.atZone(zone).toLocalTime()
            if (uv != null && uv >= 8 && local.isAfter(LocalTime.of(8, 0)) && local.isBefore(LocalTime.of(15, 0)))
                out += Smart.UV_HIGH to ComfortText("紫外線${uvWord(uv)}", "紫外線指數約 ${uv.roundToInt()}",
                    "接下來幾小時紫外線指數約 ${uv.roundToInt()}（${uvWord(uv)}）。外出請擦防曬、戴帽子，盡量走陰涼處。")
        }
        if (Smart.AIR_BAD in enabled) {
            val a = w.usAqi
            if (a != null && a > 100) out += Smart.AIR_BAD to ComfortText("空氣品質${aqiWord(a)}", "AQI 約 $a",
                "目前 AQI 約 $a（${aqiWord(a)}，模式估算）。敏感族群減少戶外活動，外出可戴口罩，室內關窗。")
        }
        return out
    }

    /** Quiet hours may wrap midnight (e.g. 22:00–07:00). */
    fun quiet(now: LocalTime, from: LocalTime, to: LocalTime): Boolean =
        if (from == to) false else if (from < to) !now.isBefore(from) && now.isBefore(to) else !now.isBefore(from) || now.isBefore(to)

    // ---------- commute ----------
    /** Weather for a trip leaving at [leave]: departure place now-ish and destination on arrival. */
    fun commute(from: WeatherSnapshot, to: WeatherSnapshot?, leave: Instant, zone: ZoneId,
                fromName: String, toName: String, label: String): ComfortText {
        fun at(w: WeatherSnapshot, t: Instant): HourlyWeather? = w.hourly.minByOrNull { kotlin.math.abs(it.at.epochSecond - t.epochSecond) }
            ?.takeIf { kotlin.math.abs(it.at.epochSecond - t.epochSecond) <= 5400 }
        val a = at(from, leave); val b = to?.let { at(it, leave.plusSeconds(3600)) }
        val pops = listOfNotNull(a?.precipitationProbabilityPct, b?.precipitationProbabilityPct)
        val maxPop = pops.maxOrNull()
        fun part(name: String, h: HourlyWeather?) = h?.let { "$name ${it.temperatureC?.roundToInt()?.let { t -> "$t°" } ?: ""}" +
            (it.precipitationProbabilityPct?.let { p -> " · 降雨 $p%" } ?: "") }
        val lines = listOfNotNull(part(fromName, a), part(toName, b)).toMutableList()
        umbrella(maxPop)?.let { lines += "雨具：$it" }
        val temps = listOfNotNull(a?.temperatureC, b?.temperatureC)
        if (temps.size == 2 && kotlin.math.abs(temps[0] - temps[1]) >= 4) lines += "兩地溫差 ${kotlin.math.abs(temps[0] - temps[1]).roundToInt()}°，多帶一件衣服"
        val summary = (if ((maxPop ?: 0) >= 50) "可能下雨，記得帶傘" else if ((maxPop ?: 0) >= 30) "可能有雨" else "天氣穩定") +
            (temps.firstOrNull()?.let { " · ${it.roundToInt()}°" } ?: "")
        return ComfortText("$label · $fromName → $toName", summary, lines.joinToString("\n"))
    }
}
