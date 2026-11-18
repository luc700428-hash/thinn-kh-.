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
    // Paging: older reports load as the list is scrolled to its end (see SeismicReportPaging).
    var stage by remember { mutableStateOf(SeismicReportPaging.Stage.OFFICIAL) }
    var expTechLimit by remember { mutableIntStateOf(30) }
    var olderLoading by remember { mutableStateOf(false) }
    var olderError by remember { mutableStateOf(false) }
    var olderFromExpTech by remember { mutableStateOf(false) }
    fun refresh() {
        if(loading)return
        loading=true
        scope.launch {
            try {
                val official=try { CwaEarthquakeRepository.latest(ctx) }
                    catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){null}
                if(!official.isNullOrEmpty()) {
                    // Keep older pages already loaded below the newest reports.
                    reports=SeismicReportPaging.merge(official,reports)
                    if(!olderFromExpTech)reportSource="中央氣象署官方震後報告";error=null
                } else {
                    reports=SeismicReportPaging.merge(ExpTechPublicEarthquakeReports.latest(expTechLimit),reports)
                    if(stage==SeismicReportPaging.Stage.OFFICIAL)stage=SeismicReportPaging.Stage.EXPTECH
                    reportSource="ExpTech 第三方彙整報告";error=null
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){error="地震報告暫時無法更新，請稍後重試"}
            finally {loading=false}
        }
    }
    fun loadOlder() {
        if(loading||olderLoading||stage==SeismicReportPaging.Stage.DONE||reports.isEmpty())return
        olderLoading=true;olderError=false
        scope.launch {
            try {
                val before=reports.size
                if(stage==SeismicReportPaging.Stage.OFFICIAL) {
                    val more=CwaEarthquakeRepository.olderThan(ctx,reports.minOf{it.origin})
                    reports=SeismicReportPaging.merge(reports,more)
                    stage=SeismicReportPaging.next(stage,reports.size-before,expTechLimit)
                } else {
                    expTechLimit=(expTechLimit+SeismicReportPaging.EXPTECH_STEP).coerceAtMost(SeismicReportPaging.EXPTECH_MAX)
                    reports=SeismicReportPaging.merge(reports,ExpTechPublicEarthquakeReports.latest(expTechLimit))
                    if(reports.size>before&&reportSource.startsWith("中央氣象署")){
                        olderFromExpTech=true;reportSource="中央氣象署官方報告 · 較早的由 ExpTech 彙整"
                    }
                    stage=SeismicReportPaging.next(stage,reports.size-before,expTechLimit)
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){olderError=true}
            finally {olderLoading=false}
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
        onRefreshReports={refresh()},
        olderReports=SeismicOlderReports(olderLoading,olderError,stage==SeismicReportPaging.Stage.DONE,{loadOlder()}))
    if(settingsOpen) EarthquakeSettingsPage(onClose={settingsOpen=false})
    }
}
