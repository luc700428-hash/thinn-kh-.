package io.atmos.weather.update
import io.atmos.weather.ui.appleBouncingScroll

import io.atmos.weather.ui.GlassDivider as HorizontalDivider
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassIconButton as IconButton
import androidx.compose.animation.AnimatedVisibility
import io.atmos.weather.ui.AppleMotion
import io.atmos.weather.ui.AppleGlassCard
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

private val UpdateText = Color(0xFF101114)
private val UpdateSoft = Color(0xFF777982)
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

    fun check() {
        if (checking || installing) return
        checking = true
        pending = null
        pendingDelta = ""
        lastError = null
        installerPrompted = false
        status = if (BuildConfig.THINN_OTA_CHANNEL == "preview") "正在檢查 V25 測試更新…"
        else "正在檢查最新正式版本…"
        haptic?.play(HapticEngine.Event.TAP)
        scope.launch {
            runCatching { AppUpdater.findUpdate(context) }
                .onSuccess {
                    pending = it
                    if (it == null) {
                        status = if (BuildConfig.THINN_OTA_CHANNEL == "preview") "目前沒有較新的測試更新"
                            else "目前沒有偵測到較新的正式版本"
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
        installing=true
        installerPrompted=false
        downloaded=0L
        totalBytes=null
        lastError=null
        downloadJob=scope.launch {
            val result=runCatching {
                AppUpdater.downloadAndInstall(context,release) { soFar,total ->
                    downloaded=soFar
                    totalBytes=total
                }
            }
            result.onSuccess {
                // Android owns the confirmation; do not claim installed.
                status="已交由 Android 安裝程式，請完成系統確認。"
                installerPrompted=true
            }.onFailure {err ->
                if(err is CancellationException) status="已取消下載"
                else {lastError=err.localizedMessage ?: "請稍後重試";status="更新失敗"}
            }
            installing=false
            downloadJob=null
        }
    }

    // Do not reset a live download when the user navigates back to this page.
    LaunchedEffect(Unit) { if(!installing && pending==null && !installerPrompted) check() }

    val light = if(reduceMotion) .12f else {
        val glow = rememberInfiniteTransition(label = "update glow")
        val animated by glow.animateFloat(
            initialValue = .12f,targetValue = .28f,
            animationSpec = infiniteRepeatable(
                animation = tween((3800 - appearance.liquidMotion.coerceIn(0f, 1f) * 2600).toInt(), easing = AppleMotion.fluid),
                repeatMode = RepeatMode.Reverse
            ), label = "edge")
        animated
    }

    // V25 presents a content-first update center: calm surfaces, lightweight controls.
    // OTA state and package validation remain owned by AppUpdater above.
    var showBuildDetails by remember { mutableStateOf(false) }
    val prefs=remember(context) {context.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE)}
    var automatic by remember {mutableStateOf(prefs.getBoolean("check_updates_automatically",true))}
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) {
            // Ambient light stays behind content rather than obscuring update text.
            Box(Modifier.fillMaxWidth().height(290.dp).background(
                Brush.radialGradient(listOf(UpdateBlue.copy(alpha=light*.08f),Color.Transparent))
            ))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .appleBouncingScroll().verticalScroll(rememberScrollState())
                .padding(horizontal=24.dp, vertical=18.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    io.atmos.weather.ui.IOS27RoundControl("‹",onClick={
                        haptic?.play(HapticEngine.Event.TAP);onClose()
                    },fill=Color.White,foreground=UpdateText)
                    Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                        Text("軟體更新",color=UpdateText,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(42.dp))
                }
                Spacer(Modifier.height(16.dp))
                UpdatePanel(glow=light) {
                    ApplePullDownMenu(label="自動檢查更新",choices=listOf("開啟", "關閉"),
                        selected=if(automatic) "開啟" else "關閉",onChoose={ choice ->
                            automatic=choice=="開啟"
                            prefs.edit().putBoolean("check_updates_automatically",automatic).apply()
                            io.atmos.weather.update.UpdateCheckWorker.schedule(context)
                        })
                    Spacer(Modifier.height(12.dp))
                    Text("更新通道："+(if(BuildConfig.THINN_OTA_CHANNEL=="preview") "測試版" else "正式版"),
                        color=UpdateSoft,fontSize=15.sp)
                }
                Spacer(Modifier.height(20.dp))

                UpdatePanel(glow=light) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp))
                            .background(Brush.verticalGradient(listOf(
                                Color(0xA5BEE8FA),Color(0x665E91B1),Color(0x863C6688))))
                            .border(1.dp,Color.White.copy(alpha=.32f),RoundedCornerShape(22.dp)),
                            contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.SystemUpdate,"已安裝版本",tint=Color.White,
                                modifier=Modifier.size(31.dp))
                        }
                        Spacer(Modifier.width(17.dp))
                        Column(Modifier.weight(1f)) {
                            Text("目前版本",color=UpdateSoft,fontSize=14.sp)
                            Text("V${BuildConfig.VERSION_NAME.substringBefore('.')}",color=UpdateText,
                                fontSize=33.sp,fontWeight=FontWeight.Bold)
                            Text(BuildConfig.VERSION_NAME,color=UpdateSoft,fontSize=15.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle,"已安裝",tint=Color(0xFF34C759),
                            modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("此版本已安裝在你的手機",color=UpdateSoft,fontSize=14.sp)
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(if(showBuildDetails) "收起版本詳情  ↑" else "版本詳情  ↓",
                        color=UpdateSoft,fontSize=14.sp,
                        modifier=Modifier.clickable {
                            haptic?.play(HapticEngine.Event.TAP)
                            showBuildDetails = !showBuildDetails
                        })
                    if(showBuildDetails) {
                        Spacer(Modifier.height(8.dp))
                        Text("建置識別碼 · ${BuildConfig.THINN_BUILD_SHA.take(10)}",
                            color=UpdateSoft,fontSize=14.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Existing download and signature verification remain unchanged.
                val otaState:NextOtaState = when {
                    checking -> NextOtaState.Checking
                    installing -> NextOtaState.Downloading(
                        totalBytes?.takeIf {it>0L}?.let { (downloaded.toFloat()/it).coerceIn(0f,1f) } ?: 0f)
                    lastError != null -> NextOtaState.Failed(lastError ?: "更新失敗")
                    installerPrompted -> NextOtaState.Installing
                    pending != null -> NextOtaState.Available(
                        pending?.version ?: "新版",
                        pendingDelta.lineSequence().map {it.trim()}
                            .filter {it.startsWith("- ") || it.startsWith("* ")}
                            .take(12).map {it.drop(2)}.toList())
                    else -> NextOtaState.Idle
                }
                MaterialTheme(colorScheme=lightColorScheme(
                    background=Color(0xFFF2F2F7), surface=Color.White,
                    onSurface=Color(0xFF1C1C1E),onSurfaceVariant=Color(0xFF777982))) {
                NextOtaStatusCard(
                    state=otaState,
                    onCheck={check()},
                    onDownload={pending?.let {downloadOfficial(it)}},
                    onInstall={pending?.let {downloadOfficial(it)}},
                    reduceTransparency=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
                        .getBoolean("reduce_transparency",false)
                )
                }
                if(installerPrompted || (!checking && pending==null && lastError==null &&
                    status != "版本與更新中心已開啟")) {
                    Spacer(Modifier.height(12.dp))
                    Text(status,color=UpdateSoft,fontSize=14.sp)
                }
                pending?.let {
                    Spacer(Modifier.height(18.dp))
                    UpdatePanel(glow=light) {
                        Text("這次更新帶來什麼",color=UpdateText,fontSize=18.sp,
                            fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(11.dp))
                        UpdateReleaseNotes(pendingDelta.ifBlank {"正在載入版本內容…"})
                    }
                }
                if(installing) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick={downloadJob?.cancel();status="正在取消下載…"},
                        modifier=Modifier.fillMaxWidth()) { Text("取消下載") }
                }
                Spacer(Modifier.height(22.dp))
                Text("系統更新安裝前會進行安全驗證，並由手機確認更新。",
                    color=UpdateSoft.copy(alpha=.88f),fontSize=14.sp,lineHeight=20.sp)
                Spacer(Modifier.height(15.dp))
            }
        }
    }

}

