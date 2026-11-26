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
        "pressure" to "氣壓與天氣痛", "calendar" to "行事曆天氣", "rules" to "自訂天氣規則", "family" to "家人天氣關懷",
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
            "calendar" -> CalendarPage()
            "rules" -> RulesPage()
            "family" -> FamilyPage()
            "snap" -> SnapPage(snapshot)
            "closure" -> ClosurePage()
            "water" -> WaterPage()
            "kit" -> KitPage()
            "shelter" -> ShelterPage()
            "disaster" -> DisasterSettingsPage(onClose)
            "transit" -> TransitSettingsPage()
        }
}

@Composable
private fun LifePage(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    WorkspacePageDialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AppSystemBars(MaterialTheme.colorScheme.background.red < .5f)
        Column(Modifier.fillMaxSize().background(ThinnOpticalMaterial.page).appleBouncingScroll().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.width(10.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

/** When notifications are off, a page that promises to notify says so and offers the switch. */
@Composable
private fun NotifyBanner() {
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

@Composable private fun Note(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
@Composable private fun Heading(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
@Composable private fun Body(text: String, color: Color = MaterialTheme.colorScheme.onSurface) = Text(text, color = color, fontSize = 16.sp, lineHeight = 23.sp)

@Composable
private fun Chips(labels: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
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
private fun Field(value: String, onValue: (String) -> Unit, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).frosted(14.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, color = scheme.onSurfaceVariant, fontSize = 16.sp)
        BasicTextField(value, onValue, singleLine = true, textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp), modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- 氣壓與天氣痛

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
    GlassSurface(Modifier.fillMaxWidth()) {
        val o = outlook
        when {
            loading -> Body("正在取得氣壓預報…")
            o == null -> Body(if (city == null) "請先在首頁定位或選擇城市。" else "暫時取不到氣壓預報，請稍後再試。")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(99.dp)).background(pressureColor(o.level)))
                    Spacer(Modifier.width(8.dp))
                    Text("天氣痛指數：${o.level.label}", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Body(o.level.advice)
                Spacer(Modifier.height(10.dp))
                Note(listOfNotNull(o.now?.let { "現在 ${it.roundToInt()} hPa" }, "3 小時最多降 ${WeatherRules.fmt(o.drop3h)} hPa",
                    "24 小時內降 ${WeatherRules.fmt(o.drop24h)} hPa", o.worstAt?.let { "降最快在 ${it.atZone(zone).hour} 點前後" },
                    o.bottomAt?.let { "${it.atZone(zone).hour} 點後回升" }).joinToString(" · "))
                Spacer(Modifier.height(12.dp))
                PressureChart(o, Modifier.fillMaxWidth().height(150.dp))
                Note("過去 12 小時到未來 36 小時的地面氣壓（Open-Meteo 模式預報），直線為現在。")
            }
        }
    }
    Heading("提前通知")
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
    Note("指數依氣壓下降速度推算（3 小時降 2 hPa 或 24 小時降 6 hPa 為「注意」），只是提醒，不能取代醫療建議；持續或劇烈疼痛請就醫。")
}

@Composable
private fun PressureChart(o: PressurePain.Outlook, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val line = pressureColor(o.level)
    Canvas(modifier) {
        val pts = o.series; if (pts.size < 2) return@Canvas
        val t0 = pts.first().at.epochSecond.toFloat(); val t1 = pts.last().at.epochSecond.toFloat()
        val lo = pts.minOf { it.hpa }.toFloat() - 1f; val hi = pts.maxOf { it.hpa }.toFloat() + 1f
        fun x(t: Instant) = (t.epochSecond - t0) / (t1 - t0).coerceAtLeast(1f) * size.width
        fun y(v: Double) = size.height - ((v.toFloat() - lo) / (hi - lo).coerceAtLeast(.1f)) * size.height
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.at), y(p.hpa)) else path.lineTo(x(p.at), y(p.hpa)) }
        drawPath(path, line, style = Stroke(3.dp.toPx()))
        val nx = x(Instant.now())
        drawLine(ink.copy(alpha = .35f), Offset(nx, 0f), Offset(nx, size.height), 1.dp.toPx())
        o.worstAt?.let { drawCircle(line, 5.dp.toPx(), Offset(x(it), y(pts.minByOrNull { p -> kotlin.math.abs(p.at.epochSecond - it.epochSecond) }!!.hpa))) }
    }
}

// ---------------------------------------------------------------- 行事曆天氣

@Composable
private fun CalendarPage() {
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(CalendarWeather.allowed(ctx)) }
    var rows by remember { mutableStateOf<List<CalendarWeather.Row>?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(allowed) {
        if (!allowed) return@LaunchedEffect
        val events = CalendarWeather.events(ctx)
        rows = events.map { CalendarWeather.Row(it, null, null) }
        val out = events.map { e ->
            val place = CalendarWeather.locate(ctx, e.location)
            val w = place?.let { LifeSources.weather(ctx, it.name, it.lat, it.lon) }
            CalendarWeather.Row(e, place, w?.let { CalendarWeather.forecast(it, e) })
        }
        rows = out
    }
    Note("只讀取（不會修改）你手機行事曆中未來 7 天「有填地點」的行程，把當時當地的天氣貼在旁邊。資料只留在手機上。")
    if (!allowed) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Body("需要讀取行事曆的權限")
            Spacer(Modifier.height(10.dp))
            GlassButton(onClick = { ask.launch(Manifest.permission.READ_CALENDAR) }, modifier = Modifier.fillMaxWidth()) {
                Text("允許讀取行事曆")
            }
            Spacer(Modifier.height(8.dp))
            Text("沒有跳出詢問？到系統設定開啟", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, modifier = Modifier.clickable {
                runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 6.dp))
        }
        return
    }
    val list = rows
    when {
        list == null -> Body("正在讀取行事曆…")
        list.isEmpty() -> GlassSurface(Modifier.fillMaxWidth()) { Body("未來 7 天沒有填了地點的行程。在行事曆的行程裡加上地點，就會出現在這裡。") }
        else -> {
            val zone = ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern("M/d（E） HH:mm", Locale.TAIWAN)
            list.forEach { r ->
                GlassSurface(Modifier.fillMaxWidth()) {
                    Text(if (r.event.allDay) DateTimeFormatter.ofPattern("M/d（E） 全天", Locale.TAIWAN).format(r.event.begin.atZone(zone)) else fmt.format(r.event.begin.atZone(zone)),
                        color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(r.event.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Note(r.event.location)
                    Spacer(Modifier.height(6.dp))
                    val f = r.forecast
                    when {
                        f != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(listOfNotNull(f.temperature?.let { "$it°" }, f.rain?.let { "降雨 $it%" }).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Note("${f.note} · ${CalendarWeather.advice(f)}")
                        }
                        r.place == null && rows !== list -> Note("找不到這個地點")
                        else -> Note(if (r.place == null) "正在查詢地點…" else "預報尚未涵蓋這個時間")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 自訂天氣規則

@Composable
private fun RulesPage() {
    val ctx = LocalContext.current
    var rules by remember { mutableStateOf(LifeStore.rules(ctx)) }
    var editing by remember { mutableStateOf<WeatherRules.Rule?>(null) }
    var testResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    fun save(next: List<WeatherRules.Rule>) { rules = next; LifeStore.setRules(ctx, next) }
    if (rules.any { it.enabled }) NotifyBanner()
    Note("自己組合條件：到了設定的時間，若條件全部成立就通知你。例如「每週五 20:00，週末降雨 < 20% 且風力 < 3 級 → 適合洗車或露營」。")
    rules.forEach { r ->
        GlassSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { editing = r }) {
                    Text(r.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Note(describe(r))
                }
                GlassSwitch(r.enabled, { on -> save(rules.map { if (it.id == r.id) it.copy(enabled = on) else it }) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("立即測試", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, modifier = Modifier.clickable {
                    scope.launch {
                        val c = ComfortStore.current(ctx)
                        val w = c?.let { LifeSources.weather(ctx, it.name, it.coordinate.latitude, it.coordinate.longitude) }
                        testResult = r.id to (if (w == null) "暫時取不到天氣" else WeatherRules.evaluate(r, w, LocalDate.now(), ZoneId.systemDefault()).let {
                            (if (it.fires) "✓ 條件成立，會通知你。" else "✗ 條件未成立，不會通知。") + "\n" + it.explanation })
                    }
                }.padding(vertical = 8.dp))
                Text("刪除", color = ThinnDesignTokens.critical, fontSize = 15.sp, modifier = Modifier.clickable { save(rules.filter { it.id != r.id }) }.padding(vertical = 8.dp))
            }
            testResult?.takeIf { it.first == r.id }?.let { Note(it.second) }
        }
    }
    GlassButton(onClick = { editing = WeatherRules.Rule(UUID.randomUUID().toString(), "我的規則", 127, 20, 0, WeatherRules.Period.TOMORROW,
        listOf(WeatherRules.Condition(WeatherRules.Metric.RAIN, WeatherRules.Op.BELOW, 30.0)), "明天適合出門") }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp))
        Text("新增規則")
    }
    Heading("快速範本")
    var added by remember { mutableStateOf("") }
    WeatherRules.templates().forEach { t ->
        val has = rules.any { it.name == t.name }
        GlassAction(if (has) "✓ ${t.name}" else t.name, if (has) "已加入，可在上方開關或刪除" else describe(t)) {
            if (!has) { save(rules + t.copy(id = UUID.randomUUID().toString())); added = "已加入「${t.name}」，到時間會自動檢查。" }
        }
    }
    if (added.isNotEmpty()) Note(added)
    editing?.let { r -> RuleEditor(r, onDismiss = { editing = null }) { saved ->
        save(if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved); editing = null
    } }
}

private val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")
private fun describe(r: WeatherRules.Rule): String {
    val days = if (r.weekdays == 127) "每天" else "每週" + weekNames.indices.filter { r.weekdays and (1 shl it) != 0 }.joinToString("") { weekNames[it] }
    val cond = r.conditions.joinToString(" 且 ") { "${it.metric.label}${it.op.label} ${WeatherRules.fmt(it.value)}${it.metric.unit}" }
    return "$days ${"%02d:%02d".format(r.hour, r.minute)} · ${r.period.label}$cond"
}

@Composable
private fun RuleEditor(start: WeatherRules.Rule, onDismiss: () -> Unit, onSave: (WeatherRules.Rule) -> Unit) {
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
private fun FamilyPage() {
    val ctx = LocalContext.current
    var people by remember { mutableStateOf(LifeStore.family(ctx)) }
    var adding by remember { mutableStateOf(false) }
    if (people.isNotEmpty()) NotifyBanner()
    Note("替家人的所在地加上關係標籤。對方那裡有豪雨或強風等特報、隔天驟降 8° 以上、寒冷或高溫時，會主動提醒你，還能一鍵把天氣關心小卡傳到 LINE。")
    people.forEach { p -> FamilyCard(p) { people = people.filter { it.id != p.id }; LifeStore.setFamily(ctx, people) } }
    GlassButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.PersonAdd, null); Spacer(Modifier.width(6.dp))
        Text("新增家人")
    }
    if (adding) AddFamilySheet(onDismiss = { adding = false }) { p -> people = people + p; LifeStore.setFamily(ctx, people); adding = false }
}

@Composable
private fun FamilyCard(p: FamilyCare.Person, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    var w by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var concerns by remember { mutableStateOf<List<FamilyCare.Concern>>(emptyList()) }
    LaunchedEffect(p.id) {
        w = LifeSources.weather(ctx, p.place, p.lat, p.lon)
        val now = Instant.now()
        val county = LifeSources.county(p.place)
        val warnings = LifeSources.alerts(ctx).filter { it.activeAt(now) && it.author.contains("氣象署") && NcdrFeed.mentions(it, county) }.map { it.category + "特報" }
        concerns = w?.let { FamilyCare.concerns(p, it, warnings) } ?: emptyList()
    }
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.relation, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.frosted(99.dp, BrandPalette.selection).padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.width(8.dp))
            Text(p.nickname.ifBlank { p.relation }, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            w?.let { Text("${it.temperatureC.roundToInt()}°", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
        Note(p.place)
        concerns.forEach { c -> Spacer(Modifier.height(6.dp)); Body("⚠ ${c.title}", ThinnDesignTokens.critical) }
        if (concerns.isEmpty() && w != null) Note("那裡天氣平穩。")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(onClick = {
                val weather = w ?: return@GlassButton
                val msg = FamilyCare.careMessage(p, weather, concerns.firstOrNull())
                LifeCards.sendCare(ctx, LifeCards.careCard(ctx, p, weather, msg), msg)
            }, enabled = w != null) {
                Icon(Icons.Rounded.Favorite, null); Spacer(Modifier.width(6.dp))
                Text("傳送關心小卡")
            }
            Text("移除", color = ThinnDesignTokens.critical, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
        }
    }
}

@Composable
private fun AddFamilySheet(onDismiss: () -> Unit, onAdd: (FamilyCare.Person) -> Unit) {
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

// ---------------------------------------------------------------- 天空浮水印相機

@Composable
private fun SnapPage(snapshot: WeatherSnapshot?) {
    val ctx = LocalContext.current
    var photo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var style by remember { mutableStateOf(LifeCards.SnapStyle.MAGAZINE) }
    var status by remember { mutableStateOf("") }
    val captureFile = remember { File(File(ctx.cacheDir, "snap").apply { mkdirs() }, "capture.jpg") }
    val captureUri = remember { FileProvider.getUriForFile(ctx, "${ctx.packageName}.update.files", captureFile) }
    fun load(uri: Uri) {
        photo = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                val opts = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeStream(s, null, opts)
            }?.let { rotateByExif(ctx, uri, it) }
        }.getOrNull()
        status = if (photo == null) "無法開啟這張照片" else ""
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(captureUri) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { load(it) } }
    val facts = remember(snapshot) {
        val alt = runCatching {
            val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)?.takeIf { it.hasAltitude() }?.altitude?.roundToInt() else null
        }.getOrNull()
        LifeCards.SnapFacts(io.atmos.weather.location.LocationLabels.display(snapshot?.city?.name ?: "台灣"), Instant.now(),
            snapshot?.temperatureC?.roundToInt(), snapshot?.let { io.atmos.weather.domain.model.WeatherDescriptions.current(it) },
            snapshot?.pressureHpa?.roundToInt(), alt, snapshot?.humidityPct)
    }
    val marked = remember(photo, style, facts) { photo?.let { LifeCards.watermark(it, facts.copy(time = Instant.now()), style) } }
    Note("拍下夕陽、雲海、彩虹或颱風雲，自動壓上當下的溫度、氣壓、海拔、地點與時間。使用系統相機拍照，App 不需要相機權限。")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(onClick = { runCatching { take.launch(captureUri) }.onFailure { status = "找不到相機程式" } }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp))
            Text("拍照")
        }
        GlassOutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp))
            Text("從相簿選")
        }
    }
    GlassChoiceGroup(LifeCards.SnapStyle.entries.map { it.label }, style.ordinal, { style = LifeCards.SnapStyle.entries[it] })
    marked?.let { bmp ->
        Image(bmp.asImageBitmap(), "加上天氣浮水印的照片", Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.FillWidth)
        GlassButton(onClick = {
            val uri = LifeCards.saveToGallery(ctx, bmp)
            status = if (uri == null) "儲存失敗" else "已存到相簿（Pictures/thinn-khi）"
            uri?.let { LifeCards.shareImage(ctx, it, "${facts.place} ${facts.temperature ?: ""}° · thinn-khì") }
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.IosShare, null); Spacer(Modifier.width(6.dp))
            Text("儲存並分享")
        }
    }
    if (status.isNotEmpty()) Note(status)
}

