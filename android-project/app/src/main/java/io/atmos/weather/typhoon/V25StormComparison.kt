package io.atmos.weather.typhoon

import java.time.Instant
import java.time.Duration
import kotlin.math.*

/** Compare two independently sourced forecast positions only at matching valid times.
 * Never interpolates a storm path, invents a forecast point, or replaces a CWA warning.
 */
object V25StormComparison {
    data class Position(val longitude: Double, val latitude: Double, val validTime: String)
    data class Match(val time: Instant, val official: Position, val ai: Position, val distanceKm: Int)

    /** A CSV ensemble track without verified storm identity is not comparable if CWA
     * currently lists several distinct cyclones. Require selecting one first. */
    fun compare(official: List<Position>, ai: List<Position>, maxMinutes: Long = 90L): List<Match> {
        if (maxMinutes !in 0L..180L) return emptyList()
        val reference = official.mapNotNull { p ->
            val t = VerifiedTrackPlanner.parseTime(p.validTime)
            if (valid(p) && t != null) t to p else null
        }.distinctBy { it.first }
        val experimental = ai.mapNotNull { p ->
            val t = VerifiedTrackPlanner.parseTime(p.validTime)
            if (valid(p) && t != null) t to p else null
        }.distinctBy { it.first }
        val toleranceSeconds = maxMinutes * 60L
        return reference.mapNotNull { (time, p) ->
            val nearest = experimental.minByOrNull { abs(Duration.between(time, it.first).seconds) }
                ?: return@mapNotNull null
            if (abs(Duration.between(time, nearest.first).seconds) > toleranceSeconds) return@mapNotNull null
            Match(time, p, nearest.second, kilometers(p, nearest.second).roundToInt())
        }.sortedBy { it.time }.take(100)
    }

    private fun valid(p: Position) =
        p.latitude.isFinite() && p.longitude.isFinite() &&
        p.latitude in 0.0..55.0 && p.longitude in 100.0..180.0

    private fun kilometers(a: Position, b: Position): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la = Math.toRadians(a.latitude)
        val lb = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(la) * cos(lb) * sin(dLon / 2).pow(2)
        return 12742.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
