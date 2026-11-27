package io.atmos.weather.life

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import io.atmos.weather.comfort.ComfortStore
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Every page of the personal and disaster features, by id. */
object LifeFeatures {
    val titles = linkedMapOf(
        "pressure" to "氣壓不適預警", "calendar" to "行事曆天氣", "rules" to "自訂天氣規則", "family" to "家人天氣關懷",
        "snap" to "天空浮水印相機", "closure" to "停班停課速查", "water" to "水庫與水情警戒", "kit" to "防災包清單",
        "shelter" to "避難收容處所", "disaster" to "災難省電模式", "transit" to "火車、高鐵與駕車模式",
    )
    fun title(id: String) = titles[id] ?: "生活工具"
}

@Composable
fun LifeFeaturePage(id: String, snapshot: WeatherSnapshot?, onClose: () -> Unit) {
    LifePage(LifeFeatures.title(id), onClose) { LifeFeatureContent(id, snapshot, onClose) }
}

/** The body of one page (also rendered on its own by the tests). */
@Composable
internal fun ColumnScope.LifeFeatureContent(id: String, snapshot: WeatherSnapshot?, onClose: () -> Unit) {
        when (id) {
            "pressure" -> PressurePage()
            "calendar" -> CalendarPageV2()
            "rules" -> RulesPageV2()
            "family" -> FamilyPageV2()
            "snap" -> SnapPageV2(snapshot)
            "closure" -> ClosurePageV2()
            "water" -> WaterPageV3()
            "kit" -> KitPageV2()
            "shelter" -> ShelterPageV2()
            "disaster" -> DisasterSettingsV2(onClose)
            "transit" -> TransitPageV2()
        }
}

