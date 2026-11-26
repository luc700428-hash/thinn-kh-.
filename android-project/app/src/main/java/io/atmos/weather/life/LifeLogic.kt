package io.atmos.weather.life

import io.atmos.weather.domain.model.HourlyWeather
import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 氣壓變化與身體不適. Weather-sensitive headaches, joint pain and asthma follow how fast the air
 * pressure falls rather than how low it is. From an hourly pressure series (past day and the next
 * two) this finds the steepest fall in any 3-hour and any 24-hour window ahead and grades it.
 * The grades follow the thresholds commonly used by weather-pain apps: a fall of about 1 hPa an
 * hour, or 6 hPa in a day, is felt by many sensitive people.
 */
object PressurePain {
    data class Point(val at: Instant, val hpa: Double)
    enum class Level(val label: String, val advice: String) {
        CALM("氣壓平穩", "氣壓變化小，頭痛、關節不適的機率低。"),
        WATCH("稍有變化", "氣壓開始下降，容易頭痛的人可以先多休息、補充水分。"),
        ALERT("注意", "氣壓明顯下降，偏頭痛、關節或舊傷可能不舒服，常用的藥物可以先準備好。"),
        WARNING("警戒", "氣壓急降，頭痛、關節痛的風險高；氣喘或舊傷容易發作的人請預先準備，避免勞累。")
    }
    data class Outlook(
        val level: Level,
        /** Steepest 3-hour fall ahead, hPa (positive = falling). */
        val drop3h: Double,
        /** Largest fall within 24 hours ahead, hPa. */
        val drop24h: Double,
        /** When the steepest fall happens (its end). */
        val worstAt: Instant?,
        /** When the pressure stops falling (the trough), if within the series. */
        val bottomAt: Instant?,
        val now: Double?,
        /** The hourly series from 12 hours ago to 36 hours ahead, for the chart. */
        val series: List<Point>,
    )

    fun grade(drop3h: Double, drop24h: Double): Level = when {
        drop3h >= 3.0 || drop24h >= 10.0 -> Level.WARNING
        drop3h >= 2.0 || drop24h >= 6.0 -> Level.ALERT
        drop3h >= 1.0 || drop24h >= 3.5 -> Level.WATCH
        else -> Level.CALM
    }

    fun assess(points: List<Point>, now: Instant = Instant.now()): Outlook? {
        val series = points.filter { it.hpa.isFinite() && it.hpa in 850.0..1090.0 }.sortedBy { it.at }.distinctBy { it.at }
        if (series.size < 6) return null
        val ahead = series.filter { !it.at.isBefore(now.minusSeconds(3600)) && !it.at.isAfter(now.plus(Duration.ofHours(24))) }
        if (ahead.size < 4) return null
        var worst3 = 0.0; var worstAt: Instant? = null
        for (i in ahead.indices) for (j in i + 1 until ahead.size) {
            val hours = Duration.between(ahead[i].at, ahead[j].at).toMinutes() / 60.0
            if (hours > 3.01) break
            val fall = ahead[i].hpa - ahead[j].hpa
            if (fall > worst3) { worst3 = fall; worstAt = ahead[j].at }
        }
        // Largest fall from any point to any later point within the next 24 hours.
        var peak = ahead.first().hpa; var worst24 = 0.0
        for (p in ahead) { peak = maxOf(peak, p.hpa); worst24 = maxOf(worst24, peak - p.hpa) }
        val nowHpa = series.minByOrNull { abs(Duration.between(it.at, now).seconds) }?.hpa
        // The trough after the steepest fall: where the pressure turns back up.
        val bottomAt = worstAt?.let { w ->
            val after = series.filter { !it.at.isBefore(w) }
            after.zipWithNext().firstOrNull { (a, b) -> b.hpa > a.hpa + .05 }?.first?.at
        }
        val chart = series.filter { !it.at.isBefore(now.minus(Duration.ofHours(12))) && !it.at.isAfter(now.plus(Duration.ofHours(36))) }
        return Outlook(grade(worst3, worst24), worst3, worst24, worstAt, bottomAt, nowHpa, chart)
    }
}

/**
 * The one-glance briefing on top of the weather page: two plain sentences that combine rain,
 * temperature swing, air, warnings and pressure, so the whole day is decided in three seconds.
 */
object DailyDigest {
    data class Inputs(
        val weather: WeatherSnapshot,
        val warnings: List<String> = emptyList(),
        val pressure: PressurePain.Level? = null,
        val closures: List<String> = emptyList(),
        val comfortBiasC: Double = 0.0,
    )

