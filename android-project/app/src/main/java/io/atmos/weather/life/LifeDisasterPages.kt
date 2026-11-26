package io.atmos.weather.life

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.comfort.ComfortStore
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val CLOSED = Color(0xFFD5523F)
private val NORMAL = Color(0xFF5FB39A)
private val QUIET = Color(0xFF8E98A4)
private fun fmtTime(i: Instant) = DateTimeFormatter.ofPattern("M/d HH:mm").format(i.atZone(ZoneId.systemDefault()))
private fun openUrl(ctx: android.content.Context, url: String) =
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

// ---------------------------------------------------------------- 停班停課速查

@Composable
internal fun ClosurePageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var statuses by remember { mutableStateOf<List<WorkSchoolClosure.Status>?>(null) }
    var stamp by remember { mutableStateOf<Instant?>(null) }
    var notify by remember { mutableStateOf(LifeStore.hazardAlerts(ctx)) }
    var refreshing by remember { mutableStateOf(false) }
    val mine = remember { LifeSources.county(ComfortStore.current(ctx)?.name) }
    val scope = rememberCoroutineScope()
    suspend fun load(force: Boolean) {
        refreshing = true
        statuses = WorkSchoolClosure.statuses(LifeSources.alerts(ctx, if (force) 0L else 5 * 60_000L))
        stamp = LifeSources.cachedAt(ctx, "ncdr.xml"); refreshing = false
    }
    LaunchedEffect(Unit) { load(false) }
    val list = statuses
    val closed = list?.filter { it.closed }.orEmpty()
    val announced = list?.filter { it.updated != null && !it.closed }.orEmpty()
    val my = list?.firstOrNull { it.county == mine }
    val accent = if (closed.isNotEmpty()) CLOSED else NORMAL
    LifeHero(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelTile(if (list == null) "…" else "${closed.size}", accent, 84.dp, small = "縣市停課")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(when {
                    list == null -> "正在取得最新公告"
                    closed.isEmpty() -> "目前沒有停班停課"
                    else -> "${closed.size} 個縣市宣布停班停課"
                }, color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
                Text(stamp?.let { "更新於 ${fmtTime(it)} · 每 5 分鐘自動更新" } ?: "人事行政總處公告", color = dim, fontSize = 14.sp)
            }
        }
        if (mine != null && my != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().frosted(16.dp, if (my.closed) CLOSED else null).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp), tint = if (my.closed) CLOSED else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("你在 $mine", color = dim, fontSize = 14.sp)
                    Text(my.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
                }
            }
        }
        if (list != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountyStatusMap({ c -> list.firstOrNull { it.county == c }?.let { s -> if (s.closed) CLOSED else if (s.updated != null) NORMAL else null } },
                    Modifier.width(150.dp).height(230.dp), description = "停班停課地圖：" + closed.joinToString("、") { it.county })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendRow(CLOSED, "停班停課", closed.size)
                    LegendRow(NORMAL, "照常上班上課", announced.size)
                    LegendRow(QUIET, "尚未發布", list.size - closed.size - announced.size)
                }
            }
        }
    }
    if (closed.isNotEmpty()) {
        SectionTitle("已宣布停班停課", Icons.Rounded.EventBusy, CLOSED, "${closed.size} 個")
        closed.forEach { s -> CountyRow(s, mine) }
    }
    if (announced.isNotEmpty()) {
        SectionTitle("照常上班上課", Icons.Rounded.EventAvailable, NORMAL, "${announced.size} 個")
        announced.forEach { s -> CountyRow(s, mine) }
    }
    if (list != null) {
        val quiet = list.filter { it.updated == null }
        if (quiet.isNotEmpty()) {
            SectionTitle("尚未發布訊息", Icons.Rounded.HourglassEmpty, QUIET, "${quiet.size} 個")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                quiet.forEach { Text(it.county.removeSuffix("縣").removeSuffix("市").let { n -> if (it.county.startsWith("嘉義") || it.county.startsWith("新竹")) it.county else n },
                    color = if (it.county == mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp,
                    fontWeight = if (it.county == mine) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.frosted(99.dp).padding(horizontal = 12.dp, vertical = 7.dp)) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassOutlinedButton(onClick = { scope.launch { load(true) } }, enabled = !refreshing) {
            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (refreshing) "更新中…" else "重新整理")
        }
        GlassOutlinedButton(onClick = { openUrl(ctx, "https://www.dgpa.gov.tw/typh/daily/nds.html") }) {
            Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text("官方公告")
        }
    }
    SectionTitle("通知", Icons.Rounded.NotificationsActive, IOSColors.red)
    if (notify) NotifyBanner()
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Body("所在縣市公告時通知我"); Note("也包含土石流、淹水、河川高水位與水庫放流警戒。") }
            GlassSwitch(notify, { notify = it; LifeStore.setHazardAlerts(ctx, it) })
        }
    }
    Note("資料來自國家災害防救科技中心的官方示警（人事行政總處發布）；正式公告以人事行政總處為準。")
}

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("$count", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CountyRow(s: WorkSchoolClosure.Status, mine: String?) {
    Row(Modifier.fillMaxWidth().frosted(16.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Badge(s.county, if (s.closed) CLOSED else NORMAL)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, lineHeight = 21.sp)
            s.updated?.let { Text(fmtTime(it) + if (s.county == mine) " · 你在這" else "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
        }
    }
}

// ---------------------------------------------------------------- 水庫與水情警戒

private val waterCategories = setOf("淹水", "河川高水位", "水庫放流", "土石流", "大規模崩塌", "水位警戒")
private fun categoryColor(c: String) = when (c) {
    "土石流", "大規模崩塌" -> Color(0xFFA0623A); "淹水" -> Color(0xFF3E7FD6); "河川高水位", "水位警戒" -> Color(0xFF2FA3B5)
    "水庫放流" -> Color(0xFF5E7BD9); else -> Color(0xFFE0883A)
}
internal fun storageColor(pct: Double) = when { pct >= 60 -> Color(0xFF4F9FD8); pct >= 40 -> Color(0xFFD8B65A); pct >= 25 -> Color(0xFFE0883A); else -> Color(0xFFD5523F) }

@Composable
internal fun WaterPageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var levels by remember { mutableStateOf<List<Reservoirs.Level>?>(null) }
    var alerts by remember { mutableStateOf<List<NcdrFeed.Alert>>(emptyList()) }
    var region by remember { mutableStateOf("全部") }
    LaunchedEffect(Unit) {
        val now = Instant.now()
        alerts = LifeSources.alerts(ctx).filter { it.activeAt(now) && it.category in waterCategories }
        levels = LifeSources.reservoirs(ctx)
    }
    val list = levels
    val total = list?.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.storage } / l.sumOf { it.info.capacity } * 100 }
    val accent = total?.let { storageColor(it) } ?: Color(0xFF4F9FD8)
    LifeHero(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(((total ?: 0.0) / 100).toFloat(), accent, total?.let { "${it.roundToInt()}%" } ?: "…", "全台蓄水", 118.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(when { list == null -> "正在取得水情"; total == null -> "暫時取不到水庫資料"; else -> "整體水情${Reservoirs.word(total)}" },
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(if (alerts.isEmpty()) "目前沒有淹水、土石流警戒" else "${alerts.size} 則水情警戒生效中",
                    color = if (alerts.isEmpty()) dim else categoryColor(alerts.first().category), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("有效蓄水量 ÷ 有效容量，經濟部水利署每小時資料", color = dim, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        if (!list.isNullOrEmpty()) {
            Spacer(Modifier.height(14.dp))
            val low = list.minByOrNull { it.percent }!!
            val regions = list.groupBy { it.info.region }.mapValues { (_, l) -> l.sumOf { it.storage } / l.sumOf { it.info.capacity } * 100 }
            val driest = regions.minByOrNull { it.value }
            InsightTiles(
                Triple("最低水庫", "${low.percent.coerceAtMost(100.0).roundToInt()}%", low.info.name),
                Triple("最吃緊地區", driest?.let { "${it.value.coerceAtMost(100.0).roundToInt()}%" } ?: "—", driest?.key?.removePrefix("臺灣")),
                Triple("監測水庫", "${list.size} 座", "主要公共給水"),
            )
        }
        if (alerts.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountyStatusMap({ c -> alerts.firstOrNull { NcdrFeed.mentions(it, c) }?.let { categoryColor(it.category) } },
                    Modifier.width(140.dp).height(214.dp), description = "水情警戒地圖")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.groupBy { it.category }.forEach { (cat, l) -> LegendRow(categoryColor(cat), cat, l.size) }
                }
            }
        }
    }
    if (alerts.isNotEmpty()) {
        SectionTitle("警戒中", Icons.Rounded.Warning, IOSColors.red, "${alerts.size} 則")
        alerts.take(15).forEach { a ->
            Column(Modifier.fillMaxWidth().frosted(18.dp).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(a.category, categoryColor(a.category)); Spacer(Modifier.width(8.dp))
                    Text("${a.author} · ${fmtTime(a.updated)}", color = dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Body(a.summary)
            }
        }
    }
    if (!list.isNullOrEmpty()) {
        SectionTitle("各水庫蓄水", Icons.Rounded.WaterDrop, IOSColors.cyan)
        val regions = listOf("全部") + list.map { it.info.region }.distinct().sortedBy { listOf("臺灣北區", "臺灣中區", "臺灣南區", "臺灣東區", "澎湖地區").indexOf(it).let { i -> if (i < 0) 9 else i } }
        IOSSegmentedControl(regions.map { it.removePrefix("臺灣").removeSuffix("地區") }, regions.indexOf(region).coerceAtLeast(0), { region = regions[it] })
        GlassSurface(Modifier.fillMaxWidth()) {
            list.filter { region == "全部" || it.info.region == region }.forEach { ReservoirRow(it) }
        }
    }
}

// ---------------------------------------------------------------- 防災包清單

private fun groupIcon(g: String) = when (g) {
    "飲食" -> Icons.Rounded.Restaurant to IOSColors.orange; "照明通訊" -> Icons.Rounded.FlashlightOn to IOSColors.yellow
    "醫療衛生" -> Icons.Rounded.MedicalServices to IOSColors.red; "重要物品" -> Icons.Rounded.Badge to IOSColors.blue
    else -> Icons.Rounded.Checkroom to IOSColors.indigo
}

@Composable
internal fun KitPageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var items by remember { mutableStateOf(LifeStore.kit(ctx)) }
    var dating by remember { mutableStateOf<DisasterKit.Item?>(null) }
    var status by remember { mutableStateOf("") }
    fun save(next: List<DisasterKit.Item>) { items = next; LifeStore.setKit(ctx, next) }
    val packed = items.count { it.packed }
    val today = LocalDate.now()
    val expiring = DisasterKit.expiring(items, today)
    val nextExpiry = items.mapNotNull { it.expires }.filter { !it.isBefore(today) }.minOrNull()
    val fraction = packed / items.size.coerceAtLeast(1).toFloat()
    val accent = when { fraction >= 1f -> Color(0xFF34C759); fraction >= .5f -> Color(0xFFD8B65A); else -> Color(0xFFE0883A) }
    LifeHero(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(fraction, accent, "$packed/${items.size}", "已準備", 118.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(when { fraction >= 1f -> "防災包已備齊"; packed == 0 -> "開始準備防災包"; else -> "還差 ${items.size - packed} 項" },
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("點一下打勾；右邊的日曆設定到期日，到期前一週提醒你汰換。", color = dim, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        InsightTiles(
            Triple("即將到期", "${expiring.size} 項", if (expiring.isEmpty()) "30 天內沒有" else expiring.first().name.substringBefore('（')),
            Triple("下次到期", nextExpiry?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "—", nextExpiry?.let { "${java.time.temporal.ChronoUnit.DAYS.between(today, it)} 天後" } ?: "尚未設定"),
            Triple("建議存量", "3 天", "每人每天 3 公升水"),
        )
    }
    items.groupBy { it.group }.forEach { (group, list) ->
        val (icon, tint) = groupIcon(group)
        SectionTitle(group, icon, tint, "${list.count { it.packed }}/${list.size}")
        GlassSurface(Modifier.fillMaxWidth()) {
            list.forEachIndexed { i, it2 ->
                if (i > 0) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(start = 36.dp).height(.5.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .1f)))
                Row(Modifier.fillMaxWidth().iosJellyPress().clickable { save(items.map { if (it.id == it2.id) it.copy(packed = !it.packed) else it }) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (it2.packed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (it2.packed) "已準備" else "未準備",
                        tint = if (it2.packed) Color(0xFF34C759) else dim, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it2.name, color = if (it2.packed) dim else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, lineHeight = 22.sp)
                        it2.expires?.let { d ->
                            val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
                            Text(if (days < 0) "已過期 ${-days} 天" else "到期 ${d.monthValue}/${d.dayOfMonth}（${days} 天後）",
                                color = if (days <= 30) CLOSED else dim, fontSize = 14.sp, fontWeight = if (days <= 30) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                    GlassIconButton(onClick = { dating = it2 }) { Icon(Icons.Rounded.Event, "設定到期日", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
    if (status.isNotEmpty()) Note(status)
    if (packed > 0) Text("全部清除勾選", color = CLOSED, fontSize = 15.sp, modifier = Modifier.clickable { save(items.map { it.copy(packed = false) }) }.padding(vertical = 8.dp))
    dating?.let { item ->
        var date by remember(item.id) { mutableStateOf(item.expires ?: LocalDate.now().plusMonths(6)) }
        IOSFormSheet("到期日：${item.name.substringBefore('（')}", onDismiss = { dating = null }, onDone = {
            save(items.map { if (it.id == item.id) it.copy(expires = date) else it })
            val remind = date.minusDays(7).let { if (it.isBefore(LocalDate.now())) LocalDate.now().plusDays(1) else it }
            runCatching {
                val tasks = io.atmos.weather.task.TaskStore.load(ctx)
                val title = "汰換防災包：${item.name.substringBefore('（')}"
                io.atmos.weather.task.TaskStore.save(ctx, tasks.filter { it.title != title } + io.atmos.weather.task.ThinnTask(title = title,
                    note = "到期日 $date", year = remind.year, month = remind.monthValue, day = remind.dayOfMonth, hour = 9, minute = 0))
            }.onSuccess { status = "已加入「我的事項」，${remind.monthValue}/${remind.dayOfMonth} 早上 9 點提醒。" }
            dating = null
        }) {
            IOSDateDrums(date) { date = it }
            if (item.expires != null) Text("清除到期日", color = CLOSED, fontSize = 16.sp, modifier = Modifier.clickable {
                save(items.map { if (it.id == item.id) it.copy(expires = null) else it }); dating = null
            }.padding(8.dp))
        }
    }
}

// ---------------------------------------------------------------- 避難收容處所

@Composable
internal fun ShelterPageV2() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    val here = remember { ComfortStore.current(ctx) }
    var hazard by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(LifeSources.sheltersIfLoaded()) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (all == null) all = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LifeSources.shelters(ctx) } }
    val list = all
    val near = remember(list, hazard, here) { if (list == null || here == null) emptyList() else Shelters.nearest(list, here.coordinate.latitude, here.coordinate.longitude, hazard, 15) }
    fun navigate(s: Shelters.Shelter) = openUrl(ctx, "geo:${s.lat},${s.lon}?q=${s.lat},${s.lon}(${Uri.encode(s.name)})")
    val first = near.firstOrNull()
    LifeHero(Color(0xFF34C759)) {
        Text("最近的避難處所", color = dim, fontSize = 14.sp)
        when {
            here == null -> Body("請先在首頁定位，才能找出離你最近的地點。")
            list == null -> Body("正在載入…")
            first == null -> Body("附近沒有符合的地點。")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(first.shelter.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                        Text(first.shelter.address.ifBlank { first.shelter.area }, color = dim, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val deg = io.atmos.weather.transit.DrivingMode.bearing(here.coordinate.latitude, here.coordinate.longitude, first.shelter.lat, first.shelter.lon)
                        androidx.compose.foundation.layout.Box(Modifier.size(54.dp).frosted(99.dp, Color(0xFF34C759)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Navigation, "方向", Modifier.size(30.dp).rotate(deg.toFloat()), tint = Color.White)
                        }
                        Text("${first.bearing} ${Shelters.distance(first.km)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val h = hazard
                val within = list.count { Shelters.km(here.coordinate.latitude, here.coordinate.longitude, it.lat, it.lon) <= 3.0 && (h == null || it.hazards.contains(h)) }
                InsightTiles(
                    Triple("3 公里內", "$within 處", h?.let { "適用$it" } ?: "全部災害"),
                    Triple("可收容", first.shelter.capacity?.let { "$it 人" } ?: "—", if (first.shelter.indoor) "室內" else "室外"),
                    Triple("行動不便", if (first.shelter.vulnerable) "可安置" else "未註明", "最近這一處"),
                )
                Spacer(Modifier.height(12.dp))
                GlassButton(onClick = { navigate(first.shelter) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Directions, null); Spacer(Modifier.width(6.dp)); Text("開始導航")
                }
            }
        }
    }
    val labels = listOf("全部", "水災", "震災", "土石流", "海嘯")
    IOSSegmentedControl(labels, labels.indexOf(hazard ?: "全部"), { hazard = if (it == 0) null else labels[it] })
    if (here != null && near.isNotEmpty()) {
        ShelterMap(near, here.coordinate.latitude, here.coordinate.longitude, Modifier.fillMaxWidth().height(230.dp))
        SectionTitle("附近 ${near.size} 處", Icons.Rounded.MapsHomeWork, IOSColors.green)
        near.drop(1).forEach { n ->
            Row(Modifier.fillMaxWidth().frosted(18.dp).iosJellyPress().clickable { navigate(n.shelter) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.shelter.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(n.shelter.capacity?.let { "可收容 $it 人" }, if (n.shelter.indoor) "室內" else "室外",
                        n.shelter.hazards.takeIf { it.isNotBlank() }?.let { "適用：$it" }).joinToString(" · "), color = dim, fontSize = 14.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Shelters.distance(n.km), color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(n.bearing + "方", color = dim, fontSize = 14.sp)
                }
            }
        }
    }
    GlassOutlinedButton(onClick = { scope.launch { status = "正在下載最新名單…"; status = if (LifeSources.refreshShelters(ctx)) "已更新為最新名單" else "更新失敗，仍使用內建名單"; all = LifeSources.shelters(ctx) } }) {
        Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(6.dp)); Text("更新名單")
    }
    if (status.isNotEmpty()) Note(status)
    Note("全台 ${list?.size ?: "…"} 處（內政部消防署開放資料），已內建在 App 裡，停電斷網也查得到；實際開設以各縣市政府公告為準。")
}

// ---------------------------------------------------------------- 災難省電模式

@Composable
internal fun DisasterSettingsV2(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(LifeStore.disasterMode(ctx)) }
    LifeHero(Color(0xFFFFD60A)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).frosted(20.dp, Color.Black), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.BatterySaver, null, Modifier.size(40.dp), tint = Color(0xFFFFD60A))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (on) "災難省電模式已開啟" else "停電或斷網時用", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("純黑底、高對比大字，最省電也最省流量。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        InsightTiles(Triple("動畫與玻璃", "全部關閉", "天氣場景停止"), Triple("網路", "只抓文字", "一次一則小請求"), Triple("避難處所", "離線可查", "內建全台名單"))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("開啟災難省電模式", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            GlassSwitch(on, { on = it; LifeStore.setDisasterMode(ctx, it); if (it) onClose() })
        }
    }
    SectionTitle("開啟後會看到", Icons.Rounded.Visibility, IOSColors.blue)
    GlassSurface(Modifier.fillMaxWidth()) {
        listOf(Icons.Rounded.Warning to "你所在縣市的警特報與示警", Icons.Rounded.School to "停班停課", Icons.Rounded.MapsHomeWork to "最近的 3 處避難處所與方向",
            Icons.Rounded.Backpack to "防災包準備進度", Icons.Rounded.Call to "119、110 緊急電話").forEach { (icon, text) ->
            Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Body(text)
            }
        }
    }
    Note("關閉後會恢復你原本的動態與透明度設定。建議同時開啟手機的省電模式並調低螢幕亮度。")
}