private fun rotateByExif(ctx: android.content.Context, uri: Uri, bmp: android.graphics.Bitmap): android.graphics.Bitmap = runCatching {
    val deg = ctx.contentResolver.openInputStream(uri)?.use { s ->
        when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
        }
    } ?: 0f
    if (deg == 0f) bmp else android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg) }, true)
}.getOrDefault(bmp)

// ---------------------------------------------------------------- 停班停課速查

@Composable
private fun ClosurePage() {
    val ctx = LocalContext.current
    var statuses by remember { mutableStateOf<List<WorkSchoolClosure.Status>?>(null) }
    var stamp by remember { mutableStateOf<Instant?>(null) }
    var notify by remember { mutableStateOf(LifeStore.hazardAlerts(ctx)) }
    val mine = remember { LifeSources.county(ComfortStore.current(ctx)?.name) }
    val scope = rememberCoroutineScope()
    suspend fun load(force: Boolean) {
        statuses = WorkSchoolClosure.statuses(LifeSources.alerts(ctx, if (force) 0L else 5 * 60_000L))
        stamp = LifeSources.cachedAt(ctx, "ncdr.xml")
    }
    LaunchedEffect(Unit) { load(false) }
    Note("資料來自國家災害防救科技中心的官方示警（人事行政總處發布），每 5 分鐘更新；正式公告以人事行政總處為準。")
    val list = statuses
    if (list == null) Body("正在取得最新公告…")
    else {
        val sorted = list.sortedWith(compareByDescending<WorkSchoolClosure.Status> { it.county == mine }.thenByDescending { it.closed })
        val anyClosed = list.any { it.closed }
        if (!anyClosed) GlassSurface(Modifier.fillMaxWidth()) { Body("目前全台沒有停班停課公告。") }
        sorted.forEach { s ->
            Row(Modifier.fillMaxWidth().frosted(16.dp, if (s.closed) ThinnDesignTokens.critical else null)
                .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.county + if (s.county == mine) "（你在這）" else "", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.width(132.dp))
                Text(s.text, color = if (s.closed) ThinnDesignTokens.critical else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
            }
        }
    }
    stamp?.let { Note("更新於 " + DateTimeFormatter.ofPattern("M/d HH:mm").format(it.atZone(ZoneId.systemDefault()))) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassOutlinedButton(onClick = { scope.launch { load(true) } }) { Text("重新整理") }
        GlassOutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dgpa.gov.tw/typh/daily/nds.html")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
            Text("人事行政總處公告")
        }
    }
    if (notify) NotifyBanner()
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Body("所在縣市公告時通知我"); Note("也包含土石流、淹水、河川高水位與水庫放流警戒。") }
            GlassSwitch(notify, { notify = it; LifeStore.setHazardAlerts(ctx, it) })
        }
    }
}

