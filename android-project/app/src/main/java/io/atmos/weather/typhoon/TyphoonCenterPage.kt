package io.atmos.weather.typhoon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.alerts.CwaWarning
import io.atmos.weather.earthquake.CwaOfficialGateway
import io.atmos.weather.maps.*
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Official tracks, probability values and regional geography are all usable inside the app. */
@Composable fun TyphoonCenterPage(warnings:List<CwaWarning>,onClose:()->Unit,
    initialBackup:List<GdacsCyclone> = emptyList(),primaryUnavailable:Boolean=false,
    currentLat:Double=Double.NaN,currentLon:Double=Double.NaN,currentName:String="目前位置") {
    val context=LocalContext.current;val palette=MaterialTheme.colorScheme;val scope=rememberCoroutineScope()
    var fixes by remember {mutableStateOf<List<TyphoonFix>>(emptyList())}
    var timelines by remember {mutableStateOf<List<StormTimeline>>(emptyList())}
    var selectedTimeline by remember {mutableStateOf<StormTimeline?>(null)}
    var selectedWarning by remember {mutableStateOf<CwaWarning?>(null)}
    var focusStorm by remember {mutableStateOf("")}
    var loading by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf(if(primaryUnavailable)"正在重新連接官方路徑…" else "正在取得官方路徑…")}
    var probabilityLoading by remember {mutableStateOf(false)}
    var officialProbability by remember {mutableStateOf<CwaWindProbability.Snapshot?>(null)}
    fun refresh() {
        if(loading||probabilityLoading)return
        loading=true
        scope.launch {
            try {
                val data=CwaOfficialGateway.read(context,"W-C0034-005")
                val parsed=withContext(Dispatchers.Default) {
                    OfficialTyphoonTrack.extract(data) to OfficialStormTimeline.parse(data)
                }
                fixes=parsed.first;timelines=parsed.second
                status=if(fixes.isEmpty())"目前沒有可顯示的官方熱帶氣旋路徑" else "中央氣象署最新預報 · 點選颱風可檢視時間軸"
            } catch(cancel:CancellationException){throw cancel}
              catch(_:Exception){status="官方路徑暫時無法更新，可重試；已載入的地圖仍可操作"}
            finally{loading=false}
        }
        scope.launch {
            probabilityLoading=true
            try {officialProbability=CwaWindProbability.read(forceRefresh=true)}
            catch(cancel:CancellationException){throw cancel}
            finally{probabilityLoading=false}
        }
    }
    LaunchedEffect(Unit){refresh()}
    BackHandler(selectedTimeline!=null||selectedWarning!=null) {
        if(selectedWarning!=null)selectedWarning=null else selectedTimeline=null
    }
    val root:@Composable ()->Unit={
        Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
            TyphoonPageHeader(loading||probabilityLoading,onClose,{refresh()})
            Column(Modifier.weight(1f).appleBouncingScroll().verticalScroll(rememberScrollState())
                .padding(horizontal=16.dp).padding(top=12.dp,bottom=128.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(status,color=palette.onSurfaceVariant,fontSize=14.sp)
                if(loading)IOSLinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                val ids=fixes.map{it.stormId}.filter{it.isNotBlank()}.distinct()
                val focused=focusStorm.takeIf{it in ids}?:ids.firstOrNull()
                if(ids.size>1) io.atmos.weather.next.NextReorderableToolDock(
                    actions=ids.map {id->io.atmos.weather.next.NextToolAction(id,timelines.firstOrNull{it.stormId==id}?.name?:id,Icons.Rounded.Cyclone)},
                    selectedId=focused,onSelected={focusStorm=it},onOrderChanged={},maxVisible=4,
                    reduceMotion=rememberAppleReduceMotion(),reduceTransparency=rememberAppleReduceTransparency(),modifier=Modifier.fillMaxWidth())
                val points=fixes.filter{focused==null||it.stormId==focused}
                OfficialTrackPlot(points)
                timelines.filter{focused==null||it.stormId==focused}.forEach {storm->
                    GlassSurface {GlassAction("${storm.name} · 預報時間軸","拖曳時間檢視中心位置、風速與暴風圈"){selectedTimeline=storm}}
                }
                GlassSurface {
                    Text("七級風暴風圈侵襲機率",color=palette.onSurface,fontSize=20.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("未來 120 小時 · 氣象署主要城市／離島參考點",color=palette.onSurfaceVariant,fontSize=14.sp)
                    val currentReference=officialProbability?.cityTable?.reference(currentLon,currentLat,currentName)
                    val risks=listOf(Triple(currentReference?.name?:currentName,currentLon,currentLat),
                        Triple("南投縣",120.69,23.91),Triple("澎湖縣",119.57,23.57),
                        Triple("金門縣",118.32,24.43),Triple("連江縣",119.95,26.16)).distinctBy{it.first}
                    risks.chunked(2).forEach {group->
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            group.forEach {(name,lon,lat)->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.surfaceVariant).padding(12.dp)) {
                                    Text(io.atmos.weather.location.LocationLabels.clean(name),color=palette.onSurfaceVariant,fontSize=14.sp,maxLines=2)
                                    Text(officialProbability?.at(lon,lat,if(lon==currentLon&&lat==currentLat)currentName else name)?:"未提供",
                                        color=palette.onSurface,fontSize=26.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                                }
                            }
                            if(group.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val probabilityMessage=when {
                        probabilityLoading->"正在取得官方機率…"
                        CwaWindProbability.fetchState==CwaWindProbability.FetchState.CACHED->"更新暫時失敗，顯示先前取得的官方資料，請核對發布時間；缺值不代表 0%。"
                        officialProbability?.cityTable!=null->"0% 是官方公布值；參考點數值不代表精確地址，也不是颱風登陸機率。"
                        officialProbability!=null->"目前顯示官方等值區間；缺值不推定為 0%。"
                        CwaWindProbability.fetchState==CwaWindProbability.FetchState.STALE->"官方檔案時間較舊，暫不當作目前機率；未提供不代表 0%。"
                        CwaWindProbability.fetchState==CwaWindProbability.FetchState.NO_USABLE_REGIONS->"官方暫無可核對的機率範圍；未提供不代表 0%。"
                        else->"目前沒有可核對的官方機率，請稍後重試；缺值不代表 0%。"
                    }
                    Text(probabilityMessage,color=palette.onSurfaceVariant,fontSize=14.sp)
                    officialProbability?.sourceUpdatedAt?.let {at->
                        Text("官方發布："+Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),color=palette.onSurfaceVariant,fontSize=14.sp)
                    }
                }
                warnings.filter{it.title.contains("颱風")||it.summary.contains("颱風警報")}.forEach {warning->
                    GlassSurface {GlassAction(warning.title,warning.summary.take(120)){selectedWarning=warning}}
                }
                Text("路徑、時間與警報以中央氣象署公告為準；地圖可離線縮放，不另開網站。",color=palette.onSurfaceVariant,fontSize=14.sp)
            }
        }
    }
    // Retain the selected timeline while the outgoing layer animates away.
    var retainedTimeline by remember{mutableStateOf<StormTimeline?>(null)}
    if(selectedTimeline!=null)retainedTimeline=selectedTimeline
    AppleParallaxStack(selectedTimeline!=null,{selectedTimeline=null},Modifier.fillMaxSize(),root) {
        retainedTimeline?.let{StormTimelineOverlay(it){selectedTimeline=null}}
    }
    var retainedWarning by remember{mutableStateOf<CwaWarning?>(null)}
    selectedWarning?.let{retainedWarning=it}
    MainDetailLayer(selectedWarning!=null,{selectedWarning=null}) {
        retainedWarning?.let{warning->io.atmos.weather.alerts.CwaAlertsPage(
            initialTitle=warning.title,initialUrl=warning.link,initialNotices=warnings,onClose={selectedWarning=null})}
    }
}

