package io.atmos.weather.earthquake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import java.util.Locale
import kotlin.math.ceil

/**
 * What an early warning says, in the live tab: magnitude, depth and epicentre; the person's own
 * expected intensity and a countdown to the main shaking; and every county's intensity. When the
 * source sends no county values they are worked out on the phone and clearly marked 推算.
 */
@Composable internal fun SeismicLiveEewDetails(incident: EewIncident, place: SeismicPlace?, now: State<Long>, replay: Boolean) {
    val palette = MaterialTheme.colorScheme
    val counties = remember(incident.id, incident.serial) { SeismicCountyEstimate.view(incident) }
    val t = now.value
    val local = SeismicLocalPrediction.resolve(incident, place, t)
    val intensity = local.intensity ?: SeismicCountyEstimate.forPlace(counties, place?.name)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (replay) "歷史資料回放中" else "地震速報 · 第 ${incident.serial} 報", color = seismicCautionInk, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            incident.magnitude?.let {
                Text("M ${"%.1f".format(Locale.US, it)}", color = SeismicReportStyle.magnitude(it, palette.background.red < .5f),
                    fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(SeismicDisplayFormat.location(incident.epicenter.ifBlank { "震央位置待來源確認" }), fontSize = 21.sp, color = palette.onSurface, fontWeight = FontWeight.SemiBold)
        Text("深度 ${SeismicDisplayFormat.depth(incident.depthKm)}" + (incident.maximumIntensity?.takeIf { it.isNotBlank() }?.let { " · 預估最大震度 ${SeismicDisplayFormat.symbol(it)} 級" } ?: ""),
            color = palette.onSurfaceVariant, fontSize = 14.sp)
        // Here: the expected intensity and the seconds left before the main shaking.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x22FF3B30)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val g = intensity?.replace("級以上", "")?.removeSuffix("級")
            val fill = SeismicIntensityColors.color(g)
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(fill), contentAlignment = Alignment.Center) {
                Text(SeismicDisplayFormat.symbol(g), color = seismicInkOn(fill), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place?.name?.let { "$it 預估震度" } ?: "你所在地預估震度", color = palette.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(if (intensity == null) local.intensityReason else if (local.intensityOrigin == PredictionOrigin.SOURCE) "來源提供" else "本機推算 · 僅供參考",
                    color = palette.onSurfaceVariant, fontSize = 14.sp)
            }
            // A stale location fix still gives a useful arrival estimate from its last position.
            val at = local.arrivalAtMs ?: run {
                val lat = incident.latitude; val lon = incident.longitude; val origin = incident.originAtMs
                if (place == null || lat == null || lon == null || origin == null) null
                else SeismicLocalPrediction.travelSeconds(incident.depthKm ?: 10.0, SeismicWavePhysics.epicentralKm(lat, lon, place.lat, place.lon))
                    ?.let { origin + (it * 1000).toLong() }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("主震波", color = palette.onSurfaceVariant, fontSize = 14.sp)
                Text(when { at == null -> "—"; at <= t -> "已抵達"; else -> "${ceil((at - t) / 1000.0).toInt()} 秒" },
                    color = if (at != null && at > t) Color(0xFFFF3B30) else palette.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("各地預估震度", color = palette.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (counties.estimated && counties.values.isNotEmpty())
                Text("來源未提供 · 本機推算", color = palette.onSurfaceVariant, fontSize = 14.sp)
        }
        if (counties.values.isEmpty()) Text("等待來源提供規模與震央…", color = palette.onSurfaceVariant, fontSize = 14.sp)
        else SeismicCountyChips(counties.values, chipHeight = 28.dp)
        Text(if (replay) "過去事件的教育回放，並非目前警報。" else "立即趴下、掩護、穩住。", fontSize = 14.sp, color = palette.onSurfaceVariant)
    }
}