// ---------------------------------------------------------------- 水庫與水情警戒

@Composable
private fun WaterPage() {
    val ctx = LocalContext.current
    var levels by remember { mutableStateOf<List<Reservoirs.Level>?>(null) }
    var alerts by remember { mutableStateOf<List<NcdrFeed.Alert>>(emptyList()) }
    LaunchedEffect(Unit) {
        val now = Instant.now()
        alerts = LifeSources.alerts(ctx).filter { it.activeAt(now) && it.category in setOf("淹水", "河川高水位", "水庫放流", "土石流", "大規模崩塌", "水位警戒") }
        levels = LifeSources.reservoirs(ctx)
    }
    Heading("淹水與土石流警戒")
    if (alerts.isEmpty()) GlassSurface(Modifier.fillMaxWidth()) { Body("目前沒有淹水、河川高水位、水庫放流或土石流警戒。") }
    alerts.take(12).forEach { a ->
        GlassSurface(Modifier.fillMaxWidth()) {
            Text("${a.category} · ${a.author}", color = ThinnDesignTokens.critical, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Body(a.summary)
            Note(DateTimeFormatter.ofPattern("M/d HH:mm 發布").format(a.updated.atZone(ZoneId.systemDefault())))
        }
    }
    Heading("全台水庫蓄水")
    val list = levels
    when {
        list == null -> Body("正在取得水庫水情…")
        list.isEmpty() -> Body("暫時取不到水庫資料，請稍後再試。")
        else -> {
            val total = list.sumOf { it.storage } / list.sumOf { it.info.capacity } * 100
            Note("主要水庫整體蓄水約 ${total.roundToInt()}%（有效蓄水量 ÷ 有效容量，經濟部水利署開放資料）。")
            list.groupBy { it.info.region }.toSortedMap(compareBy { listOf("臺灣北區", "臺灣中區", "臺灣南區", "臺灣東區", "澎湖地區").indexOf(it).let { i -> if (i < 0) 9 else i } })
                .forEach { (region, items) ->
                    Note(region)
                    GlassSurface(Modifier.fillMaxWidth()) {
                        items.forEach { l -> ReservoirRow(l) }
                    }
                }
        }
    }
}

@Composable
private fun ReservoirRow(l: Reservoirs.Level) {
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

@Composable
private fun KitPage() {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(LifeStore.kit(ctx)) }
    var dating by remember { mutableStateOf<DisasterKit.Item?>(null) }
    var status by remember { mutableStateOf("") }
    fun save(next: List<DisasterKit.Item>) { items = next; LifeStore.setKit(ctx, next) }
    val packed = items.count { it.packed }
    GlassSurface(Modifier.fillMaxWidth()) {
        Text("已準備 $packed / ${items.size}", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        GlassMeter(packed / items.size.coerceAtLeast(1).toFloat(), BrandPalette.selection)
        Spacer(Modifier.height(6.dp))
        Note("水、乾糧、電池與藥品可以設定到期日，快到期前會提醒你汰換。")
    }
    items.groupBy { it.group }.forEach { (group, list) ->
        Note(group)
        GlassSurface(Modifier.fillMaxWidth()) {
            list.forEach { it2 ->
                Row(Modifier.fillMaxWidth().iosJellyPress().clickable { save(items.map { if (it.id == it2.id) it.copy(packed = !it.packed) else it }) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (it2.packed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (it2.packed) "已準備" else "未準備",
                        tint = if (it2.packed) BrandPalette.selection else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Body(it2.name)
                        it2.expires?.let { d ->
                            val soon = !d.isAfter(LocalDate.now().plusDays(30))
                            Text("到期 $d", color = if (soon) ThinnDesignTokens.critical else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                    Icon(Icons.Rounded.Event, "設定到期日", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(99.dp)).clickable { dating = it2 }.padding(8.dp))
                }
            }
        }
    }
    if (status.isNotEmpty()) Note(status)
    dating?.let { item ->
        var date by remember(item.id) { mutableStateOf(item.expires ?: LocalDate.now().plusMonths(6)) }
        IOSFormSheet("到期日：${item.name.substringBefore('（')}", onDismiss = { dating = null }, onDone = {
            save(items.map { if (it.id == item.id) it.copy(expires = date) else it })
            // Also into 我的事項, a week before, so it shows with the person's other reminders.
            val remind = date.minusDays(7).let { if (it.isBefore(LocalDate.now())) LocalDate.now().plusDays(1) else it }
            runCatching {
                val tasks = io.atmos.weather.task.TaskStore.load(ctx)
                val title = "汰換防災包：${item.name.substringBefore('（')}"
                io.atmos.weather.task.TaskStore.save(ctx, tasks.filter { it.title != title } + io.atmos.weather.task.ThinnTask(title = title,
                    note = "到期日 $date", year = remind.year, month = remind.monthValue, day = remind.dayOfMonth, hour = 9, minute = 0))
            }.onSuccess { status = "已加入「我的事項」，到期前一週提醒。" }
            dating = null
        }) {
            IOSDateDrums(date) { date = it }
            Text("清除到期日", color = ThinnDesignTokens.critical, fontSize = 16.sp, modifier = Modifier.clickable {
                save(items.map { if (it.id == item.id) it.copy(expires = null) else it }); dating = null
            }.padding(8.dp))
        }
    }
}

// ---------------------------------------------------------------- 避難收容處所

@Composable
private fun ShelterPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val here = remember { ComfortStore.current(ctx) }
    var hazard by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(LifeSources.sheltersIfLoaded()) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (all == null) all = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LifeSources.shelters(ctx) } }
    Note("全台 ${all?.size ?: "…"} 處避難收容處所（內政部消防署開放資料），已內建在 App 裡，停電斷網也查得到。")
    val labels = listOf("全部", "水災", "震災", "土石流", "海嘯")
    GlassChoiceGroup(labels, labels.indexOf(hazard ?: "全部"), { hazard = if (it == 0) null else labels[it] })
    val list = all
    when {
        here == null -> Body("請先在首頁定位，才能找出離你最近的地點。")
        list == null -> Body("正在載入…")
        else -> {
            val near = remember(list, hazard) { Shelters.nearest(list, here.coordinate.latitude, here.coordinate.longitude, hazard) }
            ShelterMap(near, here.coordinate.latitude, here.coordinate.longitude, Modifier.fillMaxWidth().height(220.dp))
            near.forEach { n ->
                GlassSurface(Modifier.fillMaxWidth().clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${n.shelter.lat},${n.shelter.lon}?q=${n.shelter.lat},${n.shelter.lon}(${Uri.encode(n.shelter.name)})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.shelter.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${n.bearing} ${Shelters.distance(n.km)}", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Note(listOf(n.shelter.address.ifBlank { n.shelter.area }, n.shelter.capacity?.let { "可收容 $it 人" }, if (n.shelter.indoor) "室內" else "室外",
                        n.shelter.hazards.takeIf { it.isNotBlank() }?.let { "適用：$it" }, if (n.shelter.vulnerable) "可安置行動不便者" else null).filterNotNull().joinToString(" · "))
                }
            }
        }
    }
    GlassOutlinedButton(onClick = { scope.launch { status = "正在下載最新名單…"; status = if (LifeSources.refreshShelters(ctx)) "已更新為最新名單" else "更新失敗，仍使用內建名單"; all = LifeSources.shelters(ctx) } }) {
        Text("更新名單")
    }
    if (status.isNotEmpty()) Note(status)
    Note("實際開設情形以各縣市政府公告為準。點選地點可開啟地圖導航。")
}

