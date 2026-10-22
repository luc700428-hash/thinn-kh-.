package io.atmos.weather.task

/** Date checks run immediately before delivery; never send an early or superseded reminder. */
internal object TaskReminderPolicy {
    fun sameSchedule(currentDueMillis: Long, scheduledDueMillis: Long): Boolean =
        currentDueMillis > 0L && scheduledDueMillis == currentDueMillis

    fun isDue(currentDueMillis: Long, nowMillis: Long): Boolean =
        currentDueMillis > 0L && nowMillis >= currentDueMillis
}
