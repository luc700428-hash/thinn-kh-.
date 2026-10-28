package io.atmos.weather.sky

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.location.LocationLabels
import io.atmos.weather.ocean.CompassDirections
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable fun SkyPlannerPage(city:WeatherCity,timeZoneId:String?,onClose:()->Unit) {
    SkyPlannerScreen(city,timeZoneId,onClose,Dispatchers.Default)
}
@Composable internal fun SkyPlannerScreen(city:WeatherCity,timeZoneId:String?,onClose:()->Unit,computationDispatcher:CoroutineDispatcher) {
    val base=MaterialTheme.colorScheme
    val dark=base.background.luminance()<.5f
    MaterialTheme(colorScheme=base.copy(primary=if(dark)Color(0xFF90D7FF) else Color(0xFF0066CC),
        onSurfaceVariant=if(dark)Color(0xFFC7C7CC) else Color(0xFF5C5C64))) {
        CompositionLocalProvider(LocalWeatherCardPalette provides null){SkyPlannerContent(city,timeZoneId,onClose,computationDispatcher)}
    }
}
@Composable private fun SkyPlannerContent(city:WeatherCity,timeZoneId:String?,onClose:()->Unit,computationDispatcher:CoroutineDispatcher) {
    val p=MaterialTheme.colorScheme
    val zone=remember(timeZoneId){runCatching{ZoneId.of(timeZoneId?:"Asia/Taipei")}.getOrDefault(ZoneId.systemDefault())}
    val owner=LocalLifecycleOwner.current
    var now by remember{mutableStateOf(Instant.now())}
    LaunchedEffect(owner){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){now=Instant.now();delay(30_000)}}}
    var live by remember{mutableStateOf(true)}
    var chosenDate by remember{mutableStateOf(now.atZone(zone).toLocalDate())}
    var chosenMinute by remember{mutableIntStateOf(now.atZone(zone).hour*60+now.atZone(zone).minute)}
    val date=if(live)now.atZone(zone).toLocalDate() else chosenDate
    val minute=if(live)now.atZone(zone).hour*60+now.atZone(zone).minute else chosenMinute
    val at=if(live)now.atZone(zone) else date.atTime(minute/60,minute%60).atZone(zone)
    val position by produceState<Result<SkyPosition>?>(null,city,at) {
        value=withContext(computationDispatcher){runCatching{SkyEphemeris.position(city,at)}}
    }
    val dayResult by produceState<Result<SkyDay>?>(null,city,date,zone){
        value=null
        value=withContext(computationDispatcher){runCatching{SkyEphemeris.day(city,date,zone)}}
    }
    val day=dayResult?.getOrNull()
    val week by produceState<List<SkyPosition>>(emptyList(),city,date,zone) {
        value=withContext(computationDispatcher){(0..6).mapNotNull{i->runCatching{SkyEphemeris.position(city,date.plusDays(i.toLong()).atTime(12,0).atZone(zone))}.getOrNull()}}
    }
    val data=position?.getOrNull()
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader("日月與月相",onClose)
        Column(Modifier.weight(1f).appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(top=12.dp,bottom=128.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(LocationLabels.microcity(city.name),fontSize=17.sp,color=p.onSurface)
            SkySurface {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    GlassIconButton({chosenDate=date.minusDays(1);chosenMinute=minute;live=false}){Icon(Icons.Rounded.ChevronLeft,"前一天")}
                    Text(date.format(DateTimeFormatter.ofPattern("MM月dd日 EEEE",java.util.Locale.TAIWAN)),fontSize=17.sp,color=p.onSurface,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    GlassIconButton({chosenDate=date.plusDays(1);chosenMinute=minute;live=false}){Icon(Icons.Rounded.ChevronRight,"後一天")}
                }
                Text("${at.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${if(live)"目前時間" else "所選時間"}",fontSize=15.sp,color=p.onSurfaceVariant)
                GlassSlider(minute.toFloat(),{chosenDate=date;chosenMinute=it.roundToInt().coerceIn(0,1439);live=false},Modifier.fillMaxWidth(),valueRange=0f..1439f)
                if(!live)GlassTextButton({live=true}){Text("回到現在")}
            }
            if(data==null) {
                if(position?.isFailure==true)Text("目前無法計算此地的日月位置，請切換日期或地點。",color=p.error,fontSize=15.sp)
                else IOSLinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            } else {
                SkySurface {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        MoonPhaseArt(data.fraction,data.phase,Modifier.size(145.dp))
                        Column(Modifier.weight(1f)) {
                            AnimatedContent(SkyEphemeris.phaseName(data.phase),label="lunar phase name"){name->Text(name,fontSize=26.sp,color=p.onSurface,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)}
                            Text("照明 ${(data.fraction*100).roundToInt()}%",fontSize=17.sp,color=p.primary)
                            Text(if(data.moonAltitude>=0)"目前在地平線上" else "目前在地平線下",fontSize=14.sp,color=p.onSurfaceVariant)
                        }
                    }
                    Text("月相示意 · 依所選地點與時間計算",fontSize=14.sp,color=p.onSurfaceVariant)
                }
                SkySurface {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        BearingCompass(data.moonAzimuth,"月亮方位",Modifier.size(145.dp))
                        Column(Modifier.weight(1f)) {
                            Text("月亮方位",fontSize=18.sp,color=p.onSurface)
                            Text("${CompassDirections.name(data.moonAzimuth)} ${data.moonAzimuth.roundToInt()}°",fontSize=21.sp,color=p.primary)
                            Text("仰角 ${"%.1f".format(data.moonAltitude)}°",fontSize=15.sp,color=p.onSurface)
                            Text("正北 0° · 正東 90°",fontSize=14.sp,color=p.onSurfaceVariant)
                        }
                    }
                }
                SkySurface {
                    val time=DateTimeFormatter.ofPattern("HH:mm")
                    if(dayResult==null)IOSLinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    else if(day==null)Text("目前無法計算當日出沒時間",color=p.error,fontSize=15.sp)
                    else {
                        SkyTimeRow("日出",day.sunrise?.format(time)?:"當日無日出","日落",day.sunset?.format(time)?:"當日無日落")
                        Spacer(Modifier.height(16.dp))
                        SkyTimeRow("月出",day.moonrise?.format(time)?:"當日無月出","月沒",day.moonset?.format(time)?:"當日無月沒")
                    }
                    if(day?.sunrise!=null&&day.sunset!=null) {
                        val length=Duration.between(day.sunrise,day.sunset).toMinutes()
                        Spacer(Modifier.height(12.dp));Text("日長 ${length/60} 小時 ${length%60} 分鐘",fontSize=14.sp,color=p.onSurfaceVariant)
                    }
                }
                SkySurface {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        BearingCompass(data.sunAzimuth,"太陽方位",Modifier.size(145.dp))
                        Column(Modifier.weight(1f)) {
                            Text("太陽方位",fontSize=18.sp,color=p.onSurface)
                            Text("${CompassDirections.name(data.sunAzimuth)} ${data.sunAzimuth.roundToInt()}°",fontSize=21.sp,color=p.primary)
                            Text("仰角 ${"%.1f".format(data.sunAltitude)}°",fontSize=15.sp,color=p.onSurface)
                        }
                    }
                }
                SkySurface {
                    Text("接下來七天",fontSize=18.sp,color=p.onSurface)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(15.dp)) {
                        week.forEach {item->Column(Modifier.width(70.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            MoonPhaseArt(item.fraction,item.phase,Modifier.size(62.dp))
                            Text(item.at.format(DateTimeFormatter.ofPattern("MM/dd")),color=p.onSurface,fontSize=14.sp)
                            Text("${(item.fraction*100).roundToInt()}%",color=p.onSurfaceVariant,fontSize=14.sp)
                        }}
                    }
                }
                Text("月地距離約 ${data.moonDistanceKm.roundToInt()} 公里\n時間依 ${zone.id} 顯示；山勢、雲層與大氣會影響實際可見情形。方位圖以正北為上方。",color=p.onSurfaceVariant,fontSize=14.sp)
            }
        }
    }
}
@Composable private fun SkySurface(content:@Composable ColumnScope.()->Unit) {
    val p=MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides p.onSurface) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface)
            .thinnSpecularEdge(24.dp).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable private fun SkyTimeRow(left:String,leftValue:String,right:String,rightValue:String) {
    val p=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf(left to leftValue,right to rightValue).forEach {(label,value)->Column(Modifier.weight(1f)) {
            Text(label,fontSize=14.sp,color=p.onSurfaceVariant);Text(value,fontSize=24.sp,color=p.onSurface)
        }}
    }
}
@Composable fun MoonPhaseArt(fraction:Double,phase:Double,modifier:Modifier=Modifier) {
    val reduce=rememberAppleReduceMotion()
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val lit by animateFloatAsState(fraction.toFloat().coerceIn(0f,1f),tween(if(reduce)0 else 500,easing=AppleMotion.fluid),label="lunar terminator")
    Canvas(modifier) {
        val r=minOf(size.width,size.height)*.34f;val c=Offset(size.width/2,size.height/2)
        drawCircle(Brush.radialGradient(listOf(Color(0x554C8FCB),Color.Transparent),c,r*1.48f),r*1.48f,c)
        drawCircle(Brush.radialGradient(if(dark)listOf(Color(0xFF708EAE),Color(0xFF435E7E))
            else listOf(Color(0xFF415975),Color(0xFF1C2C45)),c,r),r,c)
        val path=Path();val sign=if(phase<0)1 else -1
        for(i in 0..100){val y=-r+2*r*i/100;val x=sqrt((r*r-y*y).coerceAtLeast(0f))*sign
            if(i==0)path.moveTo(c.x+x,c.y+y) else path.lineTo(c.x+x,c.y+y)}
        for(i in 100 downTo 0){val y=-r+2*r*i/100;val x=sqrt((r*r-y*y).coerceAtLeast(0f))*(1-2*lit)*sign
            path.lineTo(c.x+x,c.y+y)};path.close()
        drawPath(path,Brush.radialGradient(listOf(Color(0xFFFFF4D0),Color(0xFFE0E7E7),Color(0xFFABC9DE)),c-Offset(r*.2f,r*.3f),r*1.6f))
        if(dark)drawCircle(Color(0xFFB7CBDD),r,c,style=androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        listOf(Triple(-.35f,-.25f,.14f),Triple(.25f,.30f,.18f),Triple(-.05f,.50f,.09f)).forEach {(x,y,s)->
            drawCircle(Color(0x20516F8F),r*s,c+Offset(x*r,y*r))}
    }
}
