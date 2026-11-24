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
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/** Professional overlays, each switchable; stored as a bit set so the choice is remembered. */
internal enum class SatelliteLayer(val label: String, val bit: Int) {
    ISOBARS("等壓線", 1), CENTERS("高低壓", 2), WIND("風標", 4), ISOTHERMS("等溫線", 8),
    GRID("經緯網", 16), COAST("海岸線", 32);
    companion object { const val DEFAULT = 1 or 2 or 16 or 32 }
}

/** Everything drawn from the model field, prepared off the main thread. */
internal class FieldOverlay(
    val grid: WeatherGrid,
    val isobars: List<ContourSet>,
    val isotherms: List<ContourSet>,
    val centers: List<PressureCenter>,
) {
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

private const val COS = 0.9135 // cos 24°, one fixed scale for the whole East Asia view

private fun viewport(lon: Double, lat: Double, pxPerKm: Double, size: IntSize) =
    TyphoonViewport(lon * 111.2 * COS, lat * 111.2, pxPerKm, size.width.toDouble(), size.height.toDouble(), COS)

@Composable
fun SatelliteViewer(latitude: Double, longitude: Double, onClose: () -> Unit, modeSwitch: @Composable () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val uri = LocalUriHandler.current
    val palette = MaterialTheme.colorScheme
    AppSystemBars(true)
    val prefs = remember(context) { context.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE) }
    var layers by remember { mutableIntStateOf(prefs.getInt("satellite_layers", SatelliteLayer.DEFAULT)) }
    var channel by remember { mutableStateOf(SatelliteSource.Channel.entries.getOrElse(prefs.getInt("satellite_channel", 0)) { SatelliteSource.Channel.INFRARED }) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var centerLon by remember { mutableDoubleStateOf(121.0) }
    var centerLat by remember { mutableDoubleStateOf(24.0) }
    var pxPerKm by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(size) {
        if (pxPerKm == 0.0 && size.width > 0) pxPerKm = size.width / (28.0 * 111.2 * COS)
    }
    var frames by remember { mutableStateOf<List<Instant>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("正在取得衛星時間軸…") }
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
    var overlay by remember { mutableStateOf<FieldOverlay?>(null) }
    var fieldStatus by remember { mutableStateOf("") }
    LaunchedEffect(refresh) {
        fieldStatus = "正在取得數值模式氣壓場…"
        val grid = SatelliteSource.field(refresh > 0)
        overlay = grid?.let { withContext(Dispatchers.Default) { runCatching { FieldOverlay.build(it) }.getOrNull() } } ?: overlay
        fieldStatus = if (overlay == null) "氣壓場暫時無法取得，衛星影像仍可查看" else ""
    }
    val view = if (size.width > 0 && pxPerKm > 0) viewport(centerLon, centerLat, pxPerKm, size) else null
    val tiles = remember(view?.let { listOf(it.originXKm.roundToInt(), it.originYKm.roundToInt(), (it.pxPerKm * 100).roundToInt()) }, channel, size) {
        view?.let { v ->
            val b = v.rasterBounds()
            SatelliteSource.tilesFor(b.west, b.south, b.east, b.north, 111.2 * v.pxPerKm * COS, channel.maxZoom)
        }.orEmpty()
    }
    // What is on screen: the chosen frame's tiles, or the last complete picture while it loads.
    var shown by remember { mutableStateOf<Map<SatelliteSource.Tile, ImageBitmap>>(emptyMap()) }
    var shownTime by remember { mutableStateOf<Instant?>(null) }
    var loading by remember { mutableStateOf(false) }
    val time = frames.getOrNull(index)
    LaunchedEffect(time, tiles, channel) {
        val t = time ?: return@LaunchedEffect
        if (tiles.isEmpty()) return@LaunchedEffect
        loading = true
        try {
            val images = supervisorScope {
                tiles.map { tile -> async { tile to SatelliteSource.tile(context, channel, t, tile)?.asImageBitmap() } }.awaitAll()
            }.mapNotNull { (tile, image) -> image?.let { tile to it } }.toMap()
            if (images.isNotEmpty()) { shown = images; shownTime = t }
        } finally { loading = false }
        // Quietly fetch the next frames so playback does not wait.
        if (playing) frames.getOrNull(index + 1)?.let { next ->
            supervisorScope { tiles.forEach { tile -> launch { SatelliteSource.tile(context, channel, next, tile) } } }
        }
    }
    LaunchedEffect(playing, frames, reduce) {
        if (!playing || reduce || frames.size < 2) return@LaunchedEffect
        while (isActive) {
            delay(if (index >= frames.lastIndex) 1200 else 650)
            if (!loading) index = if (index >= frames.lastIndex) 0 else index + 1
        }
    }
    val atlas = rememberRegionalVectorAtlas()
    val density = LocalDensity.current.density
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) } }
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
                        // Keep the point under the fingers still while zooming, then pan.
                        val dx = centroid.x - size.width / 2.0; val dy = centroid.y - size.height / 2.0
                        val lonAt = centerLon + dx / (pxPerKm * 111.2 * COS); val latAt = centerLat - dy / (pxPerKm * 111.2)
                        centerLon = lonAt - dx / (next * 111.2 * COS) - pan.x / (next * 111.2 * COS)
                        centerLat = (latAt + dy / (next * 111.2) + pan.y / (next * 111.2))
                        centerLon = centerLon.coerceIn(80.0, 170.0); centerLat = centerLat.coerceIn(-10.0, 55.0)
                        pxPerKm = next
                    }
                }) {
                    val v = view ?: return@Canvas
                    drawSatellite(v, shown)
                    val o = overlay
                    if (layers and SatelliteLayer.GRID.bit != 0) drawGraticule(v, textPaint)
                    if (layers and SatelliteLayer.COAST.bit != 0) atlas?.draw(this, v, true, outline = 0xE6F2D27A.toInt(), outlineDp = 1.1f)
                    if (o != null) {
                        if (layers and SatelliteLayer.ISOTHERMS.bit != 0) drawContours(v, o.isotherms, textPaint, isotherm = true)
                        if (layers and SatelliteLayer.ISOBARS.bit != 0) drawContours(v, o.isobars, textPaint, isotherm = false)
                        if (layers and SatelliteLayer.WIND.bit != 0) drawBarbs(v, o.grid)
                        if (layers and SatelliteLayer.CENTERS.bit != 0) drawCenters(v, o.centers, textPaint)
                    }
                    val (px, py) = v.project(longitude, latitude)
                    drawCircle(Color.White, 6.dp.toPx(), Offset(px, py))
                    drawCircle(brandColor(0xFF308970), 4.dp.toPx(), Offset(px, py))
                }
                Column(Modifier.align(Alignment.TopEnd).padding(9.dp)) {
                    GlassIconButton(onClick = { pxPerKm = (pxPerKm * 1.5).coerceAtMost(5.0) }) { Icon(Icons.Rounded.Add, "放大") }
                    Spacer(Modifier.height(7.dp))
                    GlassIconButton(onClick = { if (size.width > 0) pxPerKm = (pxPerKm / 1.5).coerceAtLeast(size.width / (70.0 * 111.2 * COS)) }) { Icon(Icons.Rounded.Remove, "縮小") }
                    Spacer(Modifier.height(7.dp))
                    GlassIconButton(onClick = { centerLon = 121.0; centerLat = 24.0; if (size.width > 0) pxPerKm = size.width / (28.0 * 111.2 * COS) }) { Icon(Icons.Rounded.MyLocation, "回到台灣") }
                }
                val label = shownTime?.let { DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei")).format(it) }
                Text(listOfNotNull(label?.let { "觀測 $it" }, if (loading) "載入中…" else null).joinToString(" · ").ifEmpty { status },
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xB3000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }, controls = {
            GlassChoiceGroup(SatelliteSource.Channel.entries.map { it.label }, channel.ordinal, { i ->
                channel = SatelliteSource.Channel.entries[i]; prefs.edit().putInt("satellite_channel", i).apply()
            }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
            Text(status, color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SatelliteLayer.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { layer ->
                            GlassToggleChoice(layer.label, layers and layer.bit != 0, {
                                layers = layers xor layer.bit; prefs.edit().putInt("satellite_layers", layers).apply()
                            }, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (fieldStatus.isNotEmpty()) Text(fieldStatus, color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp))
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton({ playing = false; index = (index - 1).coerceAtLeast(0) }, enabled = frames.size > 1) { Icon(Icons.Rounded.SkipPrevious, "前一張") }
                GlassIconButton({ playing = !playing }, enabled = frames.size > 1 && !reduce) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "暫停雲圖動畫" else "播放雲圖動畫")
                }
                GlassSlider(index.toFloat(), { playing = false; index = it.roundToInt().coerceIn(0, frames.lastIndex.coerceAtLeast(0)) },
                    Modifier.weight(1f), valueRange = 0f..frames.lastIndex.coerceAtLeast(1).toFloat(), steps = 0, enabled = frames.size > 1)
                GlassIconButton({ playing = false; index = (index + 1).coerceAtMost(frames.lastIndex.coerceAtLeast(0)) }, enabled = frames.size > 1) { Icon(Icons.Rounded.SkipNext, "後一張") }
                GlassIconButton({ refresh++ }) { Icon(Icons.Rounded.Refresh, "重新讀取衛星雲圖") }
            }
            val modelTime = overlay?.grid?.time?.let { DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei")).format(it) }
            Text("等壓線每 4 hPa（1000 hPa 加粗）、等溫線每 3°C、風標單位為節；H 為高壓、L 為低壓。" +
                (modelTime?.let { "氣壓、氣溫與風來自數值模式分析（$it），不是衛星觀測。" } ?: ""),
                color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            Text("衛星：日本氣象廳 向日葵 9 號 · NASA GIBS ↗", color = palette.primary, fontSize = 14.sp,
                modifier = Modifier.clickable { uri.openUri("https://www.earthdata.nasa.gov/gibs") }.padding(horizontal = 18.dp, vertical = 4.dp))
            Text("氣象模式：Open-Meteo ↗", color = palette.primary, fontSize = 14.sp,
                modifier = Modifier.clickable { uri.openUri("https://open-meteo.com") }.padding(horizontal = 18.dp, vertical = 4.dp))
        })
    }
}