    fun build(input: Inputs, now: Instant = Instant.now(), zone: ZoneId = ZoneId.of("Asia/Taipei")): String {
        val w = input.weather
        val local = now.atZone(zone)
        val evening = local.hour >= 18
        val day = if (evening) local.toLocalDate().plusDays(1) else local.toLocalDate()
        val from = if (evening) day.atTime(6, 0).atZone(zone).toInstant() else now
        val to = day.atTime(22, 0).atZone(zone).toInstant()
        val hours = w.hourly.filter { !it.at.isBefore(from) && it.at.isBefore(to) }.sortedBy { it.at }
        val temps = hours.mapNotNull { it.temperatureC } + if (evening) emptyList() else listOf(w.temperatureC)
        val low = temps.minOrNull(); val high = temps.maxOrNull()
        val wet = hours.firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 50 }
        val maxPop = hours.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
        val whenWord = if (evening) "明天" else "今天"
        val first = buildString {
            append(whenWord)
            if (low != null && high != null) append(" ${low.roundToInt()}–${high.roundToInt()}°")
            when {
                wet != null -> append("，${clock(wet.at, zone)}起可能下雨，出門帶傘")
                maxPop != null && maxPop >= 30 -> append("，降雨機率 $maxPop%，備把折傘")
                else -> append("，大致不會下雨")
            }
            if (low != null && high != null && high - low >= 8) append("；早晚溫差 ${(high - low).roundToInt()}°，多帶件外套")
            else if (low != null) {
                val felt = low + input.comfortBiasC
                if (felt < 16) append("；偏涼，記得保暖") else if ((high ?: low) + input.comfortBiasC >= 32) append("；炎熱，注意補水")
            }
            append("。")
        }
        val extras = mutableListOf<String>()
        input.closures.firstOrNull()?.let { extras += it }
        input.warnings.take(2).forEach { extras += it }
        w.usAqi?.takeIf { it > 100 }?.let { extras += "空氣品質不佳（AQI $it），敏感族群戴口罩" }
        (hours.mapNotNull { it.uvIndex } + listOfNotNull(w.currentUv)).maxOrNull()?.takeIf { it >= 8 && !evening }?.let { extras += "紫外線很強，中午避免曝曬" }
        input.pressure?.takeIf { it >= PressurePain.Level.ALERT }?.let { extras += "氣壓明顯下降，容易頭痛的人留意" }
        val second = if (extras.isEmpty()) "沒有警特報，空氣與氣壓也都平穩。" else extras.take(3).joinToString("；") + "。"
        return "$first$second"
    }

    private fun clock(at: Instant, zone: ZoneId): String {
        val t = at.atZone(zone)
        val part = when (t.hour) { in 0..5 -> "凌晨"; in 6..10 -> "早上"; 11, 12 -> "中午"; in 13..17 -> "下午"; else -> "晚上" }
        val h = if (t.hour > 12) t.hour - 12 else t.hour
        return "$part $h 點"
    }
}

/**
 * 個人化體感校準. Each 偏冷 / 偏熱 tap moves the person's bias half a degree (at most ±2 °C); a
 * 剛好 tap pulls it a quarter back toward zero. The bias shifts clothing and comfort advice only;
 * it never changes a measured or forecast value.
 */
object ComfortCalibration {
    enum class Feeling(val label: String) { COLD("偏冷"), JUST_RIGHT("剛好"), HOT("偏熱") }
    const val LIMIT = 2.0
    fun next(bias: Double, feeling: Feeling): Double = when (feeling) {
        // Feeling cold means the day feels colder to them than the number says.
        Feeling.COLD -> bias - .5
        Feeling.HOT -> bias + .5
        Feeling.JUST_RIGHT -> bias * .75
    }.coerceIn(-LIMIT, LIMIT).let { (it * 4).roundToInt() / 4.0 }

    fun describe(bias: Double): String = when {
        bias <= -.25 -> "你比一般人怕冷約 ${"%.1f".format(java.util.Locale.US, -bias)}°，建議已自動多加一層"
        bias >= .25 -> "你比一般人怕熱約 ${"%.1f".format(java.util.Locale.US, bias)}°，建議已自動穿少一點"
        else -> "你的體感接近一般人，建議照標準給"
    }
}

/**
 * 天氣版 IFTTT: a rule fires at a set time on chosen weekdays when every condition holds for
 * the chosen day.
 */
