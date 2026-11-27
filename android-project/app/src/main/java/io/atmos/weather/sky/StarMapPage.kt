package io.atmos.weather.sky

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.IOS27RoundControl
import io.atmos.weather.ui.IOSColors
import io.atmos.weather.ui.IOSIconTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.SunPosition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Something the person can pick, find and point the phone at. */
internal sealed class SkyTarget(val label: String, val kind: String) {
    class StarT(val star: StarSky.Star) : SkyTarget(star.name.ifBlank { "恆星" }, "恆星")
    class PlanetT(val planet: StarSky.Planet) : SkyTarget(planet.label, "行星")
    class ConstellationT(val c: StarSky.Constellation) : SkyTarget(c.name, "星座")
    object Sun : SkyTarget("太陽", "恆星")
    object Moon : SkyTarget("月亮", "衛星")
}

/** The orientation of the view: three horizon-frame unit vectors (east, north, up components). */
internal class SkyView {
    val right = FloatArray(3); val up = FloatArray(3); val fwd = FloatArray(3)
    fun aim(az: Float, alt: Float) {
        val a = Math.toRadians(az.toDouble()); val h = Math.toRadians(alt.toDouble())
        fwd[0] = (cos(h) * sin(a)).toFloat(); fwd[1] = (cos(h) * cos(a)).toFloat(); fwd[2] = sin(h).toFloat()
        right[0] = cos(a).toFloat(); right[1] = (-sin(a)).toFloat(); right[2] = 0f
        cross(right, fwd, up)
    }
    /** Smoothly follows a new forward and up direction (from the phone's sensors). */
    fun follow(f: FloatArray, u: FloatArray, k: Float) {
        for (i in 0..2) { fwd[i] += (f[i] - fwd[i]) * k; up[i] += (u[i] - up[i]) * k }
        norm(fwd); cross(fwd, up, right); norm(right); cross(right, fwd, up)
    }
    fun azAlt(): Pair<Float, Float> = StarSky.azAlt(fwd[0].toDouble(), fwd[1].toDouble(), fwd[2].toDouble()).let { it.first.toFloat() to it.second.toFloat() }
    companion object {
        fun cross(a: FloatArray, b: FloatArray, o: FloatArray) {
            val x = a[1] * b[2] - a[2] * b[1]; val y = a[2] * b[0] - a[0] * b[2]; val z = a[0] * b[1] - a[1] * b[0]
            o[0] = x; o[1] = y; o[2] = z
        }
        fun norm(v: FloatArray) { val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f); v[0] /= l; v[1] /= l; v[2] /= l }
    }
}

