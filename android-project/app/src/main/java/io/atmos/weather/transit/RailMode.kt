package io.atmos.weather.transit

import kotlin.math.*

/**
 * 高鐵模式 / 火車模式. From the phone's own position and speed the app judges whether the person is
 * riding Taiwan High Speed Rail or a TRA train, which way, and which station comes next, then shows
 * what matters on board: the next stop, about when, and the weather waiting there and at the end of
 * the line. Everything here is plain geometry so the judgement can be tested.
 */
object RailMode {
    enum class Line(val label: String) { HSR("高鐵"), TRA("火車") }
    data class Station(val name: String, val lat: Double, val lon: Double)
    data class Sample(val lat: Double, val lon: Double, val speedMs: Float, val timeMs: Long)
    data class Trip(
        val line: Line,
        val confident: Boolean,
        val speedKmh: Int,
        val next: Station,
        val nextMinutes: Int,
        val terminal: Station,
        val terminalMinutes: Int,
        val towardLabel: String,
    )

    /** THSR, north to south. */
    val hsr = listOf(
        Station("南港", 25.0532, 121.6070), Station("台北", 25.0478, 121.5170), Station("板橋", 25.0136, 121.4627),
        Station("桃園", 25.0130, 121.2149), Station("新竹", 24.8081, 121.0405), Station("苗栗", 24.6053, 120.8253),
        Station("台中", 24.1124, 120.6158), Station("彰化", 23.8740, 120.5745), Station("雲林", 23.7363, 120.4163),
        Station("嘉義", 23.4593, 120.3238), Station("台南", 22.9250, 120.2856), Station("左營", 22.6872, 120.3075),
    )

    /** TRA main stations along the western line (north to south) and the eastern line (north to south). */
    val traWest = listOf(
        Station("基隆", 25.1319, 121.7389), Station("七堵", 25.0930, 121.7140), Station("松山", 25.0493, 121.5778),
        Station("台北", 25.0478, 121.5170), Station("萬華", 25.0333, 121.5000), Station("板橋", 25.0136, 121.4627),
        Station("樹林", 24.9913, 121.4248), Station("桃園", 24.9890, 121.3140), Station("中壢", 24.9536, 121.2256),
        Station("新竹", 24.8016, 120.9716), Station("竹南", 24.6866, 120.8806), Station("苗栗", 24.5700, 120.8227),
        Station("豐原", 24.2540, 120.7230), Station("台中", 24.1369, 120.6855), Station("彰化", 24.0817, 120.5383),
        Station("員林", 23.9590, 120.5700), Station("斗六", 23.7118, 120.5413), Station("嘉義", 23.4791, 120.4413),
        Station("新營", 23.3066, 120.3232), Station("台南", 22.9972, 120.2125), Station("新左營", 22.6873, 120.3075),
        Station("高雄", 22.6397, 120.3025), Station("屏東", 22.6694, 120.4865),
    )
    val traEast = listOf(
        Station("七堵", 25.0930, 121.7140), Station("瑞芳", 25.1088, 121.8060), Station("宜蘭", 24.7546, 121.7579),
        Station("羅東", 24.6774, 121.7745), Station("蘇澳新", 24.6087, 121.8279), Station("花蓮", 23.9932, 121.6011),
        Station("玉里", 23.3320, 121.3123), Station("台東", 22.7937, 121.1232),
    )

    fun km(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 12742 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Where a point lies along a line of stations: distance from the line, and km along it. */
    data class Projection(val offKm: Double, val alongKm: Double)
    fun project(line: List<Station>, lat: Double, lon: Double): Projection {
        var best = Projection(Double.MAX_VALUE, 0.0); var walked = 0.0
        val k = 111.2; val c = cos(Math.toRadians(lat))
        for (i in 0 until line.size - 1) {
            val a = line[i]; val b = line[i + 1]
            val ax = a.lon * k * c; val ay = a.lat * k; val bx = b.lon * k * c; val by = b.lat * k
            val px = lon * k * c; val py = lat * k
            val dx = bx - ax; val dy = by - ay; val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val qx = ax + dx * t; val qy = ay + dy * t
            val off = hypot(px - qx, py - qy); val seg = sqrt(len2)
            if (off < best.offKm) best = Projection(off, walked + seg * t)
            walked += seg
        }
        return best
    }
    private fun alongOf(line: List<Station>): DoubleArray {
        val out = DoubleArray(line.size); var acc = 0.0
        for (i in 1 until line.size) { acc += km(line[i - 1].lat, line[i - 1].lon, line[i].lat, line[i].lon); out[i] = acc }
        return out
    }

    /**
     * The judgement. Needs about a minute of fixes. HSR: sustained 140 km/h or more within 3 km of the
     * high-speed line. TRA: sustained 30–140 km/h within 2 km of a railway with the movement running
     * along it; a road can run beside the track, so TRA is marked unconfirmed until the person agrees.
     */
    fun judge(samples: List<Sample>): Trip? {
        val recent = samples.filter { it.speedMs.isFinite() && it.speedMs >= 0 }.sortedBy { it.timeMs }
        if (recent.size < 4 || recent.last().timeMs - recent.first().timeMs < 45_000L) return null
        val speeds = recent.takeLast(8).map { it.speedMs }.sorted()
        val median = speeds[speeds.size / 2]
        val kmh = median * 3.6f
        val last = recent.last(); val first = recent.first()
        fun trip(line: List<Station>, kind: Line, maxOff: Double, confident: Boolean): Trip? {
            val p0 = project(line, first.lat, first.lon); val p1 = project(line, last.lat, last.lon)
            if (p1.offKm > maxOff || p0.offKm > maxOff * 1.5) return null
            val moved = p1.alongKm - p0.alongKm
            val travelled = km(first.lat, first.lon, last.lat, last.lon)
            // Moving along the line, not across it.
            if (travelled < .3 || abs(moved) < travelled * .7) return null
            val south = moved > 0
            val along = alongOf(line)
            val nextIndex = if (south) along.indexOfFirst { it > p1.alongKm + .4 } else along.indexOfLast { it < p1.alongKm - .4 }
            if (nextIndex < 0) return null
            val terminal = if (south) line.last() else line.first()
            val ms = median.toDouble().coerceAtLeast(1.0)
            fun minutes(target: Double) = (abs(target - p1.alongKm) * 1000 / ms / 60).roundToInt().coerceAtLeast(1)
            return Trip(kind, confident, kmh.roundToInt(), line[nextIndex], minutes(along[nextIndex]), terminal,
                minutes(if (south) along.last() else 0.0), "往${terminal.name}")
        }
        if (kmh >= 140f) return trip(hsr, Line.HSR, 3.0, true)
        if (kmh in 30f..140f) return trip(traWest, Line.TRA, 2.0, false) ?: trip(traEast, Line.TRA, 2.0, false)
        return null
    }
}
