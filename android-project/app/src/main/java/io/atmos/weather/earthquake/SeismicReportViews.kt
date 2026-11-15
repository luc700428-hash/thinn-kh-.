package io.atmos.weather.earthquake

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassButton
import io.atmos.weather.ui.GlassOutlinedButton
import io.atmos.weather.ui.ThinnOpticalMaterial
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Earthquake reports in the layout people know from DPIP, drawn in thinn-khì's own glass:
 * a coloured intensity tile leads every row, the epicentre reads first, then time, magnitude
 * and depth; the detail page puts the county map, the numbers and every station on one page.
 */
private val taipei=ZoneId.of("Asia/Taipei")
internal fun reportTime(report:EarthquakeReport,pattern:String="MM/dd HH:mm")=report.origin.atZone(taipei).format(DateTimeFormatter.ofPattern(pattern))
private fun gradeOf(raw:String?)=raw?.removeSuffix("級")?.trim()?.takeIf{it.isNotBlank()}

/** CWA intensity tile: the colour of the scale, the numeral on top (5−/5+ for 弱/強). */
@Composable internal fun SeismicIntensityTile(raw:String?,size:Dp=48.dp,modifier:Modifier=Modifier) {
    val grade=gradeOf(raw)
    val fill=SeismicIntensityColors.color(grade)
    val ink=if(fill.luminance()<.36f)Color.White else Color(0xFF10201B)
    Box(modifier.size(size).clip(RoundedCornerShape(size*.28f)).background(fill)
        .semantics{contentDescription=grade?.let{"震度 $it 級"}?:"震度未提供"},contentAlignment=Alignment.Center) {
        Text(SeismicDisplayFormat.symbol(grade),color=ink,fontWeight=FontWeight.Bold,
            fontSize=(size.value*.46f).sp,lineHeight=(size.value*.5f).sp,textAlign=TextAlign.Center)
    }
}

@Composable internal fun SeismicReportRow(report:EarthquakeReport,onClick:()->Unit) {
    val palette=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role=Role.Button,onClick=onClick)
        .padding(horizontal=10.dp,vertical=10.dp).heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
        SeismicIntensityTile(report.maxIntensity)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(report.shortLocation,color=palette.onSurface,fontSize=17.sp,fontWeight=FontWeight.SemiBold,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("${reportTime(report)} · 深度 ${SeismicDisplayFormat.depth(report.depthKm)}",color=palette.onSurfaceVariant,fontSize=14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment=Alignment.End) {
            Text(SeismicDisplayFormat.magnitude(report.magnitude),color=palette.onSurface,fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text(if(report.category=="小區域有感")"小區域" else report.number?.let{"第 $it 號"}?:"有感",color=palette.onSurfaceVariant,fontSize=14.sp)
        }
    }
}

/** The full report list with DPIP-style filters. */
@Composable internal fun SeismicReportsPanel(reports:List<EarthquakeReport>,loading:Boolean,error:String?,source:String,
    onRefresh:()->Unit,onOpen:(EarthquakeReport)->Unit) {
    val palette=MaterialTheme.colorScheme
    var filter by rememberSaveable{mutableStateOf("全部")}
    val filters=listOf("全部","顯著有感","小區域","震度 3 以上","規模 5 以上")
    val shown=reports.filter{r->when(filter){
        "顯著有感"->r.category!="小區域有感"
        "小區域"->r.category=="小區域有感"
        "震度 3 以上"->CwaEarthquakeParser.intensityRank(r.maxIntensity?:"")>=3
        "規模 5 以上"->r.magnitude>=5.0
        else->true
    }}.sortedByDescending{it.origin}
    Column(Modifier.fillMaxWidth().testTag("seismic-report-list"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("地震報告",color=palette.onSurface,fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text(source,color=palette.onSurfaceVariant,fontSize=14.sp)
            }
            io.atmos.weather.ui.GlassTextButton(onClick=onRefresh,enabled=!loading){Text(if(loading)"更新中" else "更新")}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            filters.forEach{f->
                val on=f==filter
                Text(f,color=if(on)palette.onPrimary else palette.onSurface,fontSize=14.sp,fontWeight=if(on)FontWeight.SemiBold else FontWeight.Normal,
                    modifier=Modifier.clip(RoundedCornerShape(999.dp)).background(if(on)palette.primary else palette.onSurface.copy(alpha=.07f))
                        .clickable(role=Role.Tab){filter=f}.padding(horizontal=14.dp,vertical=8.dp))
            }
        }
        if(error!=null)Text(error,color=palette.error,fontSize=14.sp)
        if(loading&&reports.isEmpty())Text("正在取得最新地震報告…",color=palette.onSurfaceVariant,fontSize=14.sp)
        else if(shown.isEmpty())Text(if(reports.isEmpty())"尚未取得地震報告，請檢查網路連線" else "沒有符合「$filter」的地震",
            color=palette.onSurfaceVariant,fontSize=14.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ThinnOpticalMaterial.surface)) {
            shown.forEachIndexed{i,r->
                if(i>0)Box(Modifier.fillMaxWidth().padding(start=70.dp).height(.5.dp).background(palette.onSurface.copy(alpha=.10f)))
                SeismicReportRow(r){onOpen(r)}
            }
        }
        Text("震後報告為地震發生後的官方紀錄，並非預警。",color=palette.onSurfaceVariant,fontSize=14.sp)
    }
}

