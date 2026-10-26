package io.atmos.weather.push
import io.atmos.weather.ui.appleBouncingScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import io.atmos.weather.ui.GlassSurface
import io.atmos.weather.ui.GlassToggle
import io.atmos.weather.ui.GlassButton

@Composable
fun SeismicPushSettingsPage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(SeismicPushRegistration.optedIn(ctx)) }
    var status by remember {
        mutableStateOf("FCM 主題是輔助通知，並非官方災防警報，送達可能受省電與網路影響。")
    }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=120.dp)
    ) {
        io.atmos.weather.ui.IOSSettingsHeader("台灣地震通知",onClose)
        Text("需主動同意；僅涵蓋台灣本島與離島，不冒充官方災防細胞廣播。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        GlassSurface {
            Text("FCM ${if (SeismicPushRegistration.configured()) "已配置" else "尚未配置"}", color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            GlassToggle(
                "接收台灣與離島地震通知",
                "免費 FCM 主題傳輸；可能因省電、離線或網路而延遲",
                enabled
            ) { target ->
                if (!SeismicPushRegistration.configured()) {
                    status = "FCM 公開專案設定不足，無法訂閱主題"
                    return@GlassToggle
                }
                status = if (target) "正在訂閱主題…" else "正在取消訂閱…"
                SeismicPushRegistration.setEnabled(ctx, target) { result ->
                    status = result
                    enabled = SeismicPushRegistration.optedIn(ctx)
                }
            }
            if (!SeismicPushRegistration.configured()) {
                Text("目前不可接收：尚未配置 Firebase Cloud Messaging",
                    color = Color(0xFFFFD38C), fontSize = 15.sp)
            }
            Text(status, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Text("訂閱成功不等於後端已部署，也不代表取得合法即時預警來源。啟用前應完成 Cloudflare Worker、FCM 發送及實機驗證。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Text("本服務不能保證地震到達前通知；緊急防災資訊以中央氣象署和手機官方災防告警為準。",
                color = Color(0xFFFFDB9D), fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
        GlassButton(onClick = onClose) { Text("返回設定") }
    }
}
