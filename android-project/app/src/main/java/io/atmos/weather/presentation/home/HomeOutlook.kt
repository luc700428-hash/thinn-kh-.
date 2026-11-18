package io.atmos.weather.presentation.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.comfort.ComfortMessages
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.insights.OutdoorMode
import io.atmos.weather.insights.WeatherInsightMath
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosJellyPress
import java.time.Instant
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The home page's "what happens next", in the plain-words style of 氣象詳解: one sentence about
 * rain, one about tomorrow, and three tiles (best time to go out, sunrise / sunset, air).
 * Pure text building lives in [build] so it can be unit tested; missing data is left out.
 */
internal object HomeOutlook {
    data class Tile(val label: String, val value: String, val caption: String)
    data class Outlook(val rain: String, val tomorrow: String?, val tiles: List<Tile>)
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun build(w: WeatherSnapshot, now: Instant): Outlook {
        val zone = runCatching { ZoneId.of(w.timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei"))
        val next = w.hourly.filter { !it.at.isBefore(now.minusSeconds(1800)) && it.at.isBefore(now.plusSeconds(12 * 3600)) }.sortedBy { it.at }
        val pops = next.mapNotNull { h -> h.precipitationProbabilityPct?.let { h to it } }
        val raining = w.condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM, WeatherCondition.FREEZING_RAIN)
        val rain = when {
            pops.isEmpty() -> "暫時沒有逐時降雨預報"
            raining -> pops.drop(1).firstOrNull { it.second < 30 }?.let { "正在下雨，預計 ${hm.format(it.first.at.atZone(zone))} 前後趨緩" }
                ?: "正在下雨，接下來 12 小時都可能有雨"
            else -> pops.firstOrNull { it.second >= 50 }?.let { "預計 ${hm.format(it.first.at.atZone(zone))} 前後可能下雨（降雨機率 ${it.second}%）" }
                ?: if (pops.maxOf { it.second } >= 30) "接下來 12 小時偶有降雨機會，最高 ${pops.maxOf { it.second }}%" else "接下來 12 小時不太會下雨"
        }
        val today = now.atZone(zone).toLocalDate()
        val days = forecastDays(w, today)
        val t0 = days.firstOrNull { it.date == today }?.highC ?: w.maxTempC
        val t1 = days.firstOrNull { it.date == today.plusDays(1) }
        val tomorrow = t1?.highC?.let { high ->
            val base = "明天 ${t1.lowC?.roundToInt()}–${high.roundToInt()}°"
            val diff = t0?.let { high - it }
            base + when {
                diff == null -> ""
                diff <= -3 -> "，比今天涼 ${abs(diff).roundToInt()}°"
                diff >= 3 -> "，比今天熱 ${diff.roundToInt()}°"
                else -> "，和今天差不多"
            } + (t1.precipitationProbabilityPct?.takeIf { it >= 50 }?.let { "，可能下雨" } ?: "")
        }
        val tiles = buildList {
            // Only waking hours count as a "best time to go out"; 02:00 is never a useful answer.
            WeatherInsightMath.windows(w, OutdoorMode.WALK, now).firstOrNull { win ->
                !win.caution && win.hours.first().at.atZone(zone).hour in 6..20
            }?.let { best ->
                add(Tile("最佳出門", hm.format(best.hours.first().at.atZone(zone)), "至 ${hm.format(best.hours.last().at.plusSeconds(3600).atZone(zone))}"))
            }
            val rise = w.sunrise; val set = w.sunset
            if (set != null && now.isBefore(set) && (rise == null || now.isAfter(rise))) add(Tile("日落", hm.format(set.atZone(zone)), minutesLeft(now, set)))
            else if (rise != null && now.isBefore(rise)) add(Tile("日出", hm.format(rise.atZone(zone)), minutesLeft(now, rise)))
            w.usAqi?.let { add(Tile("空氣品質", it.toString(), ComfortMessages.aqiWord(it))) }
            if (size < 3) (next.mapNotNull { it.uvIndex }.maxOrNull() ?: w.currentUv)?.takeIf { it >= 1 }?.let { add(Tile("紫外線", it.roundToInt().toString(), ComfortMessages.uvWord(it) + "量級")) }
            if (size < 3) next.mapNotNull { h -> h.temperatureC?.takeIf { it.isFinite() }?.let { h to it } }.minByOrNull { it.second }?.let { (h, t) ->
                add(Tile("最低溫", "${t.roundToInt()}°", "約 ${hm.format(h.at.atZone(zone))}"))
            }
        }.take(3)
        return Outlook(rain, tomorrow, tiles)
    }
    private fun minutesLeft(now: Instant, then: Instant): String {
        val m = ((then.epochSecond - now.epochSecond) / 60).coerceAtLeast(0)
        // Short enough for a third-width tile on a phone.
        return if (m >= 60) "約 ${(m + 30) / 60} 小時後" else "$m 分鐘後"
    }

    /** Text for the system share sheet. */
    fun shareText(w: WeatherSnapshot, place: String, now: Instant): String {
        val o = build(w, now)
        return listOfNotNull("$place 現在 ${w.temperatureC.roundToInt()}°，${ComfortMessages.conditionWord(w.condition)}",
            if (w.minTempC != null && w.maxTempC != null) "今天 ${w.minTempC.roundToInt()}–${w.maxTempC.roundToInt()}°" else null,
            o.rain, o.tomorrow, "— thinn-khì").joinToString("\n")
    }
}

@Composable internal fun HomeOutlookCard(weather: WeatherSnapshot, place: String, onDetails: () -> Unit) {
    val ctx = LocalContext.current
    val now = remember(weather.fetchedAt) { Instant.now() }
    val outlook = remember(weather, now) { HomeOutlook.build(weather, now) }
    val ink = HomeInk.primary; val soft = HomeInk.secondary
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var sharing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    io.atmos.weather.ui.ThinnAtmospherePanel(Modifier.testTag("home-outlook"), accent = Color(0xFFBFE3FF)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("接下來", color = soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).iosJellyPress().clip(RoundedCornerShape(999.dp)).background(HomeInk.tile)
                    .clickable(role = Role.Button, onClickLabel = "分享今日天氣卡片") {
                        if (sharing) return@clickable
                        sharing = true
                        scope.launch {
                            val caption = HomeOutlook.shareText(weather, place, Instant.now())
                            // The picture is drawn off the main thread; plain text if that fails.
                            val card = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                runCatching { WeatherShareCard.render(ctx, weather, place) }.getOrNull()
                            }
                            val shared = card != null && WeatherShareCard.share(ctx, card, caption)
                            if (!shared) runCatching {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, caption), "分享天氣").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            sharing = false
                        }
                    }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.IosShare, "分享天氣", tint = ink, modifier = Modifier.size(18.dp))
                }
            }
            Text(outlook.rain, color = ink, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
            outlook.tomorrow?.let { Text(it, color = soft, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 2.dp)) }
            if (outlook.tiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HomeTileRow {
                    outlook.tiles.forEachIndexed { i, t -> HomeTile(t.label, t.value, t.caption, Modifier.weight(1f).iosAppear(i)) }
                    repeat(3 - outlook.tiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).iosJellyPress().clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onDetails).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("最佳出門時段與天氣細節", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = soft)
            }
        }
    }
}

