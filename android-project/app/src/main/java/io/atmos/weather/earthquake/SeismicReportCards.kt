package io.atmos.weather.earthquake

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.alerts.WarningCountyAtlas
import io.atmos.weather.alerts.WarningMapViewport
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.ThinnOpticalMaterial
import io.atmos.weather.ui.iosJellyPress
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Earthquake reports, rebuilt around what people look for first: how big (magnitude, coloured by
 * strength), how hard it shook (the largest intensity), where, and how it shook in every county,
 * with the county map beside it. The latest report leads as a full card; the rest follow as rich
 * rows with their county chips.
 */
internal object SeismicReportStyle {
    /** The epicentre: a deep cherry that no intensity uses, so it never reads as a county grade. */
    val EPICENTRE = Color(0xFFE11D48)

    /** Magnitude colour: yellow below 4, orange below 5, red below 6, magenta from 6. Darker on light pages. */
    fun magnitude(m: Double, dark: Boolean): Color = when {
        m >= 6.0 -> if (dark) Color(0xFFE879F9) else Color(0xFFA21CAF)
        m >= 5.0 -> if (dark) Color(0xFFFF4D4F) else Color(0xFFD7263D)
        m >= 4.0 -> if (dark) Color(0xFFFF9F1C) else Color(0xFFC2410C)
        m >= 3.0 -> if (dark) Color(0xFFFFE14D) else Color(0xFF8A6A00)
        else -> if (dark) Color(0xFF9BE7C4) else Color(0xFF1F7A5C)
    }

    fun relative(origin: Instant, now: Instant = Instant.now()): String {
        val d = Duration.between(origin, now).coerceAtLeast(Duration.ZERO)
        return when {
            d.toMinutes() < 1 -> "剛剛"
            d.toMinutes() < 60 -> "${d.toMinutes()} 分鐘前"
            d.toHours() < 24 -> "${d.toHours()} 小時前"
            d.toDays() < 7 -> "${d.toDays()} 天前"
            d.toDays() < 31 -> "${d.toDays() / 7} 週前"
            else -> "${d.toDays() / 30} 個月前"
        }
    }

    /** "臺東縣" → "臺東", keeping 嘉義縣/嘉義市 and 新竹縣/新竹市 apart. */
    fun shortCounty(county: String): String {
        val c = county.replace('台', '臺')
        return if (c.startsWith("嘉義") || c.startsWith("新竹")) c else c.removeSuffix("縣").removeSuffix("市")
    }

    /** "臺東縣政府南南東方 101.9 公里" from "臺東縣政府南南東方 101.9 公里 (位於臺灣東南部海域)". */
    fun bearing(report: EarthquakeReport): String = report.location.substringBefore('(').substringBefore('（').trim()

    fun coordinates(report: EarthquakeReport): String? =
        if (report.latitude != null && report.longitude != null) "%.2f, %.2f".format(Locale.US, report.latitude, report.longitude) else null
}

/**
 * County chips, strongest first: the county name on calm glass with its grade in a coloured
 * badge ("臺東 [4]"), so a long list reads as one quiet row instead of a rainbow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SeismicCountyChips(counties: Map<String, String>, limit: Int = 30, chipHeight: Dp = 30.dp) {
    val palette = MaterialTheme.colorScheme
    val dark = palette.background.red < .5f
    val sorted = counties.entries.sortedByDescending { CwaEarthquakeParser.intensityRank(it.value) }.take(limit)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sorted.forEach { (county, grade) ->
            val g = grade.removeSuffix("級")
            val fill = SeismicIntensityColors.color(g)
            Row(Modifier.height(chipHeight).clip(RoundedCornerShape(chipHeight / 2))
                .background(fill.copy(alpha = if (dark) .16f else .12f))
                .border(.75.dp, fill.copy(alpha = if (dark) .45f else .55f), RoundedCornerShape(chipHeight / 2))
                .padding(start = 10.dp, end = 3.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$county 震度 ${SeismicDisplayFormat.symbol(g)} 級" },
                verticalAlignment = Alignment.CenterVertically) {
                Text(SeismicReportStyle.shortCounty(county), color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.height(chipHeight - 6.dp).widthIn(min = chipHeight - 6.dp).clip(RoundedCornerShape(chipHeight / 2)).background(fill)
                    .padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Text(SeismicDisplayFormat.symbol(g), color = seismicInkOn(fill), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A still county map: counties filled by intensity with their grade in a box, the epicentre as a
 * red target with rings (labelled 震央, so it cannot be mistaken for a close button) and, when the
 * phone knows where the person is, a blue "你" dot.
 */
