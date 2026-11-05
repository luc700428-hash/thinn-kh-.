package io.atmos.weather.earthquake

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassTextButton

/** Read actual system state again after permission/settings changes. */
@Composable internal fun SeismicDeliveryReadiness(fullScreen:Boolean=false,criticalEnabled:Boolean=false) {
    val ctx=LocalContext.current;val owner=LocalLifecycleOwner.current;val p=MaterialTheme.colorScheme
    var revision by remember{mutableIntStateOf(0)};var meter by remember{mutableIntStateOf(0)}
    var scheduled by remember{mutableStateOf(false)};var channelsRevision by remember{mutableIntStateOf(0)}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)revision++}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    LaunchedEffect(revision,criticalEnabled){SeismicAlertPolicy.ensureChannels(ctx);channelsRevision++}
    LaunchedEffect(owner){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){kotlinx.coroutines.delay(2000);meter++}}}
    LaunchedEffect(scheduled){if(scheduled){kotlinx.coroutines.delay(6000);scheduled=false;revision++}}
    val live by ExpTechSeismicService.live.collectAsState()
    val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val audio=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val notifications=remember(revision,meter){io.atmos.weather.permission.PermissionGuide.canNotify(ctx)}
    val fullscreen=remember(revision,meter){Build.VERSION.SDK_INT<34||runCatching{nm.canUseFullScreenIntent()}.getOrDefault(false)}
    val policy=remember(revision,meter){nm.isNotificationPolicyAccessGranted}
    val channels=remember(revision,criticalEnabled,channelsRevision,meter){SeismicAlertPolicy.Scenario.entries.filter{it.name.startsWith("EEW")}
        .mapNotNull{nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,it))}}
    val blocked=channels.any{it.importance==NotificationManager.IMPORTANCE_NONE}
    val bypass=channels.isNotEmpty()&&channels.all{it.canBypassDnd()}
    fun open(intent:Intent){runCatching{ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
    @Composable fun ReadinessRow(label:String,ready:Boolean){Text(label,color=if(ready)seismicHealthyInk else seismicCautionInk,fontSize=14.sp,lineHeight=22.sp,modifier=Modifier.fillMaxWidth())}
    val connection=SeismicFeedHealth.eew(live.enabled,live.eewStatus)
    ReadinessRow("速報串流：${SeismicFeedHealth.eewLabel(connection)}",connection==SeismicFeedState.LIVE)
    ReadinessRow(if(notifications)"通知權限：已允許" else "通知權限：未允許",notifications)
    ReadinessRow(if(fullscreen)"系統全螢幕通知：目前允許" else "系統全螢幕通知：尚未授權",fullscreen)
    if(fullScreen){
        val appFullscreen=ctx.getSharedPreferences("thinn_seismic_v15",0).getBoolean("allow_seismic_full_screen",false)
        ReadinessRow(if(appFullscreen)"App 鎖屏警示：已啟用" else "App 鎖屏警示：目前關閉",appFullscreen)
    }
    if(blocked)Text("部分速報通知分類已在系統中關閉",color=p.error,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
    if(criticalEnabled){
        ReadinessRow(if(policy)"勿擾存取：已授權" else "勿擾存取：尚未授權",policy)
        ReadinessRow(if(bypass)"優先勿擾例外：已設定" else "優先勿擾例外：需在系統通知分類確認",bypass)
        ReadinessRow(if(audio.getStreamVolume(AudioManager.STREAM_ALARM)>0)"緊急音訊：使用鬧鐘音量" else "鬧鐘音量為零，目前無法發聲",audio.getStreamVolume(AudioManager.STREAM_ALARM)>0)
        if(nm.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_NONE)
            Text("目前為系統完全靜音；Android 不允許通知分類穿透此模式。",color=p.error,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
        if(!policy)GlassTextButton(onClick={open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}){Text("授權勿擾存取")}
        GlassTextButton(onClick={open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID,SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.EEW_URGENT)))}){Text("管理緊急速報通知分類")}
        GlassTextButton(onClick={open(Intent(Settings.ACTION_SOUND_SETTINGS))}){Text("調整鬧鐘音量")}
    }
    if(fullScreen&&Build.VERSION.SDK_INT>=34)GlassTextButton(onClick={open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${ctx.packageName}")))}){Text("管理全螢幕通知權限")}
    GlassTextButton(onClick={open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName))}){Text("管理系統通知")}
    if(fullScreen&&criticalEnabled){
        GlassTextButton(onClick={scheduled=SeismicCriticalTest.schedule(ctx);revision++},enabled=!scheduled){Text(if(scheduled)"演練已安排，可先鎖屏" else "5 秒後測試緊急警報")}
        if(scheduled)GlassTextButton(onClick={SeismicCriticalTest.cancel(ctx);scheduled=false;revision++}){Text("取消演練通知")}
        val testStatus=remember(revision){SeismicCriticalTest.status(ctx)}
        if(testStatus.isNotBlank())Text(testStatus,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(4.dp))
    Text("授權後會請求穿透優先勿擾，仍受目前系統規則及通知分類控制。解鎖使用其他 App 時，Android 通常顯示浮動警報；鎖定或熄屏時才嘗試全螢幕喚醒。",color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=22.sp,modifier=Modifier.fillMaxWidth())
}
