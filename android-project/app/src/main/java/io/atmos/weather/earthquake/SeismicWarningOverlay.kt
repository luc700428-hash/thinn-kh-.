package io.atmos.weather.earthquake

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import io.atmos.weather.ui.GlassIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Native warning sheet. County shading stays source-only; local model values are labeled.
 * Animated P/S rings remain illustrative and do not drive the independent S-arrival table.
 */
@Composable
fun SeismicWarningOverlay(incident:EewIncident,replay:Boolean,userCounty:String?,onClose:()->Unit,
    simulation:Boolean=false) {
    val context=LocalContext.current
    val palette=MaterialTheme.colorScheme
    val historical=simulation && ChiChi921.isScenario(incident)
    // A location that arrives after the first report must update this same warning.
    val place by produceState(SeismicUserLocation.current(context),context,incident.id) {
        while(true){value=SeismicUserLocation.current(context);delay(1000)}
    }
    val estimate=remember(historical,place){if(historical)ChiChi921.estimate(place) else null}
    val reduce=context.getSharedPreferences("thinn_settings",0).getBoolean("reduce_motion",false)
    val accent=if(replay || simulation)palette.tertiary else palette.error
    var entered by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {entered=true}
    val reveal by androidx.compose.animation.core.animateFloatAsState(if(entered)1f else .96f,
        androidx.compose.animation.core.tween(if(reduce)0 else 180,easing=io.atmos.weather.ui.AppleMotion.snap),label="速報光學入場")
    val county=(place?.name?:userCounty).orEmpty().replace('台','臺')
    val allCounties=remember(incident) { SeismicWarningCounties.allFromProvider(incident.localIntensityByCounty) }
    val eventKey="${incident.id}:${incident.serial}:$replay:$simulation"
    val frameNow=io.atmos.weather.ui.rememberDisplayWallClock(!reduce)
    // A true incident plays a bounded looping alert tone and one descriptive spoken warning.
    // Historical replay never uses the live alarm tone.
    DisposableEffect(context,eventKey) {
        if(!replay && !simulation) SeismicContinuousWarning.start(context,incident)
        onDispose {
            // Keep the bounded tone across a handoff from MainActivity to the lock-screen page.
            // Explicit dismissal/cancellation stops it in SeismicAlertDelivery.
            if(!replay && !simulation && SeismicAlertDelivery.active.value==null) SeismicContinuousWarning.stop()
        }
    }
    DisposableEffect(context,eventKey,simulation) {
        if(simulation) SeismicSound.practiceLoop(context)
        onDispose { if(simulation) SeismicSound.stop() }
    }
    DisposableEffect(context,eventKey,simulation) {
        val trainingVoice=if(simulation) SeismicTrainingVoice(context,incident,SeismicUserLocation.current(context)) else null
        onDispose { trainingVoice?.stop() }
    }
    LaunchedEffect(eventKey) {
        if(!replay && !simulation && SeismicSpeech.enabled(context,"eew_voice_enabled") && SeismicAlertNotifier.mayAnnounce(context,incident)) {
            io.atmos.weather.voice.NaturalVoiceHub.interruptForEarthquake()
            SeismicSpeech.speakWarning(context,incident,SeismicUserLocation.current(context))
        }
    }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val close by rememberUpdatedState(onClose)
    val incoming by SeismicAlertDelivery.active.collectAsState()
    LaunchedEffect(incoming?.key,incoming?.incident?.serial,replay,simulation){
        // A real accepted incident takes precedence over a private drill/replay voice and tone.
        if((replay||simulation)&&incoming!=null)close()
    }
    DisposableEffect(lifecycle,simulation) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(simulation && event==androidx.lifecycle.Lifecycle.Event.ON_STOP)close()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(eventKey) {if(simulation){delay(120_000);close()}}
    var now by remember(eventKey){mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(eventKey){while(true){delay(1000);now=System.currentTimeMillis()}}
    val prediction=remember(incident,place,now/1000){SeismicLocalPrediction.resolve(incident,place,now)}
    val local=if(historical)estimate?.intensity else prediction.intensity
    val started=remember(incident.id){System.currentTimeMillis()}
    // Explicit arrivalAtMsByCounty takes precedence inside the resolver. New reports do
    // not reset a locally calculated arrival: it is anchored to the earthquake origin.
    val arrival=if(historical)estimate?.let{started+(it.arrivalSeconds*1000).toLong()} else prediction.arrivalAtMs
    val arrivalText=if(historical)arrival?.let{if(it<=now)"已抵達" else "${kotlin.math.ceil((it-now)/1000.0).toInt()} s"}?:"—" else prediction.arrivalText(now)
    val intensity=local?.let{if(it.contains("以上"))it.replace("級"," 級") else "${SeismicDisplayFormat.symbol(it)} 級"}?:"—"
    val intensityReason=if(historical)if(estimate==null)"請選擇所在地" else "本機模型估算 · 僅供演練" else prediction.intensityReason
    val arrivalReason=if(historical)"模型示意 · 非官方預測" else prediction.arrivalReason
    val title=if(historical)"臺灣 921 大地震演練" else if(simulation)"地震演練" else if(replay)"歷史地震演練" else "緊急地震速報"
    val notice=if(historical)"1999/09/21 歷史情境 · 非即時警報" else if(simulation)"虛構情境 · 並非真實速報" else if(replay)"過去事件回放 · 非即時警報" else "${incident.source} · 第 ${incident.serial} 報"
    val intensityLabel=when{
        historical->"本機震度估算"
        prediction.intensityOrigin==PredictionOrigin.LOCAL_MODEL->"本機預估震度"
        else->"預估震度"
    }
    val arrivalLabel=when{
        historical->"演練示意倒數"
        prediction.arrivalOrigin==PredictionOrigin.LOCAL_MODEL->"本機估算抵達"
        else->"預估抵達"
    }
    val maximum=if(historical)"7 級" else incident.maximumIntensity?.let{"${SeismicDisplayFormat.symbol(it)} 級"}?:"—"
    val issued=if(historical)"1999/09/21 01:47:15.9" else java.time.Instant.ofEpochMilli(incident.issuedAtMs)
        .atZone(java.time.ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss"))
    Dialog(onDismissRequest=onClose,properties=DialogProperties(dismissOnClickOutside=false,usePlatformDefaultWidth=false,decorFitsSystemWindows=false),adaptiveContentWidth=null){
        io.atmos.weather.ui.AdaptiveWindowProvider(Modifier.background(palette.background)){
        io.atmos.weather.ui.AdaptivePageFrame(maxContentWidth=1280.dp){
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()){
            val sideBySide=maxWidth>maxHeight*1.15f
            val shortLandscape=sideBySide&&maxHeight<520.dp
            val tight=maxHeight<760.dp||LocalDensity.current.fontScale>1.3f
            val gap=if(tight)4.dp else 8.dp
            val facts:@Composable (Modifier)->Unit={modifier->
                Row(modifier.testTag("seismic-warning-facts").clip(RoundedCornerShape(20.dp)).background(palette.surface)
                    .padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    WarningStatistic("規模",SeismicDisplayFormat.magnitude(incident.magnitude),Modifier.weight(1f))
                    WarningStatistic("深度",SeismicDisplayFormat.depth(incident.depthKm),Modifier.weight(1f))
                    WarningStatistic(if(historical)"歷史最大" else "來源最大",maximum,Modifier.weight(1f))
                }
            }
            Column(Modifier.fillMaxSize().graphicsLayer{alpha=reveal;scaleX=reveal;scaleY=reveal}.testTag("seismic-warning-summary")){
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Column(Modifier.weight(1f)){
                        Text(title,fontSize=18.sp,lineHeight=23.sp,fontWeight=FontWeight.Bold,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                        Text(notice,fontSize=14.sp,lineHeight=18.sp,color=if(simulation||replay)palette.tertiary else palette.onSurfaceVariant,modifier=Modifier.fillMaxWidth())
                    }
                    if(shortLandscape)facts(Modifier.weight(1f))
                    GlassIconButton(onClick=onClose){Icon(Icons.Rounded.Close,"關閉速報",tint=palette.onSurface)}
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=14.dp)){
                    val summary:@Composable (Modifier)->Unit={modifier->
                        Column(modifier.testTag("seismic-leading-pane"),verticalArrangement=Arrangement.spacedBy(gap)){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Text(county.ifBlank{"請選擇所在地"},color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp,modifier=Modifier.weight(1f))
                                if(place==null)WarningLocationChoice{SeismicUserLocation.choose(context,it)}
                            }
                            BoxWithConstraints(Modifier.fillMaxWidth()){
                                val cellWidth=(maxWidth.value-8f)/2f
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    WarningHero(intensityLabel,intensity,"",accent,Modifier.weight(1f).fillMaxHeight().semantics{contentDescription="$intensityLabel $intensity；$intensityReason"},tight,cellWidth)
                                    WarningHero(arrivalLabel,arrivalText,"",palette.primary,Modifier.weight(1f).fillMaxHeight().semantics{contentDescription="$arrivalLabel $arrivalText；$arrivalReason"},tight,cellWidth)
                                }
                            }
                            if(!shortLandscape)facts(Modifier.fillMaxWidth())
                            Text(SeismicDisplayFormat.location(incident.epicenter),fontSize=14.sp,lineHeight=19.sp,fontWeight=FontWeight.Medium,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                            if(!shortLandscape)Text(issued+if(historical)" · 舊制" else " · 第 ${incident.serial} 報",fontSize=14.sp,lineHeight=18.sp,color=palette.onSurfaceVariant,modifier=Modifier.fillMaxWidth())
                        }
                    }
                    val map:@Composable (Modifier)->Unit={modifier->
                        SeismicWarningMap(incident,if(historical)ChiChi921.historicalCountyMaxima else incident.localIntensityByCounty,
                            place,historical,frameNow,modifier.testTag("seismic-inspector-pane"),compact=true,simulation=simulation,
                            subtitle=if(shortLandscape)issued else null)
                    }
                    if(sideBySide){
                        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            summary(Modifier.weight(1f));map(Modifier.weight(1.2f).fillMaxHeight())
                        }
                    }else{
                        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(gap)){
                            summary(Modifier.fillMaxWidth());map(Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal=14.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("趴下 · 掩護 · 穩住",fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold,color=palette.onSurface,modifier=Modifier.weight(1f))
                    Button(onClick=onClose,modifier=Modifier.width(((if(simulation||replay)4 else 3)*14f*LocalDensity.current.fontScale+44f).dp).heightIn(min=48.dp).testTag("seismic-warning-dismiss")
                        .semantics{contentDescription=if(simulation)"結束模擬演練" else if(replay)"結束歷史回放" else "我已採取避難措施"},
                        colors=ButtonDefaults.buttonColors(containerColor=if(simulation||replay)palette.primary else palette.error,contentColor=if(simulation||replay)palette.onPrimary else palette.onError)){
                        Text(if(simulation||replay)"結束演練" else "已避難",fontSize=14.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold,
                            textAlign=TextAlign.Center,maxLines=1,softWrap=false,modifier=Modifier.fillMaxWidth())
                    }
                }
            }
        }
        }
        }
    }
}

