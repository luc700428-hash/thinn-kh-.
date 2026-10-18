package io.atmos.weather.notifications

/** Priority is for the app's own island decoration, NOT control of Xiaomi SystemUI. */
internal enum class IslandPriority(val rank: Int, val holdMillis: Long) {
    SEISMIC(0, 75_000L),       // P0 genuine validated EEW
    WEATHER_ALERT(1, 65_000L),// P1 official severe weather
    TIME_CRITICAL(2, 45_000L),// P2 alarm, timer, verified minute rain
    REMINDER(3, 25_000L),     // P3 scheduled private task
    AMBIENT(4, 15_000L)       // P4 time/weather/AQI, only when legitimately refreshed
}

internal data class ActiveIsland(val key: String, val priority: IslandPriority, val expiresAt: Long)

/** Short-lived in-process arbitration. All notifications still reach Android when not on island. */
internal object IslandPriorityPolicy {
    fun shouldPresent(active: ActiveIsland?, key: String, priority: IslandPriority, now: Long): Boolean =
        active == null || now >= active.expiresAt || active.key == key || priority.rank <= active.priority.rank

    fun next(key: String, priority: IslandPriority, now: Long): ActiveIsland =
        ActiveIsland(key, priority, now + priority.holdMillis)
}
