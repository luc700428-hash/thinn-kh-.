package io.atmos.weather.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDesignTokens
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
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
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
                Switch(checked=a.enabled,onCheckedChange={value->update(alarms.map{if(it.id==a.id)it.copy(enabled=value) else it})})
            }
        }
        Text("實際提醒由 Android 鬧鐘與通知權限控制；Android 14+ 全螢幕顯示須個別授權。",color=Color(0xFFCCE0ED),fontSize=14.sp)
    }
    if(showEditor) AlarmEditor(initial=editing,onClose={showEditor=false},onDelete={a->
        update(alarms.filterNot { it.id==a.id });showEditor=false
    },onSave={a->update(alarms.filterNot { it.id==a.id }+a);showEditor=false})
}

@Composable
private fun AlarmEditor(initial:WeatherAlarm?,onClose:()->Unit,onDelete:(WeatherAlarm)->Unit,onSave:(WeatherAlarm)->Unit) {
    val ctx=LocalContext.current
    val now=remember {java.time.LocalTime.now().plusMinutes(5)}
    var hour by remember(initial) { mutableIntStateOf(initial?.hour ?: now.hour) }
    var minute by remember(initial) { mutableIntStateOf(initial?.minute ?: now.minute) }
    var label by remember(initial) { mutableStateOf(initial?.title ?: "起床鬧鐘") }
    var repeat by remember(initial) { mutableStateOf(initial?.days ?: emptySet()) }
    var voice by remember(initial) { mutableStateOf(initial?.weatherVoice ?: true) }
    var snooze by remember(initial) { mutableStateOf(initial?.snooze ?: true) }
    var ringtoneUri by remember(initial) { mutableStateOf(initial?.ringtoneUri) }
    val ringtonePicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked=result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtoneUri=picked?.toString()
        }
    }
    var ringtonePickerError by remember { mutableStateOf(false) }
    var wheelOpen by remember { mutableStateOf(false) }
    if (!wheelOpen) AlertDialog(onDismissRequest=onClose,containerColor=bg,title={Text(if(initial==null)"新增天氣鬧鐘" else "編輯鬧鐘",color=ink)},
        text={Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick={wheelOpen=true}) {
                Text(String.format(Locale.TAIWAN,"%02d:%02d  ▾",hour,minute),fontSize=38.sp,color=accent)
            }
            OutlinedTextField(label={Text("標籤")},value=label,onValueChange={label=it.take(30)},singleLine=true)
            Spacer(Modifier.height(13.dp))
            Text("重複星期（不選表示只響一次）",color=ink,fontSize=16.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                days.forEachIndexed {index,text->
                    val day=index+1
                    FilterChip(selected=day in repeat,onClick={repeat=if(day in repeat)repeat-day else repeat+day},
                        label={Text(text,fontSize=14.sp)},modifier=Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("鬧鐘鈴聲",color=ink,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={
                ringtonePickerError=false
                runCatching {
                    ringtonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"選擇鬧鐘鈴聲")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            ringtoneUri?.let(android.net.Uri::parse)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    })
                }.onFailure { ringtonePickerError=true }
            }) { Text(if(ringtoneUri==null) "選擇系統鈴聲 · 預設" else "變更已選鈴聲",color=accent,fontSize=16.sp) }
            if(ringtonePickerError) Text("無法開啟系統鈴聲挑選器，請確認裝置有支援的鈴聲 App。",color=Color(0xFFFFD69E),fontSize=14.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("播報天氣與穿著建議",color=ink,fontSize=15.sp,modifier=Modifier.weight(1f))
                Switch(checked=voice,onCheckedChange={voice=it})
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("允許稍後提醒 10 分鐘",color=ink,fontSize=15.sp,modifier=Modifier.weight(1f))
                Switch(checked=snooze,onCheckedChange={snooze=it})
            }
            Text("語音讀取最近保存的天氣摘要；資料過舊時只提醒開啟 App，不會捏造即時預報。",color=Color(0xFFC8DCE9),fontSize=14.sp)
        }},confirmButton={
            Button(onClick={val id=initial?.id ?: ((System.currentTimeMillis()%1000000).toInt()+1)
                onSave(WeatherAlarm(id,hour,minute,label,repeat,true,voice,snooze,ringtoneUri))}){Text("儲存")}
        },dismissButton={Row {
            if(initial!=null) TextButton(onClick={onDelete(initial)}){Text("刪除",color=Color(0xFFFFB7AF))}
            TextButton(onClick=onClose){Text("取消")}
        }})
    if(wheelOpen) Dialog(onDismissRequest={wheelOpen=false}) {
        Column(Modifier.fillMaxWidth().background(Color(0xFFEAF5FC),RoundedCornerShape(26.dp))
            .padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("選擇鬧鐘時間",fontSize=21.sp,color=Color(0xFF17364E),fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                AndroidView(factory={android.widget.NumberPicker(it).apply {
                    minValue=0;maxValue=23;wrapSelectorWheel=true;value=hour
                    setFormatter { v->"%02d".format(Locale.TAIWAN,v) }
                    setOnValueChangedListener { _,_,newValue->hour=newValue }
                }},modifier=Modifier.weight(1f),update={if(it.value!=hour)it.value=hour})
                Text(":",fontSize=29.sp,color=Color(0xFF17364E))
                AndroidView(factory={android.widget.NumberPicker(it).apply {
                    minValue=0;maxValue=59;wrapSelectorWheel=true;value=minute
                    setFormatter { v->"%02d".format(Locale.TAIWAN,v) }
                    setOnValueChangedListener { _,_,newValue->minute=newValue }
                }},modifier=Modifier.weight(1f),update={if(it.value!=minute)it.value=minute})
            }
            Button(onClick={wheelOpen=false}) {Text("完成")}
        }
    }
}
