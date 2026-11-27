package io.atmos.weather.life

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pixel work the colour matrix cannot do: tone curves (exposure, highlights, shadows, fade),
 * sharpening, film grain, and the app's own 超級夜景 — several handheld frames aligned and
 * averaged, which removes most of the noise of a dark scene, then lifted like a night photo.
 */
object SnapPixels {

    /** One curve applied to R, G and B: exposure and fade (−1…1, fade 0…1), shadows and highlights. */
    fun toneLut(exposure: Float, highlights: Float, shadows: Float, fade: Float): IntArray = IntArray(256) { i ->
        var x = i / 255f
        if (exposure != 0f) x *= 2f.pow(exposure)
        x = x.coerceIn(0f, 1f)
        x += shadows * .78f * x * (1 - x) * (1 - x)
        x += highlights * .78f * x * x * (1 - x)
        if (fade > 0f) x = fade * .14f + x * (1 - fade * .2f)
        (x.coerceIn(0f, 1f) * 255f).roundToInt()
    }

    fun needsPixels(e: SnapRender.Edit) = e.exposure != 0f || e.highlights != 0f || e.shadows != 0f || e.fade != 0f || e.sharpen != 0f || e.grain != 0f

    /** Applies the tone curve, sharpening and grain of [e] to [b] in place (a mutable bitmap). */
    fun apply(b: Bitmap, e: SnapRender.Edit) {
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        if (e.exposure != 0f || e.highlights != 0f || e.shadows != 0f || e.fade != 0f) {
            val lut = toneLut(e.exposure, e.highlights, e.shadows, e.fade)
            for (i in px.indices) {
                val c = px[i]
                px[i] = (c and -0x1000000) or (lut[(c shr 16) and 255] shl 16) or (lut[(c shr 8) and 255] shl 8) or lut[c and 255]
            }
        }
        if (e.sharpen != 0f) sharpen(px, w, h, e.sharpen)
        if (e.grain > 0f) grain(px, e.grain)
        b.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Unsharp mask against a soft copy (made by shrinking and enlarging); negative softens. */
    fun sharpen(px: IntArray, w: Int, h: Int, amount: Float) {
        val src = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val small = Bitmap.createScaledBitmap(src, max(1, w / 3), max(1, h / 3), true)
        val soft = Bitmap.createScaledBitmap(small, w, h, true)
        val blur = IntArray(w * h); soft.getPixels(blur, 0, w, 0, 0, w, h)
        val k = amount * 1.3f
        for (i in px.indices) {
            val c = px[i]; val s = blur[i]
            fun ch(shift: Int): Int { val a = (c shr shift) and 255; val b = (s shr shift) and 255; return (a + k * (a - b)).roundToInt().coerceIn(0, 255) }
            px[i] = (c and -0x1000000) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        src.recycle(); small.recycle(); soft.recycle()
    }

    /** Fine monochrome film grain (the same pattern every time, so the preview matches the save). */
    fun grain(px: IntArray, amount: Float) {
        var seed = 0x2545F491
        val k = amount * 22f
        for (i in px.indices) {
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
            val n = ((seed and 0xFFFF) / 65535f - .5f) * 2f * k
            val c = px[i]
            val r = (((c shr 16) and 255) + n).roundToInt().coerceIn(0, 255)
            val g = (((c shr 8) and 255) + n).roundToInt().coerceIn(0, 255)
            val b = ((c and 255) + n).roundToInt().coerceIn(0, 255)
            px[i] = (c and -0x1000000) or (r shl 16) or (g shl 8) or b
        }
    }

    /** Rotates (by 90° steps) and mirrors a photo. */
    fun orient(src: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
        if (degrees % 360 == 0 && !mirror) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()); if (mirror) postScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    // ------------------------------------------------------------------ 超級夜景

    /**
     * Adds frames one at a time (so only two full pictures are ever in memory), aligning each to the
     * first by the shift that best matches a small grey copy, then a half-size copy. Frames that do
     * not line up (a big shake, something moving across) are left out.
     */
    class Stacker {
        private var ref: Bitmap? = null
        private var w = 0; private var h = 0
        private lateinit var r: ShortArray; private lateinit var g: ShortArray; private lateinit var b: ShortArray
        private lateinit var refTiny: FloatArray; private lateinit var refHalf: FloatArray
        private var tw = 0; private var th = 0; private var hw = 0; private var hh = 0
        private var firstError = -1f
        var used = 0; private set

        fun add(frame: Bitmap) {
            val f = if (ref != null && (frame.width != w || frame.height != h)) Bitmap.createScaledBitmap(frame, w, h, true) else frame
            val px = IntArray(f.width * f.height); f.getPixels(px, 0, f.width, 0, 0, f.width, f.height)
            if (ref == null) {
                ref = f; w = f.width; h = f.height
                r = ShortArray(w * h); g = ShortArray(w * h); b = ShortArray(w * h)
                tw = max(8, w / 8); th = max(8, h / 8); hw = max(8, w / 2); hh = max(8, h / 2)
                refTiny = grey(f, tw, th); refHalf = grey(f, hw, hh)
                accumulate(px, 0, 0); used = 1; return
            }
            val tiny = grey(f, tw, th)
            val (cx, cy, _) = bestShift(refTiny, tiny, tw, th, 0, 0, 6, 1)
            val half = grey(f, hw, hh)
            val (dx, dy, err) = bestShift(refHalf, half, hw, hh, cx * 4, cy * 4, 4, 3)
            if (firstError < 0) firstError = err
            if (err > max(firstError * 2.2f, 9f)) return // this frame does not match: skip it
            accumulate(px, dx * 2, dy * 2); used++
        }

        private fun accumulate(px: IntArray, dx: Int, dy: Int) {
            val refPx = if (dx != 0 || dy != 0) IntArray(w * h).also { ref!!.getPixels(it, 0, w, 0, 0, w, h) } else null
            for (y in 0 until h) {
                val sy = y + dy
                for (x in 0 until w) {
                    val sx = x + dx
                    val c = if (sy in 0 until h && sx in 0 until w) px[sy * w + sx] else refPx?.get(y * w + x) ?: px[y * w + x]
                    val i = y * w + x
                    r[i] = (r[i] + ((c shr 16) and 255)).toShort(); g[i] = (g[i] + ((c shr 8) and 255)).toShort(); b[i] = (b[i] + (c and 255)).toShort()
                }
            }
        }

        /** The averaged picture, brightened like a night shot with highlights kept, lightly sharpened. */
        fun result(): Bitmap? {
            if (used == 0) return null
            val n = used.toFloat()
            var sum = 0.0
            for (i in r.indices step 7) sum += (.3f * r[i] + .59f * g[i] + .11f * b[i]) / n / 255f
            val mean = (sum / ((r.size + 6) / 7)).toFloat().coerceAtLeast(.01f)
            val gain = (.40f / mean).coerceIn(1f, 2.6f)
            val lut = IntArray(256 * used + 1) { s ->
                val x = s / n / 255f
                val y = x * gain / (1f + x * (gain - 1f)) // lifts the dark, keeps white white
                (y.pow(.95f) * 255f).roundToInt().coerceIn(0, 255)
            }
            val out = IntArray(w * h)
            for (i in out.indices) out[i] = -0x1000000 or (lut[r[i].toInt() and 0xFFFF] shl 16) or (lut[g[i].toInt() and 0xFFFF] shl 8) or lut[b[i].toInt() and 0xFFFF]
            sharpen(out, w, h, .35f)
            return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        }

        private fun grey(b: Bitmap, gw: Int, gh: Int): FloatArray {
            val s = Bitmap.createScaledBitmap(b, gw, gh, true)
            val px = IntArray(gw * gh); s.getPixels(px, 0, gw, 0, 0, gw, gh)
            if (s !== b) s.recycle()
            return FloatArray(px.size) { val c = px[it]; .3f * ((c shr 16) and 255) + .59f * ((c shr 8) and 255) + .11f * (c and 255) }
        }

        /** The shift (around [sx], [sy], within ±[range]) with the smallest mean difference over the middle. */
        private fun bestShift(a: FloatArray, f: FloatArray, gw: Int, gh: Int, sx: Int, sy: Int, range: Int, step: Int): Triple<Int, Int, Float> {
            var best = Float.MAX_VALUE; var bx = sx; var by = sy
            val x0 = gw / 5; val x1 = gw - gw / 5; val y0 = gh / 5; val y1 = gh - gh / 5
            for (dy in sy - range..sy + range) for (dx in sx - range..sx + range) {
                var s = 0f; var n = 0
                var y = y0
                while (y < y1) {
                    val fy = y + dy
                    if (fy in 0 until gh) {
                        var x = x0
                        while (x < x1) { val fx = x + dx; if (fx in 0 until gw) { s += abs(a[y * gw + x] - f[fy * gw + fx]); n++ }; x += step }
                    }
                    y += step
                }
                if (n > 0 && s / n < best) { best = s / n; bx = dx; by = dy }
            }
            return Triple(bx, by, best)
        }
    }

    /** How sharp a picture is (variance of a small Laplacian), to keep the steadiest frame. */
    fun sharpness(b: Bitmap): Float {
        val s = Bitmap.createScaledBitmap(b, min(320, b.width), max(1, (b.height * min(320, b.width) / b.width.toFloat()).roundToInt()), true)
        val w = s.width; val h = s.height; val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        fun l(i: Int) = (px[i] shr 8) and 255
        var sum = 0.0; var sq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val v = (4 * l(i) - l(i - 1) - l(i + 1) - l(i - w) - l(i + w)).toDouble()
            sum += v; sq += v * v; n++
        }
        return if (n == 0) 0f else (sq / n - (sum / n) * (sum / n)).toFloat()
    }

}
