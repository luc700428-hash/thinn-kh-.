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
import io.atmos.weather.BuildConfig
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import io.atmos.weather.presentation.home.LocalAppearance
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private val UpdateText = Color.White
private val UpdateSoft = Color.White.copy(alpha = .76f)
private val UpdateBlue = Color(0xFF8EDFFF)
private val UpdateCardFill = Color(0xFF88A7C5).copy(alpha = .22f)
private val UpdateCardBorder = Color.White.copy(alpha = .28f)

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

    fun check() {
        if (checking || installing) return
        checking = true
        pending = null
        pendingDelta = ""
        lastError = null
        status = "正在檢查最新正式版本…"
        haptic?.play(HapticEngine.Event.TAP)
        scope.launch {
            runCatching { AppUpdater.findUpdate(context) }
                .onSuccess {
                    pending = it
                    if (it == null) {
                        status = "目前沒有偵測到較新的正式版本"
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

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFF091C33), Color(0xFF0A3357), Color(0xFF052C49))
                )
            )
        ) {
            Box(
                Modifier.fillMaxWidth().height(260.dp).background(
                    Brush.radialGradient(
                        listOf(UpdateBlue.copy(alpha = light), Color.Transparent)
                    )
                )
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        haptic?.play(HapticEngine.Event.TAP)
                        onClose()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, "返回", tint = UpdateText)
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("系統更新", color = UpdateText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("thinn-khì · 版本與安全更新", color = UpdateSoft, fontSize=14.sp)
                    }
                    Icon(Icons.Rounded.AutoAwesome, null, tint = UpdateBlue, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(22.dp))

                UpdatePanel(glow = light) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = UpdateBlue, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("目前安裝版本", color = UpdateSoft, fontSize=14.sp)
                            Text("thinn-khì ${BuildConfig.VERSION_NAME}", color = UpdateText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (BuildConfig.THINN_BUILD_SHA == "local") "程式來源：本地測試版"
                                else "程式來源：${BuildConfig.THINN_BUILD_SHA.take(10)}",
                                color = UpdateSoft, fontSize = 15.sp, lineHeight = 21.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("已安裝版本", color = UpdateText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("安裝新版後僅顯示本次升級的變更。", color = UpdateSoft, fontSize=14.sp)
                }

                Spacer(Modifier.height(18.dp))

                UpdatePanel(glow = light) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SystemUpdate, null, tint = UpdateBlue, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("下一個版本", color = UpdateSoft, fontSize=14.sp)
                            Text(
                                when {
                                    checking -> "檢查中…"
                                    pending != null -> pending?.version ?: "暫無新版"
                                    lastError != null -> "檢查失敗"
                                    else -> "暫無新版"
                                },
                                color = UpdateText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    AnimatedVisibility(visible = checking) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = UpdateBlue)
                    }
                    if (!checking) Spacer(Modifier.height(2.dp))
                    Text(status, color = UpdateSoft, fontSize=15.sp, lineHeight = 19.sp)

                    if (lastError != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("檢查失敗：", color = UpdateText, fontSize=15.sp, fontWeight = FontWeight.SemiBold)
                        Text(lastError ?: "", color = UpdateSoft, fontSize=14.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }

                    pending?.let { release ->
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = .15f))
                        Spacer(Modifier.height(12.dp))
                        Text("新增功能與修復", color = UpdateText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        UpdateReleaseNotes(pendingDelta.ifBlank { "正在整理版本差異…" })
                        Spacer(Modifier.height(16.dp))
                        if (installing) {
                            val fraction = totalBytes?.takeIf { it > 0L }?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) }
                            io.atmos.weather.ui.ThinnProgressLine(fraction,
                                if(fraction==null) "正在下載，檔案大小尚未提供…" else "已下載 ${(fraction*100).roundToInt()}%")
                            Spacer(Modifier.height(12.dp))
                        }
                        Button(
                            onClick = {
                                haptic?.play(HapticEngine.Event.TAP)
                                installing = true
                                downloaded = 0L
                                totalBytes = null
                                lastError = null
                                downloadJob = scope.launch {
                                    val result = runCatching {
                                        AppUpdater.downloadAndInstall(context, release) { soFar, total ->
                                            downloaded = soFar
                                            totalBytes = total
                                        }
                                    }
                                    result.onSuccess {
                                        status = "APK 已下載驗證，請在 Android 安裝程式確認更新。"
                                    }.onFailure { err ->
                                        if(err is CancellationException) status = "已取消下載"
                                        else {lastError = err.localizedMessage ?: "請稍後重試";status = "更新失敗"}
                                    }
                                    installing = false
                                    downloadJob = null
                                }
                            },
                            enabled = !installing && !checking,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .35f))
                        ) {
                            Icon(Icons.Rounded.Download, null, tint = UpdateText)
                            Spacer(Modifier.width(8.dp))
                            Text(if (installing) "正在下載與驗證…" else "下載並更新", color = UpdateText)
                        }
                        if(installing) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick={downloadJob?.cancel();status="正在取消下載…"},
                                modifier=Modifier.fillMaxWidth()) {
                                Text("取消下載",color=UpdateSoft)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { check() },
                        enabled = !checking && !installing,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .35f))
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = UpdateText)
                        Spacer(Modifier.width(8.dp))
                        Text("重新檢查更新", color = UpdateText)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "更新來源：GitHub Releases。APK 會進行 SHA-256、套件名稱與版本檢查；覆蓋升級需相同簽署金鑰，安裝需經 Android 系統確認。",
                    color = UpdateSoft,
                    fontSize=14.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun UpdatePanel(glow: Float, content: @Composable ColumnScope.() -> Unit) {
    io.atmos.weather.ui.GlassSurface(Modifier.fillMaxWidth(),content)
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
