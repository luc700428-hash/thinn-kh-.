package io.atmos.weather.alarm
import io.atmos.weather.ui.appleBouncingScroll

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.material3.Icon
import io.atmos.weather.ui.GlassIconButton as IconButton
import io.atmos.weather.ui.GlassTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val bg = Color(0xFF10273D)
private val ink = Color.White
private val accent = Color(0xFF95E5FF)
private val days=listOf("一","二","三","四","五","六","日")

@Composable
fun WeatherAlarmPage(onClose:()->Unit) {
    val ctx=LocalContext.current
    var alarms by remember { mutableStateOf(WeatherAlarmStore.load(ctx)) }
    var editing by remember { mutableStateOf<WeatherAlarm?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var notificationAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    }
    val requestNotifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAllowed=it
    }
    val notifications=remember(ctx) {ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager}
    fun update(value:List<WeatherAlarm>) {
        val before=alarms.map { it.id }.toSet()
        val after=value.map { it.id }.toSet()
        (before-after).forEach { WeatherAlarmScheduler.cancel(ctx,it) }
        alarms=value.sortedWith(compareBy({it.hour},{it.minute}))
        WeatherAlarmStore.save(ctx,alarms)
        alarms.forEach { if(it.enabled) WeatherAlarmScheduler.schedule(ctx,it)
            else WeatherAlarmScheduler.cancel(ctx,it.id) }
    }
    BackHandler(enabled=showEditor) { showEditor=false }
    Column(Modifier.fillMaxSize().background(io.atmos.weather.ui.ThinnOpticalMaterial.page)
        .statusBarsPadding().navigationBarsPadding().appleBouncingScroll().verticalScroll(rememberScrollState())
        .padding(horizontal=20.dp,vertical=20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onClose) { Icon(Icons.Rounded.ArrowBack,"返回工具",tint=ink) }
            Text("天氣鬧鐘",fontSize=28.sp,fontWeight=FontWeight.Bold,color=ink,modifier=Modifier.weight(1f))
            IconButton(onClick={editing=null;showEditor=true}) {Icon(Icons.Rounded.Add,"新增鬧鐘",tint=accent)}
        }
        Text("鬧鐘時間與重複週期會保存在手機中",color=Color(0xFFD8EAF6),fontSize=16.sp)
        if (!WeatherAlarmScheduler.canBeExact(ctx)) {
            Spacer(Modifier.height(14.dp))
            Text("尚未取得精確鬧鐘權限；可能延遲響鈴。",color=Color(0xFFFFD69E),fontSize=16.sp)
            TextButton(onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }.onFailure {
                    android.widget.Toast.makeText(
                        ctx, "無法開啟精確鬧鐘權限設定", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Text("開啟精確鬧鐘授權",color=accent)
            }
        }
        if(Build.VERSION.SDK_INT >= 33 && !notificationAllowed) {
            Text("通知權限尚未開啟；可能看不到鬧鐘響鈴畫面。",color=Color(0xFFFFD69E),fontSize=15.sp)
            TextButton(onClick={requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)}) {
                Text("允許鬧鐘通知",color=accent)
            }
        }
        if(Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent()) {
            Text("尚未開放全螢幕鬧鐘，鎖定畫面可能只顯示通知。",color=Color(0xFFFFD69E),fontSize=15.sp)
            TextButton(onClick={
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        android.net.Uri.parse("package:${ctx.packageName}")))
                }.onFailure {
                    android.widget.Toast.makeText(ctx,"無法開啟全螢幕通知設定",android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text("設定全螢幕鬧鐘",color=accent) }
        }
        Spacer(Modifier.height(18.dp))
        if(alarms.isEmpty()) {
            Text("還沒有鬧鐘。按右上角＋新增起床提醒。",color=ink,fontSize=17.sp,
                modifier=Modifier.padding(vertical=28.dp))
        }
        alarms.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(bottom=12.dp).background(Color.White.copy(alpha=.12f),RoundedCornerShape(22.dp))
                .clickable {editing=a;showEditor=true}.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(String.format(Locale.TAIWAN,"%02d:%02d",a.hour,a.minute),fontSize=39.sp,color=ink,
                        fontWeight=FontWeight.Light)
                    Text(a.title.ifBlank {"鬧鐘"},fontSize=17.sp,color=ink,fontWeight=FontWeight.SemiBold)
                    Text((if(a.days.isEmpty()) "只響一次" else "每週 ${a.days.sorted().joinToString("、") {days[it-1]}}")+
                        (if(a.weatherVoice) " · 起床天氣語音" else ""),fontSize=14.sp,color=Color(0xFFE1F0F9))
                }
                R10Switch(checked=a.enabled,onCheckedChange={value->update(alarms.map{if(it.id==a.id)it.copy(enabled=value) else it})})
            }
        }
        Text("實際提醒由 Android 鬧鐘與通知權限控制；Android 14+ 全螢幕顯示須個別授權。",color=Color(0xFFCCE0ED),fontSize=14.sp)
    }
    if(showEditor) R10AlarmEditor(initial=editing,onClose={showEditor=false},onDelete={a->
        update(alarms.filterNot { it.id==a.id });showEditor=false
    },onSave={a->update(alarms.filterNot { it.id==a.id }+a);showEditor=false})
}

