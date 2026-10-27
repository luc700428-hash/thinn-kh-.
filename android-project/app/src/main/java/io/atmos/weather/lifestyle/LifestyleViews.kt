package io.atmos.weather.lifestyle
import io.atmos.weather.ui.IOSLinearProgressIndicator as LinearProgressIndicator
import io.atmos.weather.ui.appleBouncingScroll

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import io.atmos.weather.ui.GlassSlider as Slider
import io.atmos.weather.ui.GlassSwitch as Switch
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassTextButton as TextButton
import io.atmos.weather.ui.GlassOutlinedTextField as OutlinedTextField
import io.atmos.weather.ui.GlassDivider as Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import kotlinx.coroutines.launch

private val textColor:Color @Composable get()=MaterialTheme.colorScheme.onSurface
private val detailColor:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun LifestyleAdviceCard(weather: WeatherSnapshot, stale: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(OutingMode.entries.firstOrNull { it.name == context.getSharedPreferences("thinn_lifestyle_ai", 0).getString("mode", "DAILY") } ?: OutingMode.DAILY) }
    val preferences = LifestylePreferenceStore.load(context)
    val plan = remember(weather, mode, stale, preferences) {
        LifestyleAdvisor.recommend(weather, mode, stale, preferences = preferences)
    }
    var aiSummary by remember(weather, mode, preferences) { mutableStateOf<String?>(null) }
    var aiError by remember(weather, mode, preferences) { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    val settings = LifestyleAiSettings.load(context)
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("✦ 智慧生活穿搭", fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = textColor, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenSettings) { Text("AI 設定", color = Color(0xFFE2F4FF), fontSize=14.sp) }
    }
    Text(plan.headline, color = Color(0xFFFFE4A5), fontSize=16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        OutingMode.entries.forEach { choice ->
            val selected = choice == mode
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .background(if (selected) Color.White.copy(alpha = .30f) else Color.White.copy(alpha = .09f), RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = if (selected) .73f else .18f), RoundedCornerShape(14.dp))
                .clickable {
                    mode = choice
                    context.getSharedPreferences("thinn_lifestyle_ai", 0).edit().putString("mode", choice.name).apply()
                }, contentAlignment = Alignment.Center) {
                Text(choice.title, color = textColor, fontSize=14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    AdviceLine("上衣", plan.top)
    AdviceLine("褲子", plan.bottoms)
    AdviceLine("外搭", plan.layer)
    AdviceLine("鞋子", plan.shoes)
    AdviceLine("雨具", plan.umbrella)
    Spacer(Modifier.height(8.dp))
    Text(plan.reminder, color = Color(0xFFFFEAC9), fontSize=14.sp)
    Spacer(Modifier.height(9.dp))
    Text("判斷依據：${plan.evidence}", color = detailColor, fontSize=14.sp)
    if (!plan.weatherFresh) {
        Spacer(Modifier.height(8.dp))
        Text("資料非即時，已停止呼叫 AI；請刷新天氣。", color = Color(0xFFFFE1AE), fontSize=14.sp)
    } else if (settings.isConfigured()) {
        Spacer(Modifier.height(9.dp))
        Button(onClick = {
            generating = true; aiError = null; aiSummary = null
            scope.launch {
                try { aiSummary = LifestyleAiClient.generate(settings, weather, plan, mode) }
                catch (e: Exception) { aiError = e.message ?: "無法連線至 AI 服務" }
                finally { generating = false }
            }
        }, enabled = !generating, modifier = Modifier.fillMaxWidth()) {
            Text(if (generating) "開源 AI 正在整理穿搭建議…" else "讓開源 AI 補充今日穿搭", fontSize=15.sp)
        }
        if (generating) LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        Text("開源 AI 為選用功能，設定 HTTPS Ollama 後即可使用；基本穿搭不需網路 AI。",
            fontSize=14.sp, color = detailColor)
    }
    aiSummary?.let {
        Spacer(Modifier.height(10.dp))
        Text("開源 AI 補充（可能有誤，請以天氣數據為準）", color = textColor,
            fontSize=14.sp, fontWeight = FontWeight.SemiBold)
        Text(it, color = detailColor, fontSize=15.sp)
    }
    aiError?.let { Text("AI 連線：$it；仍可使用上方穿搭建議。", color=Color(0xFFFFD9B3), fontSize=14.sp) }
}

@Composable
private fun AdviceLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp), verticalAlignment=Alignment.Top) {
        Text(label, color = Color(0xFFD6EEFF), fontWeight=FontWeight.Bold,
            modifier=Modifier.width(42.dp), fontSize=15.sp)
        Text(value, color = textColor, modifier=Modifier.weight(1f), fontSize=15.sp)
    }
}

