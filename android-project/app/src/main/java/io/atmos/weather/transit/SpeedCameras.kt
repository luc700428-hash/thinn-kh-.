package io.atmos.weather.transit

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 測速與科技執法 for 駕車模式.
 *
 * Fixed speed cameras: 內政部警政署「測速執法設置點」(1,900 points with the direction they watch and
 * the limit), built in and re-downloaded weekly. Red-light cameras and 區間測速 zones: 國道公路警察局
 * and OpenStreetMap. Mobile cameras and patrol spots have no public data in Taiwan, so those are
 * the places the driver marks (kept on the phone only).
 *
 * Precision comes from three checks: the camera must watch the car's direction of travel, it must
 * lie on the line ahead (within ~40 m sideways, so a parallel street or the other carriageway does
 * not count), and on 國道/快速公路 the distance is measured along the road itself.
 */
object SpeedCameras {
    enum class Kind(val label: String) {
        SPEED("固定測速"), ZONE("區間測速"), RED_LIGHT("闖紅燈照相"), MOBILE("流動測速"), POLICE("警車常出沒")
    }

    /** [dirs]: travel bearings the camera watches, null for both ways. */
    data class Camera(val kind: Kind, val lat: Double, val lon: Double, val dirs: IntArray?, val limit: Int?, val label: String,
                      val zone: Zone? = null, val alsoRedLight: Boolean = false, val markedAt: Long = 0L) {
        override fun equals(other: Any?) = other is Camera && other.kind == kind && other.lat == lat && other.lon == lon && other.label == label
        override fun hashCode() = (lat * 1e6).toInt() * 31 + (lon * 1e6).toInt() + kind.ordinal
    }
    data class Zone(val id: Int, val limit: Int, val name: String, val fromLat: Double, val fromLon: Double, val toLat: Double, val toLon: Double)

    // ---------------------------------------------------------------- parsing

    private val compass = listOf("東北" to 45, "東南" to 135, "西北" to 315, "西南" to 225, "北" to 0, "南" to 180, "東" to 90, "西" to 270)
    private fun compassAt(t: String, i: Int): Pair<Int, Int>? = compass.firstOrNull { t.startsWith(it.first, i) }?.let { it.second to it.first.length }

    /**
     * The travel bearing a camera watches from the source's wording: 北向南 / 北往南 → 180,
     * 往南 / 南向 / 南下 → 180, 北上 → 0, 西南向東北 → 45; 雙向, 南北雙向, 往某地方向 → both ways.
     */
    fun direction(raw: String): IntArray? {
        val t = raw.replace(" ", "").replace("　", "").substringBefore("(").substringBefore("（")
        // Two limits for two directions ("南向60北向70") or an unclear wording: watch both ways.
        if (t.isEmpty() || listOf("雙向", "相向", "多向", "單向").any { it in t } || t.any { it.isDigit() }) return null
        if (t.contains("北上")) return intArrayOf(0)
        if (t.contains("南下")) return intArrayOf(180)
        // Every compass word in order.
        val found = ArrayList<Pair<Int, Int>>() // (bearing, position)
        var i = 0
        while (i < t.length) { val c = compassAt(t, i); if (c != null) { found += c.first to i; i += c.second } else i++ }
        if (found.isEmpty()) return null
        if (found.size == 2) {
            val (a, ia) = found[0]; val (b, ib) = found[1]
            val between = t.substring(ia, ib)
            // "南北" / "東西" / "南北向" side by side name an axis, not a direction.
            if (!between.contains("向") && !between.contains("往") && !between.contains("至")) return null
            if (abs(((a - b + 540) % 360) - 180) < 90) return null
            return intArrayOf(b)
        }
        if (found.size == 1) return intArrayOf(found[0].first)
        return null
    }

    /** Rows of 測速執法設置點 (the file as the source publishes it). */
    fun parseMoi(text: String): List<Camera> {
        val out = ArrayList<Camera>()
        text.removePrefix("\uFEFF").lineSequence().drop(1).forEach { line ->
            val f = csv(line)
            if (f.size < 9) return@forEach
            val lon = f[5].trim().toDoubleOrNull() ?: return@forEach; val lat = f[6].trim().toDoubleOrNull() ?: return@forEach
            if (lat !in 21.5..26.5 || lon !in 118.0..122.5) return@forEach
            val raw = f[7]
            val zone = raw.contains("區間")
            out += Camera(if (zone) Kind.ZONE else Kind.SPEED, lat, lon, direction(raw), f[8].trim().toIntOrNull()?.takeIf { it in 10..130 },
                f[2].trim().ifBlank { f[1] }, alsoRedLight = raw.contains("闖紅燈"))
        }
        return out
    }

