package io.atmos.weather.presentation.home

import io.atmos.weather.ui.GlassModalBottomSheet as ModalBottomSheet
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkManager
import io.atmos.weather.briefing.WeatherBriefingStore
import io.atmos.weather.briefing.WeatherBriefingScheduler
import io.atmos.weather.earthquake.ExpTechSeismicService
import io.atmos.weather.permission.PermissionGuide
import io.atmos.weather.update.UpdateCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Directly interrogates Android/WorkManager rather than claiming a preference implies success. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDiagnosticsPage(onClose:()->Unit) {
    val ctx=LocalContext.current
    val prefs=remember(ctx){ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)}
    var refreshKey by remember { mutableIntStateOf(0) }
    val notifications=remember(refreshKey){PermissionGuide.canNotify(ctx)}
    val location=remember(refreshKey){PermissionGuide.hasLocation(ctx)}
    val slots=remember(refreshKey){WeatherBriefingStore.load(ctx)}
    val enabledSlots=slots.filter {it.enabled}
    val updateEnabled=remember(refreshKey){prefs.getBoolean("check_updates_automatically",true)}
    val realtime by ExpTechSeismicService.live.collectAsState()
    var workStates by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    LaunchedEffect(refreshKey) {
        workStates=withContext(Dispatchers.IO) {
            val manager=WorkManager.getInstance(ctx)
            val names=listOf("thinn_update_auto_check") + (1..5).map{"thinn_weather_briefing_v154_$it"}
            names.associateWith { name ->
                try {
                    val jobs=manager.getWorkInfosForUniqueWork(name).get()
                    if(jobs.isEmpty()) "未排程" else jobs.joinToString {it.state.name}
                } catch(_:Exception) {"目前無法讀取工作狀態"}
            }
        }
    }
    ModalBottomSheet(onDismissRequest=onClose,containerColor=Color(0xFF102C49),contentColor=Color.White) {
        Column(Modifier.fillMaxWidth().heightIn(max=720.dp).verticalScroll(rememberScrollState())
            .padding(horizontal=20.dp).padding(bottom=30.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("設定健康檢查",fontSize=23.sp,fontWeight=FontWeight.Bold)
            Text("讀取系統實際授權與背景工作狀態。顯示 ENQUEUED 只代表已排程，不保證準時通知。",
                color=Color(0xFFB7D5E7),fontSize=14.sp)
            HealthStatus("通知權限",if(notifications)"已允許" else "未允許／遭系統關閉",notifications)
            HealthStatus("定位權限",if(location)"已允許" else "未授權，定位相關資訊可能無法取得",location)
            HealthStatus("版本更新自動檢查",if(updateEnabled)"開啟 · ${workStates["thinn_update_auto_check"]?:"讀取中"}" else "已停用",updateEnabled && workStates["thinn_update_auto_check"]?.contains("ENQUEUED")==true)
            HealthStatus("地震即時 SSE 連線",if(realtime.enabled)"EEW：${realtime.eewStatus} · RTS：${realtime.rtsStatus}" else "未啟動；需在地震中心開啟前景監測",realtime.enabled && realtime.eewStatus.contains("已連線"))
            Text("智慧天氣定時通知 · ${enabledSlots.size}/5 已啟用",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            (1..5).forEach { id ->
                val slot=slots.firstOrNull{it.id==id}
                val text=if(slot==null)"未建立" else if(!slot.enabled) "已停用" else "%02d:%02d · %s".format(slot.hour,slot.minute,workStates["thinn_weather_briefing_v154_$id"]?:"讀取中")
                HealthStatus("通知 $id",text,slot?.enabled==true && workStates["thinn_weather_briefing_v154_$id"]?.contains("ENQUEUED")==true)
            }
            val crash=io.atmos.weather.CrashDiagnostics.last(ctx)
            if(!crash.isNullOrBlank()) {
                Text("上次異常結束紀錄（僅儲存本機，不自動上傳）",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                Text(crash.lineSequence().firstOrNull().orEmpty().take(180),color=Color(0xFFFFD6A1),fontSize=14.sp)
                OutlinedButton(onClick={
                    val clipboard=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("thinn-khì 崩潰紀錄",crash))
                }) { Text("複製崩潰紀錄（方便修復工具／設定頁）",fontSize=14.sp) }
            }
            Text("音效受到靜音、勿擾及各通知通道設定影響；焦點通知由裝置廠商決定。",
                color=Color(0xFFB7D5E7),fontSize=14.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={
                    runCatching {ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${ctx.packageName}")))}
                }) {Text("系統權限",fontSize=14.sp)}
                OutlinedButton(onClick={
                    WeatherBriefingScheduler.reschedule(ctx)
                    UpdateCheckWorker.schedule(ctx)
                    refreshKey++
                }) {Text("重新檢查／修復排程",fontSize=14.sp)}
            }
        }
    }
}

@Composable private fun HealthStatus(title:String,detail:String,good:Boolean) {
    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.075f),RoundedCornerShape(16.dp))
        .border(1.dp,Color.White.copy(alpha=.1f),RoundedCornerShape(16.dp))
        .padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if(good) Color(0xFF65DAB1) else Color(0xFFFFC17E),RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title,fontSize=16.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
            Text(detail,fontSize=14.sp,color=Color(0xFFB7D5E7))
        }
    }
}
