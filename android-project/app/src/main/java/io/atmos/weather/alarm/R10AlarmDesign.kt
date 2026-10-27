package io.atmos.weather.alarm

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import io.atmos.weather.ui.thinnSpecularEdge
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.R
import io.atmos.weather.ui.iosJellyPress
import java.time.LocalTime
import java.util.Locale

/** Independently authored Compose Foundation controls, not Material picker or switch widgets. */
private val night=Color(0xFF141416)
private val group=Color(0xFF1C1C1E)
private val secondary=Color(0xFF8E8E93)
private val amber=Color(0xFFFF9F0A)
private val weekdays=listOf("一","二","三","四","五","六","日")

object R10AlarmTones {
    const val prefix="thinn://alarm/"
    data class Choice(val id:String,val label:String,val description:String,val upper:Color,val lower:Color)
    val choices=listOf(
        Choice("citypop","城市晨光","City Pop · 電鋼琴與彈跳貝斯 · 104 BPM",Color(0xFFFDB6B7),Color(0xFF6059A6)),
        Choice("tropical","海風假期","Tropical Pop · 木琴與熱帶節拍 · 110 BPM",Color(0xFF7CDBD3),Color(0xFF3575A8)),
        Choice("indiepop","晴空出走","Indie Pop · 吉他與輕快鼓組 · 116 BPM",Color(0xFFF6D28F),Color(0xFFBF6869)),
        Choice("rnb","雨後慢拍","Neo Soul · 慵懶律動與七和弦 · 86 BPM",Color(0xFFB9ACE6),Color(0xFF514A88)),
        Choice("futurepop","極光派對","Future Pop · 電子鈴音與明亮副歌 · 120 BPM",Color(0xFF89CFF2),Color(0xFF8B60B0)),
        Choice("daybreak","日出情歌","Piano Pop · 鋼琴與溫暖和聲 · 92 BPM",Color(0xFFF6CBA3),Color(0xFF9A798C)),
        Choice("guitar","晨光吉他","木吉他撥弦 · 94 BPM",Color(0xFFFFD7A0),Color(0xFF9D6454)),
        Choice("flute","竹林笛語","竹笛三拍 · 88 BPM",Color(0xFF9CD8B7),Color(0xFF347B75)),
        Choice("rhodes","雨窗爵士","電鋼琴與刷鼓 · 76 BPM",Color(0xFFB2C4E7),Color(0xFF4E618F)),
        Choice("kalimba","雨滴拇指琴","交錯六拍 · 102 BPM",Color(0xFF9ADDDC),Color(0xFF357D9E)),
        Choice("strings","山嵐弦樂","弦樂三拍 · 82 BPM",Color(0xFFC9B8DD),Color(0xFF76618D)),
        Choice("musicbox","星露八音盒","晶亮八音盒 · 108 BPM",Color(0xFFECC1DC),Color(0xFF91649F)),
        Choice("synth","極光合成","空氣合成器 · 84 BPM",Color(0xFF9BDCD5),Color(0xFF536FC1)),
        Choice("groove","晴日律動","撥奏與輕鼓 · 118 BPM",Color(0xFFF6C18B),Color(0xFFC16670)),
        Choice("dawn","晨光序曲","鋼琴旋律 · 14 種天氣編曲",Color(0xFFEF7AB0),Color(0xFF9D428F)),
        Choice("rain","雨光節拍","柔鋼琴與明確節拍 · 天氣自適應",Color(0xFF90EFF3),Color(0xFF258FC1)),
        Choice("wood","森間晨行","木琴與撥弦 · 天氣自適應",Color(0xFF74D1B9),Color(0xFF286E69)),
        Choice("water","水漣初醒","豎琴與水紋旋律 · 天氣自適應",Color(0xFF99C1F4),Color(0xFF516BBB)),
        Choice("fire","晴日躍動","溫暖撥弦與輕節奏 · 天氣自適應",Color(0xFFFFB877),Color(0xFFD16B68)),
        Choice("metal","霜光鈴奏","柔亮鐘琴與旋律回應 · 天氣自適應",Color(0xFFC9B4F8),Color(0xFF6C5AA8))
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
        val snowy=reliable?.condition in setOf("SNOW","SLEET")
        return when(id) {
            "citypop" -> if(snowy) R.raw.thinn_alarm_citypop_snow else if(rainy) R.raw.thinn_alarm_citypop_rain else if(clear) R.raw.thinn_alarm_citypop_clear else R.raw.thinn_alarm_citypop
            "tropical" -> if(snowy) R.raw.thinn_alarm_tropical_snow else if(rainy) R.raw.thinn_alarm_tropical_rain else if(clear) R.raw.thinn_alarm_tropical_clear else R.raw.thinn_alarm_tropical
            "indiepop" -> if(snowy) R.raw.thinn_alarm_indiepop_snow else if(rainy) R.raw.thinn_alarm_indiepop_rain else if(clear) R.raw.thinn_alarm_indiepop_clear else R.raw.thinn_alarm_indiepop
            "rnb" -> if(snowy) R.raw.thinn_alarm_rnb_snow else if(rainy) R.raw.thinn_alarm_rnb_rain else if(clear) R.raw.thinn_alarm_rnb_clear else R.raw.thinn_alarm_rnb
            "futurepop" -> if(snowy) R.raw.thinn_alarm_futurepop_snow else if(rainy) R.raw.thinn_alarm_futurepop_rain else if(clear) R.raw.thinn_alarm_futurepop_clear else R.raw.thinn_alarm_futurepop
            "daybreak" -> if(snowy) R.raw.thinn_alarm_daybreak_snow else if(rainy) R.raw.thinn_alarm_daybreak_rain else if(clear) R.raw.thinn_alarm_daybreak_clear else R.raw.thinn_alarm_daybreak
            "guitar" -> if(snowy) R.raw.thinn_alarm_guitar_snow else if(rainy) R.raw.thinn_alarm_guitar_rain else if(clear) R.raw.thinn_alarm_guitar_clear else R.raw.thinn_alarm_guitar
            "flute" -> if(snowy) R.raw.thinn_alarm_flute_snow else if(rainy) R.raw.thinn_alarm_flute_rain else if(clear) R.raw.thinn_alarm_flute_clear else R.raw.thinn_alarm_flute
            "rhodes" -> if(snowy) R.raw.thinn_alarm_rhodes_snow else if(rainy) R.raw.thinn_alarm_rhodes_rain else if(clear) R.raw.thinn_alarm_rhodes_clear else R.raw.thinn_alarm_rhodes
            "kalimba" -> if(snowy) R.raw.thinn_alarm_kalimba_snow else if(rainy) R.raw.thinn_alarm_kalimba_rain else if(clear) R.raw.thinn_alarm_kalimba_clear else R.raw.thinn_alarm_kalimba
            "strings" -> if(snowy) R.raw.thinn_alarm_strings_snow else if(rainy) R.raw.thinn_alarm_strings_rain else if(clear) R.raw.thinn_alarm_strings_clear else R.raw.thinn_alarm_strings
            "musicbox" -> if(snowy) R.raw.thinn_alarm_musicbox_snow else if(rainy) R.raw.thinn_alarm_musicbox_rain else if(clear) R.raw.thinn_alarm_musicbox_clear else R.raw.thinn_alarm_musicbox
            "synth" -> if(snowy) R.raw.thinn_alarm_synth_snow else if(rainy) R.raw.thinn_alarm_synth_rain else if(clear) R.raw.thinn_alarm_synth_clear else R.raw.thinn_alarm_synth
            "groove" -> if(snowy) R.raw.thinn_alarm_groove_snow else if(rainy) R.raw.thinn_alarm_groove_rain else if(clear) R.raw.thinn_alarm_groove_clear else R.raw.thinn_alarm_groove
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
    io.atmos.weather.ui.GlassSwitch(checked=checked,onCheckedChange=onCheckedChange,
        colors=androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor=Color(0xFF34C759),
            uncheckedTrackColor=Color(0xFF39393D)))
}

@Composable
private fun GlassSetting(label:String, detail:String?=null,click:(()->Unit)?=null,
    detailColor:Color=secondary,chevron:Boolean=false,trailing:(@Composable ()->Unit)?=null) {
    val presses=remember {MutableInteractionSource()}
    val held by presses.collectIsPressedAsState()
    val background by androidx.compose.animation.animateColorAsState(
        if(held)Color(0xFF3A3A3C) else Color.Transparent,
        tween(if(held)80 else 250),label="alarm-cell-press")
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).background(background)
        .then(if(click!=null)Modifier.clickable(interactionSource=presses,indication=null,onClick=click) else Modifier)
        .padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=Color.White,fontSize=17.sp,modifier=Modifier.weight(1f).padding(vertical=12.dp))
        if(trailing!=null)trailing()
        else if(detail!=null) {
            Text(detail,color=detailColor,fontSize=17.sp,modifier=Modifier.widthIn(max=170.dp),maxLines=2)
            if(chevron) {Spacer(Modifier.width(8.dp));io.atmos.weather.ui.SettingsChevron()}
        }
    }
}
@Composable
private fun Line(){Box(Modifier.fillMaxWidth().padding(start=18.dp).height(.5.dp).background(Color.White.copy(alpha=.10f)))}

