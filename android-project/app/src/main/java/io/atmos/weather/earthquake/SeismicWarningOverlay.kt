package io.atmos.weather.earthquake

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import io.atmos.weather.ui.thinnSpecularEdge
import io.atmos.weather.ui.GlassIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

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
            SeismicSpeech.speak(context,SeismicSpeech.warning(incident,SeismicUserLocation.current(context)))
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
    Dialog(onDismissRequest=onClose,properties=DialogProperties(dismissOnClickOutside=false,usePlatformDefaultWidth=false,decorFitsSystemWindows=false),adaptiveContentWidth=null){
        io.atmos.weather.ui.AdaptiveWindowProvider(Modifier.background(palette.background)) {
        io.atmos.weather.ui.AdaptivePageFrame(maxContentWidth=1280.dp) {
        Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding().graphicsLayer{alpha=reveal;scaleX=reveal;scaleY=reveal}){
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(if(historical)"臺灣 921 大地震演練" else if(simulation)"地震演練" else if(replay)"歷史地震演練" else "緊急地震速報",fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                    Text(if(historical)"1999/09/21 歷史情境 · 並非真實速報" else if(simulation)"虛構情境 · 並非真實速報" else if(replay)"過去事件回放 · 非即時警報" else "資料來源：${incident.source}",fontSize=14.sp,lineHeight=21.sp,color=palette.onSurfaceVariant,modifier=Modifier.fillMaxWidth())
                }
                GlassIconButton(onClick=onClose){Icon(Icons.Rounded.Close,"關閉速報",tint=palette.onSurface)}
            }
            io.atmos.weather.ui.AdaptiveScrollingPanes(Modifier.weight(1f),horizontalPadding=18.dp,
                topPadding=0.dp,bottomPadding=16.dp,paneSpacing=14.dp,applyStatusBars=false,leading={
                if(simulation||replay)WarningPlate{
                    Text(if(historical)"演練中，非即時警報。震源採官方歷史資料；當地預估為本機模型，並非當年的即時速報。" else if(simulation)"演練中：所有震央與震度均為虛構。" else "歷史回放中：不是目前發生的地震。",color=palette.onSurface,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                }
                Text("收到地震速報 · 慎防強烈搖晃",color=palette.onSurface,fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(county.ifBlank{"所在地尚未設定"},color=palette.onSurfaceVariant,fontSize=15.sp,lineHeight=22.sp,modifier=Modifier.weight(1f))
                    if(place==null)WarningLocationChoice{selected->
                        SeismicUserLocation.choose(context,selected)
                    }
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    WarningHero("預估震度",local?.let{if(it.contains("以上"))it.replace("級"," 級") else "${SeismicDisplayFormat.symbol(it)} 級"}?:"—",
                        if(historical)if(estimate==null)"請在地震設定選擇所在地" else "本機模型估算 · 僅供演練" else prediction.intensityReason,accent,Modifier.weight(1f).fillMaxHeight())
                    WarningHero(if(historical)"演練示意倒數" else "預估抵達",arrivalText,
                        if(historical)"模型示意 · 非官方預測" else prediction.arrivalReason,palette.primary,Modifier.weight(1f).fillMaxHeight())
                }
                if(place!=null)Text(place!!.source,color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                WarningPlate{
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        WarningStatistic("規模",SeismicDisplayFormat.magnitude(incident.magnitude),Modifier.weight(1f))
                        WarningStatistic("深度",SeismicDisplayFormat.depth(incident.depthKm),Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(SeismicDisplayFormat.location(incident.epicenter),fontSize=21.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        WarningStatistic("最大震度",if(historical)"7 級" else incident.maximumIntensity?.let{"${SeismicDisplayFormat.symbol(it)} 級"}?:"待來源提供",Modifier.weight(1f))
                        WarningStatistic("報次","第 ${incident.serial} 報",Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if(historical)"歷史發震：1999/09/21 01:47:15.9（臺灣時間）\n震央：23.85°N · 120.82°E" else
                        "發布："+java.time.Instant.ofEpochMilli(incident.issuedAtMs).atZone(java.time.ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")),color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                }
                if(historical)Text("演練估算無法精確到街道；實際搖晃會受地形、土壤與斷層影響。",color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                else if(prediction.intensityOrigin==PredictionOrigin.LOCAL_MODEL||prediction.arrivalOrigin==PredictionOrigin.LOCAL_MODEL)
                    Text("本機估算可能有誤差；縣市灰色不代表無震。請立即避難，勿等待倒數。",color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
            },trailing={
                SeismicWarningMap(incident,if(historical)ChiChi921.historicalCountyMaxima else incident.localIntensityByCounty,place,historical,frameNow,Modifier.fillMaxWidth())
                WarningPlate{
                    Text(if(historical)"921 歷史縣市最大震度（舊制）" else if(simulation)"演練情境震度（虛構）" else "各縣市預估震度",fontSize=17.sp,lineHeight=25.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    (if(historical)SeismicWarningCounties.counties.map{CountyIntensity(it,ChiChi921.historicalCountyMaxima[it])} else allCounties).chunked(2).forEach{row->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            row.forEach{entry->Column(Modifier.weight(1f).padding(vertical=6.dp)){
                                Text(entry.county,fontSize=14.sp,color=palette.onSurfaceVariant,modifier=Modifier.fillMaxWidth())
                                Text(entry.intensity?.let{"${SeismicDisplayFormat.symbol(it)} 級"}?:"—",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface,modifier=Modifier.fillMaxWidth())
                            }}
                            if(row.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(if(historical)"歷史縣市最大值不是每個地點的震度；舊制 5、6 級不換算成現制強弱。— 為歷史報告未列示。" else "灰色與 — 表示來源尚未提供，並非震度為零。",color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
            })
            Column(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal=18.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Text("趴下 · 掩護 · 穩住",fontSize=23.sp,lineHeight=31.sp,fontWeight=FontWeight.Bold,color=palette.onSurface,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick=onClose,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),colors=ButtonDefaults.buttonColors(containerColor=if(simulation||replay)palette.primary else palette.error,contentColor=if(simulation||replay)palette.onPrimary else palette.onError)){
                    Text(if(simulation)"結束模擬演練" else if(replay)"結束歷史回放" else "我已採取避難措施",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
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
@Composable private fun WarningPlate(content:@Composable ColumnScope.()->Unit){
    val p=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface).border(1.dp,p.outlineVariant.copy(alpha=.5f),RoundedCornerShape(24.dp)).padding(16.dp),content=content)
}
@Composable private fun WarningHero(label:String,value:String,caption:String,accent:Color,modifier:Modifier){
    val p=MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(p.surface).border(1.dp,p.outlineVariant.copy(alpha=.5f),RoundedCornerShape(24.dp)).padding(14.dp)){
        Text(label,color=p.onSurfaceVariant,fontSize=15.sp,lineHeight=22.sp,modifier=Modifier.fillMaxWidth())
        val longValue=value.length>5||value.contains("以上")||value=="已抵達"||value=="可能已抵達"
        Text(value,color=accent,fontSize=if(longValue)25.sp else 38.sp,lineHeight=if(longValue)36.sp else 48.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"))
        Text(caption,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
    }
}
@Composable private fun WarningStatistic(label:String,value:String,modifier:Modifier){
    val p=MaterialTheme.colorScheme
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(label,color=p.onSurfaceVariant,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
        Text(value,color=p.onSurface,fontSize=19.sp,lineHeight=27.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth())
    }
}
