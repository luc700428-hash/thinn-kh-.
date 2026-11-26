package io.atmos.weather.life

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.iosJellyPress
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The personal parts of 今日概況, drawn in the card's own ink and tile colours: the two-sentence
 * digest, the weather-pain gauge with its 24-hour pressure line, and "how does today feel?".
 */
object LifeToday {
    /** Everything the card needs, gathered once per refresh (warnings, closures, pressure). */
    @Composable
    fun rememberGathered(weather: WeatherSnapshot, countyWarnings: List<String>): LifeDigest.Context2? {
        val ctx = LocalContext.current
        var gathered by remember { mutableStateOf<LifeDigest.Context2?>(null) }
        LaunchedEffect(weather.city.coordinate, weather.fetchedAt, countyWarnings) {
            gathered = runCatching { LifeDigest.gather(ctx, weather, weather.city.name, countyWarnings) }.getOrNull()
        }
        return gathered
    }
}

/** 今日重點: the two sentences, under a small lit label with a seam in the card's accent. */
@Composable
fun TodayDigestBlock(weather: WeatherSnapshot, gathered: LifeDigest.Context2?, countyWarnings: List<String>,
                     ink: Color, soft: Color, tile: Color, accent: Color) {
    val ctx = LocalContext.current
    val revision by LifeStore.changes.collectAsState()
    val digest = remember(weather, gathered, revision, countyWarnings) {
        DailyDigest.build(DailyDigest.Inputs(weather, gathered?.warnings ?: countyWarnings, gathered?.pressure?.level,
            gathered?.closures ?: emptyList(), LifeStore.comfortBias(ctx)))
    }
    val warn = (gathered?.warnings ?: countyWarnings).isNotEmpty() || !gathered?.closures.isNullOrEmpty()
    val seam = if (warn) Color(0xFFE5554F) else accent
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tile)
        .drawBehind {
            drawRoundRect(seam, Offset(0f, 12.dp.toPx()), Size(4.dp.toPx(), size.height - 24.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 13.dp)
        .semantics(mergeDescendants = true) { contentDescription = "今日重點：$digest" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TipsAndUpdates, null, Modifier.size(17.dp), tint = seam)
            Spacer(Modifier.width(6.dp))
            Text("今日重點", color = soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(5.dp))
        Text(digest, color = ink, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 氣壓不適: a semicircle gauge in the level colour, the next-24-hour pressure line beside it, and
 * the one number that matters (how far it falls). Taps open the full pressure page.
 */
@Composable
fun TodayPressureTile(outlook: PressurePain.Outlook?, ink: Color, soft: Color, tile: Color, onOpen: () -> Unit) {
    val level = outlook?.level
    val color = level?.let { pressureColor(it) } ?: soft
    Row(Modifier.fillMaxWidth().iosJellyPress().clip(RoundedCornerShape(18.dp)).background(tile)
        .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp)
        .semantics(mergeDescendants = true) {
            contentDescription = if (outlook == null) "氣壓不適指數，正在取得氣壓預報" else "氣壓不適指數${outlook.level.label}，24 小時內氣壓下降 ${WeatherRules.fmt(outlook.drop24h)} 百帕"
        }, verticalAlignment = Alignment.CenterVertically) {
        // Gauge: four segments, the needle on the level.
        Canvas(Modifier.size(width = 54.dp, height = 32.dp)) {
            val stroke = 6.dp.toPx(); val r = size.width / 2 - stroke / 2
            val c = Offset(size.width / 2, size.height - 2.dp.toPx())
            val tl = Offset(c.x - r, c.y - r); val sz = Size(r * 2, r * 2)
            PressurePain.Level.entries.forEachIndexed { i, l ->
                drawArc(pressureColor(l).copy(alpha = if (l == level) 1f else .3f), 180f + i * 45f + 2f, 41f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val idx = level?.ordinal ?: 0
            val ang = Math.toRadians(180.0 + idx * 45.0 + 22.5)
            val tip = Offset(c.x + (r * .72f * kotlin.math.cos(ang)).toFloat(), c.y + (r * .72f * kotlin.math.sin(ang)).toFloat())
            drawLine(ink, c, tip, 2.4.dp.toPx(), StrokeCap.Round)
            drawCircle(ink, 3.dp.toPx(), c)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("氣壓不適", color = soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(level?.label ?: "計算中", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(when {
                outlook == null -> "正在取得氣壓預報…"
                outlook.drop24h < .5 -> "未來 24 小時氣壓平穩"
                else -> "24 小時內降 ${WeatherRules.fmt(outlook.drop24h)} hPa" + (outlook.worstAt?.let { " · ${it.atZone(java.time.ZoneId.systemDefault()).hour} 點降最快" } ?: "")
            }, color = ink, fontSize = 15.sp, maxLines = 1)
        }
        if (outlook != null) {
            Spacer(Modifier.width(8.dp))
            Sparkline(outlook, color, ink, Modifier.size(width = 64.dp, height = 30.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = soft)
    }
}

@Composable
private fun Sparkline(o: PressurePain.Outlook, color: Color, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val now = Instant.now()
        val pts = o.series.filter { !it.at.isBefore(now.minusSeconds(3 * 3600)) && !it.at.isAfter(now.plusSeconds(24 * 3600)) }
        if (pts.size < 2) return@Canvas
        val lo = pts.minOf { it.hpa }; val hi = pts.maxOf { it.hpa }
        val span = (hi - lo).coerceAtLeast(2.0)
        val t0 = pts.first().at.epochSecond.toDouble(); val t1 = pts.last().at.epochSecond.toDouble()
        fun x(t: Instant) = ((t.epochSecond - t0) / (t1 - t0).coerceAtLeast(1.0) * size.width).toFloat()
        fun y(v: Double) = (size.height - (v - lo) / span * size.height).toFloat().coerceIn(1f, size.height - 1f)
        val path = Path(); val fill = Path()
        pts.forEachIndexed { i, p -> if (i == 0) { path.moveTo(x(p.at), y(p.hpa)); fill.moveTo(x(p.at), size.height); fill.lineTo(x(p.at), y(p.hpa)) } else { path.lineTo(x(p.at), y(p.hpa)); fill.lineTo(x(p.at), y(p.hpa)) } }
        fill.lineTo(x(pts.last().at), size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .35f), color.copy(alpha = 0f))))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val nx = x(now)
        drawLine(ink.copy(alpha = .35f), Offset(nx, 0f), Offset(nx, size.height), 1.dp.toPx())
    }
}

/** 今天體感如何？ Three capsules; after a tap, one line confirming what changed, then it folds away. */
@Composable
fun TodayFeelRow(ink: Color, soft: Color, tile: Color) {
    val ctx = LocalContext.current
    val revision by LifeStore.changes.collectAsState()
    val asked = remember(revision) { LifeStore.askedFeelingToday(ctx) }
    var reply by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reply) { if (reply != null) { kotlinx.coroutines.delay(5000); reply = null } }
    AnimatedContent(when { reply != null -> 1; !asked -> 0; else -> 2 }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "體感回饋") { state ->
        when (state) {
            0 -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("今天體感？", color = soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                ComfortCalibration.Feeling.entries.forEach { f ->
                    Text(f.label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp).iosJellyPress().clip(RoundedCornerShape(999.dp)).background(tile)
                            .border(.75.dp, ink.copy(alpha = .14f), RoundedCornerShape(999.dp))
                            .clickable(role = Role.Button) {
                                LifeStore.feel(ctx, f)
                                reply = "記住了，${ComfortCalibration.describe(LifeStore.comfortBias(ctx))}。"
                            }.padding(horizontal = 13.dp, vertical = 7.dp))
                }
            }
            1 -> Text(reply ?: "", color = ink, fontSize = 14.sp, lineHeight = 20.sp)
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}

internal fun pressureColor(level: PressurePain.Level) = when (level) {
    PressurePain.Level.CALM -> Color(0xFF5FB39A)
    PressurePain.Level.WATCH -> Color(0xFFD9B44A)
    PressurePain.Level.ALERT -> Color(0xFFE0883A)
    PressurePain.Level.WARNING -> Color(0xFFD5523F)
}

/**
 * The top of 今日概況, read like the earthquake report hero: today's low and high on a warm–cold
 * bar with "now" marked, then the day's rain chance, UV and air as three small gauges.
 */
@Composable
fun TodayHeroStrip(weather: WeatherSnapshot, ink: Color, soft: Color, tile: Color) {
    val zone = runCatching { java.time.ZoneId.of(weather.timeZoneId ?: "Asia/Taipei") }.getOrDefault(java.time.ZoneId.of("Asia/Taipei"))
    val now = Instant.now()
    val day = now.atZone(zone).toLocalDate()
    val hours = weather.hourly.filter { it.at.atZone(zone).toLocalDate() == day }
    val temps = hours.mapNotNull { it.temperatureC } + weather.temperatureC
    val low = weather.minTempC ?: temps.min(); val high = (weather.maxTempC ?: temps.max()).coerceAtLeast(low + .1)
    val pop = hours.filter { !it.at.isBefore(now.minusSeconds(3600)) }.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
    val uv = (hours.mapNotNull { it.uvIndex } + listOfNotNull(weather.currentUv)).maxOrNull()
    val aqi = weather.usAqi
    fun tempColor(t: Double) = when { t < 10 -> Color(0xFF6FA8F0); t < 18 -> Color(0xFF6FC7D9); t < 24 -> Color(0xFF7BD389); t < 29 -> Color(0xFFF2C14E); t < 33 -> Color(0xFFF08A3C); else -> Color(0xFFE5554F) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${low.roundToInt()}°", color = soft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Canvas(Modifier.weight(1f).height(26.dp).padding(horizontal = 10.dp).semantics {
                contentDescription = "今天最低 ${low.roundToInt()} 度，最高 ${high.roundToInt()} 度，現在 ${weather.temperatureC.roundToInt()} 度"
            }) {
                val y = size.height / 2; val h = 7.dp.toPx()
                drawLine(ink.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
                drawLine(Brush.horizontalGradient(listOf(tempColor(low), tempColor((low + high) / 2), tempColor(high))), Offset(0f, y), Offset(size.width, y), h, StrokeCap.Round)
                val f = ((weather.temperatureC - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
                drawCircle(Color.White, 8.dp.toPx(), Offset(size.width * f, y))
                drawCircle(tempColor(weather.temperatureC), 5.dp.toPx(), Offset(size.width * f, y))
            }
            Text("${high.roundToInt()}°", color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniGauge("降雨", pop?.let { "$it%" } ?: "—", (pop ?: 0) / 100f, if ((pop ?: 0) >= 50) Color(0xFF4F95E0) else Color(0xFF7FB6EA), ink, soft, tile, Modifier.weight(1f))
            MiniGauge("紫外線", uv?.let { "${it.roundToInt()}" } ?: "—", ((uv ?: 0.0) / 11.0).toFloat(),
                when { (uv ?: 0.0) >= 8 -> Color(0xFFB05CD6); (uv ?: 0.0) >= 6 -> Color(0xFFE5554F); (uv ?: 0.0) >= 3 -> Color(0xFFF2C14E); else -> Color(0xFF7BD389) }, ink, soft, tile, Modifier.weight(1f))
            MiniGauge("空氣", aqi?.let { "$it" } ?: "—", ((aqi ?: 0) / 200f),
                when { (aqi ?: 0) > 150 -> Color(0xFFE5554F); (aqi ?: 0) > 100 -> Color(0xFFF08A3C); (aqi ?: 0) > 50 -> Color(0xFFF2C14E); else -> Color(0xFF7BD389) }, ink, soft, tile, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniGauge(label: String, value: String, fraction: Float, color: Color, ink: Color, soft: Color, tile: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(tile).padding(horizontal = 10.dp, vertical = 9.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(30.dp)) {
            val w = 4.dp.toPx(); val tl = Offset(w / 2, w / 2); val sz = Size(size.width - w, size.height - w)
            drawArc(ink.copy(alpha = .12f), 135f, 270f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * fraction.coerceIn(0f, 1f), false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = soft, fontSize = 14.sp, maxLines = 1)
            Text(value, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** The card's top seam colour: warnings red, rain blue, heat orange, otherwise sunny gold. */
fun todayAccent(weather: WeatherSnapshot, warned: Boolean): Color {
    val pop = weather.nextHours.take(12).mapNotNull { it.precipitationProbabilityPct }.maxOrNull() ?: 0
    return when { warned -> Color(0xFFE5554F); pop >= 50 -> Color(0xFF4F95E0); (weather.maxTempC ?: weather.temperatureC) >= 32 -> Color(0xFFF08A3C); else -> Color(0xFFFFD69B) }
}
