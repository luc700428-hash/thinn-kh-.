package io.atmos.weather.satellite

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.atmos.weather.maps.rememberRegionalVectorAtlas
import io.atmos.weather.radar.RadarProjection
import io.atmos.weather.typhoon.TyphoonViewport
import io.atmos.weather.ui.AppSystemBars
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassChoiceGroup
import io.atmos.weather.ui.GlassIconButton
import io.atmos.weather.ui.GlassSlider
import io.atmos.weather.ui.GlassToggleChoice
import io.atmos.weather.ui.IOSSettingsHeader
import io.atmos.weather.ui.brandColor
import io.atmos.weather.ui.rememberAppleReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/** Professional overlays, each switchable; stored as a bit set so the choice is remembered. */
internal enum class SatelliteLayer(val label: String, val bit: Int) {
    ISOBARS("等壓線", 1), CENTERS("高低壓", 2), WIND("風標", 4), ISOTHERMS("等溫線", 8),
    FLOW("風場動畫", 256), CLOUD("雲量預報", 64), RAIN("降水預報", 128),
    GRID("經緯網", 16), COAST("海岸線", 32);
    companion object { const val DEFAULT = 1 or 2 or 16 or 32 or 64 or 128 }
}

/** Everything drawn from one model field, prepared off the main thread. */
internal class FieldOverlay(
    val grid: WeatherGrid,
    val isobars: List<ContourSet>,
    val isotherms: List<ContourSet>,
    val centers: List<PressureCenter>,
) {
    /** Each contour as one path in degrees (lon − 100, lat), drawn with a single transform per frame. */
    class Line(val set: ContourSet, val path: android.graphics.Path, val labels: FloatArray)
    val isobarLines: List<Line> = isobars.map(::line)
    val isothermLines: List<Line> = isotherms.map(::line)
    private fun line(set: ContourSet): Line {
        val path = android.graphics.Path(); val s = set.segments
        var i = 0; var lastX = Float.NaN; var lastY = Float.NaN
        val labels = ArrayList<Float>()
        while (i + 3 < s.size) {
            val x0 = s[i] - 100f; val y0 = s[i + 1]; val x1 = s[i + 2] - 100f; val y1 = s[i + 3]
            if (x0 != lastX || y0 != lastY) path.moveTo(x0, y0)
            path.lineTo(x1, y1); lastX = x1; lastY = y1
            if ((i / 4) % 6 == 0) { labels += (s[i] + s[i + 2]) / 2; labels += (s[i + 1] + s[i + 3]) / 2 }
            i += 4
        }
        return Line(set, path, labels.toFloatArray())
    }

    companion object {
        fun build(grid: WeatherGrid): FieldOverlay {
            val factor = 4
            fun axis(a: DoubleArray) = DoubleArray((a.size - 1) * factor + 1) { a[0] + (a[1] - a[0]) * it / factor }
            val lats = axis(grid.lats); val lons = axis(grid.lons)
            val p = WeatherField.refine(grid.pressure, factor)
            val t = WeatherField.refine(grid.temperature, factor)
            return FieldOverlay(grid,
                WeatherField.contours(p, lats, lons, WeatherField.levels(grid.pressure, 4f, 1000f)),
                WeatherField.contours(t, lats, lons, WeatherField.levels(grid.temperature, 3f)),
                WeatherField.centers(grid))
        }
    }
}

/** Forecast clouds and rain as smooth images over the grid (built once per forecast step). */
internal class ForecastPictures(val cloud: ImageBitmap, val rain: ImageBitmap) {
    companion object {
        private const val F = 8
        fun build(step: ForecastStep): ForecastPictures {
            val rows = WeatherField.lats.size; val cols = WeatherField.lons.size
            val w = (cols - 1) * F + 1; val h = (rows - 1) * F + 1
            val c = WeatherField.refine(step.cloud, F); val r = WeatherField.refine(step.rain, F)
            val cp = IntArray(w * h); val rp = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val gy = ((rows - 1) * F - y)
                val cc = c[gy][x]; val rr = r[gy][x]
                if (cc.isFinite() && cc > 5f) {
                    val a = ((cc - 5f) / 95f).coerceIn(0f, 1f)
                    cp[y * w + x] = (((a * a * .62f + a * .2f) * 255).toInt().coerceIn(0, 230) shl 24) or 0xF2F5FA
                }
                if (rr.isFinite() && rr >= .3f) rp[y * w + x] = rainColour(rr)
            }
            fun image(px: IntArray) = android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            return ForecastPictures(image(cp), image(rp))
        }
        /** Radar-like scale for rain per 6 hours. */
        fun rainColour(mm: Float): Int {
            val stops = listOf(.3f to 0x6A9AD8FF.toInt(), 2f to 0x9A3DA2FF.toInt(), 8f to 0xB43DC36A.toInt(),
                20f to 0xC8F2D23B.toInt(), 50f to 0xD8F08A2B.toInt(), 100f to 0xE6E23B3B.toInt(), 200f to 0xEEB03BD8.toInt())
            val i = stops.indexOfLast { mm >= it.first }.coerceAtLeast(0)
            return stops[i].second
        }
    }
}

