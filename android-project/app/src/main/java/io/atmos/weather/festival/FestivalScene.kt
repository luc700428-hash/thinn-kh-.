package io.atmos.weather.festival

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Everything the festivals draw, in plain vector shapes (no images, no characters from anyone
 * else's work). Each function is a pure picture of time [t] (seconds) so it can run in any window.
 */
internal class FestivalScene(private val density: Float, private val light: Boolean) {
    private val u = density
    private val points = FloatArray(2 * 200)
    private val trail = FloatArray(2 * 200)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private var flag: ImageBitmap? = null

    /** A share of the effect for lighter phones. */
    private fun n(full: Int) = if (light) (full * .5f).toInt().coerceAtLeast(3) else full

    companion object {
        /** A stable pseudo-random number in [0,1) for item [i] and property [k]. */
        fun rnd(i: Int, k: Int): Float {
            var h = i * 374761393 + k * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777216f
        }

        fun wrap(v: Double, m: Double) = ((v % m) + m) % m

        fun heart(size: Float) = Path().apply {
            moveTo(0f, size * .3f)
            cubicTo(-size * .9f, -size * .25f, -size * .45f, -size * .95f, 0f, -size * .45f)
            cubicTo(size * .45f, -size * .95f, size * .9f, -size * .25f, 0f, size * .3f)
            close()
        }
    }

    // ---- particle fields ----

    /** Items drifting down (or up when [rise]) the screen; [draw] gets x, y, scale, item and phase. */
    private inline fun DrawScope.drift(count: Int, t: Double, rise: Boolean, minSpeed: Float, maxSpeed: Float, sway: Float,
                                       draw: DrawScope.(Float, Float, Float, Int, Float) -> Unit) {
        val w = size.width; val h = size.height
        for (i in 0 until count) {
            val sc = .6f + rnd(i, 1) * .7f
            val speed = (minSpeed + (maxSpeed - minSpeed) * rnd(i, 2)) * sc
            val travel = h * 1.25
            val pos = wrap(t * speed * h + rnd(i, 3) * travel, travel).toFloat() - h * .12f
            val y = if (rise) h - pos else pos
            val ph = rnd(i, 4) * 6.28f
            val x = rnd(i, 5) * w + sin(t * (.5 + rnd(i, 6)) + ph).toFloat() * sway * u
            draw(x, y, sc, i, ph)
        }
    }

    fun DrawScope.snow(t: Double) = drift(n(70), t, false, .028f, .07f, 16f) { x, y, sc, i, _ ->
        val r = (1.3f + 2.6f * sc) * u
        drawCircle(Color(0x33405A7A), r + .9f * u, Offset(x, y))
        drawCircle(Color.White.copy(alpha = .62f + .3f * rnd(i, 7)), r, Offset(x, y))
        if (sc > 1.1f && !light) {
            val arm = r * 2.3f
            for (k in 0 until 3) rotate(k * 60f + (t * 20 % 360).toFloat(), Offset(x, y)) {
                drawLine(Color.White.copy(alpha = .8f), Offset(x - arm, y), Offset(x + arm, y), .9f * u, StrokeCap.Round)
            }
        }
    }

    private val confettiColours = listOf(Color(0xFFFF5A6E), Color(0xFFFFC542), Color(0xFF4DD8FF), Color(0xFF7CE38B), Color(0xFFB98CFF), Color(0xFFFF8FD1))

    fun DrawScope.confetti(t: Double, colours: List<Color> = confettiColours, count: Int = 28) =
        drift(n(count), t, false, .05f, .09f, 22f) { x, y, sc, i, ph ->
            val flip = cos(t * (2.0 + rnd(i, 8) * 3) + ph).toFloat()
            withTransform({ rotate((t * (60 + 120 * rnd(i, 9)) % 360).toFloat(), Offset(x, y)); scale(flip, 1f, Offset(x, y)) }) {
                drawRect(colours[i % colours.size], Offset(x - 4f * u * sc, y - 2f * u * sc), Size(8f * u * sc, 4f * u * sc), alpha = .9f)
            }
        }

    fun DrawScope.sparkles(t: Double, colour: Color = Color(0xFFFFD36B), count: Int = 18) {
        val w = size.width; val h = size.height
        for (i in 0 until n(count)) {
            val life = 2.4 + rnd(i, 1) * 2
            val cycle = floor((t + rnd(i, 2) * life) / life).toInt()
            val p = (wrap(t + rnd(i, 2) * life, life) / life).toFloat()
            val a = sin(p * PI).toFloat()
            val x = rnd(i * 31 + cycle, 3) * w; val y = rnd(i * 17 + cycle, 4) * h * .85f
            star4(Offset(x, y), (4f + 6f * rnd(i, 5)) * u * a, colour.copy(alpha = a * .9f))
        }
    }

    private fun DrawScope.star4(c: Offset, r: Float, colour: Color) {
        if (r <= 0f) return
        val p = Path().apply {
            moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
            quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
        }
        drawPath(p, colour)
    }

    fun DrawScope.hearts(t: Double, stars: Boolean) {
        drift(n(16), t, true, .03f, .055f, 18f) { x, y, sc, i, _ ->
            val s = (12f + 10f * sc) * u
            val c = if (i % 3 == 0) Color(0xFFFF8FB8) else if (i % 3 == 1) Color(0xFFFF4D6D) else Color(0xFFE0457B)
            translate(x, y) { drawPath(heart(s), c.copy(alpha = .78f)); drawCircle(Color.White.copy(alpha = .45f), s * .09f, Offset(-s * .28f, -s * .3f)) }
        }
        if (stars) sparkles(t, Color(0xFFBFD7FF), 22)
    }

    fun DrawScope.balloons(t: Double) {
        val colours = listOf(Color(0xFFFF5A6E), Color(0xFFFFC542), Color(0xFF4DA3FF), Color(0xFF6BD48A), Color(0xFFB98CFF), Color(0xFFFF8F4D))
        drift(n(12), t, true, .035f, .06f, 14f) { x, y, sc, i, ph ->
            val bw = 22f * u * sc; val bh = 28f * u * sc
            val c = colours[i % colours.size]
            val string = Path().apply {
                moveTo(x, y + bh / 2 + 3 * u)
                cubicTo(x + 6 * u * sin(t * 2 + ph).toFloat(), y + bh, x - 6 * u, y + bh * 1.5f, x + 3 * u, y + bh * 2.1f)
            }
            drawPath(string, Color(0x99556070), style = Stroke(1f * u))
            drawOval(Brush.radialGradient(listOf(c.copy(red = minOf(1f, c.red + .25f), green = minOf(1f, c.green + .2f), blue = minOf(1f, c.blue + .2f)), c),
                Offset(x - bw * .15f, y - bh * .2f), bw * .9f), Offset(x - bw / 2, y - bh / 2), Size(bw, bh), alpha = .92f)
            drawPath(Path().apply { moveTo(x - 3 * u, y + bh / 2 + 3 * u); lineTo(x + 3 * u, y + bh / 2 + 3 * u); lineTo(x, y + bh / 2 - 1 * u); close() }, c)
            drawOval(Color.White.copy(alpha = .5f), Offset(x - bw * .3f, y - bh * .32f), Size(bw * .2f, bh * .25f))
        }
    }

    fun DrawScope.skyLanterns(t: Double) = drift(n(14), t, true, .018f, .035f, 12f) { x, y, sc, i, ph ->
        val s = sc * u
        val flicker = .85f + .15f * sin(t * 9 + ph).toFloat()
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFB347), Color(0x00FFB347)), Offset(x, y), 34f * s), 34f * s, Offset(x, y), alpha = flicker)
        val body = Path().apply {
            moveTo(x - 11 * s, y - 16 * s); quadraticTo(x, y - 21 * s, x + 11 * s, y - 16 * s)
            lineTo(x + 8 * s, y + 13 * s); lineTo(x - 8 * s, y + 13 * s); close()
        }
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFE6A8), Color(0xFFFF9E3D), Color(0xFFE2582B)), y - 20 * s, y + 14 * s), alpha = .95f)
        drawOval(Color(0xFFFFF4C7).copy(alpha = flicker), Offset(x - 3 * s, y + 7 * s), Size(6 * s, 5 * s))
    }

    fun DrawScope.willowLeaves(t: Double) = drift(n(16), t, false, .02f, .04f, 30f) { x, y, sc, i, ph ->
        rotate((sin(t * 1.4 + ph) * 50 + i * 37).toFloat(), Offset(x, y)) {
            drawOval(Color(if (i % 2 == 0) 0xFF7FB069 else 0xFF9CC77E).copy(alpha = .8f), Offset(x - 9 * u * sc, y - 2.5f * u * sc), Size(18 * u * sc, 5 * u * sc))
            drawLine(Color(0xFF55804A).copy(alpha = .6f), Offset(x - 8 * u * sc, y), Offset(x + 8 * u * sc, y), .7f * u)
        }
    }

    fun DrawScope.petals(t: Double) = drift(n(22), t, false, .03f, .055f, 26f) { x, y, sc, i, ph ->
        rotate((t * (40 + 60 * rnd(i, 9)) + i * 50).toFloat() % 360, Offset(x, y)) {
            val c = when (i % 3) { 0 -> Color(0xFFFF6F91); 1 -> Color(0xFFFF9EB5); else -> Color(0xFFE63B5C) }
            drawOval(c.copy(alpha = .85f), Offset(x - 5 * u * sc, y - 3.5f * u * sc), Size(10 * u * sc, 7 * u * sc))
            drawLine(Color.White.copy(alpha = .5f), Offset(x - 3 * u * sc, y - 1 * u * sc), Offset(x + 3 * u * sc, y + 1 * u * sc), .6f * u)
        }
    }

    fun DrawScope.carnations(t: Double) {
        petals(t)
        val h = size.height
        for ((k, x) in listOf(size.width * .1f, size.width * .9f).withIndex()) {
            val y = h * .86f; val sway = sin(t * 1.2 + k).toFloat() * 4f
            drawLine(Color(0xFF4E8B4A), Offset(x, y + 46 * u), Offset(x + sway * u, y), 2.4f * u, StrokeCap.Round)
            drawOval(Color(0xFF6AA85F), Offset(x - 12 * u, y + 18 * u), Size(12 * u, 5 * u))
            for (j in 0 until 9) rotate(j * 40f + k * 20, Offset(x + sway * u, y)) {
                drawOval(Color(if (j % 2 == 0) 0xFFE8436A else 0xFFFF7A9A), Offset(x + sway * u - 4 * u, y - 15 * u), Size(8 * u, 13 * u), alpha = .95f)
            }
            drawCircle(Color(0xFFC72C54), 5 * u, Offset(x + sway * u, y))
        }
    }

    fun DrawScope.ties(t: Double) {
        drift(n(12), t, false, .025f, .045f, 24f) { x, y, sc, i, ph ->
            val s = sc * u
            val c = if (i % 2 == 0) Color(0xFF2F5DA8) else Color(0xFF2A9D8F)
            rotate((sin(t * 1.6 + ph) * 25).toFloat(), Offset(x, y)) {
                drawPath(Path().apply { moveTo(x - 4 * s, y - 14 * s); lineTo(x + 4 * s, y - 14 * s); lineTo(x + 2.5f * s, y - 8 * s); lineTo(x - 2.5f * s, y - 8 * s); close() }, c)
                drawPath(Path().apply { moveTo(x - 2.5f * s, y - 8 * s); lineTo(x + 2.5f * s, y - 8 * s); lineTo(x + 6 * s, y + 12 * s); lineTo(x, y + 18 * s); lineTo(x - 6 * s, y + 12 * s); close() }, c)
                drawLine(Color.White.copy(alpha = .5f), Offset(x - 4 * s, y), Offset(x + 3 * s, y - 4 * s), 1.4f * s)
                drawLine(Color.White.copy(alpha = .5f), Offset(x - 4.5f * s, y + 8 * s), Offset(x + 5 * s, y + 3 * s), 1.4f * s)
            }
        }
        sparkles(t, Color(0xFF8CC8FF), 12)
    }

    fun DrawScope.tangyuan(t: Double) = drift(n(14), t, true, .015f, .03f, 10f) { x, y, sc, i, ph ->
        val r = (8f + 5f * sc) * u
        val base = if (i % 2 == 0) Color(0xFFFFF7EE) else Color(0xFFFFB7C5)
        drawCircle(Color(0x22000000), r, Offset(x + 1.5f * u, y + 2 * u))
        drawCircle(Brush.radialGradient(listOf(Color.White, base), Offset(x - r * .35f, y - r * .35f), r * 1.4f), r, Offset(x, y))
        val steam = Path().apply {
            moveTo(x, y - r - 2 * u)
            for (k in 1..3) quadraticTo(x + (if (k % 2 == 0) -5 else 5) * u, y - r - (k * 6 - 3) * u, x, y - r - k * 6 * u)
        }
        drawPath(steam, Color.White.copy(alpha = .35f + .2f * sin(t * 2 + ph).toFloat()), style = Stroke(1.2f * u, cap = StrokeCap.Round))
    }

    // ---- hanging red lanterns and the moon ----

    fun DrawScope.hangingLanterns(t: Double, count: Int = 2) {
        val w = size.width
        val xs = if (count == 2) listOf(w * .09f, w * .91f) else listOf(w * .09f, w * .26f, w * .91f)
        xs.forEachIndexed { k, x -> lantern(Offset(x, 0f), 58f * u + k % 2 * 14 * u, (sin(t * 1.3 + k * 1.7) * 6).toFloat(), t, k) }
    }

    private fun DrawScope.lantern(top: Offset, rope: Float, swing: Float, t: Double, k: Int) = rotate(swing, top) {
        val cx = top.x; val cy = top.y + rope + 20 * u
        drawLine(Color(0xFF8A5A2B), top, Offset(cx, cy - 20 * u), 1.4f * u)
        drawCircle(Brush.radialGradient(listOf(Color(0x55FF5A3C), Color(0x00FF5A3C)), Offset(cx, cy), 46 * u), 46 * u, Offset(cx, cy))
        drawRect(Color(0xFFE2B33C), Offset(cx - 10 * u, cy - 22 * u), Size(20 * u, 5 * u))
        drawRect(Color(0xFFE2B33C), Offset(cx - 10 * u, cy + 17 * u), Size(20 * u, 5 * u))
        drawOval(Brush.radialGradient(listOf(Color(0xFFFF6B4A), Color(0xFFD7261E), Color(0xFFA3150F)), Offset(cx - 6 * u, cy - 4 * u), 30 * u),
            Offset(cx - 23 * u, cy - 19 * u), Size(46 * u, 38 * u))
        for (j in -2..2) drawArc(Color(0x55800E0A), -90f, 180f, false, Offset(cx - abs(j) * 0f - j * 4.5f * u - 4 * u, cy - 19 * u), Size(8 * u + abs(j) * 2 * u, 38 * u), style = Stroke(.8f * u))
        for (j in -2..2) drawLine(Color(0xFFE2B33C), Offset(cx + j * 2.2f * u, cy + 22 * u), Offset(cx + j * 2.8f * u + sin(t * 3 + j + k).toFloat() * u, cy + 40 * u), 1.2f * u)
        drawIntoCanvas {
            text.textSize = 17 * u; text.color = 0xFFFFD86B.toInt()
            it.nativeCanvas.drawText("福", cx, cy + 6 * u, text)
        }
    }

    fun DrawScope.moon(t: Double) {
        val c = Offset(size.width * .8f, size.height * .17f); val r = 40f * u
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFE9A3), Color(0x00FFE9A3)), c, r * 3f), r * 3f, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFBE6), Color(0xFFFFE9A8)), Offset(c.x - r * .3f, c.y - r * .3f), r * 1.4f), r, c)
        for ((dx, dy, rr) in listOf(Triple(.35f, -.3f, .16f), Triple(-.25f, .4f, .12f), Triple(.45f, .35f, .09f))) drawCircle(Color(0x18A08040), r * rr, Offset(c.x + dx * r, c.y + dy * r))
        // A rabbit sitting in the moon.
        val rb = Color(0x40B08A40); val bx = c.x - r * .18f; val by = c.y + r * .22f
        drawOval(rb, Offset(bx - r * .28f, by - r * .14f), Size(r * .5f, r * .36f))
        drawCircle(rb, r * .14f, Offset(bx + r * .2f, by - r * .2f))
        rotate(-15f, Offset(bx + r * .2f, by - r * .3f)) { drawOval(rb, Offset(bx + r * .14f, by - r * .62f), Size(r * .09f, r * .34f)) }
        rotate(10f, Offset(bx + r * .26f, by - r * .3f)) { drawOval(rb, Offset(bx + r * .25f, by - r * .6f), Size(r * .09f, r * .32f)) }
        // A shooting star every 12 seconds.
        val p = (wrap(t, 12.0) / 1.2).toFloat()
        if (p < 1f) {
            val s = Offset(size.width * (.15f + .2f * rnd(floor(t / 12).toInt(), 1)), size.height * .08f)
            val head = Offset(s.x + p * 160 * u, s.y + p * 70 * u)
            drawLine(Brush.linearGradient(listOf(Color(0x00FFFFFF), Color(0xCCFFFFFF)), Offset(head.x - 60 * u, head.y - 26 * u), head),
                Offset(head.x - 60 * u, head.y - 26 * u), head, 2f * u, StrokeCap.Round, alpha = 1 - p)
        }
    }

    // ---- passes ----

    fun DrawScope.santa(p: Float, seed: Long, t: Double) {
        val w = size.width; val h = size.height
        val leftToRight = seed % 2L == 0L
        val k = (p / 10f).coerceIn(0f, 1f)
        val x = if (leftToRight) -w * .45f + k * w * 1.9f else w * 1.45f - k * w * 1.9f
        val baseY = h * (.12f + .18f * rnd(seed.toInt(), 3))
        val y = baseY + sin(k * PI * 2.2).toFloat() * 18 * u - k * 24 * u
        val s = u * (if (w > 900 * u) 1.3f else 1f)
        // Sparkle trail.
        for (j in 1..16) {
            val back = j * 9f * s
            val tx = x + if (leftToRight) -back - 40 * s else back + 40 * s
            val ty = y + sin((k - j * .01f) * PI * 2.2).toFloat() * 18 * u + 4 * s + sin(t * 8 + j).toFloat() * 3 * s
            star4(Offset(tx, ty), (5f - j * .25f) * s, Color(0xFFFFE08A).copy(alpha = (1f - j / 17f) * .9f))
        }
        withTransform({ translate(x, y); if (!leftToRight) scale(-1f, 1f, Offset.Zero) }) {
            val gallop = (t * 11).toFloat()
            // Reindeer, the leader with a glowing red nose.
            for (r in 0 until 4) {
                val rx = 34 * s + r * 30 * s; val ry = -10 * s + sin(gallop + r * .8f) * 2.5f * s
                drawLine(Color(0xFFB08A5A), Offset(-4 * s, -8 * s), Offset(rx - 6 * s, ry), .9f * s)
                reindeer(Offset(rx, ry), s, gallop + r * .8f, r == 3)
            }
            // The sleigh.
            drawCircle(Color(0xFF7A4A2A), 9 * s, Offset(-26 * s, -20 * s))
            drawCircle(Color(0x33FFFFFF), 3 * s, Offset(-29 * s, -23 * s))
            val sleigh = Path().apply {
                moveTo(-36 * s, -18 * s); lineTo(-36 * s, -4 * s); quadraticTo(-36 * s, 2 * s, -28 * s, 2 * s)
                lineTo(2 * s, 2 * s); quadraticTo(12 * s, 2 * s, 12 * s, -8 * s); quadraticTo(12 * s, -16 * s, 4 * s, -14 * s)
                lineTo(-12 * s, -10 * s); lineTo(-20 * s, -18 * s); close()
            }
            drawPath(sleigh, Brush.verticalGradient(listOf(Color(0xFFE53935), Color(0xFFA31515)), -18 * s, 2 * s))
            drawLine(Color(0xFFFFD36B), Offset(-34 * s, -6 * s), Offset(8 * s, -6 * s), 1.6f * s)
            val runner = Path().apply { moveTo(-38 * s, 8 * s); lineTo(10 * s, 8 * s); quadraticTo(20 * s, 8 * s, 18 * s, 0f); quadraticTo(16 * s, -5 * s, 12 * s, -2 * s) }
            drawPath(runner, Color(0xFFFFC94A), style = Stroke(1.8f * s, cap = StrokeCap.Round))
            drawLine(Color(0xFFFFC94A), Offset(-30 * s, 2 * s), Offset(-30 * s, 8 * s), 1.4f * s)
            drawLine(Color(0xFFFFC94A), Offset(0f, 2 * s), Offset(0f, 8 * s), 1.4f * s)
            // Santa.
            drawCircle(Color(0xFFD32F2F), 8 * s, Offset(-14 * s, -20 * s))
            drawRect(Color(0xFF3A2A1A), Offset(-21 * s, -17 * s), Size(14 * s, 2.2f * s))
            drawCircle(Color(0xFFFFD7B8), 4.6f * s, Offset(-12 * s, -31 * s))
            drawCircle(Color.White, 4.4f * s, Offset(-10.5f * s, -27.5f * s))
            drawPath(Path().apply { moveTo(-17 * s, -33 * s); lineTo(-7 * s, -33 * s); lineTo(-16 * s, -43 * s); close() }, Color(0xFFD32F2F))
            drawRect(Color.White, Offset(-17.5f * s, -34.5f * s), Size(11 * s, 2.6f * s))
            drawCircle(Color.White, 2 * s, Offset(-16.5f * s, -43 * s))
            drawCircle(Color(0xFF3A2A1A), .7f * s, Offset(-10 * s, -32 * s))
            // A waving arm.
            val wave = sin(t * 7).toFloat() * 25f
            rotate(-40f + wave, Offset(-9 * s, -23 * s)) { drawLine(Color(0xFFD32F2F), Offset(-9 * s, -23 * s), Offset(-9 * s, -33 * s), 3.2f * s, StrokeCap.Round); drawCircle(Color.White, 1.8f * s, Offset(-9 * s, -34 * s)) }
        }
    }

    private fun DrawScope.reindeer(at: Offset, s: Float, phase: Float, leader: Boolean) {
        val body = Color(0xFF8B5A2B); val dark = Color(0xFF5E3A1A)
        for (leg in 0 until 4) {
            val lx = at.x + (if (leg < 2) 6 else -6) * s + (leg % 2) * 2 * s
            val swing = sin(phase + leg * 1.6f) * 5 * s
            drawLine(dark, Offset(lx, at.y + 3 * s), Offset(lx + swing, at.y + 11 * s), 1.6f * s, StrokeCap.Round)
        }
        drawOval(body, Offset(at.x - 10 * s, at.y - 4 * s), Size(20 * s, 9 * s))
        drawOval(Color(0xFFE9D7BC), Offset(at.x - 10 * s, at.y - 1 * s), Size(5 * s, 4 * s))
        drawLine(body, Offset(at.x + 7 * s, at.y - 2 * s), Offset(at.x + 11 * s, at.y - 9 * s), 4f * s, StrokeCap.Round)
        drawOval(body, Offset(at.x + 9 * s, at.y - 13 * s), Size(9 * s, 6 * s))
        drawCircle(Color.Black, .8f * s, Offset(at.x + 14 * s, at.y - 11 * s))
        for (side in listOf(-1f, 1f)) {
            val ax = at.x + 11 * s + side * 1.5f * s
            drawLine(dark, Offset(ax, at.y - 12 * s), Offset(ax - 3 * s, at.y - 20 * s), 1.1f * s, StrokeCap.Round)
            drawLine(dark, Offset(ax - 1.5f * s, at.y - 16 * s), Offset(ax + 2 * s, at.y - 19 * s), 1f * s, StrokeCap.Round)
        }
        if (leader) {
            drawCircle(Color(0x66FF2D2D), 4.5f * s, Offset(at.x + 18 * s, at.y - 10 * s))
            drawCircle(Color(0xFFFF2D2D), 1.8f * s, Offset(at.x + 18 * s, at.y - 10 * s))
        } else drawCircle(Color(0xFF2A1A0A), 1.1f * s, Offset(at.x + 18 * s, at.y - 10 * s))
    }

    fun DrawScope.dragonBoat(p: Float, seed: Long, t: Double) {
        val w = size.width; val h = size.height
        val k = (p / 11f).coerceIn(0f, 1f)
        val s = u * (if (w > 900 * u) 1.3f else 1f)
        val x = -130 * s + k * (w + 260 * s)
        val y = h * .8f + sin(t * 2.2).toFloat() * 2 * s
        // Water.
        val water = Path().apply {
            moveTo(0f, y + 8 * s)
            var xx = 0f
            while (xx <= w) { lineTo(xx, y + 8 * s + sin(xx / (18 * s) + t * 3).toFloat() * 2.5f * s); xx += 8 * s }
            lineTo(w, y + 30 * s); lineTo(0f, y + 30 * s); close()
        }
        drawPath(water, Brush.verticalGradient(listOf(Color(0x664FA3D9), Color(0x004FA3D9)), y + 4 * s, y + 30 * s))
        translate(x, y) {
            val hull = Path().apply {
                moveTo(-110 * s, -6 * s); lineTo(92 * s, -6 * s); quadraticTo(104 * s, -6 * s, 100 * s, 2 * s)
                quadraticTo(70 * s, 10 * s, -96 * s, 10 * s); quadraticTo(-112 * s, 6 * s, -110 * s, -6 * s); close()
            }
            drawPath(hull, Brush.verticalGradient(listOf(Color(0xFFE53935), Color(0xFF9E1B1B)), -6 * s, 10 * s))
            for (j in 0 until 14) drawArc(Color(0xFFFFC94A), 0f, 180f, false, Offset(-100 * s + j * 13.5f * s, -5 * s), Size(9 * s, 7 * s), style = Stroke(1.1f * s))
            // Rowers.
            for (r in 0 until 8) {
                val rx = -70 * s + r * 19 * s; val stroke = sin(t * 6 + r * .25).toFloat()
                drawLine(Color(0xFF8D6E63), Offset(rx, -12 * s), Offset(rx - 10 * s + stroke * 7 * s, 9 * s), 1.4f * s, StrokeCap.Round)
                drawRoundRect(Color(if (r % 2 == 0) 0xFFFFC94A else 0xFFFFE08A), Offset(rx - 3 * s, -16 * s), Size(6 * s, 10 * s), androidx.compose.ui.geometry.CornerRadius(2 * s))
                drawCircle(Color(0xFF6D4C41), 3 * s, Offset(rx, -19 * s))
            }
            // The drum and the drummer.
            drawOval(Color(0xFFB71C1C), Offset(70 * s, -16 * s), Size(12 * s, 10 * s))
            drawOval(Color(0xFFFFE0B2), Offset(70 * s, -17 * s), Size(12 * s, 3 * s))
            drawCircle(Color(0xFF6D4C41), 3 * s, Offset(84 * s, -21 * s))
            val beat = abs(sin(t * 6)).toFloat()
            drawLine(Color(0xFF5D4037), Offset(84 * s, -16 * s), Offset(77 * s, -16 * s - beat * 6 * s), 1.2f * s, StrokeCap.Round)
            // Dragon head and tail.
            val head = Path().apply {
                moveTo(94 * s, -4 * s); quadraticTo(98 * s, -22 * s, 112 * s, -26 * s); quadraticTo(126 * s, -26 * s, 128 * s, -18 * s)
                lineTo(118 * s, -15 * s); lineTo(126 * s, -10 * s); quadraticTo(112 * s, -8 * s, 104 * s, -2 * s); close()
            }
            drawPath(head, Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF66BB6A)), Offset(94 * s, 0f), Offset(128 * s, -26 * s)))
            drawCircle(Color.White, 2.6f * s, Offset(113 * s, -20 * s)); drawCircle(Color.Black, 1.3f * s, Offset(114 * s, -20 * s))
            drawLine(Color(0xFFFFC94A), Offset(108 * s, -25 * s), Offset(100 * s, -34 * s), 2f * s, StrokeCap.Round)
            drawLine(Color(0xFFFFC94A), Offset(104 * s, -22 * s), Offset(94 * s, -28 * s), 1.6f * s, StrokeCap.Round)
            val whisker = sin(t * 4).toFloat() * 3 * s
            drawLine(Color(0xFFFFC94A), Offset(124 * s, -14 * s), Offset(136 * s, -10 * s + whisker), 1f * s, StrokeCap.Round)
            val tail = Path().apply { moveTo(-108 * s, -4 * s); quadraticTo(-124 * s, -14 * s, -118 * s, -28 * s); quadraticTo(-112 * s, -18 * s, -100 * s, -6 * s); close() }
            drawPath(tail, Color(0xFF43A047))
            // Splashes.
            for (j in 0 until 6) {
                val sx = -60 * s + j * 30 * s + rnd(j, (t * 4).toInt()) * 10 * s
                drawCircle(Color.White.copy(alpha = .6f), 1.6f * s, Offset(sx, 10 * s + rnd(j, (t * 5).toInt()) * 4 * s))
            }
        }
    }

    fun DrawScope.ghost(p: Float, seed: Long, t: Double) {
        val w = size.width; val h = size.height
        val k = (p / 8f).coerceIn(0f, 1f)
        val fade = minOf(1f, k * 5f, (1f - k) * 5f)
        val ltr = seed % 2L == 0L
        val x = if (ltr) w * (-.1f + 1.2f * k) else w * (1.1f - 1.2f * k)
        val y = h * (.3f + .25f * rnd(seed.toInt(), 2)) + sin(t * 2.4).toFloat() * 14 * u
        val s = u * 1.1f
        withTransform({ translate(x, y); if (!ltr) scale(-1f, 1f, Offset.Zero) }) {
            val body = Path().apply {
                moveTo(-18 * s, 18 * s); lineTo(-18 * s, -4 * s); cubicTo(-18 * s, -30 * s, 18 * s, -30 * s, 18 * s, -4 * s); lineTo(18 * s, 18 * s)
                val wv = (t * 6).toFloat()
                for (j in 0 until 4) { val x0 = 18 * s - j * 9 * s; quadraticTo(x0 - 4.5f * s, (12 + 6 * sin(wv + j)) * s, x0 - 9 * s, 18 * s) }
                close()
            }
            drawPath(body, Color(0x22000000), alpha = fade)
            drawPath(body, Brush.verticalGradient(listOf(Color.White, Color(0xFFE6ECF5)), -28 * s, 18 * s), alpha = .92f * fade)
            drawOval(Color(0xFF1B1E28), Offset(-9 * s, -10 * s), Size(5 * s, 7 * s), alpha = fade)
            drawOval(Color(0xFF1B1E28), Offset(3 * s, -10 * s), Size(5 * s, 7 * s), alpha = fade)
            drawOval(Color(0xFF1B1E28), Offset(-3 * s, 0f), Size(6 * s, 6 * s * (.6f + .4f * abs(sin(t * 3).toFloat()))), alpha = fade)
            drawCircle(Color(0x55FF8FB8), 3 * s, Offset(-11 * s, 1 * s), alpha = fade); drawCircle(Color(0x55FF8FB8), 3 * s, Offset(10 * s, 1 * s), alpha = fade)
        }
    }

    fun DrawScope.halloween(t: Double) {
        val w = size.width; val h = size.height
        // Bats.
        for (i in 0 until n(5)) {
            val period = 13.0 + rnd(i, 1) * 8
            val k = (wrap(t + rnd(i, 2) * period, period) / period).toFloat()
            val x = w * (-.1f + 1.2f * k); val y = h * (.08f + .2f * rnd(i, 3)) + sin(k * 12 + i).toFloat() * 16 * u
            val flap = sin(t * 14 + i).toFloat()
            val s = u * (.8f + .5f * rnd(i, 4))
            translate(x, y) {
                for (side in listOf(-1f, 1f)) {
                    val wing = Path().apply {
                        moveTo(0f, 0f); quadraticTo(side * 10 * s, -10 * s * flap - 2 * s, side * 22 * s, -6 * s * flap)
                        quadraticTo(side * 17 * s, 1 * s, side * 14 * s, 4 * s); quadraticTo(side * 9 * s, 1 * s, side * 6 * s, 5 * s); close()
                    }
                    drawPath(wing, Color(0xFF2B2140))
                }
                drawOval(Color(0xFF2B2140), Offset(-4 * s, -4 * s), Size(8 * s, 10 * s))
                drawPath(Path().apply { moveTo(-3.5f * s, -3 * s); lineTo(-2.5f * s, -8 * s); lineTo(-1 * s, -3.5f * s); moveTo(3.5f * s, -3 * s); lineTo(2.5f * s, -8 * s); lineTo(1 * s, -3.5f * s) }, Color(0xFF2B2140))
                drawCircle(Color(0xFFFFD54F), .9f * s, Offset(-1.6f * s, -1 * s)); drawCircle(Color(0xFFFFD54F), .9f * s, Offset(1.6f * s, -1 * s))
            }
        }
        // Jack-o'-lanterns at the bottom corners.
        for ((k, x) in listOf(w * .1f, w * .88f).withIndex()) pumpkin(Offset(x, h * .87f), u * (if (k == 0) 1.1f else .85f), t + k)
    }

    private fun DrawScope.pumpkin(c: Offset, s: Float, t: Double) {
        val glow = .75f + .25f * sin(t * 11).toFloat() * sin(t * 3.7).toFloat()
        drawCircle(Brush.radialGradient(listOf(Color(0x66FF9800), Color(0x00FF9800)), c, 60 * s), 60 * s, c, alpha = glow)
        for ((dx, wd) in listOf(-14f to 22f, 14f to 22f, 0f to 26f)) {
            drawOval(Brush.radialGradient(listOf(Color(0xFFFFA726), Color(0xFFE65100)), Offset(c.x + dx * s - 3 * s, c.y - 6 * s), 26 * s),
                Offset(c.x + dx * s - wd / 2 * s, c.y - 18 * s), Size(wd * s, 34 * s))
        }
        drawLine(Color(0xFF4E7D2E), Offset(c.x, c.y - 17 * s), Offset(c.x + 4 * s, c.y - 26 * s), 4 * s, StrokeCap.Round)
        val lit = Color(0xFFFFE082).copy(alpha = glow)
        drawPath(Path().apply { moveTo(c.x - 13 * s, c.y - 2 * s); lineTo(c.x - 8 * s, c.y - 10 * s); lineTo(c.x - 3 * s, c.y - 2 * s); close() }, lit)
        drawPath(Path().apply { moveTo(c.x + 3 * s, c.y - 2 * s); lineTo(c.x + 8 * s, c.y - 10 * s); lineTo(c.x + 13 * s, c.y - 2 * s); close() }, lit)
        drawPath(Path().apply {
            moveTo(c.x - 13 * s, c.y + 4 * s); lineTo(c.x - 8 * s, c.y + 8 * s); lineTo(c.x - 4 * s, c.y + 4 * s); lineTo(c.x, c.y + 9 * s)
            lineTo(c.x + 4 * s, c.y + 4 * s); lineTo(c.x + 8 * s, c.y + 8 * s); lineTo(c.x + 13 * s, c.y + 4 * s)
            quadraticTo(c.x, c.y + 16 * s, c.x - 13 * s, c.y + 4 * s); close()
        }, lit)
    }

    fun DrawScope.paperPlanes(t: Double) {
        val w = size.width; val h = size.height
        for (i in 0 until n(4)) {
            val period = 9.0 + rnd(i, 1) * 5
            val k = (wrap(t + rnd(i, 2) * period, period) / period).toFloat()
            val base = h * (.2f + .5f * rnd(i, 3))
            val x = w * (-.15f + 1.3f * k); val y = base + sin(k * 7 + i).toFloat() * 30 * u
            val angle = cos(k * 7 + i).toFloat() * 14f
            val s = u * (1f + .4f * rnd(i, 4))
            rotate(angle, Offset(x, y)) {
                drawPath(Path().apply { moveTo(x + 18 * s, y); lineTo(x - 14 * s, y - 9 * s); lineTo(x - 8 * s, y + 1 * s); close() }, Color(0x22000000))
                drawPath(Path().apply { moveTo(x + 18 * s, y - 2 * s); lineTo(x - 14 * s, y - 11 * s); lineTo(x - 6 * s, y - 1 * s); close() }, Color(0xFFFFFFFF))
                drawPath(Path().apply { moveTo(x + 18 * s, y - 2 * s); lineTo(x - 6 * s, y - 1 * s); lineTo(x - 10 * s, y + 6 * s); close() }, Color(0xFFDDE5F0))
                drawLine(Color(0xFF9AA8BA), Offset(x + 18 * s, y - 2 * s), Offset(x - 6 * s, y - 1 * s), .8f * s)
            }
            for (j in 1..5) drawCircle(Color(0xFFFFE9A8).copy(alpha = .5f - j * .08f), 1.5f * u, Offset(x - j * 9 * s, base + sin((k - j * .01f) * 7 + i).toFloat() * 30 * u))
        }
        sparkles(t, Color(0xFFFFF3C4), 10)
    }

    fun DrawScope.waterLanterns(t: Double) {
        val w = size.width; val h = size.height
        for (i in 0 until n(6)) {
            val x = (wrap(rnd(i, 1) * w + t * (6 + 5 * rnd(i, 2)) * u, (w + 80 * u).toDouble()).toFloat()) - 40 * u
            val y = h * (.84f + .06f * rnd(i, 3)) + sin(t * 1.5 + i).toFloat() * 2 * u
            val s = u * (.8f + .4f * rnd(i, 4))
            val flicker = .8f + .2f * sin(t * 9 + i).toFloat()
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFC46B), Color(0x00FFC46B)), Offset(x, y - 6 * s), 34 * s), 34 * s, Offset(x, y - 6 * s), alpha = flicker)
            drawOval(Color(0x335C8DB8), Offset(x - 18 * s, y + 4 * s), Size(36 * s, 6 * s))
            for (j in -2..2) rotate(j * 24f, Offset(x, y + 4 * s)) {
                drawOval(Color(if (abs(j) == 1) 0xFFFFA6C1 else 0xFFFFC7D8), Offset(x - 5 * s, y - 14 * s), Size(10 * s, 18 * s), alpha = .95f)
            }
            drawCircle(Color(0xFFFFF1B8).copy(alpha = flicker), 2.4f * s, Offset(x, y - 6 * s))
        }
    }

    // ---- the national flag of the Republic of China (Taiwan) ----

    private fun flagBitmap(width: Int): ImageBitmap {
        val height = width * 2 / 3
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFE0000.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.color = 0xFF000095.toInt(); c.drawRect(0f, 0f, width / 2f, height / 2f, p)
        // Canton height = 80 units: rays to 30, blue ring 17, white disc 15; twelve rays, one straight up.
        val unit = height / 2f / 80f; val cx = width / 4f; val cy = height / 4f
        val star = android.graphics.Path()
        for (i in 0 until 24) {
            val r = if (i % 2 == 0) 30f else 16.5f
            val a = -PI / 2 + i * PI / 12
            val x = cx + (r * unit * cos(a)).toFloat(); val y = cy + (r * unit * sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        p.color = 0xFFFFFFFF.toInt(); c.drawPath(star, p)
        p.color = 0xFF000095.toInt(); c.drawCircle(cx, cy, 17f * unit, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx, cy, 15f * unit, p)
        return bmp.asImageBitmap()
    }

    fun DrawScope.flag(t: Double, topInset: Float) {
        val fw = (60 * u).toInt()
        val img = flag?.takeIf { it.width == fw } ?: flagBitmap(fw).also { flag = it }
        val fh = img.height
        val x0 = size.width - fw - 22 * u; val y0 = topInset + 64 * u
        drawLine(Color(0xFFB0B6C0), Offset(x0 - 2 * u, y0 - 6 * u), Offset(x0 - 2 * u, y0 + fh + 30 * u), 2.2f * u, StrokeCap.Round)
        drawCircle(Color(0xFFFFD36B), 3 * u, Offset(x0 - 2 * u, y0 - 7 * u))
        val slices = 20; val sw = fw / slices
        for (i in 0 until slices) {
            val k = i / slices.toFloat()
            val dy = sin(t * 4 - k * 5).toFloat() * 4 * u * k
            val shade = (cos(t * 4 - k * 5).toFloat() * .12f * k)
            drawImage(img, IntOffset(i * sw, 0), IntSize(if (i == slices - 1) fw - i * sw else sw, fh), IntOffset((x0 + i * sw).toInt(), (y0 + dy).toInt()), IntSize(if (i == slices - 1) fw - i * sw else sw, fh))
            if (shade > 0) drawRect(Color.White.copy(alpha = shade), Offset(x0 + i * sw, y0 + dy), Size(sw.toFloat(), fh.toFloat()))
            else drawRect(Color.Black.copy(alpha = -shade), Offset(x0 + i * sw, y0 + dy), Size(sw.toFloat(), fh.toFloat()))
        }
    }

    // ---- fireworks ----

    fun DrawScope.fireworks(shells: List<Fireworks.Shell>, s: Float) {
        val w = size.width; val h = size.height
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (sh in shells) {
                if (s < sh.launch || s > sh.end) continue
                if (s < sh.burst) {
                    val y = Fireworks.rocketY(sh, s) * h; val x = sh.x * w + sin(s * 20 + sh.seed).toFloat() * .6f * u
                    dot.color = 0xFFFFE6B0.toInt(); dot.strokeWidth = 2.6f * u
                    nc.drawPoint(x, y, dot)
                    for (j in 1..6) {
                        val yy = Fireworks.rocketY(sh, s - j * .03f) * h
                        dot.alpha = 200 - j * 30; dot.strokeWidth = (2.2f - j * .25f) * u
                        nc.drawPoint(x + (rnd(j, sh.seed + (s * 30).toInt()) - .5f) * 2 * u, yy + 2 * u, dot)
                    }
                    continue
                }
                val a = Fireworks.alpha(sh, s)
                val tau = s - sh.burst
                if (tau < .25f && !light) {
                    val flash = (1f - tau / .25f) * .22f
                    nc.drawCircle(sh.x * w, sh.apex * h, minOf(w, h) * .45f, Paint().apply {
                        shader = android.graphics.RadialGradient(sh.x * w, sh.apex * h, minOf(w, h) * .45f,
                            (sh.colour and 0x00FFFFFF) or ((flash * 255).toInt() shl 24), sh.colour and 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
                    })
                }
                val layers = if (sh.type == Fireworks.Type.DOUBLE) 2 else 1
                for (layer in 0 until layers) {
                    val colour = if (layer == 0) sh.colour else sh.second
                    val count = Fireworks.stars(sh, s, w, h, points, 0f, layer)
                    if (!light && (sh.type == Fireworks.Type.WILLOW || sh.type == Fireworks.Type.CHRYSANTHEMUM || sh.type == Fireworks.Type.PALM)) {
                        for (lag in listOf(.06f, .12f, .2f)) {
                            val tc = Fireworks.stars(sh, s, w, h, trail, lag, layer)
                            dot.color = colour; dot.alpha = (a * (110 - lag * 400)).toInt().coerceIn(0, 255); dot.strokeWidth = (2.2f - lag * 5) * u
                            nc.drawPoints(trail, 0, tc * 2, dot)
                        }
                    }
                    // Stars cool from white to their colour.
                    val hot = (1f - tau / .35f).coerceIn(0f, 1f)
                    dot.color = blend(colour, 0xFFFFFFFF.toInt(), hot); dot.alpha = (a * 255).toInt().coerceIn(0, 255)
                    dot.strokeWidth = (if (sh.type == Fireworks.Type.PALM) 4.4f else 3.1f) * u * (1f - tau / sh.life * .4f)
                    nc.drawPoints(points, 0, count * 2, dot)
                }
            }
        }
    }

    private fun blend(a: Int, b: Int, k: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 255) * (1 - k) + ((b shr shift) and 255) * k).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** A string of firecrackers popping up the left edge during the lunar new year show. */
    fun DrawScope.firecrackerSparks(t: Double) {
        val h = size.height
        for (i in 0 until n(24)) {
            val life = .5
            val k = (wrap(t + rnd(i, 1) * life, life) / life).toFloat()
            val cyc = floor((t + rnd(i, 1) * life) / life).toInt()
            val x = 14 * u + rnd(i + cyc, 2) * 30 * u; val y = h * (.55f + .35f * rnd(i + cyc, 3))
            drawCircle(Color(if (i % 2 == 0) 0xFFFFD54F else 0xFFFF5252), (1f - k) * 4 * u + 1 * u, Offset(x + k * 12 * u * (rnd(i, 4) - .5f), y - k * 10 * u), alpha = 1f - k)
        }
    }
}
