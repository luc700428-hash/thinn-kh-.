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
    // Paging: older reports load as the list is scrolled to its end.
    var cursor by remember { mutableStateOf<CwaEarthquakeRepository.Cursor?>(null) }
    var expTechLimit by remember { mutableIntStateOf(30) }
    var olderLoading by remember { mutableStateOf(false) }
    var olderError by remember { mutableStateOf(false) }
    var allLoaded by remember { mutableStateOf(false) }
    fun refresh() {
        if(loading)return
        loading=true
        scope.launch {
            try {
                val official=try { CwaEarthquakeRepository.page(ctx,CwaEarthquakeRepository.start) }
                    catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){null}
                if(official!=null&&official.first.isNotEmpty()) {
                    // Keep pages already loaded below the newest ones.
                    reports=(official.first+reports.filter{old->official.first.none{it.id==old.id}}).sortedByDescending{it.origin}
                    if(cursor==null){cursor=official.second;allLoaded=official.second.done}
                    reportSource="中央氣象署官方震後報告";error=null
                } else {
                    reports=ExpTechPublicEarthquakeReports.latest(expTechLimit)
                    cursor=null;reportSource="ExpTech 第三方彙整報告";error=null
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){error="地震報告暫時無法更新，請稍後重試"}
            finally {loading=false}
        }
    }
    fun loadOlder() {
        if(loading||olderLoading||allLoaded)return
        olderLoading=true;olderError=false
        scope.launch {
            try {
                val c=cursor
                if(c!=null) {
                    val (more,next)=CwaEarthquakeRepository.page(ctx,c)
                    val before=reports.size
                    reports=(reports+more).distinctBy{it.id}.sortedByDescending{it.origin}
                    cursor=next;allLoaded=next.done||reports.size==before&&more.isEmpty()
                } else {
                    expTechLimit=(expTechLimit+30).coerceAtMost(300)
                    val more=ExpTechPublicEarthquakeReports.latest(expTechLimit)
                    val before=reports.size
                    reports=(reports+more).distinctBy{it.id}.sortedByDescending{it.origin}
                    allLoaded=reports.size==before||expTechLimit>=300
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
        olderReports=SeismicOlderReports(olderLoading,olderError,allLoaded,{loadOlder()}))
    if(settingsOpen) EarthquakeSettingsPage(onClose={settingsOpen=false})
    }
}