/** Follows the phone with the rotation-vector sensor; returns false when the phone has none. */
@Composable
private fun rememberSkySensor(enabled: Boolean, onOrientation: (FloatArray, FloatArray) -> Unit): Boolean {
    val ctx = LocalContext.current
    val manager = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember { manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val latest by rememberUpdatedState(onOrientation)
    DisposableEffect(enabled, sensor) {
        if (!enabled || sensor == null || manager == null) return@DisposableEffect onDispose { }
        val r = FloatArray(9); val m = FloatArray(9); val f = FloatArray(3); val u = FloatArray(3)
        @Suppress("DEPRECATION")
        val display = runCatching { (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay }.getOrNull()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                val (ax, ay) = when (display?.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                val g = if (ax == SensorManager.AXIS_X) r else { SensorManager.remapCoordinateSystem(r, ax, ay, m); m }
                // Rows are east, north, up; columns the screen's right, up and out (the camera looks in).
                f[0] = -g[2]; f[1] = -g[5]; f[2] = -g[8]
                u[0] = g[1]; u[1] = g[4]; u[2] = g[7]
                latest(f, u)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return sensor != null
}

private val clock = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.TAIWAN)

/**
 * 星空與銀河: the live sky over where you are. Hold the phone up and the map follows it (gyroscope),
 * or drag and pinch by hand. Stars, constellations, the Milky Way, the planets, Sun and Moon; tap
 * anything for its name and where to look; search to be guided to it; move time to see tonight.
 */
@Composable
fun StarMapPage(city: WeatherCity, fixedTime: Instant? = null, startView: Pair<Float, Float> = 180f to 40f, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val lat = city.coordinate.latitude; val lon = city.coordinate.longitude
    val catalog by produceState<StarSky.Catalog?>(null) { value = withContext(Dispatchers.IO) { runCatching { StarSky.catalog(ctx) }.getOrNull() } }

    val view = remember { SkyView().apply { aim(startView.first, startView.second) } }
    var frame by remember { mutableIntStateOf(0) }          // bumped whenever the view moves; read only while drawing
    var fov by remember { mutableFloatStateOf(80f) }
    var gyro by remember { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(fixedTime?.toEpochMilli() ?: System.currentTimeMillis()) }
    var offsetMin by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<SkyTarget?>(null) }
    var searching by remember { mutableStateOf(false) }
    var showLines by remember { mutableStateOf(true) }
    var showNames by remember { mutableStateOf(true) }
    var showMilky by remember { mutableStateOf(true) }
    var showGrid by remember { mutableStateOf(false) }
    var red by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }

    val hasSensor = rememberSkySensor(gyro) { f, u ->
        view.follow(f, u, if (settled) .22f else 1f); settled = true; frame++
    }
    val following = gyro && hasSensor
    // Time moves on by itself while you look (once a second; the sky turns 15″ in that time).
    LaunchedEffect(fixedTime) { if (fixedTime == null) while (true) { delay(1000); now = System.currentTimeMillis() } }
    val at = Instant.ofEpochMilli(now).plusSeconds(offsetMin * 60L)
    val sky = remember(at.epochSecond / 10, catalog) { SkyMoment.of(at, lat, lon) }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("star-map")
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent { drawContent(); if (red) drawRect(Color(0xFFFF2A1A), blendMode = BlendMode.Multiply) }) {
        val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) } }
        val boldPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) } }
        val buffers = remember(catalog) { catalog?.let { SkyBuffers(it) } }
        Canvas(Modifier.fillMaxSize()
            .pointerInput(following) {
                detectTransformGestures { _, pan, zoom, _ ->
                    fov = (fov / zoom).coerceIn(12f, 120f)
                    if (!following && (pan.x != 0f || pan.y != 0f)) {
                        val perPx = fov / min(size.width, size.height)
                        val (az, alt) = view.azAlt()
                        view.aim(StarSky.norm360((az - pan.x * perPx).toDouble()).toFloat(), (alt + pan.y * perPx).coerceIn(-89f, 89f))
                    }
                    frame++
                }
            }
            .pointerInput(catalog, sky) {
                detectTapGestures { p -> catalog?.let { selected = pick(it, sky, view, fov, size.width.toFloat(), size.height.toFloat(), p, 34.dp.toPx()) } }
            }) {
            frame // read so the canvas redraws when the view moves
            val c = catalog; val b = buffers
            drawSky(sky, view, fov, c, b, selected, showLines, showNames, showMilky, showGrid, textPaint, boldPaint, density.density)
        }
        // Top: back, title and place, mode and search.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IOS27RoundControl("‹", onClose, fill = Color(0xFF1C1F2A), foreground = Color.White)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("星空與銀河", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("${city.name} · ${if (offsetMin == 0) "現在" else clock.format(at.atZone(ZoneId.of("Asia/Taipei")))}",
                        color = Color.White.copy(alpha = .7f), fontSize = 14.sp, maxLines = 1)
                }
                SkyRound(Icons.Rounded.Search, "搜尋星體") { searching = true }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkyChip(if (following) "陀螺儀對位" else if (hasSensor) "手動滑動" else "手動滑動（無陀螺儀）",
                    if (following) Icons.Rounded.ScreenRotation else Icons.Rounded.PanTool, following) {
                    if (hasSensor) { if (gyro) view.azAlt().let { (az, alt) -> view.aim(az, alt) }; gyro = !gyro; settled = false; frame++ }
                }
                SkyChip("星座連線", Icons.Rounded.Timeline, showLines) { showLines = !showLines; frame++ }
                SkyChip("名稱", Icons.Rounded.TextFields, showNames) { showNames = !showNames; frame++ }
                SkyChip("銀河", Icons.Rounded.BlurOn, showMilky) { showMilky = !showMilky; frame++ }
                SkyChip("方位格線", Icons.Rounded.Grid4x4, showGrid) { showGrid = !showGrid; frame++ }
                SkyChip("夜視紅光", Icons.Rounded.Nightlight, red) { red = !red }
            }
        }
        if (catalog == null) Text("正在載入星表…", color = Color.White.copy(alpha = .8f), fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
        // Bottom: what is picked, tonight's sights, and time.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            selected?.let { t -> TargetCard(t, sky, catalog, following, onPoint = {
                val v = sky.horizonOf(t, catalog); if (v != null) { val (az, alt) = StarSky.azAlt(v[0], v[1], v[2]); if (!following) view.aim(az.toFloat(), alt.toFloat()); frame++ }
            }, onClose = { selected = null; frame++ }) }
            if (selected == null) Tonight(sky) { t ->
                selected = t
                val v = sky.horizonOf(t, catalog)
                if (v != null && !following) { val (az, alt) = StarSky.azAlt(v[0], v[1], v[2]); view.aim(az.toFloat(), alt.toFloat()) }
                frame++
            }
            TimeBar(offsetMin, { offsetMin = it; frame++ })
        }
        if (searching) SearchPanel(catalog, onPick = { t ->
            searching = false; selected = t
            val v = sky.horizonOf(t, catalog)
            if (v != null && !following) { val (az, alt) = StarSky.azAlt(v[0], v[1], v[2]); view.aim(az.toFloat(), alt.toFloat()) }
            frame++
        }, onClose = { searching = false })
    }
}

// ------------------------------------------------------------------ the moment: planets, Sun, Moon

/** Everything that moves, computed once per ten seconds of sky time. */
internal class SkyMoment(val at: Instant, val lat: Double, val lon: Double, val toHorizon: DoubleArray,
    val planets: List<Pair<StarSky.Planet, Pair<DoubleArray, Double>>>, val sun: DoubleArray, val moon: DoubleArray,
    val moonFraction: Double, val moonPhase: Double, val sunAlt: Double) {
    fun horizon(v: DoubleArray) = StarSky.apply(toHorizon, v[0], v[1], v[2])
    fun horizonOf(t: SkyTarget, c: StarSky.Catalog?): DoubleArray? = when (t) {
        is SkyTarget.StarT -> horizon(doubleArrayOf(t.star.x.toDouble(), t.star.y.toDouble(), t.star.z.toDouble()))
        is SkyTarget.PlanetT -> planets.firstOrNull { it.first == t.planet }?.let { horizon(it.second.first) }
        is SkyTarget.ConstellationT -> horizon(doubleArrayOf(t.c.x.toDouble(), t.c.y.toDouble(), t.c.z.toDouble()))
        SkyTarget.Sun -> sun
        SkyTarget.Moon -> moon
    }
    companion object {
        fun of(at: Instant, lat: Double, lon: Double): SkyMoment {
            val m = StarSky.skyToHorizon(at, lat, lon)
            val planets = StarSky.Planet.entries.map { it to StarSky.planet(it, at) }
            val s = runCatching { SunPosition.compute().on(at).at(lat, lon).execute() }.getOrNull()
            val mo = runCatching { MoonPosition.compute().on(at).at(lat, lon).execute() }.getOrNull()
            val il = runCatching { MoonIllumination.compute().on(at).at(lat, lon).execute() }.getOrNull()
            return SkyMoment(at, lat, lon, m, planets,
                StarSky.fromAzAlt(s?.azimuth ?: 0.0, s?.trueAltitude ?: -90.0), StarSky.fromAzAlt(mo?.azimuth ?: 0.0, mo?.altitude ?: -90.0),
                il?.fraction ?: 0.0, il?.phase ?: 0.0, s?.trueAltitude ?: -90.0)
        }
    }
}

