package io.atmos.weather.life

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.iosJellyPress
import io.atmos.weather.ui.rememberAppleReduceTransparency
import io.atmos.weather.ui.thinnGlass
import io.atmos.weather.ui.thinnSpecularEdge

/**
 * Under the temperature on the weather page: the two-sentence digest, the weather-pain level
 * when the pressure is falling, and once a day "how does today feel?" for the comfort calibration.
 */
@Composable
fun HomeLifeStrip(snapshot: WeatherSnapshot, countyWarnings: List<String>, ink: Color, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val revision by LifeStore.changes.collectAsState()
    var gathered by remember { mutableStateOf<LifeDigest.Context2?>(null) }
    LaunchedEffect(snapshot.city.coordinate, snapshot.fetchedAt, countyWarnings) {
        gathered = runCatching { LifeDigest.gather(ctx, snapshot, snapshot.city.name, countyWarnings) }.getOrNull()
    }
    val digest = remember(snapshot, gathered, revision) {
        DailyDigest.build(DailyDigest.Inputs(snapshot, gathered?.warnings ?: countyWarnings, gathered?.pressure?.level,
            gathered?.closures ?: emptyList(), LifeStore.comfortBias(ctx)))
    }
    val opaque = rememberAppleReduceTransparency()
    // Light ink over a dark sky gets a dark wash, dark ink over a bright sky a light one.
    val lightInk = ink.red + ink.green + ink.blue > 1.5f
    // Frosted glass tinted against the sky ink, so the text always reads (dark glass under white ink).
    val tint = if (lightInk) Color(0xFF0E1A16) else Color.White
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .thinnGlass(opaque, 22.dp, tint, clear = if (lightInk) .34f else .55f).thinnSpecularEdge(22.dp)
            .padding(horizontal = 16.dp, vertical = 13.dp).semantics(mergeDescendants = true) { contentDescription = "今日重點：$digest" }) {
            Text("今日重點", color = ink.copy(alpha = .72f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(digest, color = ink, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
        }
        val pressure = gathered?.pressure
        if (pressure != null && pressure.level >= PressurePain.Level.WATCH) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().iosJellyPress().clip(RoundedCornerShape(18.dp))
                .thinnGlass(opaque, 18.dp, tint, clear = if (lightInk) .34f else .55f).thinnSpecularEdge(18.dp)
                .clickable(role = Role.Button) { onOpen("pressure") }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Compress, null, Modifier.size(20.dp), tint = pressureColor(pressure.level))
                Spacer(Modifier.width(8.dp))
                Text("天氣痛 ${pressure.level.label} · 24 小時內氣壓降 ${WeatherRules.fmt(pressure.drop24h)} hPa", color = ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = ink.copy(alpha = .6f))
            }
        }
        val asked = remember(revision) { LifeStore.askedFeelingToday(ctx) }
        if (!asked) {
            var answered by remember { mutableStateOf(false) }
            AnimatedVisibility(!answered) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("今天體感", color = ink.copy(alpha = .8f), fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    ComfortCalibration.Feeling.entries.forEach { f ->
                        Box(Modifier.padding(end = 8.dp)) { GlassChip(f.label, false, ink) { LifeStore.feel(ctx, f); answered = true } }
                    }
                }
            }
        }
    }
}

internal fun pressureColor(level: PressurePain.Level) = when (level) {
    PressurePain.Level.CALM -> Color(0xFF6FB39B)
    PressurePain.Level.WATCH -> Color(0xFFD8B65A)
    PressurePain.Level.ALERT -> Color(0xFFE0883A)
    PressurePain.Level.WARNING -> Color(0xFFD5523F)
}
