package io.atmos.weather.ocean

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable fun MarinePage(city:WeatherCity,onClose:()->Unit) {
    val palette=MaterialTheme.colorScheme
    var place by remember(city){mutableStateOf(MarineRepository.nearest(city))}
    var picker by remember{mutableStateOf(false)};var refresh by remember{mutableIntStateOf(0)}
    var data by remember{mutableStateOf<MarineSnapshot?>(null)}
    var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)}
    var index by remember{mutableIntStateOf(0)}
    LaunchedEffect(place,refresh) {
        loading=true;error=null
        if(data?.place?.id!=place.id){data=null;index=0}
        try {data=MarineRepository.read(place,refresh>0);index=0}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){error="目前無法取得海象資料，請重試";data=null}
        finally{loading=false}
    }
    val records=remember(data){data?.let{listOf(it.current)+it.hours.filter{h->h.at>it.current.at}}.orEmpty()}
    val focus=records.getOrNull(index)?:data?.current
    val clock=DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei"))
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)){IOSSettingsHeader("海象與浪況",onClose)}
            GlassIconButton({refresh++},enabled=!loading,modifier=Modifier.padding(end=12.dp)){Icon(Icons.Rounded.Refresh,"更新海象預報")}
        }
        Column(Modifier.weight(1f).appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(top=12.dp,bottom=128.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            GlassSurface {
                GlassAction(place.name,"切換臺灣近海、東岸與離島海域"){picker=true}
                Text("近海模式預報 · 海域與陸地氣溫分開計算",fontSize=14.sp,color=palette.onSurfaceVariant)
            }
            if(loading)IOSLinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            error?.let{Text(it,color=palette.error,fontSize=15.sp)}
            data?.let{snapshot->
                if(!snapshot.fresh())Text("目前顯示較舊的海象資料，請先更新再安排行程。",color=palette.error,fontSize=14.sp)
                OceanHero(focus)
                GlassSurface {
                    Text("${if(index==0)"目前模式時刻" else "預報時刻"} · ${focus?.at?.let(clock::format)?:"未提供"}",color=palette.onSurface,fontSize=17.sp)
                    if(records.size>1)GlassSlider(index.toFloat(),{index=it.roundToInt().coerceIn(records.indices)},Modifier.fillMaxWidth(),valueRange=0f..records.lastIndex.toFloat(),steps=(records.size-2).coerceAtLeast(0))
                    MarineChart(records,index)
                    Text("拖曳時間檢視未來浪況；圖上空缺表示來源未提供。",fontSize=14.sp,color=palette.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MarineMetric("有效波高",focus?.waveHeight.unit("m"),"較大三分之一波浪的平均高度",Modifier.weight(1f))
                    MarineMetric("波浪週期",focus?.wavePeriod.unit("秒"),"波浪之間的時間",Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MarineMetric("湧浪高度",focus?.swellHeight.unit("m"),"由遠方傳來的長浪",Modifier.weight(1f))
                    MarineMetric("湧浪週期",focus?.swellPeriod.unit("秒"),"長浪週期",Modifier.weight(1f))
                }
                GlassSurface {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        BearingCompass(focus?.waveDirection,"波浪來向",Modifier.size(144.dp))
                        Column(Modifier.weight(1f)) {
                            Text("浪從哪裡來",fontSize=18.sp,color=palette.onSurface)
                            Text("${CompassDirections.name(focus?.waveDirection)} ${focus?.waveDirection?.roundToInt()?.let{"$it°"}?:""}",fontSize=22.sp,color=palette.primary)
                            Text("湧浪來向：${CompassDirections.name(focus?.swellDirection)}",fontSize=14.sp,color=palette.onSurfaceVariant)
                        }
                    }
                }
                GlassSurface {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        BearingCompass(focus?.currentDirection,"海流流向",Modifier.size(144.dp))
                        Column(Modifier.weight(1f)) {
                            Text("海流流往",fontSize=18.sp,color=palette.onSurface)
                            Text(CompassDirections.name(focus?.currentDirection),fontSize=22.sp,color=palette.primary)
                            Text("流速 ${focus?.currentMs.unit("m/s")}",fontSize=15.sp,color=palette.onSurface)
                            Text("與浪向的「來向」分開標示",fontSize=14.sp,color=palette.onSurfaceVariant)
                        }
                    }
                }
                MarineMetric("海面溫度",focus?.seaTemperature.unit("°C"),"海洋表層模式溫度",Modifier.fillMaxWidth())
                Text("資料：Open-Meteo Marine · 取得於 ${clock.format(snapshot.fetchedAt)}\n網格 ${"%.3f".format(snapshot.latitude)}, ${"%.3f".format(snapshot.longitude)}\n海象為網格模式預報，港內、礁岸與潮流可能有明顯差異；不作航海或下水安全判定。",color=palette.onSurfaceVariant,fontSize=14.sp)
            }
        }
    }
    if(picker)GlassModalBottomSheet(onDismissRequest={picker=false}) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=500.dp).padding(horizontal=16.dp).navigationBarsPadding()) {
            item{Text("選擇海域",fontSize=23.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=palette.onSurface,modifier=Modifier.padding(14.dp))}
            items(MarineRepository.places,key={it.id}){candidate->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(candidate.id==place.id)palette.primaryContainer else Color.Transparent)
                    .iosJellyPress().clickable{place=candidate;picker=false}.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(candidate.name,color=if(candidate.id==place.id)palette.onPrimaryContainer else palette.onSurface,modifier=Modifier.weight(1f),fontSize=17.sp)
                    if(candidate.id==place.id)Icon(Icons.Rounded.Check,"目前選擇",tint=palette.onPrimaryContainer)
                }
            }
        }
    }
}
private fun Double?.unit(unit:String)=this?.takeIf{it.isFinite()}?.let{"%.1f".format(it)+" $unit"}?:"未提供"
@Composable private fun MarineMetric(title:String,value:String,note:String,modifier:Modifier) {
    val p=MaterialTheme.colorScheme
    GlassSurface(modifier) {
        Text(title,color=p.onSurfaceVariant,fontSize=14.sp)
        Text(value,color=p.onSurface,fontSize=26.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text(note,color=p.onSurfaceVariant,fontSize=14.sp)
    }
}
@Composable private fun OceanHero(record:MarineHour?) {
    val reduce=rememberAppleReduceMotion()
    val transition=rememberInfiniteTransition(label="ocean highlight")
    val phase=transition.animateFloat(0f,1f,infiniteRepeatable(tween(8000,easing=LinearEasing)),label="ocean swell")
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Color(0xFF084878),Color(0xFF117A9C))))) {
        Canvas(Modifier.matchParentSize()) {
            val t=if(reduce)0f else phase.value
            repeat(3){layer->
                val path=Path();val baseline=size.height*(.62f+layer*.10f)
                path.moveTo(0f,size.height)
                for(x in 0..120){val px=x/120f*size.width;val wave=sin((x/120f*2+ t+layer*.23)*2*PI).toFloat()
                    val y=baseline+wave*(8+((record?.waveHeight?:0.0)*2).coerceAtMost(12.0)).toFloat()*density
                    path.lineTo(px,y)
                };path.lineTo(size.width,size.height);path.close()
                drawPath(path,listOf(Color(0x445CCFE6),Color(0x6677E1E8),Color(0x8876D9E7))[layer])
            }
        }
        Column(Modifier.padding(22.dp)) {
            Text("有效波高",color=Color.White.copy(alpha=.9f),fontSize=16.sp)
            Text(record?.waveHeight.unit("m"),color=Color.White,fontSize=44.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("${CompassDirections.name(record?.waveDirection)}來浪 · 週期 ${record?.wavePeriod.unit("秒")}",color=Color.White,fontSize=15.sp)
        }
    }
}
@Composable private fun MarineChart(records:List<MarineHour>,selected:Int) {
    val p=MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val max=(records.mapNotNull{it.waveHeight}.maxOrNull()?:1.0).coerceAtLeast(.5)
        val h=size.height-12*density;var previous:Offset?=null
        records.forEachIndexed {i,r->val value=r.waveHeight
            if(value==null)previous=null else {
                val at=Offset(i.toFloat()/records.lastIndex.coerceAtLeast(1)*size.width,(h-value/max*(h-12*density)).toFloat())
                previous?.let{drawLine(p.primary,it,at,2*density)};previous=at
                if(i==selected)drawCircle(p.primary,4*density,at)
            }
        }
    }
}
