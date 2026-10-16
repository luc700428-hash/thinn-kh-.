package io.atmos.weather.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure schedule computation. 1 << (Monday=0 ... Sunday=6), 1..5 user schedules. */
data class WeatherBriefingSlot(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val weekdays: Int,
    val enabled: Boolean = true
) {
    init { require(id in 1..5 && hour in 0..23 && minute in 0..59 && weekdays in 1..127) }
    fun matches(day: DayOfWeek) = (weekdays and (1 shl (day.value - 1))) != 0
}

data class BriefingOccurrence(val slot: WeatherBriefingSlot, val time: ZonedDateTime)

object WeatherBriefingPlan {
    const val MAX_SLOTS=5
    fun upcoming(slots: List<WeatherBriefingSlot>, after: Instant, zone: ZoneId): BriefingOccurrence? {
        val start = after.atZone(zone)
        return slots.asSequence().filter { it.enabled }.flatMap { slot ->
            (0L..8L).asSequence().mapNotNull { days ->
                val date = start.toLocalDate().plusDays(days)
                if (!slot.matches(date.dayOfWeek)) null else {
                    // ZonedDateTime.of handles DST overlap/gap using Java Time zone rules.
                    val whenAt = ZonedDateTime.of(date, java.time.LocalTime.of(slot.hour, slot.minute), zone)
                    if (whenAt.toInstant().isAfter(after)) BriefingOccurrence(slot, whenAt) else null
                }
            }
        }.minByOrNull { it.time.toInstant() }
    }
    /** Precisely delimits this message at the next enabled scheduled notification, even across weeks. */
    fun nextBoundary(slots: List<WeatherBriefingSlot>, current: Instant, zone: ZoneId): Instant? =
        upcoming(slots,current.plusMillis(1000),zone)?.time?.toInstant()
}
