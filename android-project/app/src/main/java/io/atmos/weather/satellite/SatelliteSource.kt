package io.atmos.weather.satellite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import io.atmos.weather.radar.RadarProjection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/**
 * Himawari-9 imagery (JMA) as served by NASA GIBS in Web Mercator tiles: public, keyless, every
 * 10 minutes, typically 30–60 minutes behind real time. Tiles are cached on disk for three hours.
 */
internal object SatelliteSource {
    enum class Channel(val label: String, val layer: String, val maxZoom: Int, val note: String) {
        INFRARED("紅外線", "Himawari_AHI_Band13_Clean_Infrared", 6, "向日葵 9 號 Band 13 紅外線 · 約 2 公里 · 日夜皆可看雲頂溫度"),
        VISIBLE("可見光", "Himawari_AHI_Band3_Red_Visible_1km", 7, "向日葵 9 號 Band 3 可見光 · 約 1 公里 · 僅白天"),
        AIR_MASS("氣團", "Himawari_AHI_Air_Mass", 6, "氣團 RGB：紅＝乾冷高空氣、綠＝熱帶暖濕、藍＝極地冷氣團"),
    }

    data class Tile(val z: Int, val x: Int, val y: Int)

    private const val HOST = "gibs.earthdata.nasa.gov"
    private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(java.time.ZoneOffset.UTC)

    fun domainsUrl(channel: Channel, now: Instant): String {
        val end = now.truncatedTo(ChronoUnit.MINUTES)
        val start = end.minus(Duration.ofHours(3))
        return "https://$HOST/wmts/epsg3857/best/1.0.0/${channel.layer}/default/" +
            "GoogleMapsCompatible_Level${channel.maxZoom}/all/${iso.format(start)}--${iso.format(end)}.xml"
    }

    fun tileUrl(channel: Channel, time: Instant, tile: Tile): String {
        require(tile.z in 0..channel.maxZoom && tile.x in 0 until (1 shl tile.z) && tile.y in 0 until (1 shl tile.z))
        return "https://$HOST/wmts/epsg3857/best/${channel.layer}/default/${iso.format(time)}/" +
            "GoogleMapsCompatible_Level${channel.maxZoom}/${tile.z}/${tile.y}/${tile.x}.png"
    }

    /** Times in the DescribeDomains answer ("a/b/PT10M" ranges or single times), newest last. */
    fun parseDomains(xml: String, now: Instant, keep: Int = 12): List<Instant> {
        val domain = Regex("<Domain>([^<]*)</Domain>").find(xml)?.groupValues?.get(1)?.trim().orEmpty()
        val times = sortedSetOf<Instant>()
        domain.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { item ->
            val parts = item.split('/')
            runCatching {
                if (parts.size == 3) {
                    val start = Instant.parse(parts[0]); val end = Instant.parse(parts[1])
                    val step = Duration.parse(parts[2]).takeIf { !it.isNegative && !it.isZero } ?: return@runCatching
                    var t = end; var n = 0
                    while (!t.isBefore(start) && n < 200) { times += t; t = t.minus(step); n++ }
                } else if (parts.size == 1) times += Instant.parse(parts[0])
            }
        }
        val earliest = now.minus(Duration.ofHours(3))
        return times.filter { !it.isAfter(now) && it.isAfter(earliest) }.takeLast(keep)
    }

    /** When the domain list is unavailable: the last [keep] 10-minute slots, 40 minutes back. */
    fun fallbackTimes(now: Instant, keep: Int = 12): List<Instant> {
        val back = now.minus(Duration.ofMinutes(40)).epochSecond
        val latest = Instant.ofEpochSecond(back - Math.floorMod(back, 600L))
        return (keep - 1 downTo 0).map { latest.minus(Duration.ofMinutes(10L * it)) }
    }

