package io.atmos.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import io.atmos.weather.ui.AppText as Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.domain.model.WeatherSnapshot
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Radar observation + 3-hour weather MODEL comparison. Neither is mislabelled as nowcasting. */
@Composable
fun RadarThreeHourCard(weather:WeatherSnapshot) {
    val cards=LocalWeatherCardPalette.current
    val ink=cards?.ink?:MaterialTheme.colorScheme.onSurface
    val muted=cards?.secondary?:if(MaterialTheme.colorScheme.background.red>.5f)Color(0xFF4B5563) else Color(0xFFC7D4E0)
    val blue=cards?.accent?:MaterialTheme.colorScheme.primary
    val warning=if(cards!=null)Color(0xFFFFD6A8) else if(MaterialTheme.colorScheme.background.red>.5f)Color(0xFF8A4300) else Color(0xFFFFD6A8)
    val lat=weather.city.coordinate.latitude
    val lon=weather.city.coordinate.longitude
    val supported=CwaRadarObservation.coverage(lat,lon)
    var radar by remember { mutableStateOf<RadarImage?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    LaunchedEffect(supported,lat,lon) {
        attempted=false;radar=null
        if(supported) radar=CwaRadarObservation.latest()
        attempted=true
    }
    val start=Instant.now()
    val range=weather.hourly.filter { !it.at.isBefore(start.minusSeconds(900)) && it.at.isBefore(start.plus(3,ChronoUnit.HOURS)) }
    val rain=range.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()
    val snow=range.mapNotNull { it.snowfallProbabilityPct }.maxOrNull()
    GlassSurface {
        Text("降雨實況與三小時預報",fontSize=20.sp,color=ink,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("即時雷達觀測 · 三小時模型預報",fontSize=14.sp,color=muted)
        Spacer(Modifier.height(11.dp))
        if(supported && radar!=null) {
            val observation=radar!!
            Box(Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp,Color.White.copy(alpha=.32f),RoundedCornerShape(18.dp))) {
                Image(observation.bitmap.asImageBitmap(),contentDescription="中央氣象署雷達回波圖；顯示觀測，不是三小時預測",
                    modifier=Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                Box(Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(9.dp)
                    .background(Color(0xD40B2944),RoundedCornerShape(12.dp))
                    .padding(horizontal=11.dp,vertical=6.dp)) {
                    Text("☁  雷達回波 · 觀測",fontSize=14.sp,color=Color.White)
                }
                Canvas(Modifier.fillMaxSize()) {
                    val (x,y)=CwaRadarObservation.mapPoint(lat,lon)
                    val point=Offset(x*size.width,y*size.height)
                    drawCircle(Color(0xFF19354D),radius=11.dp.toPx(),center=point)
                    drawCircle(Color(0xFFFFFFFF),radius=7.dp.toPx(),center=point,style=Stroke(3.dp.toPx()))
                    drawCircle(Color(0xFF63DFFD),radius=3.dp.toPx(),center=point)
                }
            }
            Spacer(Modifier.height(8.dp))
            val age=(System.currentTimeMillis()-observation.fetchedAt)/60000
            Text("中央氣象署雷達實際觀測圖 · ${if(age<15)"已取得近期圖資" else "快取 ${age} 分鐘前"}",fontSize=14.sp,color=ink)
            Text("藍色標記為選擇城市的示意位置；雷達回波不等於地面降雨量。",fontSize=14.sp,color=muted)
        } else {
            Text(when {
                !supported -> "選取地點超出中央氣象署雷達圖範圍，可開啟下方的 App 內雷達。"
                !attempted -> "正在讀取中央氣象署雷達觀測圖…"
                else -> "暫時無法取得雷達圖資；預報仍可獨立使用。"
            },fontSize=14.sp,color=warning)
        }
        Spacer(Modifier.height(13.dp))
        Text("氣象預報 · 接下來三小時",fontSize=15.sp,color=ink,fontWeight=FontWeight.SemiBold)
        Text(if(rain!=null) "最高降水機率：$rain%" else "降水機率：資料來源未提供",
            color=ink,fontSize=16.sp)
        Text(if(snow!=null) "最高降雪機率：$snow%" else "降雪機率：資料來源未提供",
            fontSize=14.sp,color=muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            range.take(3).forEach { hour ->
                val time=SimpleDateFormat("HH:mm",Locale.TAIWAN).format(Date(hour.at.toEpochMilli()))
                val probability=hour.precipitationProbabilityPct
                Column(Modifier.weight(1f)
                    .background(blue.copy(alpha=.07f),RoundedCornerShape(18.dp))
                    .padding(vertical=13.dp,horizontal=6.dp),
                    horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(time,color=ink,fontSize=14.sp)
                    Text(if((probability ?: 0)>=45) "☁☂" else "☁",fontSize=27.sp,color=blue)
                    Text(probability?.let { "$it%" } ?: "—",color=ink,fontSize=17.sp,
                        fontWeight=FontWeight.Bold)
                    Text(hour.temperatureC?.let { "${it.toInt()}°" } ?: "—",
                        fontSize=14.sp,color=muted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassAction("在 App 內操作降雨雷達", "近 2 小時回波 · 縮放、拖曳與播放",onClick={showHistory=true})
        Spacer(Modifier.height(5.dp))
        Text("雷達為歷史觀測；未來三小時是數值預報，並非衛星預測雲圖。",fontSize=14.sp,color=muted)
    }
    if (showHistory) CompositionLocalProvider(LocalWeatherCardPalette provides null) {
        RadarHistoryDialog(onClose={showHistory=false},latitude=lat,longitude=lon)
    }
}
