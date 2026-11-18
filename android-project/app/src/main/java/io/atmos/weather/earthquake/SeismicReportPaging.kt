package io.atmos.weather.earthquake

import kotlin.math.abs

/**
 * Loading older earthquake reports as the list is scrolled. The official CWA service is asked
 * first; when it has nothing older (or does not support asking by time), the list continues with
 * ExpTech's longer history. Each step either adds reports, moves to the next source, or ends:
 * it can never keep repeating the same page.
 */
internal object SeismicReportPaging {
    enum class Stage { OFFICIAL, EXPTECH, DONE }

    /** ExpTech's list is asked for in growing sizes, up to this many reports. */
    const val EXPTECH_MAX = 300
    const val EXPTECH_STEP = 40

    /**
     * Adds [more] to [shown], newest first, without repeats. The same quake can come from both
     * sources under different ids, so reports within 90 seconds and 0.3 magnitude are one quake.
     */
    fun merge(shown: List<EarthquakeReport>, more: List<EarthquakeReport>): List<EarthquakeReport> {
        val out = shown.distinctBy { it.id }.toMutableList()
        for (r in more.sortedByDescending { it.origin }) {
            if (out.any { it.id == r.id || sameQuake(it, r) }) continue
            out += r
        }
        return out.sortedByDescending { it.origin }
    }

    fun sameQuake(a: EarthquakeReport, b: EarthquakeReport): Boolean =
        abs(a.origin.epochSecond - b.origin.epochSecond) <= 90 && abs(a.magnitude - b.magnitude) <= .3

    /** Where to go after a step that added [added] reports. */
    fun next(stage: Stage, added: Int, expTechLimit: Int): Stage = when (stage) {
        Stage.OFFICIAL -> if (added > 0) Stage.OFFICIAL else Stage.EXPTECH
        Stage.EXPTECH -> if (expTechLimit >= EXPTECH_MAX && added == 0) Stage.DONE else Stage.EXPTECH
        Stage.DONE -> Stage.DONE
    }
}
