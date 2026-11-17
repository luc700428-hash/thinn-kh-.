package io.atmos.weather.comfort

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.AppContainer
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.location.SavedCityStore
import io.atmos.weather.notifications.NotificationHistory
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassSwitch
import io.atmos.weather.ui.IOSV25SettingsGroup
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosJellyPress
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith
import java.time.Instant
import java.time.ZoneId

private val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")
private fun hm(h: Int, m: Int) = "%02d:%02d".format(h, m)

/** Section title in the iOS Settings style. */
@Composable private fun Header(text: String, index: Int) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp).iosAppear(index))
}

/** iOS Settings row: tinted icon tile, title / subtitle, trailing control. */
@Composable private fun Row2(icon: ImageVector, tint: Color, title: String, subtitle: String?, onClick: (() -> Unit)? = null,
                             trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.iosJellyPress().clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
        }
        trailing()
    }
}
@Composable private fun Divider() = Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(.5.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)))

@Composable private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.iosJellyPress().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp))
}

/**
 * The r18 comfort features, placed at the top of 天氣提醒與通知: morning / evening briefing,
 * smart alerts with quiet hours, home & work with commute reminders, and the notification center.
 */
@Composable fun ComfortSections(onOpenCenter: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var morning by remember { mutableStateOf(ComfortStore.morning(ctx)) }
    var evening by remember { mutableStateOf(ComfortStore.evening(ctx)) }
    var smart by remember { mutableStateOf(ComfortStore.smart(ctx)) }
    var quiet by remember { mutableStateOf(ComfortStore.quiet(ctx)) }
    var home by remember { mutableStateOf(ComfortStore.place(ctx, "home")) }
    var work by remember { mutableStateOf(ComfortStore.place(ctx, "work")) }
    var commute by remember { mutableStateOf(ComfortStore.commute(ctx)) }
    var editing by remember { mutableStateOf<String?>(null) }
    var choosingPlace by remember { mutableStateOf<String?>(null) }
    var previewNote by remember { mutableStateOf("") }
    var voiceOpen by remember { mutableStateOf(false) }

    fun preview(kind: String) {
        scope.launch {
            val city = ComfortStore.current(ctx) ?: run { previewNote = "尚未取得位置，請先在首頁定位"; return@launch }
            val w = (AppContainer.repository(ctx).load(city, false) as? WeatherLoadResult.Success)?.value
                ?: run { previewNote = "暫時取不到天氣資料"; return@launch }
            val text = if (kind == "morning") ComfortMessages.morning(w, Instant.now(), ZoneId.systemDefault(), city.name)
                else ComfortMessages.evening(w, Instant.now(), ZoneId.systemDefault(), city.name)
            previewNote = if (text != null && ComfortWorker.post(ctx, if (kind == "morning") 39001 else 39002,
                    ThinnNotificationChannels.Type.BRIEFINGS, text, XiaomiFocusBridge.Kind.WEATHER_BRIEFING)) "已送出預覽通知" else "通知未開啟或資料不足"
        }
    }

    Column(Modifier.fillMaxWidth().testTag("comfort-sections"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header("每日天氣早晚報", 0)
        Box(Modifier.iosAppear(1)) { IOSV25SettingsGroup {
            Row2(Icons.Rounded.WbSunny, Color(0xFFFF9F0A), "早報", "今天的溫度、穿著、帶不帶傘、空氣與紫外線") {
                if (morning.enabled) { TimeChip(hm(morning.hour, morning.minute)) { editing = "morning" }; Spacer(Modifier.width(8.dp)) }
                GlassSwitch(morning.enabled, { morning = morning.copy(enabled = it); ComfortStore.setMorning(ctx, morning) })
            }
            Divider()
            Row2(Icons.Rounded.Bedtime, Color(0xFF5E5CE6), "晚報", "明天的天氣、明早出門溫度與要帶什麼") {
                if (evening.enabled) { TimeChip(hm(evening.hour, evening.minute)) { editing = "evening" }; Spacer(Modifier.width(8.dp)) }
                GlassSwitch(evening.enabled, { evening = evening.copy(enabled = it); ComfortStore.setEvening(ctx, evening) })
            }
            Divider()
            Row2(Icons.Rounded.NotificationsActive, Color(0xFF34C759), "立即預覽", previewNote.ifBlank { "用目前天氣送一則早報或晚報看看" }) {
                TimeChip("早報") { preview("morning") }; Spacer(Modifier.width(6.dp)); TimeChip("晚報") { preview("evening") }
            }
        } }

        Header("智慧提醒", 2)
        Box(Modifier.iosAppear(3)) { IOSV25SettingsGroup {
            val items = listOf(
                Triple(ComfortMessages.Smart.RAIN_SOON, Icons.Rounded.Umbrella to Color(0xFF0A84FF), "快下雨時提醒" to "接下來 3 小時內可能下雨就通知"),
                Triple(ComfortMessages.Smart.TEMP_SWING, Icons.Rounded.Thermostat to Color(0xFFFF6B35), "晚上變涼提醒" to "今晚會比現在冷 7° 以上時提醒帶外套"),
                Triple(ComfortMessages.Smart.UV_HIGH, Icons.Rounded.LightMode to Color(0xFFFFB300), "紫外線過強" to "白天紫外線指數 8 以上時提醒防曬"),
                Triple(ComfortMessages.Smart.AIR_BAD, Icons.Rounded.Air to Color(0xFF8E8E93), "空氣品質變差" to "AQI 超過 100 時提醒戴口罩、關窗"))
            items.forEachIndexed { i, (type, look, text) ->
                if (i > 0) Divider()
                Row2(look.first, look.second, text.first, text.second) {
                    GlassSwitch(type in smart, { on -> ComfortStore.setSmart(ctx, type, on); smart = ComfortStore.smart(ctx) })
                }
            }
            Divider()
            Row2(Icons.Rounded.DoNotDisturbOn, Color(0xFF5856D6), "安靜時段", "這段時間不發智慧提醒（早晚報與速報不受影響）") {
                TimeChip("${quiet.first.hour}:00–${quiet.second.hour}:00") { editing = "quiet" }
            }
        } }

        Header("地點與通勤", 4)
        Box(Modifier.iosAppear(5)) { IOSV25SettingsGroup {
            Row2(Icons.Rounded.Home, Color(0xFF34C759), "家", home?.name ?: "尚未設定", { choosingPlace = "home" }) {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            Row2(Icons.Rounded.Work, Color(0xFF0A84FF), "公司／學校", work?.name ?: "尚未設定", { choosingPlace = "work" }) {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            Row2(Icons.Rounded.DirectionsTransit, Color(0xFFFF375F), "通勤提醒",
                if (home == null || work == null) "先設定家與公司，出門前 15 分鐘提醒兩地天氣" else "${weekNames.indices.filter { commute.weekdays and (1 shl it) != 0 }.joinToString("") { weekNames[it] }} · 出門前 15 分鐘提醒") {
                GlassSwitch(commute.enabled && home != null && work != null, { on -> commute = commute.copy(enabled = on); ComfortStore.setCommute(ctx, commute) })
            }
            if (commute.enabled && home != null && work != null) {
                Divider()
                Row2(Icons.Rounded.Schedule, Color(0xFFFF9500), "出門時間", "上班 / 下班") {
                    TimeChip(hm(commute.outHour, commute.outMinute)) { editing = "out" }; Spacer(Modifier.width(6.dp))
                    TimeChip(hm(commute.backHour, commute.backMinute)) { editing = "back" }
                }
                Divider()
                Box(Modifier.padding(14.dp)) {
                    io.atmos.weather.ui.GlassWeekdayChoices(weekNames, weekNames.indices.filter { commute.weekdays and (1 shl it) != 0 }.toSet()) { i ->
                        commute = commute.copy(weekdays = (commute.weekdays xor (1 shl i)).takeIf { it != 0 } ?: commute.weekdays)
                        ComfortStore.setCommute(ctx, commute)
                    }
                }
            }
        } }

        Header("通知與語音", 6)
        Box(Modifier.iosAppear(7)) { IOSV25SettingsGroup {
            Row2(Icons.Rounded.RecordVoiceOver, Color(0xFFAF52DE), "播報聲音", "選擇最自然的中文語音並試聽", { voiceOpen = true }) {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            val count = remember { NotificationHistory.all(ctx).size }
            Row2(Icons.Rounded.Inbox, Color(0xFFFF3B30), "通知中心", if (count == 0) "還沒有通知" else "最近 $count 則通知", onOpenCenter) {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
    }

    if (voiceOpen) io.atmos.weather.voice.VoicePickerSheet { voiceOpen = false }
    // Time pickers share the iOS form sheet and drum wheels.
    editing?.let { which ->
        var h by remember(which) { mutableIntStateOf(when (which) { "morning" -> morning.hour; "evening" -> evening.hour; "out" -> commute.outHour; "back" -> commute.backHour; else -> quiet.first.hour }) }
        var m by remember(which) { mutableIntStateOf(when (which) { "morning" -> morning.minute; "evening" -> evening.minute; "out" -> commute.outMinute; "back" -> commute.backMinute; else -> quiet.second.hour }) }
        val title = when (which) { "morning" -> "早報時間"; "evening" -> "晚報時間"; "out" -> "上班出門時間"; "back" -> "下班出門時間"; else -> "安靜時段" }
        io.atmos.weather.ui.IOSFormSheet(title, onDismiss = { editing = null }, onDone = {
            when (which) {
                "morning" -> { morning = morning.copy(hour = h, minute = m); ComfortStore.setMorning(ctx, morning) }
                "evening" -> { evening = evening.copy(hour = h, minute = m); ComfortStore.setEvening(ctx, evening) }
                "out" -> { commute = commute.copy(outHour = h, outMinute = m); ComfortStore.setCommute(ctx, commute) }
                "back" -> { commute = commute.copy(backHour = h, backMinute = m); ComfortStore.setCommute(ctx, commute) }
                else -> { ComfortStore.setQuiet(ctx, h, m.coerceIn(0, 23)); quiet = ComfortStore.quiet(ctx) }
            }
            editing = null
        }) {
            if (which == "quiet") {
                // Hours only: start on the left, end on the right.
                Text("開始 ${h}:00　結束 ${m}:00", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
                io.atmos.weather.ui.IOSClockDrums(h, m.coerceIn(0, 23), { h = it }, { m = it.coerceIn(0, 23) })
                Text("右側滾輪代表結束的整點（0–23）。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else io.atmos.weather.ui.IOSClockDrums(h, m, { h = it }, { m = it })
        }
    }
    choosingPlace?.let { key ->
        val saved = remember { SavedCityStore.all(ctx) }
        val current = remember { ComfortStore.current(ctx) }
        io.atmos.weather.ui.IOSFormSheet(if (key == "home") "選擇家" else "選擇公司／學校", onDismiss = { choosingPlace = null }) {
            val options = listOfNotNull(current?.let { "目前位置 · ${it.name}" to it }) + saved.map { it.name to it }
            if (options.isEmpty()) Text("請先在首頁定位或新增城市。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            options.forEachIndexed { i, (label, city) ->
                Row(Modifier.fillMaxWidth().iosAppear(i).iosJellyPress().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface).clickable {
                        val place = ComfortStore.Place(city.name, city.coordinate.latitude, city.coordinate.longitude)
                        ComfortStore.setPlace(ctx, key, place)
                        if (key == "home") home = place else work = place
                        choosingPlace = null
                    }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (i == 0 && current != null) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, modifier = Modifier.weight(1f))
                }
            }
            if ((if (key == "home") home else work) != null) Text("清除", color = MaterialTheme.colorScheme.error, fontSize = 17.sp,
                modifier = Modifier.clickable { ComfortStore.setPlace(ctx, key, null); if (key == "home") home = null else work = null; choosingPlace = null }.padding(8.dp))
        }
    }
}

/** iOS-style notification center: grouped stacks, newest first, expandable. */
@Composable fun NotificationCenterPage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(NotificationHistory.all(ctx)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    io.atmos.weather.ui.WorkspacePageSheet(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.background) {
        io.atmos.weather.ui.IOSSettingsHeader("通知中心", onClose)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                    Text("沒有通知", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最近", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("全部清除", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp,
                        modifier = Modifier.iosJellyPress().clickable { NotificationHistory.clear(ctx); items = emptyList() }.padding(6.dp))
                }
            }
            val groups = items.groupBy { NotificationHistory.group(it.kind) }.entries.sortedByDescending { e -> e.value.maxOf { it.at } }
            groups.forEachIndexed { gi, (group, list) ->
                val open = expanded == group || list.size == 1
                Column(Modifier.fillMaxWidth().iosAppear(gi)) {
                    if (list.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(group, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (open) "收合" else "${list.size} 則", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
                            modifier = Modifier.clickable { expanded = if (open) null else group }.padding(4.dp))
                    }
                    androidx.compose.animation.AnimatedContent(open, label = "通知堆疊",
                        transitionSpec = { (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                            androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(.82f, 420f))) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) }) { isOpen ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (if (isOpen) list else list.take(1)).forEachIndexed { i, e ->
                                NotificationCard(e, zone, group, stacked = !isOpen && list.size > 1, index = i) { if (!isOpen) expanded = group }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun NotificationCard(e: NotificationHistory.Entry, zone: ZoneId, group: String, stacked: Boolean, index: Int, onTap: () -> Unit) {
    val p = MaterialTheme.colorScheme
    val ago = remember(e.at) {
        val mins = ((System.currentTimeMillis() - e.at) / 60_000).coerceAtLeast(0)
        when { mins < 1 -> "現在"; mins < 60 -> "$mins 分鐘前"; mins < 24 * 60 -> "${mins / 60} 小時前"
            else -> java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm").format(Instant.ofEpochMilli(e.at).atZone(zone)) }
    }
    val tint = when (group) { "地震" -> Color(0xFFFF3B30); "警特報" -> Color(0xFFFF9500); "鬧鐘" -> Color(0xFFFF9F0A); "天氣提醒" -> Color(0xFF0A84FF); else -> Color(0xFF8E8E93) }
    Box(Modifier.fillMaxWidth().padding(bottom = if (stacked) 10.dp else 0.dp)) {
        // A stacked group shows the edges of the cards behind, like iOS.
        if (stacked) {
            Box(Modifier.matchParentSize().padding(horizontal = 18.dp).offset(y = 10.dp).clip(RoundedCornerShape(22.dp)).background(p.surface.copy(alpha = .55f)))
            Box(Modifier.matchParentSize().padding(horizontal = 9.dp).offset(y = 5.dp).clip(RoundedCornerShape(22.dp)).background(p.surface.copy(alpha = .8f)))
        }
        Row(Modifier.fillMaxWidth().iosJellyPress().clip(RoundedCornerShape(22.dp)).background(p.surface)
            .clickable(onClick = onTap).padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(tint), contentAlignment = Alignment.Center) {
                Text(group.take(1), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(e.title, color = p.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(ago, color = p.onSurfaceVariant, fontSize = 14.sp)
                }
                Text(e.detail, color = p.onSurface, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4)
            }
        }
    }
}