@Composable
private fun LifePage(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    WorkspacePageDialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AppSystemBars(MaterialTheme.colorScheme.background.red < .5f)
        // The same page frame as 地震報告: a fixed centred title with the round back control, then the content.
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            io.atmos.weather.ui.IOSSettingsHeader(title, onClose)
            Column(Modifier.weight(1f).appleBouncingScroll().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

/** When notifications are off, a page that promises to notify says so and offers the switch. */
@Composable
internal fun NotifyBanner() {
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(io.atmos.weather.permission.PermissionGuide.canNotify(ctx)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = io.atmos.weather.permission.PermissionGuide.canNotify(ctx) }
        lifecycle.lifecycle.addObserver(o); onDispose { lifecycle.lifecycle.removeObserver(o) }
    }
    if (allowed) return
    Row(Modifier.fillMaxWidth().frosted(16.dp, ThinnDesignTokens.caution).iosJellyPress().clickable {
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("通知已關閉", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("這個功能需要通知才能提醒你，點這裡開啟。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun Note(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
@Composable internal fun Heading(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
@Composable internal fun Body(text: String, color: Color = MaterialTheme.colorScheme.onSurface) = Text(text, color = color, fontSize = 16.sp, lineHeight = 23.sp)

@Composable
internal fun Chips(labels: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { l -> GlassChip(l, l in selected) { onToggle(l) } }
    }
}

/** A Liquid Glass capsule: clear glass with the sun-lit rim and a bloom under the finger; tinted when chosen. */
@Composable
internal fun GlassChip(label: String, on: Boolean, ink: Color? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.red < .5f
    val opaque = rememberAppleReduceTransparency()
    val light = GlassLight.current(follow = !opaque)
    val touch = remember { GlassTouch() }
    val fill by androidx.compose.animation.animateColorAsState(
        if (on) BrandPalette.selection else when (ink?.let { it.red + it.green + it.blue > 1.5f }) {
            // Over the sky the capsule tints against the ink: dark glass under white text, light under dark.
            true -> Color.Black.copy(alpha = .22f); false -> Color.White.copy(alpha = .6f)
            null -> if (dark) Color.White.copy(alpha = .08f) else Color.White.copy(alpha = .62f)
        },
        androidx.compose.animation.core.tween(220, easing = AppleMotion.fluid), label = "玻璃膠囊")
    Text(label, color = if (on) Color.White else ink ?: scheme.onSurface, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier.iosJellyPress().clip(RoundedCornerShape(999.dp))
            .thinnBackdrop(opaque, 999.dp).background(fill)
            .glassTouchTracker(touch)
            .liquidGlassSurface(999.dp, { if (touch.point.isSpecified) 1f else 0f }, touch, dark, light)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}

/** Frosted glass panel for grouped content inside a page (edit boxes, map, status rows). */
@Composable
internal fun Modifier.frosted(corner: androidx.compose.ui.unit.Dp = 16.dp, tint: Color? = null): Modifier {
    val opaque = rememberAppleReduceTransparency()
    return this.clip(RoundedCornerShape(corner)).thinnGlass(opaque, corner, tint ?: MaterialTheme.colorScheme.surfaceVariant).thinnSpecularEdge(corner)
}

/** A glass progress rail: the iOS grey track with a glossy coloured fill that catches the light. */
@Composable
internal fun GlassMeter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(LiquidMaterial.rail(dark))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(99.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(lerpWhite(color, .28f), color, color.copy(alpha = .92f)))))
    }
}
private fun lerpWhite(c: Color, t: Float) = Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t, c.alpha)

@Composable
internal fun Field(value: String, onValue: (String) -> Unit, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).frosted(14.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = scheme.onSurfaceVariant, fontSize = 16.sp)
        BasicTextField(value, onValue, singleLine = true, textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp), modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- 氣壓不適預警

@Composable
private fun PressurePage() {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    var outlook by remember { mutableStateOf<PressurePain.Outlook?>(null) }
    var loading by remember { mutableStateOf(true) }
    var on by remember { mutableStateOf(LifeStore.pressureAlerts(ctx)) }
    var kinds by remember { mutableStateOf(LifeStore.sensitivities(ctx)) }
    val city = remember { ComfortStore.current(ctx) }
    LaunchedEffect(city) {
        outlook = city?.let { c -> LifeSources.pressure(c.coordinate.latitude, c.coordinate.longitude)?.let { PressurePain.assess(it) } }
        loading = false
    }
    val o = outlook
    val color = o?.let { pressureColor(it.level) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    LifeHero(color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelTile(o?.level?.label ?: "—", color, 84.dp, small = "氣壓不適")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(city?.name ?: "目前位置", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                when {
                    loading -> Body("正在取得氣壓預報…")
                    o == null -> Body(if (city == null) "請先在首頁定位或選擇城市。" else "暫時取不到氣壓預報，請稍後再試。")
                    else -> {
                        Text("24 小時內降", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(WeatherRules.fmt(o.drop24h), color = color, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
                            Text(" hPa", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, modifier = Modifier.padding(bottom = 7.dp))
                        }
                    }
                }
            }
        }
        if (o != null) {
            Spacer(Modifier.height(10.dp))
            Body(o.level.advice)
            Spacer(Modifier.height(12.dp))
            InsightTiles(
                Triple("現在氣壓", o.now?.let { "${it.roundToInt()} hPa" } ?: "—", "地面氣壓"),
                Triple("3 小時最快", "${WeatherRules.fmt(o.drop3h)} hPa", o.worstAt?.let { "${it.atZone(zone).hour} 點前後" } ?: "未來沒有急降"),
                Triple("何時回穩", o.bottomAt?.let { "${it.atZone(zone).hour} 點後" } ?: "—", if (o.bottomAt != null) "氣壓開始回升" else "36 小時內未見回升"),
            )
            Spacer(Modifier.height(14.dp))
            PressureChart(o, Modifier.fillMaxWidth().height(170.dp))
            Spacer(Modifier.height(10.dp))
            ColorLegend(PressurePain.Level.entries.map { it.label to pressureColor(it) }, o.level.ordinal)
        }
    }
    SectionTitle("提前通知", Icons.Rounded.NotificationsActive, Color(0xFFE0883A))
    if (on) NotifyBanner()
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Body("氣壓急降時提醒我")
                Note("指數到「注意」以上時通知，每天最多一次；安靜時段不打擾。")
            }
            GlassSwitch(on, { on = it; LifeStore.setPressureAlerts(ctx, it) })
        }
        Spacer(Modifier.height(12.dp))
        Note("我容易：")
        Spacer(Modifier.height(6.dp))
        Chips(listOf("偏頭痛", "關節痛", "舊傷痠痛", "氣喘", "頭暈", "耳鳴"), kinds) { k ->
            kinds = if (k in kinds) kinds - k else kinds + k; LifeStore.setSensitivities(ctx, kinds)
        }
    }
    SectionTitle("怎麼判斷", Icons.Rounded.Info, Color(0xFF5B8DEF))
    GlassSurface(Modifier.fillMaxWidth()) {
        PressurePain.Level.entries.forEach { l ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(l.label, pressureColor(l)); Spacer(Modifier.width(10.dp))
                Note(when (l) {
                    PressurePain.Level.CALM -> "24 小時降不到 3.5 hPa"
                    PressurePain.Level.WATCH -> "3 小時降 1 hPa 或 24 小時降 3.5 hPa"
                    PressurePain.Level.ALERT -> "3 小時降 2 hPa 或 24 小時降 6 hPa"
                    PressurePain.Level.WARNING -> "3 小時降 3 hPa 或 24 小時降 10 hPa（颱風、鋒面接近）"
                })
            }
        }
        Spacer(Modifier.height(6.dp))
        Note("只是提醒，不能取代醫療建議；持續或劇烈疼痛請就醫。")
    }
}

