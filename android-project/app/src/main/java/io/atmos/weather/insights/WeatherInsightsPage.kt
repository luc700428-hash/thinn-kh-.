package io.atmos.weather.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import java.time.Instant
import androidx.compose.animation.core.animateDpAsState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 外出時段與天氣細節, rebuilt to answer two questions in plain words:
 *  1. "When should I go out (or hang the laundry) today?" – pick an activity, see the next 24 hours
 *     as a green / amber / red strip, and one clear best window with the reasons in short chips;
 *  2. "What do the other numbers mean?" – wind, humidity, pressure, visibility and air quality as
 *     tiles, each with a one-line meaning instead of a bare table.
 */
@Composable fun WeatherInsightsPage(weather:WeatherSnapshot?,stale:Boolean,onClose:()->Unit) {
    val p=MaterialTheme.colorScheme
    var mode by rememberSaveable{mutableIntStateOf(0)}
    var now by remember{mutableStateOf(Instant.now())}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60_000);now=Instant.now()}}
    val zone=runCatching{ZoneId.of(weather?.timeZoneId?:"Asia/Taipei")}.getOrDefault(ZoneId.of("Asia/Taipei"))
    val hm=DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader("出門時段與生活指數",onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)
            .padding(top=8.dp,bottom=132.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(weather==null){Text("尚無天氣資料，請先回首頁更新。",color=p.onSurface);return@Column}
            Text("${weather.city.name} · 更新於 ${hm.format(weather.fetchedAt)}",color=p.onSurfaceVariant,fontSize=14.sp)
            if(stale)Text("目前是離線快取；以下建議可能已過時。",color=p.error,fontSize=15.sp)

            // 1. When to go out.
            val activity=OutdoorMode.entries[mode]
            val windows=remember(weather,mode,now){WeatherInsightMath.windows(weather,activity,now)}
            val hours=remember(weather,mode,now){WeatherInsightMath.hourRatings(weather,activity,now)}
            GlassSurface(Modifier.testTag("insights-outing")) {
                Text("什麼時候出門最好？",color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                GlassChoiceGroup(OutdoorMode.entries.map{it.title},mode,{mode=it})
                Spacer(Modifier.height(14.dp))
                val best=windows.firstOrNull{!it.caution}
                if(best==null) {
                    Text(if(windows.isEmpty())"未來的逐時預報不足，暫時無法比較時段。" else "未來 24 小時都有需要留意的天氣，建議確認最新警特報再安排。",
                        color=p.onSurfaceVariant,fontSize=15.sp,lineHeight=22.sp)
                } else key(mode) { Column(Modifier.iosAppear()) {
                    // Switching activity replays the entrance so the new answer is clearly new.
                    Text("最推薦時段",color=p.primary,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text("${dayLabel(best.hours.first().at,now,zone)} ${hm.format(best.hours.first().at)} – ${hm.format(best.hours.last().at.plusSeconds(3600))}",
                        color=p.onSurface,fontSize=28.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ReasonChips(best,activity)
                } }
                if(hours.size>=6) {
                    Spacer(Modifier.height(16.dp))
                    Text("未來 24 小時",color=p.onSurface,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    HourStrip(hours,best,zone)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Legend(goodColor,"適合");Legend(fairColor,"留意");Legend(poorColor,"避開")
                    }
                }
                val others=windows.filter{!it.caution&&it!==best&&best!=null&&(it.hours.first().at.isAfter(best.hours.last().at)||it.hours.last().at.isBefore(best.hours.first().at))}
                    .fold(listOf<OutdoorWindow>()){acc,w->if(acc.any{a->!(w.hours.last().at.isBefore(a.hours.first().at)||w.hours.first().at.isAfter(a.hours.last().at))})acc else acc+w}.take(2)
                if(others.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("也可以",color=p.onSurfaceVariant,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    others.forEach{w->
                        Text("${dayLabel(w.hours.first().at,now,zone)} ${hm.format(w.hours.first().at)} – ${hm.format(w.hours.last().at.plusSeconds(3600))} · 降雨 ${w.rain}% · ${w.temperature.roundToInt()}°",
                            color=p.onSurface,fontSize=16.sp,modifier=Modifier.padding(top=4.dp))
                    }
                }
                val avoid=hours.firstOrNull{it.level==HourLevel.POOR}
                if(avoid!=null) {
                    Spacer(Modifier.height(10.dp))
                    Text("避開 ${dayLabel(avoid.at,now,zone)} ${hm.format(avoid.at)} 前後：${avoid.reason}",color=p.error,fontSize=14.sp,lineHeight=20.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("依預報的降雨機率、體感、風速與紫外線比較${if(activity==OutdoorMode.LAUNDRY)"，晾衣另看濕度" else ""}；是相對建議，不是安全保證。",
                    color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp)
            }

            // 2. What the other numbers mean.
            Text("現在的天氣細節",color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))
            val dew=WeatherInsightMath.dewPoint(weather.temperatureC,weather.humidityPct)
            val beaufort=WeatherInsightMath.beaufort(weather.windKmh)
            val direction=weather.windDirectionDeg?.takeIf{it in 0..360}?.let{listOf("北","東北","東","東南","南","西南","西","西北")[((it+22)%360)/45]}
            TileGrid(listOf(
                Tile("風",number(weather.windKmh,"km/h"),listOfNotNull(direction?.let{"${it}風"},beaufort?.let{"$it 級"},beaufort?.let{windWord(it)}).joinToString(" · ").ifBlank{"未提供"}),
                Tile("陣風",number(weather.windGustKmh,"km/h"),weather.windGustKmh?.let{if(it>=50)"騎車、撐傘要小心" else if(it>=30)"偶有較強陣風" else "陣風不大"}?:"未提供"),
                Tile("濕度",weather.humidityPct?.let{"$it%"}?:"未提供",dew?.let{"露點 ${it.roundToInt()}° · ${mugginess(it)}"}?:"未提供"),
                Tile("體感",number(weather.feelsLikeC,"°C"),weather.feelsLikeC?.let{f->val d=f-weather.temperatureC;if(d>=2)"比氣溫熱 ${d.roundToInt()}°" else if(d<=-2)"比氣溫冷 ${(-d).roundToInt()}°" else "和氣溫差不多"}?:"未提供"),
                Tile("氣壓",number(weather.pressureHpa,"hPa"),weather.pressureHpa?.let{if(it<1000)"偏低，天氣可能不穩" else if(it>1020)"偏高，天氣較穩定" else "正常範圍"}?:"未提供"),
                Tile("能見度",number(weather.visibilityM?.div(1000),"km"),weather.visibilityM?.let{if(it<1000)"很差，開車留意" else if(it<5000)"稍差" else "良好"}?:"未提供"),
            ))
            // 3. Everyday indices.
            val life=remember(weather,now){LifeIndex.build(weather,now)}
            if(life.isNotEmpty()) {
                Text("生活指數",color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))
                Text("綠色放心、橘色留意、紅色最好避免；著涼風險與防曬看的是今天要不要多準備。",color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp)
                Column(Modifier.testTag("insights-life"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    life.chunked(2).forEachIndexed{rowIndex,row->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed{i,item->
                                val tint=when(item.level){LifeIndex.Level.GOOD->goodColor;LifeIndex.Level.FAIR->fairColor;LifeIndex.Level.POOR->poorColor}
                                GlassSurface(Modifier.weight(1f).fillMaxHeight().iosAppear(rowIndex*2+i)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text(item.title,color=p.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.weight(1f))
                                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tint))
                                    }
                                    Text(item.word,color=tint,fontSize=21.sp,fontWeight=FontWeight.Bold)
                                    Text(item.reason,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp)
                                }
                            }
                            if(row.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            GlassSurface(Modifier.testTag("insights-air")) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("空氣品質",color=p.onSurface,fontSize=18.sp,fontWeight=FontWeight.Bold)
                        Text("模式估算 · 非環境部官方測站 AQI",color=p.onSurfaceVariant,fontSize=14.sp)
                    }
                    val aqi=weather.usAqi
                    Column(horizontalAlignment=Alignment.End) {
                        Text(aqi?.toString()?:"—",color=p.onSurface,fontSize=28.sp,fontWeight=FontWeight.Bold)
                        Text(aqi?.let{aqiWord(it)}?:"未提供",color=aqi?.let{aqiColor(it)}?:p.onSurfaceVariant,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Pollutant("PM₂.₅",weather.pm25);Pollutant("PM₁₀",weather.pm10);Pollutant("臭氧 O₃",weather.ozone)
                Pollutant("二氧化氮 NO₂",weather.nitrogenDioxide);Pollutant("二氧化硫 SO₂",weather.sulfurDioxide)
                Pollutant("一氧化碳 CO",weather.carbonMonoxide);Pollutant("揚塵",weather.airborneDust)
                Text("缺值顯示未提供，不代表零污染；這是網格估算，不是你所在街道的實測。",color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp,modifier=Modifier.padding(top=6.dp))
            }
            Text((if(weather.observationStation!=null)"目前溫濕風採中央氣象署測站實測；" else "")+"時段比較與露點由 App 依預報計算；官方警報請見警特報中心。",
                color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp)
        }
    }
}

private val goodColor=Color(0xFF34C77B)
private val fairColor=Color(0xFFF2B33D)
private val poorColor=Color(0xFFE5534B)

@Composable private fun HourStrip(hours:List<HourRating>,best:OutdoorWindow?,zone:ZoneId) {
    val p=MaterialTheme.colorScheme
    val h=DateTimeFormatter.ofPattern("H").withZone(zone)
    val bestHours=best?.hours?.map{it.at}?.toSet().orEmpty()
    val shown=hours.take(24)
    Column(Modifier.fillMaxWidth().testTag("insights-hour-strip")) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.Bottom) {
            shown.forEach{r->
                val color=when(r.level){HourLevel.GOOD->goodColor;HourLevel.FAIR->fairColor;HourLevel.POOR->poorColor}
                val tall by animateDpAsState(if(r.at in bestHours)28.dp else 20.dp,androidx.compose.animation.core.spring(.7f,380f),label="最佳時段柱")
                Box(Modifier.weight(1f).iosAppear(shown.indexOf(r)/3).height(tall).clip(RoundedCornerShape(5.dp))
                    .background(color.copy(alpha=if(r.at in bestHours)1f else .55f))
                    .semantics{contentDescription="${h.format(r.at)} 時 ${r.level.word}"})
            }
        }
        // One time label per six hours, each over its six-hour block.
        Row(Modifier.fillMaxWidth().padding(top=4.dp)) {
            shown.chunked(6).forEach{block->
                Text(h.format(block.first().at)+"時",color=p.onSurfaceVariant,fontSize=14.sp,maxLines=1,softWrap=false,
                    modifier=Modifier.weight(block.size.toFloat()))
            }
        }
    }
}

@Composable private fun Legend(color:Color,label:String) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
    }
}

