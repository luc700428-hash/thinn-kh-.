package io.atmos.weather.next.features

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.data.OpenMeteoWeather
import io.atmos.weather.next.design.*
import io.atmos.weather.next.domain.*
import io.atmos.weather.next.platform.NextWeatherWidget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Screen(val label:String,val symbol:String) {
    WEATHER("天氣","☀"),QUAKE("地震","◉"),TYPHOON("颱風","◎"),ALARM("鬧鐘","◷"),SETTINGS("設定","⚙")
}

@Composable
fun NextApp() {
    val context=LocalContext.current
    val user=remember(context) { context.getSharedPreferences("next_user_v1",Context.MODE_PRIVATE) }
    var city by remember { mutableStateOf(KnownCities.cities.firstOrNull {it.id==user.getString("city","taipei")} ?: KnownCities.cities.first()) }
    var screen by remember { mutableStateOf(Screen.WEATHER) }
    val navigationOrigins=remember { mutableStateListOf<Screen>() }
    BackHandler(enabled=navigationOrigins.isNotEmpty()) {
        screen=navigationOrigins.removeAt(navigationOrigins.lastIndex)
    }
    var reduceMotion by remember { mutableStateOf(user.getBoolean("reduce_motion",false)) }
    var weather by remember { mutableStateOf<WeatherLoad?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshCount by remember { mutableIntStateOf(0) }
    val scope=rememberCoroutineScope()
    val api=remember(context) { OpenMeteoWeather(context) }
    LaunchedEffect(city,refreshCount) {
        refreshing=true
        try {
            weather=api.load(city)
        } finally {
            refreshing=false
            // City and stale/empty cache changes must also update a pinned widget.
            NextWeatherWidget.refresh(context)
        }
    }
    val reading=when(val w=weather) { is WeatherLoad.Live->w.reading;is WeatherLoad.Cached->w.reading;else->null }
    Box(Modifier.fillMaxSize()) {
        AtmosphericBackground(reading?.scene,reduceMotion,Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start=23.dp,end=23.dp,top=14.dp,bottom=10.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Column {
                    NextText("thinn-khì",size=26,heavy=true)
                    NextText("NEXT · FOUNDATION",size=14,color=NextPalette.muted)
                }
                LiquidAction(if(refreshing)"載入中…" else "↻ 更新",onClick={if(!refreshing)refreshCount++})
            }
            Box(Modifier.weight(1f)) {
                AnimatedContent(targetState=screen,transitionSpec={
                    (fadeIn(tween(250))+scaleIn(initialScale=.975f,animationSpec=tween(250))) togetherWith
                        (fadeOut(tween(170))+scaleOut(targetScale=1.015f,animationSpec=tween(170)))
                },label="nextNavigation") { target ->
                    when(target) {
                        Screen.WEATHER->WeatherScreen(city,reading,weather,refreshing,
                            onCity={city=it;user.edit().putString("city",it.id).apply()},onRetry={refreshCount++})
                        Screen.QUAKE->RoadmapScreen("地震中心","◉","真實地震速報、測站與推播功能正在從舊工程重新隔離。",
                            "尚未連上地震來源；此頁不會產生模擬的緊急警報。")
                        Screen.TYPHOON->RoadmapScreen("颱風中心","◎","將整合 CWA 官方路徑、中文移動方向、時間軸及 NLSC 真實地圖。",
                            "目前沒有接入官方颱風資料，不能據此判斷警報狀況。")
                        Screen.ALARM->RoadmapScreen("原生鬧鐘","◷","重新建立固定 AM/PM 滾輪、可靠響鈴、鎖屏及自然台灣語音。",
                            "本測試版尚未啟用鬧鐘排程；重要鬧鐘請繼續使用舊版。")
                        Screen.SETTINGS->SettingsScreen(reduceMotion,{ next ->
                            reduceMotion=next;user.edit().putBoolean("reduce_motion",next).apply()
                        })
                    }
                }
            }
            BottomNavigation(screen,reduceMotion){ destination ->
                if(destination != screen) {
                    navigationOrigins.add(screen)
                    screen=destination
                }
            }
        }
    }
}