/** Web Mercator tiles drawn into the equal-scale lon/lat view in thin strips, so latitudes line up. */
private fun DrawScope.drawSatellite(v: TyphoonViewport, images: Map<SatelliteSource.Tile, ImageBitmap>) {
    val strips = 8
    images.forEach { (tile, img) ->
        val n = (1 shl tile.z).toDouble()
        val west = tile.x / n * 360 - 180; val east = (tile.x + 1) / n * 360 - 180
        for (k in 0 until strips) {
            val north = RadarProjection.latitude((tile.y + k.toDouble() / strips) / n)
            val south = RadarProjection.latitude((tile.y + (k + 1).toDouble() / strips) / n)
            val (x0, y0) = v.project(west, north); val (x1, y1) = v.project(east, south)
            if (x1 < 0 || y1 < 0 || x0 > size.width || y0 > size.height) continue
            val sy = img.height * k / strips; val sh = img.height * (k + 1) / strips - sy
            drawImage(img, IntOffset(0, sy), IntSize(img.width, sh),
                IntOffset(floor(x0).toInt(), floor(y0).toInt()),
                IntSize((ceil(x1) - floor(x0)).toInt().coerceAtLeast(1), (ceil(y1) - floor(y0)).toInt().coerceAtLeast(1) + 1))
        }
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

private fun DrawScope.drawContours(v: TyphoonViewport, sets: List<ContourSet>, paint: Paint, isotherm: Boolean) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
    paint.textSize = 11.sp.toPx(); paint.textAlign = Paint.Align.CENTER
    sets.forEach { set ->
        val bold = !isotherm && abs(set.level - 1000f) < .01f || isotherm && abs(set.level) < .01f
        val color = when {
            !isotherm -> Color.White.copy(alpha = if (bold) .95f else .78f)
            set.level <= 0f -> Color(0xFF7EC8FF)
            set.level >= 27f -> Color(0xFFFF7A5C)
            else -> Color(0xFFFFB55C)
        }
        val width = (if (bold) 2.2f else 1.3f) * density
        val s = set.segments
        var best = -1; var bestScore = Float.MAX_VALUE
        var i = 0
        while (i + 3 < s.size) {
            val (x0, y0) = v.project(s[i].toDouble(), s[i + 1].toDouble())
            val (x1, y1) = v.project(s[i + 2].toDouble(), s[i + 3].toDouble())
            if (max(x0, x1) >= 0 && min(x0, x1) <= size.width && max(y0, y1) >= 0 && min(y0, y1) <= size.height) {
                drawLine(color, Offset(x0, y0), Offset(x1, y1), width, StrokeCap.Round, pathEffect = if (isotherm) dash else null)
                // One label per line, near the right third of the view where it rarely meets a centre mark.
                val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2
                if (mx in 30f..size.width - 30f && my in 20f..size.height - 20f) {
                    val score = abs(mx - size.width * (if (isotherm) .3f else .72f)) + abs(my - size.height / 2) * .2f
                    if (score < bestScore) { bestScore = score; best = i }
                }
            }
            i += 4
        }
        if (best >= 0) {
            val (x, y) = v.project(((s[best] + s[best + 2]) / 2).toDouble(), ((s[best + 1] + s[best + 3]) / 2).toDouble())
            val text = if (isotherm) "${set.level.roundToInt()}°" else "${set.level.roundToInt()}"
            haloText(text, x, y + 4.dp.toPx(), paint, color)
        }
    }
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
