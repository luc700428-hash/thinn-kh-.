package io.atmos.weather.alarm

/** Permanent visual order: AM row above PM row. Swipe up selects PM, down selects AM.
 * Completing a gesture never blindly toggles the current value.
 */
object V25AlarmMeridiem {
    const val AM = 0
    const val PM = 1
    fun fromDrag(current: Int, totalDragPx: Float, minimumDragPx: Float): Int {
        require(current == AM || current == PM)
        if (!totalDragPx.isFinite() || !minimumDragPx.isFinite() || minimumDragPx < 0f) return current
        if (kotlin.math.abs(totalDragPx) < minimumDragPx) return current
        return if (totalDragPx < 0f) PM else AM
    }
    fun hour24(hour12: Int, meridiem: Int): Int {
        require(hour12 in 0..11 && meridiem in AM..PM)
        return hour12 + meridiem * 12
    }
}