@Composable private fun ReasonChips(w:OutdoorWindow,mode:OutdoorMode) {
    val chips=buildList {
        add("降雨 ${w.rain}%")
        add("${if(w.usesFeelsLike)"體感" else "氣溫"} ${w.temperature.roundToInt()}°")
        add(when{w.wind<12->"幾乎無風";w.wind<29->"微風";else->"風稍大"})
        w.uv?.let{add("紫外線 ${it.roundToInt()}")}
        if(mode==OutdoorMode.LAUNDRY)add("最高相對濕度 ${w.humidity?.let{"$it%"}?:"未提供"}")
    }
    val p=MaterialTheme.colorScheme
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        chips.forEachIndexed{i,c->
            Text(c,color=p.onSurface,fontSize=14.sp,fontWeight=FontWeight.Medium,
                modifier=Modifier.iosAppear(i+1).clip(RoundedCornerShape(999.dp)).background(p.primary.copy(alpha=.12f)).padding(horizontal=12.dp,vertical=6.dp))
        }
    }
}

private data class Tile(val label:String,val value:String,val meaning:String)

@Composable private fun TileGrid(tiles:List<Tile>) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach{row->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach{t->
                    GlassSurface(Modifier.weight(1f).fillMaxHeight().iosAppear(tiles.indexOf(t))) {
                        val p=MaterialTheme.colorScheme
                        Text(t.label,color=p.onSurfaceVariant,fontSize=14.sp)
                        Text(t.value,color=p.onSurface,fontSize=21.sp,fontWeight=FontWeight.Bold)
                        Text(t.meaning,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=18.sp)
                    }
                }
                if(row.size==1)Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun Pollutant(label:String,value:Double?) {
    val p=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
        Text(label,color=p.onSurfaceVariant,fontSize=15.sp,modifier=Modifier.weight(1f))
        Text(number(value,"μg/m³"),color=p.onSurface,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
    }
}

private fun dayLabel(at:Instant,now:Instant,zone:ZoneId):String {
    val d=at.atZone(zone).toLocalDate();val today=now.atZone(zone).toLocalDate()
    return when(d){today->"今天";today.plusDays(1)->"明天";else->DateTimeFormatter.ofPattern("M/d").format(d)}
}
private fun windWord(b:Int)=when{b<=1->"無風";b<=3->"微風";b<=5->"和風";b<=7->"強風";else->"非常強風"}
private fun mugginess(dew:Double)=when{dew>=24->"非常悶熱";dew>=20->"悶熱";dew>=16->"略潮濕";dew>=10->"舒適";else->"乾燥"}
private fun aqiWord(a:Int)=when{a<=50->"良好";a<=100->"普通";a<=150->"敏感族群不健康";a<=200->"不健康";a<=300->"非常不健康";else->"危害"}
private fun aqiColor(a:Int)=when{a<=50->Color(0xFF2E9D5B);a<=100->Color(0xFFB98500);a<=150->Color(0xFFE07A1F);a<=200->Color(0xFFD64541);else->Color(0xFF8E44AD)}
private fun number(value:Double?,unit:String)=value?.takeIf{it.isFinite()}?.let{String.format(Locale.TAIWAN,"%.1f",it)+if(unit.isBlank())"" else " $unit"}?:"未提供"
