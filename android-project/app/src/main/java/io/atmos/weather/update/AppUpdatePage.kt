package io.atmos.weather.update
import io.atmos.weather.ui.appleBouncingScroll

import io.atmos.weather.ui.GlassDivider as HorizontalDivider
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassIconButton as IconButton
import androidx.compose.animation.AnimatedVisibility
import io.atmos.weather.ui.AppleMotion
import io.atmos.weather.ui.ApplePullDownMenu
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.next.NextOtaState
import io.atmos.weather.next.NextOtaStatusCard
import io.atmos.weather.BuildConfig
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import io.atmos.weather.presentation.home.LocalAppearance
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private val UpdateText:Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val UpdateSoft:Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val UpdateBlue = Color(0xFF007AFF)
private val UpdateCardFill = Color(0xFF88A7C5).copy(alpha = .22f)
private val UpdateCardBorder = Color.White.copy(alpha = .28f)

@Composable
fun AppUpdatePage(onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val haptic = LocalHapticEngine.current
    val appearance = LocalAppearance.current
    val reduceMotion = context.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    // A scope owned by the app process instead of Compose: closing this Dialog does
    // not cancel an in-progress OTA transfer. MutableState mirrors the active task
    // when the page is reopened. The OS may still stop the app process; see README.
    OtaRetainedSession.restore(context)
    val scope = OtaRetainedSession.scope
    var checking by remember { mutableStateOf(false) }
    var installing by OtaRetainedSession.installing
    var downloadJob by OtaRetainedSession.downloadJob
    var pending by OtaRetainedSession.pending
    var pendingDelta by OtaRetainedSession.pendingDelta
    var status by OtaRetainedSession.status
    var downloaded by OtaRetainedSession.downloaded
    var totalBytes by OtaRetainedSession.totalBytes
    var lastError by OtaRetainedSession.lastError
    var installerPrompted by OtaRetainedSession.installerPrompted
    var ready by OtaRetainedSession.ready
    var checkedSuccessfully by OtaRetainedSession.checkedSuccessfully
    var latestChecked by OtaRetainedSession.latestChecked

    fun check() {
        if (checking || installing) return
        ready=false
        checking = true
        checkedSuccessfully=false
        pending = null
        pendingDelta = ""
        lastError = null
        installerPrompted = false
        status = if (BuildConfig.THINN_OTA_CHANNEL == "preview") "正在檢查 V25 測試更新…"
        else "正在檢查最新正式版本…"
        haptic?.play(HapticEngine.Event.TAP)
        scope.launch {
            runCatching { AppUpdater.findLatestRelease(context) }
                .onSuccess { latest ->
                    latestChecked=latest;checkedSuccessfully=true
                    val it=latest.takeIf {candidate ->AppUpdater.isNewerThanInstalled(context,candidate)}
                    pending = it
                    OtaRetainedSession.persist(context)
                    if (it == null) {
                        status = if (BuildConfig.THINN_OTA_CHANNEL == "preview") "目前沒有較新的測試更新"
                            else "你的軟體已是最新版本"
                    } else {
                        status = "有可安裝的新版"
                        haptic?.play(HapticEngine.Event.RELEASE)
                        val release=it
                        scope.launch {
                            pendingDelta = AppUpdateDelta.describe(BuildConfig.VERSION_NAME, release)
                        }
                    }
                }
                .onFailure {
                    val message = it.localizedMessage?.takeIf(String::isNotBlank) ?: "網路或 Release 暫時不可用"
                    lastError = message
                    status = "檢查失敗"
                }
            checking = false
        }
    }

    // A real download path shared by the new NEXT button and legacy cancel action.
    // Installation success is intentionally NOT inferred from an installer handoff.
    fun downloadOfficial(release:ReleaseInfo) {
        if(installing || checking) return
        haptic?.play(HapticEngine.Event.TAP)
        runCatching { OtaDownloadService.start(context,release) }.onFailure {
            installing=false
            lastError=it.localizedMessage ?: "無法啟動下載，請稍後重試"
        }
    }
    fun installOfficial(release:ReleaseInfo) {
        if(installing || checking) return
        lastError=null
        downloadJob=scope.launch {
            installing=true
            try {
                // Verified cached APK is reused, and rechecked before each handoff.
                AppUpdater.downloadAndInstall(context,release)
                status="已交由 Android 安裝程式，請完成系統確認。"
                installerPrompted=true
            } catch(error:Exception) {
                if(!AppUpdater.downloadFile(context,release).isFile) ready=false
                lastError=error.localizedMessage ?: "無法開啟安裝程式"
            } finally { installing=false; downloadJob=null }
        }
    }

    // Do not reset a live download when the user navigates back to this page.
    LaunchedEffect(Unit) { if(!installing && pending==null && !installerPrompted) check() }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val cleared=OtaRetainedSession.reconcileInstalled(context)
                if(cleared) {if(!installing)check()}
            }
        }
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(pending?.sha256) {
        pending?.let { release ->
            pendingDelta=AppUpdateDelta.describe(BuildConfig.VERSION_NAME,release)
        }
    }

    // This scope owns only page dismissal; OTA jobs remain in OtaRetainedSession.
    val uiScope=rememberCoroutineScope()
    var pageShown by remember {mutableStateOf(false)}
    var closingPage by remember {mutableStateOf(false)}
    LaunchedEffect(Unit){pageShown=true}
    val pageProgress by androidx.compose.animation.core.animateFloatAsState(
        if(pageShown)1f else 0f,
        tween(if(reduceMotion)0 else if(closingPage)260 else 320,easing=AppleMotion.snap),
        label="software-update-navigation")
    fun closeUpdatePage() {
        if(closingPage)return
        closingPage=true;pageShown=false
        uiScope.launch {if(!reduceMotion)kotlinx.coroutines.delay(260);onClose()}
    }
    var reminderStatus by remember {mutableStateOf("")}
    var showBuildDetails by remember { mutableStateOf(false) }
    val prefs=remember(context) {context.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE)}
    var automatic by remember {mutableStateOf(prefs.getBoolean("check_updates_automatically",true))}
    val otaState:NextOtaState=when {
        checking->NextOtaState.Checking
        installing && status.contains("驗證")->NextOtaState.Verifying
        installing->NextOtaState.Downloading(
            totalBytes?.takeIf {it>0L}?.let {(downloaded.toFloat()/it).coerceIn(0f,1f)} ?: 0f,
            indeterminate=totalBytes?.let {it>0L}!=true)
        lastError!=null->NextOtaState.Failed(lastError ?: "更新失敗")
        ready && !installerPrompted->NextOtaState.ReadyToInstall
        installerPrompted->NextOtaState.Installing
        pending!=null->NextOtaState.Available(pending!!.version,emptyList())
        checkedSuccessfully->NextOtaState.UpToDate
        else->NextOtaState.Idle
    }
    Dialog(onDismissRequest={closeUpdatePage()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val backdrop=io.atmos.weather.ui.rememberThinnBackdrop()
        CompositionLocalProvider(io.atmos.weather.ui.LocalThinnBackdrop provides backdrop) {
            val palette=MaterialTheme.colorScheme
            Box(Modifier.fillMaxSize().graphicsLayer {translationX=(1f-pageProgress)*size.width}) {
            io.atmos.weather.ui.ThinnBackdropCapture(backdrop,Modifier.matchParentSize()) {
                Box(Modifier.matchParentSize().background(palette.background))
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=16.dp),contentAlignment=Alignment.Center) {
                    io.atmos.weather.ui.IOS27RoundControl("‹",onClick={closeUpdatePage()},
                        modifier=Modifier.align(Alignment.CenterStart),fill=palette.surface,foreground=palette.onSurface)
                    Text("軟體更新",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
                }
                Column(Modifier.fillMaxSize().appleBouncingScroll().verticalScroll(rememberScrollState())
                    .padding(horizontal=16.dp)) {
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(palette.surface)) {
                        ApplePullDownMenu(label="自動更新",choices=listOf("開啟","關閉"),
                            selected=if(automatic)"開啟" else "關閉",onChoose={choice ->
                                automatic=choice=="開啟"
                                prefs.edit().putBoolean("check_updates_automatically",automatic).apply()
                                UpdateCheckWorker.schedule(context)
                            },modifier=Modifier.padding(horizontal=16.dp))
                        Box(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp).height(.5.dp)
                            .background(palette.onSurface.copy(alpha=.10f)))
                        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).padding(horizontal=16.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Text("更新通道",fontSize=17.sp,color=palette.onSurface,modifier=Modifier.weight(1f))
                            Text(if(BuildConfig.THINN_OTA_CHANNEL=="preview")"測試版" else "正式版",
                                fontSize=17.sp,color=palette.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    NextOtaStatusCard(state=otaState,
                        onCheck={check()},onDownload={pending?.let {downloadOfficial(it)}},
                        onInstall={pending?.let {installOfficial(it)}},
                        version=pending?.version ?: BuildConfig.VERSION_NAME,
                        sizeBytes=pending?.sizeBytes ?: latestChecked?.sizeBytes,releaseNotes=if(pending!=null)pendingDelta else "",
                        status=status,
                        hasPending=pending!=null,resumeAvailable=downloaded>0L,
                        onTonight=if(pending!=null && !installing && !installerPrompted) ({
                            pending?.let {release ->
                                reminderStatus=runCatching {UpdateReminderWorker.scheduleTonight(context,release.version)}
                                    .fold({"將於 "+it+" 提醒更新；安裝時仍需系統確認。"},
                                        {it.localizedMessage ?: "目前無法安排通知"})
                            };Unit
                        }) else null,
                        onPause={OtaDownloadService.cancel(context);downloadJob?.cancel();status="正在暫停下載…"},
                        reduceTransparency=prefs.getBoolean("reduce_transparency",false))
                    Spacer(Modifier.height(9.dp))
                    Text(if(reminderStatus.isNotBlank())reminderStatus else
                        "選擇「今晚更新」後，將在今晚提醒你安裝。Android 安裝時仍需由你確認。",
                        fontSize=12.sp,lineHeight=17.sp,color=palette.onSurfaceVariant,
                        modifier=Modifier.padding(horizontal=16.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(if(showBuildDetails)"收起版本詳情" else "版本詳情",
                        color=palette.primary,fontSize=14.sp,modifier=Modifier
                            .clickable {showBuildDetails=!showBuildDetails}.padding(16.dp))
                    if(showBuildDetails) {
                        Text("目前版本 "+BuildConfig.VERSION_NAME+"\n建置識別碼 · "+BuildConfig.THINN_BUILD_SHA.take(10),
                            color=palette.onSurfaceVariant,fontSize=12.sp,lineHeight=18.sp,
                            modifier=Modifier.padding(horizontal=16.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            }
        }
    }
}
