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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.atmos.weather.maps.rememberNlscMap
import io.atmos.weather.maps.NlscTerrain
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
    initialBackup:List<GdacsCyclone> = emptyList(), primaryUnavailable:Boolean = false,
    currentLat:Double=Double.NaN,currentLon:Double=Double.NaN,currentName:String="目前位置") {
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
    var status by remember { mutableStateOf("尚未更新颱風資訊") }
    var loading by remember { mutableStateOf(false) }
    var officialProbability by remember { mutableStateOf<CwaWindProbability.Snapshot?>(null) }
    var probabilityLoading by remember { mutableStateOf(false) }
    var googlePath by remember { mutableStateOf<List<WeatherLabLocalCsv.Position>>(emptyList()) }
    var googleMessage by remember { mutableStateOf("尚未匯入 Google Weather Lab 路徑") }
    var sameStormConfirmed by remember { mutableStateOf(false) }
    val officialStormIds = remember(fixes) {
        fixes.filter { it.kind == TyphoonFix.Kind.FORECAST }
            .map { it.stormId }.filter { it.isNotBlank() }.distinct()
    }
    val modelDifferences = remember(fixes, googlePath, sameStormConfirmed) {
        if (!sameStormConfirmed || officialStormIds.size != 1) emptyList()
        else V25StormComparison.compare(
            fixes.filter { it.kind == TyphoonFix.Kind.FORECAST && it.stormId == officialStormIds[0] }
                .map { V25StormComparison.Position(it.longitude,it.latitude,it.timestamp) },
            googlePath.map { V25StormComparison.Position(it.longitude,it.latitude,it.time) })
    }
    val weatherLabPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) scope.launch {
            try {
                val csv=withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes=stream.readNBytes(1_000_001)
                        require(bytes.size<=1_000_000) { "檔案超過 1 MB" }
                        bytes.toString(Charsets.UTF_8)
                    } ?: error("無法讀取 CSV")
                }
                val parsed=WeatherLabLocalCsv.parse(csv)
                googlePath=parsed
                sameStormConfirmed=false
                // Store only in this app's private files; never upload research data.
                withContext(Dispatchers.IO) {
                    runCatching { context.openFileOutput("weatherlab_last.csv",android.content.Context.MODE_PRIVATE)
                        .use { it.write(csv.toByteArray(Charsets.UTF_8)) } }
                }
                googleMessage="已匯入 ${googlePath.size} 個 AI 預測座標（本機資料，非即時更新）"
            } catch(_:Exception) {
                googlePath=emptyList()
                googleMessage="無法讀取路徑，請確認是 Weather Lab 匯出的 CSV"
            }
        }
    }
    fun refresh() {
        if(loading) return
        loading=true; status="查詢官方資料中…"
        scope.launch {
            probabilityLoading=true
            try { officialProbability=CwaWindProbability.read() }
            catch (cancel:CancellationException) { throw cancel }
            catch (_:Exception) { officialProbability=null }
            finally { probabilityLoading=false }
        }
        scope.launch {
            try {
                val data=CwaOfficialGateway.read(context,"W-C0034-005")
                val points=OfficialTyphoonTrack.extract(data)
                fixes=points
                timelines=OfficialStormTimeline.parse(data)
                status=if(points.isEmpty()) "目前沒有可顯示的官方路徑" else ""
            } catch (cancel:CancellationException) {throw cancel}
            catch (_:Exception) {
                status=if(fixes.isNotEmpty())
                    "無法連接官方颱風資料；保留上次取得的路徑，非即時資訊，也不代表現在仍有警報。"
                else "目前無法更新官方路徑，請稍後重新整理"
            }
            finally {loading=false}
        }
    }
    LaunchedEffect(Unit){
        val cached=withContext(Dispatchers.IO) {
            runCatching { context.openFileInput("weatherlab_last.csv")
                .bufferedReader().use { it.readText() } }.getOrNull()
        }
        if (!cached.isNullOrBlank() && cached.length <= 1_000_000) {
            runCatching { WeatherLabLocalCsv.parse(cached) }.onSuccess {
                googlePath=it
                googleMessage="上次匯入的 AI 預測資料（非即時資料）"
            }
        }
        refresh()
    }
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
        if(fixes.isNotEmpty() || googlePath.isNotEmpty()) {
            if (googlePath.isEmpty()) OfficialTrackPlot(fixes,verifiedTracks)
            else OfficialTrackPlot(fixes,verifiedTracks,googlePath)
            Spacer(Modifier.height(12.dp))
            Text(if(fixes.isNotEmpty()) "官方預報路徑 · 台灣、澎湖、金門、馬祖" else "Weather Lab 本機匯入路徑 · 尚無官方路徑",                color=Color(0xFFCDE7F3),fontSize=14.sp)
        }
        Spacer(Modifier.height(15.dp))
        Column(Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha=.075f),RoundedCornerShape(26.dp))
            .border(1.dp,Color.White.copy(alpha=.15f),RoundedCornerShape(26.dp))
            .padding(17.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome,null,tint=Color(0xFF9CE9FF))
                Spacer(Modifier.width(9.dp))
                Text("Google Weather Lab · AI 預測對照",fontSize=17.sp,color=Color.White)
            }
            Text("官方路徑以中央氣象署為準。Google 是實驗性 AI 預測，僅供比較。",
                fontSize=14.sp,color=Color(0xFFCEE2F2))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha=.13f))
                    .clickable { link.openUri("https://deepmind.google.com/science/weatherlab") }
                    .padding(13.dp),contentAlignment=Alignment.Center) {
                    Text("開啟 Weather Lab ↗",color=Color.White,fontSize=14.sp)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF275C73))
                    .clickable { weatherLabPicker.launch(arrayOf("*/*")) }
                    .padding(13.dp),contentAlignment=Alignment.Center) {
                    Text("匯入 CSV 對照",color=Color.White,fontSize=14.sp)
                }
            }
            Text(googleMessage,color=Color(0xFFCEE2F2),fontSize=14.sp)
            if (googlePath.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clickable { sameStormConfirmed=!sameStormConfirmed }
                    .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(sameStormConfirmed) Icons.Rounded.CheckBox
                        else Icons.Rounded.CheckBoxOutlineBlank,
                        "確認匯入資料所屬颱風",tint=Color(0xFFA6E4FA))
                    Spacer(Modifier.width(8.dp))
                    Text("確認 CSV 與目前官方颱風是同一個颱風",color=Color.White,
                        fontSize=14.sp,modifier=Modifier.weight(1f))
                }
                val comparisonText=when {
                    !sameStormConfirmed -> "請先確認兩組資料屬於同一個颱風，才比較路徑距離。"
                    officialStormIds.size != 1 -> "目前無法唯一辨識官方颱風；不顯示可能誤導的比較。"
                    modelDifferences.isEmpty() -> "兩組預測時刻沒有對齊；不產生推測數值。"
                    else -> "相同預報時刻的最新路徑位置差距：約 ${modelDifferences.last().distanceKm} 公里；共 ${modelDifferences.size} 個可比對時刻。"
                }
                Text(comparisonText,color=Color(0xFFE0F5FD),fontSize=14.sp)
                Text("只顯示中心座標差距，不代表哪一個模式一定較準。",
                    color=Color(0xFFCEE2F2),fontSize=14.sp)
                Text("移除本機 Weather Lab 路徑資料",color=Color(0xFF9DE1FA),
                    fontSize=14.sp,modifier=Modifier.clickable {
                        googlePath=emptyList()
                        sameStormConfirmed=false
                        context.deleteFile("weatherlab_last.csv")
                        googleMessage="尚未匯入 Google Weather Lab 路徑"
                    }.padding(vertical=8.dp))
            }
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
            .padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("七級風暴風圈侵襲機率",color=Color.White,fontSize=19.sp,
                fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
            val risks = listOf(
                Triple(currentName,currentLon,currentLat),
                Triple("臺灣中部參考點",120.98,23.76),
                Triple("澎湖參考點",119.57,23.57),
                Triple("金門參考點",118.38,24.44),
                Triple("馬祖參考點",119.95,26.17)
            ).distinctBy { it.first }
            risks.chunked(2).forEach { group ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    group.forEach { (name,lon,lat) ->
                        Column(Modifier.weight(1f)
                            .background(Color.White.copy(alpha=.08f),RoundedCornerShape(12.dp))
                            .padding(11.dp)) {
                            Text(name,color=Color(0xFFDBEAF4),fontSize=14.sp,maxLines=2)
                            Text(officialProbability?.at(lon,lat) ?: "未提供",color=Color.White,
                                fontSize=20.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                    if(group.size==1) Spacer(Modifier.weight(1f))
                }
            }
            val probabilityMessage = when {
                probabilityLoading -> "正在取得最新侵襲機率…"
                officialProbability != null -> "未提供表示此地不在可核對的官方區間內，不代表 0%。"
                CwaWindProbability.fetchState == CwaWindProbability.FetchState.STALE -> "官方機率資料已過期，請稍後更新。"
                CwaWindProbability.fetchState == CwaWindProbability.FetchState.NO_USABLE_REGIONS -> "目前沒有可辨識的官方機率區域。"
                else -> "暫時無法讀取官方機率資料，請稍後重試。"
            }
            Text(probabilityMessage,color=Color(0xFFBDD8E9),fontSize=14.sp)
            Text("此為七級風暴風圈侵襲機率；參考點不代表整區平均，也不是登陸機率。",
                color=Color(0xFFBDD8E9),fontSize=14.sp)
            Text("資料更新後會顯示官方區間；可至氣象署查詢最新資訊。",color=Color(0xFFBDD8E9),fontSize=14.sp)
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
private fun OfficialTrackPlot(points:List<TyphoonFix>,verifiedTracks:List<List<DatedTyphoonPosition>>,
    googlePath:List<WeatherLabLocalCsv.Position> = emptyList()) {
    val displayPoints=remember(points) {
        points.filter { it.kind==TyphoonFix.Kind.OBSERVED }.takeLast(10) +
            points.filter { it.kind==TyphoonFix.Kind.FORECAST }.takeLast(16)
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var mapZoom by remember { mutableFloatStateOf(1f) }
    var mapPan by remember { mutableStateOf(Offset.Zero) }
    val pxPad=with(LocalDensity.current) { 20.dp.toPx() }
    val reference=displayPoints.map { it.latitude to it.longitude } +
        googlePath.map { it.latitude to it.longitude } +
        TyphoonGeoBackdrop.keyAnchors.map { it.second to it.first }
    val baseline=remember(reference,canvasSize,pxPad) {
        TyphoonViewport.forPoints(reference,canvasSize.width.toFloat().coerceAtLeast(1f),
            canvasSize.height.toFloat().coerceAtLeast(1f),pxPad)
    }
    // Geographic camera: every pointer move reprojects the vectors and CWA track at native pixels.
    // Camera is rendered through projection; request a fresh WMS BBOX after gestures settle.
    val view=remember(baseline,mapZoom,mapPan) {
        val scale=baseline.pxPerKm*mapZoom
        TyphoonViewport(
            baseline.originXKm - mapPan.x/scale,
            baseline.originYKm + mapPan.y/scale,
            scale,baseline.widthPx,baseline.heightPx,baseline.cosCenter)
    }
    val bounds=if(canvasSize.width>60 && canvasSize.height>60) view.rasterBounds() else null
    val settledBounds by produceState<io.atmos.weather.maps.NlscTerrain.Bounds?>(
        null,bounds) {
        value=null
        if(bounds!=null) { delay(420); value=bounds }
    }
    val terrain=rememberNlscMap(settledBounds,canvasSize.width,canvasSize.height)
    val rasterReady=settledBounds==bounds && terrain!=null
    Box(Modifier.fillMaxWidth().height(430.dp)
        .clip(RoundedCornerShape(25.dp))
        .background(Color(0xFF0B253B))
        .border(1.dp,Color.White.copy(alpha=.19f),RoundedCornerShape(25.dp))
        .pointerInput(Unit) {
            detectTransformGestures { _, move, magnification, _ ->
                val next=(mapZoom*magnification).coerceIn(1f,8f)
                mapZoom=next
                val maxX=size.width*next*0.65f
                val maxY=size.height*next*0.65f
                mapPan=if(next<=1.01f) Offset.Zero else Offset(
                    (mapPan.x+move.x).coerceIn(-maxX,maxX),
                    (mapPan.y+move.y).coerceIn(-maxY,maxY))
            }
        }) {
        Canvas(Modifier.matchParentSize().onSizeChanged { canvasSize=it }) {
            val viewport=view
            if(rasterReady && terrain!=null) {
                drawImage(terrain,dstSize=IntSize(size.width.toInt(),size.height.toInt()))
                with(NlscTerrain) { drawCredit() }
            } else {
                // Crisp vector fallback while the new-resolution map loads.
                with(TyphoonGeoBackdrop) { draw(viewport) }
                for(i in 1..3) {
                    val x=size.width*i/4f
                    val y=size.height*i/4f
                    drawLine(Color.White.copy(alpha=.07f),Offset(x,0f),Offset(x,size.height),1.dp.toPx())
                    drawLine(Color.White.copy(alpha=.07f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())
                }
            }
            verifiedTracks.forEach { track ->
                for(i in 0 until track.size-1) {
                    val from=track[i];val to=track[i+1]
                    val (x1,y1)=viewport.project(from.longitude,from.latitude)
                    val (x2,y2)=viewport.project(to.longitude,to.latitude)
                    drawLine(Color(0xFF66C7FF),Offset(x1,y1),Offset(x2,y2),
                        strokeWidth=3.dp.toPx())
                }
            }
            // Imported research data is NEVER represented as an official warning or CWA forecast.
            for(i in 0 until googlePath.size-1) {
                val from=googlePath[i];val to=googlePath[i+1]
                val (x1,y1)=viewport.project(from.longitude,from.latitude)
                val (x2,y2)=viewport.project(to.longitude,to.latitude)
                drawLine(Color(0xFFF6A8F3),Offset(x1,y1),Offset(x2,y2),
                    strokeWidth=2.5.dp.toPx())
            }
            TyphoonGeoBackdrop.drawLabels(this,viewport)
            displayPoints.forEach { pt ->
                val color=when(pt.kind) {
                    TyphoonFix.Kind.FORECAST->Color(0xFF66C7FF)
                    TyphoonFix.Kind.OBSERVED->Color(0xFFFFD389)
                    TyphoonFix.Kind.UNSPECIFIED->Color(0xFFDDE6EF)
                }
                val (x,y)=viewport.project(pt.longitude,pt.latitude)
                val at=Offset(x,y)
                drawCircle(color,5.dp.toPx(),at)
                drawCircle(color.copy(alpha=.33f),10.dp.toPx(),at,style=Stroke(1.dp.toPx()))
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xBC183950))
            .clickable { mapZoom=1f;mapPan=Offset.Zero }
            .padding(horizontal=14.dp,vertical=9.dp)) {
            Text("重設視角",color=Color.White,fontSize=14.sp)
        }
    }
    Column(Modifier.padding(top=9.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("● 實測位置",color=Color(0xFFFFD389),fontSize=14.sp)
            Text("● 官方預報",color=Color(0xFF66C7FF),fontSize=14.sp)
            if(googlePath.isNotEmpty()) Text("● Weather Lab（匯入）",color=Color(0xFFF6A8F3),fontSize=14.sp)
        }
        Text("雙指縮放、拖移 · 地圖將依視角重新載入清晰圖資",
            color=Color(0xFFCCE3ED),fontSize=14.sp)
    }
}
