package io.atmos.weather.push

/** Pure decision rule for Firebase callbacks: no stale operation can override consent. */
internal object PushOptInGate {
    fun accepts(request: Long, latest: Long, requestEnabled: Boolean, currentDesired: Boolean): Boolean =
        request == latest && requestEnabled == currentDesired
}
