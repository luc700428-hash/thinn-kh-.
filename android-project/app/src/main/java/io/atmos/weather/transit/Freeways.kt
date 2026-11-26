package io.atmos.weather.transit

import android.content.Context
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 國道與快速公路 for 駕車模式 (assets/roads/freeways.txt, built by tools/build_freeway_asset.py from
 * OpenStreetMap). Each carriageway is a directed chain, so matching the car to the chain that runs
 * its way also gives the direction; interchanges, service areas and rest stops sit along the
 * chains at a distance in metres, which makes "下個交流道 2.4 公里" a subtraction.
 */
object Freeways {
    class Chain(val id: Int, val road: String, val next: Int, val lat: DoubleArray, val lon: DoubleArray) {
        val cum = DoubleArray(lat.size).also { c -> for (i in 1 until lat.size) c[i] = c[i - 1] + metres(lat[i - 1], lon[i - 1], lat[i], lon[i]) }
        val length get() = cum.last()
        val minLat = lat.min() - .004; val maxLat = lat.max() + .004; val minLon = lon.min() - .004; val maxLon = lon.max() + .004
        val places = ArrayList<Place>()
        /** (metres along, km/h) where the posted limit changes. */
        val limits = ArrayList<Pair<Double, Int>>()
        fun limitAt(at: Double): Int? = limits.lastOrNull { it.first <= at + 1 }?.second
    }
    enum class Kind { INTERCHANGE, SERVICE, REST, END }
    data class Place(val chain: Int, val at: Double, val kind: Kind, val name: String, val exit: String, val towards: String)
    class Network(val chains: List<Chain>)
    /** Where the car is: on [chain], [at] metres along it, [off] metres beside the line. */
    data class Spot(val chain: Chain, val at: Double, val off: Double)
    data class Ahead(val place: Place, val metres: Double)
    data class Status(val road: String, val direction: String, val exit: Ahead?, val then: Ahead?, val service: Ahead?, val rest: Ahead?, val end: Ahead?)

    fun metres(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dy = (bLat - aLat) * 111_195.0
        val dx = (bLon - aLon) * 111_195.0 * cos(Math.toRadians((aLat + bLat) / 2))
        return hypot(dx, dy)
    }