@Composable
private fun UpdatePanel(glow: Float, content: @Composable ColumnScope.() -> Unit) {
    val context=LocalContext.current
    val opaque=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_transparency",false)
    AppleGlassCard(modifier=Modifier.fillMaxWidth(),radius=24.dp,
        contentPadding=PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp),content=content)
    }
}

/** Format the exact bundled/GitHub Release markdown consistently with thinn-khì glass UI. */
@Composable
private fun UpdateReleaseNotes(markdown: String) {
    val lines = remember(markdown) { markdown.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList() }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { line ->
            when {
                line.startsWith("# ") -> Text(
                    line.removePrefix("# ").replace("**", ""),
                    color = UpdateText, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 25.sp
                )
                line.startsWith("## ") -> {
                    Spacer(Modifier.height(9.dp))
                    Text(line.removePrefix("## ").replace("**", ""),
                        color = UpdateBlue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
                }
                line.startsWith("### ") -> {
                    Spacer(Modifier.height(5.dp))
                    Text(line.removePrefix("### ").replace("**", ""),
                        color = UpdateText, fontWeight = FontWeight.SemiBold, fontSize=16.sp, lineHeight = 21.sp)
                }
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = UpdateBlue, fontSize=16.sp, lineHeight = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(line.drop(2).replace("**", ""), color = UpdateSoft,
                        fontSize=15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                }
                else -> Text(line.replace("**", ""), color = UpdateSoft, fontSize=15.sp, lineHeight = 20.sp)
            }
        }
    }
}
