package io.atmos.weather.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.comfort.ComfortStore
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.AppSystemBars
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 災難弱網／極限省電模式: pure black, high-contrast large text, no animation, glass or sky.
 * Shows only the last known weather, official warnings, work/school closures, the nearest
 * shelters (bundled, offline) and the emergency kit progress. One small feed request at most.
 */
@Composable
fun DisasterModeScreen(snapshot: WeatherSnapshot?, countyWarnings: List<String>, onOpen: (String) -> Unit, onExit: () -> Unit) {
    val ctx = LocalContext.current
    AppSystemBars(true)
    val here = remember { ComfortStore.current(ctx) }
    val county = remember { LifeSources.county(here?.name ?: snapshot?.city?.name) }
    var alerts by remember { mutableStateOf<List<NcdrFeed.Alert>>(emptyList()) }
    var shelters by remember { mutableStateOf<List<Shelters.Near>>(emptyList()) }
    LaunchedEffect(Unit) {
        val now = Instant.now()
        alerts = LifeSources.alerts(ctx, 15 * 60_000L).filter { it.activeAt(now) && (NcdrFeed.mentions(it, county) || it.category == "地震") }.take(8)
        shelters = here?.let { h -> withContext(Dispatchers.IO) { Shelters.nearest(LifeSources.shelters(ctx), h.coordinate.latitude, h.coordinate.longitude, limit = 3) } } ?: emptyList()
    }
    val white = Color.White; val dim = Color(0xFFBFBFBF); val warn = Color(0xFFFFD60A)
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("災難省電模式", color = warn, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("已關閉動畫與高耗電畫面。${DateTimeFormatter.ofPattern("M/d HH:mm").format(Instant.now().atZone(zone))}", color = dim, fontSize = 15.sp)
        snapshot?.let { w ->
            Text("${here?.name ?: w.city.name}  ${w.temperatureC.roundToInt()}°", color = white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("最後更新 ${DateTimeFormatter.ofPattern("M/d HH:mm").format(w.fetchedAt.atZone(zone))}" +
                (w.nextHourRainProbabilityPct?.let { " · 下一小時降雨 $it%" } ?: ""), color = dim, fontSize = 16.sp)
        }
        Block("警特報與示警", white) {
            val lines = (countyWarnings + alerts.map { "${it.category}：${it.summary}" }).distinct()
            if (lines.isEmpty()) Text("目前沒有你所在縣市的警特報。", color = dim, fontSize = 18.sp)
            lines.forEach { Text("• $it", color = white, fontSize = 18.sp, lineHeight = 26.sp) }
        }
        Block("最近的避難處所", white) {
            if (shelters.isEmpty()) Text(if (here == null) "尚未定位。" else "載入中…", color = dim, fontSize = 18.sp)
            shelters.forEach { n ->
                Text("${n.shelter.name}", color = white, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("${n.bearing}方 ${Shelters.distance(n.km)} · ${n.shelter.address.ifBlank { n.shelter.area }}", color = dim, fontSize = 16.sp)
            }
        }
        val kit = remember { LifeStore.kit(ctx) }
        Block("防災包", white) { Text("已準備 ${kit.count { it.packed }} / ${kit.size} 項", color = white, fontSize = 18.sp) }
        listOf("closure" to "停班停課速查", "shelter" to "全部避難處所", "kit" to "防災包清單").forEach { (id, label) ->
            Text(label, color = white, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth()
                .border(1.dp, Color(0xFF5A5A5A), RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onOpen(id) }.padding(16.dp))
        }
        Text("離開災難省電模式", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()
            .background(warn, RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onExit).padding(16.dp))
        Text("緊急時請撥 119（消防救護）或 110（報案）。官方資訊以中央氣象署與各縣市政府為準。", color = dim, fontSize = 15.sp)
    }
}

@Composable
private fun Block(title: String, ink: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        content()
    }
}
