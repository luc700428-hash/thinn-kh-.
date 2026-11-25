package io.atmos.weather.festival

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * Process-wide festival state shared by every window: the preview clock, the moments when Santa,
 * the dragon boat or a ghost pass, and which window is on top (only that one draws).
 */
object FestivalDirector {
    const val SETTING = "festival_effects"
    const val PREVIEW_MS = 75_000L

    data class Preview(val festival: Festival, val realStart: Long, val anchor: Long)

    var preview by mutableStateOf<Preview?>(null)
        private set

    /** Tests and test devices never animate: an endless frame loop would keep them from idling. */
    val disabled: Boolean by lazy {
        android.os.Build.FINGERPRINT == "robolectric" ||
            runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry"); true }.getOrDefault(false)
    }

    /** The festival clock: real time, or a moment inside the festival while a preview runs. */
    fun now(real: Long): Long {
        val p = preview ?: return real
        if (real - p.realStart > PREVIEW_MS || real < p.realStart) return real
        return p.anchor + (real - p.realStart)
    }

    /** Ends a preview that has run its course; called from effects, never while composing. */
    fun expire(real: Long) {
        val p = preview ?: return
        if (real - p.realStart > PREVIEW_MS || real < p.realStart) stopPreview()
    }

    /** A moment that shows [festival] at its best: just before the midnight countdown ends, the show, or the evening. */
    fun anchorFor(festival: Festival, real: Long): Long {
        val year = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(real), FestivalCalendar.zone).year
        fun at(date: java.time.LocalDate, time: LocalTime) = LocalDateTime.of(date, time).atZone(FestivalCalendar.zone).toInstant().toEpochMilli()
        return when (festival) {
            Festival.NEW_YEARS_EVE -> at(java.time.LocalDate.of(year + 1, 1, 1), LocalTime.MIDNIGHT) - 12_000
            Festival.LUNAR_NEW_YEAR -> at(FestivalCalendar.lunarNewYear(year + 1), LocalTime.MIDNIGHT) - 12_000
            Festival.NATIONAL_DAY -> at(java.time.LocalDate.of(year, 10, 10), LocalTime.of(20, 0)) - 2_500
            else -> {
                val day = FestivalCalendar.days(year).firstOrNull { it.second == festival }?.first ?: java.time.LocalDate.of(year, 12, 24)
                at(day, LocalTime.of(20, 30))
            }
        }
    }

    fun startPreview(festival: Festival, real: Long = System.currentTimeMillis()) {
        reset(); preview = Preview(festival, real, anchorFor(festival, real))
    }

    fun stopPreview() { preview = null; reset() }

    // ---- passes (Santa, dragon boat, ghost) and the greeting ----

    enum class Pass(val seconds: Float, val first: Long, val minGap: Long, val maxGap: Long) {
        SANTA(10f, 3_500, 150_000, 280_000),
        BOAT(11f, 3_000, 70_000, 120_000),
        GHOST(8f, 5_000, 40_000, 80_000),
        BANNER(6f, 700, Long.MAX_VALUE / 4, Long.MAX_VALUE / 4),
    }

    private var festivalKey: String? = null
    private val starts = HashMap<Pass, MutableList<Long>>()
    private val random = Random(System.nanoTime())

    private fun reset() { synchronized(starts) { starts.clear(); festivalKey = null } }

    /** Called once per frame with the festival clock; a new festival (or day) starts a new schedule. */
    fun observe(key: String?, now: Long) = synchronized(starts) {
        if (key != festivalKey) { festivalKey = key; starts.clear() }
    }

    /** Start times of [pass] up to [now], scheduling the next one as time goes by. */
    fun starts(pass: Pass, now: Long): List<Long> = synchronized(starts) {
        val list = starts.getOrPut(pass) { mutableListOf(now + pass.first) }
        while (list.last() <= now) list += list.last() + pass.minGap + (random.nextDouble() * (pass.maxGap - pass.minGap)).toLong()
        if (list.size > 6) list.subList(0, list.size - 6).clear()
        list.toList()
    }

    /** Seconds into the current pass, or null when none is under way. */
    fun progress(pass: Pass, now: Long): Float? {
        val s = starts(pass, now).lastOrNull { it <= now } ?: return null
        val sec = (now - s) / 1000f
        return sec.takeIf { it < pass.seconds }
    }

    /** Only the top-most window draws, so pages over the home screen are not drawn twice. */
    val layers = mutableStateListOf<Any>()
}