/**
 * A soft glow behind the hero where the sun (or moon) really is in the sky: warm by day, cool at
 * night, breathing slowly. It only adds light, so rain, thunder flashes and leaves stay on top.
 */
@Composable internal fun Modifier.skyGlow(night: Boolean, still: Boolean): Modifier {
    val light = io.atmos.weather.ui.GlassLight.current(follow = true)
    val reduce = io.atmos.weather.ui.rememberAppleReduceMotion()
    // Kept as State and read only while drawing: reading it here would recompose the whole hero
    // (temperature, texts, tiles) on every frame of the breathing light.
    val breath: androidx.compose.runtime.State<Float> = if (still || reduce) androidx.compose.runtime.rememberUpdatedState(.5f) else
        androidx.compose.animation.core.rememberInfiniteTransition(label = "天光").animateFloat(0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(5200, easing = io.atmos.weather.ui.AppleMotion.fluid),
                androidx.compose.animation.core.RepeatMode.Reverse), label = "天光呼吸")
    val tint = if (night) Color(0xFFBFD4FF) else Color(0xFFFFE7A8)
    val lx = light.x; val ly = light.y
    return this.then(Modifier.drawBehind {
        val b = breath.value
        val center = androidx.compose.ui.geometry.Offset(size.width * (.5f + .42f * lx), size.height * (.45f + .5f * ly))
        val radius = size.maxDimension * (.62f + .06f * b)
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(tint.copy(alpha = .20f + .08f * b), tint.copy(alpha = .06f), Color.Transparent), center, radius), radius, center)
    })
}

