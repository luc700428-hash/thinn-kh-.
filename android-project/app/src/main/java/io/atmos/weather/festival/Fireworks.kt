package io.atmos.weather.festival

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A fireworks show as a pure function of time: the same show looks the same in every window and
 * after every recomposition, and costs nothing between frames. Positions are fractions of the
 * screen (x of the width, y of the height from the top); speeds are fractions of the shorter side.
 */
object Fireworks {
    enum class Type { PEONY, WILLOW, RING, HEART, PALM, GLITTER, DOUBLE, CHRYSANTHEMUM }

    data class Shell(
        val launch: Float, val rise: Float, val x: Float, val apex: Float, val type: Type,
        val colour: Int, val second: Int, val count: Int, val speed: Float, val seed: Int, val tilt: Float,
    ) {
        val burst get() = launch + rise
        val life get() = when (type) { Type.WILLOW -> 4.2f; Type.PALM -> 3.2f; Type.GLITTER -> 3.0f; else -> 2.5f }
        val end get() = burst + life
    }

    val PALETTE = intArrayOf(
        0xFFFF4D5E.toInt(), 0xFFFFB238.toInt(), 0xFFFFE066.toInt(), 0xFF7CFF9B.toInt(), 0xFF4DD8FF.toInt(),
        0xFF6E8BFF.toInt(), 0xFFC77DFF.toInt(), 0xFFFF7AD9.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC870.toInt(),
    )
    const val GOLD = 0xFFFFC45C.toInt()

    /** Seconds between shells at second [s] of a show of [length] seconds: calm, busier, then a finale. */
    fun gap(s: Float, length: Int): Float = when {
        s < 4f -> .9f
        s > length - 28f -> .22f
        s > length * .4f -> .85f
        else -> 1.45f
    }

    /** Every shell of a show. [density] 0.5 halves the count for lighter phones. */
    fun show(seed: Long, length: Int = FestivalCalendar.SHOW_SECONDS, density: Float = 1f): List<Shell> {
        val r = Random(seed)
        val out = ArrayList<Shell>()
        var at = .3f
        while (at < length - 3.5f) {
            val finale = at > length - 28f
            val pair = !finale && at > length * .4f && r.nextFloat() < .3f
            repeat(if (pair) 2 else 1) { out += shell(r, at + it * .12f, finale) }
            at += gap(at, length) / density.coerceIn(.3f, 1f) * r.nextFloat().let { .7f + it * .6f }
        }
        return out
    }

    /** One shell launched at [at]. */
    fun shell(r: Random, at: Float, finale: Boolean = false): Shell {
        val type = if (finale) listOf(Type.PEONY, Type.GLITTER, Type.WILLOW, Type.CHRYSANTHEMUM, Type.DOUBLE).random(r)
        else Type.entries[r.nextInt(Type.entries.size)]
        val colour = if (type == Type.WILLOW) GOLD else PALETTE[r.nextInt(PALETTE.size)]
        val second = PALETTE[r.nextInt(PALETTE.size)]
        val count = when (type) { Type.PALM -> 9; Type.RING -> 48; Type.HEART -> 56; Type.WILLOW -> 70; Type.DOUBLE -> 110; else -> 110 }
        return Shell(at, 1.05f + r.nextFloat() * .55f, .12f + r.nextFloat() * .76f, .14f + r.nextFloat() * .26f,
            type, colour, second, count, .26f + r.nextFloat() * .14f, r.nextInt(), r.nextFloat())
    }

    /** One shell in each 20-second slot (its time counted from the slot's start): the odd firework on a festive night. */
    fun ambient(slot: Long): Shell {
        val r = Random(slot * 7919 + 17)
        return shell(r, 2f + r.nextFloat() * 14f).copy(count = 54)
    }

    private const val DRAG = 1.7f

    /** Distance travelled by a star after [tau] seconds, as a share of its launch speed. */
    fun reach(tau: Float, drag: Float = DRAG) = (1f - exp(-drag * tau)) / drag

    /** The rocket's height while rising, 0 at the bottom edge and [Shell.apex] at the burst. */
    fun rocketY(shell: Shell, s: Float): Float {
        val p = ((s - shell.launch) / shell.rise).coerceIn(0f, 1f)
        val eased = 1f - (1f - p).pow(2.2f)
        return 1.02f - (1.02f - shell.apex) * eased
    }