// ------------------------------------------------------------------ drawing

/** Reused per-frame buffers so drawing allocates nothing. */
internal class SkyBuffers(c: StarSky.Catalog) {
    val bucket = Array(28) { FloatArray(c.stars.size * 2) }
    val count = IntArray(28)
    class Glow(val w: Int, val h: Int) {
        val acc = FloatArray(w * h); val pixels = IntArray(w * h)
        val bitmap: android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        var kernel = FloatArray(0)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    }
    private var glow: Glow? = null
    fun glow(w: Float, h: Float): Glow {
        val gw = (w / GLOW).toInt().coerceAtLeast(1); val gh = (h / GLOW).toInt().coerceAtLeast(1)
        return glow?.takeIf { it.w == gw && it.h == gh } ?: Glow(gw, gh).also { glow = it }
    }
    companion object { const val GLOW = 6f }
    val lines = FloatArray(c.figures.sumOf { it.segments.size } / 3 * 2 + 8)
    val ring = FloatArray(2 * 121)
    val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = android.graphics.Path()
}

private val STAR_COLORS = intArrayOf(0xFFB5CCFF.toInt(), 0xFFF4F7FF.toInt(), 0xFFFFE9C2.toInt(), 0xFFFFC08A.toInt())
private fun colorBucket(bv: Float) = when { bv < .1f -> 0; bv < .55f -> 1; bv < 1.1f -> 2; else -> 3 }
private fun magBucket(m: Float) = when { m < 0f -> 0; m < 1f -> 1; m < 2f -> 2; m < 3f -> 3; m < 4f -> 4; m < 5f -> 5; else -> 6 }

/** Stereographic projection of a camera-frame vector: screen x, y, or null behind the viewer. */
private inline fun project(x: Float, y: Float, z: Float, cx: Float, cy: Float, f: Float, limit: Float = -.2f, out: (Float, Float) -> Unit): Boolean {
    if (z < limit) return false
    val k = 2f / (1f + z)
    out(cx + f * k * x, cy - f * k * y); return true
}

