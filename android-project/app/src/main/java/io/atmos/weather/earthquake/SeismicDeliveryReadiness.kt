package io.atmos.weather.earthquake
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassTextButton

@Composable internal fun SeismicDeliveryReadiness(fullScreen:Boolean=false) {
    val ctx=LocalContext.current;val owner=LocalLifecycleOwner.current
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)revision++}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val permissions=remember(revision){io.atmos.weather.permission.PermissionGuide.canNotify(ctx) to (Build.VERSION.SDK_INT<34||runCatching{nm.canUseFullScreenIntent()}.getOrDefault(false))}
    Text(if(permissions.first)"通知權限：已允許" else "通知權限：未允許",color=MaterialTheme.colorScheme.onSurface,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
    Text(if(permissions.second)"系統全螢幕通知：目前允許" else "系統全螢幕通知：尚未授權",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
    val blocked=remember(revision){SeismicAlertPolicy.Scenario.entries.filter{it.name.startsWith("EEW")}.any{nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,it))?.importance==NotificationManager.IMPORTANCE_NONE}}
    if(blocked)Text("部分速報通知分類已在系統中關閉",color=MaterialTheme.colorScheme.error,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
    if(fullScreen&&Build.VERSION.SDK_INT>=34)GlassTextButton(onClick={runCatching{ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${ctx.packageName}")))}}){Text("管理全螢幕通知權限")}
    else GlassTextButton(onClick={runCatching{ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName))}}){Text("管理系統通知")}
    Spacer(Modifier.height(4.dp))
    Text("解鎖使用手機時，Android 可能只顯示浮動通知。鎖定、熄屏時是否喚醒仍由系統與省電設定決定。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
}