    /**
     * Writes the stars of [shell] at show second [s] into [out] as x,y pairs in pixels and returns
     * how many were written; [alpha] receives the shared brightness. [lag] draws a trail point.
     */
    fun stars(shell: Shell, s: Float, w: Float, h: Float, out: FloatArray, lag: Float = 0f, colourIndex: Int = 0): Int {
        val tau = s - shell.burst - lag
        if (tau < 0f || tau > shell.life) return 0
        val unit = minOf(w, h)
        val cx = shell.x * w; val cy = shell.apex * h
        val gravity = when (shell.type) { Type.WILLOW -> .22f; Type.PALM -> .16f; else -> .1f } * unit
        val fall = gravity * tau * tau * .5f
        val r = Random(shell.seed)
        val drag = if (shell.type == Type.WILLOW) 2.4f else DRAG
        val reach = reach(tau, drag) * shell.speed * unit
        var n = 0
        val total = shell.count
        for (i in 0 until total) {
            val jitter = r.nextFloat(); val jitter2 = r.nextFloat()
            if (shell.type == Type.DOUBLE && (i % 2) != colourIndex) continue
            if (shell.type != Type.DOUBLE && colourIndex != 0) continue
            val a = (2 * PI * i / total).toFloat() + (jitter - .5f) * .12f
            var dx: Float; var dy: Float
            when (shell.type) {
                Type.RING -> { val tilt = .35f + shell.tilt * .6f; dx = cos(a); dy = sin(a) * tilt }
                Type.HEART -> {
                    val t = a
                    dx = (16f * sin(t).pow(3)) / 17f
                    dy = -(13f * cos(t) - 5f * cos(2 * t) - 2f * cos(3 * t) - cos(4 * t)) / 17f
                }
                Type.DOUBLE -> { val k = if (i % 2 == 0) .55f else 1f; val ph = sqrt(1f - (jitter2 * 2 - 1).pow(2)); dx = cos(a) * k * ph; dy = sin(a) * k * ph }
                else -> { val ph = sqrt(1f - (jitter2 * 2 - 1).pow(2)) * .55f + .45f; dx = cos(a) * ph; dy = sin(a) * ph }
            }
            val speed = if (shell.type == Type.PALM) 1.1f else .9f + jitter * .15f
            if (n + 1 >= out.size) break
            out[n] = cx + dx * reach * speed; out[n + 1] = cy + dy * reach * speed + fall
            n += 2
        }
        return n / 2
    }

    /** Brightness of a shell's stars at show second [s]: a flash, a hold and a fade; glitter twinkles. */
    fun alpha(shell: Shell, s: Float): Float {
        val tau = s - shell.burst
        if (tau < 0f || tau > shell.life) return 0f
        val p = tau / shell.life
        val base = if (p < .55f) 1f else (1f - (p - .55f) / .45f).coerceIn(0f, 1f)
        return if (shell.type == Type.GLITTER && p > .35f) base * (if (((tau * 23f).toInt() + shell.seed) % 3 == 0) .25f else 1f) else base
    }

    /** Sounds to play between show seconds [from] (exclusive) and [to] (inclusive). */
    enum class Cue { LAUNCH, BOOM, BOOM_SMALL, CRACKLE }

    fun cues(shells: List<Shell>, from: Float, to: Float): List<Cue> {
        val cues = ArrayList<Cue>()
        for (sh in shells) {
            if (sh.launch > from && sh.launch <= to && sh.count >= 48 && (sh.seed and 1) == 0) cues += Cue.LAUNCH
            if (sh.burst > from && sh.burst <= to) cues += if (sh.type == Type.PEONY || sh.type == Type.DOUBLE || sh.type == Type.CHRYSANTHEMUM) Cue.BOOM else Cue.BOOM_SMALL
            val crackle = sh.burst + .6f
            if ((sh.type == Type.GLITTER || sh.type == Type.WILLOW) && crackle > from && crackle <= to) cues += Cue.CRACKLE
        }
        return cues
    }
}
