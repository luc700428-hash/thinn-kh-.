package io.atmos.weather.report

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.BrandPalette
import io.atmos.weather.ui.GlassButton
import io.atmos.weather.ui.iosJellyPress
import io.atmos.weather.ui.thinnGlass
import io.atmos.weather.ui.rememberAppleReduceTransparency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The small pill beside the sky's description: 「回報」, or 「已回報」 while a report is in force. */
@Composable
fun WeatherReportPill(reported: Boolean, ink: Color, onClick: () -> Unit) {
    val text = if (reported) "已回報" else "回報"
    Row(Modifier.clip(RoundedCornerShape(999.dp)).iosJellyPress()
        .thinnGlass(rememberAppleReduceTransparency(), 999.dp, MaterialTheme.colorScheme.surface, clear = .28f)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp)
        .semantics { contentDescription = if (reported) "已回報目前天氣，點一下修改" else "天氣不對嗎？回報目前天氣" },
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (reported) Icons.Rounded.CheckCircle else Icons.Rounded.Campaign, null, Modifier.size(14.dp), tint = ink)
        Spacer(Modifier.width(4.dp))
        Text(text, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** The report sheet: nine skies with their buddy pictures; one tap sends it. */
@Composable
fun WeatherReportSheet(weather: WeatherSnapshot?, report: WeatherReports.Report?, onReport: (WeatherReports.Kind) -> Unit,
                       onWithdraw: () -> Unit, onDismiss: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    var sent by remember { mutableStateOf<WeatherReports.Kind?>(null) }
    io.atmos.weather.ui.WorkspacePageSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            AnimatedContent(sent, transitionSpec = {
                (fadeIn(spring(stiffness = 380f)) + scaleIn(spring(dampingRatio = .8f, stiffness = 380f), .94f)) togetherWith fadeOut()
            }, label = "回報完成") { done ->
                if (done == null) Column {
                    Text("你那邊現在是什麼天氣？", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text("回報後，首頁、天氣動畫與小工具會立即改成你看到的天氣，維持一小時，並重新取得最新觀測。",
                        fontSize = 15.sp, color = palette.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    WeatherReports.Kind.entries.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { kind ->
                                val selected = report?.kind == kind
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).iosJellyPress()
                                    .background(if (selected) BrandPalette.selection.copy(alpha = .18f) else palette.surfaceVariant.copy(alpha = .7f))
                                    .border(if (selected) 1.5.dp else 0.dp, if (selected) BrandPalette.selection else Color.Transparent, RoundedCornerShape(20.dp))
                                    .clickable(role = Role.Button) { sent = kind; onReport(kind) }
                                    .padding(vertical = 12.dp).semantics { contentDescription = "回報${kind.label}" },
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.foundation.Image(
                                        androidx.compose.ui.res.painterResource(io.atmos.weather.presentation.home.WeatherShareCard.buddy(kind.condition, weather?.isDay != false)),
                                        null, Modifier.size(46.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text(kind.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.onSurface)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (report != null) io.atmos.weather.ui.GlassOutlinedButton(onClick = { onWithdraw(); onDismiss() }, Modifier.fillMaxWidth()) {
                        Text("取消我的回報，改回官方資料")
                    }
                    Spacer(Modifier.height(12.dp))
                } else Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(io.atmos.weather.presentation.home.WeatherShareCard.buddy(done.condition, weather?.isDay != false)),
                        null, Modifier.size(84.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("已回報「${done.label}」", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Spacer(Modifier.height(6.dp))
                    val differs = weather != null && WeatherReports.disagrees(weather, done)
                    Text(if (differs) "和預報不同，已改成你看到的天氣，並重新取得最新觀測與雷達。謝謝你！"
                        else "已套用到首頁與小工具，一小時後自動回到官方資料。謝謝你！",
                        fontSize = 15.sp, color = palette.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    GlassButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("完成") }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
