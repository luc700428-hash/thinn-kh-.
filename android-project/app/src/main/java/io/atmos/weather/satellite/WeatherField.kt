package io.atmos.weather.satellite

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The model field drawn over the satellite picture: sea-level pressure, 2 m temperature and 10 m
 * wind on a regular latitude/longitude grid around Taiwan, from Open-Meteo's current analysis.
 * Everything here is plain arithmetic so isobars, isotherms and pressure centres can be tested.
 */
internal class WeatherGrid(
    val lats: DoubleArray,            // south → north
    val lons: DoubleArray,            // west → east
    val pressure: Array<FloatArray>,  // [row][col] hPa, NaN where missing
    val temperature: Array<FloatArray>,
    val windKnots: Array<FloatArray>,
    val windFrom: Array<FloatArray>,  // degrees, meteorological (direction the wind comes from)
    val time: Instant?,
) {
    val rows get() = lats.size
    val cols get() = lons.size
}

internal data class ContourSet(val level: Float, val segments: FloatArray) // lon,lat,lon,lat per segment
internal class ForecastStep(val grid: WeatherGrid, val cloud: Array<FloatArray>, val rain: Array<FloatArray>)
internal data class PressureCenter(val high: Boolean, val lat: Double, val lon: Double, val hPa: Float)

internal object WeatherField {
    const val SOUTH = 12.0
    const val NORTH = 38.0
    const val WEST = 106.0
    const val EAST = 138.0
    const val STEP = 2.0

    val lats = DoubleArray(((NORTH - SOUTH) / STEP).toInt() + 1) { SOUTH + it * STEP }
    val lons = DoubleArray(((EAST - WEST) / STEP).toInt() + 1) { WEST + it * STEP }

    fun url(): String {
        val la = StringBuilder(); val lo = StringBuilder()
        for (lat in lats) for (lon in lons) {
            if (la.isNotEmpty()) { la.append(','); lo.append(',') }
            la.append(lat.toInt()); lo.append(lon.toInt())
        }
        return "https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo" +
            "&current=pressure_msl,temperature_2m,wind_speed_10m,wind_direction_10m" +
            "&wind_speed_unit=kn&timezone=GMT"
    }

    /** One object per point, in the same order as [url] lists them (row by row, south first). */
    fun parse(json: String): WeatherGrid {
        val trimmed = json.trim()
        val items: List<JSONObject> = if (trimmed.startsWith("[")) JSONArray(trimmed).let { a -> List(a.length()) { a.getJSONObject(it) } }
            else listOf(JSONObject(trimmed))
        val rows = lats.size; val cols = lons.size
        require(items.size == rows * cols) { "expected ${rows * cols} points, got ${items.size}" }
        fun grid() = Array(rows) { FloatArray(cols) { Float.NaN } }
        val p = grid(); val t = grid(); val w = grid(); val d = grid()
        var time: Instant? = null
        items.forEachIndexed { i, o ->
            val c = o.optJSONObject("current") ?: return@forEachIndexed
            val r = i / cols; val k = i % cols
            fun v(name: String, range: ClosedFloatingPointRange<Double>) =
                c.optDouble(name, Double.NaN).takeIf { it.isFinite() && it in range }?.toFloat() ?: Float.NaN
            p[r][k] = v("pressure_msl", 850.0..1090.0)
            t[r][k] = v("temperature_2m", -80.0..60.0)
            w[r][k] = v("wind_speed_10m", 0.0..250.0)
            d[r][k] = v("wind_direction_10m", 0.0..360.0)
            if (time == null) time = runCatching {
                LocalDateTime.parse(c.getString("time")).toInstant(ZoneOffset.UTC)
            }.getOrNull()
        }
        require(p.sumOf { row -> row.count { it.isFinite() } } >= rows * cols / 2) { "too little pressure data" }
        return WeatherGrid(lats, lons, p, t, w, d, time)
    }

    /** 11-day model forecast every 6 hours on the same grid (Open-Meteo, GFS/ECMWF blend). */
    fun forecastUrl(): String = url().substringBefore("&current=") +
        "&hourly=pressure_msl,temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,precipitation" +
        "&forecast_days=11&temporal_resolution=hourly_6&wind_speed_unit=kn&timezone=GMT"

