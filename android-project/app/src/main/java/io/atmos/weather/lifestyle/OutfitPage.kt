package io.atmos.weather.lifestyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherAdviceEngine
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.life.InsightTiles
import io.atmos.weather.life.LifeHero
import io.atmos.weather.life.SectionTitle
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.IOSColors
import io.atmos.weather.ui.IOSIconTile
import io.atmos.weather.ui.iosAppear
import kotlin.math.roundToInt

/**
 * 今日穿搭: the outfit at a glance (six pieces with symbols), why — the numbers it was judged on —
 * when to go out, what to bring, and the day's other reminders as cards, most important first.
 */
@Composable
fun OutfitPage(weather: WeatherSnapshot, stale: Boolean, onOpenSettings: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val p = MaterialTheme.colorScheme
    val revision by LifestylePreferenceStore.changes.collectAsState()
    val mode = remember(revision) { LifestylePreferenceStore.mode(ctx) }
    val preferences = remember(revision) { LifestylePreferenceStore.load(ctx) }
    val plan = remember(weather, mode, stale, preferences) { LifestyleAdvisor.recommend(weather, mode, stale, preferences = preferences) }
    val tips = remember(weather) { WeatherAdviceEngine.suggest(weather).take(6) }
    val feels = weather.feelsLikeC ?: weather.temperatureC
    // The page's colour follows the day: icy blue, fresh green, warm amber or hot red.
    val accent = when { feels < 12 -> Color(0xFF5E9BFF); feels < 20 -> Color(0xFF3FB6A8); feels < 28 -> Color(0xFFF2A33A); else -> Color(0xFFFF6B4A) }
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        io.atmos.weather.ui.IOSSettingsHeader("今日穿搭", onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeHero(accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("體感 ${feels.roundToInt()}°", color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(plan.headline, color = p.onSurface, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Checkroom, null, Modifier.size(36.dp), tint = accent)
                    }
                }
                if (plan.temperatureSummary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(plan.temperatureSummary, color = p.onSurfaceVariant, fontSize = 15.sp, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(12.dp))
                io.atmos.weather.ui.GlassChoiceGroup(OutingMode.entries.map { it.title }, mode.ordinal, { LifestylePreferenceStore.mode(ctx, OutingMode.entries[it]) })
                if (!plan.weatherFresh) {
                    Spacer(Modifier.height(8.dp))
                    Text("這份建議用的是較舊的天氣，出發前請重新整理。", color = p.error, fontSize = 14.sp)
                }
            }
            SectionTitle("怎麼穿", Icons.Rounded.Checkroom, IOSColors.orange)
            val pieces = listOf(
                Piece("外套", plan.coatDecision, Icons.Rounded.Layers, IOSColors.indigo),
                Piece("上衣", plan.top, Icons.Rounded.Checkroom, IOSColors.orange),
                Piece("褲子", plan.bottoms, Icons.Rounded.Accessibility, IOSColors.blue),
                Piece("搭配", plan.layer, Icons.Rounded.Style, IOSColors.pink),
                Piece("鞋子", plan.shoes, Icons.Rounded.Hiking, IOSColors.brown),
                Piece("雨具", plan.umbrella, Icons.Rounded.Umbrella, IOSColors.cyan),
            ).filter { it.value.isNotBlank() }
            pieces.chunked(2).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, piece -> PieceTile(piece, Modifier.weight(1f).fillMaxHeight().iosAppear(r * 2 + i)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SectionTitle("為什麼這樣穿", Icons.Rounded.Thermostat, IOSColors.red)
            val rain = weather.hourly.take(6).mapNotNull { it.precipitationProbabilityPct }.maxOrNull() ?: weather.nextHourRainProbabilityPct
            InsightTiles(
                Triple("氣溫", "${weather.temperatureC.roundToInt()}°", weather.minTempC?.let { lo -> weather.maxTempC?.let { hi -> "今天 ${lo.roundToInt()}–${hi.roundToInt()}°" } }),
                Triple("體感", "${feels.roundToInt()}°", "偏好：${preferences.comfort.label}"),
                Triple("6 小時降雨", rain?.let { "$it%" } ?: "—", weather.windKmh?.let { "風 ${it.roundToInt()} km/h" }),
            )
            Text(plan.evidence, color = p.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
            if (plan.timeAdvice.isNotBlank() || plan.bring.isNotEmpty()) {
                SectionTitle("出門前", Icons.Rounded.DirectionsWalk, IOSColors.green)
                Card {
                    if (plan.timeAdvice.isNotBlank()) Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(20.dp).padding(top = 2.dp), tint = IOSColors.green)
                        Spacer(Modifier.width(10.dp))
                        Text(plan.timeAdvice, color = p.onSurface, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    if (plan.bring.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("隨身帶", color = p.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            plan.bring.forEach { item ->
                                Text(item, color = p.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(IOSColors.green.copy(alpha = .14f))
                                        .border(.5.dp, IOSColors.green.copy(alpha = .4f), RoundedCornerShape(99.dp)).padding(horizontal = 11.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
            val reminders = (listOf(plan.reminder) + plan.extraReminders).filter { it.isNotBlank() }.distinct().take(4)
            if (reminders.isNotEmpty() || tips.isNotEmpty()) {
                SectionTitle("今天也要注意", Icons.Rounded.NotificationsActive, IOSColors.orange)
                tips.forEachIndexed { i, tip ->
                    val tint = when { tip.priority >= 8 -> IOSColors.red; tip.priority >= 5 -> IOSColors.orange; else -> IOSColors.blue }
                    Row(Modifier.fillMaxWidth().iosAppear(i).clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = .08f))
                        .border(.5.dp, tint.copy(alpha = .3f), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
                        IOSIconTile(if (tip.priority >= 8) Icons.Rounded.Warning else Icons.Rounded.Info, tint, 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tip.heading, color = p.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(tip.detail, color = p.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
                reminders.filter { r -> tips.none { it.heading in r || r in it.detail } }.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp).padding(top = 2.dp), tint = IOSColors.green)
                        Spacer(Modifier.width(8.dp))
                        Text(r, color = p.onSurface, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.onSurface.copy(alpha = .05f))
                .clickable(onClickLabel = "穿搭個人偏好", onClick = onOpenSettings).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IOSIconTile(Icons.Rounded.Tune, IOSColors.gray, 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("個人偏好", color = p.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("怕冷或怕熱、穿搭風格、幾 % 降雨就提醒帶傘", color = p.onSurfaceVariant, fontSize = 14.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = p.onSurfaceVariant)
            }
            Text("依現在與未來 6 小時的氣溫、體感、風雨推估；官方災害警報以中央氣象署發布為準。", color = p.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

private data class Piece(val label: String, val value: String, val icon: ImageVector, val tint: Color)

@Composable
private fun PieceTile(piece: Piece, modifier: Modifier) {
    val p = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(piece.tint.copy(alpha = .09f))
        .border(.5.dp, piece.tint.copy(alpha = .28f), RoundedCornerShape(20.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IOSIconTile(piece.icon, piece.tint, 30.dp)
            Spacer(Modifier.width(9.dp))
            Text(piece.label, color = p.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(piece.value, color = p.onSurface, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val p = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.onSurface.copy(alpha = .05f))
        .border(.5.dp, p.onSurface.copy(alpha = .08f), RoundedCornerShape(20.dp)).padding(14.dp), content = content)
}