@Composable
private fun PressureChart(o: PressurePain.Outlook, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val line = pressureColor(o.level)
    val zone = ZoneId.systemDefault()
    val label = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier.semantics { contentDescription = "未來氣壓走勢圖" }) {
        val pts = o.series; if (pts.size < 2) return@Canvas
        val left = 40.dp.toPx(); val bottom = size.height - 20.dp.toPx(); val top = 6.dp.toPx(); val right = size.width
        val t0 = pts.first().at.epochSecond.toDouble(); val t1 = pts.last().at.epochSecond.toDouble()
        val lo = kotlin.math.floor(pts.minOf { it.hpa } - 1); val hi = kotlin.math.ceil(pts.maxOf { it.hpa } + 1)
        fun x(t: Instant) = (left + (t.epochSecond - t0) / (t1 - t0).coerceAtLeast(1.0) * (right - left)).toFloat()
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo).coerceAtLeast(.1) * (bottom - top)).toFloat()
        label.textSize = 11.sp.toPx(); label.color = android.graphics.Color.argb(150, (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt())
        // Grid with hPa labels.
        val step = if (hi - lo > 12) 4 else 2
        var g = (lo.toInt() / step + 1) * step
        while (g < hi) {
            drawLine(ink.copy(alpha = .08f), Offset(left, y(g.toDouble())), Offset(right, y(g.toDouble())), 1f)
            drawContext.canvas.nativeCanvas.drawText("$g", 0f, y(g.toDouble()) + label.textSize / 3, label)
            g += step
        }
        // The steepest 3 hours, shaded.
        o.worstAt?.let { w -> drawRect(line.copy(alpha = .12f), Offset(x(w.minusSeconds(3 * 3600)), top), Size(x(w) - x(w.minusSeconds(3 * 3600)), bottom - top)) }
        val path = Path(); val fill = Path()
        pts.forEachIndexed { i, p ->
            if (i == 0) { path.moveTo(x(p.at), y(p.hpa)); fill.moveTo(x(p.at), bottom); fill.lineTo(x(p.at), y(p.hpa)) }
            else { path.lineTo(x(p.at), y(p.hpa)); fill.lineTo(x(p.at), y(p.hpa)) }
        }
        fill.lineTo(x(pts.last().at), bottom); fill.close()
        drawPath(fill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(line.copy(alpha = .30f), line.copy(alpha = 0f)), top, bottom))
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        // Hour labels every 6 hours, and "現在".
        pts.filter { it.at.atZone(zone).hour % 6 == 0 }.forEach { p ->
            val h = p.at.atZone(zone).hour
            drawContext.canvas.nativeCanvas.drawText(if (h == 0) "${p.at.atZone(zone).monthValue}/${p.at.atZone(zone).dayOfMonth}" else "${h}時", x(p.at) - 10.dp.toPx(), size.height - 4.dp.toPx(), label)
        }
        val nx = x(Instant.now())
        if (nx in left..right) {
            drawLine(ink.copy(alpha = .45f), Offset(nx, top), Offset(nx, bottom), 1.5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("現在", nx + 3.dp.toPx(), top + label.textSize, label)
        }
        o.worstAt?.let { w -> pts.minByOrNull { p -> kotlin.math.abs(p.at.epochSecond - w.epochSecond) }?.let { p ->
            drawCircle(Color.White, 6.dp.toPx(), Offset(x(p.at), y(p.hpa))); drawCircle(line, 4.dp.toPx(), Offset(x(p.at), y(p.hpa)))
        } }
    }
}

