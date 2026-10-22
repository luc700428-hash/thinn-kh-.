package io.atmos.weather.next.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.design.*

/** Fully custom grouped, drill-down dark settings; no Material UI dependency. */
@Composable
fun IosSettingsPage(reduceMotion:Boolean,onMotion:(Boolean)->Unit,
    fahrenheit:Boolean,onFahrenheit:(Boolean)->Unit,haptics:Boolean,onHaptics:(Boolean)->Unit) {
    var detail by remember {mutableStateOf("")}
    BackHandler(detail.isNotEmpty()){detail=""}
    Column(Modifier.fillMaxSize().background(IosColor.background)
        .verticalScroll(rememberScrollState()).padding(horizontal=17.dp)) {
        Spacer(Modifier.height(15.dp))
        if(detail.isEmpty()) {
            IosText("設定",34,FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Caption("APP 設定")
            IosGroup {
                IosSettingRow("☁","天氣",detail=if(fahrenheit)"華氏" else "攝氏",onClick={detail="天氣"})
                IosSeparator()
                IosSettingRow("◷","鬧鐘與聲音",onClick={detail="鬧鐘與聲音"},accent=IosColor.orange)
                IosSeparator()
                IosSettingRow("◈","通知與即時動態",onClick={detail="通知與即時動態"},accent=Color(0xFF5E5CE6))
            }
            Spacer(Modifier.height(25.dp))
            Caption("顯示與觸覺")
            IosGroup {
                IosSettingRow("◌","外觀與動態",onClick={detail="外觀與動態"},accent=Color(0xFF8E8E93))
                IosSeparator()
                IosSettingRow("♫","觸覺回饋",checked=haptics,onToggle=onHaptics,accent=Color(0xFFFF375F))
            }
            Spacer(Modifier.height(25.dp))
            Caption("隱私與資訊")
            IosGroup {
                IosSettingRow("▣","資料來源與隱私",onClick={detail="資料來源與隱私"},accent=Color(0xFF32ADE6))
                IosSeparator()
                IosSettingRow("ⓘ","關於 thinn-khì NEXT",onClick={detail="關於"},accent=Color(0xFF8E8E93))
            }
            Spacer(Modifier.height(30.dp))
            IosText("thinn-khì NEXT · iOS 風格 Android 實驗版",14,color=IosColor.muted)
            Spacer(Modifier.height(28.dp))
        } else {
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                Box(Modifier.clickable{detail=""}.padding(vertical=7.dp,end=10.dp)) {IosText("‹  設定",17,color=IosColor.blue)}
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(19.dp))
            IosText(detail,31,FontWeight.Bold)
            Spacer(Modifier.height(31.dp))
            when(detail) {
                "天氣" -> {
                    Caption("溫度單位")
                    IosGroup {
                        IosSettingRow("℃","攝氏度（°C）",detail=if(!fahrenheit)"✓" else "",onClick={onFahrenheit(false)})
                        IosSeparator()
                        IosSettingRow("℉","華氏度（°F）",detail=if(fahrenheit)"✓" else "",onClick={onFahrenheit(true)})
                    }
                    Footnote("逐時及 10 日預報的溫度會一併換算。資料仍由 Open-Meteo 提供。")
                }
                "外觀與動態" -> {
                    Caption("動態效果")
                    IosGroup {
                        IosSettingRow("◌","減少動態效果",checked=reduceMotion,onToggle=onMotion)
                    }
                    Footnote("減少動態時停止持續天氣粒子動畫及大幅轉場；保留必要的狀態變更。")
                }
                "鬧鐘與聲音" -> {
                    Caption("鬧鐘")
                    IosGroup {
                        IosSettingRow("◷","響鈴狀態",detail="依 Android 權限",accent=IosColor.orange)
                    }
                    Footnote("NEXT 測試版的鬧鐘權限及提醒可靠性需要在裝置上驗證，請勿用來取代重要的起床鬧鐘。")
                }
                "通知與即時動態" -> {
                    Caption("系統支援")
                    IosGroup {
                        IosSettingRow("◈","小米超級島",detail="依 OEM 核准")
                        IosSeparator()
                        IosSettingRow("●","Android Live Updates",detail="依系統支援",accent=Color(0xFF5E5CE6))
                    }
                    Footnote("這兩項需要裝置版本與系統授權；測試版不會冒充政府地震警報或偽造即時事件。")
                }
                "資料來源與隱私" -> {
                    Caption("資料來源")
                    IosGroup { IosSettingRow("☁","天氣來源",detail="Open-Meteo") }
                    Footnote("目前天氣數值、逐時及 10 日預報來自真實 Open-Meteo API；無資料時不會製作虛構預報。")
                    Footnote("NEXT 測試版與正式 thinn-khì 使用不同的 Android 套件與獨立資料空間。")
                }
                else -> {
                    Caption("版本")
                    IosGroup {IosSettingRow("ⓘ","套件",detail="NEXT 實驗版")}
                    Footnote("原生 Android Kotlin / Compose；以 iOS 操作模式作設計參考，未使用 Apple 私有框架。正式版須通過原簽章與舊資料相容驗證。")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
@Composable private fun Caption(value:String){
    IosText(value,14,color=IosColor.muted,modifier=Modifier.padding(start=12.dp,bottom=7.dp))
}
@Composable private fun Footnote(value:String){
    Spacer(Modifier.height(9.dp))
    IosText(value,14,color=IosColor.muted,modifier=Modifier.padding(horizontal=12.dp))
}
