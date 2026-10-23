package io.atmos.weather.update

import io.atmos.weather.ui.GlassDivider as HorizontalDivider
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassIconButton as IconButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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

private val UpdateText = Color(0xFF17202B)
private val UpdateSoft = Color(0xFF697282)
private val UpdateBlue = Color(0xFF087FF4)
private val UpdateCardFill = Color.White
private val UpdateCardBorder = Color(0xFFE1E5EB)

@Composable
fun AppUpdatePage(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticEngine.current
    val appearance = LocalAppearance.current
    val reduceMotion = context.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var pending by remember { mutableStateOf<ReleaseInfo?>(null) }
    var pendingDelta by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("版本與更新中心已開啟") }
    var downloaded by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableStateOf<Long?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var installerPrompted by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) { check() }

    val light = if(reduceMotion) .12f else {
        val glow = rememberInfiniteTransition(label = "update glow")
        val animated by glow.animateFloat(
            initialValue = .12f,targetValue = .28f,
            animationSpec = infiniteRepeatable(
                animation = tween((3800 - appearance.liquidMotion.coerceIn(0f, 1f) * 2600).toInt(), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "edge")
        animated
    }

    // Content-first iOS-inspired update layout: actual signed release data only.
    // No fake install / completion state; AppUpdater above performs certificate and SHA checks.
    val updatePrefs=remember(context) { context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE) }
    var automaticChecks by remember { mutableStateOf(updatePrefs.getBoolean("check_updates_automatically",true)) }
    var showBuildDetails by remember { mutableStateOf(false) }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFFF5F6FA),Color(0xFFEDF0F6),Color(0xFFF9FAFD))))
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal=18.dp,vertical=12.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color.White).clickable {onClose()},contentAlignment=Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack,"返回",tint=UpdateText)
                }
                Text("軟體更新",color=UpdateText,fontWeight=FontWeight.SemiBold,fontSize=19.sp,
                    modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(44.dp))
            }
            Spacer(Modifier.height(22.dp))
            UpdatePanel(glow=light) {
                Row(Modifier.fillMaxWidth().heightIn(min=45.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("自動檢查更新",color=UpdateText,fontSize=16.sp,modifier=Modifier.weight(1f))
                    Text(if(automaticChecks) "開啟" else "關閉",color=UpdateSoft,fontSize=15.sp,
                        modifier=Modifier.clickable {
                            automaticChecks=!automaticChecks
                            updatePrefs.edit().putBoolean("check_updates_automatically",automaticChecks).apply()
                            UpdateCheckWorker.schedule(context)
                        }.padding(10.dp))
                    Icon(Icons.Rounded.ChevronRight,null,tint=UpdateSoft)
                }
                HorizontalDivider(color=UpdateCardBorder)
                Row(Modifier.fillMaxWidth().heightIn(min=45.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Beta 版更新項目",color=UpdateText,fontSize=16.sp,modifier=Modifier.weight(1f))
                    Text(if(BuildConfig.THINN_OTA_CHANNEL=="preview") "測試通道" else "關閉",
                        color=UpdateSoft,fontSize=15.sp)
                }
            }
            Spacer(Modifier.height(15.dp))
            UpdatePanel(glow=light) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(57.dp).clip(RoundedCornerShape(13.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF41B5FF),Color(0xFF0873D4)))),
                        contentAlignment=Alignment.Center) {
                        Icon(Icons.Rounded.Cyclone,"thinn-khì",tint=Color.White,modifier=Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("thinn-khì ${pending?.version ?: BuildConfig.VERSION_NAME}",color=UpdateText,
                            fontSize=19.sp,fontWeight=FontWeight.Bold)
                        Text(if(pending==null) "目前已安裝版本" else "已驗證新版資訊",
                            color=UpdateSoft,fontSize=14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(if(showBuildDetails) "隱藏版本詳情 ↑" else "版本詳情 ↓",
                    color=UpdateBlue,fontSize=14.sp,
                    modifier=Modifier.clickable { showBuildDetails=!showBuildDetails }
                        .padding(vertical=8.dp))
                if(showBuildDetails) {
                    // Display real CI provenance without claiming it proves OTA installation.
                    Text("來源提交：${BuildConfig.THINN_BUILD_SHA.take(10)}",
                        color=UpdateSoft,fontSize=14.sp)
                    Text("安裝仍需原簽章與下載檔驗證。",color=UpdateSoft,fontSize=14.sp)
                }
                Spacer(Modifier.height(18.dp))
                if(pending != null) {
                    Text("版本更新內容",color=UpdateText,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    UpdateReleaseNotes(pendingDelta.ifBlank {"正在讀取官方發布的更新說明…"})
                } else {
                    Text("目前沒有可安裝的新版本。你可以重新檢查最新正式發布。",
                        color=UpdateSoft,fontSize=15.sp,lineHeight=22.sp)
                }
                if(lastError != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("更新檢查失敗：${lastError ?: "網路暫時無法使用"}",color=Color(0xFFAF2F3C),fontSize=14.sp)
                }
                if(checking || installing) {
                    Spacer(Modifier.height(12.dp))
                    Text(status,color=UpdateSoft,fontSize=14.sp)
                    totalBytes?.takeIf {it>0L}?.let {total ->
                        val percent=((downloaded.toFloat()/total)*100).toInt().coerceIn(0,100)
                        Text("已下載 $percent%",color=UpdateSoft,fontSize=14.sp)
                    }
                }
                if(installerPrompted) {
                    Spacer(Modifier.height(12.dp))
                    Text("已開啟 Android 系統安裝確認；尚未確認完成安裝。",color=UpdateSoft,fontSize=14.sp)
                }
                Spacer(Modifier.height(19.dp))
                HorizontalDivider(color=UpdateCardBorder)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if(installing || checking) Color(0xFF8CA9CB) else UpdateBlue)
                    .clickable(enabled=!installing && !checking) {
                        if(pending==null) check() else pending?.let {downloadOfficial(it)}
                    },contentAlignment=Alignment.Center) {
                    Text(when {
                        checking -> "正在檢查…"
                        installing -> "正在下載…"
                        pending!=null -> "立即更新"
                        else -> "重新檢查"
                    },color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                }
                if(installing) {
                    Text("取消下載",color=UpdateBlue,fontSize=15.sp,
                        modifier=Modifier.fillMaxWidth().clickable {downloadJob?.cancel();status="正在取消下載…"}
                            .padding(15.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("更新下載後須由 Android 系統及使用者確認安裝；暫不提供無法保證執行的『今晚自動安裝』功能。",
                color=UpdateSoft,fontSize=14.sp,lineHeight=20.sp)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun UpdatePanel(glow: Float, content: @Composable ColumnScope.() -> Unit) {
    val context=LocalContext.current
    val opaque=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_transparency",false)
    val shape=RoundedCornerShape(30.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(UpdateCardFill)
        .border(1.dp,UpdateCardBorder,shape)
        .padding(20.dp),content=content)
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
