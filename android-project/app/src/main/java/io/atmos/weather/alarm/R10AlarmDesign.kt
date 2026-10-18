package io.atmos.weather.alarm

import android.content.Context
import android.media.MediaPlayer
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
    data class Choice(val id:String,val label:String,val description:String,val upper:Color,val lower:Color)
    val choices=listOf(
        Choice("dawn","晨曦微風","隨天氣變化 · 清晨光感",Color(0xFFEF7AB0),Color(0xFF9D428F)),
        Choice("rain","雨後窗聲","隨天氣變化 · 雨滴與木音",Color(0xFF90EFF3),Color(0xFF258FC1)),
        Choice("wood","木息 Forest","林間木琴 · 自然柔和",Color(0xFF74D1B9),Color(0xFF286E69)),
        Choice("water","水漣 Spring","山泉水滴 · 透明鐘音",Color(0xFF99C1F4),Color(0xFF516BBB)),
        Choice("fire","曜火 Dawn","溫暖漸進 · 日出能量",Color(0xFFFFB877),Color(0xFFD16B68)),
        Choice("metal","金石 Bellglass","清透和弦 · 明確醒腦",Color(0xFFC9B4F8),Color(0xFF6C5AA8))
    )
    fun isBuiltIn(uri:String?):Boolean=uri?.startsWith(prefix)==true && choices.any { prefix+it.id==uri }
    fun resource(uri:String?, weather:AlarmWeatherReading?, nowMs:Long):Int? {
        if(!isBuiltIn(uri)) return null
        val id=uri!!.removePrefix(prefix)
        val reliable=weather?.takeIf {
            it.fetchedEpochMs > 0 && nowMs-it.fetchedEpochMs in 0L..WeatherAlarmNarration.MAX_WEATHER_AGE_MS
        }
        val rainy=reliable?.condition in setOf("RAIN","THUNDERSTORM","HAIL","SLEET","FREEZING_RAIN","SNOW","DRIZZLE","HEAVY_RAIN")
        val clear=reliable?.condition in setOf("CLEAR","MOSTLY_CLEAR","PARTLY_CLOUDY","SUNNY","FAIR")
        return when(id) {
            "dawn" -> if(rainy) R.raw.thinn_alarm_dawn_rain else if(clear) R.raw.thinn_alarm_dawn_clear else R.raw.thinn_alarm_dawn
            "rain" -> if(rainy) R.raw.thinn_alarm_rain_rain else if(clear) R.raw.thinn_alarm_rain_clear else R.raw.thinn_alarm_rain
            "wood" -> if(rainy) R.raw.thinn_alarm_wood_rain else if(clear) R.raw.thinn_alarm_wood_clear else R.raw.thinn_alarm_wood
            "water" -> if(rainy) R.raw.thinn_alarm_water_rain else if(clear) R.raw.thinn_alarm_water_clear else R.raw.thinn_alarm_water
            "fire" -> if(rainy) R.raw.thinn_alarm_fire_rain else if(clear) R.raw.thinn_alarm_fire_clear else R.raw.thinn_alarm_fire
            "metal" -> if(rainy) R.raw.thinn_alarm_metal_rain else if(clear) R.raw.thinn_alarm_metal_clear else R.raw.thinn_alarm_metal
            else -> null
        }
    }
}