private const val COS = 0.9135 // cos 24°, one fixed scale for the whole East Asia view

private fun viewport(lon: Double, lat: Double, pxPerKm: Double, size: IntSize) =
    TyphoonViewport(lon * 111.2 * COS, lat * 111.2, pxPerKm, size.width.toDouble(), size.height.toDouble(), COS)

/** A wind particle field: streaks that drift with the model wind, fading and reborn. */
private class WindFlow(n: Int) {
    val lon = FloatArray(n); val lat = FloatArray(n); val pLon = FloatArray(n); val pLat = FloatArray(n); val age = IntArray(n)
    private val r = java.util.Random(7)
    fun seed(i: Int, w: Double, s: Double, e: Double, nn: Double) {
        lon[i] = (w + r.nextDouble() * (e - w)).toFloat(); lat[i] = (s + r.nextDouble() * (nn - s)).toFloat()
        pLon[i] = lon[i]; pLat[i] = lat[i]; age[i] = r.nextInt(80)
    }
    fun step(grid: WeatherGrid, w: Double, s: Double, e: Double, n: Double, dt: Float) {
        for (i in lon.indices) {
            val kt = WeatherField.sample(grid.windKnots, lat[i].toDouble(), lon[i].toDouble())
            val from = WeatherField.sample(grid.windFrom, lat[i].toDouble(), lon[i].toDouble())
            age[i]++
            if (!kt.isFinite() || !from.isFinite() || age[i] > 110 || lon[i] < w || lon[i] > e || lat[i] < s || lat[i] > n) { seed(i, w, s, e, n); continue }
            pLon[i] = lon[i]; pLat[i] = lat[i]
            val a = Math.toRadians(from.toDouble())
            val k = .0011f * dt * 60f
            lon[i] -= (sin(a) * kt * k).toFloat(); lat[i] -= (cos(a) * kt * k).toFloat()
        }
    }
}

