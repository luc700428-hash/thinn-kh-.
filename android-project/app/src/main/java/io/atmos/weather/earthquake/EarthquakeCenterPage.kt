package io.atmos.weather.earthquake
import io.atmos.weather.ui.IOSLinearProgressIndicator as LinearProgressIndicator
import io.atmos.weather.ui.appleBouncingScroll

import io.atmos.weather.ui.GlassDivider as HorizontalDivider
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassTextButton as TextButton
import io.atmos.weather.ui.GlassIconButton as IconButton
import io.atmos.weather.ui.GlassSwitch as Switch
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import io.atmos.weather.presentation.home.LocalAppearance
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val white:Color @Composable get()=MaterialTheme.colorScheme.onSurface
private val soft:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
private val cyan:Color @Composable get()=MaterialTheme.colorScheme.primary
private val orange:Color @Composable get()=if(MaterialTheme.colorScheme.background.red<.5f)Color(0xFFFFD094) else Color(0xFF8A4300)

/** Report center only. NOT a certified seconds-before-shaking early warning receiver. */
@Composable
fun EarthquakeCenterPage(onClose: () -> Unit) {
    val context=LocalContext.current
    val uri=LocalUriHandler.current
    val haptics=LocalHapticEngine.current
    val appearance=LocalAppearance.current
    val scope=rememberCoroutineScope()
    val prefs=remember(context) { context.getSharedPreferences("thinn_seismic_v15",android.content.Context.MODE_PRIVATE) }
    val realtime by ExpTechSeismicService.live.collectAsState()
    var reports by remember { mutableStateOf<List<EarthquakeReport>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var drillOpen by remember { mutableStateOf(false) }
    var simulationOpen by remember { mutableStateOf(false) }
    var simulationScenario by remember { mutableStateOf<EewIncident?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var monitorOpen by remember { mutableStateOf(false) }
    var autoWatch by remember { mutableStateOf(prefs.getBoolean("earthquake_foreground_watch",true)) }
    var lastChecked by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (loading) return
        loading=true;error=null
        scope.launch {
            val official=runCatching { CwaEarthquakeRepository.latest(context) }.getOrNull()
            if(!official.isNullOrEmpty()) {
                reports=official
                lastChecked=java.time.ZonedDateTime.now(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            } else {
                runCatching { ExpTechPublicEarthquakeReports.latest() }
                    .onSuccess { reports=it; lastChecked=java.time.ZonedDateTime.now(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("HH:mm:ss")) }
                    .onFailure { error=it.localizedMessage ?: "資料無法取得" }
            }
            loading=false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    // Keep the report-polling switch in sync after returning from the settings dialog.
    LaunchedEffect(settingsOpen) {
        if (!settingsOpen) autoWatch=prefs.getBoolean("earthquake_foreground_watch",true)
    }
    // Foreground-only polling for post-event reports; not EEW or RTS.
    LaunchedEffect(autoWatch) {
        if (autoWatch) while(true) {
            kotlinx.coroutines.delay(60_000L)
            refresh()
        }
    }
    val motion=rememberInfiniteTransition(label="seismic rings")
    val progress by motion.animateFloat(0f,1f,infiniteRepeatable(tween((2600-appearance.liquidMotion.coerceIn(0f,1f)*900).toInt(),easing=LinearEasing)),label="wave")
    if (settingsOpen) {
        EarthquakeSettingsPage(onClose = { settingsOpen = false })
    } else if (monitorOpen) {
        SeismicLiveMapPage(onClose = { monitorOpen = false }, onSettings = { settingsOpen = true },
            reports = reports, onRefreshReports = { refresh() })
    } else Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(io.atmos.weather.ui.ThinnOpticalMaterial.page)) {
            Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.radialGradient(listOf(cyan.copy(alpha=.15f),Color.Transparent))))
            Column(Modifier.fillMaxSize().appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=28.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={haptics?.play(HapticEngine.Event.TAP);onClose()}) {Icon(Icons.Rounded.ArrowBack,"返回",tint=white)}
                    Column(Modifier.weight(1f)) {
                        Text("臺灣即時地震監測",fontSize=25.sp,fontWeight=FontWeight.Bold,color=white)
                        Text("thinn-khì · ExpTech 即時速報與防災引導",fontSize=14.sp,color=soft)
                    }
                    IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings,"地震設定",tint=cyan,modifier=Modifier.size(27.dp)) }
                }
                Spacer(Modifier.height(17.dp))
                SeismicGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(100.dp),contentAlignment=Alignment.Center) {
                            val ringInk=cyan
                            Canvas(Modifier.fillMaxSize()) {
                                val radius=size.minDimension/2f
                                for (i in 0..2) {
                                    val p=(progress + i/3f)%1f
                                    drawCircle(ringInk.copy(alpha=(1f-p)*.52f),radius=radius*(.20f+.78f*p),style=Stroke(width=2.dp.toPx()))
                                }
                                drawCircle(ringInk.copy(alpha=.36f),radius*.17f)
                            }
                            Icon(Icons.Rounded.Waves,null,tint=white,modifier=Modifier.size(27.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("監測資訊面板",color=white,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                            Text(if (loading) "正在查詢公開地震報告…" else "ExpTech 震後報告 · 免個人授權碼",color=cyan,fontSize=14.sp)
                            Text("ExpTech EEW：${realtime.eewStatus}",color=soft,fontSize=14.sp,lineHeight=16.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("ExpTech SSE 是第三方地震速報與測站即時資料；沒有官方保證的提前秒數。監測需要使用者開啟，離線、系統省電或網路異常時可能無法即時收到。",color=soft,fontSize=14.sp,lineHeight=19.sp)
                }
                Spacer(Modifier.height(13.dp))
                SeismicGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Map,null,tint=cyan,modifier=Modifier.size(31.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("第二頁 · 全臺即時監測地圖",color=white,fontSize=17.sp,fontWeight=FontWeight.Bold)
                            Text("EEW／RTS 狀態、測站震度、加速度、震央及歷史事件",color=soft,fontSize=14.sp,lineHeight=17.sp)
                        }
                        IconButton(onClick={monitorOpen=true;haptics?.play(HapticEngine.Event.TAP)}) {
                            Icon(Icons.Rounded.ChevronRight,"開啟地圖",tint=cyan)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Button(onClick={monitorOpen=true},modifier=Modifier.fillMaxWidth()) { Text("進入臺灣即時地震監測地圖") }
                }
                if (realtime.lastAlertAt > 0 && System.currentTimeMillis() - realtime.lastAlertAt < 90_000L) {
                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF8A311F),Color(0xFF53233C))))
                        .border(2.dp,orange.copy(alpha=.6f),RoundedCornerShape(26.dp))
                        .padding(18.dp)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
                            val white=Color.White
                            val orange=Color(0xFFFFD094)
                            Icon(Icons.Rounded.WarningAmber,null,tint=orange,modifier=Modifier.size(37.dp))
                            Text("收到即時地震速報",color=white,fontSize=20.sp,fontWeight=FontWeight.Black)
                            Text("趴下 · 掩護 · 穩住",color=white,fontSize=28.sp,fontWeight=FontWeight.Bold)
                            Text("保護頭頸部；不要為了看手機延誤避難",color=white.copy(alpha=.9f),fontSize=14.sp)
                            Text("第三方資料來源 · 非政府災防警報",color=orange,fontSize=14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                SeismicGlass {
                    Text("ExpTech · 秒級資料串流",color=white,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text("EEW 速報與 TREM-Net 測站 SSE，臺北／高雄節點自動備援。資料提供者為 ExpTech，非政府官方警報。",color=soft,fontSize=14.sp,lineHeight=18.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if(realtime.enabled) "即時監測已啟動" else "即時監測未啟動",color=if(realtime.enabled) cyan else soft,fontWeight=FontWeight.SemiBold)
                            Text("EEW：${realtime.eewStatus}",color=soft,fontSize=14.sp)
                            Text("測站：${realtime.rtsStatus}",color=soft,fontSize=14.sp)
                        }
                        Button(onClick={
                            runCatching {
                                if(realtime.enabled) ExpTechSeismicService.stop(context)
                                else ExpTechSeismicService.start(context,promoteLive=true)
                            }.onFailure { error="無法啟動背景監測：${it.localizedMessage}" }
                        }) {Text(if(realtime.enabled) "停止" else "啟動")}
                    }
                    if(realtime.lastRtsAt>0) {
                        Spacer(Modifier.height(8.dp))
                        Text("最近測站資料：${java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.TAIWAN).format(java.util.Date(realtime.lastRtsAt))}" +
                            (realtime.stationCount?.let { " · 取得 $it 個測站項目" } ?: " · 測站結構待確認"),color=cyan,fontSize=14.sp)
                    }
                    realtime.incident?.let { item ->
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color=white.copy(alpha=.15f))
                        Text(if(item.test) "測試來源事件（不觸發警報）" else if(item.cancelled) "已取消速報" else "最近接收到的 EEW 速報",color=orange,fontWeight=FontWeight.Bold)
                        Text(SeismicDisplayFormat.location(item.epicenter)+" · "+(item.magnitude?.let { "M ${String.format(java.util.Locale.US,"%.1f",it)}" } ?: "規模待確認"),color=white,fontSize=16.sp)
                        Text("來源 ${item.source} · 第 ${item.serial} 報 · ID ${item.id}",color=soft,fontSize=14.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("偵測到真實且時間有效的 EEW 時，新通知將播放專屬地震警示音並提示『趴下、掩護、穩住』。演練、取消、舊資料不發出真實警報。",color=soft,fontSize=14.sp,lineHeight=18.sp)
                    Text("必須允許通知；Android 靜音、勿擾、背景管理或服務故障仍可能讓警報延遲或漏收。",color=orange,fontSize=14.sp,lineHeight=17.sp)
                    TextButton(onClick={ SeismicSound.play(context,SeismicSound.Kind.EEW_PREVIEW) }) {
                        Icon(Icons.Rounded.PlayArrow,null,tint=cyan)
                        Text("試聽地震速報專屬音（非警報）",color=cyan,fontSize=14.sp)
                    }
                }
                Spacer(Modifier.height(13.dp))
                SeismicGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("震後報告監看（第三方彙整）",color=white,fontSize=17.sp,fontWeight=FontWeight.Bold)
                            Text("前景每 60 秒嘗試更新；網路與服務延遲不受保證",color=soft,fontSize=14.sp)
                        }
                        Switch(checked=autoWatch,onCheckedChange={autoWatch=it;prefs.edit().putBoolean("earthquake_foreground_watch",it).apply()})
                    }
                    Spacer(Modifier.height(9.dp))
                    LocalMotionTrace()
                    Spacer(Modifier.height(9.dp))
                    Text("公開 ExpTech 來源提供震後報告；ExpTech SSE 提供第三方即時測站與速報。本機波形僅為加速度示意。尚未提供可信的到達秒數預估。",color=soft,fontSize=14.sp,lineHeight=17.sp)
                }
                Spacer(Modifier.height(13.dp))
                SeismicGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("公開地震報告（無需授權碼）",color=white,fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.weight(1f))
                        TextButton(onClick = { refresh() }, enabled = !loading) {Icon(Icons.Rounded.Refresh,null,tint=cyan);Text("重新整理",color=cyan,fontSize=14.sp)}
                    }
                    Text(if(lastChecked!=null) "上次查詢：$lastChecked（臺灣時間，僅代表查詢時間）" else "資料由 ExpTech 公開 API 彙整；非即時速報",color=soft,fontSize=14.sp)
                    if(loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth().padding(vertical=12.dp),color=cyan)
                    if(error!=null) Text(error ?: "",color=orange,fontSize=14.sp,lineHeight=18.sp)
                    if(reports.isEmpty() && !loading) {
                        Spacer(Modifier.height(8.dp))
                        Text("目前未取得可顯示的地震報告；不代表沒有地震。",color=soft,fontSize=15.sp)
                    }
                    reports.firstOrNull()?.let { newest ->
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.primaryContainer))).padding(13.dp)) {
                            Column {
                                Text("最近一筆震後報告 · ${newest.category}",color=cyan,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                                Spacer(Modifier.height(5.dp))
                                Text("M ${String.format(java.util.Locale.US,"%.1f",newest.magnitude)}  ·  最大震度 ${newest.maxIntensity ?: "待確認"}",color=white,fontSize=19.sp,fontWeight=FontWeight.Bold)
                                Text(SeismicDisplayFormat.location(newest.location),color=soft,fontSize=15.sp)
                            }
                        }
                    }
                    reports.take(15).forEach { report ->
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color=Color.White.copy(alpha=.13f))
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF286A8A),Color(0xFF194059)))),contentAlignment=Alignment.Center) {
                                Text(String.format(java.util.Locale.US,"%.1f",report.magnitude),color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(SeismicDisplayFormat.location(report.location),color=white,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                val time=report.origin.atZone(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
                                Text("$time · ${report.category}",fontSize=14.sp,color=soft)
                                Text("深度 ${report.depthKm?.let { String.format(java.util.Locale.US,"%.1f",it) } ?: "—"} km  ·  最大震度 ${report.maxIntensity ?: "待確認"}",fontSize=14.sp,color=soft)
                            }
                        }
                        if (report.shakingAreas.isNotEmpty()) {
                            Text("各地震度：" + report.shakingAreas.take(5).joinToString("  ·  ") { "${it.first} ${it.second}" },
                                color=soft,fontSize=14.sp,lineHeight=17.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={uri.openUri(report.officialUrl)}) {
                                Text("中央氣象署地震資訊",color=cyan,fontSize=14.sp)
                                Icon(Icons.Rounded.OpenInNew,null,tint=cyan,modifier=Modifier.size(15.dp))
                            }
                            if (report.latitude != null && report.longitude != null) {
                                TextButton(onClick={
                                    uri.openUri("https://www.openstreetmap.org/?mlat=${report.latitude}&mlon=${report.longitude}#map=9/${report.latitude}/${report.longitude}")
                                }) {
                                    Icon(Icons.Rounded.Place,null,tint=cyan,modifier=Modifier.size(15.dp))
                                    Text("震央地圖",color=cyan,fontSize=14.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                SeismicGlass {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("專屬地震設定中心",color=white,fontSize=17.sp,fontWeight=FontWeight.Bold)
                            Text("一般 · 警報 · 音效 · 關於",color=soft,fontSize=14.sp)
                        }
                        Button(onClick={settingsOpen=true}) {
                            Icon(Icons.Rounded.Settings,null,modifier=Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("開啟設定")
                        }
                    }
                    Text("地震警示音獨立於原有八款天氣通知音；個別類型可試聽、控制聲音與通知。測站震度與 gal 自動警報尚待驗證。",color=soft,fontSize=14.sp,lineHeight=18.sp)
                }
                Spacer(Modifier.height(13.dp))
                SeismicGlass {
                    Text("防災行動指引",color=white,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text("""室內：立刻趴下、在堅固桌子下掩護頭頸、抓住桌腳穩住；切勿在搖晃中奔出室外。

戶外：遠離招牌、玻璃與電線，尋找空曠處保護頭部。

開車：平穩減速、靠安全路邊停車。

沿海：強烈或長時間搖晃停止後，留意官方海嘯警報，必要時前往高處。""",color=soft,fontSize=15.sp,lineHeight=20.sp)
                    Spacer(Modifier.height(7.dp))
                    TextButton(onClick={uri.openUri("https://scweb.cwa.gov.tw/zh-tw/earthquake/data")}) {Text("前往中央氣象署地震測報中心",color=cyan)}
                    Text("已獨立串接 ExpTech DPIP 公開 SSE 端點；不是 TREM-Lite 官方 Android 版本，也未複製其 AGPL 原始碼。商用或公開大量佈署前請先確認 API 使用條款。",color=soft,fontSize=14.sp,lineHeight=17.sp)
                    TextButton(onClick={uri.openUri("https://github.com/ExpTechTW/TREM-Lite")}) {Text("參考 TREM-Lite 開源專案",color=cyan)}
                }
                Spacer(Modifier.height(13.dp))
                SeismicGlass {
                    Text("地震防護演練 · 趴下／掩護／穩住",color=white,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("12 秒三階段全畫面動畫、原創提示音、觸覺回饋與可選中文語音。只有使用者主動開始演練時才會播放；絕不以假警報冒充真實地震。",color=soft,fontSize=14.sp,lineHeight=19.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        listOf("趴下","掩護","穩住").forEachIndexed { index, title ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(cyan.copy(alpha=.10f)).border(1.dp,cyan.copy(alpha=.2f),RoundedCornerShape(15.dp)).padding(vertical=14.dp),contentAlignment=Alignment.Center) {
                                Text("${index+1} $title",color=white,fontSize=16.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick={
                        simulationScenario=SeismicSimulation.scenario(System.currentTimeMillis())
                        simulationOpen=true
                        haptics?.play(HapticEngine.Event.TAP)
                    },modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.WarningAmber,null)
                        Spacer(Modifier.width(8.dp))
                        Text("體驗完整地震預警演練")
                    }
                    Text("模擬警示畫面、22 縣市震度、P/S 波動畫、演練音與語音。全程標示演練，不發送推播或真實警報。",
                        color=soft,fontSize=14.sp,lineHeight=19.sp,modifier=Modifier.padding(top=7.dp,bottom=12.dp))
                    Button(onClick={drillOpen=true;haptics?.play(HapticEngine.Event.TAP)},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("開始 12 秒防護演練")
                    }
                    Text("演練音效獨立播放；震後報告與即時速報可在專屬地震設定中心逐項控制，與八款一般天氣通知音分開。",color=soft,fontSize=14.sp,lineHeight=17.sp,modifier=Modifier.padding(top=9.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick={SeismicSound.play(context,SeismicSound.Kind.DRILL_START)}) { Text("試聽演練音",color=cyan) }
                        TextButton(onClick={SeismicSound.play(context,SeismicSound.Kind.COMPLETE)}) { Text("試聽結束音",color=cyan) }
                        TextButton(onClick={SeismicSound.stop()}) { Text("停止",color=soft) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            if (drillOpen) EarthquakeSafetyDrill(onClose={drillOpen=false})
            if (simulationOpen) simulationScenario?.let { scenario ->
                SeismicWarningOverlay(incident=scenario,replay=true,userCounty=SeismicUserLocation.current(context)?.name,
                    simulation=true,onClose={simulationOpen=false;simulationScenario=null})
            }
        }
    }
}

@Composable
private fun SeismicGlass(content:@Composable ColumnScope.()->Unit) {
    val style=LocalAppearance.current
    val alpha=(.08f + style.glassOpacity.coerceIn(0f,1f)*.17f).coerceIn(.08f,.34f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
        .background(Brush.verticalGradient(listOf(white.copy(alpha=alpha),white.copy(alpha=alpha*.54f))))
        .border(1.dp,white.copy(alpha=.14f+style.edgeLight.coerceIn(0f,1f)*.25f),RoundedCornerShape(27.dp))
        .padding(17.dp),content=content)
}
