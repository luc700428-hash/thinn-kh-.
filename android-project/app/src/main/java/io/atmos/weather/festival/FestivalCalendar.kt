package io.atmos.weather.festival

import android.icu.util.ChineseCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.sin

/** The festivals that get an easter egg, with the line shown when it starts. */
enum class Festival(val label: String, val greeting: String) {
    NEW_YEAR("元旦", "新年快樂！"),
    LUNAR_NEW_YEAR("春節", "新春快樂 · 恭喜發財"),
    LANTERN("元宵節", "元宵節快樂 · 花好月圓"),
    VALENTINE("情人節", "情人節快樂 ♥"),
    CHILDREN("兒童節", "兒童節快樂！"),
    QINGMING("清明節", "清明時節 · 慎終追遠"),
    MOTHERS_DAY("母親節", "母親節快樂 · 媽媽辛苦了"),
    DRAGON_BOAT("端午節", "端午節安康"),
    QIXI("七夕", "七夕情人節快樂 ♥"),
    FATHERS_DAY("父親節", "八八節快樂 · 爸爸辛苦了"),
    GHOST_FESTIVAL("中元節", "中元普渡 · 平安順心"),
    MID_AUTUMN("中秋節", "中秋節快樂 · 月圓人團圓"),
    TEACHERS_DAY("教師節", "老師辛苦了 · 教師節快樂"),
    NATIONAL_DAY("國慶日", "雙十國慶 · 國慶日快樂"),
    HALLOWEEN("萬聖節", "Trick or Treat！萬聖節快樂"),
    WINTER_SOLSTICE("冬至", "冬至快樂 · 記得吃湯圓"),
    CHRISTMAS("聖誕節", "Merry Christmas · 聖誕快樂"),
    NEW_YEARS_EVE("跨年", "跨年倒數中"),
}

/**
 * Which festival is on at a moment in Taiwan, and the timed moments within it (countdowns and
 * fireworks shows). Lunar dates come from the platform's Chinese calendar.
 */
object FestivalCalendar {
    val zone: ZoneId = ZoneId.of("Asia/Taipei")

    /** A countdown to [target] that starts ten minutes before it. */
    data class Countdown(val target: Long, val title: String, val after: String)

    /** A fireworks show of [seconds] starting at [start] (epoch ms). */
    data class Show(val start: Long, val seconds: Int, val title: String, val firecrackers: Boolean, val seed: Long)

    const val COUNTDOWN_MINUTES = 10L
    const val SHOW_SECONDS = 180

    /** Gregorian date of a lunar month/day in the lunar year that starts in [year]. */
    private val lunarCache = HashMap<Int, LocalDate>()

    fun lunar(year: Int, month: Int, day: Int): LocalDate = synchronized(lunarCache) {
        lunarCache.getOrPut(year * 10_000 + month * 100 + day) { computeLunar(year, month, day) }
    }