private fun DrawScope.drawSky(sky: SkyMoment, view: SkyView, fov: Float, c: StarSky.Catalog?, b: SkyBuffers?, selected: SkyTarget?,
    showLines: Boolean, showNames: Boolean, showMilky: Boolean, showGrid: Boolean, text: Paint, bold: Paint, dp: Float) {
    val w = size.width; val h = size.height; val cx = w / 2; val cy = h / 2
    val f = (min(w, h) / 2f) / (2f * tan(Math.toRadians(fov / 4.0)).toFloat())
    // Sky colour from the Sun's height: day blue, twilight, then deep night.
    val sunAlt = sky.sunAlt
    val night = ((-sunAlt - 2) / 12).coerceIn(0.0, 1.0).toFloat()     // 0 by day, 1 once it is properly dark
    val top = lerp(Color(0xFF2F6FD6), Color(0xFF03050C), night); val low = lerp(Color(0xFF9CC8F2), Color(0xFF0E1A33), night)
    drawRect(Brush.verticalGradient(listOf(top, low)))
    val canvas = drawContext.canvas.nativeCanvas
    // One matrix: J2000 sky → camera (right, up, forward).
    val m = sky.toHorizon
    val r = view.right; val u = view.up; val fw = view.fwd
    fun row(v: FloatArray) = floatArrayOf(
        (v[0] * m[0] + v[1] * m[3] + v[2] * m[6]).toFloat(), (v[0] * m[1] + v[1] * m[4] + v[2] * m[7]).toFloat(), (v[0] * m[2] + v[1] * m[5] + v[2] * m[8]).toFloat())
    val R = row(r); val U = row(u); val F = row(fw)
    // (Labels and the few moving bodies use these small helpers; the thousands of stars are inlined above.)
    fun local(x: Float, y: Float, z: Float, o: (Float, Float, Float) -> Unit) =
        o(R[0] * x + R[1] * y + R[2] * z, U[0] * x + U[1] * y + U[2] * z, F[0] * x + F[1] * y + F[2] * z)
    fun localH(v: DoubleArray, o: (Float, Float, Float) -> Unit) {
        val x = v[0].toFloat(); val y = v[1].toFloat(); val z = v[2].toFloat()
        o(r[0] * x + r[1] * y + r[2] * z, u[0] * x + u[1] * y + u[2] * z, fw[0] * x + fw[1] * y + fw[2] * z)
    }
    val pxPerDeg = f * 2f * tan(Math.toRadians(.5)).toFloat()
    val visibility = (.25f + .75f * night)
    if (c != null && b != null) {
        // The Milky Way: each sample is splatted as a soft Gaussian into a small glow image (1/6 of
        // the screen), which is then drawn stretched and filtered, so the band reads as smooth light.
        if (showMilky && night > .05f) {
            val g = b.glow(w, h)
            val acc = g.acc; acc.fill(0f)
            val scale = 1f / SkyBuffers.GLOW
            val sigma = (pxPerDeg * 1.25f * scale).coerceIn(.8f, 14f)
            val rad = (sigma * 2.4f).toInt().coerceAtLeast(1)
            val kw = rad * 2 + 1
            if (g.kernel.size < kw * kw) g.kernel = FloatArray(kw * kw)
            for (ky in -rad..rad) for (kx in -rad..rad) g.kernel[(ky + rad) * kw + kx + rad] = kotlin.math.exp(-(kx * kx + ky * ky) / (2f * sigma * sigma))
            val weight = floatArrayOf(.16f, .26f, .36f, .46f, .56f)
            val xyz = c.milkyWay.xyz
            for (i in c.milkyWay.level.indices) {
                val x = xyz[i * 3]; val y = xyz[i * 3 + 1]; val z = xyz[i * 3 + 2]
                val lz = F[0] * x + F[1] * y + F[2] * z
                if (lz < -.1f) continue
                val k = 2f / (1f + lz)
                val gx = ((cx + f * k * (R[0] * x + R[1] * y + R[2] * z)) * scale).roundToInt()
                val gy = ((cy - f * k * (U[0] * x + U[1] * y + U[2] * z)) * scale).roundToInt()
                if (gx < -rad || gy < -rad || gx >= g.w + rad || gy >= g.h + rad) continue
                val wt = weight[c.milkyWay.level[i] - 1] / (sigma * sigma * .9f)
                for (ky in -rad..rad) {
                    val yy = gy + ky; if (yy < 0 || yy >= g.h) continue
                    val row = yy * g.w; val krow = (ky + rad) * kw + rad
                    for (kx in -rad..rad) { val xx = gx + kx; if (xx in 0 until g.w) acc[row + xx] += wt * g.kernel[krow + kx] }
                }
            }
            val peak = night * 150f
            for (p in acc.indices) {
                val a = (1f - kotlin.math.exp(-acc[p] * 1.6f)) * peak
                g.pixels[p] = (a.toInt().coerceIn(0, 255) shl 24) or 0xC8D6FF
            }
            g.bitmap.setPixels(g.pixels, 0, g.w, 0, 0, g.w, g.h)
            canvas.drawBitmap(g.bitmap, null, android.graphics.RectF(0f, 0f, w, h), g.paint)
        }
        if (showGrid) drawGrid(canvas, b, view, cx, cy, f, dp)
        // Constellation stick figures.
        if (showLines) {
            var n = 0
            val sel = (selected as? SkyTarget.ConstellationT)?.c?.id
            c.figures.forEach { fig ->
                val s = fig.segments
                var i = 0
                while (i + 5 < s.size) {
                    val z1 = F[0] * s[i] + F[1] * s[i + 1] + F[2] * s[i + 2]
                    val z2 = F[0] * s[i + 3] + F[1] * s[i + 4] + F[2] * s[i + 5]
                    if (z1 > .05f && z2 > .05f) {
                        val k1 = 2f / (1f + z1); val k2 = 2f / (1f + z2)
                        val ax = cx + f * k1 * (R[0] * s[i] + R[1] * s[i + 1] + R[2] * s[i + 2]); val ay = cy - f * k1 * (U[0] * s[i] + U[1] * s[i + 1] + U[2] * s[i + 2])
                        val px = cx + f * k2 * (R[0] * s[i + 3] + R[1] * s[i + 4] + R[2] * s[i + 5]); val py = cy - f * k2 * (U[0] * s[i + 3] + U[1] * s[i + 4] + U[2] * s[i + 5])
                        if (fig.id == sel) { b.line.color = 0xCCFFD479.toInt(); b.line.strokeWidth = 2.2f * dp; canvas.drawLine(ax, ay, px, py, b.line) }
                        else if (n + 4 <= b.lines.size) { b.lines[n] = ax; b.lines[n + 1] = ay; b.lines[n + 2] = px; b.lines[n + 3] = py; n += 4 }
                    }
                    i += 6
                }
            }
            b.line.color = android.graphics.Color.argb((40 + 90 * night).roundToInt(), 140, 175, 255); b.line.strokeWidth = 1.2f * dp
            if (n > 0) canvas.drawLines(b.lines, 0, n, b.line)
        }
        // Stars, sorted into size and colour buckets: 28 draw calls for 5,000 stars.
        val limit = (if (fov > 90) 5.2f else if (fov > 50) 5.7f else 6.2f) - (1 - night) * 3.2f
        b.count.fill(0)
        val zoom = sqrt(70f / fov).coerceIn(.85f, 2.2f)
        for (s in c.stars) {
            if (s.mag > limit) break // the list is sorted brightest first
            val lz = F[0] * s.x + F[1] * s.y + F[2] * s.z
            if (lz < 0f) continue
            val kk = 2f / (1f + lz)
            val sx = cx + f * kk * (R[0] * s.x + R[1] * s.y + R[2] * s.z); val sy = cy - f * kk * (U[0] * s.x + U[1] * s.y + U[2] * s.z)
            if (sx > -10 && sy > -10 && sx < w + 10 && sy < h + 10) {
                val k = magBucket(s.mag) * 4 + colorBucket(s.bv); val n = b.count[k]
                b.bucket[k][n] = sx; b.bucket[k][n + 1] = sy; b.count[k] = n + 2
            }
        }
        for (k in 0 until 28) if (b.count[k] > 0) {
            val mb = k / 4
            val radius = (floatArrayOf(4.6f, 3.6f, 2.8f, 2.1f, 1.6f, 1.2f, .9f)[mb]) * dp * zoom
            b.point.strokeWidth = radius * 2
            val alpha = (floatArrayOf(1f, 1f, .95f, .9f, .8f, .65f, .5f)[mb] * visibility * 255).roundToInt()
            b.point.color = (STAR_COLORS[k % 4] and 0xFFFFFF) or (alpha shl 24)
            canvas.drawPoints(b.bucket[k], 0, b.count[k], b.point)
            if (mb <= 1) { // a soft halo round the brightest
                b.point.strokeWidth = radius * 5; b.point.color = (STAR_COLORS[k % 4] and 0xFFFFFF) or ((alpha * .16f).roundToInt() shl 24)
                canvas.drawPoints(b.bucket[k], 0, b.count[k], b.point)
            }
        }
        // Names: constellations (more as you zoom in), then bright stars.
        if (showNames) {
            text.textSize = 14f * dp * 1.05f; text.textAlign = Paint.Align.CENTER
            text.color = android.graphics.Color.argb((120 + 80 * night).roundToInt(), 175, 195, 255)
            val rank = if (fov > 75) 1 else if (fov > 40) 2 else 3
            c.constellations.forEach { k ->
                if (k.rank <= rank) local(k.x, k.y, k.z) { x, y, z -> project(x, y, z, cx, cy, f, .2f) { sx, sy -> canvas.drawText(k.name, sx, sy, text) } }
            }
            text.textAlign = Paint.Align.LEFT; text.color = android.graphics.Color.argb((180 + 60 * night).roundToInt(), 255, 255, 255)
            val nameLimit = if (fov > 80) 1.0f else if (fov > 40) 2.0f else 3.2f
            for (s in c.stars) {
                if (s.mag > nameLimit) break
                if (s.name.isNotEmpty()) local(s.x, s.y, s.z) { x, y, z -> project(x, y, z, cx, cy, f, .05f) { sx, sy ->
                    canvas.drawText(s.name, sx + 7 * dp, sy - 5 * dp, text) } }
            }
        }
    }
    // Planets.
    sky.planets.forEach { (p, pos) ->
        val v = sky.horizon(pos.first)
        localH(v) { x, y, z -> project(x, y, z, cx, cy, f, .0f) { sx, sy ->
            b?.fill?.let { paint ->
                paint.color = (p.color and 0xFFFFFF) or (0x40 shl 24); canvas.drawCircle(sx, sy, 9f * dp, paint)
                paint.color = p.color; canvas.drawCircle(sx, sy, (if (p == StarSky.Planet.URANUS || p == StarSky.Planet.NEPTUNE) 3f else 5f) * dp, paint)
            }
            bold.textSize = 15f * dp; bold.textAlign = Paint.Align.LEFT; bold.color = p.color
            canvas.drawText(p.label, sx + 12 * dp, sy + 5 * dp, bold)
        } }
    }
    // The Moon, lit on the side facing the Sun, and the Sun.
    var sunX = 0f; var sunY = 0f; var sunOn = false
    localH(sky.sun) { x, y, z -> sunOn = project(x, y, z, cx, cy, f, -.6f) { sx, sy -> sunX = sx; sunY = sy } }
    localH(sky.moon) { x, y, z -> project(x, y, z, cx, cy, f, .0f) { sx, sy ->
        val rad = max(10f * dp, pxPerDeg * .26f)
        drawMoon(canvas, b, sx, sy, rad, sky.moonFraction, if (sunOn) kotlin.math.atan2(sunY - sy, sunX - sx) else 0f)
        bold.textSize = 15f * dp; bold.textAlign = Paint.Align.LEFT; bold.color = 0xFFF2F2F2.toInt()
        canvas.drawText("月亮", sx + rad + 6 * dp, sy + 5 * dp, bold)
    } }
    localH(sky.sun) { x, y, z -> project(x, y, z, cx, cy, f, .0f) { sx, sy ->
        b?.fill?.let { paint ->
            paint.shader = android.graphics.RadialGradient(sx, sy, 46f * dp, intArrayOf(0x99FFE28A.toInt(), 0x00FFE28A), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(sx, sy, 46f * dp, paint); paint.shader = null
            paint.color = 0xFFFFE9A8.toInt(); canvas.drawCircle(sx, sy, max(9f * dp, pxPerDeg * .27f), paint)
        }
        bold.textSize = 15f * dp; bold.textAlign = Paint.Align.LEFT; bold.color = 0xFFFFE9A8.toInt()
        canvas.drawText("太陽", sx + 16 * dp, sy + 5 * dp, bold)
    } }
    if (b != null) drawGround(canvas, b, view, cx, cy, f, night, bold, dp)
    // The picked object: a ring, or an arrow at the edge pointing the way when it is off screen.
    if (selected != null) sky.horizonOf(selected, c)?.let { v ->
        val x = (r[0] * v[0] + r[1] * v[1] + r[2] * v[2]).toFloat(); val y = (u[0] * v[0] + u[1] * v[1] + u[2] * v[2]).toFloat()
        val z = (fw[0] * v[0] + fw[1] * v[1] + fw[2] * v[2]).toFloat()
        var sx = -1f; var sy = -1f
        val front = project(x, y, z, cx, cy, f, .0f) { px, py -> sx = px; sy = py }
        if (front && sx in 0f..w && sy in 0f..h) {
            b?.line?.let { p -> p.color = 0xFFFFD479.toInt(); p.strokeWidth = 2f * dp; canvas.drawCircle(sx, sy, 20f * dp, p) }
        } else b?.let { bb ->
            val ang = kotlin.math.atan2(-y, x)
            val ex = cx + cos(ang) * (w / 2 - 40 * dp); val ey = cy + sin(ang) * (h / 2 - 150 * dp).coerceAtLeast(40 * dp)
            val p = bb.fill; p.color = 0xFFFFD479.toInt()
            val path = bb.path; path.reset()
            path.moveTo(ex + cos(ang) * 18 * dp, ey + sin(ang) * 18 * dp)
            path.lineTo(ex + cos(ang + 2.5f) * 14 * dp, ey + sin(ang + 2.5f) * 14 * dp)
            path.lineTo(ex + cos(ang - 2.5f) * 14 * dp, ey + sin(ang - 2.5f) * 14 * dp); path.close()
            canvas.drawPath(path, p)
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)

private fun drawMoon(canvas: android.graphics.Canvas, b: SkyBuffers?, x: Float, y: Float, r: Float, fraction: Double, towardsSun: Float) {
    val p = b?.fill ?: return
    p.color = 0x33FFFFFF; canvas.drawCircle(x, y, r * 1.8f, p)
    p.color = 0xFF3A3D46.toInt(); canvas.drawCircle(x, y, r, p)
    // The lit part: half the disc towards the Sun plus (or minus) an ellipse for the terminator.
    canvas.save(); canvas.rotate(Math.toDegrees(towardsSun.toDouble()).toFloat(), x, y)
    val path = b.path; path.reset()
    path.addArc(x - r, y - r, x + r, y + r, -90f, 180f)
    val k = (1 - 2 * fraction).toFloat() * r
    path.arcTo(x - abs(k), y - r, x + abs(k), y + r, 90f, if (k > 0) -180f else 180f, false)
    path.close()
    p.color = 0xFFF4F1E6.toInt(); canvas.drawPath(path, p)
    canvas.restore()
}

/** The ground below the horizon, the horizon line and the compass points on it. */
private fun drawGround(canvas: android.graphics.Canvas, b: SkyBuffers, view: SkyView, cx: Float, cy: Float, f: Float, night: Float, label: Paint, dp: Float) {
    val r = view.right; val u = view.up; val fw = view.fwd
    val path = b.path; path.reset()
    for (i in 0..120) {
        val a = Math.toRadians(i * 3.0); val e = sin(a).toFloat(); val n = cos(a).toFloat()
        var x = r[0] * e + r[1] * n; var y = u[0] * e + u[1] * n; var z = fw[0] * e + fw[1] * n
        if (z < -.85f) { val s = sqrt((1 - .85f * .85f) / (x * x + y * y).coerceAtLeast(1e-6f)); x *= s; y *= s; z = -.85f }
        val k = 2f / (1f + z); val sx = cx + f * k * x; val sy = cy - f * k * y
        if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
    }
    path.close()
    // Which side is the ground? The side holding the point straight down (when it can be drawn).
    val nz = -fw[2]
    val inside = if (nz < -.85f) false else {
        val k = 2f / (1f + nz); val nx = cx + f * k * (-r[2]); val ny = cy - f * k * (-u[2])
        val region = android.graphics.RectF(); path.computeBounds(region, true)
        val clip = android.graphics.Region(-100000, -100000, 100000, 100000)
        android.graphics.Region().apply { setPath(path, clip) }.contains(nx.roundToInt(), ny.roundToInt())
    }
    path.fillType = if (inside) android.graphics.Path.FillType.WINDING else android.graphics.Path.FillType.INVERSE_WINDING
    b.fill.color = android.graphics.Color.argb(235, (14 + 40 * (1 - night)).roundToInt(), (22 + 50 * (1 - night)).roundToInt(), (20 + 30 * (1 - night)).roundToInt())
    canvas.drawPath(path, b.fill)
    path.fillType = android.graphics.Path.FillType.WINDING
    b.line.color = 0x9970C890.toInt(); b.line.strokeWidth = 1.5f * dp
    canvas.drawPath(path, b.line)
    label.textSize = 17f * dp; label.textAlign = Paint.Align.CENTER
    listOf(0 to "北", 45 to "東北", 90 to "東", 135 to "東南", 180 to "南", 225 to "西南", 270 to "西", 315 to "西北").forEach { (az, name) ->
        val a = Math.toRadians(az.toDouble()); val e = sin(a).toFloat(); val n = cos(a).toFloat()
        val x = r[0] * e + r[1] * n; val y = u[0] * e + u[1] * n; val z = fw[0] * e + fw[1] * n
        project(x, y, z, cx, cy, f, .05f) { sx, sy ->
            label.color = if (az == 0) 0xFFFF6B5E.toInt() else 0xFFE8F3EA.toInt()
            canvas.drawText(name, sx, sy - 8 * dp, label)
        }
    }
}

/** Altitude rings every 30° and azimuth spokes every 45°. */
private fun drawGrid(canvas: android.graphics.Canvas, b: SkyBuffers, view: SkyView, cx: Float, cy: Float, f: Float, dp: Float) {
    val r = view.right; val u = view.up; val fw = view.fwd
    b.line.color = 0x3399CCFF; b.line.strokeWidth = 1f * dp
    fun polyline(points: List<DoubleArray>) {
        var lx = 0f; var ly = 0f; var has = false
        points.forEach { v ->
            val x = (r[0] * v[0] + r[1] * v[1] + r[2] * v[2]).toFloat(); val y = (u[0] * v[0] + u[1] * v[1] + u[2] * v[2]).toFloat()
            val z = (fw[0] * v[0] + fw[1] * v[1] + fw[2] * v[2]).toFloat()
            val ok = project(x, y, z, cx, cy, f, .0f) { sx, sy -> if (has) canvas.drawLine(lx, ly, sx, sy, b.line); lx = sx; ly = sy }
            has = ok
        }
    }
    for (alt in listOf(30, 60)) polyline((0..72).map { StarSky.fromAzAlt(it * 5.0, alt.toDouble()) })
    for (az in 0 until 360 step 45) polyline((0..18).map { StarSky.fromAzAlt(az.toDouble(), it * 5.0) })
}

/** The object nearest a tap (within [radiusPx] on screen). */
private fun pick(c: StarSky.Catalog, sky: SkyMoment, view: SkyView, fov: Float, w: Float, h: Float, p: Offset, radiusPx: Float): SkyTarget? {
    val f = (min(w, h) / 2f) / (2f * tan(Math.toRadians(fov / 4.0)).toFloat())
    val dx = p.x - w / 2; val dy = p.y - h / 2; val rr = sqrt(dx * dx + dy * dy)
    val theta = 2 * atan(rr / (2 * f))
    val dir = FloatArray(3)
    for (i in 0..2) dir[i] = cos(theta) * view.fwd[i] + if (rr > 0) sin(theta) * (dx / rr * view.right[i] - dy / rr * view.up[i]) else 0f
    val tolerance = cos(2 * atan(radiusPx / (2 * f)))
    var best: SkyTarget? = null; var bestDot = tolerance
    fun consider(t: SkyTarget, v: DoubleArray, bonus: Float = 0f) {
        val d = (dir[0] * v[0] + dir[1] * v[1] + dir[2] * v[2]).toFloat() + bonus
        if (d > bestDot) { bestDot = d; best = t }
    }
    consider(SkyTarget.Sun, sky.sun, 2e-4f); consider(SkyTarget.Moon, sky.moon, 2e-4f)
    sky.planets.forEach { (pl, pos) -> consider(SkyTarget.PlanetT(pl), sky.horizon(pos.first), 1e-4f) }
    for (s in c.stars) { if (s.mag > 5f) break; consider(SkyTarget.StarT(s), sky.horizon(doubleArrayOf(s.x.toDouble(), s.y.toDouble(), s.z.toDouble()))) }
    if (best == null) {
        // Nothing close: name the constellation the tap fell in (nearest label within 12°).
        var bc: StarSky.Constellation? = null; var bd = cos(Math.toRadians(12.0)).toFloat()
        c.constellations.forEach { k -> val v = sky.horizon(doubleArrayOf(k.x.toDouble(), k.y.toDouble(), k.z.toDouble()))
            val d = (dir[0] * v[0] + dir[1] * v[1] + dir[2] * v[2]).toFloat(); if (d > bd) { bd = d; bc = k } }
        bc?.let { best = SkyTarget.ConstellationT(it) }
    }
    return best
}

// ------------------------------------------------------------------ controls

@Composable
private fun SkyRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF1C1F2A).copy(alpha = .85f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp), tint = Color.White) }
}