    fun parse(text: String): Network {
        val chains = ArrayList<Chain>()
        val pending = ArrayList<List<String>>()
        text.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val p = line.split('|')
            when (p[0]) {
                "C" -> runCatching {
                    val n = p[4].toInt(); val la = DoubleArray(n); val lo = DoubleArray(n)
                    var a = 0L; var b = 0L
                    p[5].split(';').forEachIndexed { i, d -> val c = d.indexOf(','); a += d.substring(0, c).toLong(); b += d.substring(c + 1).toLong(); la[i] = a / 1e5; lo[i] = b / 1e5 }
                    if (n >= 2) chains += Chain(p[1].toInt(), p[2], p[3].toInt(), la, lo)
                }
                "I", "L" -> pending += p
            }
        }
        val byId = chains.associateBy { it.id }
        pending.forEach { p ->
            if (p[0] == "L") { runCatching { byId[p[1].toInt()]?.limits?.add(p[2].toDouble() to p[3].toInt()) }; return@forEach }
            runCatching {
                val kind = when (p[3]) { "S" -> Kind.SERVICE; "R" -> Kind.REST; "E" -> Kind.END; else -> Kind.INTERCHANGE }
                byId[p[1].toInt()]?.places?.add(Place(p[1].toInt(), p[2].toDouble(), kind, p[4], p.getOrElse(5) { "" }, p.getOrElse(6) { "" }))
            }
        }
        chains.forEach { c -> c.places.sortBy { it.at }; c.limits.sortBy { it.first } }
        return Network(chains.sortedBy { it.id })
    }

    @Volatile private var cached: Network? = null
    fun load(ctx: Context): Network? = cached ?: runCatching {
        parse(ctx.assets.open("roads/freeways.txt").bufferedReader().use { it.readText() })
    }.getOrNull()?.also { cached = it }

    private fun turn(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)

    /**
     * The chain the car is on: within 45 m of its line (GPS on a viaduct wanders) and running
     * within 50° of the car's heading, so the other carriageway and crossing roads never match.
     */
    fun match(net: Network, lat: Double, lon: Double, heading: Double, within: Double = 45.0): Spot? {
        var best: Spot? = null
        for (c in net.chains) {
            if (lat < c.minLat || lat > c.maxLat || lon < c.minLon || lon > c.maxLon) continue
            val kx = 111_195.0 * cos(Math.toRadians(lat)); val ky = 111_195.0
            for (i in 0 until c.lat.size - 1) {
                val ax = (c.lon[i] - lon) * kx; val ay = (c.lat[i] - lat) * ky
                val bx = (c.lon[i + 1] - lon) * kx; val by = (c.lat[i + 1] - lat) * ky
                val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
                if (l2 <= 0.0) continue
                val t = (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
                val d = hypot(ax + t * dx, ay + t * dy)
                if (d > within || (best != null && d >= best.off)) continue
                val segHeading = DrivingMode.bearing(c.lat[i], c.lon[i], c.lat[i + 1], c.lon[i + 1])
                if (turn(segHeading, heading) > 50) continue
                best = Spot(c, c.cum[i] + t * (c.cum[i + 1] - c.cum[i]), d)
            }
        }
        return best
    }

    /** Places ahead along this chain and the ones it runs into, nearest first (up to [limit] metres). */
    fun ahead(net: Network, spot: Spot, limit: Double = 150_000.0): List<Ahead> {
        val byId = net.chains.associateBy { it.id }
        val out = ArrayList<Ahead>(); val seen = HashSet<Int>()
        var chain: Chain? = spot.chain; var base = -spot.at
        while (chain != null && seen.add(chain.id) && base < limit) {
            chain.places.forEach { p -> val m = base + p.at; if (m > 30 && m <= limit) out += Ahead(p, m) }
            base += chain.length
            chain = byId[chain.next]
        }
        return out
    }

    /** The point [metres] further along the road (following on into the next chain), or null past its end. */
    fun pointAlong(net: Network, spot: Spot, metres: Double): Pair<Double, Double>? {
        val byId = net.chains.associateBy { it.id }
        var chain: Chain = spot.chain; var at = spot.at + metres; val seen = HashSet<Int>()
        while (at > chain.length) {
            if (!seen.add(chain.id)) return null
            at -= chain.length; chain = byId[chain.next] ?: return null
        }
        var i = 1
        while (i < chain.cum.size - 1 && chain.cum[i] < at) i++
        val seg = (chain.cum[i] - chain.cum[i - 1]).coerceAtLeast(1e-6)
        val t = ((at - chain.cum[i - 1]) / seg).coerceIn(0.0, 1.0)
        return (chain.lat[i - 1] + (chain.lat[i] - chain.lat[i - 1]) * t) to (chain.lon[i - 1] + (chain.lon[i] - chain.lon[i - 1]) * t)
    }

    /** 北上 / 南下 / 東向 / 西向 over the next 3 km, the way the road signs put it. */
    fun direction(net: Network, spot: Spot): String {
        val a = pointAlong(net, spot, 0.0) ?: return ""
        val b = pointAlong(net, spot, 3000.0) ?: pointAlong(net, spot, 600.0) ?: return ""
        val deg = DrivingMode.bearing(a.first, a.second, b.first, b.second)
        val ns = abs(cos(Math.toRadians(deg))); val ew = abs(kotlin.math.sin(Math.toRadians(deg)))
        // National freeways with odd numbers run north–south, even ones east–west.
        val number = Regex("""^國道(\d+)號$""").find(spot.chain.road)?.groupValues?.get(1)?.toIntOrNull()
        val northSouth = number?.let { it % 2 == 1 } ?: (ns >= ew)
        return if (northSouth) { if (cos(Math.toRadians(deg)) >= 0) "北上" else "南下" } else { if (kotlin.math.sin(Math.toRadians(deg)) >= 0) "東向" else "西向" }
    }

    fun status(net: Network, spot: Spot): Status {
        val ahead = ahead(net, spot)
        val exits = ahead.filter { it.place.kind == Kind.INTERCHANGE }
        return Status(spot.chain.road, direction(net, spot), exits.getOrNull(0), exits.getOrNull(1),
            ahead.firstOrNull { it.place.kind == Kind.SERVICE }, ahead.firstOrNull { it.place.kind == Kind.REST },
            ahead.firstOrNull { it.place.kind == Kind.END })
    }

    /** "800 公尺" below 1 km, then "2.4 公里", and whole kilometres from 20 km. */
    fun distance(m: Double): String = when {
        m < 1000 -> "${((m / 50).roundToInt() * 50).coerceAtLeast(50)} 公尺"
        m < 20_000 -> String.format(java.util.Locale.US, "%.1f 公里", m / 1000)
        else -> "${(m / 1000).roundToInt()} 公里"
    }

    /** "國道1號" → "國1", "台61線" → "台61" for tight spots. */
    fun short(road: String) = road.replace("國道", "國").replace("號甲線", "甲").removeSuffix("號").removeSuffix("線")
}
