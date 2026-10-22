package io.atmos.weather.task

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import io.atmos.weather.notifications.WeatherNotificationDispatcher
import io.atmos.weather.notifications.XiaomiFocusBridge
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** WorkManager is inexact: never promise an alarm to the exact minute. */
object TaskReminderScheduler {
    private fun workId(taskId:String)="thinn-task-$taskId"
    fun sync(context:Context,tasks:List<ThinnTask>) {
        val work=WorkManager.getInstance(context)
        // Cancel removed tasks by using the persistent registry independent of the new list.
        val prefs=context.getSharedPreferences("thinn_task_schedule",Context.MODE_PRIVATE)
        val old=prefs.getStringSet("registered",emptySet())?.toSet() ?: emptySet()
        val newIds=tasks.map {it.id}.toSet()
        (old-newIds).forEach {work.cancelUniqueWork(workId(it))}
        prefs.edit().putStringSet("registered",newIds).apply()
        val now=System.currentTimeMillis()
        tasks.forEach {task->
            work.cancelUniqueWork(workId(task.id))
            if(!task.completed && task.notify && task.dueMillis()>now) {
                val input=Data.Builder().putString("id",task.id)
                    .putLong("due_millis",task.dueMillis()).build()
                val request=OneTimeWorkRequestBuilder<TaskReminderWorker>()
                    .setInitialDelay(task.dueMillis()-now,TimeUnit.MILLISECONDS)
                    .setInputData(input).addTag("thinn_task_reminder").build()
                work.enqueueUniqueWork(workId(task.id),ExistingWorkPolicy.REPLACE,request)
            }
        }
    }
}

class TaskReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val taskId=inputData.getString("id")?:return Result.success()
        // Re-check current data: deleted or completed reminders must not alert.
        val task=TaskStore.load(applicationContext).firstOrNull {it.id==taskId && !it.completed && it.notify}
            ?:return Result.success()
        val now=System.currentTimeMillis()
        val scheduledAt=inputData.getLong("due_millis",task.dueMillis())
        // A job from before the user edited this item cannot notify for the new schedule.
        if(!TaskReminderPolicy.sameSchedule(task.dueMillis(),scheduledAt)) return Result.success()
        // WorkManager can run early after clock changes; never notify before the user's due time.
        if(!TaskReminderPolicy.isDue(task.dueMillis(),now)) return Result.retry()
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(
                applicationContext,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel="thinn_personal_reminders"
        manager.createNotificationChannel(NotificationChannel(channel,"個人事項提醒",NotificationManager.IMPORTANCE_DEFAULT))
        val open=Intent(applicationContext,io.atmos.weather.MainActivity::class.java).apply {
            putExtra("open_tasks",true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending=PendingIntent.getActivity(applicationContext,task.id.hashCode(),open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message=NotificationCompat.Builder(applicationContext,channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pending)
            .setContentTitle(task.title).setContentText(task.note.ifBlank{"你安排的事項已到時間"})
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.note.ifBlank{"你安排的事項已到時間"}))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        // Do not put private note body into the island's public condensed text.
        WeatherNotificationDispatcher.post(applicationContext,task.id.hashCode(),message,
            "個人事項提醒","解鎖 App 查看事項內容",XiaomiFocusBridge.Kind.TASK_REMINDER)
        io.atmos.weather.voice.NaturalVoiceHub.offer(applicationContext,
            io.atmos.weather.voice.NaturalVoiceHub.Request(
                (task.title+"。"+task.note).take(220),
                io.atmos.weather.voice.NaturalVoiceHub.Priority.PERSONAL,
                "task-reminder:${task.id}:${task.dueMillis()}",
                privacySensitive=true,cacheable=false))
        return Result.success()
    }
}