// ---------------------------------------------------------------- 行事曆天氣

// ---------------------------------------------------------------- 自訂天氣規則

internal val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")
internal fun describe(r: WeatherRules.Rule): String {
    val days = if (r.weekdays == 127) "每天" else "每週" + weekNames.indices.filter { r.weekdays and (1 shl it) != 0 }.joinToString("") { weekNames[it] }
    val cond = r.conditions.joinToString(" 且 ") { "${it.metric.label}${it.op.label} ${WeatherRules.fmt(it.value)}${it.metric.unit}" }
    return "$days ${"%02d:%02d".format(r.hour, r.minute)} · ${r.period.label}$cond"
}

@Composable
internal fun RuleEditor(start: WeatherRules.Rule, onDismiss: () -> Unit, onSave: (WeatherRules.Rule) -> Unit) {
    var r by remember { mutableStateOf(start) }
    IOSFormSheet("編輯規則", onDismiss = onDismiss, onDone = { onSave(r) }, validate = { r.name.isNotBlank() && r.conditions.isNotEmpty() && r.weekdays != 0 }) {
        Note("名稱")
        Field(r.name, { r = r.copy(name = it.take(20)) }, "規則名稱")
        Note("通知內容")
        Field(r.message, { r = r.copy(message = it.take(60)) }, "條件成立時要說的話")
        Note("檢查哪一天")
        GlassChoiceGroup(WeatherRules.Period.entries.map { it.label }, r.period.ordinal, { r = r.copy(period = WeatherRules.Period.entries[it]) })
        Note("什麼時候檢查")
        IOSClockDrums(r.hour, r.minute, { r = r.copy(hour = it) }, { r = r.copy(minute = it) })
        GlassWeekdayChoices(weekNames, weekNames.indices.filter { r.weekdays and (1 shl it) != 0 }.toSet()) { i ->
            r = r.copy(weekdays = r.weekdays xor (1 shl i))
        }
        Note("條件（全部成立才通知）")
        r.conditions.forEachIndexed { i, c ->
            Column(Modifier.fillMaxWidth().frosted(16.dp).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chips(WeatherRules.Metric.entries.map { it.label }, setOf(c.metric.label)) { label ->
                    val m = WeatherRules.Metric.entries.first { it.label == label }
                    r = r.copy(conditions = r.conditions.mapIndexed { j, x -> if (j == i) x.copy(metric = m, value = defaultValue(m)) else x })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chips(WeatherRules.Op.entries.map { it.label }, setOf(c.op.label)) { label ->
                        r = r.copy(conditions = r.conditions.mapIndexed { j, x -> if (j == i) x.copy(op = WeatherRules.Op.entries.first { it.label == label }) else x })
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${WeatherRules.fmt(c.value)}${c.metric.unit}", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                val range = valueRange(c.metric)
                GlassSlider(c.value.toFloat().coerceIn(range.start, range.endInclusive), { v ->
                    r = r.copy(conditions = r.conditions.mapIndexed { j, x -> if (j == i) x.copy(value = v.toDouble()) else x })
                }, Modifier.fillMaxWidth(), valueRange = range, steps = valueSteps(c.metric))
                if (r.conditions.size > 1) Text("移除這個條件", color = ThinnDesignTokens.critical, fontSize = 14.sp,
                    modifier = Modifier.clickable { r = r.copy(conditions = r.conditions.filterIndexed { j, _ -> j != i }) })
            }
        }
        if (r.conditions.size < 4) Text("＋ 再加一個條件", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp,
            modifier = Modifier.clickable { r = r.copy(conditions = r.conditions + WeatherRules.Condition(WeatherRules.Metric.WIND, WeatherRules.Op.BELOW, 4.0)) }.padding(vertical = 8.dp))
    }
}

private fun valueRange(m: WeatherRules.Metric) = when (m) {
    WeatherRules.Metric.RAIN -> 0f..100f; WeatherRules.Metric.HIGH, WeatherRules.Metric.LOW -> 0f..40f
    WeatherRules.Metric.WIND -> 0f..12f; WeatherRules.Metric.UV -> 0f..12f; WeatherRules.Metric.AQI -> 0f..300f
}
/** Slider stops: every 5 % rain, every degree, every Beaufort force or UV step, every 10 AQI. */
private fun valueSteps(m: WeatherRules.Metric) = when (m) {
    WeatherRules.Metric.RAIN -> 19; WeatherRules.Metric.HIGH, WeatherRules.Metric.LOW -> 39
    WeatherRules.Metric.WIND, WeatherRules.Metric.UV -> 11; WeatherRules.Metric.AQI -> 29
}

private fun defaultValue(m: WeatherRules.Metric) = when (m) {
    WeatherRules.Metric.RAIN -> 30.0; WeatherRules.Metric.HIGH -> 30.0; WeatherRules.Metric.LOW -> 15.0
    WeatherRules.Metric.WIND -> 4.0; WeatherRules.Metric.UV -> 6.0; WeatherRules.Metric.AQI -> 100.0
}

// ---------------------------------------------------------------- 家人天氣關懷

@Composable
internal fun AddFamilySheet(onDismiss: () -> Unit, onAdd: (FamilyCare.Person) -> Unit) {
    val ctx = LocalContext.current
    var relation by remember { mutableStateOf(FamilyCare.relations.first()) }
    var nick by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Triple<String, Double, Double>>>(emptyList()) }
    var chosen by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }
    val saved = remember { io.atmos.weather.location.SavedCityStore.all(ctx).map { Triple(it.name, it.coordinate.latitude, it.coordinate.longitude) } }
    LaunchedEffect(query) {
        if (query.length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        results = runCatching { io.atmos.weather.AppContainer.geoApi.search(query, 8, "zh").results.orEmpty()
            .map { Triple(it.asCity().name, it.latitude, it.longitude) } }.getOrDefault(emptyList())
    }
    IOSFormSheet("新增家人", onDismiss = onDismiss, onDone = {
        chosen?.let { onAdd(FamilyCare.Person(UUID.randomUUID().toString(), relation, nick.trim(), it.first, it.second, it.third)) }
    }, validate = { chosen != null }) {
        Note("關係")
        Chips(FamilyCare.relations, setOf(relation)) { relation = it }
        Note("稱呼（可不填）")
        Field(nick, { nick = it.take(10) }, "例如：媽媽、阿公")
        Note("所在地")
        chosen?.let { Body("✓ ${it.first}", MaterialTheme.colorScheme.primary) } ?: Note("搜尋後點一下地名就選好了；選好才能按「完成」。")
        Field(query, { query = it }, "搜尋城市或鄉鎮")
        (results.ifEmpty { if (query.isBlank()) saved else emptyList() }).take(8).forEach { r ->
            Text(r.first, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).clickable { chosen = r }.padding(vertical = 10.dp, horizontal = 6.dp))
        }
    }
}

// ---------------------------------------------------------------- 停班停課速查

// ---------------------------------------------------------------- 水庫與水情警戒

@Composable
internal fun ReservoirRow(l: Reservoirs.Level) {
    val pct = l.percent.coerceAtMost(100.0)
    val color = when { pct >= 60 -> Color(0xFF4F9FD8); pct >= 40 -> Color(0xFFD8B65A); pct >= 25 -> Color(0xFFE0883A); else -> Color(0xFFD5523F) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.info.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${pct.roundToInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(Reservoirs.word(pct), color = color, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        GlassMeter((pct / 100).toFloat(), color)
        Note("有效蓄水 ${l.storage.roundToInt()} 萬噸 / 容量 ${l.info.capacity.roundToInt()} 萬噸" + (l.observed?.let { " · ${it.monthValue}/${it.dayOfMonth} ${it.hour}時" } ?: ""))
    }
}

// ---------------------------------------------------------------- 防災包清單

// ---------------------------------------------------------------- 避難收容處所

// ---------------------------------------------------------------- 災難省電模式

