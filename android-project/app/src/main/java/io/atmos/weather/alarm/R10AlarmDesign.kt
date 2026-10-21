package io.atmos.weather.alarm

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.R
import java.time.LocalTime
import java.util.Locale

/** Independently authored Compose Foundation controls, not Material picker or switch widgets. */
private val night=Color(0xFF101114)
private val group=Color(0xFF2B2C31)
private val secondary=Color(0xFFB9BAC4)
private val amber=Color(0xFFFFB32B)
private val weekdays=listOf("一","二","三","四","五","六","日")

object R10AlarmTones {
    const val prefix="thinn://alarm/"
    data class Choice(val id:String,val label:String,val description:String,val upper:Color,val lower:Color,val artwork:Int)
    val choices=listOf(
        Choice("dawn","晨光序曲","14 種天氣的清新鋼琴與自然音",Color(0xFFEF7AB0),Color(0xFF9D428F),R.drawable.alarm_art_dawn),
        Choice("rain","天氣絮語","14 種天氣的和聲與環境白噪音",Color(0xFF90EFF3),Color(0xFF258FC1),R.drawable.alarm_art_rain),
        Choice("wood","木息 Forest","樹影、木琴與鳥鳴意象",Color(0xFF74D1B9),Color(0xFF286E69),R.drawable.alarm_art_wood),
        Choice("water","水漣 Spring","泉水、水晶與清透鐘音",Color(0xFF99C1F4),Color(0xFF516BBB),R.drawable.alarm_art_water),
        Choice("fire","曜火 Dawn","暖光、晨曦與和緩脈動",Color(0xFFFFB877),Color(0xFFD16B68),R.drawable.alarm_art_fire),
        Choice("metal","金石 Bellglass","玻璃、水晶與金屬共鳴",Color(0xFFC9B4F8),Color(0xFF6C5AA8),R.drawable.alarm_art_metal)
    )
    fun isBuiltIn(uri:String?):Boolean=uri?.startsWith(prefix)==true && choices.any { prefix+it.id==uri }
    fun resource(uri:String?, weather:AlarmWeatherReading?, nowMs:Long):Int? {
        if(!isBuiltIn(uri)) return null
        val id=uri!!.removePrefix(prefix)
        val reliable=weather?.takeIf {
            it.fetchedEpochMs > 0 && nowMs-it.fetchedEpochMs in 0L..WeatherAlarmNarration.MAX_WEATHER_AGE_MS
        }
        val rainy=reliable?.condition in setOf("RAIN","THUNDERSTORM","HAIL","SLEET","FREEZING_RAIN","SNOW")
        return when(id) {
            "dawn" -> if(rainy) R.raw.thinn_alarm_dawn_rain else R.raw.thinn_alarm_dawn
            "rain" -> if(rainy) R.raw.thinn_alarm_rain else R.raw.thinn_alarm_rain_clear
            "wood" -> if(rainy) R.raw.thinn_alarm_wood_rain else R.raw.thinn_alarm_wood
            "water" -> if(rainy) R.raw.thinn_alarm_water_rain else R.raw.thinn_alarm_water
            "fire" -> if(rainy) R.raw.thinn_alarm_fire_rain else R.raw.thinn_alarm_fire
            "metal" -> if(rainy) R.raw.thinn_alarm_metal_rain else R.raw.thinn_alarm_metal
            else -> null
        }
    }
}

@Composable
fun R10Switch(checked:Boolean,onCheckedChange:(Boolean)->Unit) {
    val x by animateDpAsState(if(checked) 24.dp else 2.dp, animationSpec=spring(),label="switch thumb")
    val haptic=LocalHapticFeedback.current
    Box(Modifier.size(width=54.dp,height=32.dp).clip(CircleShape)
        .background(if(checked) Color(0xFF35CF65) else Color(0xFF565861))
        .clickable {haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);onCheckedChange(!checked)},contentAlignment=Alignment.CenterStart) {
        Box(Modifier.offset(x=x).size(28.dp).background(Color.White,CircleShape))
    }
}

