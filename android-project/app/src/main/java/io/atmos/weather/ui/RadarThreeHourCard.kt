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
import androidx.compose.material3.Text
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
fun RadarThreeHourCard(weather:WeatherSnapshot,openCwaRadar:()->Unit) {
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
        Text("降雨實況與三小時預報",fontSize=20.sp,color=Color.White,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("即時雷達觀測 · 三小時模型預報",fontSize=14.sp,color=Color(0xFFDEEBF7))
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
            Text("中央氣象署雷達實際觀測圖 · ${if(age<15)"已取得近期圖資" else "快取 ${age} 分鐘前"}",fontSize=14.sp,color=Color.White)
            Text("藍色標記為選擇的城市位置；雷達回波不等於地面降雨量。",fontSize=14.sp,color=Color(0xFFCDDEEA))
        } else {
            Text(when {
                !supported -> "目前選取地點超出臺灣雷達圖範圍，僅提供氣象預報。"
                !attempted -> "正在讀取中央氣象署雷達觀測圖…"
                else -> "暫時無法取得雷達圖資；預報仍可獨立使用。"
            },fontSize=14.sp,color=Color(0xFFFFD6A8))
        }
        Spacer(Modifier.height(13.dp))
        Text("氣象預報 · 接下來三小時",fontSize=15.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
        Text(if(rain!=null) "最高降水機率：$rain%" else "降水機率：資料來源未提供",
            color=Color.White,fontSize=16.sp)
        Text(if(snow!=null) "最高降雪機率：$snow%" else "降雪機率：資料來源未提供",
            fontSize=14.sp,color=Color(0xFFE1EDF7))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            range.take(3).forEach { hour ->
                val time=SimpleDateFormat("HH:mm",Locale.TAIWAN).format(Date(hour.at.toEpochMilli()))
                val probability=hour.precipitationProbabilityPct
                Column(Modifier.weight(1f)
                    .background(Color.White.copy(alpha=.09f),RoundedCornerShape(18.dp))
                    .padding(vertical=13.dp,horizontal=6.dp),
                    horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(time,color=Color.White,fontSize=14.sp)
                    Text(if((probability ?: 0)>=45) "☁☂" else "☁",fontSize=27.sp,color=Color(0xFFB5EAFF))
                    Text(probability?.let { "$it%" } ?: "—",color=Color.White,fontSize=17.sp,
                        fontWeight=FontWeight.Bold)
                    Text(hour.temperatureC?.let { "${it.toInt()}°" } ?: "—",
                        fontSize=14.sp,color=Color(0xFFE1EDF7))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassAction("歷史 2 小時降雨雷達", "RainViewer 實況圖層、時間軸，每 5 分鐘檢查",onClick={showHistory=true})
        Spacer(Modifier.height(8.dp))
        GlassAction("開啟中央氣象署資料", "官方雷達觀測來源與原始時間",onClick=openCwaRadar)
        Spacer(Modifier.height(5.dp))
        Text("雷達為歷史觀測；未來三小時是數值預報，並非衛星預測雲圖。",fontSize=14.sp,color=Color(0xFFD9E8F1))
    }
    if (showHistory) RadarHistoryDialog(onClose={showHistory=false})
}
