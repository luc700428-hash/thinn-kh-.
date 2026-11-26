package io.atmos.weather.life

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FireHydrantAlt
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.radar.RadarProjection
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 臺灣通用電子地圖 tiles from 內政部國土測繪中心 (NLSC WMTS, open government data): every building
 * outline, lane and house number down to zoom 19, and the latest orthophoto as 衛星. Tiles are
 * kept in memory and on disk (cache), so a map opened once still draws without a signal.
 */
internal object NlscTiles {
    /** [minZ]: overlays only appear (and are fetched) once you are close enough to read them. */
    enum class Layer(val id: String, val label: String, val maxZ: Int, val minZ: Int = 0) {
        MAP("EMAP98", "標準", 19), PHOTO("PHOTO2", "衛星", 19), LABELS("EMAP2", "路名", 19), TERRAIN("EMAP5_OPENDATA", "地形", 15),
        AED("AED", "AED", 19, 15), STORE("ConvenienceStore", "超商", 19, 15), HYDRANT("fireplug", "消防栓", 19, 16)
    }
    /** The base looks you choose between; 衛星 carries the road names over the photo. */
    enum class Style(val label: String, val layers: List<Layer>) {
        STANDARD("標準", listOf(Layer.MAP)), HYBRID("衛星", listOf(Layer.PHOTO, Layer.LABELS)), TERRAIN("地形", listOf(Layer.TERRAIN))
    }

    private val memory = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failed = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(6)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var pruned = false
    @Volatile var wanted: Set<String> = emptySet()
    /** Bumped on the main thread whenever a tile lands, so the map redraws. */
    val version = mutableIntStateOf(0)
    private val offline = android.os.Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)

    fun key(l: Layer, z: Int, x: Int, y: Int) = "${l.id}/$z/$x/$y"
    fun get(l: Layer, z: Int, x: Int, y: Int): ImageBitmap? = memory.get(key(l, z, x, y))
    /** Tests and previews hand tiles in directly. */
    internal fun prime(key: String, image: ImageBitmap) { memory.put(key, image); version.intValue++ }
    fun url(l: Layer, z: Int, x: Int, y: Int) = "https://wmts.nlsc.gov.tw/wmts/${l.id}/default/GoogleMapsCompatible/$z/$y/$x"

    fun request(ctx: Context, l: Layer, z: Int, x: Int, y: Int) {
        val k = key(l, z, x, y)
        if (offline || memory.get(k) != null || !inFlight.add(k)) return
        if ((failed[k] ?: 0L) > System.currentTimeMillis() - 30_000L) { inFlight.remove(k); return }
        val dir = File(ctx.applicationContext.cacheDir, "nlsc")
        scope.launch {
            try {
                if (!pruned) { pruned = true; prune(dir) }
                gate.withPermit {
                    // Panned away while waiting: leave it for later.
                    if (k !in wanted) return@withPermit
                    val file = File(dir, "$k.img")
                    val fresh = file.isFile && file.length() > 0 && System.currentTimeMillis() - file.lastModified() < 45L * 86_400_000L
                    val bytes = if (fresh) file.readBytes() else download(url(l, z, x, y))?.also { b ->
                        runCatching { file.parentFile?.mkdirs(); file.writeBytes(b) }
                    } ?: (if (file.isFile) file.readBytes() else null)
                    val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    if (bmp == null) failed[k] = System.currentTimeMillis()
                    else { memory.put(k, bmp.asImageBitmap()); withContext(Dispatchers.Main) { version.intValue++ } }
                }
            } catch (_: Throwable) { failed[k] = System.currentTimeMillis() } finally { inFlight.remove(k) }
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 10000
            setRequestProperty("User-Agent", "thinn-khi/34 (+https://github.com/luc700428-hash/thinn-kh-.)")
        }
        try { if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }.takeIf { it.size in 64..2_000_000 } } finally { c.disconnect() }
    }.getOrNull()

    /** Keeps the disk cache under 120 MB, dropping the tiles seen longest ago. */
    private fun prune(dir: File) = runCatching {
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        if (total < 120L * 1024 * 1024) return@runCatching
        for (f in files.sortedBy { it.lastModified() }) { if (total < 90L * 1024 * 1024) break; total -= f.length(); f.delete() }
    }

    /** Night map: inverted lightness with the hues kept (roads stay amber, water stays blue). */
    val night: ColorFilter = run {
        val h = floatArrayOf(-.574f, 1.43f, .144f, .426f, .43f, .144f, .426f, 1.43f, -.856f)   // hue-rotate(180°)
        val m = FloatArray(20)
        for (r in 0..2) {
            for (c in 0..2) m[r * 5 + c] = -h[r * 3 + c] * .82f
            m[r * 5 + 4] = (h[r * 3] + h[r * 3 + 1] + h[r * 3 + 2]) * 255f * .82f + 14f
        }
        m[18] = 1f
        ColorFilter.colorMatrix(ColorMatrix(m))
    }
}