@Composable
private fun GlassSetting(label:String, detail:String?=null,click:(()->Unit)?=null, trailing:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().heightIn(min=54.dp)
        .then(if(click!=null) Modifier.clickable(onClick=click) else Modifier)
        .padding(horizontal=18.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=Color.White,fontSize=17.sp,modifier=Modifier.weight(1f))
        if(trailing!=null) trailing()
        else if(detail!=null) Text(detail,color=secondary,fontSize=16.sp)
    }
}
@Composable
private fun Line(){Box(Modifier.fillMaxWidth().padding(start=18.dp).height(.5.dp).background(Color.White.copy(alpha=.13f)))}

/** Flinging LazyColumn wheel. Pixel-continuous drag, velocity decay and centered snap
 * are provided by Compose Foundation (not the old one-value-per-drag stepping).
 * The virtualized repeating sequence avoids abrupt wraparound at 12/59.
 */
@Composable
private fun Wheel(value:Int,count:Int,display:(Int)->String,onValue:(Int)->Unit,modifier:Modifier=Modifier) {
    val middle = 100 * count
    val state=rememberLazyListState(initialFirstVisibleItemIndex=middle+value-2)
    val snap=rememberSnapFlingBehavior(lazyListState=state)
    val haptic=LocalHapticFeedback.current
    val latestValue by rememberUpdatedState(value)
    // Follow the item physically nearest the central highlight, not finger delta thresholds.
    LaunchedEffect(state,count) {
        snapshotFlow {
            val info=state.layoutInfo
            val midpoint=(info.viewportStartOffset+info.viewportEndOffset)/2
            info.visibleItemsInfo.minByOrNull {kotlin.math.abs(it.offset+it.size/2-midpoint)}?.index
        }.distinctUntilChanged().collect { item ->
            if(item!=null) {
                val next=(item % count + count)%count
                if(next!=latestValue){onValue(next);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
            }
        }
    }
    LazyColumn(state=state,flingBehavior=snap,modifier=modifier.height(220.dp),
        contentPadding=PaddingValues(vertical=88.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        items(count*250) { item ->
            val selected=(item % count + count)%count
            val picked=selected==value
            Box(Modifier.fillMaxWidth().height(44.dp),contentAlignment=Alignment.Center) {
                Text(display(selected),color=if(picked) Color.White else Color.White.copy(alpha=.33f),
                    fontSize=if(picked) 31.sp else 23.sp,fontWeight=FontWeight.Light,
                    textAlign=TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MeridiemWheel(value:Int,onValue:(Int)->Unit,modifier:Modifier=Modifier) {
    val haptic=LocalHapticFeedback.current
    val density=LocalDensity.current
    val threshold=with(density) { 12.dp.toPx() }
    val latestValue by rememberUpdatedState(value)
    val latestUpdate by rememberUpdatedState(onValue)
    var totalDrag by remember { mutableFloatStateOf(0f) }
    val reduceMotion=LocalContext.current.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    val targetY=if(value==V25AlarmMeridiem.AM) 0.dp else 55.dp
    val selectedY by animateDpAsState(
        targetValue=targetY,
        animationSpec=if(reduceMotion) tween(durationMillis=1) else spring(
            dampingRatio=0.86f,stiffness=420f),label="v25-am-pm-wheel")
    val shape=RoundedCornerShape(22.dp)
    Box(modifier.height(213.dp).clip(shape)
        .pointerInput(threshold) {
            detectVerticalDragGestures(
                onDragStart={totalDrag=0f},
                onVerticalDrag={change,dy -> change.consume(); totalDrag+=dy},
                onDragEnd={
                    // Labels never exchange places. Direction selects the specific row.
                    val next=V25AlarmMeridiem.fromDrag(latestValue,totalDrag,threshold)
                    if(next!=latestValue) {
                        latestUpdate(next)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    totalDrag=0f
                },
                onDragCancel={totalDrag=0f}
            )
        },contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(102.dp)) {
            Box(Modifier.fillMaxWidth().height(47.dp).offset(y=selectedY)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White.copy(alpha=.16f))
                .border(1.dp,Color.White.copy(alpha=.36f),RoundedCornerShape(17.dp)))
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(47.dp),contentAlignment=Alignment.Center) {
                    Text("上午",color=Color.White.copy(alpha=if(value==0) 1f else .55f),
                        fontSize=if(value==0) 28.sp else 23.sp,fontWeight=FontWeight.Light)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(47.dp),contentAlignment=Alignment.Center) {
                    Text("下午",color=Color.White.copy(alpha=if(value==1) 1f else .55f),
                        fontSize=if(value==1) 28.sp else 23.sp,fontWeight=FontWeight.Light)
                }
            }
        }
    }
}


@Composable
fun R10AlarmEditor(initial:WeatherAlarm?,onClose:()->Unit,onDelete:(WeatherAlarm)->Unit,onSave:(WeatherAlarm)->Unit) {
    val now=remember { LocalTime.now().plusMinutes(5) }
    var hour by remember(initial) { mutableIntStateOf(initial?.hour?:now.hour) }
    var minute by remember(initial) { mutableIntStateOf(initial?.minute?:now.minute) }
    var label by remember(initial) { mutableStateOf(initial?.title?:"鬧鐘") }
    var repeat by remember(initial) { mutableStateOf(initial?.days?:emptySet()) }
    var voice by remember(initial) { mutableStateOf(initial?.weatherVoice?:true) }
    var snooze by remember(initial) { mutableStateOf(initial?.snooze?:true) }
    var uri by remember(initial) { mutableStateOf(initial?.ringtoneUri?:R10AlarmTones.prefix+"dawn") }
    var choosing by remember { mutableStateOf(false) }
    var repeats by remember { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf(false) }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(night).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("取消",color=amber,fontSize=17.sp,modifier=Modifier.clickable(onClick=onClose))
                Text(if(initial==null)"加入鬧鐘" else "編輯鬧鐘",color=Color.White,fontSize=18.sp,
                    textAlign=TextAlign.Center,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                Text("儲存",color=amber,fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.clickable {
                    val id=initial?.id?:((System.currentTimeMillis()%1000000).toInt()+1)
                    onSave(WeatherAlarm(id,hour,minute,label.take(30),repeat,true,voice,snooze,uri))
                })
            }
            Box(Modifier.fillMaxWidth().padding(horizontal=32.dp).height(235.dp),contentAlignment=Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(45.dp).background(Color(0xFF303137),RoundedCornerShape(12.dp)))
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                    MeridiemWheel(if(hour>=12) 1 else 0,{meridiem->hour=V25AlarmMeridiem.hour24(hour%12,meridiem)},Modifier.weight(1.1f))
                    Wheel(hour%12,12,{if(it==0)"12" else "$it"},
                        {v->hour=(hour/12)*12+v},Modifier.weight(.85f))
                    Wheel(minute,60,{"%02d".format(Locale.TAIWAN,it)},
                        {minute=it},Modifier.weight(.85f))
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
                Spacer(Modifier.height(22.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(group)) {
                    GlassSetting("重複",detail=if(repeat.isEmpty())"永不" else "每週 ${repeat.sorted().joinToString("") { weekdays[it-1] }}  ›",click={repeats=true});Line()
                    GlassSetting("標籤",detail=label+"  ›",click={editingLabel=true});Line()
                    GlassSetting("提示聲",detail=(R10AlarmTones.choices.firstOrNull {R10AlarmTones.prefix+it.id==uri}?.label?:"系統鈴聲")+"  ›",click={choosing=true});Line()
                    GlassSetting("稍後提醒",trailing={R10Switch(snooze){snooze=it}})
                }
                Spacer(Modifier.height(22.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(group)) {
                    GlassSetting("智慧天氣語音",trailing={R10Switch(voice){voice=it}})
                }
                if(initial!=null) {
                    Spacer(Modifier.height(10.dp))
                    Text("刪除此鬧鐘",color=Color(0xFFFF7D82),fontSize=17.sp,textAlign=TextAlign.Center,
                        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(group)
                            .clickable {onDelete(initial)}.padding(17.dp))
                }
            }
        }
    }
    if(choosing) R10RingtonePicker(selected=uri,onChoose={uri=it;choosing=false},onClose={choosing=false})
    if(repeats) Dialog(onDismissRequest={repeats=false}) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(night).padding(22.dp)) {
            Text("重複",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            weekdays.forEachIndexed {index,name ->
                val d=index+1
                GlassSetting("星期$name",trailing={Text(if(d in repeat)"✓" else "",color=amber,fontSize=22.sp)},click={
                    repeat=if(d in repeat) repeat-d else repeat+d
                })
                Line()
            }
            Text("完成",color=amber,modifier=Modifier.align(Alignment.End).clickable {repeats=false}.padding(15.dp))
        }
    }
    if(editingLabel) Dialog(onDismissRequest={editingLabel=false}) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(group).padding(20.dp)) {
            Text("鬧鐘標籤",color=Color.White,fontSize=20.sp)
            Spacer(Modifier.height(20.dp))
            BasicTextField(value=label,onValueChange={label=it.take(30)},singleLine=true,
                textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=19.sp),
                modifier=Modifier.fillMaxWidth().background(Color.Black.copy(alpha=.4f),RoundedCornerShape(12.dp)).padding(14.dp))
            Text("完成",color=amber,modifier=Modifier.align(Alignment.End).clickable {editingLabel=false}.padding(15.dp))
        }
    }
}