@Composable
private fun SkyChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Color.White.copy(alpha = .92f) else Color(0xFF1C1F2A).copy(alpha = .82f))
        .clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(17.dp), tint = if (on) Color(0xFF0B1020) else Color.White)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (on) Color(0xFF0B1020) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private val panel = Color(0xFF141824).copy(alpha = .92f)

@Composable
private fun TargetCard(t: SkyTarget, sky: SkyMoment, c: StarSky.Catalog?, following: Boolean, onPoint: () -> Unit, onClose: () -> Unit) {
    val v = sky.horizonOf(t, c) ?: return
    val (az, alt) = StarSky.azAlt(v[0], v[1], v[2])
    val (icon, color) = when (t) {
        is SkyTarget.PlanetT -> Icons.Rounded.Public to Color(t.planet.color)
        is SkyTarget.ConstellationT -> Icons.Rounded.Timeline to IOSColors.indigo
        SkyTarget.Sun -> Icons.Rounded.WbSunny to IOSColors.orange
        SkyTarget.Moon -> Icons.Rounded.DarkMode to IOSColors.gray
        else -> Icons.Rounded.AutoAwesome to IOSColors.blue
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IOSIconTile(icon, color, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val sub = when (t) {
                    is SkyTarget.StarT -> listOfNotNull("恆星", c?.byId?.get(t.star.constellation)?.name).joinToString(" · ")
                    is SkyTarget.PlanetT -> "行星"
                    is SkyTarget.ConstellationT -> "星座"
                    SkyTarget.Moon -> "月亮 · ${SkyEphemeris.phaseName(sky.moonPhase)}"
                    SkyTarget.Sun -> "太陽 · 請勿直視"
                }
                Text(sub, color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(onClickLabel = "關閉", onClick = onClose),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "關閉", Modifier.size(18.dp), tint = Color.White) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val stats = buildList {
                add(StarSky.compass(az) to "方位 ${az.roundToInt()}°")
                add("${alt.roundToInt()}°" to if (alt >= 0) "高度" else "在地平線下")
                when (t) {
                    is SkyTarget.StarT -> add(String.format(Locale.US, "%.1f", t.star.mag) to "星等")
                    is SkyTarget.PlanetT -> sky.planets.firstOrNull { it.first == t.planet }?.let { add(String.format(Locale.US, "%.1f 億", it.second.second * 1.496) to "距離（公里）") }
                    SkyTarget.Moon -> add("${(sky.moonFraction * 100).roundToInt()}%" to "被照亮")
                    else -> {}
                }
            }
            stats.forEach { (value, label) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .08f)).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(label, color = Color.White.copy(alpha = .65f), fontSize = 14.sp, maxLines = 1)
                }
            }
        }
        Text(if (following) "跟著畫面邊緣的黃色箭頭轉動手機，就能找到它。" else "已移到畫面中央；也可以切換成陀螺儀，舉起手機對準天空。",
            color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
        if (!following) Text("置中顯示", color = Color(0xFFFFD479), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).clickable(onClick = onPoint).padding(vertical = 4.dp))
    }
}

