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
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
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
    // The limit to hold now: the zone you are in, a camera close ahead, the freeway's posted limit.
    // A camera's limit is the police's own figure for that spot, so it wins within a kilometre of it.
    val limit = zone?.zone?.limit ?: alert?.takeIf { it.metres < 1000 }?.camera?.limit ?: spot?.let { it.chain.limitAt(it.at) } ?: alert?.camera?.limit
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
        if (drive == null) return@AnimatedVisibility
        val st = status
        // A night dashboard in any theme: deep graphite glass, white type, colour only where it means something.
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1E25), Color(0xFF0E1015))))
            .drawBehind {
                // Top light: a hairline highlight and a soft wash of the speed colour.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent), endY = 90.dp.toPx()))
            }
            .border(.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .05f))), RoundedCornerShape(30.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp).semantics {
                contentDescription = "駕車模式，時速 $live" + (limit?.let { "，速限 $it" } ?: "") +
                    (alert?.let { "，" + SpeedCameras.speech(it) } ?: "") +
                    (st?.let { s -> "，${s.road}${s.direction}" + (s.exit?.let { "，下個出口${it.place.name}，${Freeways.distance(it.metres)}" } ?: "") +
                        (s.service?.let { "，${it.place.name}${Freeways.distance(it.metres)}" } ?: "") } ?: "") +
                    "，" + road.joinToString("，") { "${it.ahead.minutes} 分鐘後${it.text}" }
            }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3D9BFF), Color(0xFF0A60D6)))),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DirectionsCar, null, Modifier.size(19.dp), tint = Color.White) }
                Spacer(Modifier.width(9.dp))
                Text("駕車模式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.width(8.dp))
                if (st != null) { RoadShield(st.road); Spacer(Modifier.width(6.dp)) }
                HeadingPill(heading, st?.direction?.takeIf { it.isNotEmpty() } ?: "往${DrivingMode.headingWord(heading)}")
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = if (voiceOn) .14f else .06f))
                    .clickable(onClickLabel = if (voiceOn) "關閉語音提醒" else "開啟語音提醒") { voiceOn = !voiceOn; prefs.edit().putBoolean("drive_voice", voiceOn).apply() },
                    contentAlignment = Alignment.Center) {
                    Icon(if (voiceOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, if (voiceOn) "語音提醒開啟" else "語音提醒關閉", Modifier.size(21.dp),
                        tint = if (voiceOn) Color.White else Color.White.copy(alpha = .5f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Speedometer(live, limit)
            AnimatedVisibility(alert != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                alert?.let { a -> CameraAlert(a, firstSeen, live) }
            }
            AnimatedVisibility(zone != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                zone?.let { z -> ZonePanel(z, motion?.atMs ?: System.currentTimeMillis()) }
            }
            AnimatedVisibility(st != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                val s = st ?: return@AnimatedVisibility
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.exit?.let { ExitSign(it, s.then) }
                    val stops = listOfNotNull(
                        s.service?.let { Triple(Icons.Rounded.LocalCafe, IOSColors.orange, "下個服務區" to it) },
                        s.rest?.takeIf { r -> s.service == null || r.metres < s.service.metres }?.let { Triple(Icons.Rounded.Weekend, IOSColors.teal, "下個休息站" to it) },
                        s.end?.takeIf { it.metres < 8000 }?.let { Triple(Icons.Rounded.Flag, IOSColors.gray, "道路終點" to it) })
                    if (stops.isNotEmpty()) DashGroup {
                        stops.forEachIndexed { i, (icon, tint, p) -> if (i > 0) Hairline(); StopRow(icon, tint, p.first, p.second) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (road.isEmpty()) DashGroup(Modifier.weight(1f)) { Text("正在查看前方路段的天氣…", fontSize = 15.sp, color = Color.White.copy(alpha = .75f)) }
                road.forEach { r -> RainTile(r.ahead, r.text, r.wet, Modifier.weight(1f).fillMaxHeight()) }
            }
            if (lat != null && lon != null) MarkRow { kind -> SpeedCameras.mark(ctx, kind, lat, lon, heading); marksVersion++ }
            Spacer(Modifier.height(8.dp))
            Text("測速資料：警政署、國道公路警察局與各縣市警察局" + (if (st != null) "，路網 © OpenStreetMap" else "") +
                "。速度以 GPS 為準，請依路旁標誌行駛，開車請專心看路。", fontSize = 14.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = .5f))
        }
    }
}

private val tabular = TextStyle(fontFeatureSettings = "tnum")

/** A quiet inset group, like an iOS grouped list on a dark dashboard. */
@Composable
private fun DashGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .06f))
        .border(.5.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), content = content)

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().padding(start = 42.dp, top = 8.dp, bottom = 8.dp).height(.5.dp).background(Color.White.copy(alpha = .12f)))