@Composable
private fun R10RingtonePicker(selected:String?,onChoose:(String)->Unit,onClose:()->Unit) {
    val ctx=androidx.compose.ui.platform.LocalContext.current
    var active by remember { mutableStateOf(selected?:R10AlarmTones.prefix+"dawn") }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    val openAudio=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { chosen ->
        if(chosen!=null) {
            runCatching {ctx.contentResolver.takePersistableUriPermission(chosen,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            preview?.release();preview=null
            active=chosen.toString()
            onChoose(active)
        }
    }
    DisposableEffect(Unit) {onDispose {preview?.release()}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(night).statusBarsPadding().navigationBarsPadding().padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth().height(68.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("返回",color=amber,fontSize=17.sp,modifier=Modifier.clickable(onClick=onClose))
                Text("鬧鐘鈴聲",color=Color.White,fontSize=19.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
                Text("完成",color=amber,fontSize=17.sp,modifier=Modifier.clickable {preview?.release();preview=null;onChoose(active)})
            }
            Text("專屬自然系鈴聲",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("原創音色 · 隨有效天氣資訊變換編曲，點按卡片即可試聽。",
                color=secondary,fontSize=14.sp,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                R10AlarmTones.choices.chunked(2).forEach {row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        row.forEach {choice->
                            val chosen=active==R10AlarmTones.prefix+choice.id
                            Box(Modifier.weight(1f).height(206.dp).clip(RoundedCornerShape(22.dp))
                                .border(if(chosen) 2.dp else 1.dp,if(chosen) Color.White else Color.White.copy(alpha=.20f),RoundedCornerShape(22.dp))
                                .clickable {
                                    active=R10AlarmTones.prefix+choice.id
                                    preview?.release();preview=null
                                    val res=R10AlarmTones.resource(active,WeatherAlarmSpeechCache.load(ctx),System.currentTimeMillis())
                                    preview=if(res!=null) runCatching {MediaPlayer.create(ctx,res)?.apply {
                                        setOnCompletionListener {p->p.release();if(preview===p)preview=null}
                                        start()
                                    }}.getOrNull() else null
                                }) {
                                Image(painterResource(choice.artwork),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.08f),Color.Transparent,Color.Black.copy(alpha=.72f)))))
                                Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween) {
                                    Text(if(chosen)"✓ 已選擇" else "♪ 試聽",color=Color.White,fontSize=14.sp)
                                    Column {
                                        Text(choice.label,color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                                        Text(choice.description,color=Color.White.copy(alpha=.93f),fontSize=14.sp,lineHeight=17.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha=.1f)).border(1.dp,Color.White.copy(alpha=.16f),RoundedCornerShape(20.dp))
                    .clickable {openAudio.launch(arrayOf("audio/*"))}
                    .padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("▣",color=Color(0xFF9BDDFB),fontSize=24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("從手機選擇音樂",fontSize=17.sp,color=Color.White)
                        Text("音樂資料夾、下載或雲端檔案",fontSize=14.sp,color=secondary)
                    }
                    Text("›",fontSize=26.sp,color=secondary)
                }
                if(active.startsWith("content://")) Text("✓ 已選擇本機音樂",color=Color(0xFFA4E8C0),fontSize=14.sp,
                    modifier=Modifier.padding(10.dp))
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