object WeatherRules {
    enum class Period(val label: String) { TODAY("今天"), TOMORROW("明天"), WEEKEND("這個週末") }
    enum class Metric(val label: String, val unit: String) {
        RAIN("降雨機率", "%"), HIGH("最高溫", "°"), LOW("最低溫", "°"), WIND("風力", "級"), UV("紫外線", ""), AQI("空氣品質 AQI", "")
    }
    enum class Op(val label: String) { BELOW("低於"), ABOVE("高於") }
    data class Condition(val metric: Metric, val op: Op, val value: Double)
    data class Rule(
        val id: String,
        val name: String,
        val weekdays: Int,
        val hour: Int,
        val minute: Int,
        val period: Period,
        val conditions: List<Condition>,
        val message: String,
        val enabled: Boolean = true,
    )

    /** Beaufort force from km/h (the CWA uses Beaufort for wind). */
    fun beaufort(kmh: Double): Int {
        val ms = kmh / 3.6
        val limits = doubleArrayOf(.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7)
        return limits.indexOfFirst { ms < it }.let { if (it < 0) 12 else it }
    }

    fun days(period: Period, today: LocalDate): List<LocalDate> = when (period) {
        Period.TODAY -> listOf(today)
        Period.TOMORROW -> listOf(today.plusDays(1))
        Period.WEEKEND -> {
            val sat = generateSequence(today) { it.plusDays(1) }.first { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            if (sat.dayOfWeek == DayOfWeek.SUNDAY) listOf(sat) else listOf(sat, sat.plusDays(1))
        }
    }

    /** The value of [metric] over [days] (worst case), or null when the forecast does not cover it. */
    fun value(w: WeatherSnapshot, metric: Metric, days: List<LocalDate>, zone: ZoneId): Double? {
        val hours = w.hourly.filter { it.at.atZone(zone).toLocalDate() in days && it.at.atZone(zone).hour in 6..21 }
        val daily = w.daily.filter { it.date in days }
        return when (metric) {
            Metric.RAIN -> (hours.mapNotNull { it.precipitationProbabilityPct } + daily.mapNotNull { it.precipitationProbabilityPct }).maxOrNull()?.toDouble()
            Metric.HIGH -> (hours.mapNotNull { it.temperatureC } + daily.mapNotNull { it.highC }).maxOrNull()
            Metric.LOW -> (hours.mapNotNull { it.temperatureC } + daily.mapNotNull { it.lowC }).minOrNull()
            Metric.WIND -> hours.mapNotNull { it.windKmh }.maxOrNull()?.let { beaufort(it).toDouble() }
            Metric.UV -> hours.mapNotNull { it.uvIndex }.maxOrNull()
            Metric.AQI -> if (days.contains(LocalDate.now(zone))) w.usAqi?.toDouble() else null
        }
    }

    data class Verdict(val fires: Boolean, val explanation: String)

    fun evaluate(rule: Rule, w: WeatherSnapshot, today: LocalDate, zone: ZoneId): Verdict {
        val days = days(rule.period, today)
        val parts = rule.conditions.map { c ->
            val v = value(w, c.metric, days, zone)
            val ok = v != null && if (c.op == Op.BELOW) v < c.value else v > c.value
            Triple(c, v, ok)
        }
        val text = parts.joinToString("、") { (c, v, _) -> "${c.metric.label} ${v?.let { fmt(it) + c.metric.unit } ?: "無資料"}" }
        return Verdict(parts.isNotEmpty() && parts.all { it.third }, "${rule.period.label}：$text")
    }

    fun fmt(v: Double) = if (abs(v - v.roundToInt()) < .05) "${v.roundToInt()}" else "%.1f".format(java.util.Locale.US, v)

    /** Ready-made rules people can add with one tap. */
    fun templates(): List<Rule> = listOf(
        Rule("tpl-wash", "適合洗車／露營", 1 shl 4, 20, 0, Period.WEEKEND,
            listOf(Condition(Metric.RAIN, Op.BELOW, 20.0), Condition(Metric.WIND, Op.BELOW, 3.0)), "週末好天氣，適合洗車或露營"),
        Rule("tpl-laundry", "明天適合晾衣服", 127, 21, 0, Period.TOMORROW,
            listOf(Condition(Metric.RAIN, Op.BELOW, 20.0)), "明天降雨機率低，衣服可以拿出去晾"),
        Rule("tpl-cold", "明天變冷提醒", 127, 21, 30, Period.TOMORROW,
            listOf(Condition(Metric.LOW, Op.BELOW, 14.0)), "明天最低溫偏低，記得多穿一件"),
        Rule("tpl-run", "晨跑空氣好", 31, 6, 0, Period.TODAY,
            listOf(Condition(Metric.AQI, Op.BELOW, 51.0), Condition(Metric.RAIN, Op.BELOW, 30.0)), "空氣好又不太會下雨，適合出門晨跑"),
    )
}

/**
 * 家人天氣關懷: people whose weather the user looks after, with a relation label. The worker
 * tells the user when their place faces heavy rain, a warning, a sharp cold drop or great heat.
 */
object FamilyCare {
    val relations = listOf("爸媽", "伴侶", "孩子", "家人", "朋友")
    data class Person(val id: String, val relation: String, val nickname: String, val place: String, val lat: Double, val lon: Double)
    data class Concern(val key: String, val title: String, val detail: String)

