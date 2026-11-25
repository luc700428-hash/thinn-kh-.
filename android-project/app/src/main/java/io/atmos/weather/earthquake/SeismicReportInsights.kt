package io.atmos.weather.earthquake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * What a report means for the person reading it, worked out on the phone: how hard it shook
 * where they are, how far they were from it and how soon the shaking reached them, how deep it
 * was and how much energy it released. Everything here comes from the official report plus the
 * same travel-time table used for warnings; nothing is presented as an official figure.
 */
internal object SeismicReportFacts {
    data class Local(val county: String, val intensity: String?, val distanceKm: Double?, val arrivalSeconds: Double?)

    fun local(report: EarthquakeReport, place: SeismicPlace?): Local? {
        place ?: return null
        val county = place.name.replace('台', '臺').removeSuffix("附近").trim()
        val grade = report.countyIntensity.entries.firstOrNull { county == it.key || county.contains(it.key) }?.value
        val lat = report.latitude; val lon = report.longitude
        val distance = if (lat != null && lon != null) SeismicWavePhysics.epicentralKm(lat, lon, place.lat, place.lon) else null
        val depth = report.depthKm?.takeIf { it.isFinite() && it in 0.0..700.0 } ?: 10.0
        val arrival = distance?.let { SeismicLocalPrediction.travelSeconds(depth, it) }
        return Local(county, grade, distance, arrival)
    }

    /** 極淺層 / 淺層 / 中層 / 深層, as the CWA describes focal depth. */
    fun depthClass(depthKm: Double?): String? = depthKm?.takeIf { it.isFinite() }?.let {
        when { it < 30 -> "極淺層"; it < 70 -> "淺層"; it < 300 -> "中層"; else -> "深層" }
    }

    /** Energy relative to a magnitude-4.0 earthquake: each magnitude step is about 31.6×. */
    fun energyVersusFour(magnitude: Double): String {
        val ratio = 10.0.pow(1.5 * (magnitude - 4.0))
        return when {
            ratio >= 1_000_000 -> "規模 4 的 ${(ratio / 10_000).roundToInt()} 萬倍"
            ratio >= 10_000 -> "規模 4 的 ${(ratio / 10_000 * 10).roundToInt() / 10.0} 萬倍"
            ratio >= 2 -> "規模 4 的 ${ratio.roundToInt()} 倍"
            ratio >= .5 -> "與規模 4 相當"
            else -> "規模 4 的 1/${(1 / ratio).roundToInt()}"
        }
    }

    data class Week(val count: Int, val felt: Int, val strongest: EarthquakeReport?, val mine: Int, val days: List<List<EarthquakeReport>>)

    /** The last seven days, day by day (oldest first), with what matters at a glance. */
    fun week(reports: List<EarthquakeReport>, place: SeismicPlace?, now: Instant = Instant.now()): Week {
        val zone = ZoneId.of("Asia/Taipei")
        val today = now.atZone(zone).toLocalDate()
        val recent = reports.filter { Duration.between(it.origin, now).toDays() < 7 && !it.origin.isAfter(now.plusSeconds(60)) }
        val days = (6 downTo 0).map { back -> recent.filter { it.origin.atZone(zone).toLocalDate() == today.minusDays(back.toLong()) } }
        val county = place?.name?.replace('台', '臺')?.removeSuffix("附近")?.trim()
        val mine = if (county == null) 0 else recent.count { r -> r.countyIntensity.keys.any { county == it || county.contains(it) } }
        return Week(recent.size, recent.count { it.category != "小區域有感" }, recent.maxByOrNull { it.magnitude }, mine, days)
    }
}

