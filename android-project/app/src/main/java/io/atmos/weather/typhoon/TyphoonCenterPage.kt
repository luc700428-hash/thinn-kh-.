package io.atmos.weather.typhoon

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Official reports + CWA dataset view: an unavailable feed never becomes a fake forecast. */
@Composable
fun TyphoonCenterPage(warnings:List<CwaWarning>,onClose:()->Unit) {
    val context=LocalContext.current
    val link=LocalUriHandler.current
    val scope=rememberCoroutineScope()
    val typhoonWarnings=warnings.filter { it.title.contains("颱風") || it.summary.contains("颱風警報") }
    var fixes by remember { mutableStateOf<List<TyphoonFix>>(emptyList()) }
    var googlePath by remember { mutableStateOf<List<WeatherLabLocalCsv.Point>>(emptyList()) }
    var googleMessage by remember { mutableStateOf("Google Weather Lab：尚未匯入資料") }
    var confirmedSameStorm by remember { mutableStateOf(false) }
    val importCsv=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            runCatching {
                val csv=withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readNBytes(1_000_001).also { require(it.size <= 1_000_000) {"CSV 超過 1 MB"} }
                            .toString(Charsets.UTF_8)
                    } ?: error("無法讀取檔案")
                }
                val parsed=WeatherLabLocalCsv.parse(csv)
                withContext(Dispatchers.IO) { context.openFileOutput("weatherlab_last.csv",0).use {
                    it.write(csv.toByteArray(Charsets.UTF_8))
                } }
                parsed
            }.onSuccess { googlePath=it;confirmedSameStorm=false
                googleMessage="已載入 ${it.size} 個 AI 預報位置（本機匯入；不是即時串流）"
            }.onFailure { googleMessage="匯入失敗：${it.localizedMessage?.take(120)}" }
        }
    }
    var lastUpdate by remember { mutableStateOf("") }
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
                val data=CwaOfficialGateway.read(context,"W-C0034-002")
                val points=OfficialTyphoonTrack.extract(data)
                fixes=points
                lastUpdate=java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Taipei"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                status=if(points.isEmpty()) "目前官方回覆無可驗證的颱風座標；不顯示虛構路徑" else "官方座標 ${points.size} 筆 · 僅顯示官方資料回傳內容"
            } catch (cancel:CancellationException) {throw cancel}
            catch (ex:Exception) {status="官方資料暫時無法查詢：${ex.localizedMessage?.take(100) ?: "連線失敗"}"}
            finally {loading=false}
        }
    }
    LaunchedEffect(Unit){
        withContext(Dispatchers.IO) {
            runCatching { context.openFileInput("weatherlab_last.csv").bufferedReader().use { it.readText() } }
                .getOrNull()?.takeIf { it.length <= 1_000_000 }
        }?.let { text ->
            runCatching {WeatherLabLocalCsv.parse(text)}.onSuccess {
                googlePath=it;googleMessage="上次匯入 ${it.size} 個預報位置（非即時）"
            }
        }
        refresh()
    }
    val singleOfficialStorm=remember(fixes) {
        fixes.filter {it.kind==TyphoonFix.Kind.FORECAST && it.stormId.isNotBlank()}
            .map {it.stormId}.distinct().singleOrNull()
    }
    val comparisons=remember(fixes,googlePath,confirmedSameStorm,singleOfficialStorm) {
        if(!confirmedSameStorm || singleOfficialStorm==null) emptyList() else
            V25StormComparison.compare(
                fixes.filter {it.kind==TyphoonFix.Kind.FORECAST && it.stormId==singleOfficialStorm}
                    .map {V25StormComparison.Position(it.longitude,it.latitude,it.timestamp)},
                googlePath.map {V25StormComparison.Position(it.longitude,it.latitude,it.time)})
    }
    Column(Modifier.fillMaxSize().background(io.atmos.weather.ui.ThinnOpticalMaterial.page)
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal=20.dp,vertical=20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onClose) { Icon(Icons.Rounded.ArrowBack,"返回",tint=Color.White) }
            Text("颱風動向與警報",fontSize=26.sp,color=Color.White,modifier=Modifier.weight(1f))
            IconButton(onClick={refresh()}) { Icon(Icons.Rounded.Refresh,"重新查詢",tint=Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        Text("中央氣象署 · 官方資料",color=Color(0xFFAAE6FF),fontSize=20.sp)
        Spacer(Modifier.height(8.dp))
        Text(status,color=Color.White,fontSize=16.sp,lineHeight=22.sp)
        if(lastUpdate.isNotEmpty()) Text("App 查詢：$lastUpdate（不代表官方發布時間）",color=Color(0xFFCCE0EA),fontSize=14.sp)
        Spacer(Modifier.height(14.dp))
        if(fixes.isNotEmpty()) {
            OfficialTrackPlot(fixes,verifiedTracks,if(confirmedSameStorm && singleOfficialStorm!=null) googlePath else emptyList())
            verifiedTracks.firstOrNull()?.takeIf {it.size>=2}?.let {track ->
                val a=track[track.lastIndex-1];val b=track.last()
                Text(TyphoonDirectionZh.from(a.latitude,a.longitude,b.latitude,b.longitude),
                    color=Color(0xFFB5EAFE),fontSize=16.sp)
            }
            Spacer(Modifier.height(7.dp))
            Text(if(verifiedTracks.isEmpty()) "僅有點位，尚無足夠、可核對的同一颱風預報時間序列；不畫虛構路徑。"
                else "藍線：符合時序與速度檢查、同一颱風、具有預報時間的 ${verifiedTracks.size} 段路徑；藍點預報、橘點觀測、灰點待確認。",
                color=Color(0xFFC7DBEA),fontSize=14.sp,lineHeight=20.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Google Weather Lab · 實驗預報對照",color=Color.White,fontSize=19.sp)
        Spacer(Modifier.height(5.dp))
        Text(googleMessage,color=Color(0xFFC8E5F8),fontSize=14.sp)
        Spacer(Modifier.height(7.dp))
        io.atmos.weather.ui.GlassTextButton(onClick={importCsv.launch(arrayOf("text/*","application/vnd.ms-excel","application/octet-stream"))},modifier=Modifier.fillMaxWidth()) {
            Text("在 App 內匯入 Google Weather Lab CSV",color=Color.White)
        }
        if(googlePath.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth().clickable { confirmedSameStorm=!confirmedSameStorm }
                .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(if(confirmedSameStorm) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                    contentDescription=null,tint=Color(0xFF9FE3F9))
                Spacer(Modifier.width(8.dp))
                Text("我確認匯入資料與官方資料為同一颱風",color=Color.White,fontSize=14.sp)
            }
            val message=when {
                !confirmedSameStorm -> "請確認同一颱風後才進行路徑比較"
                singleOfficialStorm==null -> "目前無法從官方資料確認唯一颱風，不進行比較"
                comparisons.isEmpty() -> "官方與 AI 沒有足夠、可對齊的相同預報時刻"
                else -> "已比較 ${comparisons.size} 個預報時刻；末筆中心距離約 ${comparisons.last().distanceKm} 公里"
            }
            Text(message,color=Color(0xFFCEE7F7),fontSize=14.sp)
            Text("AI 路徑僅供研究；警報與撤離以中央氣象署為準。",color=Color.White,fontSize=14.sp)
            Text("清除匯入資料",color=Color(0xFF9DE7F4),fontSize=15.sp,
                modifier=Modifier.clickable {context.deleteFile("weatherlab_last.csv");googlePath=emptyList();confirmedSameStorm=false;googleMessage="已清除"}.padding(vertical=8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("颱風公告 ${typhoonWarnings.size} 則",color=Color.White,fontSize=20.sp)
        if(typhoonWarnings.isEmpty()) Text("目前沒有從已讀取的 RSS 確認颱風公告；不代表全臺無颱風。",
            color=Color.White,fontSize=16.sp)
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
        Button(onClick={link.openUri("https://www.cwa.gov.tw/V8/C/P/Typhoon/TY_WARN.html")},
            modifier=Modifier.fillMaxWidth().height(55.dp)) {
            Icon(Icons.Rounded.OpenInNew,null)
            Spacer(Modifier.width(8.dp))
            Text("查看中央氣象署完整路徑圖",fontSize=17.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("目前為官方資料點位檢視，尚非完整風圈／預測錐。發布及通知可能受到官方更新時間、網路與 Android 省電限制；重大警報以官方災防管道為準。",
            color=Color(0xFFCDE1ED),fontSize=15.sp,lineHeight=22.sp)
    }
}

@Composable
private fun OfficialTrackPlot(points:List<TyphoonFix>,verifiedTracks:List<List<DatedTyphoonPosition>>,ai:List<WeatherLabLocalCsv.Point>) {
    Canvas(Modifier.fillMaxWidth().height(220.dp)
        .background(Color(0xFF102D4A),RoundedCornerShape(18.dp))) {
        val pad=20.dp.toPx()
        val viewport=TyphoonViewport.forPoints(points.map {it.latitude to it.longitude} + ai.map {it.latitude to it.longitude},size.width,size.height,pad)
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
                    strokeWidth=2.dp.toPx())
            }
        }
        if(ai.size>=2) ai.zipWithNext().forEach { (from,to) ->
            val a=viewport.project(from.longitude,from.latitude)
            val b=viewport.project(to.longitude,to.latitude)
            drawLine(Color(0xFFD4A6FF),Offset(a.first,a.second),Offset(b.first,b.second),strokeWidth=2.dp.toPx())
        }
        val (twX,twY)=viewport.project(121.0,23.6)
        if (twX in pad..(size.width-pad) && twY in pad..(size.height-pad)) {
            drawCircle(Color(0xFFA6F2EE),3.dp.toPx(),Offset(twX,twY))
            drawCircle(Color(0xFFA6F2EE).copy(alpha=.4f),7.dp.toPx(),Offset(twX,twY),
                style=Stroke(1.dp.toPx()))
        }
        points.forEach { pt ->
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
