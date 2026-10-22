package io.atmos.weather.next.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.design.*
import io.atmos.weather.next.domain.*
import kotlin.math.roundToInt

private fun showTemp(temp:Double?,fahrenheit:Boolean):String = temp?.let {
    val value=if(fahrenheit) it*9.0/5+32 else it
    "${value.roundToInt()}°"
} ?: "—"
private fun condition(code:Int?):String = code?.let{WmoWeatherCodes.scene(it)?.emoji}?:"•"

/** City hero, scrolling hours, ten-day range, and source-aware detail panels. */
@Composable
fun IosWeatherPage(city:WeatherCity,reading:WeatherReading?,state:WeatherLoad?,loading:Boolean,
    fahrenheit:Boolean,onCity:(WeatherCity)->Unit,onRetry:()->Unit,onOpenSettings:()->Unit) {
    var cityList by remember { mutableStateOf(false) }
    BackHandler(cityList){cityList=false}
    if(cityList) {
        Column(Modifier.fillMaxSize().background(Color(0xFF080F1D)).padding(horizontal=22.dp)) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IosText("天氣",34,FontWeight.Bold,modifier=Modifier.weight(1f))
                Box(Modifier.clickable{onOpenSettings()}.padding(8.dp)){IosText("•••",23)}
                Box(Modifier.clickable{cityList=false}.padding(8.dp)){IosText("完成",16,color=Color(0xFF90CCFF))}
            }
            Spacer(Modifier.height(16.dp))
            IosText("我的城市",14,color=Color.White.copy(alpha=.65f))
            Spacer(Modifier.height(8.dp))
            KnownCities.cities.forEach { option ->
                Box(Modifier.fillMaxWidth().padding(vertical=6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF175B94),Color(0xFF25415F))))
                    .clickable {onCity(option);cityList=false}.padding(19.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            IosText(option.name,25,FontWeight.Medium)
                            IosText(if(option==city)"目前查看" else "點選查看當地天氣",14,color=Color.White.copy(alpha=.75f))
                        }
                        IosText(if(option==city)"✓" else "›",27)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            IosText("資料來自 Open-Meteo；城市列表不代表 GPS 已啟用。",14,color=Color.White.copy(alpha=.65f))
        }
        return
    }
    val label=when(state) {
        is WeatherLoad.Live->DataFreshnessPolicy.display(state.reading,System.currentTimeMillis(),false)
        is WeatherLoad.Cached->DataFreshnessPolicy.display(state.reading,System.currentTimeMillis(),true)
        is WeatherLoad.Unavailable->state.explanation
        null->"正在查詢真實天氣…"
    }
    val first=reading?.daily?.firstOrNull()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top=26.dp,bottom=34.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    IosText(city.name,35,FontWeight.Normal)
                    Spacer(Modifier.height(1.dp))
                    IosText(showTemp(reading?.temperatureC,fahrenheit),95,FontWeight.ExtraLight)
                    IosText(reading?.scene?.label?:if(loading)"載入中" else "暫無資料",19,color=Color.White.copy(alpha=.89f))
                    IosText(if(first!=null)"最高 ${showTemp(first.highC,fahrenheit)}  最低 ${showTemp(first.lowC,fahrenheit)}" else "最高 —  最低 —",17)
                    Spacer(Modifier.height(13.dp))
                    IosText(label,14,color=Color.White.copy(alpha=.86f))
                }
            }
            item {
                WeatherGlassCard(Modifier.fillMaxWidth()) {
                    IosText("目前天氣狀況來自實際來源。未能取得預報時，不會產生示意數值。",14,
                        color=Color.White.copy(alpha=.94f))
                    Spacer(Modifier.height(12.dp))
                    Spacer(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha=.27f)))
                    Spacer(Modifier.height(10.dp))
                    if(reading?.hourly.isNullOrEmpty()) IosText("暫時無法取得逐時預報",14)
                    else LazyRow(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                        items(reading!!.hourly){hour ->
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                IosText(hour.hourLabel,14,FontWeight.Medium)
                                Spacer(Modifier.height(14.dp))
                                IosText(condition(hour.wmoCode),25)
                                Spacer(Modifier.height(8.dp))
                                IosText(showTemp(hour.temperatureC,fahrenheit),17,FontWeight.SemiBold)
                                if(hour.rainChance!=null) IosText("${hour.rainChance}%",14,color=Color(0xFFA4DBFF))
                            }
                        }
                    }
                }
            }
            item {
                WeatherGlassCard(Modifier.fillMaxWidth()) {
                    IosText("▦  10 日天氣預報",14,color=Color.White.copy(alpha=.70f))
                    Spacer(Modifier.height(8.dp))
                    if(reading?.daily.isNullOrEmpty()) IosText("暫時無法取得每日預報",14)
                    else {
                        val days=reading!!.daily
                        val minimum=days.minOf{it.lowC};val maximum=days.maxOf{it.highC}
                        days.forEachIndexed { index, day ->
                            if(index>0) Spacer(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha=.22f)))
                            Row(Modifier.fillMaxWidth().height(43.dp),verticalAlignment=Alignment.CenterVertically) {
                                IosText(day.dayLabel,16,modifier=Modifier.width(47.dp))
                                IosText(condition(day.wmoCode),20,modifier=Modifier.width(36.dp))
                                IosText(showTemp(day.lowC,fahrenheit),16,color=Color.White.copy(alpha=.72f),modifier=Modifier.width(43.dp))
                                RangeBar(day.lowC,day.highC,minimum,maximum,Modifier.weight(1f).height(10.dp))
                                Spacer(Modifier.width(9.dp))
                                IosText(showTemp(day.highC,fahrenheit),16,modifier=Modifier.width(39.dp))
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(11.dp),modifier=Modifier.fillMaxWidth()) {
                    DetailCard("◉  體感溫度",showTemp(reading?.feelsLikeC,fahrenheit),"根據實際觀測",Modifier.weight(1f))
                    DetailCard("◌  濕度",reading?.humidityPct?.let{"$it%"}?:"—","相對濕度",Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(11.dp),modifier=Modifier.fillMaxWidth()) {
                    DetailCard("➤  風速",reading?.windKmh?.let{"${it.roundToInt()}"}?:"—","公里／小時",Modifier.weight(1f))
                    DetailCard("☂  降水量",reading?.precipitationMm?.let{"$it"}?:"—","毫米",Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.clickable{onRetry()}.padding(9.dp)) {
                        IosText(if(loading)"更新中…" else "↻ 重新整理",16,color=Color.White)
                    }
                    IosText("●  ${KnownCities.cities.indexOf(city)+1}/${KnownCities.cities.size}",14,color=Color.White.copy(alpha=.8f))
                    Box(Modifier.clickable{cityList=true}.padding(9.dp)) {IosText("☷ 城市列表",16)}
                }
            }
        }
    }
}
@Composable private fun RangeBar(low:Double,high:Double,min:Double,max:Double,modifier:Modifier) {
    Canvas(modifier) {
        drawLine(Color.White.copy(alpha=.18f),Offset(0f,size.height/2),Offset(size.width,size.height/2),strokeWidth=5.dp.toPx())
        val span=(max-min).coerceAtLeast(.1)
        val start=((low-min)/span).toFloat().coerceIn(0f,1f)*size.width
        val end=((high-min)/span).toFloat().coerceIn(0f,1f)*size.width
        drawLine(Brush.horizontalGradient(listOf(Color(0xFF7AD9CF),Color(0xFFFFC45F))),
            Offset(start,size.height/2),Offset(end.coerceAtLeast(start+1f),size.height/2),strokeWidth=5.dp.toPx())
    }
}
@Composable private fun DetailCard(label:String,value:String,subtitle:String,modifier:Modifier) {
    WeatherGlassCard(modifier.height(142.dp)) {
        IosText(label,14,color=Color.White.copy(alpha=.75f))
        Spacer(Modifier.height(11.dp))
        IosText(value,32,FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        IosText(subtitle,14,color=Color.White.copy(alpha=.80f))
    }
}