@Composable
private fun WeatherScreen(city:WeatherCity,reading:WeatherReading?,state:WeatherLoad?,
    loading:Boolean,onCity:(WeatherCity)->Unit,onRetry:()->Unit) {
    LazyColumn(contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.fillMaxSize()) {
        item {
            NextText("現在的天氣光景",size=18,color=NextPalette.muted)
            Spacer(Modifier.height(10.dp))
            NextText(city.name,size=24,heavy=true)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                NextText(reading?.scene?.emoji ?: "◌",size=55)
                Spacer(Modifier.width(12.dp))
                Column {
                    NextText(reading?.temperatureC?.let { "${it.toInt()}°" } ?: "—°",size=64,heavy=true)
                    NextText(reading?.scene?.label ?: "沒有可確認的天氣資料",size=18,color=NextPalette.muted)
                }
            }
            Spacer(Modifier.height(7.dp))
            NextText(when(state) {
                is WeatherLoad.Live->DataFreshnessPolicy.display(state.reading,System.currentTimeMillis(),false)
                is WeatherLoad.Cached->DataFreshnessPolicy.display(state.reading,System.currentTimeMillis(),true)
                is WeatherLoad.Unavailable->state.explanation
                null->"正在向 Open-Meteo 查詢…"
            },size=14,color=NextPalette.muted)
        }
        item {
            LiquidPanel {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    NextText("今天出門",size=17,heavy=true)
                    NextText(when(state) { is WeatherLoad.Live->"即時來源";is WeatherLoad.Cached->"上次快取";else->"未取得" },size=14,color=NextPalette.aqua)
                }
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Metric("體感",reading?.feelsLikeC?.let { "${it.toInt()}°" } ?: "—")
                    Metric("濕度",reading?.humidityPct?.let { "$it%" } ?: "—")
                    Metric("風速",reading?.windKmh?.let { "${it.toInt()} km/h" } ?: "—")
                }
                Spacer(Modifier.height(14.dp))
                NextText("當前降水量：${reading?.precipitationMm?.let { "$it mm" } ?: "待取得"}",size=14,color=NextPalette.muted)
            }
        }
        item {
            NextText("選擇城市",size=16,heavy=true)
            Spacer(Modifier.height(10.dp))
            KnownCities.cities.chunked(3).forEach {line ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    line.forEach {option ->
                        LiquidAction(option.name,Modifier.weight(1f),selected=option.id==city.id,onClick={onCity(option)})
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
        }
        item {
            LiquidPanel(onClick=onRetry) {
                NextText("重新取得即時資料 ↗",size=16,heavy=true)
                Spacer(Modifier.height(6.dp))
                NextText("資料來自 Open-Meteo；非中央氣象署官方警報。離線時會明確標示上次資料。",
                    size=14,color=NextPalette.muted)
            }
        }
        item { Spacer(Modifier.height(22.dp)) }
    }
}

@Composable
private fun Metric(label:String,value:String) {
    Column {
        NextText(label,size=14,color=NextPalette.muted)
        Spacer(Modifier.height(4.dp))
        NextText(value,size=17,heavy=true)
    }
}
@Composable
private fun RoadmapScreen(title:String,symbol:String,summary:String,warning:String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(22.dp))
        NextText(symbol,size=58)
        NextText(title,size=29,heavy=true)
        LiquidPanel {
            NextText("重新開發中",size=17,heavy=true,color=NextPalette.aqua)
            Spacer(Modifier.height(10.dp))
            NextText(summary,size=16)
            Spacer(Modifier.height(10.dp))
            NextText(warning,size=14,color=Color(0xFFFFD6A3))
        }
        NextText("此頁不提供假資料，也不代表原手機 App 的相關功能已被替換。",size=14,color=NextPalette.muted)
    }
}
@Composable
private fun SettingsScreen(reduceMotion:Boolean,onMotion:(Boolean)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        NextText("設定與關於",size=28,heavy=true)
        LiquidPanel {
            NextText("Next Optical Engine",size=19,heavy=true)
            Spacer(Modifier.height(8.dp))
            NextText("自製光學材質 · 共用 Spring 規則 · 場景動態",size=14,color=NextPalette.muted)
            Spacer(Modifier.height(15.dp))
            LiquidAction(if(reduceMotion) "✓ 已減少動態" else "開啟減少動態",selected=reduceMotion,
                onClick={onMotion(!reduceMotion)})
        }
        LiquidPanel {
            NextText("獨立測試版",size=19,heavy=true)
            Spacer(Modifier.height(9.dp))
            NextText("io.atmos.weather.nextlab",size=14,color=NextPalette.aqua)
            Spacer(Modifier.height(9.dp))
            NextText("和正式 thinn-khì 安裝在不同資料空間；不會存取或清除舊版鬧鐘與事項。",size=15)
            Spacer(Modifier.height(7.dp))
            NextText("這是重新開發的第一份基礎工程，不是完整 V25 或正式 OTA。",size=14,color=Color(0xFFFFD6A3))
        }
        LiquidPanel {
            NextText("系統整合規劃",size=19,heavy=true)
            Spacer(Modifier.height(8.dp))
            NextText("下一階段依序接入：官方地震來源、颱風地圖、可靠鬧鐘、HyperOS 超級島、Android Live Updates。",size=15)
            Spacer(Modifier.height(8.dp))
            NextText("系統級通知與動畫必須遵守 OEM 授權及桌面更新限制。",size=14,color=NextPalette.muted)
        }
    }
}
@Composable
private fun BottomNavigation(active:Screen,reduceMotion:Boolean,onPick:(Screen)->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp),
        horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Screen.entries.forEach { item ->
            val selected=active==item
            val tone=if(selected) Color.White.copy(alpha=.23f) else Color.Transparent
            val shape=RoundedCornerShape(19.dp)
            Column(Modifier.weight(1f).background(tone,shape)
                .clickable {onPick(item)}.padding(vertical=8.dp),
                horizontalAlignment=Alignment.CenterHorizontally) {
                NextText(item.symbol,size=21)
                NextText(item.label,size=14,heavy=selected)
            }
        }
    }
}