    /** One grid per forecast time. Precipitation is the total of each 6-hour step (mm). */
    fun parseForecast(json: String): List<Pair<Instant, ForecastStep>> {
        val trimmed = json.trim()
        val items: List<JSONObject> = if (trimmed.startsWith("[")) JSONArray(trimmed).let { a -> List(a.length()) { a.getJSONObject(it) } }
            else listOf(JSONObject(trimmed))
        val rows = lats.size; val cols = lons.size
        require(items.size == rows * cols) { "expected ${rows * cols} points, got ${items.size}" }
        val times = items[0].getJSONObject("hourly").getJSONArray("time").let { a ->
            List(a.length()) { LocalDateTime.parse(a.getString(it)).toInstant(ZoneOffset.UTC) } }
        val n = times.size
        fun grid() = Array(n) { Array(rows) { FloatArray(cols) { Float.NaN } } }
        val p = grid(); val t = grid(); val w = grid(); val d = grid(); val cc = grid(); val pr = grid()
        items.forEachIndexed { i, o ->
            val h = o.optJSONObject("hourly") ?: return@forEachIndexed
            val r = i / cols; val k = i % cols
            fun fill(name: String, into: Array<Array<FloatArray>>, range: ClosedFloatingPointRange<Double>) {
                val a = h.optJSONArray(name) ?: return
                for (s in 0 until minOf(n, a.length())) {
                    val v = a.optDouble(s, Double.NaN)
                    if (v.isFinite() && v in range) into[s][r][k] = v.toFloat()
                }
            }
            fill("pressure_msl", p, 850.0..1090.0); fill("temperature_2m", t, -80.0..60.0)
            fill("wind_speed_10m", w, 0.0..250.0); fill("wind_direction_10m", d, 0.0..360.0)
            fill("cloud_cover", cc, 0.0..100.0); fill("precipitation", pr, 0.0..2000.0)
        }
        return times.indices.map { s ->
            times[s] to ForecastStep(WeatherGrid(lats, lons, p[s], t[s], w[s], d[s], times[s]), cc[s], pr[s])
        }.filter { (_, step) -> step.grid.pressure.sumOf { row -> row.count { it.isFinite() } } >= rows * cols / 2 }
    }

    /** Bilinear value of a grid at a point; NaN outside it. */
    fun sample(values: Array<FloatArray>, lat: Double, lon: Double): Float {
        val fr = (lat - SOUTH) / STEP; val fc = (lon - WEST) / STEP
        if (fr < 0 || fc < 0 || fr > lats.size - 1 || fc > lons.size - 1) return Float.NaN
        val r0 = floor(fr).toInt().coerceAtMost(lats.size - 2); val c0 = floor(fc).toInt().coerceAtMost(lons.size - 2)
        val tr = (fr - r0).toFloat(); val tc = (fc - c0).toFloat()
        val a = values[r0][c0]; val b = values[r0][c0 + 1]; val c = values[r0 + 1][c0]; val d = values[r0 + 1][c0 + 1]
        return (a * (1 - tc) + b * tc) * (1 - tr) + (c * (1 - tc) + d * tc) * tr
    }