/** Shared date/time/duration cylinders; callers retain their 24-hour business values. */
@Composable fun AppleAlarmTimeSelector(hour:Int,minute:Int,onHour:(Int)->Unit,onMinute:(Int)->Unit,modifier:Modifier=Modifier){
 io.atmos.weather.ui.IOSClockDrums(hour,minute,onHour,onMinute,modifier)
}

@Composable
fun R10AlarmEditor(initial:WeatherAlarm?,onClose:()->Unit,onDelete:(WeatherAlarm)->Unit,onSave:(WeatherAlarm)->Unit) {
    val ctx=LocalContext.current
    var ttsOptions by remember { mutableStateOf(false) }
    var ttsLabel by remember { mutableStateOf(SamsungTtsSelector.label(ctx)) }
    var ttsPreview by remember { mutableStateOf(SamsungTtsSelector.lastStatus(ctx)) }
    val now=remember { LocalTime.now().plusMinutes(5) }
    var hour by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.hour?:now.hour) }
    var minute by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.minute?:now.minute) }
    var label by rememberSaveable(initial?.id) { mutableStateOf(initial?.title?:"鬧鐘") }
    var repeat by rememberSaveable(initial?.id) { mutableStateOf(initial?.days?:emptySet()) }
    var voice by rememberSaveable(initial?.id) { mutableStateOf(false) }
    var snooze by rememberSaveable(initial?.id) { mutableStateOf(initial?.snooze?:true) }
    var snoozeMinutes by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.snoozeMinutes?:9) }
    var chooseSnoozeMinutes by remember {mutableStateOf(false)}
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    var uri by rememberSaveable(initial?.id) { mutableStateOf(initial?.ringtoneUri?:R10AlarmTones.prefix+(if(initial==null)"citypop" else "dawn")) }
    var choosing by remember { mutableStateOf(false) }
    var sheetShown by remember {mutableStateOf(false)}
    var closingSheet by remember {mutableStateOf(false)}
    val editorScope=rememberCoroutineScope()
    val reduceEditorMotion=ctx.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    fun finishSheet(action:()->Unit) {
        if(closingSheet)return
        closingSheet=true;sheetShown=false
        editorScope.launch {if(!reduceEditorMotion)kotlinx.coroutines.delay(260);action()}
    }
    LaunchedEffect(Unit) { sheetShown=true }
    val hiddenSheetOffset=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp+64.dp
    val sheetOffset by animateDpAsState(if(sheetShown) 0.dp else hiddenSheetOffset,
        animationSpec=if(reduceEditorMotion)tween(0) else if(closingSheet)tween(260,easing=io.atmos.weather.ui.AppleMotion.snap)
            else spring(dampingRatio=io.atmos.weather.ui.AppleMotion.springDamping,
                stiffness=io.atmos.weather.ui.AppleMotion.springStiffness),label="ios27-alarm-sheet-entry")
    Dialog(onDismissRequest={finishSheet(onClose)},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.46f)),
            contentAlignment=Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).offset {IntOffset(0,sheetOffset.roundToPx())}
            .clip(RoundedCornerShape(topStart=32.dp,topEnd=32.dp))
            .background(night).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                io.atmos.weather.ui.IOS27RoundControl("×",onClick={finishSheet(onClose)},
                    fill=Color(0xFF2C2C2E),foreground=Color.White)
                Text(if(initial==null)"新增鬧鐘" else "編輯鬧鐘",color=Color.White,fontSize=18.sp,
                    textAlign=TextAlign.Center,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                io.atmos.weather.ui.IOS27RoundControl("✓",onClick={
                    val id=initial?.id ?: generateSequence(1) {it+1}.first {candidate ->
                        WeatherAlarmStore.load(ctx).none {it.id==candidate}
                    }
                    val saved=WeatherAlarm(id,hour,minute,label.take(30),repeat,initial?.enabled ?: true,voice,snooze,uri,
                        WeatherAlarmTime.validSnoozeMinutes(snoozeMinutes))
                    finishSheet {onSave(saved)}
                },fill=Color(0xFFFF9F0A),foreground=Color.White)
            }
            AppleAlarmTimeSelector(hour=hour,minute=minute,
                onHour={hour=it},onMinute={minute=it},
                modifier=Modifier.padding(horizontal=20.dp))
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(group)) {
                    GlassSetting("重複",detail=if(repeat.isEmpty())"不重複" else "每週 ${repeat.sorted().joinToString("") { weekdays[it-1] }}")
                    Line()
                    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),
                        horizontalArrangement=Arrangement.SpaceEvenly) {
                        listOf(7,1,2,3,4,5,6).forEach { day ->
                            val active=day in repeat
                            AlarmWeekday(day,active) {repeat=if(active) repeat-day else repeat+day}
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(group)) {
                    GlassSetting("標籤",trailing={
                        Box(Modifier.width(170.dp).heightIn(min=48.dp),contentAlignment=Alignment.CenterEnd) {
                            if(label.isEmpty())Text("鬧鐘",color=secondary,fontSize=17.sp)
                            BasicTextField(value=label,onValueChange={label=it.take(30)},singleLine=true,
                                textStyle=androidx.compose.ui.text.TextStyle(color=secondary,fontSize=17.sp,textAlign=TextAlign.End),
                                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={focusManager.clearFocus()}),
                                modifier=Modifier.fillMaxWidth().semantics {contentDescription="鬧鐘標籤"})
                        }
                    });Line()
                    GlassSetting("鈴聲",detail=(R10AlarmTones.choices.firstOrNull {R10AlarmTones.prefix+it.id==uri}?.label?:"系統鈴聲"),chevron=true,click={choosing=true});Line()
                    GlassSetting("延遲響鬧",trailing={R10Switch(snooze){snooze=it}})
                    Line()
                    GlassSetting("延遲響鬧時長",detail="$snoozeMinutes 分鐘",
                        detailColor=if(snooze)amber else secondary,click={if(snooze)chooseSnoozeMinutes=true})
                }
                if(initial!=null) {
                    Spacer(Modifier.height(10.dp))
                    Text("刪除此鬧鐘",color=Color(0xFFFF7D82),fontSize=17.sp,textAlign=TextAlign.Center,
                        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(group)
                            .clickable {finishSheet {onDelete(initial)}}.padding(17.dp))
                }
            }
        }
        }
    }
    if(chooseSnoozeMinutes){
        var draft by remember{mutableIntStateOf(snoozeMinutes)}
        io.atmos.weather.ui.IOSFormSheet("延遲響鬧時長",onDismiss={chooseSnoozeMinutes=false},onDone={snoozeMinutes=draft;chooseSnoozeMinutes=false},accent=amber){
            io.atmos.weather.ui.IOSDurationDrums(draft,{draft=it},1,30)
        }
    }
    if(ttsOptions) Dialog(onDismissRequest={ttsOptions=false}) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF24252B),night)))
            .border(1.dp,Color.White.copy(alpha=.28f),RoundedCornerShape(24.dp))
            .padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("鬧鐘播報引擎",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White)
            Spacer(Modifier.height(8.dp))
            Text("每支手機分開設定。Samsung TTS 需要手機已安裝繁中離線聲線；Bixby 非公開 TTS。",
                fontSize=14.sp,color=secondary)
            SamsungTtsSelector.choices(ctx).forEach { item ->
                Text((if(SamsungTtsSelector.selected(ctx)==item.id)"●  " else "○  ")+item.title,
                    color=Color.White,fontSize=15.sp,
                    modifier=Modifier.fillMaxWidth().clickable {
                        SamsungTtsSelector.setSelected(ctx,item.id)
                        ttsLabel=SamsungTtsSelector.label(ctx)
                        ttsPreview="已選擇，請按試聽"
                    }.padding(vertical=12.dp))
            }
            Text("試聽所選引擎  ▷",fontSize=16.sp,color=amber,modifier=Modifier
                .fillMaxWidth().clickable {
                    ttsPreview="正在試聽…"
                    SamsungTtsSelector.preview(ctx) { result -> ttsPreview=result }
                }.padding(vertical=14.dp))
            Text(ttsPreview,color=secondary,fontSize=14.sp)
            Spacer(Modifier.height(8.dp))
            Text("完成",fontSize=16.sp,color=amber,modifier=Modifier.align(Alignment.End)
                .clickable {ttsOptions=false}.padding(12.dp))
        }
    }
    if(choosing) R10RingtonePicker(selected=uri,onChoose={uri=it;choosing=false},onClose={choosing=false},voiceSettings={
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(group)) {
            GlassSetting("智慧天氣語音",trailing={R10Switch(voice){voice=it}})
            if(voice) {
                Line()
                GlassSetting("播報聲線／引擎",detail=ttsLabel,chevron=true,click={choosing=false;ttsOptions=true})
                Text("上次語音狀態："+SamsungTtsSelector.lastStatus(ctx),color=secondary,fontSize=14.sp,
                    modifier=Modifier.padding(horizontal=18.dp,vertical=10.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    })
}

/** Preview through Android's ALARM volume stream, not the media-volume stream. */
private fun prepareAlarmPreview(context:Context,uri:Uri):MediaPlayer? {
    val player=MediaPlayer()
    return try {
        player.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        player.setDataSource(context,uri)
        player.prepare()
        player
    } catch(_ : Exception) {
        player.release()
        null
    }
}

@Composable
private fun R10RingtonePicker(selected:String?,onChoose:(String)->Unit,onClose:()->Unit,voiceSettings:@Composable ()->Unit={}) {
    val ctx=androidx.compose.ui.platform.LocalContext.current
    var active by remember { mutableStateOf(selected?:R10AlarmTones.prefix+"dawn") }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    var weatherPreview by remember {mutableStateOf<io.atmos.weather.next.NextWeatherAlarmPlayer?>(null)}
    DisposableEffect(Unit) {onDispose {preview?.release();weatherPreview?.close()}}
    val localAudioPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if(picked!=null) {
            val access=Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { ctx.contentResolver.takePersistableUriPermission(picked,access) }
            preview?.release(); preview=null;weatherPreview?.close();weatherPreview=null
            active=picked.toString()
            preview=runCatching { prepareAlarmPreview(ctx,picked)?.apply {
                setOnCompletionListener {player->player.release();if(preview===player)preview=null}
                start()
            } }.getOrNull()
        }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(night).statusBarsPadding().navigationBarsPadding().padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth().height(68.dp),verticalAlignment=Alignment.CenterVertically) {
                io.atmos.weather.ui.IOS27RoundControl("×",onClose,fill=Color(0xFF2C2C2E),foreground=Color.White)
                Text("鬧鐘鈴聲",color=Color.White,fontSize=19.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
                io.atmos.weather.ui.IOS27RoundControl("✓",onClick={preview?.release();preview=null;weatherPreview?.close();onChoose(active)},fill=amber,foreground=Color.White)
            }
            // Weather speech remains removed; the rebuilt suite uses melody and nature.
            Text("天氣流行音樂館",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("新增 6 首原創流行短曲，共 20 款風格。主歌、旋律、副歌與天氣聲景分層編曲。點選試聽，按右上角勾號套用。",
                color=secondary,fontSize=14.sp,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                R10AlarmTones.choices.chunked(2).forEach {row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        row.forEach {choice->
                            val chosen=active==R10AlarmTones.prefix+choice.id
                            Column(Modifier.weight(1f).heightIn(min=184.dp).iosJellyPress().clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(listOf(choice.upper,choice.lower)))
                                .border(if(chosen) 2.dp else 1.dp,if(chosen) Color.White else Color.White.copy(alpha=.18f),RoundedCornerShape(22.dp))
                                .clickable {
                                    active=R10AlarmTones.prefix+choice.id
                                    preview?.release();preview=null;weatherPreview?.close()
                                    weatherPreview=io.atmos.weather.next.NextWeatherAlarmPlayer.start(ctx,active,WeatherAlarmSpeechCache.load(ctx),preview=true)
                                    val res=R10AlarmTones.resource(active,WeatherAlarmSpeechCache.load(ctx),System.currentTimeMillis())
                                    preview=if(weatherPreview==null && res!=null) runCatching {prepareAlarmPreview(ctx,Uri.parse("android.resource://${ctx.packageName}/$res"))?.apply {
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
            "guitar","indiepop" -> {
                drawOval(Color.White.copy(alpha=.30f),c-Offset(r*.68f,r*.45f),androidx.compose.ui.geometry.Size(r*1.36f,r*1.35f))
                drawCircle(Color.White.copy(alpha=.74f),r*.21f,c+Offset(0f,r*.15f))
                for(i in -2..2)drawLine(Color.White.copy(alpha=.76f),c+Offset(i*r*.08f,-r),c+Offset(i*r*.08f,r*.75f),.7.dp.toPx())
            }
            "flute","kalimba" -> {
                for(i in 0..5){val x=c.x-r*.8f+i*r*.32f;val h=r*(.85f+(i%3)*.24f)
                    drawLine(Color.White.copy(alpha=.80f),Offset(x,c.y-h/2),Offset(x,c.y+h/2),if(id=="flute")5.dp.toPx() else 2.8.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)}
            }
            "rhodes","rnb","daybreak" -> {
                for(i in 0..6){val x=c.x-r+i*r*.29f
                    drawRoundRect(Color.White.copy(alpha=.84f),Offset(x,c.y-r*.54f),androidx.compose.ui.geometry.Size(r*.24f,r*1.2f),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                    if(i%3!=0)drawRect(Color(0xFF45597E),Offset(x-r*.045f,c.y-r*.54f),androidx.compose.ui.geometry.Size(r*.15f,r*.65f))}
            }
            "strings","synth" -> {
                for(i in 0..4){val path=Path();for(j in 0..40){val x=c.x-r+j*r/20;val y=c.y+(i-2)*r*.30f+kotlin.math.sin(j*.16f+i*.5f)*r*.28f
                    if(j==0)path.moveTo(x,y) else path.lineTo(x,y)}
                    drawPath(path,Color.White.copy(alpha=.38f+i*.11f),style=Stroke(1.6.dp.toPx()))}
            }
            "musicbox" -> {
                for(i in 0..7){val a=i*Math.PI/4;val point=c+Offset(kotlin.math.cos(a).toFloat()*r,kotlin.math.sin(a).toFloat()*r)
                    drawLine(Color.White.copy(alpha=.80f),c,point,1.6.dp.toPx());drawCircle(Color.White.copy(alpha=.80f),r*.095f,point)}
                drawCircle(Color.White.copy(alpha=.22f),r*.65f,c)
            }
            "citypop" -> {
                drawCircle(Color(0xFFFFF0B7).copy(alpha=.75f),r*.65f,c+Offset(r*.35f,-r*.30f))
                for(i in 0..6){val h=r*(.45f+((i*3)%4)*.22f);val x=c.x-r+i*r*.30f
                    drawRect(Color.White.copy(alpha=.82f),Offset(x,c.y+r*.85f-h),androidx.compose.ui.geometry.Size(r*.22f,h))}
            }
            "tropical" -> {
                drawCircle(Color(0xFFFFF1BE).copy(alpha=.75f),r*.55f,c+Offset(r*.6f,-r*.4f))
                drawLine(Color.White.copy(alpha=.9f),c+Offset(-r*.15f,r*.9f),c+Offset(-r*.4f,-r*.2f),3.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)
                for(i in 0..4){val a=(-2.4+i*.65);val end=c+Offset(-r*.4f+kotlin.math.cos(a).toFloat()*r*.9f,-r*.2f+kotlin.math.sin(a).toFloat()*r*.7f)
                    drawLine(Color.White.copy(alpha=.88f),c+Offset(-r*.4f,-r*.2f),end,4.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)}
                drawLine(Color.White.copy(alpha=.5f),c+Offset(-r,r*.95f),c+Offset(r,r*.95f),1.dp.toPx())
            }
            "futurepop" -> {
                drawCircle(Color.White.copy(alpha=.18f),r*1.1f,c)
                drawCircle(Color.White.copy(alpha=.75f),r*.65f,c,style=Stroke(2.dp.toPx()))
                for(i in 0..3){val a=i*Math.PI/2+.4;val e=c+Offset(kotlin.math.cos(a).toFloat()*r,kotlin.math.sin(a).toFloat()*r)
                    drawLine(Color.White.copy(alpha=.8f),c,e,3.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)}
            }
            "groove" -> {
                for(i in 0..7){val h=r*(.45f+((i*7)%5)*.30f);val x=c.x-r+i*r*.29f
                    drawLine(Color.White.copy(alpha=.83f),Offset(x,c.y-h/2),Offset(x,c.y+h/2),4.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)}
            }
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

@Composable
private fun AlarmWeekday(day:Int,selected:Boolean,onClick:()->Unit) {
    val presses=remember {MutableInteractionSource()}
    val pressed by presses.collectIsPressedAsState()
    val reduce=LocalContext.current.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val scale by animateFloatAsState(if(pressed && !reduce) .96f else 1f,
        animationSpec=if(reduce)tween(0) else spring(.72f,190f),label="repeat-day-press")
    Box(Modifier.size(40.dp).graphicsLayer {scaleX=scale;scaleY=scale}
        .clip(CircleShape).background(if(selected) Color(0xFFFF9F0A) else Color(0xFF3A3A3C))
        .thinnSpecularEdge(20.dp)
        .semantics {contentDescription="星期"+weekdays[day-1];stateDescription=if(selected)"已選取" else "未選取"}
        .clickable(interactionSource=presses,indication=null,onClick=onClick),contentAlignment=Alignment.Center) {
        Text(weekdays[day-1],color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Medium)
    }
}
