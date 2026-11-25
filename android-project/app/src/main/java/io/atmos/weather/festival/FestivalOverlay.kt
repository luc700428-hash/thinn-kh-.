package io.atmos.weather.festival

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.atmos.weather.ui.GlassQuality
import io.atmos.weather.ui.rememberAppleReduceMotion
import io.atmos.weather.ui.rememberBooleanSetting
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The festival layer over every page. It draws nothing and costs nothing on ordinary days; on a
 * festival it never takes a touch, and only the top-most window draws. [host] is the main
 * window, which also plays the sounds.
 */
@Composable
fun FestivalOverlay(host: Boolean) {
    if (FestivalDirector.disabled) return
    val ctx = LocalContext.current
    val enabled = rememberBooleanSetting(FestivalDirector.SETTING, true)
    val reduce = rememberAppleReduceMotion()
    val preview = FestivalDirector.preview
    var coarse by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(preview) {
        while (true) {
            coarse = System.currentTimeMillis(); FestivalDirector.expire(coarse)
            delay(if (FestivalDirector.preview != null) 1_000 else 15_000)
        }
    }
    val probe = FestivalDirector.now(coarse)
    val active = enabled && (FestivalDirector.preview != null || FestivalCalendar.at(probe) != null ||
        FestivalCalendar.countdown(probe + 20_000) != null || FestivalCalendar.show(probe) != null)
    val token = remember { Any() }
    DisposableEffect(active) {
        if (active) FestivalDirector.layers += token
        onDispose { FestivalDirector.layers.remove(token) }
    }
    if (!active) return

    if (host) {
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) FestivalSound.open(ctx)
            val observer = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_START) FestivalSound.open(ctx)
                if (e == Lifecycle.Event.ON_STOP) FestivalSound.close()
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); FestivalSound.close() }
        }
    }

    val light = GlassQuality.current() == GlassQuality.Level.LIGHT
    val density = LocalDensity.current.density
    val scene = remember(density, light) { FestivalScene(density, light) }
    val shows = remember { HashMap<Long, List<Fireworks.Shell>>() }
    fun showShells(show: FestivalCalendar.Show) = shows.getOrPut(show.seed) { Fireworks.show(show.seed, show.seconds, if (light) .55f else 1f) }
    val clock = remember { mutableLongStateOf(FestivalDirector.now(System.currentTimeMillis())) }

    LaunchedEffect(reduce, light) {
        var last = FestivalDirector.now(System.currentTimeMillis())
        fun step() {
            val now = FestivalDirector.now(System.currentTimeMillis())
            val festival = FestivalCalendar.at(now)
            FestivalDirector.observe(festival?.name + "/" + ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), FestivalCalendar.zone).toLocalDate(), now)
            if (host && now > last && now - last < 5_000) cues(last, now, festival, ::showShells)
            last = now; clock.longValue = now
        }
        while (true) {
            if (reduce) { delay(500); step() } else withFrameMillis { step() }
        }
    }

    val top by remember { derivedStateOf { FestivalDirector.layers.lastOrNull() === token } }
    if (!top) return

    Box(Modifier.fillMaxSize()) {
        if (!reduce) Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val now = clock.longValue
            val t = (now % 86_400_000L) / 1000.0
            val festival = FestivalCalendar.at(now)
            val show = FestivalCalendar.show(now)
            with(scene) {
                if (show != null) {
                    val s = (now - show.start) / 1000f
                    val veil = minOf(1f, s / 1.5f, (show.seconds - s) / 2.5f).coerceIn(0f, 1f) * .55f
                    drawRect(Color(0xFF060814), alpha = veil)
                    fireworks(showShells(show), s)
                    if (show.firecrackers) firecrackerSparks(t)
                } else if (festival != null && FestivalCalendar.isNight(now) && festival in NIGHT_FIREWORKS) {
                    val slot = Math.floorDiv(now, 20_000L)
                    for (sl in listOf(slot - 1, slot)) fireworks(listOf(Fireworks.ambient(sl)), (now - sl * 20_000L) / 1000f)
                }
                when (festival) {
                    Festival.CHRISTMAS -> {
                        snow(t)
                        FestivalDirector.progress(FestivalDirector.Pass.SANTA, now)?.let { p ->
                            santa(p, FestivalDirector.starts(FestivalDirector.Pass.SANTA, now).last { it <= now }, t)
                        }
                    }
                    Festival.NEW_YEAR, Festival.NEW_YEARS_EVE -> { sparkles(t); if (show == null) confetti(t, count = 18) }
                    Festival.LUNAR_NEW_YEAR -> { hangingLanterns(t); sparkles(t, Color(0xFFFFC542), 14) }
                    Festival.LANTERN -> { skyLanterns(t); hangingLanterns(t) }
                    Festival.VALENTINE -> hearts(t, false)
                    Festival.QIXI -> hearts(t, true)
                    Festival.CHILDREN -> balloons(t)
                    Festival.QINGMING -> willowLeaves(t)
                    Festival.MOTHERS_DAY -> carnations(t)
                    Festival.DRAGON_BOAT -> FestivalDirector.progress(FestivalDirector.Pass.BOAT, now)?.let { p ->
                        dragonBoat(p, FestivalDirector.starts(FestivalDirector.Pass.BOAT, now).last { it <= now }, t)
                    }
                    Festival.FATHERS_DAY -> ties(t)
                    Festival.GHOST_FESTIVAL -> waterLanterns(t)
                    Festival.MID_AUTUMN -> { moon(t); hangingLanterns(t) }
                    Festival.TEACHERS_DAY -> paperPlanes(t)
                    Festival.NATIONAL_DAY -> { if (show == null) confetti(t, NATIONAL_COLOURS, 20); flag(t, 24 * density) }
                    Festival.HALLOWEEN -> {
                        halloween(t)
                        FestivalDirector.progress(FestivalDirector.Pass.GHOST, now)?.let { p ->
                            ghost(p, FestivalDirector.starts(FestivalDirector.Pass.GHOST, now).last { it <= now }, t)
                        }
                    }
                    Festival.WINTER_SOLSTICE -> tangyuan(t)
                    null -> Unit
                }
            }
        }
        FestivalWords(clock)
    }
}