    /**
     * Tiles covering a lon/lat box at [pixelsPerDegree] (screen pixels per degree of longitude):
     * the coarsest zoom that is not stretched more than 1.25×, capped at the channel's detail and at
     * [budget] tiles.
     */
    fun tilesFor(west: Double, south: Double, east: Double, north: Double, pixelsPerDegree: Double,
                 maxZoom: Int, budget: Int = 30): List<Tile> {
        if (!(pixelsPerDegree > 0) || east <= west || north <= south) return emptyList()
        var z = ceil(ln(pixelsPerDegree * 360.0 / 320.0) / ln(2.0)).toInt().coerceIn(0, maxZoom)
        while (true) {
            val n = 1 shl z
            val x0 = floor(RadarProjection.x(west.coerceIn(-180.0, 179.999)) * n).toInt().coerceIn(0, n - 1)
            val x1 = floor(RadarProjection.x(east.coerceIn(-180.0, 179.999)) * n).toInt().coerceIn(0, n - 1)
            val y0 = floor(RadarProjection.y(north.coerceIn(-84.0, 84.0)) * n).toInt().coerceIn(0, n - 1)
            val y1 = floor(RadarProjection.y(south.coerceIn(-84.0, 84.0)) * n).toInt().coerceIn(0, n - 1)
            val count = (x1 - x0 + 1) * (y1 - y0 + 1)
            if (count <= budget || z == 0) return (y0..y1).flatMap { y -> (x0..x1).map { x -> Tile(z, x, y) } }
            z--
        }
    }

    // ---- network and caches ----

    private val frameLock = Mutex()
    private val frames = HashMap<Channel, Pair<Long, List<Instant>>>()
    private val gate = Semaphore(4)
    private val images = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun frames(channel: Channel, force: Boolean = false): List<Instant> = frameLock.withLock {
        val now = System.currentTimeMillis()
        frames[channel]?.takeIf { !force && now - it.first < 5 * 60_000L }?.let { return@withLock it.second }
        val list = try {
            parseDomains(String(request(domainsUrl(channel, Instant.ofEpochMilli(now)), 400_000), Charsets.UTF_8), Instant.ofEpochMilli(now))
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptyList() }
        val result = list.ifEmpty { frames[channel]?.second ?: fallbackTimes(Instant.ofEpochMilli(now)) }
        frames[channel] = now to result
        result
    }

    fun cached(channel: Channel, time: Instant, tile: Tile): Bitmap? =
        synchronized(images) { images.get(key(channel, time, tile)) }

    private fun key(channel: Channel, time: Instant, tile: Tile) = "${channel.name}/${time.epochSecond}/${tile.z}/${tile.x}/${tile.y}"