/** Three small tiles under the hero: here, the waves, and the earthquake's size and depth. */
@Composable internal fun SeismicReportInsightTiles(report: EarthquakeReport, place: SeismicPlace?) {
    val palette = MaterialTheme.colorScheme
    val dark = palette.background.red < .5f
    val local = remember(report.id, place) { SeismicReportFacts.local(report, place) }
    val tile = Modifier.clip(RoundedCornerShape(16.dp)).background(palette.onSurface.copy(alpha = if (dark) .06f else .045f))
        .border(.5.dp, palette.onSurface.copy(alpha = .08f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 9.dp)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(tile.weight(1f).fillMaxHeight()) {
            Text(local?.county?.let { "你在 ${SeismicReportStyle.shortCounty(it)}" } ?: "你所在地", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
            if (local == null) Text("未定位", color = palette.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                val g = local.intensity?.removeSuffix("級")
                if (g != null) Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(SeismicIntensityColors.color(g)), contentAlignment = Alignment.Center) {
                    Text(SeismicDisplayFormat.symbol(g), color = seismicInkOn(SeismicIntensityColors.color(g)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (g != null) Spacer(Modifier.width(6.dp))
                Text(if (g != null) "級" else "未達 1 級", color = palette.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            local?.distanceKm?.let { Text("距震央 ${it.roundToInt()} km", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1) }
        }
        Column(tile.weight(1f).fillMaxHeight()) {
            Text("主震波抵達", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
            Text(local?.arrivalSeconds?.let { "震後 ${it.roundToInt()} 秒" } ?: "—", color = palette.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("走時表推算", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
        }
        Column(tile.weight(1f).fillMaxHeight()) {
            Text(SeismicReportFacts.depthClass(report.depthKm) ?: "深度", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
            Text(SeismicDisplayFormat.depth(report.depthKm), color = palette.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(SeismicReportFacts.energyVersusFour(report.magnitude), color = palette.onSurfaceVariant, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        }
    }
}

/** The intensity scale under the map: 1 to 7, the colours people know, those that occurred raised. */
@Composable internal fun SeismicIntensityLegend(present: Set<String>, modifier: Modifier = Modifier) {
    val grades = listOf("1", "2", "3", "4", "5弱", "5強", "6弱", "6強", "7")
    val palette = MaterialTheme.colorScheme
    Row(modifier.semantics { contentDescription = "震度色階" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        grades.forEach { g ->
            val on = g in present
            val c = SeismicIntensityColors.color(g)
            Box(Modifier.weight(1f).height(if (on) 16.dp else 10.dp).clip(RoundedCornerShape(3.dp))
                .background(if (on) c else c.copy(alpha = .38f)), contentAlignment = Alignment.Center) {
                if (on) Text(SeismicDisplayFormat.symbol(g), color = seismicInkOn(c), fontSize = 14.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") palette
}

/**
 * The week at a glance: a seven-day strip where each earthquake is a dot sized by magnitude and
 * coloured by its strongest intensity, with the count, the largest one and how many were felt
 * where the person is.
 */
@Composable internal fun SeismicWeekSummary(reports: List<EarthquakeReport>, place: SeismicPlace?, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.colorScheme
    val dark = palette.background.red < .5f
    val week = remember(reports, place) { SeismicReportFacts.week(reports, place) }
    if (week.count == 0) return
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(palette.primary.copy(alpha = if (dark) .16f else .10f), palette.onSurface.copy(alpha = .03f))))
        .border(.5.dp, palette.onSurface.copy(alpha = .08f), RoundedCornerShape(22.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("近 7 天", color = palette.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${week.count} 次有感 · 顯著 ${week.felt} 次", color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        val dayLabels = remember { val today = java.time.LocalDate.now(ZoneId.of("Asia/Taipei")); (6 downTo 0).map { today.minusDays(it.toLong()) } }
        Canvas(Modifier.fillMaxWidth().height(64.dp).semantics {
            contentDescription = "近 7 天地震分布：" + week.days.mapIndexed { i, d -> "${dayLabels[i].dayOfMonth} 日 ${d.size} 次" }.joinToString("、")
        }) {
            val col = size.width / 7f
            for (i in 0 until 7) {
                if (i % 2 == 0) drawRoundRect(palette.onSurface.copy(alpha = .035f), Offset(i * col, 0f), Size(col, size.height), CornerRadius(8.dp.toPx()))
                val day = week.days[i]
                day.sortedBy { it.origin }.forEachIndexed { k, r ->
                    val frac = (r.origin.atZone(ZoneId.of("Asia/Taipei")).toLocalTime().toSecondOfDay() / 86400f)
                    val x = i * col + col * (.12f + .76f * frac)
                    val y = size.height * (1f - ((r.magnitude - 2.0) / 5.0).toFloat().coerceIn(.05f, .95f))
                    val radius = (3f + (r.magnitude.toFloat() - 2f).coerceIn(0f, 5f) * 2.2f).dp.toPx()
                    val c = SeismicIntensityColors.color(r.maxIntensity?.removeSuffix("級"))
                    drawCircle(c.copy(alpha = .28f), radius * 1.6f, Offset(x, y))
                    drawCircle(c, radius, Offset(x, y))
                    drawCircle(if (dark) Color.Black.copy(alpha = .4f) else Color.White, radius, Offset(x, y), style = Stroke(1.dp.toPx()))
                    @Suppress("UNUSED_EXPRESSION") k
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            dayLabels.forEachIndexed { i, d ->
                Text(if (i == 6) "今天" else "${d.monthValue}/${d.dayOfMonth}", color = if (i == 6) palette.onSurface else palette.onSurfaceVariant,
                    fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.strongest?.let { s ->
                Text("最大 M${"%.1f".format(Locale.US, s.magnitude)} · ${s.shortLocation}", color = palette.onSurface, fontSize = 14.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (place != null) Text("你所在縣市有感 ${week.mine} 次", color = palette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/** "你所在 2 級" when the person's county felt it. */
@Composable internal fun SeismicMyCountyPill(report: EarthquakeReport, place: SeismicPlace?) {
    val county = place?.name?.replace('台', '臺')?.removeSuffix("附近")?.trim() ?: return
    val grade = report.countyIntensity.entries.firstOrNull { county == it.key || county.contains(it.key) }?.value?.removeSuffix("級") ?: return
    val c = SeismicIntensityColors.color(grade)
    Row(Modifier.clip(RoundedCornerShape(99.dp)).border(1.5.dp, c, RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(c))
        Spacer(Modifier.width(5.dp))
        Text("你所在 ${SeismicDisplayFormat.symbol(grade)} 級", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The person's place for report pages, read once. */
@Composable internal fun rememberSeismicPlace(): SeismicPlace? {
    val ctx = LocalContext.current.applicationContext
    return remember { runCatching { SeismicUserLocation.current(ctx) }.getOrNull() }
}