@Composable
fun R10Switch(checked:Boolean,onCheckedChange:(Boolean)->Unit) {
    // One consistent optically highlighted switch and hardware-calibrated haptic feedback.
    io.atmos.weather.ui.GlassSwitch(checked=checked,onCheckedChange=onCheckedChange)
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

/** Foundation-drawn, continuous inertial wheel: drag follows the finger, release decays,
 * then settles with a damped spring. Value is committed only after the animation finishes.
 */
@Composable
private fun Wheel(value:Int,count:Int,display:(Int)->String,onValue:(Int)->Unit,modifier:Modifier=Modifier) {
    val density=LocalDensity.current
    val stepPx=with(density) { 41.dp.toPx() }
    val scope=rememberCoroutineScope()
    val haptics=LocalHapticFeedback.current
    val reduceMotion=LocalContext.current.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var movingJob by remember { mutableStateOf<Job?>(null) }
    val liveValue by rememberUpdatedState(value)
    val liveUpdate by rememberUpdatedState(onValue)
    val shifted= (offsetPx/stepPx).roundToInt()
    val fraction=offsetPx-shifted*stepPx
    Box(modifier.height(213.dp).clip(RoundedCornerShape(18.dp))
        .pointerInput(count,stepPx,reduceMotion) {
            var tracker=VelocityTracker()
            detectVerticalDragGestures(
                onDragStart={ position ->
                    movingJob?.cancel()
                    tracker=VelocityTracker()
                    // Initial contact is recorded by the first real drag event.
                },
                onVerticalDrag={ change, distance ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis,change.position)
                    offsetPx=(offsetPx+distance).coerceIn(-stepPx*160f,stepPx*160f)
                },
                onDragEnd={
                    val speed=tracker.calculateVelocity().y.coerceIn(-4400f,4400f)
                    movingJob=scope.launch {
                        val motion=Animatable(offsetPx)
                        if(!reduceMotion && abs(speed)>120f) {
                            motion.animateDecay(speed,exponentialDecay(frictionMultiplier=1.65f)) {
                                offsetPx=value.coerceIn(-stepPx*160f,stepPx*160f)
                            }
                        }
                        val nearest=(offsetPx/stepPx).roundToInt().coerceIn(-160,160)
                        if(!reduceMotion) {
                            motion.animateTo(nearest*stepPx,spring(dampingRatio=.79f,stiffness=360f)) {
                                offsetPx=value
                            }
                        }
                        if(nearest!=0) {
                            liveUpdate(((liveValue-nearest)%count+count)%count)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        offsetPx=0f
                    }
                },onDragCancel={
                    movingJob=scope.launch {
                        val snap=(offsetPx/stepPx).roundToInt()
                        if(snap!=0) liveUpdate(((liveValue-snap)%count+count)%count)
                        offsetPx=0f
                    }
                }
            )
        },contentAlignment=Alignment.Center) {
        Column(Modifier.fillMaxWidth().offset {IntOffset(0,fraction.roundToInt())},
            horizontalAlignment=Alignment.CenterHorizontally) {
            for (row in -3..3) {
                val distance=abs(row-fraction/stepPx)
                val item=((value+row-shifted)%count+count)%count
                Box(Modifier.fillMaxWidth().height(41.dp).clickable {
                    movingJob?.cancel()
                    liveUpdate(item)
                    offsetPx=0f
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },contentAlignment=Alignment.Center) {
                    Text(display(item),textAlign=TextAlign.Center,
                        color=Color.White.copy(alpha=when {
                            distance<.6f -> 1f
                            distance<1.6f -> .58f
                            distance<2.6f -> .26f
                            else -> .06f
                        }),fontSize=if(distance<.6f) 30.sp else 23.sp,fontWeight=FontWeight.Light,
                        maxLines=1)
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
                    Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha=.11f))
                        .border(1.dp,Color.White.copy(alpha=.24f),RoundedCornerShape(18.dp))
                        .clickable { hour=(hour+12)%24 },contentAlignment=Alignment.Center) {
                        Text(if(hour<12) "上午" else "下午",color=Color.White,fontSize=24.sp)
                    }
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
    DisposableEffect(Unit) {onDispose {preview?.release()}}
    val localAudioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if(picked!=null) {
            val access=Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { ctx.contentResolver.takePersistableUriPermission(picked,access) }
            preview?.release(); preview=null
            active=picked.toString()
            preview=runCatching { MediaPlayer.create(ctx,picked)?.apply {
                setOnCompletionListener {player->player.release();if(preview===player)preview=null}
                start()
            } }.getOrNull()
        }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(night).statusBarsPadding().navigationBarsPadding().padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth().height(68.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("返回",color=amber,fontSize=17.sp,modifier=Modifier.clickable(onClick=onClose))
                Text("鬧鐘鈴聲",color=Color.White,fontSize=19.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
                Text("完成",color=amber,fontSize=17.sp,modifier=Modifier.clickable {preview?.release();preview=null;onChoose(active)})
            }
            Text("專屬自然系鈴聲",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("點選卡片選取並試聽。六款都會依有效天氣資料調整聲景；沒有新資料時播放中性版本。",
                color=secondary,fontSize=14.sp,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                R10AlarmTones.choices.chunked(2).forEach {row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        row.forEach {choice->
                            val chosen=active==R10AlarmTones.prefix+choice.id
                            Column(Modifier.weight(1f).height(176.dp).clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(listOf(choice.upper,choice.lower)))
                                .border(if(chosen) 2.dp else 1.dp,if(chosen) Color.White else Color.White.copy(alpha=.18f),RoundedCornerShape(22.dp))
                                .clickable {
                                    active=R10AlarmTones.prefix+choice.id
                                    preview?.release()
                                    val res=R10AlarmTones.resource(active,WeatherAlarmSpeechCache.load(ctx),System.currentTimeMillis())
                                    preview=if(res!=null) runCatching {MediaPlayer.create(ctx,res)?.apply {
                                        setOnCompletionListener {p->p.release();if(preview===p)preview=null}
                                        start()
                                    }}.getOrNull() else null
                                }.padding(14.dp),verticalArrangement=Arrangement.SpaceBetween) {
                                Box(Modifier.fillMaxWidth().height(88.dp)) {
                                    ToneArtwork(choice.id,Modifier.fillMaxSize())
                                    Text(if(chosen) "✓ 已選擇" else "▶ 試聽",color=Color.White,fontSize=14.sp,
                                        modifier=Modifier.align(Alignment.TopStart)
                                            .background(Color.Black.copy(alpha=.23f),RoundedCornerShape(14.dp))
                                            .padding(horizontal=8.dp,vertical=4.dp))
                                }
                                Column {
                                    Text(choice.label,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                                    Text(choice.description,color=Color.White.copy(alpha=.93f),fontSize=14.sp,lineHeight=17.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha=.13f))
                    .border(1.dp,Color.White.copy(alpha=.30f),RoundedCornerShape(22.dp))
                    .clickable { localAudioPicker.launch(arrayOf("audio/*")) }
                    .padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("♫",color=Color.White,fontSize=29.sp,modifier=Modifier.padding(end=16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("選擇本地音樂",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        Text("從手機資料夾或其他檔案提供者選取音訊",color=secondary,fontSize=14.sp)
                    }
                    Text(if(active.startsWith("content://")) "✓" else "›",color=amber,fontSize=24.sp)
                }
            }
        }
    }
}

/** Six decorative, original vector mini-scenes drawn in Compose, no bundled Apple/Xiaomi art. */
@Composable
private fun ToneArtwork(id:String,modifier:Modifier=Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val c=Offset(size.width*.54f,size.height*.50f)
        val r=size.minDimension*.30f
        when(id) {
            "dawn","fire" -> {
                drawCircle(Color(0xFFFFF0AE).copy(alpha=.24f),r*1.28f,c)
                drawCircle(Color(0xFFFFF5C8).copy(alpha=.95f),r*.74f,c)
                for(i in 0..9){
                    val a=i*2*Math.PI/10
                    val cs=kotlin.math.cos(a).toFloat();val sn=kotlin.math.sin(a).toFloat()
                    drawLine(Color.White.copy(alpha=.64f),c+Offset(cs*r,sn*r),
                        c+Offset(cs*r*1.17f,sn*r*1.17f),2.dp.toPx())
                }
            }
            "rain","water" -> {
                for(i in 0..6){
                    val x=size.width*(.15f+.11f*i)
                    val y=size.height*(.25f+.08f*(i%3))
                    drawLine(Color(0xFFD9FCFF).copy(alpha=.74f),Offset(x,y),
                        Offset(x-8.dp.toPx(),y+22.dp.toPx()),2.2.dp.toPx())
                }
                drawCircle(Color(0xFFE2FAFF).copy(alpha=.24f),r,c)
                drawCircle(Color.White.copy(alpha=.68f),r*.24f,c)
            }
            "wood" -> {
                val leaf=Path().apply {
                    moveTo(c.x-r,c.y+r*.4f)
                    quadraticTo(c.x-r,c.y-r*1.5f,c.x+r,c.y-r)
                    quadraticTo(c.x+r*1.2f,c.y+r*.65f,c.x-r,c.y+r*.4f)
                    close()
                }
                drawPath(leaf,Color(0xFFE2FFBD).copy(alpha=.82f))
                drawLine(Color(0xFF35744D),c+Offset(-r*.9f,r*.4f),c+Offset(r*.9f,-r*.8f),2.dp.toPx())
            }
            else -> {
                val crystal=Path().apply {
                    moveTo(c.x,c.y-r*1.2f)
                    lineTo(c.x+r*.74f,c.y-r*.35f)
                    lineTo(c.x+r*.7f,c.y+r*.65f)
                    lineTo(c.x,c.y+r*1.2f)
                    lineTo(c.x-r*.7f,c.y+r*.65f)
                    lineTo(c.x-r*.74f,c.y-r*.35f)
                    close()
                }
                drawPath(crystal,Color(0xFFE6F9FF).copy(alpha=.75f))
                drawLine(Color.White.copy(alpha=.8f),c+Offset(0f,-r*1.2f),c+Offset(0f,r*1.2f),1.dp.toPx())
            }
        }
    }
}
