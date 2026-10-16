package io.atmos.weather.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.atmos.weather.MainActivity
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.permission.PermissionGuide
import java.util.concurrent.TimeUnit

/** Respects WorkManager/battery limits and avoids repeat alerts for the same release. */
class UpdateCheckWorker(context:Context, params:WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val ctx=applicationContext
        if (!ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getBoolean("check_updates_automatically",true))
            return Result.success()
        val release=try { AppUpdater.findUpdate(ctx) } catch (_:Exception) { return Result.retry() }
            ?: return Result.success()
        val store=ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        if (store.getString("last_update_notified",null) == release.version) return Result.success()
        if (!PermissionGuide.canNotify(ctx)) return Result.success()
        ThinnNotificationChannels.ensure(ctx)
        val go=PendingIntent.getActivity(
            ctx,752,
            Intent(ctx,MainActivity::class.java).putExtra("open_update_center",true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title="thinn-khì ${release.version} 已發布"
        val detail="發現新版本，開啟更新中心查看特色並安全安裝"
        val message=NotificationCompat.Builder(ctx,ThinnNotificationChannels.channelId(ctx, ThinnNotificationChannels.Type.UPDATES))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true).setContentIntent(go).setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        if (WeatherNotificationDispatcher.post(ctx,752,message,title,detail,XiaomiFocusBridge.Kind.APP_UPDATE)) {
            store.edit().putString("last_update_notified",release.version).apply()
        }
        return Result.success()
    }
    companion object {
        fun schedule(context:Context) {
            val manager=WorkManager.getInstance(context)
            if (!context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
                    .getBoolean("check_updates_automatically",true)) {
                manager.cancelUniqueWork("thinn_update_auto_check")
                return
            }
            val req=PeriodicWorkRequestBuilder<UpdateCheckWorker>(12,TimeUnit.HOURS)
                .setInitialDelay(20,TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(
                "thinn_update_auto_check",ExistingPeriodicWorkPolicy.KEEP,req)
        }
    }
}
