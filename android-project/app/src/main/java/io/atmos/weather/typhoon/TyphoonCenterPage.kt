package io.atmos.weather.typhoon

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.alerts.CwaWarning
import io.atmos.weather.alerts.CwaWarningFeed

/** Official-only public typhoon entry: no fabricated forecast cone or fake issuance. */
@Composable
fun TyphoonCenterPage(warnings:List<CwaWarning>,onClose:()->Unit) {
    val link=LocalUriHandler.current
    val typhoonWarnings=warnings.filter { it.title.contains("颱風") || it.summary.contains("颱風警報") }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07192D),Color(0xFF24506C))))
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal=20.dp,vertical=20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onClose) { Icon(Icons.Rounded.ArrowBack,"返回工具",tint=Color.White) }
            Text("颱風動向與警報",fontSize=27.sp,color=Color.White,modifier=Modifier.weight(1f))
        }
        Spacer(Modifier.height(13.dp))
        Text("中央氣象署官方颱風動態",color=Color(0xFFAAE6FF),fontSize=20.sp)
        Text("所在地 RSS 公告 ${typhoonWarnings.size} 則。若尚無可核對公告，不代表全臺無颱風。",
            color=Color.White,fontSize=16.sp)
        Spacer(Modifier.height(16.dp))
        if(typhoonWarnings.isEmpty()) Text("目前尚無從所在地 RSS 確認的颱風公告；請點下方官方即時頁面。",
            color=Color.White,fontSize=16.sp)
        typhoonWarnings.forEach { a ->
            Column(Modifier.fillMaxWidth().padding(vertical=6.dp)
                .background(Color.White.copy(alpha=.13f),RoundedCornerShape(22.dp))
                .clickable {link.openUri(a.link)}.padding(17.dp)) {
                Text(a.title,color=Color.White,fontSize=19.sp)
                Text(a.summary.take(190),color=Color(0xFFE1EFF7),fontSize=15.sp)
                Text("查看官方公告 ↗",color=Color(0xFF9FECFF),fontSize=15.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick={link.openUri("https://www.cwa.gov.tw/V8/C/P/Typhoon/TY_WARN.html")},
            modifier=Modifier.fillMaxWidth().height(55.dp)) {
            Icon(Icons.Rounded.OpenInNew,null)
            Spacer(Modifier.width(8.dp))
            Text("查看官方颱風路徑與預測",fontSize=17.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("警報推送使用既有官方 RSS 背景檢查。WorkManager 受網路、省電與排程限制，不能保證警報發布瞬間送達；請以官方災防訊息為準。",
            color=Color(0xFFCDE1ED),fontSize=15.sp,lineHeight=22.sp)
    }
}
