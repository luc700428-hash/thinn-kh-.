package io.atmos.weather.transit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRailway
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.atmos.weather.presentation.home.WeatherShareCard
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.rememberAppleReduceTransparency
import io.atmos.weather.ui.thinnGlass
import io.atmos.weather.widgets.WidgetWeatherArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** The station weather shown in the card. */
data class StationWeather(val temperature: Int, val code: Int)

/**
 * Follows the phone's position while the weather page is open (never in the background) and
 * returns the trip once [RailMode.judge] is sure enough. Speed falls back to the distance between
 * fixes when the GPS does not report one.
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberRailTrip(active: Boolean): RailMode.Trip? {
    val context = LocalContext.current.applicationContext
    var trip by remember { mutableStateOf<RailMode.Trip?>(null) }
    val samples = remember { ArrayList<RailMode.Sample>() }
    DisposableEffect(active, context) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (!active || !granted || manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            trip = null
            onDispose { }
        } else {
            val listener = LocationListener { l: Location ->
                val prev = samples.lastOrNull()
                val speed = if (l.hasSpeed()) l.speed else prev?.let {
                    val dt = (l.time - it.timeMs) / 1000f
                    if (dt > 0) (RailMode.km(it.lat, it.lon, l.latitude, l.longitude) * 1000 / dt).toFloat() else 0f
                } ?: 0f
                samples += RailMode.Sample(l.latitude, l.longitude, speed, l.time)
                samples.removeAll { l.time - it.timeMs > 150_000L }
                trip = RailMode.judge(samples)
            }
            runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 20f, listener, Looper.getMainLooper()) }
            onDispose { runCatching { manager.removeUpdates(listener) }; samples.clear() }
        }
    }
    return trip
}

/** Current temperature and weather code at stations (Open-Meteo, one request). */
suspend fun stationWeather(stations: List<RailMode.Station>): List<StationWeather?> = withContext(Dispatchers.IO) {
    runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${stations.joinToString(",") { "%.3f".format(java.util.Locale.US, it.lat) }}" +
            "&longitude=${stations.joinToString(",") { "%.3f".format(java.util.Locale.US, it.lon) }}&current=temperature_2m,weather_code&timezone=GMT"
        val c = (URL(url).openConnection() as HttpsURLConnection).apply { connectTimeout = 8000; readTimeout = 10000 }
        try {
            check(c.responseCode == 200)
            val text = c.inputStream.bufferedReader().use { it.readText() }.trim()
            val items = if (text.startsWith("[")) JSONArray(text).let { a -> List(a.length()) { a.getJSONObject(it) } } else listOf(JSONObject(text))
            items.map { o -> o.optJSONObject("current")?.let { cur ->
                StationWeather(cur.optDouble("temperature_2m", Double.NaN).takeIf { it.isFinite() }?.let { Math.round(it).toInt() } ?: return@let null,
                    cur.optInt("weather_code", -1))
            } }
        } finally { c.disconnect() }
    }.getOrDefault(stations.map { null })
}

/** 高鐵模式 / 火車模式 card: the next stop, when, and the weather there and at the end of the line. */
@Composable
fun RailModeCard(trip: RailMode.Trip?, ink: Color) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE) }
    var dismissedUntil by remember { mutableLongStateOf(prefs.getLong("rail_dismissed_until", 0L)) }
    var confirmed by remember { mutableStateOf(false) }
    val shown = trip != null && System.currentTimeMillis() > dismissedUntil
    var weather by remember { mutableStateOf<List<StationWeather?>>(emptyList()) }
    LaunchedEffect(trip?.next?.name, trip?.terminal?.name) {
        val t = trip ?: return@LaunchedEffect
        while (true) { weather = stationWeather(listOf(t.next, t.terminal)); delay(10 * 60_000L) }
    }
    AnimatedVisibility(shown, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val t = trip ?: return@AnimatedVisibility
        val title = if (t.line == RailMode.Line.HSR) "高鐵模式" else "火車模式"
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
            .thinnGlass(rememberAppleReduceTransparency(), 24.dp, MaterialTheme.colorScheme.surface)
            .padding(16.dp).semantics { contentDescription = "$title，${t.towardLabel}，下一站${t.next.name}約${t.nextMinutes}分鐘" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.line == RailMode.Line.HSR) Icons.Rounded.Train else Icons.Rounded.DirectionsRailway, null, Modifier.size(22.dp), tint = ink)
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
                Spacer(Modifier.width(8.dp))
                Text("${t.towardLabel} · 時速 ${t.speedKmh} km/h", fontSize = 14.sp, color = ink.copy(alpha = .75f), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StationTile("下一站", t.next.name, "約 ${t.nextMinutes} 分", weather.getOrNull(0), ink, Modifier.weight(1f))
                StationTile("終點", t.terminal.name, "約 ${t.terminalMinutes} 分", weather.getOrNull(1), ink, Modifier.weight(1f))
            }
            if (!t.confident && !confirmed) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("是，我在搭火車", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { confirmed = true }.padding(vertical = 6.dp))
                    Text("不是", color = ink.copy(alpha = .7f), fontSize = 15.sp,
                        modifier = Modifier.clickable {
                            dismissedUntil = System.currentTimeMillis() + 30 * 60_000L
                            prefs.edit().putLong("rail_dismissed_until", dismissedUntil).apply()
                        }.padding(vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("依手機定位與速度判斷；到站時間為估算，請以車站與車上廣播為準。", fontSize = 14.sp, color = ink.copy(alpha = .6f))
        }
    }
}

@Composable
private fun StationTile(label: String, station: String, eta: String, w: StationWeather?, ink: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (w != null) Image(painterResource(WeatherShareCard.buddy(WidgetWeatherArt.condition(w.code), true)), null, Modifier.size(38.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 14.sp, color = ink.copy(alpha = .65f))
            Text(station, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
            Text(listOfNotNull(eta, w?.let { "${it.temperature}°" }).joinToString(" · "), fontSize = 14.sp, color = ink.copy(alpha = .8f))
        }
    }
}