@Composable internal fun TyphoonPageHeader(loading:Boolean,onClose:()->Unit,onRefresh:()->Unit) {
    Box(Modifier.fillMaxWidth()) {
        IOSSettingsHeader("颱風動向與警報",onClose)
        GlassIconButton(onRefresh,enabled=!loading,modifier=Modifier.align(Alignment.CenterEnd).padding(end=16.dp)) {
            Icon(Icons.Rounded.Refresh,"更新官方颱風資料",tint=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun OfficialTrackPlot(points:List<TyphoonFix>) {
    val palette=MaterialTheme.colorScheme
    val dark=palette.background.red<.5f
    val regional=rememberRegionalVectorAtlas();val taiwan=rememberTaiwanVectorAtlas()
    val displayPoints=remember(points){points.filter{it.kind==TyphoonFix.Kind.OBSERVED}.takeLast(10)+points.filter{it.kind==TyphoonFix.Kind.FORECAST}.takeLast(24)}
    val tracks=remember(points){VerifiedTrackPlanner.verifiedForecastSegments(points.map{DatedTyphoonPosition(it.stormId,it.latitude,it.longitude,it.kind==TyphoonFix.Kind.FORECAST,it.timestamp)})}
    var canvasSize by remember{mutableStateOf(IntSize.Zero)}
    var fitTrack by remember(points){mutableStateOf(true)}
    var mapZoom by remember{mutableFloatStateOf(1f)};var mapPan by remember{mutableStateOf(Offset.Zero)}
    val padding=with(LocalDensity.current){22.dp.toPx()}
    val reference=displayPoints.map{it.latitude to it.longitude}+TyphoonGeoBackdrop.keyAnchors.map{it.second to it.first}
    val base=remember(reference,canvasSize,fitTrack,padding){
        if(fitTrack)TyphoonViewport.forPoints(reference,canvasSize.width.toFloat().coerceAtLeast(1f),canvasSize.height.toFloat().coerceAtLeast(1f),padding)
        else TyphoonViewport.forTaiwan(canvasSize.width.toFloat().coerceAtLeast(1f),canvasSize.height.toFloat().coerceAtLeast(1f),padding)
    }
    val view=remember(base,mapZoom,mapPan){val scale=base.pxPerKm*mapZoom
        TyphoonViewport(base.originXKm-mapPan.x/scale,base.originYKm+mapPan.y/scale,scale,base.widthPx,base.heightPx,base.cosCenter)}
    val predicted=if(dark)Color(0xFF69CFFF) else Color(0xFF0059B9)
    val observed=if(dark)Color(0xFFFFD389) else Color(0xFF8F4B00)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(25.dp))
            .background(if(dark)Color(0xFF081E31) else Color(0xFFE4F0F7)).border(1.dp,palette.outlineVariant,RoundedCornerShape(25.dp))
            .pointerInput(Unit){detectTransformGestures {centroid,move,factor,_->
                if(factor.isFinite()&&factor>0&&move.x.isFinite()&&move.y.isFinite()) {
                    val next=(mapZoom*factor).coerceIn(1f,32f);val anchor=centroid-Offset(size.width/2f,size.height/2f)
                    val pan=anchor+(mapPan-anchor)*(next/mapZoom)+move
                    mapZoom=next;mapPan=Offset(pan.x.coerceIn(-size.width*next,size.width*next),pan.y.coerceIn(-size.height*next,size.height*next))
                }
            }}) {
            Canvas(Modifier.matchParentSize().onSizeChanged{canvasSize=it}) {
                regional?.draw(this,view,dark)
                taiwan?.draw(this){lon,lat->val p=view.project(lon,lat);Offset(p.first,p.second)}
                drawRegionalPlaceLabels(view,dark)
                tracks.forEach {track->for(i in 0 until track.size-1){
                    val a=view.project(track[i].longitude,track[i].latitude);val b=view.project(track[i+1].longitude,track[i+1].latitude)
                    drawDirectedTrack(Offset(a.first,a.second),Offset(b.first,b.second),predicted)
                }}
                val labelled=mutableListOf<Offset>()
                displayPoints.forEach {point->
                    val p=view.project(point.longitude,point.latitude);val at=Offset(p.first,p.second)
                    if(at.x !in 0f..size.width||at.y !in 0f..size.height)return@forEach
                    val color=if(point.kind==TyphoonFix.Kind.OBSERVED)observed else predicted
                    drawCircle(color.copy(alpha=.28f),9.dp.toPx(),at,style=Stroke(1.dp.toPx()))
                    drawCircle(if(dark)Color(0xFF082139) else Color.White,5.5.dp.toPx(),at)
                    drawCircle(color,4.dp.toPx(),at)
                    if(point.kind==TyphoonFix.Kind.FORECAST&&labelled.none{(it-at).getDistance()<72.dp.toPx()}) {
                        VerifiedTrackPlanner.parseTime(point.timestamp)?.let {
                            drawTrackTime(it.atZone(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("dd日 HH:mm")),at);labelled+=at
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                GlassTextButton({fitTrack=!fitTrack;mapZoom=1f;mapPan=Offset.Zero}){Text(if(fitTrack)"回到臺灣" else "完整路徑")}
                GlassIconButton({mapZoom=(mapZoom*1.5f).coerceAtMost(32f)}){Icon(Icons.Rounded.Add,"放大颱風地圖")}
                GlassIconButton({mapZoom=(mapZoom/1.5f).coerceAtLeast(1f)}){Icon(Icons.Rounded.Remove,"縮小颱風地圖")}
            }
            Text("Natural Earth 10m · © 國土測繪中心",color=if(dark)Color(0xFFD9EBF5) else Color(0xFF234353),fontSize=14.sp,
                modifier=Modifier.align(Alignment.BottomStart).background(if(dark)Color(0xD9081E31) else Color(0xDEE4F0F7)).padding(7.dp))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("● 實測位置",color=observed,fontSize=14.sp);Text("┄ 官方預報 →",color=predicted,fontSize=14.sp)
        }
        Text("雙指縮放、拖移 · 32 倍原生向量縮放 · 時間為臺灣時間",color=palette.onSurfaceVariant,fontSize=14.sp)
    }
}
