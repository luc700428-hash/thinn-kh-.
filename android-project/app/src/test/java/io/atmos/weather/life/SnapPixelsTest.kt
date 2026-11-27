package io.atmos.weather.life

import android.app.Application
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.random.Random

/** 超級夜景 stacking and the editor's pixel tools. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnapPixelsTest {
    /** A dark scene with a bright square, shifted by (dx, dy), with sensor noise. */
    private fun frame(dx: Int, dy: Int, seed: Int): Bitmap {
        val w = 240; val h = 180; val rnd = Random(seed)
        val px = IntArray(w * h) { i ->
            val x = i % w - dx; val y = i / w - dy
            val base = if (x in 90..150 && y in 60..120) 150 else 25
            val v = (base + rnd.nextInt(-24, 25)).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun noise(b: Bitmap): Double {
        // Spread of the flat dark area (top-left corner) around its mean.
        val v = (10 until 50).flatMap { y -> (10 until 50).map { x -> (b.getPixel(x, y) shr 8) and 255 } }
        // Relative to its brightness, since 超級夜景 also lifts the picture.
        val m = v.average(); return kotlin.math.sqrt(v.sumOf { (it - m) * (it - m) } / v.size) / m
    }

    @Test fun stackingAlignsShiftedFramesAndRemovesNoise() {
        val s = SnapPixels.Stacker()
        val shifts = listOf(0 to 0, 2 to 1, -3 to 2, 1 to -2, 4 to 3, -2 to -1)
        shifts.forEachIndexed { i, (dx, dy) -> s.add(frame(dx, dy, i)) }
        assertEquals(6, s.used)
        val out = s.result()!!
        val single = frame(0, 0, 99)
        assertTrue("noise is lower after stacking", noise(out) < noise(single) * .6)
        // The square's edge stays sharp (aligned): just inside is bright, just outside is dark.
        val inside = (out.getPixel(95, 90) shr 8) and 255; val outside = (out.getPixel(84, 90) shr 8) and 255
        assertTrue("edge kept ($inside vs $outside)", inside - outside > 60)
    }

    @Test fun toneCurveKeepsEndsAndMovesTheMiddle() {
        val flat = SnapPixels.toneLut(0f, 0f, 0f, 0f)
        assertTrue((0..255).all { abs(flat[it] - it) <= 1 })
        val lifted = SnapPixels.toneLut(0f, 0f, 1f, 0f)
        assertTrue(lifted[60] > 75); assertEquals(255, lifted[255]); assertEquals(0, lifted[0])
        val faded = SnapPixels.toneLut(0f, 0f, 0f, 1f)
        assertTrue(faded[0] > 20)
    }

    @Test fun editorRenderAppliesEveryNewTool() {
        val src = frame(0, 0, 1)
        val facts = LifeCards.SnapFacts("臺北市", java.time.Instant.EPOCH, 20, "晴", 1012, null, 60)
        val e = SnapRender.Edit(exposure = .4f, highlights = -.3f, shadows = .5f, tint = .2f, vibrance = .3f, sharpen = .5f, fade = .2f, grain = .3f,
            strength = .5f, filter = SnapRender.Filter.JAPAN, turns = 1, mirror = true, template = SnapRender.Template.NONE)
        val out = SnapRender.render(null, src, e, facts, 400)
        assertEquals(180, out.width); assertEquals(240, out.height) // turned a quarter
    }
}