@Composable private fun WarningLocationChoice(onChoose:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box{
        TextButton(onClick={expanded=true}){Text("選擇所在地")}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=300.dp)){
            SeismicUserLocation.options().drop(1).forEach{county->
                DropdownMenuItem(text={Text(county,color=MaterialTheme.colorScheme.onSurface)},
                    onClick={onChoose(county);expanded=false})
            }
        }
    }
}
@Composable private fun WarningPlate(compact:Boolean=false,content:@Composable ColumnScope.()->Unit){
    val p=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface).border(1.dp,p.outlineVariant.copy(alpha=.5f),RoundedCornerShape(24.dp)).padding(if(compact)12.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
@Composable private fun WarningHero(label:String,value:String,caption:String,accent:Color,modifier:Modifier,tight:Boolean,width:Float){
    val p=MaterialTheme.colorScheme
    val fontScale=LocalDensity.current.fontScale
    val characters=value.sumOf{if(it.code<128).58 else 1.0}.toFloat().coerceAtLeast(1f)
    val size=minOf(if(tight)34f else 48f,(width-(if(tight)20f else 28f))/(characters*fontScale)).coerceAtLeast(14f)
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(p.surface).border(1.dp,p.outlineVariant.copy(alpha=.5f),RoundedCornerShape(24.dp)).padding(if(tight)10.dp else 14.dp)){
        Text(label,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.fillMaxWidth())
        Text(value,color=accent,fontSize=size.sp,lineHeight=(size*1.24f).sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"))
        if(caption.isNotEmpty())Text(caption,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.fillMaxWidth())
    }
}
@Composable private fun WarningStatistic(label:String,value:String,modifier:Modifier){
    val p=MaterialTheme.colorScheme
    Column(modifier,verticalArrangement=Arrangement.spacedBy(1.dp)){
        Text(label,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=18.sp,modifier=Modifier.fillMaxWidth())
        Text(value,color=p.onSurface,fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth())
    }
}
