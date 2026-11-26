package io.atmos.weather.transit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.comfort.RainSoon
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.rememberAppleReduceTransparency
import io.atmos.weather.ui.thinnGlass
import kotlinx.coroutines.delay
import java.time.Instant

/** 駕車模式 card: large, glanceable, nothing to tap. Rain ahead in about 15 and 45 minutes. */
@Composable
fun DrivingModeCard(drive: DrivingMode.Drive?, ink: Color) {
    data class Road(val ahead: DrivingMode.Ahead, val text: String, val wet: Boolean)
    var road by remember { mutableStateOf<List<Road>>(emptyList()) }
    // Re-checked every 5 minutes; the heading is rounded so small curves do not refetch.
    val key = drive?.let { "${(it.lat * 20).toInt()}:${(it.lon * 20).toInt()}:${(it.headingDeg / 45).toInt()}" }
    LaunchedEffect(key) {
        val d = drive ?: return@LaunchedEffect
        while (true) {
            road = DrivingMode.aheads(d).map { a ->
                val slots = RainSoon.fetch(a.lat, a.lon)
                val at = Instant.now().plusSeconds(a.minutes * 60L)
                val slot = slots?.minByOrNull { kotlin.math.abs(it.at.epochSecond - at.epochSecond) }
                val mm = slot?.mm?.times(4)
                Road(a, DrivingMode.describe(mm, slot?.chance), (mm ?: 0.0) >= .8)
            }
            delay(5 * 60_000L)
        }
    }
    AnimatedVisibility(drive != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val d = drive ?: return@AnimatedVisibility
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
            .thinnGlass(rememberAppleReduceTransparency(), 24.dp, MaterialTheme.colorScheme.surface)
            .padding(16.dp).semantics { contentDescription = "駕車模式，時速 ${d.speedKmh}，" + road.joinToString("，") { "${it.ahead.minutes} 分鐘後${it.text}" } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, null, Modifier.size(24.dp), tint = ink)
                Spacer(Modifier.width(8.dp))
                Text("駕車模式", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = ink)
                Spacer(Modifier.width(8.dp))
                Text("往${DrivingMode.headingWord(d.headingDeg)} · ${d.speedKmh} km/h", fontSize = 15.sp, color = ink.copy(alpha = .75f))
            }
            Spacer(Modifier.height(12.dp))
            if (road.isEmpty()) Text("正在查看前方路段的天氣…", fontSize = 17.sp, color = ink.copy(alpha = .8f))
            road.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(99.dp)).background(if (r.wet) Color(0xFF5AA9F0) else Color(0xFF7BD389)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${r.ahead.minutes} 分鐘後 · 前方約 ${r.ahead.km} 公里", fontSize = 14.sp, color = ink.copy(alpha = .7f))
                        Text(r.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("依行進方向直線推估，僅供參考；開車請專心看路，不要操作手機。", fontSize = 14.sp, color = ink.copy(alpha = .6f))
        }
    }
}
