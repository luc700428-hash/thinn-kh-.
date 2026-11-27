package io.atmos.weather.sky

import android.content.Context
import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 星空與銀河 — the sky's contents and the astronomy that places them. Everything is computed on the
 * phone (no network): about 5,000 stars down to magnitude 6, the 88 constellations' stick figures and
 * names, the Milky Way sampled on an even grid, the planets from their orbital elements, and the Sun
 * and Moon from the same library the 日月與月相 page uses.
 *
 * Directions are unit vectors. The catalogue stays in J2000 equatorial coordinates; one 3×3 matrix
 * per frame ([SkyFrame]) carries every star to the observer's horizon (east, north, up).
 */
object StarSky {
    class Star(val x: Float, val y: Float, val z: Float, val mag: Float, val bv: Float, val name: String, val constellation: String)
    class Constellation(val id: String, val name: String, val x: Float, val y: Float, val z: Float, val rank: Int)
    /** One stick figure: a flat list of segment end points, x1 y1 z1 x2 y2 z2 … */
    class Figure(val id: String, val segments: FloatArray)
    class MilkyWay(val level: ByteArray, val xyz: FloatArray)
    class Catalog(val stars: List<Star>, val constellations: List<Constellation>, val figures: List<Figure>, val milkyWay: MilkyWay) {
        val byId = constellations.associateBy { it.id }
    }

    @Volatile private var cached: Catalog? = null

    /** Reads the sky data from the app's assets (once; about 15 ms on a phone). */
    fun catalog(ctx: Context): Catalog = cached ?: synchronized(this) {
        cached ?: load { name -> ctx.assets.open("sky/$name").bufferedReader().readLines() }.also { cached = it }
    }

    fun load(read: (String) -> List<String>): Catalog {
        val stars = read("stars.tsv").mapNotNull { line ->
            val f = line.split('\t'); if (f.size < 4) return@mapNotNull null
            val v = vec(f[0].toDouble(), f[1].toDouble())
            Star(v[0], v[1], v[2], f[2].toFloat(), f[3].toFloat(), f.getOrElse(4) { "" }, f.getOrElse(5) { "" })
        }
        val cons = read("constellations.tsv").mapNotNull { line ->
            val f = line.split('\t'); if (f.size < 4) return@mapNotNull null
            val v = vec(f[2].toDouble(), f[3].toDouble())
            Constellation(f[0], f[1], v[0], v[1], v[2], f.getOrElse(4) { "3" }.toIntOrNull() ?: 3)
        }
        val figures = read("lines.tsv").mapNotNull { line ->
            val f = line.split('\t'); if (f.size < 2) return@mapNotNull null
            val out = ArrayList<Float>()
            f[1].split('|').forEach { seg ->
                val pts = seg.split(';').map { p -> p.split(',').let { vec(it[0].toDouble(), it[1].toDouble()) } }
                for (i in 0 until pts.size - 1) { out.addAll(pts[i].toList()); out.addAll(pts[i + 1].toList()) }
            }
            Figure(f[0], out.toFloatArray())
        }
        val mw = read("milkyway.tsv").mapNotNull { line ->
            val f = line.split('\t'); if (f.size < 3) null else f[0].toInt() to vec(f[1].toDouble(), f[2].toDouble())
        }
        val xyz = FloatArray(mw.size * 3); val level = ByteArray(mw.size)
        mw.forEachIndexed { i, (l, v) -> level[i] = l.toByte(); xyz[i * 3] = v[0]; xyz[i * 3 + 1] = v[1]; xyz[i * 3 + 2] = v[2] }
        return Catalog(stars, cons, figures, MilkyWay(level, xyz))
    }

    /** Equatorial unit vector for right ascension and declination in degrees. */
    fun vec(raDeg: Double, decDeg: Double): FloatArray {
        val a = Math.toRadians(raDeg); val d = Math.toRadians(decDeg)
        return floatArrayOf((cos(d) * cos(a)).toFloat(), (cos(d) * sin(a)).toFloat(), sin(d).toFloat())
    }

    fun julianDay(at: Instant): Double = at.toEpochMilli() / 86_400_000.0 + 2440587.5

    /** Local sidereal time in degrees (east longitude positive). */
    fun localSiderealDeg(at: Instant, lonDeg: Double): Double {
        val jd = julianDay(at); val t = (jd - 2451545.0) / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t
        return norm360(gmst + lonDeg)
    }

    fun norm360(d: Double) = d - 360.0 * floor(d / 360.0)

    /** J2000 → equator and equinox of [at] (IAU 1976 precession), as a row-major 3×3 matrix. */
    fun precession(at: Instant): DoubleArray {
        val t = (julianDay(at) - 2451545.0) / 36525.0
        val sec = PI / 180 / 3600
        val zeta = (2306.2181 * t + 0.30188 * t * t) * sec
        val z = (2306.2181 * t + 1.09468 * t * t) * sec
        val th = (2004.3109 * t - 0.42665 * t * t) * sec
        val cz = cos(zeta); val sz = sin(zeta); val cZ = cos(z); val sZ = sin(z); val ct = cos(th); val st = sin(th)
        return doubleArrayOf(
            cZ * ct * cz - sZ * sz, -cZ * ct * sz - sZ * cz, -cZ * st,
            sZ * ct * cz + cZ * sz, -sZ * ct * sz + cZ * cz, -sZ * st,
            st * cz, -st * sz, ct)
    }

