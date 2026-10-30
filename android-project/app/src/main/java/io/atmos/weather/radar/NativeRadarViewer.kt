package io.atmos.weather.radar

import android.app.ActivityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

private class RadarSceneMemory(var scene:RadarScene?=null)

@Composable fun NativeRadarViewer(latitude:Double,longitude:Double,onClose:()->Unit) {
    val uri=LocalUriHandler.current
    val context=LocalContext.current.applicationContext;val density=LocalDensity.current.density
    val palette=MaterialTheme.colorScheme
    val dark=palette.background.luminance()<.4f
    AppSystemBars(dark)
    val lifecycle=LocalLifecycleOwner.current
    val cameraState=remember(latitude,longitude){mutableStateOf(RadarCamera(latitude,longitude,6.5).normalized())}
    var camera by cameraState
    var size by remember{mutableStateOf(IntSize.Zero)}
    var cwa by remember(latitude,longitude){mutableStateOf(CwaHighDefinitionRadar.covers(latitude,longitude))}
    val radarMaxZoom=if(cwa)CwaHighDefinitionRadar.MAX_ZOOM else RadarTilePolicy.RADAR_MAX_ZOOM
    var frames by remember{mutableStateOf<List<RadarFrame>>(emptyList())}
    var position by remember{mutableFloatStateOf(0f)}
    val currentPosition by rememberUpdatedState(position)
    var playing by remember{mutableStateOf(false)}
    var attempt by remember{mutableIntStateOf(0)};var status by remember{mutableStateOf("正在取得雷達時間軸…")}
    var opacity by remember(cwa){mutableFloatStateOf(if(cwa)1f else .75f)}
    val reduce=rememberAppleReduceMotion()
    LaunchedEffect(reduce){if(reduce)playing=false}
    val maxSources=remember(context){if(context.getSystemService(ActivityManager::class.java)?.isLowRamDevice==true)2 else RadarPlayback.MAX_SOURCE_TILES}
    LaunchedEffect(attempt,cwa) {
        try {
            val old=frames.getOrNull(RadarPlayback.sample(position,frames.size).lower)
            val wasLatest=frames.isEmpty()||position>=frames.lastIndex-.01f
            val timeline=if(cwa)CwaHighDefinitionRadar.timeline(attempt>0) else RadarRepository.timeline(attempt>0)
            frames=timeline.frames
            position=if(wasLatest)frames.lastIndex.toFloat() else frames.indices.minByOrNull{abs(frames[it].time-(old?.time?:frames.last().time))}?.toFloat()?:0f
            status=if(timeline.cached)"目前顯示快取圖層" else if(cwa)"中央氣象署 · 臺灣高解析回波 · 近 2 小時" else "歷史回波實況 · 近 2 小時"
        } catch(cancel:CancellationException){throw cancel}
          catch(_:Exception){status="雷達服務暫時無法連線，可保留地圖操作並重試"}
    }
    LaunchedEffect(lifecycle) {lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while(isActive){delay(300000);attempt++}
    }}
    val baseTiles by remember(cameraState,size,density){derivedStateOf{RadarProjection.tiles(camera,size.width.toFloat(),size.height.toFloat(),density,RadarTilePolicy.BASE_MAX_ZOOM)}}
    val radarTiles by remember(cameraState,size,density,maxSources,radarMaxZoom){derivedStateOf{RadarPlayback.tiles(camera,size.width.toFloat(),size.height.toFloat(),density,maxSources,radarMaxZoom)}}
    val pastNativeLimit by remember(cameraState,radarMaxZoom){derivedStateOf{camera.zoom>radarMaxZoom}}
    var base by remember{mutableStateOf<Map<RadarTile,ImageBitmap>>(emptyMap())}
    val buffered=remember(cwa){mutableStateMapOf<String,Map<RadarTile,ImageBitmap>>()}
    val motions=remember(cwa){mutableStateMapOf<Pair<String,String>,RadarFlowPair>()}
    var bufferedViewport by remember{mutableStateOf<List<RadarTile>>(emptyList())}
    var baseFetching by remember{mutableStateOf(false)};var radarFetching by remember{mutableStateOf(false)}
    var baseFailure by remember{mutableStateOf(false)};var radarFailure by remember{mutableStateOf(false)}
    var motionPending by remember{mutableIntStateOf(0)}
    var tileRetry by remember{mutableIntStateOf(0)}
    LaunchedEffect(baseTiles,tileRetry) {
        baseFetching=true
        try {
            delay(40)
            supervisorScope {baseTiles.map {tile->launch {
                RadarRepository.image(context,null,tile)?.asImageBitmap()?.let {image->base=RadarTileWindow.merge(base,tile,image)}
            }}.joinAll()}
            baseFailure=baseTiles.any{it !in base}
            if(!baseFailure)base=base.filterKeys{it in baseTiles}
        } finally{baseFetching=false}
    }
    // Cursor changes NEVER launch tile requests. Buffer every observation for the viewport once.
    LaunchedEffect(radarTiles,frames,tileRetry,lifecycle,reduce) {
        if(radarTiles.isEmpty()||frames.isEmpty())return@LaunchedEffect
        if(bufferedViewport!=radarTiles){buffered.clear();motions.clear();bufferedViewport=radarTiles}
        val paths=frames.map{it.path}.toSet()
        buffered.keys.toList().filter{it !in paths}.forEach{buffered.remove(it)}
        motions.keys.toList().filter{it.first !in paths||it.second !in paths}.forEach{motions.remove(it)}
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            radarFetching=true;radarFailure=false
            val attempted=mutableSetOf<String>();val flowAttempted=motions.keys.toMutableSet()
            val flowGate=Semaphore(1)
            try {supervisorScope {
                fun prepareMotion() {
                    if(reduce)return
                    frames.zipWithNext().forEach{(a,b)->
                        val key=a.path to b.path
                        val first=buffered[a.path];val second=buffered[b.path]
                        if(first!=null&&second!=null&&RadarPlayback.canInterpolate(a,b)&&flowAttempted.add(key)) {
                            motionPending++
                            launch {
                                try {
                                    val motion=flowGate.withPermit {withContext(Dispatchers.Default) {
                                        val job=currentCoroutineContext()[Job]
                                        RadarMotionField.estimate(first,second){job?.ensureActive()}
                                    }}
                                    if(motion!=null)motions[key]=motion
                                } finally{motionPending--}
                            }
                        }
                    }
                }
                prepareMotion()
                while(isActive) {
                    val next=RadarPlayback.loadOrder(frames.size,currentPosition).map{frames[it]}
                        .firstOrNull{it.path !in buffered && attempted.add(it.path)}?:break
                    val images=if(next.path.startsWith("cwa:")) {
                        try{CwaHighDefinitionRadar.tiles(context,next,radarTiles)}
                        catch(cancel:CancellationException){throw cancel}
                        catch(_:Exception){emptyMap()}
                    } else radarTiles.map{tile->async {tile to RadarRepository.image(context,next,tile)?.asImageBitmap()}}
                        .awaitAll().mapNotNull{(tile,image)->image?.let{tile to it}}.toMap()
                    if(images.size==radarTiles.size)buffered[next.path]=images else radarFailure=true
                    prepareMotion()
                }
            }}finally{radarFetching=false}
        }
    }
    val readyCount=frames.count{it.path in buffered}
    val animationReady=frames.isNotEmpty()&&readyCount==frames.size&&motionPending==0&&bufferedViewport==radarTiles
    val canScrub=animationReady||(!radarFetching&&readyCount>0&&bufferedViewport==radarTiles)
    fun selectLoaded(target:Float):Float=if(animationReady)target.coerceIn(0f,frames.lastIndex.coerceAtLeast(0).toFloat())
        else frames.indices.filter{frames[it].path in buffered}.minByOrNull{abs(it-target)}?.toFloat()?:position
    val ready by rememberUpdatedState(animationReady)
    LaunchedEffect(playing,frames,lifecycle,reduce) {
        if(playing&&!reduce&&frames.size>1)lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous=0L;var endHold=0L
            while(isActive)withFrameNanos {now->
                val elapsed=if(previous==0L)0 else (now-previous).coerceIn(0,100_000_000L);previous=now
                if(ready) {
                    if(position>=frames.lastIndex) {
                        endHold+=elapsed
                        if(endHold>=700_000_000L){position=0f;endHold=0}
                    } else {position=RadarPlayback.advance(position,elapsed,frames.size);endHold=0}
                }
            }
        }
    }
    val sample=RadarPlayback.sample(if(reduce)position.roundToInt().toFloat() else position,frames.size)
    val first=frames.getOrNull(sample.lower);val second=frames.getOrNull(sample.upper)
    val lower=first?.let{buffered[it.path]};val upper=second?.let{buffered[it.path]}
    val prepared=if(first!=null&&second!=null&&lower!=null&&upper!=null)
        RadarScene(first,second,sample.fraction,lower,upper,motions[first.path to second.path]) else null
    val memory=remember(cwa){RadarSceneMemory()}
    val scene=prepared?:memory.scene
    SideEffect {if(prepared!=null)memory.scene=prepared}
    val painter=remember{RadarTemporalPainter()}
    val fetching=baseFetching||radarFetching
    val tileFailure=baseFailure||radarFailure
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader("降雨雷達",onClose)
        io.atmos.weather.ui.AdaptiveMapInspector(Modifier.weight(1f),map={mapModifier->
        Box(mapModifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(if(dark)RadarMapStyle.darkBackground else RadarMapStyle.lightBackground).onSizeChanged{size=it}) {
            Canvas(Modifier.fillMaxSize().pointerInput(density,cwa) {
                detectTransformGestures {centroid,pan,zoom,_->
                    val bounded=if(cwa)minOf(zoom,2.0.pow(9.0-camera.zoom).toFloat()) else zoom
                    camera=camera.zoomAt(bounded,centroid.x,centroid.y,size.width.toFloat(),size.height.toFloat(),density).pan(pan.x,pan.y,density)
                }
            }) {
                drawRadarLayers(camera,density,base,emptyMap(),opacity,dark)
                scene?.let{painter.draw(this,camera,density,it,opacity)}
                val world=camera.world(density)
                val dx=((RadarProjection.x(longitude)-RadarProjection.x(camera.longitude)+1.5)%1-.5)*world
                val point=Offset((size.width/2+dx).toFloat(),(size.height/2+(RadarProjection.y(latitude)-RadarProjection.y(camera.latitude))*world).toFloat())
                drawCircle(Color(0x552788FF),13.dp.toPx(),point);drawCircle(Color.White,7.dp.toPx(),point)
                drawCircle(Color(0xFF147BE2),5.dp.toPx(),point)
            }
            Column(Modifier.align(Alignment.TopEnd).padding(9.dp)) {
                GlassIconButton(onClick={camera=camera.copy(zoom=(camera.zoom+.5).coerceAtMost(if(cwa)9.0 else RadarTilePolicy.CAMERA_MAX_ZOOM))}){androidx.compose.material3.Icon(Icons.Rounded.Add,"放大")}
                Spacer(Modifier.height(7.dp))
                GlassIconButton(onClick={camera=camera.copy(zoom=(camera.zoom-.5).coerceAtLeast(2.0))}){androidx.compose.material3.Icon(Icons.Rounded.Remove,"縮小")}
                Spacer(Modifier.height(7.dp))
                GlassIconButton(onClick={camera=if(cwa&&!CwaHighDefinitionRadar.covers(latitude,longitude))RadarCamera() else RadarCamera(latitude,longitude,6.5).normalized()}){androidx.compose.material3.Icon(Icons.Rounded.MyLocation,"回到選擇地點")}
            }
            if(!animationReady&&radarFetching)Text(if(motionPending>0&&readyCount==frames.size)"準備連續動畫…" else "預載回放 $readyCount/${frames.size}",color=Color.White,modifier=Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xD017334B)),fontSize=14.sp)
            else if(baseFetching)Text("載入底圖…",color=Color.White,modifier=Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xC017334B)),fontSize=14.sp)
            if(cwa&&!CwaHighDefinitionRadar.covers(camera.latitude,camera.longitude))Text("超出臺灣高解析圖資範圍，可切換全球雷達",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=44.dp).background(Color(0xE017334B)).padding(8.dp))
            if(tileFailure&&!fetching)Text("部分圖資未完整，可查看已載入時段或重新讀取",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xE017334B)))
            Text("底圖 © OpenStreetMap contributors",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.BottomStart).background(Color(0xD017334B)).clickable{uri.openUri("https://www.openstreetmap.org/copyright")}.padding(7.dp))
        }
        },controls={
        GlassChoiceGroup(listOf("臺灣高解析","全球雷達"),if(cwa)0 else 1,{index->
            val next=index==0
            if(next!=cwa) {
                playing=false;frames=emptyList();position=0f;cwa=next;status="正在取得雷達時間軸…"
                if(next && !CwaHighDefinitionRadar.covers(camera.latitude,camera.longitude))camera=RadarCamera()
                else if(next)camera=camera.copy(zoom=camera.zoom.coerceAtMost(9.0))
            }
        },modifier=Modifier.padding(horizontal=16.dp,vertical=5.dp))
        Text(status,color=palette.onSurfaceVariant,modifier=Modifier.padding(horizontal=18.dp),fontSize=14.sp)
        Column(Modifier.padding(horizontal=4.dp)) {
            val timeFormat=remember{DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Taipei"))}
            val displayTime=scene?.let{it.first.time+((it.second.time-it.first.time)*it.fraction).roundToLong()}
            val time=displayTime?.let{timeFormat.format(Instant.ofEpochSecond(it))}?:"尚無時間資料"
            if(pastNativeLimit)Text("底圖持續放大；回波細節已達資料來源上限",color=palette.onSurfaceVariant,fontSize=14.sp)
            Text("回放時間 $time（台灣時間）",color=palette.onSurface,fontSize=15.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                GlassIconButton({playing=false;position=RadarPlayback.step(position,-1,frames.indices.filter{frames[it].path in buffered})},enabled=canScrub){androidx.compose.material3.Icon(Icons.Rounded.SkipPrevious,"前一張")}
                GlassIconButton({if(!playing&&position>=frames.lastIndex)position=0f;playing=!playing},enabled=(animationReady||playing)&&frames.size>1&&!reduce){androidx.compose.material3.Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(playing)"暫停回波動畫" else "播放回波動畫")}
                GlassSlider(position,{playing=false;position=selectLoaded(if(reduce)it.roundToInt().toFloat() else it)},Modifier.weight(1f),valueRange=0f..frames.lastIndex.coerceAtLeast(1).toFloat(),steps=0,enabled=canScrub&&frames.size>1)
                GlassIconButton({playing=false;position=RadarPlayback.step(position,1,frames.indices.filter{frames[it].path in buffered})},enabled=canScrub){androidx.compose.material3.Icon(Icons.Rounded.SkipNext,"後一張")}
                GlassIconButton({attempt++;tileRetry++}){androidx.compose.material3.Icon(Icons.Rounded.Refresh,"重新讀取雷達")}
            }
            Row(verticalAlignment=Alignment.CenterVertically){Text("回波透明度",color=palette.onSurfaceVariant,fontSize=14.sp);GlassSlider(opacity,{opacity=it},Modifier.weight(1f),valueRange=.2f..1f)}
            if(cwa)Text("回波：中央氣象署 · 原始 3600 × 3600 圖資",color=palette.primary,fontSize=14.sp,modifier=Modifier.padding(vertical=4.dp))
            else Text("Weather data by RainViewer ↗",color=palette.primary,fontSize=14.sp,modifier=Modifier.clickable{uri.openUri("https://www.rainviewer.com")}.padding(vertical=4.dp))
            Text("原始圖層時間為回波合成時間；中間僅為動畫過渡，非新增觀測或未來預測。空白亦可能是圖資不足。",color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(bottom=7.dp))
        }
        })
    }
}