    fun parseExtra(text: String): List<Camera> {
        val out = ArrayList<Camera>()
        text.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val p = line.split('|')
            runCatching {
                when (p[0]) {
                    "R" -> out += Camera(Kind.RED_LIGHT, p[1].toDouble(), p[2].toDouble(), p[3].toIntOrNull()?.let { intArrayOf(it) }, null, p[4])
                    "Z" -> {
                        val z = Zone(p[1].toInt(), p[2].toInt(), p[3], p[4].toDouble(), p[5].toDouble(), p[6].toDouble(), p[7].toDouble())
                        // Announced at its start, for traffic heading into the zone.
                        val dir = DrivingMode.bearing(z.fromLat, z.fromLon, z.toLat, z.toLon).roundToInt()
                        out += Camera(Kind.ZONE, z.fromLat, z.fromLon, intArrayOf(dir), z.limit, z.name, zone = z)
                    }
                }
            }
        }
        return out
    }

    private fun csv(line: String): List<String> {
        val out = ArrayList<String>(); val b = StringBuilder(); var q = false
        for (c in line) when {
            c == '"' -> q = !q
            c == ',' && !q -> { out += b.toString(); b.clear() }
            else -> b.append(c)
        }
        out += b.toString(); return out
    }

    // ---------------------------------------------------------------- personal marks

    private const val MARKS = "thinn_drive_marks"
    fun marks(ctx: Context): List<Camera> {
        val now = System.currentTimeMillis()
        return ctx.getSharedPreferences(MARKS, Context.MODE_PRIVATE).getStringSet("marks", emptySet()).orEmpty().mapNotNull { s ->
            val p = s.split('|')
            runCatching {
                val at = p[4].toLong()
                if (now - at > 120L * 86_400_000L) null
                else Camera(Kind.valueOf(p[0]), p[1].toDouble(), p[2].toDouble(), intArrayOf(p[3].toInt()), null, "你標記的${Kind.valueOf(p[0]).label}", markedAt = at)
            }.getOrNull()
        }
    }
    fun mark(ctx: Context, kind: Kind, lat: Double, lon: Double, heading: Double) {
        val prefs = ctx.getSharedPreferences(MARKS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet("marks", emptySet()).orEmpty().toMutableSet()
        set += "${kind.name}|${"%.6f".format(java.util.Locale.US, lat)}|${"%.6f".format(java.util.Locale.US, lon)}|${heading.roundToInt()}|${System.currentTimeMillis()}"
        prefs.edit().putStringSet("marks", set).apply()
        cached = null
    }
    fun unmark(ctx: Context, c: Camera) {
        val prefs = ctx.getSharedPreferences(MARKS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet("marks", emptySet()).orEmpty().filterNot { s -> s.split('|').let { it.size > 2 && it[1].toDoubleOrNull() == c.lat && it[2].toDoubleOrNull() == c.lon } }.toSet()
        prefs.edit().putStringSet("marks", set).apply()
        cached = null
    }

    // ---------------------------------------------------------------- index

    /** Cameras in 0.02° cells (about 2 km), plus their place along a freeway chain when they sit on one. */
    class Index(val all: List<Camera>, net: Freeways.Network?) {
        private val cells = HashMap<Long, MutableList<Camera>>()
        val onChain = HashMap<Camera, Freeways.Spot>()
        init {
            all.forEach { c -> cells.getOrPut(key(c.lat, c.lon)) { ArrayList() } += c }
            if (net != null) all.forEach { c ->
                val dirs = c.dirs ?: return@forEach
                dirs.asSequence().mapNotNull { d -> Freeways.match(net, c.lat, c.lon, d.toDouble(), within = 60.0) }.firstOrNull()?.let { onChain[c] = it }
            }
        }
        private fun key(lat: Double, lon: Double) = ((lat / .02).toLong() shl 32) + (lon / .02).toLong()
        fun near(lat: Double, lon: Double, metres: Double): List<Camera> {
            val r = (metres / 2000.0).toInt() + 1
            val ky = (lat / .02).toLong(); val kx = (lon / .02).toLong()
            val out = ArrayList<Camera>()
            for (dy in -r..r) for (dx in -r..r) cells[((ky + dy) shl 32) + (kx + dx)]?.let { out += it }
            return out
        }
    }

    @Volatile private var cached: Index? = null
    private const val MOI_URL = "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/EA5E6FCD-B82D-43B7-A5CF-E9893253187E/resource/1C3D4C5D-44D4-418C-8CD8-0971D4BE9BE2/download"

    fun load(ctx: Context): Index {
        cached?.let { return it }
        val fresh = File(ctx.filesDir, "roads/speed_cameras.csv")
        val moi = (if (fresh.isFile) runCatching { parseMoi(fresh.readText()) }.getOrNull()?.takeIf { it.size > 1000 } else null)
            ?: runCatching { parseMoi(ctx.assets.open("roads/speed_cameras.csv").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
        val extra = runCatching { parseExtra(ctx.assets.open("roads/enforcement_extra.txt").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
        return Index(moi + extra + marks(ctx), Freeways.load(ctx)).also { cached = it }
    }

    /** The newest official list, at most weekly; kept only when it looks complete. */
    suspend fun refresh(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val file = File(ctx.filesDir, "roads/speed_cameras.csv")
        if (file.isFile && System.currentTimeMillis() - file.lastModified() < 7L * 86_400_000L) return@withContext false
        runCatching {
            val c = (URL(MOI_URL).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 20_000 }
            val text = try { if (c.responseCode == 200) c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8) else null } finally { c.disconnect() }
            if (text == null || !text.removePrefix("\uFEFF").startsWith("CityName,") || parseMoi(text).size < 1000) return@runCatching false
            file.parentFile?.mkdirs(); file.writeText(text); cached = null; true
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------- what lies ahead

    data class Alert(val camera: Camera, val metres: Double)

    private fun turn(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)
    private fun watches(c: Camera, heading: Double) = c.dirs == null || c.dirs.any { turn(it.toDouble(), heading) <= 50 }

    /**
     * The nearest enforcement point ahead within [lookahead] metres. On a freeway chain, cameras
     * assigned to the road are measured along it (and only those count); elsewhere a camera must
     * be in front and within 40 m (plus 8% of the distance, for gentle bends) of the line of travel.
     */
    fun ahead(index: Index, net: Freeways.Network?, spot: Freeways.Spot?, lat: Double, lon: Double, heading: Double, lookahead: Double): Alert? {
        if (spot != null && net != null) {
            val byId = net.chains.associateBy { it.id }
            var best: Alert? = null
            index.onChain.forEach { (c, s) ->
                // Walk from the car's chain to the camera's, the way Freeways.ahead does.
                var chain: Freeways.Chain? = spot.chain; var base = -spot.at; val seen = HashSet<Int>()
                while (chain != null && seen.add(chain.id) && base < lookahead) {
                    if (chain.id == s.chain.id) { val m = base + s.at; if (m > 0 && m <= lookahead && (best == null || m < best!!.metres)) best = Alert(c, m); break }
                    base += chain.length; chain = byId[chain.next]
                }
            }
            // Ramp and toll cameras beside the road still count when right in front.
            return best ?: straight(index, lat, lon, heading, minOf(lookahead, 400.0), freeway = true)
        }
        return straight(index, lat, lon, heading, lookahead, freeway = false)
    }

    private fun straight(index: Index, lat: Double, lon: Double, heading: Double, lookahead: Double, freeway: Boolean): Alert? {
        var best: Alert? = null
        for (c in index.near(lat, lon, lookahead)) {
            if (freeway && index.onChain.containsKey(c)) continue
            if (!watches(c, heading)) continue
            val d = Freeways.metres(lat, lon, c.lat, c.lon)
            if (d > lookahead || d < 15) continue
            val rel = Math.toRadians(turn(DrivingMode.bearing(lat, lon, c.lat, c.lon), heading))
            val along = d * cos(rel); val side = abs(d * sin(rel))
            if (along <= 0 || side > 40 + along * .08) continue
            if (best == null || along < best.metres) best = Alert(c, along)
        }
        return best
    }

    /** How far ahead to warn: about 30 seconds at this speed, 300 m to 1.5 km. */
    fun lookahead(speedKmh: Int) = (speedKmh / 3.6 * 30).coerceIn(300.0, 1500.0)

    /** Average-speed zone in progress: distance and time since its start. */
    data class ZoneRun(val zone: Zone, val startMs: Long, val metres: Double, val lastLat: Double, val lastLon: Double) {
        fun averageKmh(nowMs: Long): Int? = ((nowMs - startMs) / 1000.0).takeIf { it >= 5 }?.let { (metres / it * 3.6).roundToInt() }
    }

    /** Starts a zone when the car passes its start, follows it, ends it at the far end (or 2 km past it). */
    fun followZone(run: ZoneRun?, index: Index, lat: Double, lon: Double, nowMs: Long): ZoneRun? {
        if (run != null) {
            val moved = Freeways.metres(run.lastLat, run.lastLon, lat, lon)
            val next = run.copy(metres = run.metres + moved, lastLat = lat, lastLon = lon)
            val toEnd = Freeways.metres(lat, lon, run.zone.toLat, run.zone.toLon)
            val span = Freeways.metres(run.zone.fromLat, run.zone.fromLon, run.zone.toLat, run.zone.toLon)
            return if (toEnd < 50 || toEnd > span + 2000 || nowMs - run.startMs > 45 * 60_000L) null else next
        }
        val start = index.near(lat, lon, 200.0).firstOrNull { it.zone != null && Freeways.metres(lat, lon, it.lat, it.lon) < 45 } ?: return null
        return ZoneRun(start.zone!!, nowMs, 0.0, lat, lon)
    }

    /** "前方 400 公尺，固定測速，速限 60" for the voice. */
    fun speech(a: Alert): String {
        val m = ((a.metres / 100).roundToInt() * 100).coerceAtLeast(100)
        return "前方 $m 公尺，${a.camera.kind.label}" + (a.camera.limit?.let { "，速限 $it" } ?: "") + if (a.camera.alsoRedLight) "，兼闖紅燈照相" else ""
    }
}
