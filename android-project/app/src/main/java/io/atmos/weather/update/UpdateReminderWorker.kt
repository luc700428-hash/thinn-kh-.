package io.atmos.weather.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import io.atmos.weather.MainActivity
import io.atmos.weather.R
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Reminds the user; never opens an installer or silently updates the app. */
class UpdateReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        if(!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"軟體更新提醒",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(applicationContext,250026,Intent(applicationContext,MainActivity::class.java)
            .putExtra("open_update_center",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(250026,NotificationCompat.Builder(applicationContext,CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_weather).setContentTitle("thinn-khì 更新提醒")
            .setContentText("開啟軟體更新，確認並安裝新版 ${inputData.getString("version") ?: ""}")
            .setContentIntent(open).setAutoCancel(true).build())
        return Result.success()
    }
    companion object {
        private const val CHANNEL="thinn_update_reminder"
        fun scheduleTonight(context:Context,version:String):String {
            check(NotificationManagerCompat.from(context).areNotificationsEnabled()) {"請先在手機設定開啟通知，再安排更新提醒。"}
            val now=ZonedDateTime.now()
            val evening=now.withHour(22).withMinute(0).withSecond(0).withNano(0)
            val at=if(evening.isAfter(now)) evening else now.plusMinutes(30)
            val request=OneTimeWorkRequestBuilder<UpdateReminderWorker>()
                .setInitialDelay(Duration.between(now,at).toMillis(),TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("version" to version)).build()
            WorkManager.getInstance(context).enqueueUniqueWork("thinn_update_tonight",ExistingWorkPolicy.REPLACE,request)
            return at.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
        }
    }
}