@Composable
fun LifestyleAiSettingsPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val saved = remember { LifestyleAiSettings.load(context) }
    var enable by remember { mutableStateOf(saved.enabled) }
    var endpoint by remember { mutableStateOf(saved.endpoint) }
    var model by remember { mutableStateOf(saved.model) }
    var error by remember { mutableStateOf<String?>(null) }
    val originalPreferences = remember { LifestylePreferenceStore.load(context) }
    var comfort by remember { mutableStateOf(originalPreferences.comfort) }
    var style by remember { mutableStateOf(originalPreferences.style) }
    var umbrellaThreshold by remember { mutableFloatStateOf(originalPreferences.umbrellaThresholdPct.toFloat()) }
    Column(Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding().appleBouncingScroll().verticalScroll(rememberScrollState()).padding(start=16.dp, end=16.dp, bottom=120.dp)) {
        io.atmos.weather.ui.IOSSettingsHeader("智慧生活",onClose)
        Spacer(Modifier.height(7.dp))
        Text("上衣、褲子、鞋子、雨具由本機可解釋規則即時計算。AI 僅補充生活化建議。",
            color=detailColor, fontSize=15.sp)
        Spacer(Modifier.height(17.dp))
        Text("穿搭個人偏好 · 完全在手機運作", color=textColor, fontSize=18.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("溫感敏感度", color=detailColor, fontSize=15.sp)
        io.atmos.weather.ui.GlassChoiceGroup(TemperatureComfort.entries.map{it.label},
            TemperatureComfort.entries.indexOf(comfort),{comfort=TemperatureComfort.entries[it]})
        Spacer(Modifier.height(9.dp))
        Text("穿搭風格", color=detailColor, fontSize=15.sp)
        io.atmos.weather.ui.GlassChoiceGroup(ClothingStyle.entries.map{it.label},
            ClothingStyle.entries.indexOf(style),{style=ClothingStyle.entries[it]})
        Text("目前選擇：${comfort.label} · ${style.label}",color=textColor,fontSize=14.sp,
            modifier=Modifier.padding(top=8.dp))
        Spacer(Modifier.height(9.dp))
        Text("未來六小時降雨機率達 ${umbrellaThreshold.toInt()}% 提醒帶傘", color=textColor, fontSize=15.sp)
        Slider(value=umbrellaThreshold, onValueChange={umbrellaThreshold=it}, valueRange=20f..90f,
            steps=6, modifier=Modifier.fillMaxWidth())
        Text("偏好只改變衣物與雨具建議，不會修改實際氣象資料或政府警報。", color=detailColor, fontSize=14.sp)
        Spacer(Modifier.height(18.dp))
        Divider(color=Color.White.copy(alpha=.18f))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("啟用開源 AI 補充", modifier=Modifier.weight(1f), color=textColor, fontSize=16.sp)
            Switch(checked=enable,onCheckedChange={enable=it})
        }
        Text("選用功能，不會在背景自動發送資料；按下首頁『讓開源 AI 補充』才會連線。",
            color=detailColor, fontSize=14.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value=endpoint,onValueChange={endpoint=it},label={ Text("Ollama HTTPS 伺服器網址") },
            placeholder={Text("https://ai.example.com")}, modifier=Modifier.fillMaxWidth(),
            singleLine=true, colors=OutlinedTextFieldDefaults.colors(
                focusedTextColor=textColor,unfocusedTextColor=textColor,
                focusedLabelColor=textColor,unfocusedLabelColor=detailColor,
                focusedBorderColor=Color.White,unfocusedBorderColor=detailColor))
        Spacer(Modifier.height(13.dp))
        OutlinedTextField(value=model,onValueChange={model=it},label={Text("模型名稱")},
            modifier=Modifier.fillMaxWidth(),singleLine=true,
            colors=OutlinedTextFieldDefaults.colors(focusedTextColor=textColor,unfocusedTextColor=textColor,
                focusedLabelColor=textColor,unfocusedLabelColor=detailColor,
                focusedBorderColor=Color.White,unfocusedBorderColor=detailColor))
        Spacer(Modifier.height(14.dp))
        Text("推薦開源模型：Qwen2.5 1.5B（Apache-2.0）；透過自己架設的 Ollama 提供。",
            color=textColor,fontSize=15.sp)
        Text("Ollama 與模型不包含在 APK 內，不是免費雲端服務；需要自行架設並提供可信任 HTTPS 連線。為避免資料外洩，App 不接受 HTTP。",
            color=detailColor,fontSize=14.sp)
        Text("資料隱私：僅在點擊生成時傳送天氣數值、出行情境與本機建議。不傳 GPS、城市名稱、帳號、裝置 ID；其他資料的處理取決於你設定的服務端。",
            color=detailColor,fontSize=14.sp)
        Spacer(Modifier.height(16.dp))
        error?.let { Text(it,color=Color(0xFFFFE0B4),fontSize=14.sp) }
        Button(onClick={
            try {
                if(enable) {
                    require(endpoint.isNotBlank() && model.trim().isNotBlank()) { "啟用 AI 時請填入伺服器與模型名稱" }
                    LifestyleAiClient.validatedEndpoint(endpoint)
                }
                LifestyleAiSettings(enable,endpoint.trim(),model.trim()).save(context)
                LifestylePreferenceStore.save(context, LifestylePreferences(comfort, style, umbrellaThreshold.toInt()))
                onClose()
            } catch(e: Exception) { error=e.message ?: "設定內容無效" }
        },modifier=Modifier.fillMaxWidth()) { Text("儲存智慧生活設定") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick=onClose,modifier=Modifier.fillMaxWidth()) { Text("返回",color=textColor) }
    }
}
