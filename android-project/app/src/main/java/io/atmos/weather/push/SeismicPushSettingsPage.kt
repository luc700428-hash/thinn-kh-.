package io.atmos.weather.push

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import io.atmos.weather.ui.GlassSurface
import io.atmos.weather.ui.GlassToggle

@Composable
fun SeismicPushSettingsPage(onClose:()->Unit) {
    val ctx=LocalContext.current
    var enabled by remember{mutableStateOf(SeismicPushRegistration.optedIn(ctx))}
    var status by remember{mutableStateOf("請先了解：FCM、網路、通知權限與系統省電設定均會影響送達。")}
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF091B30),Color(0xFF235477))))
        .verticalScroll(rememberScrollState()).padding(22.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("地震推播服務",fontSize=28.sp,color=Color.White)
        Text("需你主動同意；不會強制解鎖或假冒災防細胞廣播。",color=Color.White.copy(alpha=.8f))
        Spacer(Modifier.height(18.dp))
        GlassSurface {
            Text("Firebase ${if(SeismicPushRegistration.configured()) "已設定" else "尚未配置"}",color=Color.White)
            Spacer(Modifier.height(10.dp))
            GlassToggle("接收地震速報推播","可能因離線、省電、Doze 或系統通知設定延遲",enabled) { target ->
                SeismicPushRegistration.setEnabled(ctx,target) {status=it}
                enabled=SeismicPushRegistration.optedIn(ctx)
            }
            Text(status,color=Color.White.copy(alpha=.88f),fontSize=15.sp)
            Spacer(Modifier.height(12.dp))
            Text("注意：此功能須由部署端取得合法即時地震來源，再透過驗證後的 FCM 後端送出。手機災防警報仍以官方 PWS 為準。",color=Color.White.copy(alpha=.8f),fontSize=14.sp)
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick=onClose) {Text("返回設定")}
    }
}