    /** Catmull-Rom upsampling: smooth curves through the model points, no new extremes added. */
    fun refine(values: Array<FloatArray>, factor: Int): Array<FloatArray> {
        val rows = values.size; val cols = values[0].size
        val outR = (rows - 1) * factor + 1; val outC = (cols - 1) * factor + 1
        fun at(r: Int, c: Int): Float {
            val v = values[r.coerceIn(0, rows - 1)][c.coerceIn(0, cols - 1)]
            return if (v.isFinite()) v else values[r.coerceIn(0, rows - 1)].firstOrNull { it.isFinite() } ?: 0f
        }
        fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
            val raw = .5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t)
            return raw.coerceIn(min(p1, p2), max(p1, p2))
        }
        return Array(outR) { y ->
            val r = y / factor; val ty = (y % factor).toFloat() / factor
            FloatArray(outC) { x ->
                val c = x / factor; val tx = (x % factor).toFloat() / factor
                val col = FloatArray(4) { j ->
                    val rr = r - 1 + j
                    cr(at(rr, c - 1), at(rr, c), at(rr, c + 1), at(rr, c + 2), tx)
                }
                cr(col[0], col[1], col[2], col[3], ty)
            }
        }
    }

    /** Marching squares. [lats]/[lons] give the coordinates of each row/column of [values]. */
    fun contours(values: Array<FloatArray>, lats: DoubleArray, lons: DoubleArray, levels: List<Float>): List<ContourSet> {
        val rows = values.size; val cols = values[0].size
        require(rows == lats.size && cols == lons.size)
        return levels.map { level ->
            val out = ArrayList<Float>()
            fun edge(x0: Int, y0: Int, x1: Int, y1: Int) {
                val a = values[y0][x0]; val b = values[y1][x1]
                val t = if (b == a) .5f else ((level - a) / (b - a)).coerceIn(0f, 1f)
                out.add((lons[x0] + (lons[x1] - lons[x0]) * t).toFloat())
                out.add((lats[y0] + (lats[y1] - lats[y0]) * t).toFloat())
            }
            for (y in 0 until rows - 1) for (x in 0 until cols - 1) {
                val v0 = values[y][x]; val v1 = values[y][x + 1]; val v2 = values[y + 1][x + 1]; val v3 = values[y + 1][x]
                if (!(v0.isFinite() && v1.isFinite() && v2.isFinite() && v3.isFinite())) continue
                var case = 0
                if (v0 >= level) case = case or 1
                if (v1 >= level) case = case or 2
                if (v2 >= level) case = case or 4
                if (v3 >= level) case = case or 8
                if (case == 0 || case == 15) continue
                // Edges: bottom (0-1), right (1-2), top (3-2), left (0-3) in grid terms.
                fun bottom() = edge(x, y, x + 1, y)
                fun right() = edge(x + 1, y, x + 1, y + 1)
                fun top() = edge(x, y + 1, x + 1, y + 1)
                fun left() = edge(x, y, x, y + 1)
                val centre = (v0 + v1 + v2 + v3) / 4f
                when (case) {
                    1, 14 -> { left(); bottom() }
                    2, 13 -> { bottom(); right() }
                    3, 12 -> { left(); right() }
                    4, 11 -> { right(); top() }
                    6, 9 -> { bottom(); top() }
                    7, 8 -> { left(); top() }
                    5 -> if (centre >= level) { left(); top(); bottom(); right() } else { left(); bottom(); right(); top() }
                    10 -> if (centre >= level) { left(); bottom(); right(); top() } else { left(); top(); bottom(); right() }
                }
            }
            ContourSet(level, out.toFloatArray())
        }.filter { it.segments.isNotEmpty() }
    }

    fun levels(values: Array<FloatArray>, step: Float, anchor: Float = 0f): List<Float> {
        var lo = Float.POSITIVE_INFINITY; var hi = Float.NEGATIVE_INFINITY
        values.forEach { row -> row.forEach { if (it.isFinite()) { lo = min(lo, it); hi = max(hi, it) } } }
        if (!lo.isFinite()) return emptyList()
        val first = floor((lo - anchor) / step).toInt() + 1
        val last = floor((hi - anchor) / step).toInt()
        return (first..last).map { anchor + it * step }
    }

    /**
     * Highs and lows: a grid point that is the strict extreme of the 5×5 block around it and stands
     * at least [depth] hPa above (or below) the average of that block's outer ring.
     */
    fun centers(grid: WeatherGrid, depth: Float = 1.5f): List<PressureCenter> {
        val p = grid.pressure; val out = ArrayList<PressureCenter>()
        for (r in 2 until grid.rows - 2) for (c in 2 until grid.cols - 2) {
            val v = p[r][c]; if (!v.isFinite()) continue
            var high = true; var low = true; var ring = 0f; var n = 0; var complete = true
            for (dr in -2..2) for (dc in -2..2) {
                if (dr == 0 && dc == 0) continue
                val o = p[r + dr][c + dc]
                if (!o.isFinite()) { complete = false; continue }
                if (o >= v) high = false
                if (o <= v) low = false
                if (abs(dr) == 2 || abs(dc) == 2) { ring += o; n++ }
            }
            if (!complete || n == 0) continue
            val mean = ring / n
            if (high && v - mean >= depth) out += PressureCenter(true, grid.lats[r], grid.lons[c], v)
            if (low && mean - v >= depth) out += PressureCenter(false, grid.lats[r], grid.lons[c], v)
        }
        return out
    }

    /** Wind barb parts for a speed in knots, rounded to the nearest 5: flags (50), full (10), half (5). */
    data class Barb(val flags: Int, val full: Int, val half: Int, val calm: Boolean)
    fun barb(knots: Float): Barb {
        if (!knots.isFinite() || knots < 2.5f) return Barb(0, 0, 0, true)
        var rest = (Math.round(knots / 5f) * 5).coerceAtLeast(5)
        val flags = rest / 50; rest -= flags * 50
        val full = rest / 10; rest -= full * 10
        return Barb(flags, full, if (rest >= 5) 1 else 0, false)
    }
}