/** One place on the map. */
internal data class MapPin(val id: String, val lat: Double, val lon: Double, val color: Color, val title: String)

/**
 * A real street map (zoom 19, building level). Drag to move, pinch or double-tap to zoom, tap a
 * pin to pick it. The view first fits you and the first [fit] pins; [selected] flies into view.
 */
@Composable
internal fun TaiwanDetailMap(
    pins: List<MapPin>, me: Pair<Double, Double>?, selected: String?, onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier, fit: Int = 6,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val dark = MaterialTheme.colorScheme.background.luminance() < .4f
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val pick by rememberUpdatedState(onSelect)
    var style by remember { mutableStateOf(NlscTiles.Style.STANDARD) }
    var overlays by remember { mutableStateOf(setOf<NlscTiles.Layer>()) }
    var panel by remember { mutableStateOf(false) }
    val drawn = style.layers + overlays.sortedBy { it.ordinal }
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    val start = me ?: pins.firstOrNull()?.let { it.lat to it.lon } ?: (23.7 to 120.96)
    var cx by remember { mutableDoubleStateOf(RadarProjection.x(start.second)) }
    var cy by remember { mutableDoubleStateOf(RadarProjection.y(start.first)) }
    var zoom by remember { mutableDoubleStateOf(15.0) }
    var fitted by remember { mutableStateOf(false) }
    val maxZoom = 19.4 - log2(density.toDouble().coerceAtLeast(1.0))
    fun world() = 256.0 * density * 2.0.pow(zoom)
    fun screen(lat: Double, lon: Double): Offset {
        val wp = world()
        return Offset(((RadarProjection.x(lon) - cx) * wp + w / 2.0).toFloat(), ((RadarProjection.y(lat) - cy) * wp + h / 2.0).toFloat())
    }
    suspend fun flyTo(lat: Double, lon: Double, z: Double = zoom) {
        val fx = cx; val fy = cy; val fz = zoom
        val tx = RadarProjection.x(lon); val ty = RadarProjection.y(lat)
        animate(0f, 1f, animationSpec = spring(dampingRatio = .9f, stiffness = 220f)) { t, _ ->
            cx = fx + (tx - fx) * t; cy = fy + (ty - fy) * t; zoom = fz + (z - fz) * t
        }
    }
    // Fit you and the nearest places once the size is known.
    LaunchedEffect(w, h, pins.size) {
        if (fitted || w == 0 || h == 0) return@LaunchedEffect
        val pts = listOfNotNull(me) + pins.take(fit).map { it.lat to it.lon }
        if (pts.isEmpty()) return@LaunchedEffect
        val xs = pts.map { RadarProjection.x(it.second) }; val ys = pts.map { RadarProjection.y(it.first) }
        cx = (xs.min() + xs.max()) / 2; cy = (ys.min() + ys.max()) / 2
        val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-7); val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-7)
        val z = log2(minOf(w * .78 / (spanX * 256 * density), h * .72 / (spanY * 256 * density)))
        zoom = z.coerceIn(11.0, minOf(17.0, maxZoom))
        fitted = true
    }
    LaunchedEffect(selected) {
        val p = pins.firstOrNull { it.id == selected } ?: return@LaunchedEffect
        if (!fitted) return@LaunchedEffect
        val s = screen(p.lat, p.lon)
        if (s.x < w * .12f || s.x > w * .88f || s.y < h * .15f || s.y > h * .85f) flyTo(p.lat, p.lon)
    }
    // Ask for the tiles in view (sharp: one tile pixel per screen pixel), nearest the centre first.
    LaunchedEffect(drawn) {
        snapshotFlow { Triple(cx, cy, zoom) to (w to h) }.collect {
            if (w == 0 || h == 0) return@collect
            val want = drawn.flatMap { l ->
                if (zoom + log2(density.toDouble()) < l.minZ) emptyList() else visibleTiles(cx, cy, zoom, w, h, density, l.maxZ).map { l to it }
            }
            NlscTiles.wanted = want.map { (l, t) -> NlscTiles.key(l, t.first, t.second, t.third) }.toSet()
            want.forEach { (l, t) -> NlscTiles.request(ctx, l, t.first, t.second, t.third) }
        }
    }
    val reduceMotion = io.atmos.weather.ui.rememberAppleReduceMotion()
    val photo = style == NlscTiles.Style.HYBRID
    val bg = if (photo) Color(0xFF1F2A22) else if (dark) Color(0xFF1C2026) else Color(0xFFF2EFE9)
    val chosen = pins.firstOrNull { it.id == selected }
    Box(modifier.clip(RoundedCornerShape(22.dp)).border(.5.dp, Color.White.copy(alpha = if (dark) .14f else .5f), RoundedCornerShape(22.dp))
        .semantics { contentDescription = "地圖，${pins.size} 個地點" + (chosen?.let { "，已選 ${it.title}" } ?: "") }) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { w = it.width; h = it.height }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoomBy, _ ->
                    val before = world()
                    val ax = cx + (centroid.x - w / 2.0) / before; val ay = cy + (centroid.y - h / 2.0) / before
                    zoom = (zoom + ln(zoomBy.toDouble()) / ln(2.0)).coerceIn(7.0, maxZoom)
                    val after = world()
                    cx = (ax - (centroid.x - w / 2.0) / after - pan.x / after).coerceIn(0.0, 1.0)
                    cy = (ay - (centroid.y - h / 2.0) / after - pan.y / after).coerceIn(0.0, 1.0)
                }
            }
            .pointerInput(pins) {
                detectTapGestures(
                    onDoubleTap = { o ->
                        val wp = world()
                        val lon = RadarProjection.longitude(cx + (o.x - w / 2.0) / wp); val lat = RadarProjection.latitude(cy + (o.y - h / 2.0) / wp)
                        scope.launch { flyTo(lat, lon, (zoom + 1).coerceAtMost(maxZoom)) }
                    },
                    onTap = { o ->
                        if (panel) { panel = false; return@detectTapGestures }
                        val hit = pins.map { it to (screen(it.lat, it.lon) - o).getDistance() }.filter { it.second < 26 * density }.minByOrNull { it.second }
                        pick(hit?.first?.id)
                    },
                )
            }) {
            NlscTiles.version.intValue
            drawRect(bg)
            val wp = world()
            drawn.forEach { layer ->
            if (zoom + log2(density.toDouble()) < layer.minZ) return@forEach
            // Night: the street maps are inverted with their hues kept; photos and symbols stay as they are.
            val filter = if (dark && (layer == NlscTiles.Layer.MAP || layer == NlscTiles.Layer.TERRAIN)) NlscTiles.night else null
            visibleTiles(cx, cy, zoom, size.width.toInt(), size.height.toInt(), density, layer.maxZ).forEach { (z, x, y) ->
                val span = wp / (1 shl z)
                val left = (x * span - cx * wp + size.width / 2.0); val top = (y * span - cy * wp + size.height / 2.0)
                val dst = IntOffset(floor(left).toInt(), floor(top).toInt())
                val dsz = IntSize((floor(left + span) - floor(left)).toInt() + 1, (floor(top + span) - floor(top)).toInt() + 1)
                val img = NlscTiles.get(layer, z, x, y)
                if (img != null) { drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), dst, dsz, colorFilter = filter, filterQuality = FilterQuality.Medium); return@forEach }
                // Not here yet: stretch a coarser tile already in memory, so the map never goes blank.
                for (up in 1..4) {
                    val pz = z - up; if (pz < 0) break
                    val parent = NlscTiles.get(layer, pz, x shr up, y shr up) ?: continue
                    val part = parent.width / (1 shl up)
                    val sx = (x - ((x shr up) shl up)) * part; val sy = (y - ((y shr up) shl up)) * part
                    drawImage(parent, IntOffset(sx, sy), IntSize(part.coerceAtLeast(1), part.coerceAtLeast(1)), dst, dsz, colorFilter = filter, filterQuality = FilterQuality.Low)
                    break
                }
            }
            }
            // Places: a white-ringed dot each; the chosen one grows a label.
            pins.forEach { p ->
                if (p.id == selected) return@forEach
                val s = screen(p.lat, p.lon)
                if (s.x < -30 || s.y < -30 || s.x > size.width + 30 || s.y > size.height + 30) return@forEach
                drawCircle(Color.Black.copy(alpha = .25f), 9.5f * density, s + Offset(0f, 1.5f * density))
                drawCircle(Color.White, 9f * density, s); drawCircle(p.color, 6.5f * density, s)
            }
            me?.let { (la, lo) ->
                val s = screen(la, lo)
                drawCircle(Color.White, 9f * density, s); drawCircle(Color(0xFF0A84FF), 6.5f * density, s)
            }
            chosen?.let { p ->
                val s = screen(p.lat, p.lon)
                // A map pin: drop body with a white rim, the label in a capsule above.
                val r = 13f * density; val tip = Offset(s.x, s.y)
                val body = Path().apply {
                    moveTo(tip.x, tip.y); cubicTo(tip.x - r * .55f, tip.y - r * .9f, tip.x - r, tip.y - r * 1.35f, tip.x - r, tip.y - r * 2.05f)
                    cubicTo(tip.x - r, tip.y - r * 2.8f, tip.x + r, tip.y - r * 2.8f, tip.x + r, tip.y - r * 2.05f)
                    cubicTo(tip.x + r, tip.y - r * 1.35f, tip.x + r * .55f, tip.y - r * .9f, tip.x, tip.y); close()
                }
                drawPath(body, Color.Black.copy(alpha = .25f)); drawPath(body, p.color); drawPath(body, Color.White, style = Stroke(2f * density))
                drawCircle(Color.White, r * .38f, Offset(tip.x, tip.y - r * 2.05f))
                val text = measurer.measure(p.title.take(14), TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                val bw = text.size.width + 20f * density; val bh = text.size.height + 10f * density
                val bx = (tip.x - bw / 2).coerceIn(6f * density, size.width - bw - 6f * density); val by = (tip.y - r * 3.1f - bh).coerceAtLeast(4f * density)
                drawRoundRect(Color(0xE6202226), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
                drawText(text, topLeft = Offset(bx + 10f * density, by + 5f * density))
            }
            // Scale bar, bottom right.
            val metresPerPx = cos(Math.toRadians(RadarProjection.latitude(cy))) * 2 * PI * 6_378_137.0 / wp
            val nice = listOf(20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000).lastOrNull { it / metresPerPx <= 90 * density } ?: 20
            val len = (nice / metresPerPx).toFloat()
            val label = measurer.measure(if (nice >= 1000) "${nice / 1000} 公里" else "$nice 公尺", TextStyle(fontSize = 14.sp, color = if (dark || photo) Color.White else Color(0xFF1C1C1E)))
            val ink = if (dark || photo) Color.White else Color(0xFF1C1C1E)
            val bx = size.width - len - 14 * density; val by = size.height - 12 * density
            drawLine(ink, Offset(bx, by), Offset(bx + len, by), 2.5f * density)
            drawLine(ink, Offset(bx, by - 6 * density), Offset(bx, by), 2f * density); drawLine(ink, Offset(bx + len, by - 6 * density), Offset(bx + len, by), 2f * density)
            drawText(label, topLeft = Offset(bx + len - label.size.width, by - 8 * density - label.size.height))
        }
        // Your dot's halo pulses on its own layer, so the map itself is not redrawn every frame.
        if (me != null && !reduceMotion) {
            val pulse = rememberInfiniteTransition(label = "me").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
            Canvas(Modifier.fillMaxSize()) {
                val s = screen(me.first, me.second)
                drawCircle(Color(0xFF0A84FF).copy(alpha = .28f * (1f - pulse.value)), (10f + 22f * pulse.value) * density, s)
            }
        }
        // Apple-Maps style controls: one glass capsule (styles · your position), the style card under it.
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalAlignment = Alignment.End) {
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE61F2125)).border(.5.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(14.dp))) {
                MapButton(Icons.Rounded.Layers, "地圖樣式與圖層", panel) { panel = !panel }
                if (me != null) {
                    Box(Modifier.width(44.dp).height(.5.dp).background(Color.White.copy(alpha = .22f)))
                    MapButton(Icons.Rounded.NearMe, "回到我的位置") { panel = false; scope.launch { flyTo(me.first, me.second, zoom.coerceAtLeast(15.5)) } }
                }
            }
            androidx.compose.animation.AnimatedVisibility(panel, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = .9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = .9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f))) {
                StylePanel(style, overlays, { style = it }, { l -> overlays = if (l in overlays) overlays - l else overlays + l })
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .42f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text("© 國土測繪中心", color = Color.White, fontSize = 14.sp)
        }
    }
}

