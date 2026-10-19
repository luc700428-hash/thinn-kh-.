package io.atmos.weather.typhoon

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.alerts.CwaWarning
import io.atmos.weather.earthquake.CwaOfficialGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Official reports + CWA dataset view: an unavailable feed never becomes a fake forecast. */
@Composable
fun TyphoonCenterPage(warnings:List<CwaWarning>,onClose:()->Unit,
    initialBackup:List<GdacsCyclone> = emptyList(), primaryUnavailable:Boolean = false) {
    val context=LocalContext.current
    val link=LocalUriHandler.current
    val scope=rememberCoroutineScope()
    val typhoonWarnings=warnings.filter { it.title.contains("颱風") || it.summary.contains("颱風警報") }
    var fixes by remember { mutableStateOf<List<TyphoonFix>>(emptyList()) }
    var timelines by remember { mutableStateOf<List<StormTimeline>>(emptyList()) }
    var selectedTimeline by remember { mutableStateOf<StormTimeline?>(null) }
    val verifiedTracks=remember(fixes) { VerifiedTrackPlanner.verifiedForecastSegments(fixes.map {
        DatedTyphoonPosition(it.stormId,it.latitude,it.longitude,it.kind==TyphoonFix.Kind.FORECAST,it.timestamp)
    }) }
    var status by remember { mutableStateOf("尚未查詢 CWA 颱風資料") }
    var loading by remember { mutableStateOf(false) }
    fun refresh() {
        if(loading) return
        loading=true; status="查詢官方資料中…"
        scope.launch {
            try {
                val data=CwaOfficialGateway.read(context,"W-C0034-005")
                val points=OfficialTyphoonTrack.extract(data)
                fixes=points
                timelines=OfficialStormTimeline.parse(data)
                status=if(points.isEmpty()) "目前沒有可顯示的官方路徑" else ""
            } catch (cancel:CancellationException) {throw cancel}
            catch (_:Exception) {status="目前無法更新官方路徑，請稍後重新整理"}
            finally {loading=false}
        }
    }
    LaunchedEffect(Unit){refresh()}
    Column(Modifier.fillMaxSize().background(io.atmos.weather.ui.ThinnOpticalMaterial.page)
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal=20.dp,vertical=20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color.White.copy(alpha=.12f),RoundedCornerShape(24.dp))
                .clickable(onClick=onClose),contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack,"返回",tint=Color.White)
            }
            Text("颱風動向與警報",fontSize=26.sp,color=Color.White,modifier=Modifier.weight(1f))
            Box(Modifier.size(48.dp).background(Color.White.copy(alpha=.12f),RoundedCornerShape(24.dp))
                .clickable { refresh() },contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.Refresh,"重新查詢",tint=Color.White)
            }
        }
        Spacer(Modifier.height(15.dp))
        Text("中央氣象署 · 颱風預報",color=Color(0xFFAAE6FF),fontSize=17.sp)
        if(status.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(status,color=Color.White,fontSize=15.sp)
        }
        Spacer(Modifier.height(14.dp))
        if(fixes.isNotEmpty()) {
            OfficialTrackPlot(fixes,verifiedTracks)
            Spacer(Modifier.height(12.dp))
            Text("官方預報路徑 · 台灣、澎湖、金門、馬祖",color=Color(0xFFCDE7F3),fontSize=14.sp)
        }
        if(timelines.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            timelines.forEach { storm ->
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(vertical=5.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF3D7492),Color(0xFF1A3856))),RoundedCornerShape(22.dp))
                        .border(1.dp,Color.White.copy(alpha=.26f),RoundedCornerShape(22.dp))
                        .clickable { selectedTimeline=storm }.padding(19.dp)) {
                    Text("${storm.name} · 查看可拖曳預報時間軸  ↗",color=Color.White,fontSize=17.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth()
            .background(Color(0xFF183C56),RoundedCornerShape(22.dp))
            .padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("七級風暴風圈侵襲機率",color=Color.White,fontSize=18.sp,
                fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("官方機率尚未取得 · 不以單一路徑換算百分比",
                color=Color(0xFFD4EAF3),fontSize=14.sp)
            Text("機率並非颱風登陸機率；請查看氣象署最新機率圖。",
                color=Color(0xFFBDD8E9),fontSize=14.sp)
            TextButton(onClick={
                link.openUri("https://opendata.cwa.gov.tw/dataset/warning/W-C0034-003")
            }) { Text("查看官方侵襲機率圖 ↗",color=Color.White,fontSize=14.sp) }
        }
        if(typhoonWarnings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("最新官方颱風公告",color=Color.White,fontSize=18.sp)
        }
        typhoonWarnings.forEach { a ->
            Column(Modifier.fillMaxWidth().padding(vertical=6.dp)
                .background(Color.White.copy(alpha=.13f),RoundedCornerShape(22.dp))
                .clickable {link.openUri(a.link)}.padding(17.dp)) {
                Text(a.title,color=Color.White,fontSize=19.sp)
                Text(a.summary.take(190),color=Color(0xFFE1EFF7),fontSize=15.sp)
                Text("查看官方公告 ↗",color=Color(0xFF9FECFF),fontSize=15.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(55.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF4B8FAB),Color(0xFF27658A))),RoundedCornerShape(28.dp))
            .border(1.dp,Color.White.copy(alpha=.33f),RoundedCornerShape(28.dp))
            .clickable { link.openUri("https://www.cwa.gov.tw/V8/C/P/Typhoon/TY_WARN.html") }
            .padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.Center) {
            Icon(Icons.Rounded.OpenInNew,null,tint=Color.White)
            Spacer(Modifier.width(8.dp))
            Text("查看中央氣象署完整路徑圖",fontSize=17.sp,color=Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Text("路徑與警報以中央氣象署最新公告為準。",
            color=Color(0xFFCDE1ED),fontSize=14.sp)
    }
    selectedTimeline?.let { storm ->
        StormTimelineOverlay(storm) { selectedTimeline=null }
    }
}

@Composable
private fun OfficialTrackPlot(points:List<TyphoonFix>,verifiedTracks:List<List<DatedTyphoonPosition>>) {
    val displayPoints=remember(points) {
        points.filter { it.kind==TyphoonFix.Kind.OBSERVED }.takeLast(10) +
            points.filter { it.kind==TyphoonFix.Kind.FORECAST }.takeLast(16)
    }
    Canvas(Modifier.fillMaxWidth().height(290.dp)
        .background(Color(0xFF102D4A),RoundedCornerShape(18.dp))) {
        val pad=20.dp.toPx()
        val viewport=TyphoonViewport.forPoints(
            displayPoints.map {it.latitude to it.longitude}+
                TyphoonGeoBackdrop.keyAnchors.map { it.second to it.first },
            size.width,size.height,pad)
        with(TyphoonGeoBackdrop) { draw(viewport) }
        fun p(point:TyphoonFix):Offset {
            val (x,y)=viewport.project(point.longitude,point.latitude)
            return Offset(x,y)
        }
        for(i in 1..3) {
            val x=pad+(size.width-2*pad)*i/4
            val y=pad+(size.height-2*pad)*i/4
            drawLine(Color.White.copy(alpha=.13f),Offset(x,pad),Offset(x,size.height-pad),1.dp.toPx())
            drawLine(Color.White.copy(alpha=.13f),Offset(pad,y),Offset(size.width-pad,y),1.dp.toPx())
        }
        verifiedTracks.forEach { track ->
            for(i in 0 until track.size-1) {
                val from=track[i];val to=track[i+1]
                val (x1,y1)=viewport.project(from.longitude,from.latitude)
                val (x2,y2)=viewport.project(to.longitude,to.latitude)
                drawLine(Color(0xFF69BDF5),Offset(x1,y1),Offset(x2,y2),
                    strokeWidth=3.dp.toPx())
            }
        }
        // Land outlines above are geographic references; official forecasts remain independent.
        displayPoints.forEach { pt ->
            val c=when(pt.kind) {
                TyphoonFix.Kind.FORECAST->Color(0xFF69BDF5)
                TyphoonFix.Kind.OBSERVED->Color(0xFFFFCE82)
                TyphoonFix.Kind.UNSPECIFIED->Color(0xFFDDE6EF)
            }
            val at=p(pt)
            drawCircle(c,5.dp.toPx(),at)
            drawCircle(c.copy(alpha=.4f),9.dp.toPx(),at,style=Stroke(1.dp.toPx()))
        }
    }
}