/** Where the nearest shelters lie around you, as a small offline compass map. */
@Composable
private fun ShelterMap(near: List<Shelters.Near>, lat: Double, lon: Double, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = BrandPalette.selection
    if (near.isEmpty()) return
    val radius = (near.maxOf { it.km } * 1.15).coerceAtLeast(.5)
    Canvas(modifier.frosted(18.dp)) {
        val c = center; val r = minOf(size.width, size.height) / 2 * .9f
        listOf(1f, .5f).forEach { drawCircle(ink.copy(alpha = .12f), r * it, c, style = Stroke(1.dp.toPx())) }
        near.forEach { n ->
            val ang = Math.toRadians(io.atmos.weather.transit.DrivingMode.bearing(lat, lon, n.shelter.lat, n.shelter.lon))
            val d = (n.km / radius).toFloat() * r
            drawCircle(accent, 6.dp.toPx(), Offset(c.x + (kotlin.math.sin(ang) * d).toFloat(), c.y - (kotlin.math.cos(ang) * d).toFloat()))
        }
        drawCircle(Color(0xFF2563EB), 7.dp.toPx(), c); drawCircle(Color.White, 3.dp.toPx(), c)
    }
    Note("藍點是你，綠點是最近的避難處所（外圈約 ${Shelters.distance(radius)}，上方為北）。")
}

