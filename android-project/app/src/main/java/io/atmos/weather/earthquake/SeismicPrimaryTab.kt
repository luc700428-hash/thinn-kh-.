package io.atmos.weather.earthquake

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** 第二個主分頁：直接打開地震監測地圖，不需要從天氣頁頂部按鈕進入。 */
@Composable
fun SeismicPrimaryTab(onExit:()->Unit) {
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<EarthquakeReport>>(emptyList()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reportSource by remember { mutableStateOf("中央氣象署官方報告") }
    fun refresh() {
        scope.launch {
            val official = runCatching { CwaEarthquakeRepository.latest(ctx) }.getOrNull()
            if(!official.isNullOrEmpty()) {
                reports=official;reportSource="中央氣象署官方震後報告";error=null
            } else {
                runCatching { ExpTechPublicEarthquakeReports.latest() }
                    .onSuccess { reports=it;reportSource="ExpTech 第三方彙整報告";error=null }
                    .onFailure { error="地震報告暫不可用：${it.message.orEmpty()}" }
            }
        }
    }
    LaunchedEffect(Unit) {
        refresh()
        val pref=ctx.getSharedPreferences("thinn_seismic_v15",android.content.Context.MODE_PRIVATE)
        if(pref.getBoolean("auto_start_realtime_on_app_open",true) && !ExpTechSeismicService.live.value.enabled) {
            runCatching { ExpTechSeismicService.start(ctx) }
                .onFailure { error="背景監測啟動受限制，請檢查通知及省電權限" }
        }
    }
    if(settingsOpen) EarthquakeSettingsPage(onClose={settingsOpen=false})
    else SeismicLiveMapPage(
        onClose=onExit,
        onSettings={settingsOpen=true},
        reports=reports, reportSource=reportSource,
        onRefreshReports={refresh()})
}
