package io.atmos.weather.ui

/** Geometry-only bottom-sheet settling; full/half/closed anchors are screen-size independent.
 * This is UI state, not a gesture detector, so it can be tested without Android hardware.
 */
object GlassSheetMotionPolicy {
    enum class Anchor { FULL, HALF, CLOSED }
    private const val HALF_FRACTION = .46f
    fun offset(anchor: Anchor, sheetHeightPx: Float): Float {
        val h = sheetHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        return when (anchor) {
            Anchor.FULL -> 0f
            Anchor.HALF -> h * HALF_FRACTION
            Anchor.CLOSED -> h
        }
    }
    fun boundedDrag(valuePx: Float, sheetHeightPx: Float): Float {
        val h = sheetHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        return if (valuePx.isFinite()) valuePx.coerceIn(0f, h) else 0f
    }
    fun release(positionPx: Float, sheetHeightPx: Float): Anchor {
        val h = sheetHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val fraction = boundedDrag(positionPx, h) / h
        return when {
            fraction < .23f -> Anchor.FULL
            fraction < .72f -> Anchor.HALF
            else -> Anchor.CLOSED
        }
    }
    /** A cancelled drag must never dismiss the whole editor or discard unsaved inputs. */
    fun cancel(positionPx: Float, sheetHeightPx: Float): Anchor =
        if (boundedDrag(positionPx, sheetHeightPx) < offset(Anchor.HALF, sheetHeightPx) / 2f)
            Anchor.FULL else Anchor.HALF
}
