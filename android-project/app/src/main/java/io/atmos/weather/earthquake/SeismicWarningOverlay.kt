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
    var showDetails by remember(eventKey){mutableStateOf(false)}
    val intensity=local?.let{if(it.contains("以上"))it.replace("級"," 級") else "${SeismicDisplayFormat.symbol(it)} 級"}?:"—"
    val intensityReason=if(historical)if(estimate==null)"請選擇所在地" else "本機模型估算 · 僅供演練" else prediction.intensityReason
    val arrivalReason=if(historical)"模型示意 · 非官方預測" else prediction.arrivalReason
    val title=if(historical)"臺灣 921 大地震演練" else if(simulation)"地震演練" else if(replay)"歷史地震演練" else "緊急地震速報"
    val notice=if(historical)"1999/09/21 歷史情境 · 非即時警報" else if(simulation)"虛構情境 · 並非真實速報" else if(replay)"過去事件回放 · 非即時警報" else "${incident.source} · 第 ${incident.serial} 報"
    val intensityCaption=when {
        historical->if(estimate==null)"請選擇所在地" else "本機演練估算"
        prediction.intensityOrigin==PredictionOrigin.SOURCE->"來源預估"
        prediction.intensityOrigin==PredictionOrigin.LOCAL_MODEL->"本機初估"
        place==null->"請選擇所在地"
        else->"待來源提供"
    }
    val arrivalCaption=when {
        historical->"演練示意"
        prediction.arrivalOrigin==PredictionOrigin.SOURCE->"來源預估"
        prediction.arrivalOrigin==PredictionOrigin.LOCAL_MODEL->"本機走時估算"
        place==null->"請選擇所在地"
        else->"待來源提供"
    }
    val scale=LocalDensity.current.fontScale
    Dialog(onDismissRequest=onClose,properties=DialogProperties(dismissOnClickOutside=false,usePlatformDefaultWidth=false,decorFitsSystemWindows=false),adaptiveContentWidth=null){
        io.atmos.weather.ui.AdaptiveWindowProvider(Modifier.background(palette.background)) {
        io.atmos.weather.ui.AdaptivePageFrame(maxContentWidth=1280.dp) {
        BoxWithConstraints(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
            val landscape=maxWidth>maxHeight*1.15f
            val tight=maxHeight.value<720f||scale>1.3f
            val gap=if(tight)6.dp else 12.dp
            Column(Modifier.fillMaxSize().graphicsLayer{alpha=reveal;scaleX=reveal;scaleY=reveal}.testTag("seismic-warning-summary")){
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(title,fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                        Text(notice,fontSize=14.sp,lineHeight=19.sp,color=if(simulation||replay)palette.tertiary else palette.onSurfaceVariant,modifier=Modifier.fillMaxWidth())
                    }
                    GlassIconButton(onClick=onClose){Icon(Icons.Rounded.Close,"關閉速報",tint=palette.onSurface)}
                }
                // Critical values are bounded by the viewport. Only the optional inspector scrolls.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=18.dp)) {
                    val paneWidth=maxWidth;val paneHeight=maxHeight
                    val wide=maxWidth.value>=980f*scale.coerceIn(1f,1.2f)&&maxHeight>=420.dp
                    val summary:@Composable (Modifier)->Unit={modifier->
                        Column(modifier.testTag("seismic-leading-pane"),verticalArrangement=Arrangement.spacedBy(gap)){
                            if(!landscape&&!tight)Text("收到地震速報 · 慎防強烈搖晃",fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Text(county.ifBlank{"未設定所在地"},color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp,modifier=Modifier.weight(1f))
                                if(place==null)WarningLocationChoice{SeismicUserLocation.choose(context,it)}
                                if(landscape)TextButton(onClick={showDetails=true}){Text(if(historical)"歷史觀測與詳細資訊" else "地圖與詳細資訊",fontSize=14.sp,lineHeight=21.sp,color=palette.primary)}
                            }
                            val heroes:@Composable (Modifier)->Unit={heroModifier->
                                BoxWithConstraints(heroModifier){
                                    val cellWidth=(maxWidth.value-10f)/2f
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                        WarningHero("預估震度",intensity,intensityCaption,accent,Modifier.weight(1f).fillMaxHeight(),tight,cellWidth)
                                        WarningHero(if(historical)"演練示意倒數" else "預估抵達",arrivalText,arrivalCaption,palette.primary,Modifier.weight(1f).fillMaxHeight(),tight,cellWidth)
                                    }
                                }
                            }
                            val facts:@Composable (Modifier)->Unit={factModifier->
                                Column(factModifier,verticalArrangement=Arrangement.spacedBy(gap)){
                                    WarningPlate(compact=tight){
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                            WarningStatistic("規模",SeismicDisplayFormat.magnitude(incident.magnitude),Modifier.weight(1f))
                                            WarningStatistic("深度",SeismicDisplayFormat.depth(incident.depthKm),Modifier.weight(1f))
                                        }
                                    }
                                    Text(SeismicDisplayFormat.location(incident.epicenter),fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium,color=palette.onSurface,
                                        maxLines=if(tight)1 else 2,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth())
                                }
                            }
                            if(landscape&&!wide){
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                                    heroes(Modifier.weight(1.2f));facts(Modifier.weight(1f))
                                }
                            } else {heroes(Modifier.fillMaxWidth());facts(Modifier.fillMaxWidth())}
                            if(!landscape)TextButton(onClick={showDetails=true},modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)){
                                Text(if(historical)"歷史觀測與詳細資訊" else "地圖與詳細資訊",fontSize=14.sp,lineHeight=21.sp,color=palette.primary,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                            }
                        }
                    }
                    if(wide){
                        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(20.dp)){
                            summary(Modifier.width((paneWidth*.40f).coerceAtMost(470.dp)))
                            Box(Modifier.weight(1f).fillMaxHeight().testTag("seismic-inspector-pane")){
                                SeismicWarningMap(incident,if(historical)ChiChi921.historicalCountyMaxima else incident.localIntensityByCounty,place,historical,frameNow,
                                    Modifier.fillMaxWidth(),viewportHeight=paneHeight.coerceAtMost(640.dp))
                            }
                        }
                    }else summary(Modifier.fillMaxSize())
                }
                Column(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal=18.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("趴下 · 掩護 · 穩住",fontSize=if(landscape)18.sp else 22.sp,lineHeight=if(landscape)24.sp else 29.sp,fontWeight=FontWeight.Bold,color=palette.onSurface,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Button(onClick=onClose,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),colors=ButtonDefaults.buttonColors(containerColor=if(simulation||replay)palette.primary else palette.error,contentColor=if(simulation||replay)palette.onPrimary else palette.onError)){
                        Text(if(simulation)"結束模擬演練" else if(replay)"結束歷史回放" else "我已採取避難措施",fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                    }
                }
            }
        }
        }
        }
        if(showDetails)io.atmos.weather.ui.IOSFormSheet(if(historical)"921 歷史觀測" else "地震詳細資訊",{showDetails=false},onDone={showDetails=false}){
            Text(if(historical)"震源與縣市最大震度採官方歷史資料；本頁觀測不是所在地的模型預估。" else if(simulation)"所有震央與震度均為演練情境。" else "資料來源：${incident.source} · 第 ${incident.serial} 報",fontSize=14.sp,lineHeight=21.sp,color=palette.onSurfaceVariant)
            WarningPlate{
                Text("所在地震度：$intensityReason\n抵達時間：$arrivalReason",fontSize=14.sp,lineHeight=21.sp,color=palette.onSurfaceVariant)
                Text(SeismicDisplayFormat.location(incident.epicenter),fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
                WarningStatistic(if(historical)"歷史最大震度（舊制）" else "來源最大震度",if(historical)"7 級" else incident.maximumIntensity?.let{"${SeismicDisplayFormat.symbol(it)} 級"}?:"待來源提供",Modifier.fillMaxWidth())
                Text(if(historical)"歷史發震：1999/09/21 01:47:15.9（臺灣時間）\n震央：23.85°N · 120.82°E" else
                    "發布："+java.time.Instant.ofEpochMilli(incident.issuedAtMs).atZone(java.time.ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")),fontSize=14.sp,lineHeight=21.sp,color=palette.onSurfaceVariant)
                place?.let{Text(it.source,fontSize=14.sp,color=palette.onSurfaceVariant)}
            }
            Text(if(historical)"本機演練估算不代表當年即時速報；地形、土壤與斷層會影響實際搖晃。" else "本機估算可能有誤差。請立即避難，勿等待倒數。",fontSize=14.sp,lineHeight=21.sp,color=palette.onSurfaceVariant)
            SeismicWarningMap(incident,if(historical)ChiChi921.historicalCountyMaxima else incident.localIntensityByCounty,place,historical,frameNow,Modifier.fillMaxWidth(),viewportHeight=300.dp)
            WarningPlate{
                Text(if(historical)"921 歷史縣市最大震度（舊制）" else if(simulation)"演練情境震度（虛構）" else "各縣市預估震度",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
                (if(historical)SeismicWarningCounties.counties.map{CountyIntensity(it,ChiChi921.historicalCountyMaxima[it])} else allCounties).chunked(2).forEach{row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        row.forEach{entry->Column(Modifier.weight(1f).padding(vertical=6.dp)){
                            Text(entry.county,fontSize=14.sp,color=palette.onSurfaceVariant)
                            Text(entry.intensity?.let{"${SeismicDisplayFormat.symbol(it)} 級"}?:"—",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
                        }}
                        if(row.size==1)Spacer(Modifier.weight(1f))
                    }
                }
                Text(if(historical)"歷史最大值不是每個地點的震度；舊制 5、6 級不換算成現制強弱。— 為歷史報告未列示。" else "灰色與 — 表示來源尚未提供，並非震度為零。",fontSize=14.sp,lineHeight=21.sp,color=palette.onSurfaceVariant)
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
        Text(caption,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.fillMaxWidth())
    }
}
@Composable private fun WarningStatistic(label:String,value:String,modifier:Modifier){
    val p=MaterialTheme.colorScheme
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(label,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
        Text(value,color=p.onSurface,fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth())
    }
}
