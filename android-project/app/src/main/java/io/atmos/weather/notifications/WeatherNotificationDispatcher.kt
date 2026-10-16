package io.atmos.weather.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import io.atmos.weather.permission.PermissionGuide

/** Single delivery entry point for every app-authored user-facing notification.
 *
 * On supported, Xiaomi-authorized HyperOS 2/3 devices, attach official focus/island
 * extensions to the regular Android notification before posting it. Xiaomi may still
 * reject an unapproved notification scenario. The notification is never discarded.
 * App permission, channel blocking, device firmware and vendor scenario approval
 * remain OS-controlled. No root, private Settings edits or allowlist bypass.
 */
object WeatherNotificationDispatcher {
    fun post(
        context: Context,
        id: Int,
        notification: Notification,
        title: String,
        detail: String,
        kind: XiaomiFocusBridge.Kind
    ): Boolean {
        if (!PermissionGuide.canNotify(context)) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Vendor failure must never prevent a real weather alert from reaching Android.
        val vendorAttached = runCatching {
            XiaomiFocusBridge.decorate(context, notification, title, detail, kind)
        }.getOrDefault(false)
        return try {
            manager.notify(id, notification)
            true
        } catch (_: Exception) {
            // Defensive fallback if an OEM rejects its own optional extensions.
            if (!vendorAttached) false
            else runCatching {
                notification.extras.remove("miui.focus.param")
                notification.extras.remove("miui.focus.pics")
                manager.notify(id, notification)
                true
            }.getOrDefault(false)
        }
    }
}
