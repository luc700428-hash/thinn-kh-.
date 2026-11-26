package io.atmos.weather.life

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.IOSColors
import io.atmos.weather.ui.IOSSegmentedControl
import io.atmos.weather.ui.iosAppear
import java.time.Instant
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val tabular = TextStyle(fontFeatureSettings = "tnum")
private val regionOrder = listOf("臺灣北區", "臺灣中區", "臺灣南區", "臺灣東區", "澎湖地區")
private fun regionShort(r: String) = r.removePrefix("臺灣").removeSuffix("地區").removeSuffix("區").ifBlank { r }

/** 水庫與水情警戒: loads the data, then [WaterBody] draws it. */
@Composable
internal fun WaterPageV3() {
    val ctx = LocalContext.current
    var levels by remember { mutableStateOf<List<Reservoirs.Level>?>(null) }
    var alerts by remember { mutableStateOf<List<NcdrFeed.Alert>>(emptyList()) }
    LaunchedEffect(Unit) {
        val now = Instant.now()
        alerts = LifeSources.alerts(ctx).filter { it.activeAt(now) && it.category in WaterLook.categories }
        levels = LifeSources.reservoirs(ctx)
    }
    WaterBody(levels, alerts)
}

internal object WaterLook {
    val categories = setOf("淹水", "河川高水位", "水庫放流", "土石流", "大規模崩塌", "水位警戒")
    fun alertColor(c: String) = when (c) {
        "土石流", "大規模崩塌" -> Color(0xFFA0623A); "淹水" -> Color(0xFF3E7FD6); "河川高水位", "水位警戒" -> Color(0xFF2FA3B5)
        "水庫放流" -> Color(0xFF5E7BD9); else -> Color(0xFFE0883A)
    }
    /** The water colour says the state: deep blue plenty, teal fine, amber low, orange tight, red short. */
    fun water(pct: Double) = when {
        pct >= 80 -> Color(0xFF2F80ED); pct >= 60 -> Color(0xFF2EA8C9); pct >= 40 -> Color(0xFFE0B23A); pct >= 25 -> Color(0xFFEB8A2F); else -> Color(0xFFE0483E)
    }
    val legend = listOf("不足" to Color(0xFFE0483E), "吃緊" to Color(0xFFEB8A2F), "偏低" to Color(0xFFE0B23A), "正常" to Color(0xFF2EA8C9), "充足" to Color(0xFF2F80ED))
    fun legendIndex(pct: Double) = when { pct >= 80 -> 4; pct >= 60 -> 3; pct >= 40 -> 2; pct >= 25 -> 1; else -> 0 }
}

/**
 * A glass tank filled to [percent] with a gently moving surface: the picture everyone reads
 * at once. Still with Reduce Motion (and in tests).
 */
