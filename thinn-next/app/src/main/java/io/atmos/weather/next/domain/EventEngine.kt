package io.atmos.weather.next.domain

/** Immutable shared model for future app/UI/widget/HyperOS/Live Updates adapters. */
data class NextEvent(
    val id: String,
    val type: Type,
    val title: String,
    val detail: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val verified: Boolean
) {
    enum class Type { ATMOSPHERE, TASK, TIMER, ALARM, VERIFIED_WEATHER_ALERT, VERIFIED_EARTHQUAKE }
}
object EventPolicy {
    fun priority(type: NextEvent.Type) = when (type) {
        NextEvent.Type.VERIFIED_EARTHQUAKE -> 60
        NextEvent.Type.VERIFIED_WEATHER_ALERT -> 50
        NextEvent.Type.ALARM -> 40
        NextEvent.Type.TIMER -> 30
        NextEvent.Type.TASK -> 20
        NextEvent.Type.ATMOSPHERE -> 10
    }
    fun isDeliverable(event: NextEvent, nowMs: Long): Boolean {
        if (event.id.isBlank() || event.title.isBlank()) return false
        if (event.issuedAtMs > nowMs + 30_000L || event.expiresAtMs <= nowMs) return false
        if (event.expiresAtMs <= event.issuedAtMs) return false
        if (event.type in listOf(NextEvent.Type.VERIFIED_EARTHQUAKE,
                NextEvent.Type.VERIFIED_WEATHER_ALERT) && !event.verified) return false
        return true
    }
    fun active(events: List<NextEvent>, nowMs: Long): NextEvent? = events
        .asSequence().filter { isDeliverable(it, nowMs) }
        .sortedWith(compareByDescending<NextEvent> { priority(it.type) }
            .thenByDescending { it.issuedAtMs }).firstOrNull()
}
