package io.atmos.weather.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

/**
 * The shared look of the personal and disaster pages, matching the earthquake reports: a hero
 * card washed by one accent colour with a light seam on top, insight tiles, coloured badges and
 * a still county map of Taiwan with a crisp coastline.
 */
private val dim @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ink @Composable get() = MaterialTheme.colorScheme.onSurface
private val isDark @Composable get() = MaterialTheme.colorScheme.background.red < .5f

/** The page's lead card. */
@Composable
internal fun LifeHero(accent: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = isDark
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.fillMaxWidth().shadow(if (dark) 0.dp else 10.dp, shape, ambientColor = accent.copy(alpha = .25f), spotColor = accent.copy(alpha = .25f))
        .clip(shape).background(ThinnOpticalMaterial.surface)
        .drawBehind {
            // A soft pool of the accent from the top-right corner, a wash down from the top edge,
            // and a short bright seam: the same light the earthquake report cards use.
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = if (dark) .30f else .20f), Color.Transparent),
                center = Offset(size.width * .92f, -size.height * .05f), radius = size.width * .75f))
            drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = if (dark) .10f else .06f), Color.Transparent), endY = 170.dp.toPx()),
                size = Size(size.width, 170.dp.toPx()))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, accent, accent.copy(alpha = .4f), Color.Transparent)),
                topLeft = Offset(size.width * .06f, 0f), size = Size(size.width * .88f, 3.dp.toPx()))
        }
        .border(.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .20f else .75f), ink.copy(alpha = if (dark) .06f else .07f))), shape)
        .padding(horizontal = 18.dp, vertical = 18.dp), content = content)
}

/** A big coloured tile with a short word in it (like the intensity tile). */
@Composable
internal fun LevelTile(text: String, color: Color, size: Dp = 76.dp, small: String? = null) {
    val ink = if (color.luminanceApprox() > .55f) Color(0xFF15201C) else Color.White
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .24f)).background(Brush.verticalGradient(listOf(color.lighten(.12f), color))),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = ink, fontSize = (size.value * (if (text.length > 2) .24f else .34f)).coerceAtLeast(14f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            small?.let { Text(it, color = ink.copy(alpha = .85f), fontSize = 14.sp, maxLines = 1) }
        }
    }
}

internal fun Color.luminanceApprox() = .2126f * red + .7152f * green + .0722f * blue
internal fun Color.lighten(t: Float) = Color(red + (1 - red) * t, green + (1 - green) * t, blue + (1 - blue) * t, alpha)

/** Three facts in a row, each in a quiet tile. */
@Composable
internal fun InsightTiles(vararg tiles: Triple<String, String, String?>) {
    val dark = isDark
    val tile = Modifier.clip(RoundedCornerShape(16.dp))
        .background(Brush.verticalGradient(listOf(ink.copy(alpha = if (dark) .085f else .055f), ink.copy(alpha = if (dark) .04f else .025f))))
        .border(.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .14f else .7f), ink.copy(alpha = .06f))), RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (label, value, caption) ->
            Column(tile.weight(1f).fillMaxHeight()) {
                Text(label, color = dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, color = ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                caption?.let { Text(it, color = dim, fontSize = 14.sp, maxLines = 2, lineHeight = 18.sp, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** A section title with a tinted symbol, as in iOS grouped lists. */
@Composable
internal fun SectionTitle(text: String, icon: ImageVector, tint: Color, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        io.atmos.weather.ui.IOSIconTile(icon, tint, 26.dp)
        Spacer(Modifier.width(9.dp))
        Text(text, color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = dim, fontSize = 14.sp) }
    }
}

/** A small filled capsule with white or dark text. */
@Composable
internal fun Badge(text: String, color: Color) {
    val ink = if (color.luminanceApprox() > .55f) Color(0xFF15201C) else Color.White
    Text(text, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(color).padding(horizontal = 9.dp, vertical = 3.dp))
}

/** A thick ring with the fraction filled, the big number in the middle. */
@Composable
internal fun RingGauge(fraction: Float, color: Color, center: String, caption: String, size: Dp = 112.dp) {
    val track = ink.copy(alpha = .10f)
    Box(Modifier.size(size).semantics { contentDescription = "$center $caption" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = 11.dp.toPx()
            val tl = Offset(w / 2, w / 2); val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color.copy(alpha = .18f), 135f, 270f * fraction.coerceIn(0f, 1f), false, tl, sz, style = Stroke(w * 2f, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(color.lighten(.3f), color, color.lighten(.3f))), 135f, 270f * fraction.coerceIn(0f, 1f), false, tl, sz,
                style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, color = ink, fontSize = (size.value * .22f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, color = dim, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/** A row of coloured swatches with labels: the scale a page uses. */
@Composable
internal fun ColorLegend(items: List<Pair<String, Color>>, current: Int? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, (label, c) ->
            val on = current == null || current == i
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(if (current == i) 10.dp else 6.dp).clip(RoundedCornerShape(99.dp)).background(if (on) c else c.copy(alpha = .35f)))
                Spacer(Modifier.height(4.dp))
                Text(label, color = if (current == i) ink else dim, fontSize = 14.sp, fontWeight = if (current == i) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/**
 * Taiwan's counties filled by [fill] (null = not involved), with a bold coastline and optional
 * markers. Kinmen and Matsu are left out to keep the island large.
 */
@Composable
internal fun CountyStatusMap(fill: (String) -> Color?, modifier: Modifier, markers: List<Pair<Offset, Color>> = emptyList(), description: String) {
    val ctx = LocalContext.current.applicationContext
    val atlas by produceState<WarningCountyAtlas?>(null, ctx) { value = WarningCountyAtlas.load(ctx) }
    val dark = isDark
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier.semantics { contentDescription = description }) {
        val loaded = atlas ?: return@Canvas
        val view = WarningMapViewport(Rect(Offset.Zero, size), 119.95, 21.85, 122.10, 25.35)
        val canvas = drawContext.canvas.nativeCanvas
        val island = loaded.counties.filter { it.name !in setOf("金門縣", "連江縣") }
        canvas.save(); canvas.concat(view.matrix())
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeJoin = android.graphics.Paint.Join.ROUND
        paint.strokeWidth = (3f * density / view.scale).toFloat(); paint.color = if (dark) 0xFFE8EDE9.toInt() else 0xFF22302B.toInt()
        island.forEach { canvas.drawPath(it.path, paint) }
        island.forEach { shape ->
            val c = fill(shape.name) ?: if (dark) Color(0xFF3A423F) else Color(0xFFE6EAE7)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
            canvas.drawPath(shape.path, paint)
            paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = (.7f * density / view.scale).toFloat()
            paint.color = if (dark) 0xFF151A18.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawPath(shape.path, paint)
        }
        canvas.restore()
        markers.forEach { (lonLat, color) ->
            val p = view.project(lonLat.x.toDouble(), lonLat.y.toDouble())
            drawCircle(Color.White, 6.dp.toPx(), p); drawCircle(color, 4.dp.toPx(), p)
        }
    }
}