/** The style card: three looks to pick from, then the layers to lay over them. */
@Composable
private fun StylePanel(style: NlscTiles.Style, overlays: Set<NlscTiles.Layer>, onStyle: (NlscTiles.Style) -> Unit, onToggle: (NlscTiles.Layer) -> Unit) {
    Column(Modifier.padding(top = 8.dp).width(228.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xF0202226))
        .border(.5.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text("地圖樣式", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NlscTiles.Style.entries.forEach { s ->
                val on = s == style
                val swatch = when (s) {
                    NlscTiles.Style.STANDARD -> listOf(Color(0xFFF4EFE6), Color(0xFFFFD98A), Color(0xFFBFD9F2))
                    NlscTiles.Style.HYBRID -> listOf(Color(0xFF3F5B3A), Color(0xFF6E7F5B), Color(0xFF9AA58A))
                    NlscTiles.Style.TERRAIN -> listOf(Color(0xFFE9E4D2), Color(0xFFC9D8B0), Color(0xFFA7C08C))
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = s.label) { onStyle(s) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(swatch))
                        .border(if (on) 2.5.dp else .5.dp, if (on) Color(0xFF0A84FF) else Color.White.copy(alpha = .25f), RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(4.dp))
                    Text(s.label, color = if (on) Color(0xFF64B5FF) else Color.White.copy(alpha = .85f), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("圖層", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        listOf(Triple(NlscTiles.Layer.AED, Icons.Rounded.MonitorHeart, Color(0xFFFF375F)), Triple(NlscTiles.Layer.STORE, Icons.Rounded.LocalConvenienceStore, Color(0xFF30D158)),
            Triple(NlscTiles.Layer.HYDRANT, Icons.Rounded.FireHydrantAlt, Color(0xFFFF9F0A))).forEach { (l, icon, tint) ->
            val on = l in overlays
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = l.label) { onToggle(l) }.padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                io.atmos.weather.ui.IOSIconTile(icon, tint, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(l.label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(22.dp), tint = if (on) Color(0xFF0A84FF) else Color.White.copy(alpha = .4f))
            }
        }
        Text("靠近一點才會顯示", color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
    }
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (active) Color(0xFF64B5FF) else Color.White)
    }
}

/** Tiles covering the view at the zoom that gives one tile pixel per screen pixel (nearest first). */
internal fun visibleTiles(cx: Double, cy: Double, zoom: Double, w: Int, h: Int, density: Float, maxZ: Int): List<Triple<Int, Int, Int>> {
    if (w <= 0 || h <= 0) return emptyList()
    var z = (zoom + log2(density.toDouble().coerceAtLeast(1.0))).roundToInt().coerceIn(0, maxZ)
    while (true) {
        val wp = 256.0 * density * 2.0.pow(zoom); val n = 1 shl z; val span = wp / n
        val x0 = floor((cx * wp - w / 2.0) / span).toInt().coerceAtLeast(0); val x1 = floor((cx * wp + w / 2.0) / span).toInt().coerceAtMost(n - 1)
        val y0 = floor((cy * wp - h / 2.0) / span).toInt().coerceAtLeast(0); val y1 = floor((cy * wp + h / 2.0) / span).toInt().coerceAtMost(n - 1)
        val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
        if (count <= 64 || z == 0) {
            val mx = cx * n; val my = cy * n
            return (y0..y1).flatMap { y -> (x0..x1).map { x -> Triple(z, x, y) } }
                .sortedBy { (_, x, y) -> (x + .5 - mx) * (x + .5 - mx) + (y + .5 - my) * (y + .5 - my) }
        }
        z--
    }
}