private val NIGHT_FIREWORKS = setOf(Festival.NATIONAL_DAY, Festival.LUNAR_NEW_YEAR, Festival.NEW_YEAR, Festival.NEW_YEARS_EVE)
private val NATIONAL_COLOURS = listOf(Color(0xFFFE0000), Color(0xFF000095), Color.White, Color(0xFFFF5A6E), Color(0xFF3D5AFE))

private val shadowed = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 14f))

/** The greeting, the countdown and the midnight line: always white on a dark glass pill, readable on any theme. */
@Composable
private fun FestivalWords(clock: androidx.compose.runtime.MutableLongState) {
    val greeting by remember { derivedStateOf {
        val now = clock.longValue
        FestivalDirector.progress(FestivalDirector.Pass.BANNER, now)?.let { FestivalCalendar.at(now)?.let { f -> greetingFor(f) } }
    } }
    val countdown by remember { derivedStateOf {
        val now = clock.longValue
        FestivalCalendar.countdown(now)?.let { c -> c.title to ceil((c.target - now) / 1000.0).toLong() }
    } }
    val headline by remember { derivedStateOf {
        val now = clock.longValue
        FestivalCalendar.show(now)?.takeIf { now - it.start < 8_000 }?.title
    } }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        val pill = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
            .background(Color(0xD9101218), RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 9.dp)
        countdown?.let { (title, left) ->
            Text("$title  ${"%02d:%02d".format(left / 60, left % 60)}", pill, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (left in 1..10) {
                Text("$left", Modifier.align(Alignment.Center).graphicsLayer {
                    val frac = ((clock.longValue % 1000L) / 1000f)
                    val pop = 1.35f - .35f * minOf(1f, frac * 3f)
                    scaleX = pop; scaleY = pop; alpha = 1f - maxOf(0f, frac - .75f) * 2f
                }, color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Black, style = shadowed)
            }
        } ?: greeting?.let { Text(it, pill.graphicsLayer {
            val p = FestivalDirector.progress(FestivalDirector.Pass.BANNER, clock.longValue) ?: 6f
            alpha = minOf(1f, p / .4f, (6f - p) / .6f).coerceIn(0f, 1f); translationY = (1f - minOf(1f, p / .4f)) * -24f
        }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        headline?.let {
            Text(it, Modifier.align(Alignment.Center).padding(horizontal = 24.dp), color = Color(0xFFFFE6A0), fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, style = shadowed)
        }
    }
}

private fun greetingFor(f: Festival) = when (f) {
    Festival.CHRISTMAS -> "🎄 " + f.greeting
    Festival.HALLOWEEN -> "🎃 " + f.greeting
    Festival.LUNAR_NEW_YEAR -> "🧧 " + f.greeting
    Festival.LANTERN -> "🏮 " + f.greeting
    Festival.MID_AUTUMN -> "🥮 " + f.greeting
    Festival.DRAGON_BOAT -> "🐉 " + f.greeting
    Festival.NATIONAL_DAY, Festival.NEW_YEAR, Festival.NEW_YEARS_EVE -> "🎆 " + f.greeting
    Festival.CHILDREN -> "🎈 " + f.greeting
    Festival.MOTHERS_DAY -> "💐 " + f.greeting
    Festival.TEACHERS_DAY -> "📚 " + f.greeting
    Festival.WINTER_SOLSTICE -> "🍡 " + f.greeting
    Festival.QINGMING -> "🌿 " + f.greeting
    Festival.FATHERS_DAY -> "👔 " + f.greeting
    Festival.GHOST_FESTIVAL -> "🪷 " + f.greeting
    else -> "💝 " + f.greeting
}

/** Sounds due between two moments of the festival clock. */
private fun cues(from: Long, to: Long, festival: Festival?, shells: (FestivalCalendar.Show) -> List<Fireworks.Shell>) {
    FestivalCalendar.show(to)?.let { show ->
        val s0 = (from - show.start) / 1000f; val s1 = (to - show.start) / 1000f
        Fireworks.cues(shells(show), s0, s1).take(3).forEach { FestivalSound.play(it.clip()) }
        if (show.firecrackers && floor(s0 / 22f) != floor(s1 / 22f)) FestivalSound.play(FestivalSound.Clip.FIRECRACKER)
    }
    (FestivalCalendar.countdown(from) ?: FestivalCalendar.countdown(to))?.let { c ->
        val r0 = ceil((c.target - from) / 1000.0).toLong(); val r1 = ceil((c.target - to) / 1000.0).toLong()
        if (r1 != r0 && r1 in 1..10) FestivalSound.play(FestivalSound.Clip.TICK)
        if (from < c.target && to >= c.target) FestivalSound.play(FestivalSound.Clip.CHIME)
    }
    if (festival != null && FestivalCalendar.show(to) == null && FestivalCalendar.isNight(to) && festival in NIGHT_FIREWORKS) {
        val slot = Math.floorDiv(to, 20_000L); val sh = Fireworks.ambient(slot)
        val s0 = (from - slot * 20_000L) / 1000f; val s1 = (to - slot * 20_000L) / 1000f
        Fireworks.cues(listOf(sh), s0, s1).filter { it != Fireworks.Cue.LAUNCH }.forEach { FestivalSound.play(it.clip(), .6f) }
    }
    fun began(pass: FestivalDirector.Pass) = FestivalDirector.starts(pass, to).any { it in (from + 1)..to }
    when (festival) {
        Festival.CHRISTMAS -> if (began(FestivalDirector.Pass.SANTA)) { FestivalSound.play(FestivalSound.Clip.SLEIGH); FestivalSound.carol() }
        Festival.DRAGON_BOAT -> if (began(FestivalDirector.Pass.BOAT)) FestivalSound.play(FestivalSound.Clip.DRUM)
        Festival.HALLOWEEN -> if (began(FestivalDirector.Pass.GHOST)) FestivalSound.play(FestivalSound.Clip.GHOST)
        Festival.LUNAR_NEW_YEAR -> if (began(FestivalDirector.Pass.BANNER)) FestivalSound.play(FestivalSound.Clip.FIRECRACKER)
        else -> Unit
    }
}

private fun Fireworks.Cue.clip() = when (this) {
    Fireworks.Cue.LAUNCH -> FestivalSound.Clip.LAUNCH
    Fireworks.Cue.BOOM -> FestivalSound.Clip.BOOM
    Fireworks.Cue.BOOM_SMALL -> FestivalSound.Clip.BOOM_SMALL
    Fireworks.Cue.CRACKLE -> FestivalSound.Clip.CRACKLE
}
