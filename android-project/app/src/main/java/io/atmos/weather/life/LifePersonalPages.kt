package io.atmos.weather.life

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.comfort.ComfortStore
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.presentation.home.WeatherShareCard
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.widgets.WidgetWeatherArt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private fun rainColor(p: Int?) = when { p == null -> Color(0xFF8E98A4); p >= 60 -> Color(0xFF3E7FD6); p >= 30 -> Color(0xFF5FA8E8); else -> Color(0xFF5FB39A) }

// ---------------------------------------------------------------- 行事曆天氣

@Composable
internal fun CalendarPageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var allowed by remember { mutableStateOf(CalendarWeather.allowed(ctx)) }
    var rows by remember { mutableStateOf<List<CalendarWeather.Row>?>(null) }
    var done by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(allowed) {
        if (!allowed) return@LaunchedEffect
        val events = CalendarWeather.events(ctx)
        rows = events.map { CalendarWeather.Row(it, null, null) }
        // Fill in place by place, so the list appears at once and the weather arrives as it is found.
        val out = events.map { CalendarWeather.Row(it, null, null) }.toMutableList()
        events.forEachIndexed { i, e ->
            val place = CalendarWeather.locate(ctx, e.location)
            val w = place?.let { LifeSources.weather(ctx, it.name, it.lat, it.lon) }
            out[i] = CalendarWeather.Row(e, place, w?.let { CalendarWeather.forecast(it, e) })
            rows = out.toList()
        }
        done = true
    }
    val list = rows
    val wet = list?.count { (it.forecast?.rain ?: 0) >= 30 } ?: 0
    LifeHero(Color(0xFFFF3B30)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val today = LocalDate.now()
            Column(Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(Color.White), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(DateTimeFormatter.ofPattern("E", Locale.TAIWAN).format(today), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFFF3B30)).padding(vertical = 3.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("${today.dayOfMonth}", color = Color(0xFF1C1C1E), fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(when { !allowed -> "行程旁直接看天氣"; list == null -> "正在讀取行事曆"; list.isEmpty() -> "未來 7 天沒有外出行程"
                    else -> "未來 7 天 ${list.size} 個行程" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(when { !allowed -> "只讀取、不修改你的行事曆"; list.isNullOrEmpty() -> "行程要填地點才會出現"
                    wet == 0 -> "都不太會下雨"; else -> "$wet 個行程可能遇到雨，記得帶傘" },
                    color = if (wet > 0) rainColor(60) else dim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (!allowed) {
            Spacer(Modifier.height(14.dp))
            GlassButton(onClick = { ask.launch(Manifest.permission.READ_CALENDAR) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text("允許讀取行事曆")
            }
            Text("沒有跳出詢問？到系統設定開啟", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, modifier = Modifier.clickable {
                runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 10.dp))
        }
    }
    if (!allowed) { Note("只讀取未來 7 天「有填地點」的行程，資料只留在手機上。"); return }
    val zone = ZoneId.systemDefault()
    list.orEmpty().groupBy { it.event.begin.atZone(zone).toLocalDate() }.forEach { (day, items) ->
        val today = LocalDate.now()
        SectionTitle(when (day) { today -> "今天"; today.plusDays(1) -> "明天"; else -> DateTimeFormatter.ofPattern("M/d（E）", Locale.TAIWAN).format(day) },
            Icons.Rounded.Event, IOSColors.red, "${items.size} 個")
        items.forEach { r -> EventCard(r, done) }
    }
    if (list != null && list.isEmpty()) GlassSurface(Modifier.fillMaxWidth()) {
        Body("在行事曆的行程裡加上地點（例如「臺南市美術館」），就會出現在這裡。")
    }
}

@Composable
private fun EventCard(r: CalendarWeather.Row, done: Boolean) {
    val zone = ZoneId.systemDefault()
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val f = r.forecast
    Row(Modifier.fillMaxWidth().frosted(20.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (r.event.allDay) "全天" else DateTimeFormatter.ofPattern("HH:mm").format(r.event.begin.atZone(zone)),
                color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (!r.event.allDay) Text(DateTimeFormatter.ofPattern("HH:mm").format(r.event.end.atZone(zone)), color = dim, fontSize = 14.sp)
        }
        Box(Modifier.padding(horizontal = 10.dp).width(3.dp).height(46.dp).clip(RoundedCornerShape(2.dp)).background(rainColor(f?.rain)))
        Column(Modifier.weight(1f)) {
            Text(r.event.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = dim)
                Text(r.event.location, color = dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(when { f != null -> CalendarWeather.advice(f); r.place == null && done -> "找不到這個地點"; r.place == null -> "正在查詢地點…"; else -> "預報尚未涵蓋這個時間" },
                color = if (f != null && (f.rain ?: 0) >= 30) rainColor(f.rain) else dim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (f != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            f.code?.let { Image(painterResource(WeatherShareCard.buddy(WidgetWeatherArt.condition(it), true)), null, Modifier.size(38.dp)) }
            Text(f.temperature?.let { "$it°" } ?: "—", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            f.rain?.let { Text("$it%", color = rainColor(it), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------- 自訂天氣規則

private fun metricIcon(m: WeatherRules.Metric) = when (m) {
    WeatherRules.Metric.RAIN -> Icons.Rounded.Umbrella to IOSColors.blue; WeatherRules.Metric.HIGH -> Icons.Rounded.Thermostat to IOSColors.orange
    WeatherRules.Metric.LOW -> Icons.Rounded.AcUnit to IOSColors.cyan; WeatherRules.Metric.WIND -> Icons.Rounded.Air to IOSColors.teal
    WeatherRules.Metric.UV -> Icons.Rounded.WbSunny to IOSColors.yellow; WeatherRules.Metric.AQI -> Icons.Rounded.Masks to IOSColors.green
}

@Composable
internal fun RulesPageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var rules by remember { mutableStateOf(LifeStore.rules(ctx)) }
    var editing by remember { mutableStateOf<WeatherRules.Rule?>(null) }
    var results by remember { mutableStateOf<Map<String, WeatherRules.Verdict?>>(emptyMap()) }
    var added by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun save(next: List<WeatherRules.Rule>) { rules = next; LifeStore.setRules(ctx, next) }
    val active = rules.filter { it.enabled }
    val next = remember(rules) {
        val now = java.time.ZonedDateTime.now()
        active.mapNotNull { r -> io.atmos.weather.comfort.ComfortScheduler.next(now, r.hour, r.minute, r.weekdays)?.let { it to r } }.minByOrNull { it.first }
    }
    LifeHero(IOSColors.indigo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelTile("${active.size}", IOSColors.indigo, 84.dp, small = "條啟用")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (rules.isEmpty()) "讓天氣自動提醒你" else "下一次檢查", color = dim, fontSize = 14.sp)
                Text(next?.let { (t, r) -> "${DateTimeFormatter.ofPattern("E HH:mm", Locale.TAIWAN).format(t)} · ${r.name}" } ?: "選一個範本或自己組合",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                Text("時間到了檢查天氣，條件全部成立才通知。", color = dim, fontSize = 14.sp)
            }
        }
    }
    if (active.isNotEmpty()) NotifyBanner()
    if (rules.isNotEmpty()) SectionTitle("我的規則", Icons.Rounded.AutoAwesome, IOSColors.indigo, "${rules.size} 條")
    rules.forEach { r ->
        Column(Modifier.fillMaxWidth().frosted(20.dp).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { editing = r }) {
                    Text(r.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(describeWhen(r), color = dim, fontSize = 14.sp)
                }
                GlassSwitch(r.enabled, { on -> save(rules.map { if (it.id == r.id) it.copy(enabled = on) else it }) })
            }
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.conditions.forEach { c ->
                    val (icon, tint) = metricIcon(c.metric)
                    Row(Modifier.frosted(99.dp, tint).padding(start = 6.dp, end = 11.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IOSIconTile(icon, tint, 20.dp); Spacer(Modifier.width(6.dp))
                        Text("${c.metric.label}${c.op.label} ${WeatherRules.fmt(c.value)}${c.metric.unit}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (r.message.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("→ ${r.message}", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp) }
            results[r.id]?.let { v ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().frosted(14.dp, if (v.fires) Color(0xFF34C759) else Color(0xFF8E98A4)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (v.fires) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = if (v.fires) Color(0xFF34C759) else dim)
                    Spacer(Modifier.width(8.dp))
                    Column { Text(if (v.fires) "現在條件成立，到時會通知" else "現在條件不成立，不會通知", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(v.explanation, color = dim, fontSize = 14.sp, lineHeight = 19.sp) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                GlassOutlinedButton(onClick = {
                    scope.launch {
                        val c = ComfortStore.current(ctx)
                        val w = c?.let { LifeSources.weather(ctx, it.name, it.coordinate.latitude, it.coordinate.longitude) }
                        results = results + (r.id to w?.let { WeatherRules.evaluate(r, it, LocalDate.now(), ZoneId.systemDefault()) })
                    }
                }) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("立即測試") }
                GlassOutlinedButton(onClick = { editing = r }) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(4.dp)); Text("編輯") }
                GlassOutlinedButton(onClick = { save(rules.filter { it.id != r.id }) }) { Icon(Icons.Rounded.Delete, null, tint = ThinnDesignTokens.critical) }
            }
        }
    }
    GlassButton(onClick = { editing = WeatherRules.Rule(UUID.randomUUID().toString(), "我的規則", 127, 20, 0, WeatherRules.Period.TOMORROW,
        listOf(WeatherRules.Condition(WeatherRules.Metric.RAIN, WeatherRules.Op.BELOW, 30.0)), "明天適合出門") }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("自己組合一條規則")
    }
    SectionTitle("一鍵範本", Icons.Rounded.Bolt, IOSColors.orange)
    WeatherRules.templates().forEach { t ->
        val has = rules.any { it.name == t.name }
        val (icon, tint) = metricIcon(t.conditions.first().metric)
        Row(Modifier.fillMaxWidth().frosted(18.dp).iosJellyPress().clickable(enabled = !has) {
            save(rules + t.copy(id = UUID.randomUUID().toString())); added = "已加入「${t.name}」，到時間會自動檢查。"
        }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IOSIconTile(icon, tint, 34.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(describe(t), color = dim, fontSize = 14.sp, lineHeight = 19.sp)
            }
            Icon(if (has) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircle, if (has) "已加入" else "加入", tint = if (has) Color(0xFF34C759) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
    if (added.isNotEmpty()) Note(added)
    editing?.let { r -> RuleEditor(r, onDismiss = { editing = null }) { saved ->
        save(if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved); editing = null
    } }
}

private fun describeWhen(r: WeatherRules.Rule): String {
    val days = if (r.weekdays == 127) "每天" else "每週" + weekNames.indices.filter { r.weekdays and (1 shl it) != 0 }.joinToString("") { weekNames[it] }
    return "$days ${"%02d:%02d".format(r.hour, r.minute)} 檢查${r.period.label}"
}

// ---------------------------------------------------------------- 家人天氣關懷

@Composable
internal fun FamilyPageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var people by remember { mutableStateOf(LifeStore.family(ctx)) }
    var adding by remember { mutableStateOf(false) }
    val weather = remember { mutableStateMapOf<String, WeatherSnapshot?>() }
    val worries = remember { mutableStateMapOf<String, List<FamilyCare.Concern>>() }
    LaunchedEffect(people) {
        val now = Instant.now()
        val alerts = LifeSources.alerts(ctx).filter { it.activeAt(now) && it.author.contains("氣象署") }
        people.forEach { p ->
            val w = LifeSources.weather(ctx, p.place, p.lat, p.lon)
            weather[p.id] = w
            val county = LifeSources.county(p.place)
            worries[p.id] = w?.let { FamilyCare.concerns(p, it, alerts.filter { a -> NcdrFeed.mentions(a, county) }.map { a -> a.category + "特報" }) } ?: emptyList()
        }
    }
    val needCare = people.count { worries[it.id].orEmpty().isNotEmpty() }
    LifeHero(IOSColors.pink) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (people.isNotEmpty()) CountyStatusMap({ null }, Modifier.width(110.dp).height(160.dp),
                people.map { Offset(it.lon.toFloat(), it.lat.toFloat()) to (if (worries[it.id].orEmpty().isEmpty()) Color(0xFF34C759) else Color(0xFFFF3B30)) },
                description = "家人所在地圖")
            else LevelTile("❤", IOSColors.pink, 84.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (people.isEmpty()) "關心家人那裡的天氣" else if (needCare == 0) "家人那裡天氣都平穩" else "$needCare 位家人那裡要留意",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
                Text(if (people.isEmpty()) "豪雨、強風、驟降 8°、寒冷或高溫時提醒你，還能一鍵傳關心小卡到 LINE。"
                    else "${people.size} 位家人 · 綠點平穩、紅點要留意", color = dim, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
    if (people.isNotEmpty()) NotifyBanner()
    people.forEach { p -> FamilyCardV2(p, weather[p.id], worries[p.id].orEmpty(), loading = !weather.containsKey(p.id)) {
        people = people.filter { it.id != p.id }; LifeStore.setFamily(ctx, people)
    } }
    GlassButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("新增家人")
    }
    if (adding) AddFamilySheet(onDismiss = { adding = false }) { p -> people = people + p; LifeStore.setFamily(ctx, people); adding = false }
}

@Composable
private fun FamilyCardV2(p: FamilyCare.Person, w: WeatherSnapshot?, concerns: List<FamilyCare.Concern>, loading: Boolean, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().frosted(22.dp, if (concerns.isNotEmpty()) Color(0xFFFF3B30) else null).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(99.dp)).background(IOSColors.pink.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Text(p.nickname.ifBlank { p.relation }.take(1), color = IOSColors.pink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.nickname.ifBlank { p.relation }, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp)); Badge(p.relation, IOSColors.pink)
                }
                Text(p.place, color = dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (w != null) {
                Image(painterResource(WeatherShareCard.buddy(w.condition, w.isDay)), null, Modifier.size(44.dp))
                Text("${w.temperatureC.roundToInt()}°", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Note("正在取得那裡的天氣…")
            w == null -> Note("暫時取不到那裡的天氣。")
            concerns.isEmpty() -> InsightTiles(Triple("高／低", "${w.maxTempC?.roundToInt() ?: "—"}°/${w.minTempC?.roundToInt() ?: "—"}°", "今天"),
                Triple("降雨", w.nextHourRainProbabilityPct?.let { "$it%" } ?: "—", "下一小時"), Triple("狀態", "平穩", "不用擔心"))
            else -> concerns.forEach { c ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30)); Spacer(Modifier.width(6.dp))
                    Column { Text(c.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(c.detail, color = dim, fontSize = 14.sp, lineHeight = 19.sp) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(onClick = {
                val weather = w ?: return@GlassButton
                val msg = FamilyCare.careMessage(p, weather, concerns.firstOrNull())
                LifeCards.sendCare(ctx, LifeCards.careCard(ctx, p, weather, msg), msg)
            }, enabled = w != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Favorite, null); Spacer(Modifier.width(6.dp)); Text("傳送關心小卡")
            }
            GlassIconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "移除", tint = ThinnDesignTokens.critical) }
        }
        if (confirm) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("確定移除 ${p.nickname.ifBlank { p.relation }}？", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("取消", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, modifier = Modifier.clickable { confirm = false }.padding(8.dp))
            Text("移除", color = ThinnDesignTokens.critical, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
        }
    }
}

// ---------------------------------------------------------------- 火車、高鐵與駕車模式

@Composable
internal fun TransitPageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var driving by remember { mutableStateOf(LifeStore.drivingCard(ctx)) }
    LifeHero(IOSColors.blue) {
        Text("不用設定、不用按", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("開著天氣頁時，App 依定位與速度判斷你在搭車還是開車，卡片自己出現在溫度下方。", color = dim, fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(14.dp))
        InsightTiles(Triple("高鐵模式", "≥140 km/h", "沿高鐵線"), Triple("火車模式", "30–140", "沿台鐵各線"), Triple("駕車模式", "20–150", "離鐵路 1.2 公里外"))
    }
    listOf(Triple(Icons.Rounded.Train, IOSColors.orange, "高鐵模式" to "顯示下一站、約幾分鐘到站，以及下一站和終點的天氣。"),
        Triple(Icons.Rounded.DirectionsRailway, IOSColors.blue, "火車模式" to "含山線、海線、宜蘭線、花東線與南迴線。公路常貼著鐵軌，所以會先問「是不是在搭火車」，在開車就點「我在開車」。"),
        Triple(Icons.Rounded.DirectionsCar, IOSColors.green, "駕車模式" to "大字顯示前方約 15 與 45 分鐘路段會不會下雨，全程不需要點按。")).forEach { (icon, tint, t) ->
        Row(Modifier.fillMaxWidth().frosted(18.dp).padding(14.dp), verticalAlignment = Alignment.Top) {
            IOSIconTile(icon, tint, 34.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.first, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(t.second, color = dim, fontSize = 14.sp, lineHeight = 20.sp)
            }
            if (t.first == "駕車模式") GlassSwitch(driving, { driving = it; LifeStore.setDrivingCard(ctx, it) })
        }
    }
    Note("需要「精確位置」權限，只在你開著天氣頁時判斷，不會在背景追蹤位置。上下班帶傘提醒在「天氣提醒與通知」裡設定家與公司。")
}
