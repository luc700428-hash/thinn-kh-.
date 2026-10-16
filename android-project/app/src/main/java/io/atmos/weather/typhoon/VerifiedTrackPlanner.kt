package io.atmos.weather.typhoon

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/** Domain-only validator: never join positions from different storms or unlabeled forecasts. */
data class DatedTyphoonPosition(
    val stormId: String,
    val latitude: Double,
    val longitude: Double,
    val forecast: Boolean,
    val issuedTime: String
)

object VerifiedTrackPlanner {
    private val taipei = ZoneId.of("Asia/Taipei")
    private val patterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    )

    fun parseTime(source: String): Instant? {
        val raw = source.trim()
        if(raw.isEmpty()) return null
        runCatching { Instant.parse(raw) }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()?.let { return it }
        for (pattern in patterns) {
            runCatching { LocalDateTime.parse(raw, pattern).atZone(taipei).toInstant() }
                .getOrNull()?.let { return it }
        }
        return null
    }

    private fun kilometers(a: DatedTyphoonPosition,b: DatedTyphoonPosition): Double {
        val dLat=Math.toRadians(b.latitude-a.latitude)
        val dLon=Math.toRadians(b.longitude-a.longitude)
        val lat1=Math.toRadians(a.latitude)
        val lat2=Math.toRadians(b.latitude)
        val h=sin(dLat/2).pow(2) + cos(lat1)*cos(lat2)*sin(dLon/2).pow(2)
        return 12742.0*asin(sqrt(h.coerceIn(0.0,1.0)))
    }

    /** Returns only independently identifiable, timed forecast segments, never fake curves or cones. */
    fun verifiedForecastSegments(points:List<DatedTyphoonPosition>):List<List<DatedTyphoonPosition>> {
        val output=mutableListOf<List<DatedTyphoonPosition>>()
        for((storm, items) in points.filter {
            it.forecast && it.stormId.isNotBlank() && it.stormId.length<=64 &&
                it.latitude in 0.0..55.0 && it.longitude in 100.0..180.0 &&
                parseTime(it.issuedTime)!=null
        }.groupBy {it.stormId}) {
            if(storm.isBlank()) continue
            val sorted=items.distinctBy {parseTime(it.issuedTime)}.sortedBy {parseTime(it.issuedTime)}
            var segment=mutableListOf<DatedTyphoonPosition>()
            for(point in sorted) {
                val before=segment.lastOrNull()
                if(before!=null) {
                    val hours=(parseTime(point.issuedTime)!!.epochSecond-parseTime(before.issuedTime)!!.epochSecond)/3600.0
                    val speed=if(hours>0)kilometers(before,point)/hours else Double.POSITIVE_INFINITY
                    // Do not connect disconnected storm tracks, stale forecast runs, or implausible jumps.
                    if(hours !in 0.5..36.0 || speed>140.0) {
                        if(segment.size>=2) output.add(segment)
                        segment=mutableListOf()
                    }
                }
                segment.add(point)
            }
            if(segment.size>=2) output.add(segment)
        }
        return output.take(20)
    }
}