/** Which way you are heading: a small arrow turned to the compass bearing, then 南下 / 往東. */
@Composable
private fun HeadingPill(deg: Double, text: String) {
    val turn by animateFloatAsState(deg.toFloat(), tween(600), label = "heading")
    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .10f)).padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Navigation, null, Modifier.size(15.dp).rotate(turn), tint = Color(0xFF64B5FF))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 14.sp, color = Color.White.copy(alpha = .9f), maxLines = 1)
    }
}

/**
 * The speed on a dial with fine ticks, the arc filling toward the limit: green, amber within
 * 5 km/h, red (pulsing) over it; the round limit sign and a status capsule beside it.
 */
@Composable
private fun Speedometer(speed: Int, limit: Int?) {
    val over = limit != null && speed > limit
    val near = limit != null && !over && speed >= limit - 5
    val tone by animateColorAsState(when { over -> Color(0xFFFF453A); near -> Color(0xFFFFB340); limit != null -> Color(0xFF32D74B); else -> Color(0xFF0A84FF) }, label = "tone")
    val scale = maxOf(140, ((limit ?: 100) * 1.4 / 20).roundToInt() * 20)
    val shown by animateFloatAsState(speed.toFloat(), spring(stiffness = Spring.StiffnessMediumLow), label = "speed")
    // Only an over-limit arc pulses (and not with Reduce Motion): nothing animates forever otherwise.
    val reduce = io.atmos.weather.ui.rememberAppleReduceMotion()
    val pulse = if (over && !reduce) rememberInfiniteTransition(label = "over").animateFloat(1f, .45f,
        infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse") else remember { mutableFloatStateOf(1f) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(172.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 11.dp.toPx(); val inset = stroke / 2 + 12.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2); val tl = Offset(inset, inset)
                val c = Offset(size.width / 2, size.height / 2); val r = arc.width / 2
                // Ticks every 10 km/h, longer every 20, just outside the track.
                for (v in 0..scale step 10) {
                    val a = Math.toRadians(135.0 + 270.0 * v / scale)
                    val major = v % 20 == 0
                    val r1 = r + stroke * .9f; val r2 = r1 + (if (major) 7.dp else 4.dp).toPx()
                    drawLine(Color.White.copy(alpha = if (v <= shown) .75f else if (major) .35f else .18f),
                        c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()), c + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()),
                        (if (major) 1.6f else 1f).dp.toPx(), StrokeCap.Round)
                }
                drawArc(Color.White.copy(alpha = .08f), 135f, 270f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = 270f * (shown / scale).coerceIn(0f, 1f)
                // A soft glow under the value arc, then the arc itself.
                drawArc(tone.copy(alpha = .22f * pulse.value), 135f, sweep, false, tl, arc, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(tone.copy(alpha = .55f), tone, tone), c), 135f, sweep, false, tl, arc,
                    style = Stroke(stroke, cap = StrokeCap.Round), alpha = pulse.value.coerceIn(.45f, 1f))
                limit?.let { l ->
                    val a = Math.toRadians(135.0 + 270.0 * l.coerceAtMost(scale) / scale)
                    val r1 = r - stroke * .8f; val r2 = r + stroke * .8f
                    drawLine(Color(0xFFFF453A), c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()), c + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()), 3.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$speed", color = if (over) Color(0xFFFF6961) else Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 58.sp, style = tabular)
                Text("km/h", color = Color.White.copy(alpha = .55f), fontSize = 14.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (limit != null) {
                LimitSign(limit, 78.dp)
                Spacer(Modifier.height(10.dp))
                Text(when { over -> "超速 ${speed - limit}"; near -> "接近速限"; else -> "速限內" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(tone.copy(alpha = if (over) .85f else .28f)).padding(horizontal = 12.dp, vertical = 4.dp))
            } else {
                Box(Modifier.size(78.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("—", color = Color.White.copy(alpha = .4f), fontSize = 26.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("速限未知", color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
            }
        }
    }
}

/** The round speed-limit sign: white face, thick red ring, black numerals. */
@Composable
private fun LimitSign(limit: Int, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White).border(size * .1f, Color(0xFFE5232B), CircleShape),
        contentAlignment = Alignment.Center) {
        Text("$limit", color = Color.Black, fontSize = (size.value * (if (limit >= 100) .32f else .38f)).sp, fontWeight = FontWeight.Black, style = tabular)
    }
}

private fun SpeedCameras.Kind.color() = when (this) {
    SpeedCameras.Kind.SPEED -> Color(0xFFFF453A); SpeedCameras.Kind.ZONE -> Color(0xFFBF5AF2); SpeedCameras.Kind.TECH -> Color(0xFFFF9F0A)
    SpeedCameras.Kind.RED_LIGHT -> Color(0xFFFF9F0A); SpeedCameras.Kind.RAIL -> Color(0xFFFFD60A)
    SpeedCameras.Kind.MOBILE -> Color(0xFF0A84FF); SpeedCameras.Kind.POLICE -> Color(0xFF5E5CE6)
}
private fun SpeedCameras.Kind.icon() = when (this) {
    SpeedCameras.Kind.SPEED -> Icons.Rounded.PhotoCamera; SpeedCameras.Kind.ZONE -> Icons.Rounded.Timer; SpeedCameras.Kind.TECH -> Icons.Rounded.Videocam
    SpeedCameras.Kind.RED_LIGHT -> Icons.Rounded.Traffic; SpeedCameras.Kind.RAIL -> Icons.Rounded.Train
    SpeedCameras.Kind.MOBILE -> Icons.Rounded.Speed; SpeedCameras.Kind.POLICE -> Icons.Rounded.LocalPolice
}

/** The camera ahead: a glowing panel in its colour, what it catches, the limit and a countdown that drains. */
@Composable
private fun CameraAlert(a: SpeedCameras.Alert, firstSeen: Double, speed: Int) {
    val c = a.camera; val tint = c.kind.color()
    val over = c.limit != null && speed > c.limit
    val left by animateFloatAsState((a.metres / firstSeen.coerceAtLeast(a.metres).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f), tween(900, easing = LinearEasing), label = "left")
    val reduce = io.atmos.weather.ui.rememberAppleReduceMotion()
    val flash = if (over && !reduce) rememberInfiniteTransition(label = "flash").animateFloat(1f, .3f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "edge")
        else remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(tint.copy(alpha = .38f), tint.copy(alpha = .14f), Color.White.copy(alpha = .04f))))
        .border(if (over) 2.dp else .75.dp, tint.copy(alpha = if (over) flash.value else .55f), RoundedCornerShape(22.dp))
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Brush.verticalGradient(listOf(tint.lighten(.15f), tint))), contentAlignment = Alignment.Center) {
                Icon(c.kind.icon(), null, Modifier.size(27.dp), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.kind.label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(listOf(c.label, c.catches.takeIf { c.kind == SpeedCameras.Kind.TECH && it.isNotEmpty() }).filterNotNull().joinToString(" · ").ifBlank { c.kind.label },
                    color = Color.White.copy(alpha = .78f), fontSize = 14.sp, maxLines = 2, lineHeight = 18.sp, overflow = TextOverflow.Ellipsis)
            }
            if (c.limit != null) { Spacer(Modifier.width(8.dp)); LimitSign(c.limit, 52.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val m = a.metres
            Text(if (m >= 1000) String.format(java.util.Locale.US, "%.1f", m / 1000) else "${((m / 10).roundToInt() * 10)}", color = Color.White, fontSize = 38.sp,
                fontWeight = FontWeight.Bold, lineHeight = 40.sp, style = tabular)
            Text(if (m >= 1000) " 公里" else " 公尺", color = Color.White.copy(alpha = .75f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 5.dp))
            Spacer(Modifier.weight(1f))
            if (over) Text("請減速", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFFFF453A)).padding(horizontal = 12.dp, vertical = 3.dp))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .14f))) {
            Box(Modifier.fillMaxWidth(left).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(Brush.horizontalGradient(listOf(tint, Color.White))))
        }
    }
}