    /** Equator of date → horizon (east, north, up) for local sidereal time and latitude. */
    fun horizon(lstDeg: Double, latDeg: Double): DoubleArray {
        val l = Math.toRadians(lstDeg); val p = Math.toRadians(latDeg)
        val cl = cos(l); val sl = sin(l); val cp = cos(p); val sp = sin(p)
        return doubleArrayOf(
            -sl, cl, 0.0,
            -sp * cl, -sp * sl, cp,
            cp * cl, cp * sl, sp)
    }

    fun mul(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(9) { i ->
        val r = i / 3; val c = i % 3
        a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
    }

    /** J2000 equatorial → the observer's horizon at [at]. */
    fun skyToHorizon(at: Instant, latDeg: Double, lonDeg: Double): DoubleArray =
        mul(horizon(localSiderealDeg(at, lonDeg), latDeg), precession(at))

    fun apply(m: DoubleArray, x: Double, y: Double, z: Double) = doubleArrayOf(
        m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z)

    /** Azimuth (from north through east) and altitude, in degrees, of a horizon vector. */
    fun azAlt(e: Double, n: Double, u: Double): Pair<Double, Double> =
        norm360(Math.toDegrees(atan2(e, n))) to Math.toDegrees(asin(u.coerceIn(-1.0, 1.0)))

    fun fromAzAlt(azDeg: Double, altDeg: Double): DoubleArray {
        val a = Math.toRadians(azDeg); val h = Math.toRadians(altDeg)
        return doubleArrayOf(cos(h) * sin(a), cos(h) * cos(a), sin(h))
    }

    // ------------------------------------------------------------------ planets

    enum class Planet(val label: String, val color: Int, val el: DoubleArray) {
        // a, e, I, L, ϖ, Ω and their rates per Julian century (Standish, JPL, valid 1800–2050).
        MERCURY("水星", 0xFFC9BBA8.toInt(), doubleArrayOf(0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
            0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081)),
        VENUS("金星", 0xFFFFF1C9.toInt(), doubleArrayOf(0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
            0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418)),
        MARS("火星", 0xFFFF8A5C.toInt(), doubleArrayOf(1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
            0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343)),
        JUPITER("木星", 0xFFF3D6A8.toInt(), doubleArrayOf(5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
            -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106)),
        SATURN("土星", 0xFFF0D79A.toInt(), doubleArrayOf(9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
            -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794)),
        URANUS("天王星", 0xFFA8E6EA.toInt(), doubleArrayOf(19.18916464, 0.04725744, 0.77263783, 313.23810451, 170.95427630, 74.01692503,
            -0.00196176, -0.00004397, -0.00242939, 428.48202785, 0.40805281, 0.04240589)),
        NEPTUNE("海王星", 0xFF7FA6FF.toInt(), doubleArrayOf(30.06992276, 0.00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574,
            0.00026291, 0.00005105, 0.00035372, 218.45945325, -0.32241464, -0.00508664));
    }

    private val EARTH = doubleArrayOf(1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
        0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0)

    /** Heliocentric ecliptic J2000 position (au). */
    private fun helio(el: DoubleArray, t: Double): DoubleArray {
        val a = el[0] + el[6] * t; val e = el[1] + el[7] * t; val i = Math.toRadians(el[2] + el[8] * t)
        val l = el[3] + el[9] * t; val peri = el[4] + el[10] * t; val node = el[5] + el[11] * t
        val w = Math.toRadians(peri - node); val o = Math.toRadians(node)
        var m = Math.toRadians(norm360(l - peri)); if (m > PI) m -= 2 * PI
        var ecc = m + e * sin(m)
        repeat(8) { ecc -= (ecc - e * sin(ecc) - m) / (1 - e * cos(ecc)) }
        val xp = a * (cos(ecc) - e); val yp = a * sqrt(1 - e * e) * sin(ecc)
        val cw = cos(w); val sw = sin(w); val co = cos(o); val so = sin(o); val ci = cos(i); val si = sin(i)
        return doubleArrayOf(
            (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp,
            (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp,
            (sw * si) * xp + (cw * si) * yp)
    }

    /** Geocentric J2000 equatorial unit vector and distance (au) of a planet. */
    fun planet(p: Planet, at: Instant): Pair<DoubleArray, Double> {
        val t = (julianDay(at) - 2451545.0) / 36525.0
        val b = helio(p.el, t); val e = helio(EARTH, t)
        val x = b[0] - e[0]; val y = b[1] - e[1]; val z = b[2] - e[2]
        val eps = Math.toRadians(23.43928)
        val ex = x; val ey = y * cos(eps) - z * sin(eps); val ez = y * sin(eps) + z * cos(eps)
        val r = sqrt(ex * ex + ey * ey + ez * ez)
        return doubleArrayOf(ex / r, ey / r, ez / r) to r
    }

    /** Right ascension and declination (degrees, J2000) of a J2000 unit vector. */
    fun raDec(v: DoubleArray): Pair<Double, Double> = norm360(Math.toDegrees(atan2(v[1], v[0]))) to Math.toDegrees(asin(v[2].coerceIn(-1.0, 1.0)))

    /** Direction names in Chinese for an azimuth. */
    fun compass(az: Double): String = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")[((norm360(az) + 22.5) / 45).toInt() % 8]
}
