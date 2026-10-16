package io.atmos.weather.alarm

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Shared, Android-free alarm recurrence logic; independently tested by Kotlin/JVM. */
data class WeatherAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val title: String,
    val days: Set<Int> = emptySet(), // ISO days Monday=1 .. Sunday=7, empty = once
    val enabled: Boolean = true,
    val weatherVoice: Boolean = true,
    val snooze: Boolean = true
)

object WeatherAlarmTime {
    fun next(a: WeatherAlarm, after: ZonedDateTime): Instant? {
        if (!a.enabled || a.hour !in 0..23 || a.minute !in 0..59) return null
        for (daysAway in 0..8) {
            val date=after.toLocalDate().plusDays(daysAway.toLong())
            if (a.days.isNotEmpty() && date.dayOfWeek.value !in a.days) continue
            val candidate=LocalDateTime.of(date,java.time.LocalTime.of(a.hour,a.minute))
                .atZone(after.zone)
            if (candidate.toInstant().isAfter(after.toInstant().plusSeconds(2))) return candidate.toInstant()
            if (a.days.isEmpty() && daysAway == 1) break
        }
        return null
    }
}