    suspend fun tile(context: Context, channel: Channel, time: Instant, tile: Tile): Bitmap? {
        val key = key(channel, time, tile)
        synchronized(images) { images.get(key) }?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                synchronized(images) { images.get(key) }?.let { return@withContext it }
                try {
                    val dir = File(context.cacheDir, "satellite-tiles")
                    val file = File(dir, key.replace('/', '_') + ".png")
                    val bytes = if (file.isFile && System.currentTimeMillis() - file.lastModified() < 3 * 3600_000L) file.readBytes()
                    else request(tileUrl(channel, time, tile), 1_500_000).also { payload ->
                        dir.mkdirs()
                        val pending = File.createTempFile("sat-", ".part", dir)
                        try { pending.writeBytes(payload); if (!pending.renameTo(file)) file.writeBytes(payload) } finally { pending.delete() }
                        trim(dir)
                    }
                    currentCoroutineContext().ensureActive()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) { file.delete(); return@withContext null }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { it.prepareToDraw(); synchronized(images) { images.put(key, it) } }
                } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
            }
        }
    }

    private var lastTrim = 0L
    private fun trim(dir: File) = synchronized(this) {
        val now = System.currentTimeMillis(); if (now - lastTrim < 60_000) return@synchronized
        lastTrim = now
        val files = dir.listFiles()?.sortedBy { it.lastModified() }.orEmpty(); var total = files.sumOf { it.length() }
        for (f in files) { if (total <= 64L * 1024 * 1024 && now - f.lastModified() < 3 * 3600_000L) break; val l = f.length(); if (f.delete()) total -= l }
    }

    suspend fun request(url: String, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        val u = URL(url)
        require(u.protocol == "https" && (u.host == HOST || u.host == "api.open-meteo.com"))
        val c = (u.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 8000; readTimeout = 12000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", "thinn-khi/26 (+https://github.com/luc700428-hash/thinn-kh-.)")
        }
        try {
            check(c.responseCode == 200)
            check(c.contentLengthLong <= limit)
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { input ->
                val b = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(b); if (n < 0) break
                    check(out.size() + n <= limit); out.write(b, 0, n)
                }
            }
            out.toByteArray()
        } finally { c.disconnect() }
    }

    /** Tiles that cover East Asia coarsely; drawn under anything not loaded yet, so a drag never shows black. */
    fun overview(channel: Channel): List<Tile> = tilesFor(95.0, 0.0, 150.0, 48.0, 8.0, minOf(3, channel.maxZoom), budget = 12)

    /** The tiles a view of [widthPx] pixels centred on Taiwan needs, with a wide margin for dragging. */
    fun around(channel: Channel, lon: Double, lat: Double, spanDeg: Double, widthPx: Int, margin: Double = .6, budget: Int = 64): List<Tile> {
        val halfW = spanDeg / 2 * (1 + 2 * margin); val halfH = halfW * 1.1
        val centre = Pair(lon, lat)
        return tilesFor(lon - halfW, lat - halfH, lon + halfW, lat + halfH, widthPx / spanDeg, channel.maxZoom, budget)
            .sortedBy { t -> val n = (1 shl t.z).toDouble(); val tl = (t.x + .5) / n * 360 - 180; val tt = RadarProjection.latitude((t.y + .5) / n)
                (tl - centre.first) * (tl - centre.first) + (tt - centre.second) * (tt - centre.second) }
    }

    private val warmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    @Volatile private var warmedAt = 0L
    @Volatile var warmOverlay: Any? = null

    /**
     * Started as soon as the radar page opens: the satellite timeline, the latest picture of the
     * Taiwan view, the coarse East Asia picture for every time, and the model field (prepared for
     * drawing), so switching to 衛星雲圖 shows everything at once and a drag never waits.
     */
    fun warmUp(context: Context, widthPx: Int, channel: Channel, prepare: (WeatherGrid) -> Any?) {
        val now = System.currentTimeMillis()
        if (now - warmedAt < 5 * 60_000L) return
        warmedAt = now
        val app = context.applicationContext
        warmScope.launch {
            val list = frames(channel)
            val latest = list.lastOrNull()
            if (latest != null) {
                val view = around(channel, 121.0, 24.0, 28.0, widthPx.coerceAtLeast(720), margin = .3, budget = 30)
                kotlinx.coroutines.coroutineScope { view.forEach { t -> launch { tile(app, channel, latest, t) } } }
                kotlinx.coroutines.coroutineScope { list.asReversed().forEach { time -> overview(channel).forEach { t -> launch { tile(app, channel, time, t) } } } }
            }
            field()?.let { g -> runCatching { warmOverlay = prepare(g) } }
        }
    }

    private val forecastLock = Mutex()
    private var forecast: Pair<Long, List<Pair<Instant, ForecastStep>>>? = null

    /** The 11-day model forecast, refreshed at most hourly. */
    suspend fun forecast(force: Boolean = false): List<Pair<Instant, ForecastStep>> = forecastLock.withLock {
        val now = System.currentTimeMillis()
        forecast?.takeIf { !force && now - it.first < 60 * 60_000L }?.let { return@withLock it.second }
        try {
            WeatherField.parseForecast(String(request(WeatherField.forecastUrl(), 12_000_000), Charsets.UTF_8)).also { forecast = now to it }
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { forecast?.second.orEmpty() }
    }

    private val fieldLock = Mutex()
    private var field: Pair<Long, WeatherGrid>? = null

    /** The model field, refreshed at most every 30 minutes. */
    suspend fun field(force: Boolean = false): WeatherGrid? = fieldLock.withLock {
        val now = System.currentTimeMillis()
        field?.takeIf { !force && now - it.first < 30 * 60_000L }?.let { return@withLock it.second }
        try {
            WeatherField.parse(String(request(WeatherField.url(), 3_000_000), Charsets.UTF_8)).also { field = now to it }
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { field?.second }
    }
}
