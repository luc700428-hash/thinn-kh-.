package io.atmos.weather.earthquake

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.unit.dp

/** 第二個主分頁：直接打開地震監測地圖，不需要從天氣頁頂部按鈕進入。 */
@Composable
fun SeismicPrimaryTab(onExit:()->Unit) {
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<EarthquakeReport>>(emptyList()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reportSource by remember { mutableStateOf("中央氣象署官方報告") }
    fun refresh() {
        if(loading)return
        loading=true
        scope.launch {
            try {
                val official=try { CwaEarthquakeRepository.latest(ctx) }
                    catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){null}
                if(!official.isNullOrEmpty()) {
                    reports=official;reportSource="中央氣象署官方震後報告";error=null
                } else {
                    reports=ExpTechPublicEarthquakeReports.latest()
                    reportSource="ExpTech 第三方彙整報告";error=null
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){error="地震報告暫時無法更新，請稍後重試"}
            finally {loading=false}
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
    io.atmos.weather.ui.AdaptivePageFrame(maxContentWidth=1280.dp) {
    SeismicLiveMapPage(
        onClose=onExit,
        onSettings={settingsOpen=true},
        reports=reports, reportSource=reportSource,reportsLoading=loading,reportError=error,
        onRefreshReports={refresh()})
    if(settingsOpen) EarthquakeSettingsPage(onClose={settingsOpen=false})
    }
}
