package io.atmos.weather.presentation.home

import kotlin.math.floor

/**
 * Deterministic per-drop rainfall field. No global sequence or repeated 18-second grid.
 * Each particle loops only when it falls below the viewport; individual periods differ.
 * Rain touch feedback and the renderer use exactly the same per-drop parameters.
 */
internal object RainParticles {
    private val originNanos = System.nanoTime()
    fun secondsNow(): Float = secondsAt(System.nanoTime())
    fun secondsAt(nanos: Long): Float = ((nanos - originNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()

    internal data class Drop(
        val x: Float, val start: Float, val speed: Float, val length: Float,
        val thickness: Float, val alpha: Float, val slant: Float, val depth: Float
    )
    private fun rand(seed: Int): Float {
        var x = seed * 0x45d9f3b
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return ((x ushr 8) and 0x00FFFFFF) / 16777216f
    }
    fun drop(i: Int, heavy: Boolean): Drop {
        val depth = rand(i * 13 + 3)
        val speed = (.42f + depth * .63f) * (if (heavy) 1.35f else 1f)
        return Drop(
            x=rand(i * 17 + 89),
            start=rand(i * 71 + 1) * 1.3f,
            speed=speed,
            length=(.008f + depth * .025f) * (if (heavy) 1.22f else 1f),
            thickness=.65f + depth * 1.2f,
            alpha=.075f + depth * .20f,
            slant=.009f + rand(i * 61 + 7) * .015f,
            depth=depth
        )
    }
    const val SPAN = 1.3f
    fun cycles(d: Drop, time: Float): Int = floor((d.start + time * d.speed) / SPAN).toInt()
    fun y(d: Drop, time: Float): Float = (d.start + time * d.speed) % SPAN - .18f
    fun x(d: Drop, time: Float): Float {
        val raw = d.x - .013f * time * (.3f + d.depth)
        return ((raw % 1f) + 1f) % 1f
    }
}
