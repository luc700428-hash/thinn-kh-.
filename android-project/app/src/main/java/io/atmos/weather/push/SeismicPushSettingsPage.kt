
package io.atmos.weather.push

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.GlassSurface
import io.atmos.weather.ui.GlassToggle

@Composable
fun SeismicPushSettingsPage(onClose: () -> Unit) {
    val ctx = LocalContext.current

    var enabled by remember {
        mutableStateOf(SeismicPushRegistration.optedIn(ctx))
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var status by remember {
        mutableStateOf(
            "地震通知需完成 Firebase 身分驗證及伺服器註冊。"
        )
    }

    val configured = SeismicPushRegistration.configured()

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF091B30),
                        Color(0xFF235477)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            "地震推播服務",
            fontSize = 28.sp,
            color = Color.White
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "使用 Firebase FCM 與 Cloudflare Worker；" +
                "不會冒充政府災防告警。",
            color = Color.White.copy(alpha = 0.8f)
        )

        Spacer(Modifier.height(18.dp))

        GlassSurface {
            Text(
                if (configured) {
                    "Firebase 與 Worker 網址已設定"
                } else {
                    "Firebase 或 Worker 尚未配置"
                },
                color = if (configured) {
                    Color.White
                } else {
                    Color(0xFFFFD38C)
                }
            )

            Spacer(Modifier.height(12.dp))

            GlassToggle(
                "接收地震速報推播",
                "需使用者同意、網路連線與伺服器註冊",
                enabled
            ) { target ->

                if (busy) {
                    return@GlassToggle
                }

                if (target && !configured) {
                    status = "Firebase 或 Worker 尚未配置，無法啟用。"
                    return@GlassToggle
                }

                busy = true

                status = if (target) {
                    "正在驗證 Firebase 並向伺服器註冊……"
                } else {
                    "正在取消遠端推播註冊……"
                }

                SeismicPushRegistration.setEnabled(
                    ctx,
                    target
                ) { message ->
                    enabled = SeismicPushRegistration.optedIn(ctx)
                    status = message
                    busy = false
                }
            }

            Spacer(Modifier.height(12.dp))

            if (busy) {
                Text(
                    "請稍候，正在處理推播設定。",
                    color = Color(0xFFFFD38C),
                    fontSize = 15.sp
                )
            }

            if (!configured) {
                Text(
                    "目前無法接收遠端推播，請先完成建置設定。",
                    color = Color(0xFFFFD38C),
                    fontSize = 15.sp
                )
            }

            Text(
                status,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "伺服器未開放註冊時，" +
                    "即使 Firebase 已設定，也不會顯示註冊成功。",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "FCM 通知可能受到網路、通知權限、" +
                    "省電設定與 Android 系統限制而延遲。" +
                    "本功能不是政府 PWS，也不保證即時送達。",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onClose,
            enabled = !busy
        ) {
            Text("返回設定")
        }
    }
}
