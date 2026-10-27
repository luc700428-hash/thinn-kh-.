package io.atmos.weather.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Real, offline utilities. The stopwatch uses monotonic time, including background time. */
@Composable fun EverydayToolsSheet(tool:String?,onDismiss:()->Unit) {
    var accumulated by rememberSaveable {mutableLongStateOf(0L)}
    var started by rememberSaveable {mutableStateOf<Long?>(null)}
    var laps by rememberSaveable {mutableStateOf(listOf<Long>())}
    var category by rememberSaveable {mutableIntStateOf(0)}
    var input by rememberSaveable {mutableStateOf("20")}
    var source by rememberSaveable {mutableIntStateOf(0)}
    var target by rememberSaveable {mutableIntStateOf(1)}
    var now by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
    var wallNow by remember {mutableStateOf(ZonedDateTime.now())}
    LaunchedEffect(tool,started) {
        if(started!=null && started!!>SystemClock.elapsedRealtime()) {started=null;accumulated=0L}
        while(tool!=null) {now=SystemClock.elapsedRealtime();wallNow=ZonedDateTime.now();if(tool=="碼錶" && started!=null)withFrameNanos {} else delay(1000)}
    }
    if(tool==null)return
    val palette=MaterialTheme.colorScheme
    val reduced=rememberAppleReduceMotion()
    var entered by remember(tool) {mutableStateOf(false)}
    var closing by remember(tool) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(tool) {entered=true}
    val reveal by animateFloatAsState(if(entered)1f else 0f,tween(if(reduced)0 else if(closing)200 else 380,easing=AppleMotion.snap),label="工具抽屜轉場")
    fun closeSheet() {
        if(closing)return
        closing=true;entered=false
        scope.launch {if(!reduced)delay(200);onDismiss()}
    }
    Dialog(onDismissRequest={closeSheet()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.88f)
                .graphicsLayer {translationY=(1f-reveal)*size.height;alpha=.8f+.2f*reveal}.clip(RoundedCornerShape(topStart=32.dp,topEnd=32.dp))
                .background(palette.background).thinnSpecularEdge(32.dp).navigationBarsPadding().imePadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(tool,fontSize=23.sp,fontWeight=FontWeight.Bold,color=palette.onSurface,modifier=Modifier.weight(1f))
                    GlassIconButton(onClick={closeSheet()}) {Text("×",fontSize=27.sp,color=palette.onSurface)}
                }
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    when(tool) {
                        "碼錶" -> {
                            val elapsed=EverydayToolMath.elapsed(accumulated,started,now)
                            Text(stopwatchTime(elapsed),fontSize=54.sp,fontWeight=FontWeight.Light,color=palette.onSurface,
                                style=TextStyle(fontFeatureSettings="tnum",letterSpacing=(-2).sp),modifier=Modifier.align(Alignment.CenterHorizontally))
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                GlassOutlinedButton(onClick={if(started==null){accumulated=0L;laps=emptyList()} else laps=laps+EverydayToolMath.elapsed(accumulated,started,SystemClock.elapsedRealtime())},modifier=Modifier.weight(1f)) {
                                    Text(if(started==null)"重置" else "計圈")
                                }
                                GlassButton(onClick={
                                    val instant=SystemClock.elapsedRealtime()
                                    if(started==null)started=instant else {accumulated=EverydayToolMath.elapsed(accumulated,started,instant);started=null}
                                    now=instant
                                },modifier=Modifier.weight(1f)) {Text(if(started==null)"開始" else "暫停")}
                            }
                            laps.asReversed().forEachIndexed {index,total ->
                                val original=laps.lastIndex-index
                                Row(Modifier.fillMaxWidth().padding(vertical=9.dp)) {
                                    Text("第 ${original+1} 圈",color=palette.onSurfaceVariant,modifier=Modifier.weight(1f))
                                    Text(stopwatchTime(total-laps.getOrElse(original-1){0L}),color=palette.onSurface,style=TextStyle(fontFeatureSettings="tnum"))
                                }
                                GlassDivider()
                            }
                        }
                        "氣象單位換算" -> {
                            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                EverydayToolMath.categories.forEachIndexed {i,c ->
                                    GlassTextButton(onClick={category=i;source=0;target=1}) {Text(c.title,color=if(category==i)palette.primary else palette.onSurfaceVariant)}
                                }
                            }
                            val units=EverydayToolMath.categories[category].units
                            Text("輸入數值",color=palette.onSurfaceVariant,fontSize=14.sp)
                            BasicTextField(input,{input=it.take(24)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                                textStyle=TextStyle(fontSize=36.sp,color=palette.onSurface,fontFeatureSettings="tnum"),
                                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(18.dp))
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                GlassOutlinedButton(onClick={source=(source+1)%units.size},modifier=Modifier.weight(1f)) {Text(units[source].symbol)}
                                GlassIconButton(onClick={val old=source;source=target;target=old}) {Text("⇄",fontSize=24.sp,color=palette.primary)}
                                GlassOutlinedButton(onClick={target=(target+1)%units.size},modifier=Modifier.weight(1f)) {Text(units[target].symbol)}
                            }
                            val number=input.trim().replace(",","").toDoubleOrNull()?.takeIf {it.isFinite()}
                            val result=number?.let {EverydayToolMath.convert(it,units[source],units[target])}
                            Text(result?.let {String.format(Locale.US,"%.4f",it).trimEnd('0').trimEnd('.')+" "+units[target].symbol} ?: "請輸入有效數值",
                                fontSize=32.sp,lineHeight=42.sp,color=palette.primary,fontWeight=FontWeight.SemiBold)
                            Text("點選單位即可切換，⇄ 交換來源與目標；換算可離線使用。",fontSize=14.sp,color=palette.onSurfaceVariant)
                        }
                        "世界時鐘" -> {
                            val formatter=DateTimeFormatter.ofPattern("HH:mm:ss")
                            val date=DateTimeFormatter.ofPattern("MM/dd · EEE",Locale.TRADITIONAL_CHINESE)
                            listOf("臺北" to "Asia/Taipei","東京" to "Asia/Tokyo","倫敦" to "Europe/London",
                                "紐約" to "America/New_York","雪梨" to "Australia/Sydney").forEach {(city,zone) ->
                                val time=wallNow.withZoneSameInstant(ZoneId.of(zone))
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {Text(city,fontSize=19.sp,color=palette.onSurface);Text(time.format(date),fontSize=14.sp,color=palette.onSurfaceVariant)}
                                    Text(time.format(formatter),fontSize=29.sp,color=palette.onSurface,style=TextStyle(fontFeatureSettings="tnum"))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
private fun stopwatchTime(ms:Long):String=String.format(Locale.US,"%02d:%02d.%02d",ms/60000,(ms/1000)%60,(ms/10)%100)
