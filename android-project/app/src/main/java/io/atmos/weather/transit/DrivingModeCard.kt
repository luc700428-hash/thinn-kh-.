package io.atmos.weather.transit

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.comfort.RainSoon
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.IOSColors
import io.atmos.weather.ui.IOSIconTile
import io.atmos.weather.ui.rememberAppleReduceTransparency
import io.atmos.weather.ui.thinnGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * 駕車模式 card: large, glanceable. Always the live speed (GPS, every second) against the posted
 * limit; speed cameras, 區間測速 and red-light cameras ahead with a countdown and a voice warning;
 * on a freeway the green road sign (next interchange, service area, rest stop); and whether rain
 * lies ahead in about 15 and 45 minutes.
 */
@Composable
fun DrivingModeCard(drive: DrivingMode.Drive?, ink: Color, motion: TransitMotion? = null) {
    val ctx = LocalContext.current
    data class Road(val ahead: DrivingMode.Ahead, val text: String, val wet: Boolean)
    var road by remember { mutableStateOf<List<Road>>(emptyList()) }
    val network by produceState<Freeways.Network?>(null) { value = withContext(Dispatchers.IO) { Freeways.load(ctx) } }
    var cameras by remember { mutableStateOf<SpeedCameras.Index?>(null) }
    var marksVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(network, marksVersion) {
        if (network == null) return@LaunchedEffect
        cameras = withContext(Dispatchers.IO) { SpeedCameras.load(ctx) }
        if (SpeedCameras.refresh(ctx)) cameras = withContext(Dispatchers.IO) { SpeedCameras.load(ctx) }
    }
    val prefs = remember { ctx.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE) }
    var voiceOn by remember { mutableStateOf(prefs.getBoolean("drive_voice", true)) }
    val voice = rememberDriveVoice(voiceOn && drive != null)
    // The freeway the car is on; held through one or two wobbly fixes (tunnels, viaducts).
    var spot by remember { mutableStateOf<Freeways.Spot?>(null) }
    var spotAt by remember { mutableLongStateOf(0L) }
    var alert by remember { mutableStateOf<SpeedCameras.Alert?>(null) }
    var firstSeen by remember { mutableStateOf(0.0) }
    var zone by remember { mutableStateOf<SpeedCameras.ZoneRun?>(null) }
    val announced = remember { HashMap<SpeedCameras.Camera, Long>() }
    val live = motion?.speedKmh ?: drive?.speedKmh ?: 0
    val heading = motion?.heading ?: drive?.recentHeading ?: 0.0
    val lat = motion?.lat ?: drive?.lat; val lon = motion?.lon ?: drive?.lon
    LaunchedEffect(drive, network, cameras, motion?.atMs) {
        val d = drive; val net = network
        if (d == null || net == null || lat == null || lon == null) { spot = null; alert = null; zone = null; return@LaunchedEffect }
        val found = if (live >= 35) withContext(Dispatchers.Default) { Freeways.match(net, lat, lon, heading) } else null
        val now = System.currentTimeMillis()
        if (found != null) { spot = found; spotAt = now } else if (now - spotAt > 20_000L) spot = null
        val idx = cameras ?: return@LaunchedEffect
        val ahead = if (live < 5) alert else withContext(Dispatchers.Default) {
            SpeedCameras.ahead(idx, net, spot, lat, lon, heading, SpeedCameras.lookahead(maxOf(live, 40)))
        }
        if (ahead?.camera != alert?.camera) firstSeen = ahead?.metres ?: 0.0
        alert = ahead
        zone = SpeedCameras.followZone(zone, idx, lat, lon, motion?.atMs ?: now)
        // Speak once per camera (again only after 10 minutes), and "請減速" when still too fast close by.
        ahead?.let { a ->
            val last = announced[a.camera]
            if (last == null || now - last > 10 * 60_000L) { announced[a.camera] = now; voice.say(SpeedCameras.speech(a), chime = true) }
            val lim = a.camera.limit
            if (lim != null && live > lim && a.metres < 250 && announced[a.camera.copy(label = a.camera.label + "#slow")] == null) {
                announced[a.camera.copy(label = a.camera.label + "#slow")] = now; voice.say("請減速", chime = true)
            }
        }
    }
    val status = remember(spot, network) { val s = spot; val n = network; if (s == null || n == null) null else Freeways.status(n, s) }
    // The limit to hold now: the zone you are in, the freeway's posted limit, or the camera coming up.
    val limit = zone?.zone?.limit ?: spot?.let { it.chain.limitAt(it.at) } ?: alert?.camera?.limit
    // Rain re-checked every 5 minutes; along the freeway itself when on one, else straight ahead.
    val key = drive?.let { "${(it.lat * 20).toInt()}:${(it.lon * 20).toInt()}:${(it.headingDeg / 45).toInt()}:${spot?.chain?.id}" }
    LaunchedEffect(key) {
        val d = drive ?: return@LaunchedEffect
        while (true) {
            val s = spot; val n = network
            road = DrivingMode.aheads(d).map { a ->
                val along = if (s != null && n != null) Freeways.pointAlong(n, s, d.speedKmh.coerceAtLeast(30) * a.minutes / 60.0 * 1000) else null
                val target = along?.let { DrivingMode.Ahead(a.minutes, a.km, it.first, it.second) } ?: a
                val slots = RainSoon.fetch(target.lat, target.lon)
                val at = Instant.now().plusSeconds(a.minutes * 60L)
                val slot = slots?.minByOrNull { kotlin.math.abs(it.at.epochSecond - at.epochSecond) }
                val mm = slot?.mm?.times(4)
                Road(target, DrivingMode.describe(mm, slot?.chance), (mm ?: 0.0) >= .8)
            }
            delay(5 * 60_000L)
        }
    }
    AnimatedVisibility(drive != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val d = drive ?: return@AnimatedVisibility
        val st = status
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
            .thinnGlass(rememberAppleReduceTransparency(), 24.dp, MaterialTheme.colorScheme.surface)
            .padding(16.dp).semantics {
                contentDescription = "駕車模式，時速 $live" + (limit?.let { "，速限 $it" } ?: "") +
                    (alert?.let { "，" + SpeedCameras.speech(it) } ?: "") +
                    (st?.let { s -> "，${s.road}${s.direction}" + (s.exit?.let { "，下個出口${it.place.name}，${Freeways.distance(it.metres)}" } ?: "") +
                        (s.service?.let { "，${it.place.name}${Freeways.distance(it.metres)}" } ?: "") } ?: "") +
                    "，" + road.joinToString("，") { "${it.ahead.minutes} 分鐘後${it.text}" }
            }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, null, Modifier.size(24.dp), tint = ink)
                Spacer(Modifier.width(8.dp))
                Text("駕車模式", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = ink)
                Spacer(Modifier.width(8.dp))
                if (st != null) { RoadShield(st.road); Spacer(Modifier.width(6.dp)) }
                Text(st?.direction?.takeIf { it.isNotEmpty() } ?: "往${DrivingMode.headingWord(heading)}",
                    fontSize = 15.sp, color = ink.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = if (voiceOn) "關閉語音提醒" else "開啟語音提醒") {
                    voiceOn = !voiceOn; prefs.edit().putBoolean("drive_voice", voiceOn).apply()
                }, contentAlignment = Alignment.Center) {
                    Icon(if (voiceOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, if (voiceOn) "語音提醒開啟" else "語音提醒關閉", Modifier.size(22.dp), tint = ink.copy(alpha = .8f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Speedometer(live, limit, ink)
            AnimatedVisibility(alert != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                alert?.let { a -> CameraAlert(a, firstSeen, live) }
            }
            AnimatedVisibility(zone != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                zone?.let { z -> ZonePanel(z, motion?.atMs ?: System.currentTimeMillis(), ink) }
            }
            AnimatedVisibility(st != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                val s = st ?: return@AnimatedVisibility
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.exit?.let { ExitSign(it, s.then) }
                    s.service?.let { StopRow(Icons.Rounded.LocalCafe, IOSColors.orange, "下個服務區", it, ink) }
                    s.rest?.takeIf { r -> s.service == null || r.metres < s.service.metres }?.let { StopRow(Icons.Rounded.Weekend, IOSColors.teal, "下個休息站", it, ink) }
                    s.end?.takeIf { it.metres < 8000 }?.let { StopRow(Icons.Rounded.Flag, IOSColors.gray, "道路終點", it, ink) }
                }
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
            if (lat != null && lon != null) MarkRow(ink) { kind ->
                SpeedCameras.mark(ctx, kind, lat, lon, heading); marksVersion++
            }
            Spacer(Modifier.height(6.dp))
            Text(if (st != null) "測速資料：警政署、國道公路警察局，路網 © OpenStreetMap；速度以 GPS 為準，請依路旁標誌行駛，開車請專心看路。"
                else "測速資料：警政署、國道公路警察局；速度以 GPS 為準，請依路旁標誌行駛，開車請專心看路。", fontSize = 14.sp, color = ink.copy(alpha = .6f))
        }
    }
}

/** 國道 shields are the plum-flower green, expressway ones blue, as on the real signs' colours. */
@Composable
private fun RoadShield(road: String) {
    val freeway = road.startsWith("國道")
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (freeway) Color(0xFF0B7A3E) else Color(0xFF1F5FBF))
        .border(1.5.dp, Color.White, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(Freeways.short(road), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The next interchange, styled like the green overhead sign. */
@Composable
private fun ExitSign(exit: Freeways.Ahead, then: Freeways.Ahead?) {
    val green = Brush.verticalGradient(listOf(Color(0xFF138A4A), Color(0xFF0A6634)))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(green)
        .border(2.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下個出口", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
            if (exit.place.exit.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFD60A)).padding(horizontal = 6.dp)) {
                    Text("出口 ${exit.place.exit}", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(exit.place.name, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "exit", modifier = Modifier.weight(1f)) { name ->
                Text(name, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            val (num, unit) = Freeways.distance(exit.metres).split(' ').let { it[0] to it.getOrElse(1) { "" } }
            Text(num, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(" $unit", color = Color.White.copy(alpha = .9f), fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (exit.place.towards.isNotBlank()) Text("往 ${exit.place.towards}", color = Color.White.copy(alpha = .9f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        then?.let {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .25f)))
            Spacer(Modifier.height(6.dp))
            Text("再下一個：${it.place.name} · ${Freeways.distance(it.metres)}", color = Color.White.copy(alpha = .85f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StopRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, a: Freeways.Ahead, ink: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IOSIconTile(icon, tint, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = ink.copy(alpha = .7f))
            Text(a.place.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Freeways.distance(a.metres), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}


/** The speed, big, on an arc that fills toward the limit: green, amber within 5 km/h, red over it. */
@Composable
private fun Speedometer(speed: Int, limit: Int?, ink: Color) {
    val over = limit != null && speed > limit
    val near = limit != null && !over && speed >= limit - 5
    val tone by animateColorAsState(when { over -> Color(0xFFFF3B30); near -> Color(0xFFFF9F0A); limit != null -> Color(0xFF34C759); else -> Color(0xFF0A84FF) }, label = "tone")
    val scale = maxOf(140, ((limit ?: 100) * 1.4).roundToInt())
    val shown by animateFloatAsState(speed.toFloat(), spring(stiffness = Spring.StiffnessMediumLow), label = "speed")
    val pulse = rememberInfiniteTransition(label = "over").animateFloat(1f, if (over) .35f else 1f,
        infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 13.dp.toPx(); val inset = stroke / 2 + 2.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2); val tl = Offset(inset, inset)
                drawArc(ink.copy(alpha = .12f), 150f, 240f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                limit?.let { l ->
                    // A tick where the limit sits on the dial.
                    val a = Math.toRadians(150.0 + 240.0 * l / scale)
                    val r1 = arc.width / 2 - stroke; val r2 = arc.width / 2 + stroke * .6f
                    val c = Offset(size.width / 2, size.height / 2)
                    drawLine(Color(0xFFFF3B30), c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()), c + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()), 3.dp.toPx(), StrokeCap.Round)
                }
                drawArc(Brush.sweepGradient(listOf(tone.copy(alpha = .55f), tone)), 150f, 240f * (shown / scale).coerceIn(0f, 1f), false, tl, arc,
                    style = Stroke(stroke, cap = StrokeCap.Round), alpha = if (over) pulse.value.coerceIn(.35f, 1f) else 1f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$speed", color = if (over) Color(0xFFFF3B30) else ink, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp)
                Text("km/h", color = ink.copy(alpha = .7f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (limit != null) {
                // The round speed-limit sign: white face, thick red ring.
                Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White).border(8.dp, Color(0xFFE5232B), CircleShape), contentAlignment = Alignment.Center) {
                    Text("$limit", color = Color.Black, fontSize = if (limit >= 100) 26.sp else 30.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(6.dp))
                Text(when { over -> "超速 ${speed - limit}"; near -> "接近速限"; else -> "速限內" }, color = tone, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("—", color = ink.copy(alpha = .5f), fontSize = 30.sp)
                Text("此路段速限未知", color = ink.copy(alpha = .65f), fontSize = 14.sp)
            }
        }
    }
}

private fun SpeedCameras.Kind.color() = when (this) {
    SpeedCameras.Kind.SPEED -> Color(0xFFFF3B30); SpeedCameras.Kind.ZONE -> Color(0xFFAF52DE); SpeedCameras.Kind.RED_LIGHT -> Color(0xFFFF9500)
    SpeedCameras.Kind.MOBILE -> Color(0xFF0A84FF); SpeedCameras.Kind.POLICE -> Color(0xFF5E5CE6)
}
private fun SpeedCameras.Kind.icon() = when (this) {
    SpeedCameras.Kind.SPEED -> Icons.Rounded.PhotoCamera; SpeedCameras.Kind.ZONE -> Icons.Rounded.Timer; SpeedCameras.Kind.RED_LIGHT -> Icons.Rounded.Traffic
    SpeedCameras.Kind.MOBILE -> Icons.Rounded.Speed; SpeedCameras.Kind.POLICE -> Icons.Rounded.LocalPolice
}

/** The camera ahead: what it is, the limit, a big countdown and a bar that empties as you get there. */
@Composable
private fun CameraAlert(a: SpeedCameras.Alert, firstSeen: Double, speed: Int) {
    val c = a.camera; val tint = c.kind.color()
    val over = c.limit != null && speed > c.limit
    val left by animateFloatAsState((a.metres / firstSeen.coerceAtLeast(a.metres).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f), tween(900, easing = LinearEasing), label = "left")
    val flash = rememberInfiniteTransition(label = "flash").animateFloat(1f, if (over) .25f else 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "edge")
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(tint.copy(alpha = .92f), tint.copy(alpha = .72f))))
        .border(if (over) 3.dp else 1.dp, Color.White.copy(alpha = if (over) flash.value else .5f), RoundedCornerShape(20.dp))
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                Icon(c.kind.icon(), null, Modifier.size(28.dp), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.kind.label + if (c.alsoRedLight) " · 闖紅燈" else "", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(c.label, color = Color.White.copy(alpha = .88f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (c.limit != null) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White).border(5.dp, Color(0xFFE5232B), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${c.limit}", color = Color.Black, fontSize = if (c.limit >= 100) 17.sp else 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val m = a.metres
            Text(if (m >= 1000) String.format(java.util.Locale.US, "%.1f", m / 1000) else "${((m / 10).roundToInt() * 10)}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(if (m >= 1000) " 公里" else " 公尺", color = Color.White.copy(alpha = .9f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 5.dp))
            Spacer(Modifier.weight(1f))
            if (over) Text("請減速", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .25f))) {
            Box(Modifier.fillMaxWidth(left).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(Color.White))
        }
    }
}

/** 區間測速 in progress: your average so far against the zone's limit. */
@Composable
private fun ZonePanel(z: SpeedCameras.ZoneRun, now: Long, ink: Color) {
    val avg = z.averageKmh(now)
    val over = avg != null && avg > z.zone.limit
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFAF52DE).copy(alpha = .16f))
        .border(1.dp, Color(0xFFAF52DE).copy(alpha = .6f), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IOSIconTile(Icons.Rounded.Timer, Color(0xFFAF52DE), 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("區間測速中", color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${z.zone.name.substringBefore("區間測速").ifBlank { z.zone.name }} · 已行駛 ${Freeways.distance(z.metres)}", color = ink.copy(alpha = .7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(avg?.let { "平均 $it" } ?: "計算中", color = if (over) Color(0xFFFF3B30) else ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("速限 ${z.zone.limit}", color = ink.copy(alpha = .7f), fontSize = 14.sp)
        }
    }
}

/**
 * 流動測速 and patrol spots have no public data, so they are the driver's own: one tap marks this
 * spot for this direction, and the next time it is announced like any camera (kept 120 days).
 */
@Composable
private fun MarkRow(ink: Color, onMark: (SpeedCameras.Kind) -> Unit) {
    var done by remember { mutableStateOf<SpeedCameras.Kind?>(null) }
    LaunchedEffect(done) { if (done != null) { delay(2500); done = null } }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(done?.let { "已標記${it.label}" } ?: "這裡有：", color = ink.copy(alpha = .75f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        listOf(SpeedCameras.Kind.MOBILE, SpeedCameras.Kind.POLICE).forEach { k ->
            Row(Modifier.clip(RoundedCornerShape(99.dp)).background(k.color().copy(alpha = .16f)).border(1.dp, k.color().copy(alpha = .5f), RoundedCornerShape(99.dp))
                .clickable(onClickLabel = "標記${k.label}") { onMark(k); done = k }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(k.icon(), null, Modifier.size(18.dp), tint = k.color()); Spacer(Modifier.width(4.dp))
                Text(if (k == SpeedCameras.Kind.MOBILE) "流動測速" else "警車", color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The voice: a short chime, then the sentence, in Taiwanese Mandarin. Created only while driving with voice on. */
private class DriveVoice(ctx: android.content.Context) {
    private var ready = false
    private val tts = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.also { t -> runCatching { t.language = java.util.Locale.TAIWAN } }
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    fun say(text: String, chime: Boolean) {
        if (chime) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180) }
        if (ready) runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "drive-${text.hashCode()}") }
    }
    fun close() { runCatching { tts.stop(); tts.shutdown() }; runCatching { tone?.release() } }
}
private interface Voice { fun say(text: String, chime: Boolean) }

@Composable
private fun rememberDriveVoice(on: Boolean): Voice {
    val ctx = LocalContext.current
    val holder = remember { arrayOfNulls<DriveVoice>(1) }
    DisposableEffect(on) {
        if (on && !android.os.Build.FINGERPRINT.orEmpty().contains("robolectric")) holder[0] = runCatching { DriveVoice(ctx) }.getOrNull()
        onDispose { holder[0]?.close(); holder[0] = null }
    }
    return remember { object : Voice { override fun say(text: String, chime: Boolean) { holder[0]?.say(text, chime) } } }
}
