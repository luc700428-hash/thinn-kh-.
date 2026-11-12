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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import io.atmos.weather.ui.GlassButton
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
    val connection=SeismicFeedHealth.eew(live.enabled,live.eewStatus)
    @Composable fun StatusRow(label:String,value:String,ready:Boolean,onClick:(()->Unit)?=null) {
        Row(Modifier.fillMaxWidth().then(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier)
            .padding(vertical=12.dp).testTag("seismic-readiness-$label"),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text(label,color=p.onSurface,fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth())
                Text(value,color=if(ready)seismicHealthyInk else seismicCautionInk,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
            }
            if(onClick!=null)Icon(Icons.Rounded.ChevronRight,null,tint=p.onSurfaceVariant,modifier=Modifier.size(20.dp))
        }
        io.atmos.weather.ui.GlassDivider(color=p.outlineVariant)
    }
    StatusRow("速報連線",SeismicFeedHealth.eewLabel(connection),connection==SeismicFeedState.LIVE)
    StatusRow("系統通知",if(notifications&&!blocked)"已允許" else if(blocked)"部分速報分類已關閉" else "尚未允許",notifications&&!blocked){
        open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName))
    }
    if(fullScreen&&Build.VERSION.SDK_INT>=34)StatusRow("鎖定時全螢幕",if(fullscreen)"已允許" else "前往系統授權",fullscreen){
        open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${ctx.packageName}")))
    }
    if(criticalEnabled){
        StatusRow("勿擾模式",if(!policy)"尚未授權" else if(bypass)"優先勿擾例外已設定" else "請確認緊急速報分類",policy&&bypass){
            if(!policy)open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            else open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID,SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.EEW_URGENT)))
        }
        val audible=audio.getStreamVolume(AudioManager.STREAM_ALARM)>0
        StatusRow("警報音量",if(audible)"使用鬧鐘音量" else "音量為零",audible){open(Intent(Settings.ACTION_SOUND_SETTINGS))}
        if(nm.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_NONE)
            Text("完全靜音模式下無法穿透勿擾。",color=p.error,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth().padding(top=8.dp))
    }
    if(fullScreen&&criticalEnabled){
        Spacer(Modifier.height(16.dp))
        GlassButton(onClick={scheduled=SeismicCriticalTest.schedule(ctx);revision++},enabled=!scheduled,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Text(if(scheduled)"已安排，可先鎖定螢幕" else "5 秒後測試緊急警報",fontSize=15.sp,lineHeight=22.sp,modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
        if(scheduled)GlassTextButton(onClick={SeismicCriticalTest.cancel(ctx);scheduled=false;revision++},modifier=Modifier.fillMaxWidth()){Text("取消演練通知")}
        val testStatus=remember(revision,meter){SeismicCriticalTest.status(ctx)}
        if(testStatus.isNotBlank())Text(testStatus,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth().padding(top=8.dp))
    }
    Text("App 使用中會直接顯示速報。鎖定或使用其他 App 時，顯示方式由系統控制。",color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth().padding(top=12.dp))
}
