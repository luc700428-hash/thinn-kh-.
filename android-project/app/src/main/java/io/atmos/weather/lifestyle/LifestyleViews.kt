package io.atmos.weather.lifestyle
import io.atmos.weather.ui.appleBouncingScroll

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassSlider as Slider
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot

private val textColor:Color @Composable get()=MaterialTheme.colorScheme.onSurface
private val detailColor:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun LifestyleAdviceCard(weather: WeatherSnapshot, stale: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val revision by LifestylePreferenceStore.changes.collectAsState()
    val mode = remember(revision){LifestylePreferenceStore.mode(context)}
    val preferences = remember(revision){LifestylePreferenceStore.load(context)}
    val plan = remember(weather, mode, stale, preferences) {
        LifestyleAdvisor.recommend(weather, mode, stale, preferences = preferences)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("出門穿搭清單", fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = textColor, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenSettings) { Text("個人偏好", color = MaterialTheme.colorScheme.primary, fontSize=14.sp) }
    }
    Text(plan.headline, color = MaterialTheme.colorScheme.primary, fontSize=16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    io.atmos.weather.ui.GlassChoiceGroup(OutingMode.entries.map{it.title},mode.ordinal,{index->
        LifestylePreferenceStore.mode(context,OutingMode.entries[index])
    })
    Spacer(Modifier.height(12.dp))
    Text(plan.temperatureSummary,color=detailColor,fontSize=15.sp,lineHeight=22.sp)
    Spacer(Modifier.height(10.dp))
    AdviceLine("外套", plan.coatDecision)
    AdviceLine("上衣", plan.top)
    AdviceLine("褲子", plan.bottoms)
    AdviceLine("搭配", plan.layer)
    AdviceLine("鞋子", plan.shoes)
    AdviceLine("雨具", plan.umbrella)
    Spacer(Modifier.height(8.dp))
    Text(plan.reminder,color=textColor,fontSize=14.sp)
    plan.extraReminders.filter{it!=plan.reminder}.take(3).forEach {reminder->
        Spacer(Modifier.height(7.dp));Text("• $reminder",color=textColor,fontSize=14.sp)
    }
    Spacer(Modifier.height(9.dp))
    Text("判斷依據：${plan.evidence}", color = detailColor, fontSize=14.sp)
    Spacer(Modifier.height(12.dp))
    Text("外出時段提醒",color=textColor,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
    Text(plan.timeAdvice,color=textColor,fontSize=15.sp,lineHeight=23.sp)
    if(plan.bring.isNotEmpty()) {
        Spacer(Modifier.height(9.dp))
        Text("隨身帶：${plan.bring.joinToString(" · ")}",color=textColor,fontSize=15.sp,lineHeight=23.sp)
    }
    if(!plan.weatherFresh)Text("這份建議使用較舊的天氣，出發前請重新整理。",color=MaterialTheme.colorScheme.error,fontSize=14.sp)

}

@Composable
private fun AdviceLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp), verticalAlignment=Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold,
            modifier=Modifier.width((42*androidx.compose.ui.platform.LocalDensity.current.fontScale).dp), fontSize=15.sp)
        Text(value, color = textColor, modifier=Modifier.weight(1f), fontSize=15.sp)
    }
}

@Composable
fun LifestyleSettingsPage(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(context){LifestylePreferenceStore.removeLegacyAi(context)}
    val originalPreferences = remember { LifestylePreferenceStore.load(context) }
    var comfort by remember { mutableStateOf(originalPreferences.comfort) }
    var style by remember { mutableStateOf(originalPreferences.style) }
    var umbrellaThreshold by remember { mutableFloatStateOf(originalPreferences.umbrellaThresholdPct.toFloat()) }
    Column(Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding().appleBouncingScroll().verticalScroll(rememberScrollState()).padding(start=16.dp, end=16.dp, bottom=120.dp)) {
        io.atmos.weather.ui.IOSSettingsHeader("智慧生活",onClose)
        Spacer(Modifier.height(7.dp))
        Text("依現在與未來六小時的氣溫、體感、風雨調整穿搭。建議直接在手機產生，不需另外申請服務。",
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
        Text("外套提醒：低於 21°C 帶件外套；低於 10°C 完整保暖；低於 4°C 加強頭頸與四肢保暖。若體感更冷，也會提前提醒。",color=textColor,fontSize=15.sp,lineHeight=23.sp)
        Text("以上是個人穿搭提醒門檻，並非官方低溫特報標準。怕熱偏好也不會關閉低溫保暖提醒。",color=detailColor,fontSize=14.sp,lineHeight=21.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick={
            LifestylePreferenceStore.save(context,LifestylePreferences(comfort,style,umbrellaThreshold.toInt()))
            onClose()
        },modifier=Modifier.fillMaxWidth()) { Text("儲存穿搭偏好") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick=onClose,modifier=Modifier.fillMaxWidth()) { Text("返回",color=textColor) }
    }
}