/** 今晚看點: what is up now, brightest first, as chips to tap. */
@Composable
private fun Tonight(sky: SkyMoment, onPick: (SkyTarget) -> Unit) {
    val up = buildList {
        if (StarSky.azAlt(sky.moon[0], sky.moon[1], sky.moon[2]).second > 0) add(SkyTarget.Moon)
        sky.planets.filter { (p, pos) -> p != StarSky.Planet.NEPTUNE && p != StarSky.Planet.URANUS && sky.horizon(pos.first)[2] > 0 }
            .forEach { add(SkyTarget.PlanetT(it.first)) }
        if (sky.sunAlt > 0) add(SkyTarget.Sun)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IOSIconTile(Icons.Rounded.AutoAwesome, IOSColors.indigo, 30.dp); Spacer(Modifier.width(9.dp))
            Text(if (sky.sunAlt > -6) "現在天空" else "今晚看點", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(when { sky.sunAlt > 0 -> "白天 · 星星被陽光蓋過"; sky.sunAlt > -12 -> "暮光 · 亮星先出現"; else -> "天色已暗" },
                color = Color.White.copy(alpha = .65f), fontSize = 14.sp)
        }
        if (up.isEmpty()) Text("目前沒有行星在地平線上，試試把時間往後調。", color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
        else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            up.forEach { t ->
                val dot = when (t) { is SkyTarget.PlanetT -> Color(t.planet.color); SkyTarget.Sun -> Color(0xFFFFE9A8); else -> Color(0xFFF4F1E6) }
                Row(Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .1f)).clickable(onClickLabel = t.label) { onPick(t) }
                    .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(7.dp))
                    Text(t.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TimeBar(offsetMin: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)).background(panel).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SkyRound(Icons.Rounded.FastRewind, "往前一小時") { onChange((offsetMin - 60).coerceAtLeast(-24 * 60)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val h = offsetMin / 60; val m = abs(offsetMin % 60)
            Text(if (offsetMin == 0) "現在" else "${if (offsetMin > 0) "+" else "−"}${abs(h)} 小時${if (m > 0) " $m 分" else ""}",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(if (offsetMin == 0) "時間軸 · 看看稍後的星空" else "點一下回到現在", color = Color.White.copy(alpha = .6f), fontSize = 14.sp,
                modifier = Modifier.clickable(enabled = offsetMin != 0, onClickLabel = "回到現在") { onChange(0) })
        }
        SkyRound(Icons.Rounded.FastForward, "往後一小時") { onChange((offsetMin + 60).coerceAtMost(24 * 60)) }
    }
}

@Composable
private fun SearchPanel(c: StarSky.Catalog?, onPick: (SkyTarget) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember(c) {
        buildList<SkyTarget> {
            add(SkyTarget.Moon); add(SkyTarget.Sun)
            StarSky.Planet.entries.forEach { add(SkyTarget.PlanetT(it)) }
            c?.stars?.filter { it.name.isNotEmpty() }?.forEach { add(SkyTarget.StarT(it)) }
            c?.constellations?.sortedBy { it.rank }?.forEach { add(SkyTarget.ConstellationT(it)) }
        }
    }
    val shown = remember(query, all) { if (query.isBlank()) all else all.filter { it.label.contains(query.trim()) } }
    Column(Modifier.fillMaxSize().background(Color(0xF20A0D16)).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = .7f)); Spacer(Modifier.width(8.dp))
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                    cursorBrush = SolidColor(Color.White), modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (query.isEmpty()) Text("找行星、亮星或星座", color = Color.White.copy(alpha = .45f), fontSize = 17.sp); inner() })
            }
            Spacer(Modifier.width(12.dp))
            Text("完成", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { "${it.kind}-${it.label}-${(it as? SkyTarget.StarT)?.star?.mag}" }) { t ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(t) }.padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color) = when (t) {
                        is SkyTarget.PlanetT -> Icons.Rounded.Public to Color(t.planet.color)
                        is SkyTarget.ConstellationT -> Icons.Rounded.Timeline to IOSColors.indigo
                        SkyTarget.Sun -> Icons.Rounded.WbSunny to IOSColors.orange
                        SkyTarget.Moon -> Icons.Rounded.DarkMode to IOSColors.gray
                        else -> Icons.Rounded.AutoAwesome to IOSColors.blue
                    }
                    IOSIconTile(icon, color, 32.dp); Spacer(Modifier.width(12.dp))
                    Text(t.label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(t.kind, color = Color.White.copy(alpha = .55f), fontSize = 14.sp)
                }
            }
        }
    }
}