// ---------------------------------------------------------------- 災難省電模式

@Composable
private fun DisasterSettingsPage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LifeStore.disasterMode(ctx)) }
    Note("颱風停電或大地震後基地台壅塞時使用：純黑底、高對比大字，關閉所有動畫、玻璃與天氣場景，只保留文字警報、停班停課、最近的避難處所與防災包清單，最省電也最省流量。")
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Body("開啟災難省電模式"); Note("關閉後會恢復你原本的動態與透明度設定。") }
            GlassSwitch(on, { on = it; LifeStore.setDisasterMode(ctx, it); if (it) onClose() })
        }
    }
    Note("建議同時開啟手機的省電模式並調低螢幕亮度。")
}

// ---------------------------------------------------------------- 火車、高鐵與駕車模式

@Composable
private fun TransitSettingsPage() {
    val ctx = LocalContext.current
    var driving by remember { mutableStateOf(LifeStore.drivingCard(ctx)) }
    Note("不用設定、不用按：打開天氣頁時，App 依手機定位與速度自動判斷你在搭車還是開車，卡片會自己出現在溫度下方。")
    GlassSurface(Modifier.fillMaxWidth()) {
        Body("高鐵模式")
        Note("時速 140 公里以上、沿著高鐵線：顯示下一站、約幾分鐘到，以及下一站和終點的天氣。")
        Spacer(Modifier.height(10.dp))
        Body("火車模式")
        Note("時速 30–140 公里、沿著台鐵（含山線、海線、宜蘭線、花東線與南迴線）：同樣顯示下一站與終點天氣。因為公路常貼著鐵軌，會先問你「是不是在搭火車」，在開車就點「我在開車」。")
    }
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Body("駕車模式")
                Note("以開車速度移動、離鐵路 1.2 公里以上時，用大字顯示前方約 15 與 45 分鐘路段會不會下雨，全程不需要點按。")
            }
            GlassSwitch(driving, { driving = it; LifeStore.setDrivingCard(ctx, it) })
        }
    }
    Note("需要「精確位置」權限，而且只在你開著天氣頁時判斷，不會在背景追蹤位置。上下班帶傘提醒在「天氣提醒與通知」裡設定家與公司。")
}
