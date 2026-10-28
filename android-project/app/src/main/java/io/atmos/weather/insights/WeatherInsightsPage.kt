package io.atmos.weather.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun WeatherInsightsPage(weather:WeatherSnapshot?,stale:Boolean,onClose:()->Unit) {
    val p=MaterialTheme.colorScheme
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var mode by rememberSaveable{mutableIntStateOf(0)}
    var now by remember{mutableStateOf(Instant.now())}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60_000);now=Instant.now()}}
    val zone=runCatching{ZoneId.of(weather?.timeZoneId?:"Asia/Taipei")}.getOrDefault(ZoneId.of("Asia/Taipei"))
    val clock=DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(zone)
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader("氣象詳解與外出時段",onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)
            .padding(top=12.dp,bottom=132.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(weather==null){Text("尚無天氣資料，請先回首頁更新。",color=p.onSurface);return@Column}
            Text(weather.city.name,color=p.onSurface,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            Text("資料取得 ${clock.format(weather.fetchedAt)} · 地點當地時間",color=p.onSurfaceVariant,fontSize=14.sp)
            if(stale)Text("目前是離線快取；以下比較可能已過時。",color=p.error,fontSize=15.sp)
            GlassChoiceGroup(listOf("外出時段","風與濕度","空氣成分"),tab,{tab=it})
            when(tab) {
                0->{
                    GlassChoiceGroup(OutdoorMode.entries.map{it.title},mode,{mode=it})
                    val candidates=remember(weather,mode,now){WeatherInsightMath.windows(weather,OutdoorMode.entries[mode],now)}
                    Text("未來 24 小時 · 三小時時段比較",color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold)
                    Text("比較降雨機率、體感、風速與已提供的紫外線；晾衣另外比較濕度。只使用完整連續的逐時資料，排序代表相對條件，並非安全保證。",
                        color=p.onSurfaceVariant,fontSize=15.sp,lineHeight=23.sp)
                    if(candidates.isEmpty())Text("未來完整的逐時資料不足，暫不推薦時段。",color=p.onSurfaceVariant)
                    candidates.take(3).forEachIndexed{i,w->GlassSurface {
                        Text(if(w.caution)"${i+1} · 此時段仍需留意天氣" else "${i+1} · 預報條件相對較佳",color=p.primary,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("${clock.format(w.hours.first().at)} — ${DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(w.hours.last().at.plusSeconds(3600))}",color=p.onSurface,fontSize=19.sp,fontWeight=FontWeight.Bold)
                        InsightValue("最高降雨機率","${w.rain}%")
                        InsightValue(if(w.usesFeelsLike)"平均體感參考" else "平均氣溫",number(w.temperature,"°C"))
                        InsightValue("最高平均風速",number(w.wind,"km/h"))
                        w.uv?.let{InsightValue("最高紫外線指數",number(it,""))}
                        if(mode==OutdoorMode.LAUNDRY.ordinal)InsightValue("最高相對濕度",w.humidity?.let{"$it%"}?:"未提供")
                        if(w.rain>=40)Text("仍有降雨機會，請準備雨具。",color=p.onSurfaceVariant,fontSize=14.sp)
                        if(w.caution)Text("預報含雷雨、冰雪、強風或極端冷熱條件，請確認最新警特報。",color=p.error,fontSize=14.sp)
                    }}
                }
                1->{
                    GlassSurface {
                        Text("風況與能見度",color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold)
                        InsightValue("平均風速",number(weather.windKmh,"km/h"))
                        InsightValue("公尺／秒",number(weather.windKmh?.div(3.6),"m/s"))
                        InsightValue("蒲福風級參考",WeatherInsightMath.beaufort(weather.windKmh)?.let{"$it 級"}?:"未提供")
                        InsightValue("陣風",number(weather.windGustKmh,"km/h"))
                        InsightValue("風向（來向）",weather.windDirectionDeg?.takeIf{it in 0..360}?.let{"${listOf("北","東北","東","東南","南","西南","西","西北")[((it+22)%360)/45]} · $it°"}?:"未提供")
                        InsightValue("能見度",number(weather.visibilityM?.div(1000),"km"))
                        Text("平均風與陣風代表不同量測，不能互相替代。",color=p.onSurfaceVariant,fontSize=14.sp)
                    }
                    GlassSurface {
                        Text("溫濕與露點",color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold)
                        InsightValue("氣溫",number(weather.temperatureC,"°C"))
                        InsightValue("體感",number(weather.feelsLikeC,"°C"))
                        InsightValue("相對濕度",weather.humidityPct?.let{"$it%"}?:"未提供")
                        InsightValue("露點估算",number(WeatherInsightMath.dewPoint(weather.temperatureC,weather.humidityPct),"°C"))
                        InsightValue("地面氣壓",number(weather.pressureHpa,"hPa"))
                        Text("露點依目前溫濕度估算，並非露點測站實測。氣溫接近露點表示空氣較接近飽和，不能單憑此值判定有霧。",color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=22.sp)
                        Text(weather.feelsLikeMethod?:"體感由氣象模式估算",color=p.onSurfaceVariant,fontSize=14.sp)
                    }
                }
                2->GlassSurface {
                    Text("空氣成分",color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold)
                    Text("模式估算 · 非環境部官方測站 AQI",color=p.onSurfaceVariant,fontSize=15.sp)
                    InsightValue("美國 AQI",weather.usAqi?.let{"$it"}?:"未提供")
                    InsightValue("PM₂.₅",number(weather.pm25,"μg/m³"))
                    InsightValue("PM₁₀",number(weather.pm10,"μg/m³"))
                    InsightValue("臭氧 O₃",number(weather.ozone,"μg/m³"))
                    InsightValue("二氧化氮 NO₂",number(weather.nitrogenDioxide,"μg/m³"))
                    InsightValue("二氧化硫 SO₂",number(weather.sulfurDioxide,"μg/m³"))
                    InsightValue("一氧化碳 CO",number(weather.carbonMonoxide,"μg/m³"))
                    InsightValue("揚塵",number(weather.airborneDust,"μg/m³"))
                    Text("缺值顯示未提供，不視為零污染。這些是模式網格估算，不代表所在街道的即時實測。",color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=22.sp)
                }
            }
            Text("資料：Open-Meteo 天氣與空品模式"+(if(weather.observationStation!=null)"；目前溫濕風採 ${weather.observationStation} 站附近實測。" else "。")+
                "露點與時段比較由 App 依現有數據計算；官方警報請見警特報中心。",color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=22.sp)
        }
    }
}
@Composable private fun InsightValue(label:String,value:String) {
    val p=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,color=p.onSurfaceVariant,fontSize=16.sp,modifier=Modifier.weight(1f))
        Text(value,color=p.onSurface,fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }
}
private fun number(value:Double?,unit:String)=value?.takeIf{it.isFinite()}?.let{String.format(Locale.TAIWAN,"%.1f",it)+if(unit.isBlank())"" else " $unit"}?:"未提供"