@Composable
fun SatelliteViewer(latitude: Double, longitude: Double, onClose: () -> Unit, modeSwitch: @Composable () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val uri = LocalUriHandler.current
    val palette = MaterialTheme.colorScheme
    AppSystemBars(true)
    val prefs = remember(context) { context.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE) }
    var layers by remember { mutableIntStateOf(prefs.getInt("satellite_layers_v2", SatelliteLayer.DEFAULT)) }
    var channel by remember { mutableStateOf(SatelliteSource.Channel.entries.getOrElse(prefs.getInt("satellite_channel", 0)) { SatelliteSource.Channel.INFRARED }) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var centerLon by remember { mutableDoubleStateOf(121.0) }
    var centerLat by remember { mutableDoubleStateOf(24.0) }
    var pxPerKm by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(size) {
        if (pxPerKm == 0.0 && size.width > 0) pxPerKm = size.width / (28.0 * 111.2 * COS)
    }
    var frames by remember { mutableStateOf<List<Instant>>(emptyList()) }
    var forecast by remember { mutableStateOf<List<Pair<Instant, ForecastStep>>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("正在取得衛星時間軸…") }
    var revision by remember { mutableIntStateOf(0) }
    val reduce = rememberAppleReduceMotion()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(channel, refresh) {
        playing = false
        val list = SatelliteSource.frames(channel, refresh > 0)
        frames = list; index = list.lastIndex.coerceAtLeast(0)
        status = if (list.isEmpty()) "衛星服務暫時無法連線" else channel.note
    }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (isActive) { delay(600_000); refresh++ } }
    }
    // The analysis now, and the 11-day forecast after the last picture.
    var overlay by remember { mutableStateOf(SatelliteSource.warmOverlay as? FieldOverlay) }
    var fieldStatus by remember { mutableStateOf("") }
    LaunchedEffect(refresh) {
        fieldStatus = "正在取得數值模式氣壓場與 11 天預報…"
        val grid = SatelliteSource.field(refresh > 0)
        overlay = grid?.let { withContext(Dispatchers.Default) { runCatching { FieldOverlay.build(it) }.getOrNull() } } ?: overlay
        forecast = SatelliteSource.forecast(refresh > 0)
        fieldStatus = when {
            overlay == null && forecast.isEmpty() -> "數值模式資料暫時無法取得，衛星影像仍可查看"
            forecast.isEmpty() -> "11 天預報暫時無法取得"
            else -> ""
        }
    }
    val lastObserved = frames.lastOrNull()
    val ahead = remember(forecast, lastObserved) { forecast.filter { lastObserved == null || it.first.isAfter(lastObserved) } }
    val total = frames.size + ahead.size
    val forecastIndex = index - frames.size
    val step = ahead.getOrNull(forecastIndex)
    // Forecast pictures and overlays are built once per step, off the main thread, and kept.
    val pictures = remember(ahead) { mutableStateMapOf<Int, ForecastPictures>() }
    val stepOverlays = remember(ahead) { mutableStateMapOf<Int, FieldOverlay>() }
    LaunchedEffect(forecastIndex, ahead) {
        val s = step ?: return@LaunchedEffect
        if (forecastIndex !in pictures) withContext(Dispatchers.Default) { runCatching { ForecastPictures.build(s.second) }.getOrNull() }?.let { pictures[forecastIndex] = it }
        if (forecastIndex !in stepOverlays) withContext(Dispatchers.Default) { runCatching { FieldOverlay.build(s.second.grid) }.getOrNull() }?.let { stepOverlays[forecastIndex] = it }
    }
    // The camera is read only while drawing and in effects, so a drag redraws the map and nothing else.
    fun currentView() = if (size.width > 0 && pxPerKm > 0) viewport(centerLon, centerLat, pxPerKm, size) else null
    // Tiles for a wide margin around the view (about three screens across): dragging within it needs
    // nothing new. The set changes only when the view moves far or the zoom level changes.
    var tiles by remember { mutableStateOf<List<SatelliteSource.Tile>>(emptyList()) }
    LaunchedEffect(channel) {
        snapshotFlow {
            if (size.width <= 0 || pxPerKm <= 0) null
            else Triple((centerLon * 2).roundToInt(), (centerLat * 2).roundToInt(), kotlin.math.round(ln(pxPerKm) / ln(1.5)).toInt())
        }.distinctUntilChanged().collect { key ->
            if (key == null) return@collect
            val span = size.width / (pxPerKm * 111.2 * COS)
            tiles = SatelliteSource.around(channel, centerLon, centerLat, span, size.width, margin = .8, budget = 72)
        }
    }
    val overview = remember(channel) { SatelliteSource.overview(channel) }
    // Preload: the newest picture everywhere first (nearest tiles first), then the coarse East Asia
    // picture for every time, then every older picture. Progress is counted without recomposing.
    val loaded = remember { java.util.concurrent.atomic.AtomicInteger() }
    var progress by remember { mutableIntStateOf(100) }
    val needed = frames.size * tiles.size
    LaunchedEffect(frames, tiles, channel) {
        if (frames.isEmpty() || tiles.isEmpty()) return@LaunchedEffect
        loaded.set(frames.sumOf { t -> tiles.count { SatelliteSource.cached(channel, t, it) != null } })
        progress = loaded.get() * 100 / needed.coerceAtLeast(1)
        val ticker = launch { while (isActive) { delay(250); revision++; progress = (loaded.get() * 100 / needed.coerceAtLeast(1)).coerceIn(0, 100) } }
        suspend fun fetch(time: Instant, list: List<SatelliteSource.Tile>, count: Boolean) = supervisorScope {
            list.map { tile -> launch {
                if (SatelliteSource.cached(channel, time, tile) == null) {
                    SatelliteSource.tile(context, channel, time, tile)
                    if (count) loaded.incrementAndGet()
                }
            } }.joinAll()
        }
        fetch(frames.last(), tiles, true)
        for (t in frames.asReversed()) fetch(t, overview, false)
        for (t in frames.asReversed().drop(1)) fetch(t, tiles, true)
        ticker.cancel(); revision++; progress = 100
    }
    val allLoaded = progress >= 100
    LaunchedEffect(playing, total, reduce, allLoaded) {
        if (!playing || reduce || total < 2) return@LaunchedEffect
        while (isActive) {
            delay(if (index >= total - 1) 1400 else if (index >= frames.size) 450 else 600)
            index = if (index >= total - 1) 0 else index + 1
        }
    }
    // Wind particles run only while the layer is on and the page is visible.
    val flow = remember { WindFlow(650) }
    var flowTick by remember { mutableIntStateOf(0) }
    val flowOn = layers and SatelliteLayer.FLOW.bit != 0 && !reduce
    LaunchedEffect(flowOn, lifecycle) {
        if (!flowOn) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (isActive) withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60 else ((now - last) / 1e9f).coerceIn(0f, .05f); last = now
                val v = currentView(); val g = (if (forecastIndex >= 0) stepOverlays[forecastIndex] else overlay)?.grid
                if (v != null && g != null) { val b = v.rasterBounds(); flow.step(g, b.west, b.south, b.east, b.north, dt) }
                flowTick++
            }
        }
    }
    val atlas = rememberRegionalVectorAtlas()
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) } }
    val taipei = remember { ZoneId.of("Asia/Taipei") }
    val timeLabel = when {
        index < frames.size -> frames.getOrNull(index)?.let { "觀測 " + DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(taipei).format(it) }
        step != null -> {
            val days = java.time.Duration.between(lastObserved ?: Instant.now(), step.first).toHours() / 24.0
            "預報 " + DateTimeFormatter.ofPattern("MM/dd(E) HH:00", java.util.Locale.TAIWAN).withZone(taipei).format(step.first) +
                " · " + if (days < 1) "${java.time.Duration.between(Instant.now(), step.first).toHours().coerceAtLeast(0)} 小時後" else "%.1f 天後".format(days)
        }
        else -> null
    }
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader("衛星雲圖", onClose)
        modeSwitch()
        io.atmos.weather.ui.AdaptiveMapInspector(Modifier.weight(1f), map = { mapModifier ->
            Box(mapModifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF05080D)).onSizeChanged { size = it }) {
                Canvas(Modifier.fillMaxSize().pointerInput(size) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (size.width <= 0 || pxPerKm <= 0) return@detectTransformGestures
                        val min = size.width / (70.0 * 111.2 * COS); val max = 5.0
                        val next = (pxPerKm * zoom).coerceIn(min, max)
                        val dx = centroid.x - size.width / 2.0; val dy = centroid.y - size.height / 2.0
                        val lonAt = centerLon + dx / (pxPerKm * 111.2 * COS); val latAt = centerLat - dy / (pxPerKm * 111.2)
                        centerLon = (lonAt - dx / (next * 111.2 * COS) - pan.x / (next * 111.2 * COS)).coerceIn(80.0, 170.0)
                        centerLat = (latAt + dy / (next * 111.2) + pan.y / (next * 111.2)).coerceIn(-10.0, 55.0)
                        pxPerKm = next
                    }
                }) {
                    @Suppress("UNUSED_EXPRESSION") revision
                    val v = currentView() ?: return@Canvas
                    val o: FieldOverlay?
                    if (index < frames.size) {
                        drawSatellite(v, channel, frames, index, tiles, overview)
                        o = overlay
                    } else {
                        // Forecast: a dark map with the model's clouds and rain.
                        atlas?.draw(this, v, true)
                        pictures[forecastIndex]?.let { pic ->
                            val (x0, y0) = v.project(WeatherField.WEST, WeatherField.NORTH); val (x1, y1) = v.project(WeatherField.EAST, WeatherField.SOUTH)
                            val dst = IntOffset(x0.roundToInt(), y0.roundToInt()); val dsz = IntSize((x1 - x0).roundToInt(), (y1 - y0).roundToInt())
                            if (layers and SatelliteLayer.CLOUD.bit != 0) drawImage(pic.cloud, dstOffset = dst, dstSize = dsz, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                            if (layers and SatelliteLayer.RAIN.bit != 0) drawImage(pic.rain, dstOffset = dst, dstSize = dsz, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                        }
                        o = stepOverlays[forecastIndex]
                    }
                    if (layers and SatelliteLayer.GRID.bit != 0) drawGraticule(v, textPaint)
                    if (layers and SatelliteLayer.COAST.bit != 0) atlas?.draw(this, v, true, outline = 0xE6F2D27A.toInt(), outlineDp = 1.1f)
                    if (o != null) {
                        if (layers and SatelliteLayer.ISOTHERMS.bit != 0) drawContours(v, o.isothermLines, textPaint, isotherm = true)
                        if (layers and SatelliteLayer.ISOBARS.bit != 0) drawContours(v, o.isobarLines, textPaint, isotherm = false)
                        if (layers and SatelliteLayer.WIND.bit != 0) drawBarbs(v, o.grid)
                        if (layers and SatelliteLayer.CENTERS.bit != 0) drawCenters(v, o.centers, textPaint)
                    }
                    val (px, py) = v.project(longitude, latitude)
                    drawCircle(Color.White, 6.dp.toPx(), Offset(px, py))
                    drawCircle(brandColor(0xFF308970), 4.dp.toPx(), Offset(px, py))
                }
                // Wind particles in their own layer: their every-frame redraw leaves the map untouched.
                if (flowOn) Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION") flowTick
                    currentView()?.let { drawFlow(it, flow) }
                }
                Column(Modifier.align(Alignment.TopEnd).padding(9.dp)) {
                    GlassIconButton(onClick = { pxPerKm = (pxPerKm * 1.5).coerceAtMost(5.0) }) { Icon(Icons.Rounded.Add, "放大") }
                    Spacer(Modifier.height(7.dp))
                    GlassIconButton(onClick = { if (size.width > 0) pxPerKm = (pxPerKm / 1.5).coerceAtLeast(size.width / (70.0 * 111.2 * COS)) }) { Icon(Icons.Rounded.Remove, "縮小") }
                    Spacer(Modifier.height(7.dp))
                    GlassIconButton(onClick = { centerLon = 121.0; centerLat = 24.0; if (size.width > 0) pxPerKm = size.width / (28.0 * 111.2 * COS) }) { Icon(Icons.Rounded.MyLocation, "回到台灣") }
                }
                Column(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    Text(timeLabel ?: status, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.background(Color(0xB3000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                    if (needed > 0 && !allLoaded) {
                        Spacer(Modifier.height(5.dp))
                        Text("預載雲圖 $progress%", color = Color.White, fontSize = 14.sp,
                            modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (layers and SatelliteLayer.RAIN.bit != 0 && index >= frames.size) RainLegend(Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
        }, controls = {
            GlassChoiceGroup(SatelliteSource.Channel.entries.map { it.label }, channel.ordinal, { i ->
                channel = SatelliteSource.Channel.entries[i]; prefs.edit().putInt("satellite_channel", i).apply()
            }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
            Text(if (index >= frames.size) "數值模式預報（未來 11 天，每 6 小時）：雲量、降水、氣壓與風" else status,
                color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp))
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton({ playing = false; index = (index - 1).coerceAtLeast(0) }, enabled = total > 1) { Icon(Icons.Rounded.SkipPrevious, "前一張") }
                GlassIconButton({ playing = !playing }, enabled = total > 1 && !reduce) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "暫停動畫" else "播放動畫")
                }
                GlassSlider(index.toFloat(), { playing = false; index = it.roundToInt().coerceIn(0, (total - 1).coerceAtLeast(0)) },
                    Modifier.weight(1f), valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(), steps = 0, enabled = total > 1)
                GlassIconButton({ playing = false; index = (index + 1).coerceAtMost((total - 1).coerceAtLeast(0)) }, enabled = total > 1) { Icon(Icons.Rounded.SkipNext, "後一張") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassToggleChoice("現在", index == frames.lastIndex, { playing = false; index = frames.lastIndex.coerceAtLeast(0) }, Modifier.weight(1f))
                GlassToggleChoice("明天", false, { playing = false; jumpTo(ahead, 1)?.let { index = frames.size + it } }, Modifier.weight(1f))
                GlassToggleChoice("3 天後", false, { playing = false; jumpTo(ahead, 3)?.let { index = frames.size + it } }, Modifier.weight(1f))
                GlassToggleChoice("7 天後", false, { playing = false; jumpTo(ahead, 7)?.let { index = frames.size + it } }, Modifier.weight(1f))
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SatelliteLayer.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { layer ->
                            GlassToggleChoice(layer.label, layers and layer.bit != 0, {
                                layers = layers xor layer.bit; prefs.edit().putInt("satellite_layers_v2", layers).apply()
                            }, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (fieldStatus.isNotEmpty()) Text(fieldStatus, color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp))
            Text("等壓線每 4 hPa（1000 hPa 加粗）、等溫線每 3°C、風標單位為節；H 為高壓、L 為低壓。拖到「現在」之後是數值模式預報，越遠越不確定。",
                color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            Text("衛星：日本氣象廳 向日葵 9 號 · NASA GIBS ↗", color = palette.primary, fontSize = 14.sp,
                modifier = Modifier.clickable { uri.openUri("https://www.earthdata.nasa.gov/gibs") }.padding(horizontal = 18.dp, vertical = 4.dp))
            Text("氣象模式與預報：Open-Meteo ↗", color = palette.primary, fontSize = 14.sp,
                modifier = Modifier.clickable { uri.openUri("https://open-meteo.com") }.padding(horizontal = 18.dp, vertical = 4.dp))
        })
    }
}

/** The first forecast step at least [days] days from now (Taipei noon when possible). */
private fun jumpTo(ahead: List<Pair<Instant, ForecastStep>>, days: Int): Int? {
    val target = Instant.now().plusSeconds(days * 86_400L)
    return ahead.indices.minByOrNull { kotlin.math.abs(ahead[it].first.epochSecond - target.epochSecond) }
}

@Composable
private fun RainLegend(modifier: Modifier) {
    Row(modifier.background(Color(0xB3000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("降水", color = Color.White, fontSize = 14.sp); Spacer(Modifier.width(6.dp))
        listOf(.3f, 2f, 8f, 20f, 50f, 100f).forEach { mm ->
            Box(Modifier.size(14.dp).background(Color(ForecastPictures.rainColour(mm)).copy(alpha = 1f), RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(3.dp))
        }
        Text("多", color = Color.White, fontSize = 14.sp)
    }
}

private fun DrawScope.drawFlow(v: TyphoonViewport, f: WindFlow) {
    val c = Color(0xFFCFF7FF)
    for (i in f.lon.indices) {
        if (f.age[i] < 2) continue
        val (x0, y0) = v.project(f.pLon[i].toDouble(), f.pLat[i].toDouble()); val (x1, y1) = v.project(f.lon[i].toDouble(), f.lat[i].toDouble())
        if (x1 !in 0f..size.width || y1 !in 0f..size.height) continue
        val life = (1f - kotlin.math.abs(f.age[i] - 55f) / 55f).coerceIn(0f, 1f)
        // Stretch each step into a streak so slow winds still read as motion.
        val ex = x1 + (x1 - x0) * 3f; val ey = y1 + (y1 - y0) * 3f
        drawLine(c.copy(alpha = .55f * life), Offset(x0, y0), Offset(ex, ey), 1.3f * density, StrokeCap.Round)
    }
}

/**
 * The satellite picture for [index]: each tile from the cache for that time; if one is still on its
 * way, the nearest time already loaded, and failing that the coarser tile around it. Never blank.
 */
private fun DrawScope.drawSatellite(v: TyphoonViewport, channel: SatelliteSource.Channel, frames: List<Instant>, index: Int,
                                    tiles: List<SatelliteSource.Tile>, overview: List<SatelliteSource.Tile>) {
    val time = frames.getOrNull(index) ?: return
    val order = frames.indices.sortedBy { kotlin.math.abs(it - index) }
    val b = v.rasterBounds()
    fun visible(t: SatelliteSource.Tile): Boolean {
        val n = (1 shl t.z).toDouble()
        val west = t.x / n * 360 - 180; val east = (t.x + 1) / n * 360 - 180
        val north = RadarProjection.latitude(t.y / n); val south = RadarProjection.latitude((t.y + 1) / n)
        return east >= b.west && west <= b.east && north >= b.south && south <= b.north
    }
    // The coarse picture first, so nothing is ever black while finer tiles arrive.
    overview.forEach { t ->
        if (!visible(t)) return@forEach
        order.firstNotNullOfOrNull { SatelliteSource.cached(channel, frames[it], t) }?.let { drawTile(v, t, it.asImageBitmap(), 0, 0, it.width, it.height) }
    }
    tiles.forEach { tile ->
        if (!visible(tile)) return@forEach
        SatelliteSource.cached(channel, time, tile)?.let { drawTile(v, tile, it.asImageBitmap(), 0, 0, it.width, it.height); return@forEach }
        // Not here yet: the nearest coarser tile of the same time, then this tile at the nearest time.
        for (dz in 1..minOf(3, tile.z)) {
            val parent = SatelliteSource.Tile(tile.z - dz, tile.x shr dz, tile.y shr dz)
            val p = SatelliteSource.cached(channel, time, parent) ?: continue
            val part = p.width shr dz
            drawTile(v, tile, p.asImageBitmap(), (tile.x - (parent.x shl dz)) * part, (tile.y - (parent.y shl dz)) * part, part, part)
            return@forEach
        }
        order.firstNotNullOfOrNull { SatelliteSource.cached(channel, frames[it], tile) }?.let { drawTile(v, tile, it.asImageBitmap(), 0, 0, it.width, it.height) }
    }
}

/** One Mercator tile (or a part of it) drawn into the lon/lat view in strips, so latitudes line up. */
private fun DrawScope.drawTile(v: TyphoonViewport, tile: SatelliteSource.Tile, img: ImageBitmap, sx: Int, sy: Int, sw: Int, sh: Int) {
    // Fine tiles span few degrees, where Mercator is nearly linear: fewer strips, fewer draws.
    val strips = when { tile.z <= 3 -> 8; tile.z == 4 -> 4; else -> 2 }
    val n = (1 shl tile.z).toDouble()
    val west = tile.x / n * 360 - 180; val east = (tile.x + 1) / n * 360 - 180
    for (k in 0 until strips) {
        val north = RadarProjection.latitude((tile.y + k.toDouble() / strips) / n)
        val south = RadarProjection.latitude((tile.y + (k + 1).toDouble() / strips) / n)
        val (x0, y0) = v.project(west, north); val (x1, y1) = v.project(east, south)
        if (x1 < 0 || y1 < 0 || x0 > size.width || y0 > size.height) continue
        val top = sy + sh * k / strips; val bottom = sy + sh * (k + 1) / strips
        drawImage(img, IntOffset(sx, top), IntSize(sw, (bottom - top).coerceAtLeast(1)),
            IntOffset(floor(x0).toInt(), floor(y0).toInt()),
            IntSize((ceil(x1) - floor(x0)).toInt().coerceAtLeast(1), (ceil(y1) - floor(y0)).toInt().coerceAtLeast(1) + 1),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
    }
}

private fun DrawScope.drawGraticule(v: TyphoonViewport, paint: Paint) {
    val b = v.rasterBounds()
    val span = b.east - b.west
    val step = when { span > 40 -> 10.0; span > 12 -> 5.0; span > 5 -> 2.0; else -> 1.0 }
    val line = Color.White.copy(alpha = .22f)
    paint.color = 0xB3FFFFFF.toInt(); paint.textSize = 11.sp.toPx(); paint.textAlign = Paint.Align.LEFT
    var lon = ceil(b.west / step) * step
    while (lon <= b.east) {
        val (x, _) = v.project(lon, 0.0)
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
        drawContext.canvas.nativeCanvas.drawText("${lon.roundToInt()}°E", x + 3, size.height - 6.dp.toPx(), paint)
        lon += step
    }
    var lat = ceil(b.south / step) * step
    while (lat <= b.north) {
        val (_, y) = v.project(0.0, lat)
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        drawContext.canvas.nativeCanvas.drawText("${lat.roundToInt()}°N", 4.dp.toPx(), y - 3, paint)
        lat += step
    }
}

private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

/** Contours: one prepared path per level under a single transform, and one label per line. */
private fun DrawScope.drawContours(v: TyphoonViewport, lines: List<FieldOverlay.Line>, paint: Paint, isotherm: Boolean) {
    val ppd = (111.2 * v.pxPerKm).toFloat()
    if (!ppd.isFinite() || ppd <= 0f) return
    val origin = v.project(100.0, 0.0)
    val canvas = drawContext.canvas.nativeCanvas
    val dash = if (isotherm) android.graphics.DashPathEffect(floatArrayOf(7.dp.toPx() / ppd, 5.dp.toPx() / ppd), 0f) else null
    canvas.save(); canvas.clipRect(0f, 0f, size.width, size.height)
    canvas.translate(origin.first, origin.second); canvas.scale((ppd * COS).toFloat(), -ppd)
    lines.forEach { line ->
        val set = line.set
        val bold = !isotherm && abs(set.level - 1000f) < .01f || isotherm && abs(set.level) < .01f
        contourPaint.color = contourColour(set.level, isotherm, bold)
        contourPaint.strokeWidth = (if (bold) 2.2f else 1.3f) * density / ppd
        contourPaint.pathEffect = dash
        canvas.drawPath(line.path, contourPaint)
    }
    canvas.restore()
    paint.textSize = 11.sp.toPx(); paint.textAlign = Paint.Align.CENTER
    lines.forEach { line ->
        val l = line.labels; var bx = 0f; var by = 0f; var best = Float.MAX_VALUE
        var i = 0
        while (i + 1 < l.size) {
            val (x, y) = v.project(l[i].toDouble(), l[i + 1].toDouble())
            if (x in 30f..size.width - 30f && y in 20f..size.height - 20f) {
                val score = abs(x - size.width * (if (isotherm) .3f else .72f)) + abs(y - size.height / 2) * .2f
                if (score < best) { best = score; bx = x; by = y }
            }
            i += 2
        }
        if (best < Float.MAX_VALUE) {
            val bold = !isotherm && abs(line.set.level - 1000f) < .01f || isotherm && abs(line.set.level) < .01f
            haloText(if (isotherm) "${line.set.level.roundToInt()}°" else "${line.set.level.roundToInt()}", bx, by + 4.dp.toPx(), paint,
                Color(contourColour(line.set.level, isotherm, bold)))
        }
    }
}

private fun contourColour(level: Float, isotherm: Boolean, bold: Boolean): Int = when {
    !isotherm -> if (bold) 0xF2FFFFFF.toInt() else 0xC7FFFFFF.toInt()
    level <= 0f -> 0xFF7EC8FF.toInt()
    level >= 27f -> 0xFFFF7A5C.toInt()
    else -> 0xFFFFB55C.toInt()
}

private fun DrawScope.haloText(text: String, x: Float, y: Float, paint: Paint, color: Color) {
    val canvas = drawContext.canvas.nativeCanvas
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.dp.toPx(); paint.color = 0xCC000000.toInt()
    canvas.drawText(text, x, y, paint)
    paint.style = Paint.Style.FILL; paint.color = android.graphics.Color.argb((color.alpha * 255).toInt(),
        (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    canvas.drawText(text, x, y, paint)
}

private fun DrawScope.drawCenters(v: TyphoonViewport, centers: List<PressureCenter>, paint: Paint) {
    paint.textAlign = Paint.Align.CENTER
    centers.forEach { c ->
        val (x, y) = v.project(c.lon, c.lat)
        if (x !in -20f..size.width + 20f || y !in -20f..size.height + 20f) return@forEach
        val color = if (c.high) Color(0xFF5AA9FF) else Color(0xFFFF5A5A)
        paint.textSize = 26.sp.toPx()
        haloText(if (c.high) "H" else "L", x, y + 9.dp.toPx(), paint, color)
        paint.textSize = 12.sp.toPx()
        haloText("${c.hPa.roundToInt()}", x, y + 24.dp.toPx(), paint, Color.White)
    }
}

/** Standard station-model wind barbs, staff toward where the wind comes from, barbs on its right. */
private fun DrawScope.drawBarbs(v: TyphoonViewport, grid: WeatherGrid) {
    val spacing = 42.dp.toPx()
    val staff = 22.dp.toPx(); val feather = 9.dp.toPx(); val gap = 3.5.dp.toPx()
    val color = Color(0xFFB8F5C8); val w = 1.4f * density
    for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
        val kt = grid.windKnots[r][c]; val from = grid.windFrom[r][c]
        if (!kt.isFinite() || !from.isFinite()) continue
        val (x, y) = v.project(grid.lons[c], grid.lats[r])
        if (x !in 0f..size.width || y !in 0f..size.height) continue
        // Thin out when zoomed far out: keep a point only if its neighbours are far enough apart.
        val (nx, _) = v.project(grid.lons[c] + WeatherField.STEP, grid.lats[r])
        val every = ceil(spacing / max(1f, nx - x)).toInt().coerceAtLeast(1)
        if (r % every != 0 || c % every != 0) continue
        val b = WeatherField.barb(kt)
        if (b.calm) { drawCircle(color, 3.dp.toPx(), Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(w)); continue }
        val a = Math.toRadians(from.toDouble())
        val sx = sin(a).toFloat(); val sy = -cos(a).toFloat()          // staff direction on screen
        val px = -sy; val py = sx                                       // to the right of the staff
        val tip = Offset(x + sx * staff, y + sy * staff)
        drawLine(color, Offset(x, y), tip, w, StrokeCap.Round)
        var along = 0f
        fun base() = Offset(tip.x - sx * along, tip.y - sy * along)
        repeat(b.flags) {
            val p0 = base(); along += gap * 1.6f; val p1 = base()
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(p0.x, p0.y); lineTo(p0.x + px * feather + sx * 2f, p0.y + py * feather + sy * 2f); lineTo(p1.x, p1.y); close()
            }
            drawPath(path, color); along += gap * .4f
        }
        repeat(b.full) {
            val p = base()
            drawLine(color, p, Offset(p.x + px * feather + sx * feather * .35f, p.y + py * feather + sy * feather * .35f), w, StrokeCap.Round)
            along += gap
        }
        if (b.half == 1) {
            if (b.flags == 0 && b.full == 0) along += gap
            val p = base()
            drawLine(color, p, Offset(p.x + px * feather * .55f + sx * feather * .2f, p.y + py * feather * .55f + sy * feather * .2f), w, StrokeCap.Round)
        }
    }
}