    /**
     * The platform calendar says which month is which (leap months included), but its new moons
     * can be minutes off, enough to move a festival by a day when the moon is new near midnight
     * (2027's Lunar New Year, for one). The first day is therefore taken from Meeus' new-moon
     * formula (good to about a minute) in Taiwan time.
     */
    private fun computeLunar(year: Int, month: Int, day: Int): LocalDate {
        val rough = icuLunar(year, month, 1)
        val k = Math.round(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2000, 1, 6), rough) / 29.530588861).toDouble()
        val first = java.time.Instant.ofEpochMilli(((newMoon(k) - 2440587.5) * 86_400_000.0).toLong()).atZone(zone).toLocalDate()
        val start = if (abs(java.time.temporal.ChronoUnit.DAYS.between(rough, first)) <= 1) first else rough
        return start.plusDays(day - 1L)
    }

    /** Julian day (UT) of new moon number [k] counted from January 2000 (Meeus, ch. 49). */
    fun newMoon(k: Double): Double {
        fun r(d: Double) = Math.toRadians(d)
        val t = k / 1236.85
        val jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t * t - 0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = r(2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t * t * t)
        val mp = r(201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t * t * t - 0.000000058 * t * t * t * t)
        val f = r(160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t * t * t + 0.000000011 * t * t * t * t)
        val o = r(124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t * t * t)
        var c = -0.40720 * sin(mp) + 0.17241 * e * sin(m) + 0.01608 * sin(2 * mp) + 0.01039 * sin(2 * f) +
            0.00739 * e * sin(mp - m) - 0.00514 * e * sin(mp + m) + 0.00208 * e * e * sin(2 * m) - 0.00111 * sin(mp - 2 * f) -
            0.00057 * sin(mp + 2 * f) + 0.00056 * e * sin(2 * mp + m) - 0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * f) +
            0.00038 * e * sin(m - 2 * f) - 0.00024 * e * sin(2 * mp - m) - 0.00017 * sin(o) - 0.00007 * sin(mp + 2 * m) +
            0.00004 * sin(2 * mp - 2 * f) + 0.00004 * sin(3 * m) + 0.00003 * sin(mp + m - 2 * f) + 0.00003 * sin(2 * mp + 2 * f) -
            0.00003 * sin(mp + m + 2 * f) + 0.00003 * sin(mp - m + 2 * f) - 0.00002 * sin(mp - m - 2 * f) - 0.00002 * sin(3 * mp + m) +
            0.00002 * sin(4 * mp)
        val planets = arrayOf(
            299.77 + 0.107408 * k - 0.009173 * t * t to 325, 251.88 + 0.016321 * k to 165, 251.83 + 26.651886 * k to 164,
            349.42 + 36.412478 * k to 126, 84.66 + 18.206239 * k to 110, 141.74 + 53.303771 * k to 62, 207.14 + 2.453732 * k to 60,
            154.84 + 7.306860 * k to 56, 34.52 + 27.261239 * k to 47, 207.19 + 0.121824 * k to 42, 291.34 + 1.844379 * k to 40,
            161.72 + 24.198154 * k to 37, 239.56 + 25.513099 * k to 35, 331.55 + 3.592518 * k to 23)
        for ((a, w) in planets) c += w * 1e-6 * sin(r(a))
        return jde + c - 72.0 / 86_400   // ΔT ≈ 72 s
    }

    private fun icuLunar(year: Int, month: Int, day: Int): LocalDate {
        val c = ChineseCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Taipei"))
        c.set(ChineseCalendar.EXTENDED_YEAR, year + 2637)
        c.set(ChineseCalendar.MONTH, month - 1)
        c.set(ChineseCalendar.IS_LEAP_MONTH, 0)
        c.set(ChineseCalendar.DAY_OF_MONTH, day)
        c.set(ChineseCalendar.HOUR_OF_DAY, 12)
        val g = android.icu.util.GregorianCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Taipei"))
        g.timeInMillis = c.timeInMillis
        return LocalDate.of(g.get(android.icu.util.Calendar.YEAR), g.get(android.icu.util.Calendar.MONTH) + 1, g.get(android.icu.util.Calendar.DAY_OF_MONTH))
    }

    fun lunarNewYear(year: Int): LocalDate = lunar(year, 1, 1)

    /** Qingming (sun at 15°): the usual 21st-century rule, 4 or 5 April. */
    fun qingming(year: Int): LocalDate {
        val y = year % 100
        return LocalDate.of(year, 4, (y * 0.2422 + 4.81).toInt() - y / 4)
    }

    /** Winter solstice: 21 or 22 December this century. */
    fun winterSolstice(year: Int): LocalDate {
        val y = year % 100
        return LocalDate.of(year, 12, (y * 0.2422 + 21.94).toInt() - y / 4)
    }

    /** Every festival day of [year], most specific first when two share a day. */
    fun days(year: Int): List<Pair<LocalDate, Festival>> {
        val list = ArrayList<Pair<LocalDate, Festival>>()
        list += LocalDate.of(year, 1, 1) to Festival.NEW_YEAR
        val ny = lunarNewYear(year)
        list += ny.minusDays(1) to Festival.LUNAR_NEW_YEAR
        for (d in 0L..4L) list += ny.plusDays(d) to Festival.LUNAR_NEW_YEAR
        list += lunar(year, 1, 15) to Festival.LANTERN
        list += LocalDate.of(year, 2, 14) to Festival.VALENTINE
        list += LocalDate.of(year, 4, 4) to Festival.CHILDREN
        list += qingming(year) to Festival.QINGMING
        list += LocalDate.of(year, 5, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY)) to Festival.MOTHERS_DAY
        list += lunar(year, 5, 5) to Festival.DRAGON_BOAT
        list += lunar(year, 7, 7) to Festival.QIXI
        list += LocalDate.of(year, 8, 8) to Festival.FATHERS_DAY
        list += lunar(year, 7, 15) to Festival.GHOST_FESTIVAL
        list += lunar(year, 8, 15) to Festival.MID_AUTUMN
        list += LocalDate.of(year, 9, 28) to Festival.TEACHERS_DAY
        list += LocalDate.of(year, 10, 10) to Festival.NATIONAL_DAY
        list += LocalDate.of(year, 10, 31) to Festival.HALLOWEEN
        list += winterSolstice(year) to Festival.WINTER_SOLSTICE
        list += LocalDate.of(year, 12, 24) to Festival.CHRISTMAS
        list += LocalDate.of(year, 12, 25) to Festival.CHRISTMAS
        list += LocalDate.of(year, 12, 31) to Festival.NEW_YEARS_EVE
        return list
    }

    private val cache = HashMap<Int, List<Pair<LocalDate, Festival>>>()
    private fun cachedDays(year: Int) = synchronized(cache) { cache.getOrPut(year) { days(year) } }

    /** The festival on at [millis], if any. A day with two keeps the one listed first. */
    fun at(millis: Long): Festival? {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)
        val date = now.toLocalDate()
        // New Year's Day keeps the midnight show going after 00:00.
        if (date.monthValue == 1 && date.dayOfMonth == 1 && now.toLocalTime() < LocalTime.of(0, 3)) return Festival.NEW_YEARS_EVE
        // The lunar year that a January date belongs to may have started the previous Gregorian year: check both.
        val found = (cachedDays(date.year) + if (date.monthValue <= 2) cachedDays(date.year - 1).filter { it.second == Festival.LUNAR_NEW_YEAR || it.second == Festival.LANTERN } else emptyList())
            .filter { it.first == date }.map { it.second }
        return when {
            found.isEmpty() -> null
            Festival.LUNAR_NEW_YEAR in found -> Festival.LUNAR_NEW_YEAR
            Festival.NEW_YEARS_EVE in found -> Festival.NEW_YEARS_EVE
            Festival.CHRISTMAS in found -> Festival.CHRISTMAS
            Festival.CHILDREN in found -> Festival.CHILDREN
            else -> found.first()
        }
    }

    private fun epoch(date: LocalDate, time: LocalTime) = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    /** Midnight targets: the Gregorian new year and the lunar new year. */
    fun midnights(year: Int): List<Pair<Long, Pair<String, String>>> = listOf(
        epoch(LocalDate.of(year, 1, 1), LocalTime.MIDNIGHT) to ("跨年倒數 · $year" to "$year 新年快樂！"),
        epoch(lunarNewYear(year), LocalTime.MIDNIGHT) to ("除夕守歲 · 新春倒數" to "新春快樂 · 恭喜發財！"),
    )

    /** The countdown on at [millis]: the last ten minutes before a new year. */
    fun countdown(millis: Long): Countdown? {
        val year = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).year
        for (y in listOf(year, year + 1)) for ((target, words) in midnights(y)) {
            if (millis in (target - COUNTDOWN_MINUTES * 60_000) until target) return Countdown(target, words.first, words.second)
        }
        return null
    }

    /** The fireworks show on at [millis]: three minutes from each new year, and 20:00 on National Day. */
    fun show(millis: Long): Show? {
        val year = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).year
        val shows = ArrayList<Show>()
        for (y in year - 1..year + 1) {
            val (gregorian, lunarNy) = midnights(y)
            shows += Show(gregorian.first, SHOW_SECONDS, gregorian.second.second, firecrackers = false, seed = gregorian.first)
            shows += Show(lunarNy.first, SHOW_SECONDS, lunarNy.second.second, firecrackers = true, seed = lunarNy.first)
            val national = epoch(LocalDate.of(y, 10, 10), LocalTime.of(20, 0))
            shows += Show(national, SHOW_SECONDS, "雙十國慶 · 國慶煙火", firecrackers = false, seed = national)
        }
        return shows.firstOrNull { millis >= it.start && millis < it.start + it.seconds * 1000L }
    }

    /** Christmas runs from Christmas Eve 00:00 to the end of Christmas Day. */
    fun isChristmas(millis: Long): Boolean {
        val d = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).toLocalDate()
        return d.monthValue == 12 && (d.dayOfMonth == 24 || d.dayOfMonth == 25)
    }

    fun isNight(millis: Long): Boolean {
        val h = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).hour
        return h >= 18 || h < 6
    }
}
