package io.atmos.weather.alarm

/** Android-free alarm edit reconciliation. Never reschedule unrelated alarms. */
object WeatherAlarmMutation {
    /** The receiver may disable one-shot alarms while the editor holds older UI state. */
    fun preserveBackgroundChanges(
        stored: List<WeatherAlarm>,
        displayed: List<WeatherAlarm>,
        requested: List<WeatherAlarm>
    ): List<WeatherAlarm> {
        val oldScreen = displayed.associateBy { it.id }
        val disk = stored.associateBy { it.id }
        return requested.map { next ->
            val previous = oldScreen[next.id]
            val authoritative = disk[next.id]
            if (previous != null && authoritative != null && next == previous) authoritative
            else next
        }
    }

    fun changedIds(before: List<WeatherAlarm>, after: List<WeatherAlarm>): Set<Int> {
        val old = before.associateBy { it.id }
        val now = after.associateBy { it.id }
        return (old.keys + now.keys).filterTo(linkedSetOf()) { old[it] != now[it] }
    }
}
