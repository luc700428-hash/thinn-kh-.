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
import io.atmos.weather.BuildConfig
import io.atmos.weather.earthquake.CwaOfficialGateway
import io.atmos.weather.push.SeismicPushRegistration
import io.atmos.weather.notifications.XiaomiFocusBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Directly interrogates Android/WorkManager rather than claiming a preference implies success. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDiagnosticsPage(onClose:()->Unit) {
    val ctx=LocalContext.current
    val prefs=remember(ctx){ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)}
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope=rememberCoroutineScope()
    var gatewayRunning by remember { mutableStateOf(false) }
    var gatewaySuccess by remember { mutableStateOf(false) }
    var gatewayStatus by remember { mutableStateOf("尚未測試；請手動執行正式資料代理檢查") }
    var xiaomiAvailability by remember { mutableStateOf<XiaomiFocusBridge.Availability?>(null) }
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
            val names=listOf("thinn_update_auto_check", "thinn-khi-widget-interval-v3") +
                (1..5).map{"thinn_weather_briefing_v154_$it"}
            names.associateWith { name ->
                try {
                    val jobs=manager.getWorkInfosForUniqueWork(name).get()
                    if(jobs.isEmpty()) "未排程" else jobs.joinToString {it.state.name}
                } catch(_:Exception) {"目前無法讀取工作狀態"}
            }
        }
        xiaomiAvailability=withContext(Dispatchers.IO) {
            runCatching { XiaomiFocusBridge.availability(ctx) }.getOrNull()
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
            HealthStatus("桌面 Widget 排程",
                workStates["thinn-khi-widget-interval-v3"] ?: "讀取中／尚未排程",
                workStates["thinn-khi-widget-interval-v3"]?.contains("ENQUEUED")==true)
            val focusDetail=when(xiaomiAvailability) {
                XiaomiFocusBridge.Availability.ENABLED -> "系統允許焦點通知；實際上島仍需事件及小米系統批准"
                XiaomiFocusBridge.Availability.NOT_XIAOMI -> "非小米裝置；使用 Android 標準通知"
                XiaomiFocusBridge.Availability.UNSUPPORTED -> "系統沒有相容的超級島協定；使用 Android 標準通知"
                XiaomiFocusBridge.Availability.PERMISSION_NOT_GRANTED -> "尚未取得小米焦點通知權限；使用 Android 標準通知"
                null -> "讀取中／無法確認；使用 Android 標準通知"
            }
            HealthStatus("小米超級島授權",focusDetail,
                xiaomiAvailability==XiaomiFocusBridge.Availability.ENABLED)
            Text("已安裝版本 ${BuildConfig.VERSION_NAME} · ${BuildConfig.THINN_OTA_CHANNEL} · ${BuildConfig.THINN_BUILD_SHA.take(10)}",
                color=Color(0xFFB7D5E7),fontSize=14.sp)
            Text("中央氣象署官方資料代理（手動真實連線檢查）",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            val configured=SeismicPushRegistration.configured()
            HealthStatus("Firebase 專案配置",
                if(configured) "App 已設定專案資訊；不代表雲端函式已部署或 FCM 可送達"
                else "Firebase 專案資料缺漏；請檢查 Actions 建置變數",configured)
            HealthStatus("CWA 官方資料代理",gatewayStatus,gatewaySuccess)
            OutlinedButton(onClick={
                if(!gatewayRunning) scope.launch {
                    gatewayRunning=true
                    gatewaySuccess=false
                    gatewayStatus="正在向已部署的官方資料代理發送測試查詢…"
                    try {
                        val response=withTimeoutOrNull(20_000L) {
                            CwaOfficialGateway.read(ctx,"E-A0015-001")
                        }
                        gatewaySuccess=response?.optJSONObject("records")!=null
                        gatewayStatus=when {
                            response==null -> "代理查詢逾時；請檢查網路、Firebase Functions 與服務部署"
                            gatewaySuccess -> "已收到官方資料結構；不代表地震即時速報、颱風或 FCM 已驗證"
                            else -> "代理已回覆，但資料結構不是預期的官方 records"
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                      catch(error:Exception) {
                        gatewayStatus=CwaOfficialGateway.describeFailure(error)
                    } finally { gatewayRunning=false }
                }
            },enabled=!gatewayRunning) {
                Text(if(gatewayRunning) "測試中…" else "測試 CWA 官方資料代理",fontSize=14.sp)
            }
            Text("這項檢查只測單次授權代理和資料結構，不會產生正式地震警報，也不會判定所有氣象資料都即時。",
                color=Color(0xFFB7D5E7),fontSize=14.sp)
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