@Composable internal fun SeismicReportMiniMap(report: EarthquakeReport, modifier: Modifier = Modifier, place: SeismicPlace? = null) {
    val ctx = LocalContext.current.applicationContext
    val atlas by produceState<WarningCountyAtlas?>(null, ctx) { value = WarningCountyAtlas.load(ctx) }
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val counties = report.countyIntensity
    val fill = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL } }
    val edge = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeJoin = android.graphics.Paint.Join.ROUND } }
    val text = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER } }
    Canvas(modifier.semantics {
        contentDescription = "縣市震度地圖：" + counties.entries.sortedByDescending { CwaEarthquakeParser.intensityRank(it.value) }
            .joinToString("、") { "${it.key} ${it.value} 級" }
    }) {
        val loaded = atlas ?: return@Canvas
        val lat = report.latitude; val lon = report.longitude
        // Taiwan, widened just enough to keep an offshore epicentre in view.
        var west = 119.95; var east = 122.10; var south = 21.85; var north = 25.35
        if (lat != null && lon != null && lat in 20.0..27.0 && lon in 118.0..124.5) {
            west = minOf(west, lon - .35); east = maxOf(east, lon + .35); south = minOf(south, lat - .3); north = maxOf(north, lat + .3)
        }
        val view = WarningMapViewport(Rect(Offset.Zero, size), west, south, east, north)
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save(); canvas.clipRect(0f, 0f, size.width, size.height); canvas.concat(view.matrix())
        val island = loaded.counties.filter { it.name !in setOf("金門縣", "連江縣") }
        // Coastline first: a bold stroke round every county, whose inner half the fills cover,
        // leaves a crisp outline of Taiwan against the card in both appearances.
        edge.strokeWidth = (3.2f * density / view.scale).toFloat()
        edge.color = if (dark) 0xFFE8EDE9.toInt() else 0xFF22302B.toInt()
        island.forEach { canvas.drawPath(it.path, edge) }
        edge.strokeWidth = (.7f * density / view.scale).toFloat()
        edge.color = if (dark) 0xFF151A18.toInt() else 0xFFFFFFFF.toInt()
        island.forEach { shape ->
            val grade = counties[shape.name]
            val c = if (grade == null) (if (dark) Color(0xFF3A423F) else Color(0xFFE6EAE7)) else SeismicIntensityColors.color(grade)
            fill.color = android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
            canvas.drawPath(shape.path, fill); canvas.drawPath(shape.path, edge)
        }
        canvas.restore()
        // Grade boxes at each county's label point.
        text.textSize = 12f * density
        val box = 16f * density
        // Strongest counties first; a box that would cover another is left out (the chips list all).
        val placed = ArrayList<android.graphics.RectF>()
        loaded.counties.filter { it.name in counties && it.name !in setOf("金門縣", "連江縣") }
            .sortedByDescending { CwaEarthquakeParser.intensityRank(counties[it.name] ?: "") }.forEach { shape ->
            val grade = counties[shape.name] ?: return@forEach
            val at = view.project(shape.label.first, shape.label.second)
            if (at.x !in 0f..size.width || at.y !in 0f..size.height) return@forEach
            val claim = android.graphics.RectF(at.x - box * .6f, at.y - box * .6f, at.x + box * .6f, at.y + box * .6f)
            if (placed.any { android.graphics.RectF.intersects(it, claim) }) return@forEach
            placed += claim
            val c = SeismicIntensityColors.color(grade)
            fill.color = android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
            edge.strokeWidth = 1.4f * density; edge.color = if (dark) 0xFF0E1116.toInt() else 0xFF1F2937.toInt()
            val r = android.graphics.RectF(at.x - box / 2, at.y - box / 2, at.x + box / 2, at.y + box / 2)
            canvas.drawRoundRect(r, 3f * density, 3f * density, fill); canvas.drawRoundRect(r, 3f * density, 3f * density, edge)
            text.color = seismicInkOn(c).let { android.graphics.Color.rgb((it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
            canvas.drawText(SeismicDisplayFormat.symbol(grade), at.x, at.y + text.textSize * .36f, text)
        }
        // The person, under the epicentre so the epicentre always stays readable.
        if (place != null && place.lat.isFinite() && place.lon.isFinite()) {
            val me = view.project(place.lon, place.lat)
            if (me.x in 0f..size.width && me.y in 0f..size.height) {
                drawCircle(Color(0xFF3B82F6).copy(alpha = .25f), 9.dp.toPx(), me)
                drawCircle(Color.White, 5.5.dp.toPx(), me)
                drawCircle(Color(0xFF2563EB), 4.dp.toPx(), me)
            }
        }
        if (lat != null && lon != null) {
            val at = view.project(lon, lat)
            val red = SeismicReportStyle.EPICENTRE
            // Rings spreading from the epicentre, then a solid centre with a white rim.
            listOf(20f to .16f, 14f to .28f).forEach { (r, a) ->
                drawCircle(red.copy(alpha = a * .5f), r.dp.toPx(), at)
                drawCircle(red.copy(alpha = a + .2f), r.dp.toPx(), at, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            }
            drawCircle(Color.White, 7.dp.toPx(), at)
            drawCircle(red, 5.dp.toPx(), at)
            // "震央" tag beside it, on whichever side has room.
            text.textSize = 11f * density
            val label = "震央"
            val w = text.measureText(label) + 10f * density
            val h = 17f * density
            val right = at.x + 12f * density + w <= size.width
            val left = if (right) at.x + 12f * density else at.x - 12f * density - w
            val top = (at.y - h / 2).coerceIn(0f, (size.height - h).coerceAtLeast(0f))
            fill.color = android.graphics.Color.rgb((red.red * 255).toInt(), (red.green * 255).toInt(), (red.blue * 255).toInt())
            canvas.drawRoundRect(android.graphics.RectF(left, top, left + w, top + h), h / 2, h / 2, fill)
            text.color = android.graphics.Color.WHITE
            canvas.drawText(label, left + w / 2, top + h / 2 + text.textSize * .36f, text)
        }
    }
}

/** The latest report as one card: numbers left, county map right, every county below. */
@Composable internal fun SeismicReportHero(report: EarthquakeReport, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    val dark = palette.background.red < .5f
    val uri = LocalUriHandler.current
    var open by rememberSaveable(report.id) { mutableStateOf(true) }
    val turn by animateFloatAsState(if (open) 0f else 180f, label = "報告收合")
    val counties = report.countyIntensity
    val place = rememberSeismicPlace()
    val now by produceState(Instant.now()) { while (true) { kotlinx.coroutines.delay(30_000); value = Instant.now() } }
    // thinn-khì style: calm glass, one colour only — the strongest shaking — as a light seam
    // along the top edge and on the intensity tile. Numbers stay in the text colour.
    val shake = SeismicIntensityColors.color(report.maxIntensity?.removeSuffix("級"))
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(ThinnOpticalMaterial.surface)
        .drawBehind {
            drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(shake, shake.copy(alpha = .35f), Color.Transparent)),
                size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(shake.copy(alpha = if (dark) .07f else .05f), Color.Transparent),
                endY = 120.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 120.dp.toPx()))
        }
        .border(.75.dp, palette.onSurface.copy(alpha = if (dark) .10f else .08f), RoundedCornerShape(26.dp))
        .testTag("seismic-report-hero")) {
        // Header: when, how long ago, report number, collapse.
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(99.dp)).background(shake))
            Spacer(Modifier.width(10.dp))
            Text(reportTime(report, "yyyy-MM-dd HH:mm"), color = palette.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(SeismicReportStyle.relative(report.origin, now), color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(report.number?.let { "第 $it 號" } ?: report.category, color = palette.onSurfaceVariant, fontSize = 14.sp)
            Icon(Icons.Rounded.KeyboardArrowUp, if (open) "收合" else "展開", Modifier.padding(start = 4.dp).size(22.dp).rotate(turn), tint = palette.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(start = 16.dp, end = 12.dp, bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("規模", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                Text("%.1f".format(Locale.US, report.magnitude), color = palette.onSurface,
                                    fontSize = 52.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("最大震度", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                SeismicGradeTile(report.maxIntensity, 70.dp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(SeismicReportStyle.bearing(report), color = palette.onSurface, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("${report.shortLocation} · 深度 ${SeismicDisplayFormat.depth(report.depthKm)}", color = palette.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Column(Modifier.width(148.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SeismicReportMiniMap(report, Modifier.fillMaxWidth().height(224.dp), place)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(99.dp)).background(SeismicReportStyle.EPICENTRE))
                            Text(" 震央", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                            if (place != null) {
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF2563EB)))
                                Text(" 你", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        SeismicIntensityLegend(counties.values.map { it.removeSuffix("級") }.toSet(), Modifier.fillMaxWidth())
                    }
                }
                SeismicReportFacts.summary(report)?.let { line ->
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(shake.copy(alpha = if (dark) .14f else .10f))
                        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(shake))
                        Spacer(Modifier.width(8.dp))
                        Text(line, color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SeismicReportInsightTiles(report, place)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("各地最大震度", color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    (report.imageUrl ?: report.officialUrl).let { url ->
                        Box(Modifier.size(40.dp).iosJellyPress().clip(RoundedCornerShape(12.dp)).background(palette.onSurface.copy(alpha = .08f))
                            .clickable(role = Role.Button) { runCatching { uri.openUri(url) } }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Description, "氣象署報告圖", Modifier.size(22.dp), tint = palette.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (counties.isEmpty()) Text("此報告未附各地震度", color = palette.onSurfaceVariant, fontSize = 14.sp)
                else SeismicCountyChips(counties)
            }
        }
    }
}

/** Intensity tile with the "級" suffix, like the CWA cards. */
@Composable internal fun SeismicGradeTile(raw: String?, size: Dp) {
    val grade = raw?.removeSuffix("級")?.trim()?.takeIf { it.isNotBlank() }
    val fill = SeismicIntensityColors.color(grade)
    val ink = seismicInkOn(fill)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .2f)).background(fill)
        .semantics { contentDescription = grade?.let { "最大震度 $it 級" } ?: "震度未提供" }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(SeismicDisplayFormat.symbol(grade), color = ink, fontSize = (size.value * .58f).sp, lineHeight = (size.value * .62f).sp, fontWeight = FontWeight.Bold)
            if (grade != null) Text("級", color = ink, fontSize = maxOf(14f, size.value * .2f).sp, modifier = Modifier.padding(bottom = (size.value * .08f).dp))
        }
    }
}

/** One earlier report: magnitude, when and where, depth and position, county chips, the largest intensity. */
@Composable internal fun SeismicReportListRow(report: EarthquakeReport, onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val place = rememberSeismicPlace()
    Row(Modifier.fillMaxWidth().iosJellyPress().clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 14.dp).testTag("seismic-report-row"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("規模", color = palette.onSurfaceVariant, fontSize = 14.sp)
            Text("%.1f".format(Locale.US, report.magnitude), color = palette.onSurface,
                fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${reportTime(report, "MM-dd HH:mm")} · ${if (report.category == "小區域有感") "小區域" else report.number?.let { "第 $it 號" } ?: "有感"}",
                    color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(SeismicReportStyle.relative(report.origin), color = palette.onSurfaceVariant, fontSize = 14.sp)
            }
            Text(report.shortLocation, color = palette.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val away = remember(report.id, place) { SeismicReportFacts.local(report, place)?.distanceKm }
            Text(listOfNotNull("深度 ${SeismicDisplayFormat.depth(report.depthKm)}",
                away?.let { "距你 ${it.roundToInt()} km" } ?: SeismicReportStyle.coordinates(report)).joinToString(" · "),
                color = palette.onSurfaceVariant, fontSize = 14.sp)
            SeismicMyCountyPill(report, place)
            if (report.countyIntensity.isNotEmpty()) { Spacer(Modifier.height(2.dp)); SeismicCountyChips(report.countyIntensity, limit = 8, chipHeight = 28.dp) }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("最大震度", color = palette.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            SeismicGradeTile(report.maxIntensity, 56.dp)
        }
    }
}