/** Compact "latest earthquake" card for the monitor tab. */
@Composable internal fun SeismicLatestReportCard(report:EarthquakeReport?,loading:Boolean,onOpen:(EarthquakeReport)->Unit,onAll:()->Unit) {
    val palette=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ThinnOpticalMaterial.surface).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("最新地震報告",color=palette.onSurface,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            io.atmos.weather.ui.GlassTextButton(onClick=onAll){Text("全部報告")}
        }
        if(report==null)Text(if(loading)"正在取得…" else "尚無報告",color=palette.onSurfaceVariant,fontSize=14.sp)
        else SeismicReportRow(report){onOpen(report)}
    }
}

/** Full report page: tile and headline, key numbers, county map, intensity by county and station. */
@Composable internal fun SeismicReportDetailPage(report:EarthquakeReport,place:SeismicPlace?,onClose:()->Unit,onReplay:(EarthquakeReport)->Unit) {
    BackHandler(onBack=onClose)
    val palette=MaterialTheme.colorScheme
    val uri=LocalUriHandler.current
    val counties=report.countyIntensity
    val localCounty=place?.name?.replace('台','臺')?.let{n->counties.keys.firstOrNull{n.contains(it)}}
    val incident=remember(report.id){EewIncident(id="report-"+report.id,serial=0,source="CWA",issuedAtMs=report.origin.toEpochMilli(),
        epicenter=report.location,magnitude=report.magnitude,depthKm=report.depthKm,latitude=report.latitude,longitude=report.longitude,
        cancelled=false,test=false,originAtMs=null,localIntensityByCounty=counties,maximumIntensity=report.maxIntensity)}
    val frame=remember{mutableLongStateOf(System.currentTimeMillis())}
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().testTag("seismic-report-detail")) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=12.dp),contentAlignment=Alignment.Center) {
            io.atmos.weather.ui.IOS27RoundControl("‹",onClick=onClose,modifier=Modifier.align(Alignment.CenterStart),
                fill=palette.surface,foreground=palette.onSurface)
            Text(if(report.category=="小區域有感")"小區域有感地震" else "地震報告",color=palette.onSurface,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal=16.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                SeismicIntensityTile(report.maxIntensity,size=72.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(report.shortLocation,color=palette.onSurface,fontSize=24.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp)
                    Text(reportTime(report,"yyyy/MM/dd HH:mm:ss"),color=palette.onSurfaceVariant,fontSize=15.sp)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("規模" to SeismicDisplayFormat.magnitude(report.magnitude),"深度" to SeismicDisplayFormat.depth(report.depthKm),
                    "最大震度" to (gradeOf(report.maxIntensity)?.let{SeismicDisplayFormat.symbol(it)+" 級"}?:"—"),
                    "編號" to (report.number?:"—")).forEach{(k,v)->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(ThinnOpticalMaterial.surface).padding(vertical=12.dp),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(v,color=palette.onSurface,fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=1)
                        Text(k,color=palette.onSurfaceVariant,fontSize=14.sp)
                    }
                }
            }
            if(localCounty!=null)Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ThinnOpticalMaterial.surface).padding(14.dp),
                verticalAlignment=Alignment.CenterVertically) {
                SeismicIntensityTile(counties[localCounty],size=40.dp);Spacer(Modifier.width(12.dp))
                Text("你所在的$localCounty 最大震度 ${SeismicDisplayFormat.symbol(counties[localCounty])} 級",color=palette.onSurface,fontSize=16.sp)
            }
            SeismicWarningMap(incident,counties,place,historical=false,frameNow=frame,viewportHeight=340.dp,
                subtitle="各縣市實測最大震度",observed=true)
            Text(report.location,color=palette.onSurfaceVariant,fontSize=14.sp)
            Text(report.reportText,color=palette.onSurface,fontSize=16.sp,lineHeight=23.sp)
            // Intensity by county, then every station, highest first.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ThinnOpticalMaterial.surface).padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("各地震度",color=palette.onSurface,fontSize=18.sp,fontWeight=FontWeight.Bold)
                if(counties.isEmpty())Text("此報告未附各地震度",color=palette.onSurfaceVariant,fontSize=14.sp)
                counties.entries.sortedByDescending{CwaEarthquakeParser.intensityRank(it.value)}.forEach{(county,grade)->
                    val stations=report.stations.filter{it.county.replace('台','臺')==county}
                    Row(verticalAlignment=Alignment.Top) {
                        SeismicIntensityTile(grade,size=36.dp);Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(county,color=palette.onSurface,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                            if(stations.isNotEmpty())Text(stations.take(8).joinToString("、"){"${it.name} ${SeismicDisplayFormat.symbol(it.intensity)}"},
                                color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp)
                        }
                    }
                }
            }
            if(report.stations.isNotEmpty())Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ThinnOpticalMaterial.surface).padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("測站紀錄（${report.stations.size} 站）",color=palette.onSurface,fontSize=18.sp,fontWeight=FontWeight.Bold)
                report.stations.take(40).forEach{st->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        SeismicIntensityTile(st.intensity,size=28.dp);Spacer(Modifier.width(10.dp))
                        Text("${st.county} ${st.name}",color=palette.onSurface,fontSize=15.sp,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(st.distanceKm?.let{"%.1f km".format(Locale.US,it)},st.pgaGal?.let{"%.1f gal".format(Locale.US,it)}).joinToString(" · "),
                            color=palette.onSurfaceVariant,fontSize=14.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                GlassButton(onClick={onReplay(report)},modifier=Modifier.weight(1f)){Text("歷史速報回放")}
                GlassOutlinedButton(onClick={runCatching{uri.openUri(report.officialUrl)}},modifier=Modifier.weight(1f)){Text("氣象署報告")}
            }
            if(report.imageUrl!=null||report.shakemapUrl!=null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                report.imageUrl?.let{url->GlassOutlinedButton(onClick={runCatching{uri.openUri(url)}},modifier=Modifier.weight(1f)){Text("震度分布圖")}}
                report.shakemapUrl?.let{url->GlassOutlinedButton(onClick={runCatching{uri.openUri(url)}},modifier=Modifier.weight(1f)){Text("等震度圖")}}
            }
            Text("資料來源：${if(report.number!=null)"中央氣象署地震報告" else "地震報告"} · 震後紀錄，並非預警。",color=palette.onSurfaceVariant,fontSize=14.sp)
        }
    }
}

