package io.atmos.weather.presentation.home

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import io.atmos.weather.ui.iosAppear
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.AppContainer
import io.atmos.weather.comfort.ComfortMessages
import io.atmos.weather.comfort.ComfortStore
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassSwitch
import org.json.JSONArray
import kotlin.math.roundToInt

/** Which home cards show, and in what order. Edited like the iOS widget stack. */
object HomeCardLayout {
    val titles = linkedMapOf(
        "outing" to "今日概況", "forecast" to "24 小時預報", "daily" to "7 天預報", "radar" to "降雨雷達",
        "details" to "天氣細節與空氣品質", "commute" to "通勤天氣", "key" to "未來 6 小時重點")
    val defaultOrder = titles.keys.toList()
    /** r19 keeps the home calm: the six-hour facts overlap 今日概況, so they start hidden. */
    private val defaultHidden = setOf("key")
    private fun p(ctx: Context) = ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
    fun order(ctx: Context): List<String> {
        val saved = runCatching { JSONArray(p(ctx).getString("home_card_order_v19", "[]")) }.getOrNull()
            ?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().filter { it in titles }
        return (saved + defaultOrder.filterNot { it in saved }).distinct()
    }
    fun hidden(ctx: Context): Set<String> =
        if (!p(ctx).contains("home_card_hidden_v19")) defaultHidden
        else p(ctx).getStringSet("home_card_hidden_v19", emptySet()).orEmpty().filter { it in titles }.toSet()
    fun save(ctx: Context, order: List<String>, hidden: Set<String>) {
        p(ctx).edit().putString("home_card_order_v19", JSONArray(order).toString()).putStringSet("home_card_hidden_v19", hidden).apply()
        revision.intValue++
    }
    /** Bumped on every save so the home screen recomposes with the new layout. */
    val revision = mutableIntStateOf(0)
}

/** iOS widget-style editor: long-press the handle to drag, switch to show or hide. */
@Composable fun HomeCardEditor(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var order by remember { mutableStateOf(HomeCardLayout.order(ctx)) }
    var hidden by remember { mutableStateOf(HomeCardLayout.hidden(ctx)) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableIntStateOf(1) }
    io.atmos.weather.ui.IOSFormSheet("編輯首頁", onDismiss = onClose, onDone = { HomeCardLayout.save(ctx, order, hidden); onClose() }) {
        Text("長按右側把手拖曳排序，開關可隱藏卡片。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Column(Modifier.fillMaxWidth().testTag("home-card-editor")) {
            order.forEachIndexed { index, id ->
                val lifted = dragging == id
                val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(.62f, 480f), label = "卡片浮起")
                val shade by animateDpAsState(if (lifted) 14.dp else 0.dp, spring(.8f, 500f), label = "卡片陰影")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).onSizeChanged { rowPx = it.height.coerceAtLeast(1) }
                    .graphicsLayer {
                        translationY = if (lifted) dragY else 0f
                        val s = 1f + .035f * lift; scaleX = s; scaleY = s
                    }
                    .zIndex(if (lifted) 1f else 0f)
                    .shadow(shade, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("上移") { if (index > 0) { order = order.toMutableList().apply { add(index - 1, removeAt(index)) }; true } else false },
                            CustomAccessibilityAction("下移") { if (index < order.lastIndex) { order = order.toMutableList().apply { add(index + 1, removeAt(index)) }; true } else false })
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(HomeCardLayout.titles.getValue(id), color = if (id in hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp, modifier = Modifier.weight(1f))
                    GlassSwitch(id !in hidden, { on -> hidden = if (on) hidden - id else hidden + id })
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.DragHandle, "拖曳排序", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp).pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = id; dragY = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDrag = { change, amount ->
                                    change.consume(); dragY += amount.y
                                    val from = order.indexOf(id)
                                    val to = (from + (dragY / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    if (to != from) {
                                        order = order.toMutableList().apply { add(to, removeAt(from)) }
                                        dragY -= (to - from) * rowPx
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = { dragging = null; dragY = 0f },
                                onDragCancel = { dragging = null; dragY = 0f })
                        })
                }
            }
        }
        Text("恢復預設", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp,
            modifier = Modifier.clickable { order = HomeCardLayout.defaultOrder; hidden = setOf("key") }.padding(vertical = 8.dp))
    }
}

/** Home card: both commute ends side by side, refreshed with the home weather. */
@Composable internal fun HomeCommuteCard(revision: Any?) {
    val ctx = LocalContext.current
    val home = remember(revision) { ComfortStore.place(ctx, "home") }
    val work = remember(revision) { ComfortStore.place(ctx, "work") }
    if (home == null || work == null) return
    val weather by produceState<Pair<io.atmos.weather.domain.model.WeatherSnapshot?, io.atmos.weather.domain.model.WeatherSnapshot?>>(null to null, home, work, revision) {
        suspend fun get(p: ComfortStore.Place, id: String) = (AppContainer.repository(ctx).load(p.city(id), false) as? WeatherLoadResult.Success)?.value
        value = get(home, "commute_home") to get(work, "commute_work")
    }
    val (a, b) = weather
    io.atmos.weather.ui.ThinnAtmospherePanel(accent = Color(0xFFFF8FA3)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("通勤天氣", color = HomeInk.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(home to a, work to b).forEachIndexed { i, (place, w) ->
                    Column(Modifier.weight(1f).iosAppear(i).clip(RoundedCornerShape(16.dp)).background(HomeInk.tile).padding(12.dp)) {
                        Text(if (i == 0) "家 · ${place.name}" else "公司 · ${place.name}", color = HomeInk.secondary, fontSize = 14.sp, maxLines = 1)
                        Text(w?.let { "${it.temperatureC.roundToInt()}°" } ?: "—", color = HomeInk.primary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        val pop = w?.hourly?.take(4)?.mapNotNull { it.precipitationProbabilityPct }?.maxOrNull()
                        Text(w?.let { ComfortMessages.conditionWord(it.condition) + (pop?.let { p -> " · 降雨 $p%" } ?: "") } ?: "載入中",
                            color = HomeInk.secondary, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
            val pops = listOfNotNull(a, b).flatMap { it.hourly.take(4).mapNotNull { h -> h.precipitationProbabilityPct } }
            ComfortMessages.umbrella(pops.maxOrNull())?.let {
                Spacer(Modifier.height(10.dp)); Text("雨具：$it", color = HomeInk.primary, fontSize = 15.sp)
            }
        }
    }
}
