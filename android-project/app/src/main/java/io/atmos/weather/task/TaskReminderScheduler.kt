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
import androidx.core.app.NotificationManagerCompat
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
                val input=Data.Builder().putString("id",task.id).putString("title",task.title).build()
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
        if(task.dueMillis()>now+60_000L)return Result.retry()
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
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(task.id.hashCode(),message) }
        return Result.success()
    }
}