private fun Color.lighten(t: Float) = Color(red + (1 - red) * t, green + (1 - green) * t, blue + (1 - blue) * t, alpha)

/** 區間測速 in progress: your average so far against the zone's limit, with how far you have come. */
@Composable
private fun ZonePanel(z: SpeedCameras.ZoneRun, now: Long) {
    val avg = z.averageKmh(now)
    val over = avg != null && avg > z.zone.limit
    val purple = Color(0xFFBF5AF2)
    val span = Freeways.metres(z.zone.fromLat, z.zone.fromLon, z.zone.toLat, z.zone.toLon).coerceAtLeast(1.0)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(purple.copy(alpha = .32f), purple.copy(alpha = .10f))))
        .border(.75.dp, purple.copy(alpha = .55f), RoundedCornerShape(22.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IOSIconTile(Icons.Rounded.Timer, purple, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("區間測速中", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(z.zone.name.substringBefore("區間測速").ifBlank { z.zone.name }, color = Color.White.copy(alpha = .75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(avg?.let { "$it" } ?: "—", color = if (over) Color(0xFFFF6961) else Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = tabular)
                Text("平均 · 速限 ${z.zone.limit}", color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .14f))) {
            Box(Modifier.fillMaxWidth((z.metres / span).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(purple))
        }
        Text("已行駛 ${Freeways.distance(z.metres)}，全長約 ${Freeways.distance(span)}", color = Color.White.copy(alpha = .65f), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 國道 shields are green, expressway ones blue, as on the real signs. */
@Composable
private fun RoadShield(road: String) {
    val freeway = road.startsWith("國道")
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (freeway) Color(0xFF0B7A3E) else Color(0xFF1F5FBF))
        .border(1.5.dp, Color.White, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(Freeways.short(road), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The next interchange, drawn like the green overhead sign: exit tab, name, arrow, distance. */
@Composable
private fun ExitSign(exit: Freeways.Ahead, then: Freeways.Ahead?) {
    val green = Brush.verticalGradient(listOf(Color(0xFF11904D), Color(0xFF07622F)))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(green)
        .border(2.dp, Color.White.copy(alpha = .92f), RoundedCornerShape(18.dp)).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (exit.place.exit.isNotBlank()) {
                Text("出口 ${exit.place.exit}", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = tabular,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFD60A)).padding(horizontal = 7.dp, vertical = 1.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text("下個出口", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NorthEast, null, Modifier.size(30.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            AnimatedContent(exit.place.name, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "exit", modifier = Modifier.weight(1f)) { name ->
                Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            val (num, unit) = Freeways.distance(exit.metres).split(' ').let { it[0] to it.getOrElse(1) { "" } }
            Text(num, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = tabular)
            Text(" $unit", color = Color.White.copy(alpha = .9f), fontSize = 15.sp)
        }
        if (exit.place.towards.isNotBlank()) Text("往 ${exit.place.towards}", color = Color.White.copy(alpha = .92f), fontSize = 15.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 36.dp))
        then?.let {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Color.White.copy(alpha = .22f)))
            Text("再下一個　${it.place.name} · ${Freeways.distance(it.metres)}", color = Color.White.copy(alpha = .85f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StopRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, a: Freeways.Ahead) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IOSIconTile(icon, tint, 30.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
            Text(a.place.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Freeways.distance(a.metres), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, style = tabular)
    }
}

/** Weather where the car will be: a tile per time, dry green or wet blue. */
@Composable
private fun RainTile(a: DrivingMode.Ahead, text: String, wet: Boolean, modifier: Modifier) {
    val tint = if (wet) Color(0xFF64B5FF) else Color(0xFF32D74B)
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(tint.copy(alpha = .16f), Color.White.copy(alpha = .04f))))
        .border(.5.dp, tint.copy(alpha = .35f), RoundedCornerShape(18.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (wet) Icons.Rounded.WaterDrop else Icons.Rounded.WbSunny, null, Modifier.size(18.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
            Text("${a.minutes} 分鐘後", fontSize = 14.sp, color = Color.White.copy(alpha = .8f), fontWeight = FontWeight.SemiBold)
        }
        Text("前方約 ${a.km} 公里", fontSize = 14.sp, color = Color.White.copy(alpha = .55f))
        Spacer(Modifier.height(4.dp))
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 23.sp)
    }
}

/**
 * 流動測速 and patrol spots have no public data, so they are the driver's own: one tap marks this
 * spot for this direction, and the next time it is announced like any camera (kept 120 days).
 */
@Composable
private fun MarkRow(onMark: (SpeedCameras.Kind) -> Unit) {
    var done by remember { mutableStateOf<SpeedCameras.Kind?>(null) }
    LaunchedEffect(done) { if (done != null) { delay(2500); done = null } }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(done?.let { "已標記${it.label}" } ?: "這裡有：", color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        listOf(SpeedCameras.Kind.MOBILE, SpeedCameras.Kind.POLICE).forEach { k ->
            Row(Modifier.clip(RoundedCornerShape(99.dp)).background(k.color().copy(alpha = .22f)).border(.75.dp, k.color().copy(alpha = .6f), RoundedCornerShape(99.dp))
                .clickable(onClickLabel = "標記${k.label}") { onMark(k); done = k }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(k.icon(), null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(5.dp))
                Text(if (k == SpeedCameras.Kind.MOBILE) "流動測速" else "警車", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
