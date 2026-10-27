package io.atmos.weather.radar
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.atmos.weather.ui.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable fun NativeRadarViewer(latitude:Double,longitude:Double,onClose:()->Unit) {
    val uri=LocalUriHandler.current
    val context=LocalContext.current;val density=LocalDensity.current.density
    val palette=MaterialTheme.colorScheme;val lifecycle=LocalLifecycleOwner.current
    var camera by remember(latitude,longitude){mutableStateOf(RadarCamera(latitude,longitude,6.5).normalized())}
    var size by remember{mutableStateOf(IntSize.Zero)}
    var frames by remember{mutableStateOf<List<RadarFrame>>(emptyList())}
    var index by remember{mutableIntStateOf(0)};var playing by remember{mutableStateOf(false)}
    var attempt by remember{mutableIntStateOf(0)};var status by remember{mutableStateOf("正在取得雷達時間軸…")}
    var opacity by remember{mutableFloatStateOf(.75f)}
    val reduce=rememberAppleReduceMotion()
    LaunchedEffect(attempt) {
        try {val t=RadarRepository.timeline(attempt>0);frames=t.frames;index=frames.lastIndex
            status=if(t.cached)"目前顯示快取圖層" else "歷史回波實況 · 近 2 小時"}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){status="雷達服務暫時無法連線，可保留地圖操作並重試"}
    }
    LaunchedEffect(lifecycle) {lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while(isActive){delay(300000);attempt++}
    }}
    LaunchedEffect(playing,frames,lifecycle,reduce) {
        if(playing&&!reduce&&frames.isNotEmpty())lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive){delay(1100);index=(index+1)%frames.size}
        }
    }
    val frame=frames.getOrNull(index)
    val tiles=remember(camera,size,density){RadarProjection.tiles(camera,size.width.toFloat(),size.height.toFloat(),density)}
    var base by remember{mutableStateOf<Map<RadarTile,ImageBitmap>>(emptyMap())}
    var echoes by remember{mutableStateOf<Map<RadarTile,ImageBitmap>>(emptyMap())}
    var loadedFrame by remember{mutableStateOf<String?>(null)}
    var fetching by remember{mutableStateOf(false)}
    var tileFailure by remember{mutableStateOf(false)}
    LaunchedEffect(tiles,frame?.path) {
        fetching=true;delay(120)
        supervisorScope {
            val b=async {tiles.map{tile->async {tile to RadarRepository.image(context,null,tile)?.asImageBitmap()}}.awaitAll().mapNotNull{(k,v)->v?.let{k to it}}.toMap()}
            val e=async {if(frame==null)emptyMap() else tiles.map{tile->async {tile to RadarRepository.image(context,frame,tile)?.asImageBitmap()}}.awaitAll().mapNotNull{(k,v)->v?.let{k to it}}.toMap()}
            base=b.await();echoes=e.await();loadedFrame=frame?.path;tileFailure=base.size<tiles.size||(frame!=null&&echoes.size<tiles.size);fetching=false
        }
    }
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
        IOSSettingsHeader("降雨雷達",onClose)
        Text(status,color=palette.onSurfaceVariant,modifier=Modifier.padding(horizontal=18.dp),fontSize=14.sp)
        Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(Color(0xFF17334B)).onSizeChanged{size=it}) {
            Canvas(Modifier.fillMaxSize().pointerInput(density) {
                detectTransformGestures {centroid,pan,zoom,_->camera=camera.zoomAt(zoom,centroid.x,centroid.y,size.width.toFloat(),size.height.toFloat(),density).pan(pan.x,pan.y,density)}
            }) {
                fun paint(images:Map<RadarTile,ImageBitmap>,alpha:Float) {
                    images.forEach {(tile,img)->
                        val tileSize=(256*density*2.0.pow(camera.zoom-tile.z)).toFloat()
                        val world=camera.world(density)
                        val x=(tile.column*tileSize-RadarProjection.x(camera.longitude)*world+size.width/2).toFloat()
                        val y=(tile.y*tileSize-RadarProjection.y(camera.latitude)*world+size.height/2).toFloat()
                        drawImage(img,dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),dstSize=IntSize(ceil(tileSize).toInt(),ceil(tileSize).toInt()),alpha=alpha,filterQuality=FilterQuality.Low)
                    }
                }
                paint(base,1f);if(loadedFrame==frame?.path)paint(echoes,opacity)
                val w=camera.world(density)
                val dx=((RadarProjection.x(longitude)-RadarProjection.x(camera.longitude)+1.5)%1-.5)*w
                val point=Offset((size.width/2+dx).toFloat(),(size.height/2+(RadarProjection.y(latitude)-RadarProjection.y(camera.latitude))*w).toFloat())
                drawCircle(Color(0x552788FF),13.dp.toPx(),point);drawCircle(Color.White,7.dp.toPx(),point)
                drawCircle(Color(0xFF147BE2),5.dp.toPx(),point)
            }
            Column(Modifier.align(Alignment.TopEnd).padding(9.dp)) {
                GlassIconButton(onClick={camera=camera.copy(zoom=(camera.zoom+.5).coerceAtMost(7.0))}){androidx.compose.material3.Icon(Icons.Rounded.Add,"放大")}
                Spacer(Modifier.height(7.dp))
                GlassIconButton(onClick={camera=camera.copy(zoom=(camera.zoom-.5).coerceAtLeast(2.0))}){androidx.compose.material3.Icon(Icons.Rounded.Remove,"縮小")}
                Spacer(Modifier.height(7.dp))
                GlassIconButton(onClick={camera=RadarCamera(latitude,longitude,6.5).normalized()}){androidx.compose.material3.Icon(Icons.Rounded.MyLocation,"回到選擇地點")}
            }
            if(fetching)Text("載入圖資…",color=Color.White,modifier=Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xC017334B)),fontSize=14.sp)
            if(tileFailure&&!fetching)Text("部分圖資尚未載入，請重試或移動地圖",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xD017334B)))
            Text("底圖 © OpenStreetMap contributors",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.BottomStart).background(Color(0xD017334B)).clickable{uri.openUri("https://www.openstreetmap.org/copyright")}.padding(7.dp))
        }
        Column(Modifier.padding(horizontal=16.dp)) {
            val time=frame?.let{Instant.ofEpochSecond(it.time).atZone(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))}?:"尚無時間資料"
            Text("圖層時間 $time（台灣時間）",color=palette.onSurface,fontSize=15.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                GlassIconButton({playing=false;index=(index-1).coerceAtLeast(0)},enabled=frames.isNotEmpty()){androidx.compose.material3.Icon(Icons.Rounded.SkipPrevious,"前一張")}
                GlassIconButton({playing=!playing},enabled=frames.isNotEmpty()&&!reduce){androidx.compose.material3.Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(playing)"暫停回波動畫" else "播放回波動畫")}
                GlassSlider(index.toFloat(),{playing=false;index=it.roundToInt().coerceIn(0,frames.lastIndex.coerceAtLeast(0))},Modifier.weight(1f),valueRange=0f..frames.lastIndex.coerceAtLeast(1).toFloat(),steps=(frames.size-2).coerceAtLeast(0),enabled=frames.isNotEmpty())
                GlassIconButton({playing=false;index=(index+1).coerceAtMost(frames.lastIndex.coerceAtLeast(0))},enabled=frames.isNotEmpty()){androidx.compose.material3.Icon(Icons.Rounded.SkipNext,"後一張")}
                GlassIconButton({attempt++}){androidx.compose.material3.Icon(Icons.Rounded.Refresh,"重新讀取雷達")}
            }
            Row(verticalAlignment=Alignment.CenterVertically){Text("回波透明度",color=palette.onSurfaceVariant,fontSize=14.sp);GlassSlider(opacity,{opacity=it},Modifier.weight(1f),valueRange=.2f..1f)}
            Text("Weather data by RainViewer ↗",color=palette.primary,fontSize=14.sp,modifier=Modifier.clickable{uri.openUri("https://www.rainviewer.com")}.padding(vertical=4.dp))
            Text("圖層時間為回波合成時間；歷史觀測不是未來預測。空白亦可能是圖資不足。",color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(bottom=7.dp))
        }
    }
}
