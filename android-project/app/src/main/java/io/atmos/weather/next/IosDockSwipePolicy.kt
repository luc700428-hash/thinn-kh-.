package io.atmos.weather.next

import kotlin.math.abs

/** A bounded, deterministic dock-scrub policy, independent of the Android UI toolkit. */
object IosDockSwipePolicy {
    fun indexAt(x: Float, width: Float, count: Int): Int? {
        if (!x.isFinite() || !width.isFinite() || width <= 0f || count <= 0) return null
        return ((x.coerceIn(0f, width) / width) * count).toInt().coerceIn(0, count - 1)
    }

    fun target(startX: Float, endX: Float, width: Float, count: Int, threshold: Float): Int? {
        if (!startX.isFinite() || !endX.isFinite() || !threshold.isFinite() || threshold < 0f) return null
        if (abs(endX - startX) < threshold) return null
        return indexAt(endX, width, count)
    }
}