@Composable
internal fun WaterTank(percent: Double, modifier: Modifier, corner: Dp = 18.dp, showText: Boolean = true, big: Boolean = false) {
    val pct = percent.coerceIn(0.0, 100.0)
    val color = WaterLook.water(pct)
    val ink = MaterialTheme.colorScheme.onSurface
    val reduce = io.atmos.weather.ui.rememberAppleReduceMotion()
    val level by animateFloatAsState((pct / 100).toFloat(), tween(900), label = "level")
    val phase = if (reduce) remember { mutableFloatStateOf(0f) }
        else rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Box(modifier.semantics { contentDescription = "蓄水 ${pct.roundToInt()}%，${Reservoirs.word(pct)}" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = corner.toPx()
            drawRoundRect(ink.copy(alpha = .07f), cornerRadius = CornerRadius(r))
            val top = size.height * (1f - level)
            val amp = if (level in .02f..0.98f) 3.dp.toPx() else 0f
            val wave = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width) {
                    lineTo(x, top + amp * sin((x / size.width * 2 * PI + phase.value * 2 * PI).toFloat())); x += 4f
                }
                lineTo(size.width, size.height); close()
            }
            val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
            val inside = Path().apply { op(clip, wave, androidx.compose.ui.graphics.PathOperation.Intersect) }
            drawPath(inside, Brush.verticalGradient(listOf(color.copy(alpha = .75f), color), startY = top, endY = size.height))
            // A lighter band at the surface, like light on water.
            drawLine(Color.White.copy(alpha = .35f), Offset(r * .4f, top + 1.dp.toPx()), Offset(size.width - r * .4f, top + 1.dp.toPx()), 1.5.dp.toPx())
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .45f), ink.copy(alpha = .08f))), cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
        }
        if (showText) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val onWater = level > .62f
            Text("${pct.roundToInt()}%", color = if (onWater) Color.White else ink, fontSize = if (big) 30.sp else 18.sp, fontWeight = FontWeight.Bold, style = tabular)
            if (big) Text(Reservoirs.word(pct), color = if (onWater) Color.White.copy(alpha = .9f) else color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun WaterBody(levels: List<Reservoirs.Level>?, alerts: List<NcdrFeed.Alert>) {
    val p = MaterialTheme.colorScheme
    var region by remember { mutableStateOf("全部") }
    val list = levels
    val total = list?.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.storage } / l.sumOf { it.info.capacity } * 100 }
    val accent = total?.let { WaterLook.water(it) } ?: Color(0xFF2EA8C9)
    LifeHero(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaterTank(total ?: 0.0, Modifier.width(104.dp).height(132.dp), 22.dp, showText = total != null, big = true)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("全台水庫", color = p.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(when { list == null -> "正在取得水情"; total == null -> "暫時取不到水庫資料"; else -> "整體水情${Reservoirs.word(total)}" },
                    color = p.onSurface, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.clip(RoundedCornerShape(99.dp)).background((if (alerts.isEmpty()) IOSColors.green else IOSColors.red).copy(alpha = .14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(if (alerts.isEmpty()) IOSColors.green else IOSColors.red))
                    Spacer(Modifier.width(6.dp))
                    Text(if (alerts.isEmpty()) "沒有淹水、土石流警戒" else "${alerts.size} 則水情警戒", color = p.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!list.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            // Region by region: one small tank each, the tightest one easy to spot.
            val regions = list.groupBy { it.info.region }.mapValues { (_, l) -> l.sumOf { it.storage } / l.sumOf { it.info.capacity } * 100 }
                .toList().sortedBy { (r, _) -> regionOrder.indexOf(r).let { if (it < 0) 9 else it } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                regions.take(5).forEachIndexed { i, (r, pct) ->
                    Column(Modifier.weight(1f).iosAppear(i), horizontalAlignment = Alignment.CenterHorizontally) {
                        WaterTank(pct, Modifier.fillMaxWidth().height(64.dp), 14.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(regionShort(r), color = p.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(Reservoirs.word(pct), color = WaterLook.water(pct), fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ColorLegend(WaterLook.legend, total?.let { WaterLook.legendIndex(it) })
        }
        Spacer(Modifier.height(10.dp))
        Text("水庫：有效蓄水量 ÷ 有效容量（經濟部水利署，每小時）；警戒：國家災害防救科技中心。", color = p.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
    }
    if (alerts.isNotEmpty()) {
        SectionTitle("水情警戒中", Icons.Rounded.Warning, IOSColors.red, "${alerts.size} 則")
        Row(Modifier.fillMaxWidth().frosted(20.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CountyStatusMap({ c -> alerts.firstOrNull { NcdrFeed.mentions(it, c) }?.let { WaterLook.alertColor(it.category) } },
                Modifier.width(128.dp).height(196.dp), description = "水情警戒地圖")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts.groupBy { it.category }.forEach { (cat, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(WaterLook.alertColor(cat)))
                        Spacer(Modifier.width(8.dp))
                        Text(cat, color = p.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("${l.size}", color = p.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        alerts.take(15).forEachIndexed { i, a ->
            val c = WaterLook.alertColor(a.category)
            Row(Modifier.fillMaxWidth().iosAppear(i).clip(RoundedCornerShape(18.dp)).background(c.copy(alpha = .08f))
                .border(.5.dp, c.copy(alpha = .35f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(99.dp)).background(c))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(a.category, c); Spacer(Modifier.width(8.dp))
                        Text(a.author, color = p.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(a.summary, color = p.onSurface, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
    if (!list.isNullOrEmpty()) {
        SectionTitle("各水庫蓄水", Icons.Rounded.WaterDrop, IOSColors.cyan, "低的在前")
        val regions = listOf("全部") + list.map { it.info.region }.distinct().sortedBy { r -> regionOrder.indexOf(r).let { if (it < 0) 9 else it } }
        IOSSegmentedControl(regions.map { if (it == "全部") it else regionShort(it) }, regions.indexOf(region).coerceAtLeast(0), { region = regions[it] })
        val shown = list.filter { region == "全部" || it.info.region == region }.sortedBy { it.percent }
        shown.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { i, l -> ReservoirTile(l, Modifier.weight(1f).fillMaxHeight().iosAppear(r * 2 + i)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReservoirTile(l: Reservoirs.Level, modifier: Modifier) {
    val p = MaterialTheme.colorScheme
    val pct = l.percent.coerceAtMost(100.0)
    val c = WaterLook.water(pct)
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(p.onSurface.copy(alpha = .045f))
        .border(.5.dp, p.onSurface.copy(alpha = .08f), RoundedCornerShape(20.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        WaterTank(pct, Modifier.width(30.dp).height(62.dp), 9.dp, showText = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(l.info.name, color = p.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${pct.roundToInt()}", color = c, fontSize = 24.sp, fontWeight = FontWeight.Bold, style = tabular)
                Text("%", color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.width(6.dp))
                Text(Reservoirs.word(pct), color = p.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text("${fmtWan(l.storage)} / ${fmtWan(l.info.capacity)} 萬噸", color = p.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun fmtWan(v: Double) = if (v >= 100) "%,d".format(v.roundToInt()) else String.format(java.util.Locale.US, "%.1f", v)
