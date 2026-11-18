package io.atmos.weather.permission

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * What the first-run checklist asks for, in the order a warning needs it, and how to check and
 * open each one. Items that do not exist on this Android version are left out.
 */
internal object OnboardingPermissions {
    enum class Item(val title: String, val reason: String, val essential: Boolean) {
        LOCATION("位置", "顯示你所在地的天氣，並估算地震波抵達時間", true),
        NOTIFICATIONS("通知", "地震速報、特報、下雨與鬧鐘提醒", true),
        BATTERY("電池使用不受限制", "背景監測地震不會被省電功能中斷", true),
        FULL_SCREEN("全螢幕警報", "鎖定畫面時，地震速報與鬧鐘直接亮起", true),
        OVERLAY("跳出速報視窗", "使用其他 App 時，速報也能立刻跳出", false),
        EXACT_ALARM("準時鬧鐘", "天氣鬧鐘與提醒分秒不差", false),
        DND("勿擾模式例外", "開啟勿擾時，地震速報仍會響", false),
    }

    /** Runtime permissions are asked in-app; the rest open a system page. */
    val runtime: Map<Item, Array<String>> = mapOf(
        Item.LOCATION to arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
        Item.NOTIFICATIONS to if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
    )

    fun applicable(item: Item, sdk: Int = Build.VERSION.SDK_INT): Boolean = when (item) {
        Item.NOTIFICATIONS -> sdk >= 33
        Item.FULL_SCREEN -> sdk >= 34
        Item.EXACT_ALARM -> sdk >= 31
        else -> true
    }

    fun items(sdk: Int = Build.VERSION.SDK_INT): List<Item> = Item.entries.filter { applicable(it, sdk) }

    fun granted(ctx: Context, item: Item): Boolean = runCatching {
        when (item) {
            Item.LOCATION -> PermissionGuide.hasLocation(ctx)
            Item.NOTIFICATIONS -> PermissionGuide.hasNotifications(ctx)
            Item.BATTERY -> (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
            Item.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
            Item.OVERLAY -> Settings.canDrawOverlays(ctx)
            Item.EXACT_ALARM -> Build.VERSION.SDK_INT < 31 || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            Item.DND -> (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        }
    }.getOrDefault(false)

    /** The system page for a special access, with a general fallback if a vendor ROM lacks it. */
    @SuppressLint("BatteryLife") // An EEW app is an accepted use of the exemption; the user decides.
    fun intents(ctx: Context, item: Item): List<Intent> {
        val pkg = Uri.parse("package:${ctx.packageName}")
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        return when (item) {
            Item.BATTERY -> listOf(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), details)
            Item.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg), details) else listOf(details)
            Item.OVERLAY -> listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg), details)
            Item.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= 31) listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg), details) else listOf(details)
            Item.DND -> listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), details)
            Item.NOTIFICATIONS -> listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName), details)
            Item.LOCATION -> listOf(details)
        }
    }

    /** Opens the first page this device has. False if none could open. */
    fun open(ctx: Context, item: Item): Boolean = intents(ctx, item).any { intent ->
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }
}
