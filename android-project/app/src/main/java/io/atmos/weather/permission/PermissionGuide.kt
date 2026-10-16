package io.atmos.weather.permission

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Permission education only; opening OS settings requires an explicit user tap. */
object PermissionGuide {
    fun hasLocation(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)== PackageManager.PERMISSION_GRANTED

    fun canNotify(context: Context): Boolean {
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return hasNotifications(context) && manager.areNotificationsEnabled()
    }

    fun needsBatteryGuidance(context: Context): Boolean {
        val brand=(Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "oppo", "vivo", "realme", "huawei", "honor").any { brand.contains(it) }
    }

    fun openAppNotificationSettings(activity:Activity) {
        val intent=Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)
        activity.startActivity(intent)
    }
    fun openAppDetails(activity:Activity) {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${activity.packageName}")))
    }
    fun openBatterySettings(activity: Activity) {
        // OEM settings vary. This generic application detail screen lets the user decide.
        openAppDetails(activity)
    }
}
