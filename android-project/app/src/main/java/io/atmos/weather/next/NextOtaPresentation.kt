package io.atmos.weather.next

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presentation-only model: the existing signed OTA client remains sole authority. */
sealed interface NextOtaState {
    data object Idle : NextOtaState
    data object Checking : NextOtaState
    data class Available(val version: String, val notes: List<String>) : NextOtaState
    data class Downloading(val progress: Float) : NextOtaState
    data object Verifying : NextOtaState
    data object ReadyToInstall : NextOtaState
    data object Installing : NextOtaState
    data object Completed : NextOtaState
    data class Failed(val message: String) : NextOtaState
}

/**
 * Android Compose treatment of the NEXT OTA screen with honest state labels.
 * Integrate with existing AppUpdater callbacks; no simulated completion, installer or status.
 * Visual specification only until connected to the real OTA state machine.
 */
@Composable
fun NextOtaStatusCard(
    state: NextOtaState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
    reduceTransparency: Boolean = false
) {
    val title = when (state) {
        NextOtaState.Idle -> "檢查更新"
        NextOtaState.Checking -> "正在檢查更新"
        is NextOtaState.Available -> "發現新版 ${state.version}"
        is NextOtaState.Downloading -> "下載系統更新"
        NextOtaState.Verifying -> "驗證安裝檔"
        NextOtaState.ReadyToInstall -> "已準備安裝"
        NextOtaState.Installing -> "系統正在處理安裝"
        NextOtaState.Completed -> "更新完成"
        is NextOtaState.Failed -> "無法完成更新"
    }
    val body = when (state) {
        NextOtaState.Idle -> "保持 App 在最新且安全的版本。"
        NextOtaState.Checking -> "請稍候，正在比對正式版本資訊。"
        is NextOtaState.Available -> "版本特性及修正事項如下。"
        is NextOtaState.Downloading -> "正在下載正式發行檔，請勿關閉程式。"
        NextOtaState.Verifying -> "正在驗證檔案完整性及來源。"
        NextOtaState.ReadyToInstall -> "下載與驗證完成，等待系統安裝確認。"
        NextOtaState.Installing -> "是否安裝成功，以 Android 系統安裝結果為準。"
        NextOtaState.Completed -> "已確認完成升級。"
        is NextOtaState.Failed -> state.message
    }
    NextOpticalSurface(modifier = modifier.fillMaxWidth(), reduceTransparency = reduceTransparency) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("THINN-KHÌ NEXT", color = Color(0xFFB6DDF4), fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold)
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFFD8EAF7), fontSize = 14.sp, lineHeight = 20.sp)
            if (state is NextOtaState.Available) {
                state.notes.take(20).forEach { note ->
                    Text("• $note", color = Color(0xFFE6F5FC), fontSize = 13.sp)
                }
            }
            if (state is NextOtaState.Downloading) {
                val p = state.progress.coerceIn(0f, 1f)
                Row {
                    Text("下載進度", color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text("${(p * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                }
                Box(Modifier.fillMaxWidth().height(7.dp)
                    .background(Color(0x605D88A0), RoundedCornerShape(5.dp))
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }) {
                    Box(Modifier.fillMaxWidth(p).height(7.dp)
                        .background(Color(0xFFB8EAFE), RoundedCornerShape(5.dp)))
                }
            }
            val action: (() -> Unit)? = when (state) {
                NextOtaState.Idle, is NextOtaState.Failed -> onCheck
                is NextOtaState.Available -> onDownload
                NextOtaState.ReadyToInstall -> onInstall
                else -> null
            }
            if (action != null) {
                val label = when (state) {
                    NextOtaState.Idle, is NextOtaState.Failed -> "重新檢查"
                    is NextOtaState.Available -> "下載更新"
                    else -> "開始安裝"
                }
                Box(Modifier.fillMaxWidth()
                    .background(Color(0x654F9AC0), RoundedCornerShape(18.dp))
                    .clickable(onClick = action).padding(vertical = 13.dp)) {
                    Text(label, modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                        color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}
