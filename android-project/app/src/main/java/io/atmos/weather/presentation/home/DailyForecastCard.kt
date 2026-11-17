package io.atmos.weather.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.DailyWeather
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosJellyPress
import io.atmos.weather.ui.rememberAppleReduceMotion
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val weekNames = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")

/** The days the card can show: today onward, only complete highs and lows. */
internal fun forecastDays(w: WeatherSnapshot, today: LocalDate): List<DailyWeather> {
    val zone = runCatching { ZoneId.of(w.timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei"))
    val source = w.daily.ifEmpty { DailyWeather.fromHourly(w.hourly, zone) }
    return source.filter { !it.date.isBefore(today) && it.highC != null && it.lowC != null }.sortedBy { it.date }.take(7)
}

/**
 * iOS Weather's multi-day list: day, icon, rain chance, low, a range bar placed on the scale of the
 * whole week (with a dot for "now" on today's row), and high. Tapping a row expands its details.
 */
@Composable internal fun DailyForecastCard(weather: WeatherSnapshot, modifier: Modifier = Modifier) {
    val zone = remember(weather.timeZoneId) { runCatching { ZoneId.of(weather.timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei")) }
    val today = remember(weather.fetchedAt) { weather.fetchedAt.atZone(zone).toLocalDate() }
    val days = remember(weather) { forecastDays(weather, today) }
    if (days.size < 2) return
    val weekLow = days.mapNotNull { it.lowC }.min(); val weekHigh = days.mapNotNull { it.highC }.max()
    val reduce = rememberAppleReduceMotion()
    // Bars grow from the centre on first appearance.
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = io.atmos.weather.ui.AppleMotion.fluid)) }
    var open by remember { mutableStateOf<LocalDate?>(null) }
    io.atmos.weather.ui.ThinnAtmospherePanel(modifier.testTag("daily-forecast"), accent = Color(0xFF9CC9FF)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${days.size} 天預報", color = Color.White.copy(alpha = .7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            days.forEachIndexed { i, d ->
                Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .14f)))
                val label = when (d.date) { today -> "今天"; today.plusDays(1) -> "明天"; else -> weekNames[d.date.dayOfWeek.value - 1] }
                val pop = d.precipitationProbabilityPct?.takeIf { it >= 20 }
                val lo = d.lowC!!; val hi = d.highC!!
                Column(Modifier.fillMaxWidth().iosAppear(i).iosJellyPress().clip(RoundedCornerShape(12.dp))
                    .clickable { open = if (open == d.date) null else d.date }
                    .semantics { contentDescription = "$label ${hourlyWeatherName(d.weatherCode, 12)} 最低 ${lo.roundToInt()} 度 最高 ${hi.roundToInt()} 度" + (pop?.let { " 降雨機率 $it%" } ?: "") }
                    .padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(52.dp))
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(weatherVector(d.weatherCode, 12), null, tint = Color(0xFFFFDB92), modifier = Modifier.size(22.dp))
                            if (pop != null) Text("$pop%", color = Color(0xFF8FD0FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${lo.roundToInt()}°", color = Color.White.copy(alpha = .6f), fontSize = 17.sp, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                        // Range bar on the week's scale; warm colours on the hot end like iOS.
                        val now = if (d.date == today) weather.temperatureC else null
                        Canvas(Modifier.weight(1f).height(6.dp).padding(horizontal = 10.dp)) {
                            val span = (weekHigh - weekLow).coerceAtLeast(1.0)
                            fun x(t: Double) = (((t - weekLow) / span).toFloat().coerceIn(0f, 1f)) * size.width
                            drawRoundRect(Color.White.copy(alpha = .14f), cornerRadius = CornerRadius(size.height / 2))
                            val a = x(lo); val b = x(hi); val mid = (a + b) / 2f; val half = (b - a) / 2f * grow.value
                            drawRoundRect(Brush.horizontalGradient(listOf(tempColor(lo), tempColor(hi)), mid - half, mid + half),
                                Offset(mid - half, 0f), Size((half * 2).coerceAtLeast(size.height), size.height), CornerRadius(size.height / 2))
                            if (now != null) {
                                drawCircle(Color.Black.copy(alpha = .35f), size.height * .95f, Offset(x(now), size.height / 2))
                                drawCircle(Color.White, size.height * .7f, Offset(x(now), size.height / 2))
                            }
                        }
                        Text("${hi.roundToInt()}°", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                    }
                    androidx.compose.animation.AnimatedVisibility(open == d.date,
                        enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(.78f, 380f)) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(.9f, 500f)) + androidx.compose.animation.fadeOut()) {
                        Text(listOfNotNull(hourlyWeatherName(d.weatherCode, 12),
                            d.precipitationProbabilityPct?.let { "降雨機率 $it%" },
                            "溫差 ${(hi - lo).roundToInt()}°",
                            io.atmos.weather.comfort.ComfortMessages.umbrella(d.precipitationProbabilityPct)).joinToString(" · "),
                            color = Color.White.copy(alpha = .78f), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, start = 52.dp))
                    }
                }
            }
        }
    }
}

/** iOS-like temperature colours: cool blue → green → yellow → orange → red. */
private fun tempColor(t: Double): Color = when {
    t < 10 -> Color(0xFF5AC8FA); t < 16 -> Color(0xFF64D2FF); t < 21 -> Color(0xFF7ED957)
    t < 26 -> Color(0xFFFFD60A); t < 31 -> Color(0xFFFF9F0A); else -> Color(0xFFFF453A)
}
