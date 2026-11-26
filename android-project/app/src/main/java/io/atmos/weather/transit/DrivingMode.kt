package io.atmos.weather.transit

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 駕車模式. Moving at road speed away from every railway means a car (or a scooter): the card
 * then shows only what a driver can take in at a glance — whether rain lies ahead on the road in
 * about 15 and 45 minutes — in large text, with nothing to tap.
 */
object DrivingMode {
    /** [recentHeading]: over the last few hundred metres only, which follows curves on a freeway. */
    data class Drive(val speedKmh: Int, val headingDeg: Double, val lat: Double, val lon: Double, val recentHeading: Double = headingDeg)
    data class Ahead(val minutes: Int, val km: Int, val lat: Double, val lon: Double)

    /**
     * Road speed (20–150 km/h, median of recent fixes) for at least 45 seconds, and more than
     * 1.2 km from any railway — or the person said it is a car when we guessed a train.
     */
    fun judge(samples: List<RailMode.Sample>, saidCar: Boolean = false): Drive? {
        val recent = samples.filter { it.speedMs.isFinite() && it.speedMs >= 0 }.sortedBy { it.timeMs }
        if (recent.size < 4 || recent.last().timeMs - recent.first().timeMs < 45_000L) return null
        val speeds = recent.takeLast(8).map { it.speedMs }.sorted()
        val kmh = speeds[speeds.size / 2] * 3.6f
        if (kmh < 20f || kmh > 150f) return null
        val first = recent.first(); val last = recent.last()
        if (RailMode.km(first.lat, first.lon, last.lat, last.lon) < .3) return null
        val awayFromRail = RailMode.railOffKm(last.lat, last.lon) > 1.2 && RailMode.railOffKm(first.lat, first.lon) > 1.2
        if (!awayFromRail && !saidCar) return null
        val back = recent.lastOrNull { RailMode.km(it.lat, it.lon, last.lat, last.lon) >= .12 } ?: first
        return Drive(kmh.roundToInt(), bearing(first.lat, first.lon, last.lat, last.lon), last.lat, last.lon,
            bearing(back.lat, back.lon, last.lat, last.lon))
    }

    fun bearing(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val y = sin(Math.toRadians(bLon - aLon)) * cos(Math.toRadians(bLat))
        val x = cos(Math.toRadians(aLat)) * sin(Math.toRadians(bLat)) - sin(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * cos(Math.toRadians(bLon - aLon))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** The point [km] ahead along [headingDeg] (a straight-line estimate of where the road leads). */
    fun pointAhead(lat: Double, lon: Double, headingDeg: Double, km: Double): Pair<Double, Double> {
        val r = 6371.0; val d = km / r; val b = Math.toRadians(headingDeg)
        val la = Math.toRadians(lat); val lo = Math.toRadians(lon)
        val la2 = asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
        val lo2 = lo + atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(la2))
        return Math.toDegrees(la2) to Math.toDegrees(lo2)
    }

    /** Where the car will be in 15 and 45 minutes at today's average speed (at least 1 km away). */
    fun aheads(d: Drive): List<Ahead> = listOf(15, 45).map { m ->
        val km = (d.speedKmh * m / 60.0).coerceAtLeast(1.0)
        val (lat, lon) = pointAhead(d.lat, d.lon, d.headingDeg, km)
        Ahead(m, km.roundToInt(), lat, lon)
    }

    fun headingWord(deg: Double) = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")[((deg + 22.5) / 45).toInt() % 8]

    /** What the driver reads for one point ahead. */
    fun describe(mmPerHour: Double?, chance: Int?): String = when {
        mmPerHour == null -> "暫無資料"
        mmPerHour >= 15 -> "大雨，請減速、開大燈"
        mmPerHour >= 4 -> "明顯降雨，保持車距"
        mmPerHour >= .8 -> "小雨，路面濕滑"
        (chance ?: 0) >= 50 -> "可能飄雨"
        else -> "無雨"
    }
}
