package io.atmos.weather.earthquake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/** Incident sheet with a strict source label. The arrival countdown is deliberately
 * unavailable unless a verified local estimate is in the incoming provider payload.
 * A schematic wave animation must never pretend to be predictive seismology.
 */
@Composable
fun SeismicWarningOverlay(incident:EewIncident,replay:Boolean,userCounty:String?,onClose:()->Unit) {
    val county=userCounty.orEmpty().replace('台','臺')
    val local=incident.localIntensityByCounty.entries.firstOrNull {
        county.contains(it.key) || it.key.contains(county) && county.isNotEmpty()
    }?.value
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            if(replay) Color(0xFF24354B) else Color(0xFF6C271F), Color(0xFF091829))))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal=22.dp,vertical=20.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(replay) "歷史預警回顧 · 模擬畫面" else "第三方即時地震速報",
                color=if(replay) Color(0xFFFFD38F) else Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text(if(replay) "過去真實歷史資料回放，並非現在發生地震" else "僅在前景收到有效來源資料時顯示，不是官方 PWS",
                fontSize=15.sp,color=Color(0xFFF1E1DD),textAlign=TextAlign.Center)
            Spacer(Modifier.height(15.dp))
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0x80203248),RoundedCornerShape(25.dp)),
                contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                    val shape=Path()
                    SeismicGeo.island.forEachIndexed {i,p ->
                        val pt=Offset(SeismicGeo.x(p.first)*size.width, SeismicGeo.y(p.second)*size.height)
                        if(i==0) shape.moveTo(pt.x,pt.y) else shape.lineTo(pt.x,pt.y)
                    }
                    shape.close()
                    drawPath(shape,Color(0xFF28647A))
                    drawPath(shape,Color(0xFF81D8F2),style=Stroke(2.dp.toPx()))
                    SeismicGeo.outlyingIslands.forEach { island ->
                        val islandPath=Path()
                        island.points.forEachIndexed {index,(longitude,latitude) ->
                            val x=SeismicGeo.x(longitude)*size.width
                            val y=SeismicGeo.y(latitude)*size.height
                            if(index==0)islandPath.moveTo(x,y) else islandPath.lineTo(x,y)
                        }
                        islandPath.close()
                        drawPath(islandPath,Color(0xFF28647A))
                        drawPath(islandPath,Color(0xFF81D8F2),style=Stroke(1.dp.toPx()))
                    }
                    val lat=incident.latitude;val lon=incident.longitude
                    if(SeismicGeo.visible(lat,lon)) {
                        val center=Offset(SeismicGeo.x(lon!!)*size.width,SeismicGeo.y(lat!!)*size.height)
                        drawCircle(Color(0xFFFFB57A),24.dp.toPx(),center,style=Stroke(3.dp.toPx()))
                        drawCircle(Color.White,5.dp.toPx(),center)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(incident.epicenter,fontSize=24.sp,color=Color.White,fontWeight=FontWeight.Bold)
            Text("規模 ${incident.magnitude?.let { "M${"%.1f".format(Locale.US,it)}" } ?: "尚未提供"} · " +
                "深度 ${incident.depthKm?.let {"${"%.0f".format(Locale.US,it)} km"} ?: "尚未提供"}",
                fontSize=18.sp,color=Color.White)
            Spacer(Modifier.height(6.dp))
            Text("所在地預估震度：${local ?: "來源未提供"}",fontSize=18.sp,color=Color(0xFFFFDFAF))
            Text("震波到達倒數：缺少可驗證資料，不顯示虛構秒數",fontSize=15.sp,
                color=Color(0xFFFFDFAF),textAlign=TextAlign.Center)
            Spacer(Modifier.height(17.dp))
            Text("趴下　掩護　穩住",fontSize=29.sp,fontWeight=FontWeight.ExtraBold,
                color=Color.White,textAlign=TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick=onClose,modifier=Modifier.fillMaxWidth().height(58.dp)) {
                Text(if(replay)"返回歷史回放" else "我知道了",fontSize=19.sp)
            }
        }
    }
}