    fun concerns(p: Person, w: WeatherSnapshot, warnings: List<String>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.of("Asia/Taipei")): List<Concern> {
        val out = mutableListOf<Concern>()
        val who = p.nickname.ifBlank { p.relation }
        val today = now.atZone(zone).toLocalDate()
        fun dayTemps(d: LocalDate) = w.hourly.filter { it.at.atZone(zone).toLocalDate() == d }.mapNotNull { it.temperatureC }
        val todayLow = (dayTemps(today) + listOfNotNull(w.minTempC)).minOrNull()
        val tomorrow = dayTemps(today.plusDays(1))
        val tomorrowLow = (tomorrow + w.daily.filter { it.date == today.plusDays(1) }.mapNotNull { it.lowC }).minOrNull()
        val tomorrowHigh = (tomorrow + w.daily.filter { it.date == today.plusDays(1) }.mapNotNull { it.highC }).maxOrNull()
        warnings.firstOrNull { it.contains("豪雨") || it.contains("大雨") || it.contains("颱風") || it.contains("強風") || it.contains("低溫") }?.let {
            out += Concern("warn:$it", "${who}那裡有$it", "${p.place}目前有$it，傳個訊息關心一下吧。")
        }
        if (todayLow != null && tomorrowLow != null && todayLow - tomorrowLow >= 8)
            out += Concern("cold:${today.plusDays(1)}", "${who}那裡明天驟降 ${(todayLow - tomorrowLow).roundToInt()}°",
                "${p.place}明天最低約 ${tomorrowLow.roundToInt()}°，比今天低 ${(todayLow - tomorrowLow).roundToInt()}°，提醒多穿一點。")
        else if (tomorrowLow != null && tomorrowLow <= 10)
            out += Concern("chill:${today.plusDays(1)}", "${who}那裡明天很冷", "${p.place}明天最低約 ${tomorrowLow.roundToInt()}°，記得保暖。")
        if (tomorrowHigh != null && tomorrowHigh >= 35)
            out += Concern("heat:${today.plusDays(1)}", "${who}那裡明天高溫 ${tomorrowHigh.roundToInt()}°", "${p.place}明天很熱，提醒多喝水、避開中午外出。")
        val heavy = w.hourly.filter { it.at.isAfter(now) && it.at.isBefore(now.plus(Duration.ofHours(24))) }
            .firstOrNull { (it.precipitationProbabilityPct ?: 0) >= 80 }
        if (heavy != null && out.none { it.key.startsWith("warn") })
            out += Concern("rain:${heavy.at.atZone(zone).toLocalDate()}", "${who}那裡可能下大雨",
                "${p.place}${heavy.at.atZone(zone).hour} 點前後降雨機率 ${heavy.precipitationProbabilityPct}%，提醒帶傘。")
        return out
    }

    /** The words on the care card sent to them. */
    fun careMessage(p: Person, w: WeatherSnapshot, concern: Concern?): String {
        val who = p.nickname.ifBlank { p.relation }
        val body = concern?.detail ?: "${p.place}現在 ${w.temperatureC.roundToInt()}°" +
            (w.nextHourRainProbabilityPct?.let { "，降雨機率 $it%" } ?: "") + "。"
        return "$who，$body 照顧好自己 ❤"
    }
}

/** Hourly helpers shared by the features. */
internal fun List<HourlyWeather>.nearest(at: Instant): HourlyWeather? =
    minByOrNull { abs(Duration.between(it.at, at).seconds) }?.takeIf { abs(Duration.between(it.at, at).toHours()) <= 2 }
