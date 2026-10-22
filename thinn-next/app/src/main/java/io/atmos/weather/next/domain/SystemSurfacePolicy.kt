package io.atmos.weather.next.domain

/**
 * Platform selection is permission- and protocol-gated. It does NOT claim to control
 * Xiaomi SystemUI, bypass OEM focus approvals, or create unlimited Android Live Updates.
 * A future adapter must independently check real notification/channel permissions.
 */
enum class Surface { IN_APP_ONLY, ANDROID_NOTIFICATION, ANDROID_LIVE_UPDATE, XIAOMI_FOCUS }
data class DeviceSurfaceCapabilities(
    val canNotify:Boolean,
    val isXiaomi:Boolean,
    val focusPermissionApproved:Boolean,
    val promotedNotificationSupported:Boolean,
    val userInitiatedOngoing:Boolean
)
object SystemSurfacePolicy {
    fun route(event:NextEvent,nowMs:Long,cap:DeviceSurfaceCapabilities):Surface {
        if(!EventPolicy.isDeliverable(event,nowMs)) return Surface.IN_APP_ONLY
        if(!cap.canNotify) return Surface.IN_APP_ONLY
        val mayPersist=event.type in setOf(NextEvent.Type.TIMER,NextEvent.Type.ALARM)
        if(cap.isXiaomi && cap.focusPermissionApproved && mayPersist) return Surface.XIAOMI_FOCUS
        if(cap.promotedNotificationSupported && cap.userInitiatedOngoing && mayPersist)
            return Surface.ANDROID_LIVE_UPDATE
        return Surface.ANDROID_NOTIFICATION
    }
}
