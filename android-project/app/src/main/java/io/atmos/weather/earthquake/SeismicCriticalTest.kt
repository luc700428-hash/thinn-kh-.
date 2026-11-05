package io.atmos.weather.earthquake

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.UUID

/** Explicit LOCAL notification drill; cannot publish a live EEW ticket. */
internal object SeismicCriticalTest {
    private const val STORE="thinn_critical_test_v1"
    private const val NOTIFICATION=29205
    private var pending:Pair<Handler,Runnable>?=null
    private fun prefs(ctx:Context)=ctx.getSharedPreferences(STORE,0)
    fun status(ctx:Context):String=prefs(ctx).getString("status","").orEmpty()
    private fun status(ctx:Context,text:String){prefs(ctx).edit().putString("status",text).apply()}
    fun schedule(ctx:Context):Boolean {
        val app=ctx.applicationContext
        if(!SeismicCriticalMode.enabled(app)){status(app,"請先啟用緊急速報模式");return false}
        if(SeismicAlertDelivery.active.value!=null){status(app,"目前有真實速報，已停止演練");return false}
        cancel(app)
        val now=System.currentTimeMillis();val token=UUID.randomUUID().toString()
        if(!prefs(app).edit().putString("token",token).putLong("created",now)
                .putString("status","已安排：5 秒後發送演練通知，可先鎖定螢幕").commit())return false
        val handler=Handler(Looper.getMainLooper())
        val task=Runnable{pending=null;publish(app,token)}
        pending=handler to task;handler.postDelayed(task,5000)
        return true
    }
    fun valid(ctx:Context,token:String?,now:Long=System.currentTimeMillis()):Boolean =
        token!=null&&token.matches(Regex("[0-9a-f-]{36}"))&&prefs(ctx).getString("token",null)==token&&
            now-prefs(ctx).getLong("created",0L) in 0..65_000L&&SeismicCriticalMode.enabled(ctx)&&SeismicAlertDelivery.active.value==null
    fun cancel(ctx:Context) {
        pending?.let{(handler,task)->handler.removeCallbacks(task)};pending=null
        prefs(ctx).edit().remove("token").remove("created").apply()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION)
    }
    private fun publish(ctx:Context,token:String) {
        if(!valid(ctx,token)){status(ctx,"演練已取消或被真實速報中斷");return}
        if(!io.atmos.weather.permission.PermissionGuide.canNotify(ctx)){status(ctx,"測試未送出：請先允許通知權限");return}
        SeismicAlertPolicy.ensureChannels(ctx)
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel=SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.EEW_URGENT)
        val importance=nm.getNotificationChannel(channel)?.importance?:NotificationManager.IMPORTANCE_NONE
        if(importance==NotificationManager.IMPORTANCE_NONE){status(ctx,"測試未送出：緊急速報通知分類已關閉");return}
        val intent=Intent(ctx,SeismicCriticalTestActivity::class.java).setData(Uri.parse("thinn-eew-test://$token"))
            .putExtra("test_ticket",token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val options=if(Build.VERSION.SDK_INT>=35)ActivityOptions.makeBasic().apply{
            setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }.toBundle() else null
        val action=PendingIntent.getActivity(ctx,NOTIFICATION,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,options)
        val note=NotificationCompat.Builder(ctx,channel).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("演練測試 · 並非真實地震").setContentText("本機通知測試：請確認聲音、鎖屏與畫面。沒有目前地震事件。")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setContentIntent(action).setAutoCancel(true).setTimeoutAfter(60_000)
        if(SeismicAlertDelivery.mayUseFullScreen(ctx)&&importance>=NotificationManager.IMPORTANCE_HIGH)note.setFullScreenIntent(action,true)
        runCatching{nm.notify(NOTIFICATION,note.build())}
            .onSuccess{status(ctx,"演練通知已送出；請依實際聲音與鎖屏畫面確認")}
            .onFailure{status(ctx,"測試未送出：通知權限或系統狀態已改變")}
    }
}