/** Highest live station intensity per county in the last frame, DPIP's monitor summary. */
@Composable internal fun SeismicCountyMaxPanel(stations:List<SeismicStation>) {
    val palette=MaterialTheme.colorScheme
    val byCounty=stations.filter{(it.intensity?:0.0)>=0.5}
        .groupBy{st->SeismicWarningCounties.counties.firstOrNull{st.label.replace('台','臺').contains(it)}?:""}
        .filterKeys{it.isNotBlank()}.mapValues{(_,v)->v.maxOf{it.intensity?:0.0}}
        .entries.sortedByDescending{it.value}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ThinnOpticalMaterial.surface).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("各縣市即時最大震度",color=palette.onSurface,fontSize=17.sp,fontWeight=FontWeight.Bold)
        if(byCounty.isEmpty())Text("目前各地無明顯搖晃",color=palette.onSurfaceVariant,fontSize=14.sp)
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            byCounty.take(12).forEach{(county,value)->
                val grade=SeismicLiveIntensity.grade(value)
                Column(Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp,palette.onSurface.copy(alpha=.08f),RoundedCornerShape(16.dp))
                    .padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    SeismicIntensityTile(grade,size=36.dp);Spacer(Modifier.height(4.dp))
                    Text(county,color=palette.onSurface,fontSize=14.sp)
                }
            }
        }
    }
}

/** Instrumental intensity (TREM I value) to the CWA scale. */
internal object SeismicLiveIntensity {
    fun grade(i:Double):String=when {
        i<0.5->"0";i<1.5->"1";i<2.5->"2";i<3.5->"3";i<4.5->"4"
        i<5.0->"5弱";i<5.5->"5強";i<6.0->"6弱";i<6.5->"6強";else->"7"
    }
}
