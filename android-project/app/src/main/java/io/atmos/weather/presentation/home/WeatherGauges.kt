package io.atmos.weather.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.iosAppear
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToInt

fun windDirectionLabel(degrees: Int?): String {
    if (degrees == null) return "風向未提供"
    val directions = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")
    return "${directions[(((degrees % 360) + 360 + 22) % 360) / 45]}風"
}

fun usAqiLabel(value: Int?): String = when {
    value == null -> "暫無資料"
    value <= 50 -> "良好"
    value <= 100 -> "普通"
    value <= 150 -> "敏感族群注意"
    value <= 200 -> "不健康"
    value <= 300 -> "非常不健康"
    else -> "危害"
}

/**
 * The weather details as equal tiles: every tile has a label, one value, one caption and a
 * scale. Anything the source does not provide is left out instead of showing "—".
 */
internal object HomeDetailTiles {
    enum class Kind { UV, FEELS, HUMIDITY, WIND, AIR, VISIBILITY, PRESSURE, SUN }
    data class Tile(val kind: Kind, val label: String, val value: String, val caption: String, val fraction: Float?)

    fun build(w: WeatherSnapshot, now: Instant): List<Tile> {
        val zone = runCatching { ZoneId.of(w.timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei"))
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        return buildList {
            w.currentUv?.takeIf { it.isFinite() && it >= 0 }?.let { uv ->
                add(Tile(Kind.UV, "紫外線", uv.roundToInt().toString(), uvWord(uv), (uv / 11.0).toFloat()))
            }
            val feels = w.feelsLikeC?.takeIf { it.isFinite() }
            if (feels != null) {
                val delta = feels - w.temperatureC
                val caption = when {
                    delta >= 2 -> "比實際悶熱 ${delta.roundToInt()}°"
                    delta <= -2 -> "比實際冷 ${(-delta).roundToInt()}°"
                    else -> "與實際氣溫相近"
                }
                add(Tile(Kind.FEELS, "體感", "${feels.roundToInt()}°", caption, ((feels + 5) / 45).toFloat()))
            }
            w.humidityPct?.takeIf { it in 0..100 }?.let { rh ->
                val dew = dewPoint(w.temperatureC, rh)
                add(Tile(Kind.HUMIDITY, "濕度", "$rh%", dew?.let { "露點 ${it.roundToInt()}°" } ?: humidityWord(rh), rh / 100f))
            }
            w.windKmh?.takeIf { it.isFinite() && it >= 0 }?.let { kmh ->
                val gust = w.windGustKmh?.takeIf { it.isFinite() && it > kmh + 5 }
                val dir = w.windDirectionDeg?.let { windDirectionLabel(it) }
                val caption = listOfNotNull(dir, gust?.let { "陣風 ${it.roundToInt()}" }).joinToString(" · ").ifBlank { windWord(kmh) }
                add(Tile(Kind.WIND, "風速", "${kmh.roundToInt()} km/h", caption, (kmh / 62.0).toFloat()))
            }
            (w.usAqi ?: w.pm25?.roundToInt())?.let { aqi ->
                val pm = w.pm25?.takeIf { it.isFinite() }?.let { " · PM2.5 ${it.roundToInt()}" }.orEmpty()
                add(Tile(Kind.AIR, "空氣品質", aqi.toString(), airWord(aqi) + pm, (aqi / 300f)))
            }
            w.visibilityM?.takeIf { it.isFinite() && it >= 0 }?.let { m ->
                val km = m / 1000.0
                val value = if (km >= 10) "${km.roundToInt()} km" else String.format(Locale.US, "%.1f km", km)
                add(Tile(Kind.VISIBILITY, "能見度", value, visibilityWord(km, w.condition), (km / 20.0).toFloat()))
            }
            w.pressureHpa?.takeIf { it.isFinite() && it in 850.0..1100.0 }?.let { p ->
                add(Tile(Kind.PRESSURE, "氣壓", "${p.roundToInt()} hPa", if (p < 1005) "偏低 · 天氣較不穩" else if (p > 1022) "偏高 · 天氣較穩定" else "正常範圍", ((p - 980) / 60).toFloat()))
            }
            val rise = w.sunrise; val set = w.sunset
            if (rise != null && set != null && set.isAfter(rise)) {
                val day = (now.epochSecond - rise.epochSecond).toFloat() / (set.epochSecond - rise.epochSecond).coerceAtLeast(1)
                val tile = when {
                    now.isBefore(rise) -> Tile(Kind.SUN, "日出", hm.format(rise.atZone(zone)), "日落 ${hm.format(set.atZone(zone))}", 0f)
                    now.isBefore(set) -> Tile(Kind.SUN, "日落", hm.format(set.atZone(zone)), "日出 ${hm.format(rise.atZone(zone))}", day)
                    else -> Tile(Kind.SUN, "日出", hm.format(rise.plusSeconds(86_400).atZone(zone)), "明天 · 今日日落 ${hm.format(set.atZone(zone))}", 1f)
                }
                add(tile)
            }
        }
    }

    fun uvWord(uv: Double) = when { uv < 3 -> "低量級"; uv < 6 -> "中量級"; uv < 8 -> "高量級 · 記得防曬"; uv < 11 -> "過量級 · 避免曝曬"; else -> "危險級 · 避免外出" }
    fun humidityWord(rh: Int) = when { rh >= 85 -> "潮濕"; rh >= 60 -> "略潮"; rh >= 40 -> "舒適"; else -> "乾燥" }
    fun windWord(kmh: Double) = when { kmh < 6 -> "幾乎無風"; kmh < 20 -> "微風"; kmh < 39 -> "和風"; kmh < 62 -> "強風"; else -> "暴風" }
    fun airWord(aqi: Int) = when { aqi <= 50 -> "良好"; aqi <= 100 -> "普通"; aqi <= 150 -> "敏感族群注意"; aqi <= 200 -> "不健康"; else -> "非常不健康" }
    fun visibilityWord(km: Double, c: WeatherCondition) = when {
        c == WeatherCondition.FOG || km < 1 -> "低 · 行車減速"
        km < 3 -> "偏低 · 開車燈"
        km < 8 -> "尚可"
        else -> "良好"
    }
    /** Magnus approximation, good to about ±0.4 °C in everyday ranges. */
    fun dewPoint(tempC: Double, rh: Int): Double? {
        if (!tempC.isFinite() || rh <= 0) return null
        val g = ln(rh / 100.0) + 17.62 * tempC / (243.12 + tempC)
        return (243.12 * g / (17.62 - g)).takeIf { it.isFinite() }
    }
}

private fun HomeDetailTiles.Kind.icon(): ImageVector = when (this) {
    HomeDetailTiles.Kind.UV -> Icons.Rounded.WbSunny
    HomeDetailTiles.Kind.FEELS -> Icons.Rounded.Thermostat
    HomeDetailTiles.Kind.HUMIDITY -> Icons.Rounded.WaterDrop
    HomeDetailTiles.Kind.WIND -> Icons.Rounded.Air
    HomeDetailTiles.Kind.AIR -> Icons.Rounded.Masks
    HomeDetailTiles.Kind.VISIBILITY -> Icons.Rounded.Visibility
    HomeDetailTiles.Kind.PRESSURE -> Icons.Rounded.Speed
    HomeDetailTiles.Kind.SUN -> Icons.Rounded.WbTwilight
}

private fun HomeDetailTiles.Kind.scale(): List<Color> = when (this) {
    HomeDetailTiles.Kind.UV -> HomeScales.uv
    HomeDetailTiles.Kind.FEELS -> HomeScales.warmth
    HomeDetailTiles.Kind.HUMIDITY -> HomeScales.water
    HomeDetailTiles.Kind.WIND -> HomeScales.wind
    HomeDetailTiles.Kind.AIR -> HomeScales.aqi
    HomeDetailTiles.Kind.VISIBILITY -> HomeScales.sky
    HomeDetailTiles.Kind.PRESSURE -> HomeScales.pressure
    HomeDetailTiles.Kind.SUN -> HomeScales.sun
}

private fun HomeDetailTiles.Kind.tint(): Color = when (this) {
    HomeDetailTiles.Kind.UV, HomeDetailTiles.Kind.SUN -> Color(0xFFF2A54A)
    HomeDetailTiles.Kind.FEELS -> Color(0xFFE58A5B)
    HomeDetailTiles.Kind.HUMIDITY -> Color(0xFF4F95E0)
    HomeDetailTiles.Kind.WIND -> Color(0xFF3FAF8A)
    HomeDetailTiles.Kind.AIR -> Color(0xFF6FBF7A)
    HomeDetailTiles.Kind.VISIBILITY -> Color(0xFF6C9BC9)
    HomeDetailTiles.Kind.PRESSURE -> Color(0xFF7C8FD0)
}

/**
 * 天氣細節: equal tiles, two columns on phones and three on wide screens, each settling in on
 * the iOS curve. Air quality and visibility are tiles too, so the section reads as one grid.
 */
@Composable
fun WeatherIndicatorGrid(weather: WeatherSnapshot) {
    val tiles = remember(weather) { HomeDetailTiles.build(weather, Instant.now()) }
    if (tiles.isEmpty()) return
    io.atmos.weather.ui.ThinnAtmospherePanel(Modifier.testTag("home-details"), accent = Color(0xFFA9C8F0)) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
        val columns = if (maxWidth >= 560.dp) 3 else 2
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("天氣細節", color = HomeInk.primary, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            tiles.chunked(columns).forEachIndexed { row, chunk ->
                HomeTileRow {
                    chunk.forEachIndexed { col, t ->
                        HomeTile(t.label, t.value, t.caption, Modifier.weight(1f).iosAppear(row * columns + col),
                            icon = t.kind.icon(), tint = t.kind.tint()) {
                            HomeScaleBar(t.fraction, t.kind.scale())
                        }
                    }
                    repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (tiles.any { it.kind == HomeDetailTiles.Kind.AIR }) {
                Text("空氣品質為模型推估，非環境部官方 AQI。", color = HomeInk.secondary, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }
        }
    }
    }
}
