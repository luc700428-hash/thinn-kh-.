package io.atmos.weather.next.features

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.next.alarm.NextAlarm
import io.atmos.weather.next.alarm.NextAlarmScheduler
import io.atmos.weather.next.alarm.NextAlarmStore
import io.atmos.weather.next.alarm.AlarmTimePolicy
import io.atmos.weather.next.design.*
import java.time.LocalTime

private fun displayTime(a:NextAlarm):Pair<String,String> {
    val hr=AlarmTimePolicy.hour12(a.hour)
    return "%d:%02d".format(hr,a.minute) to if(a.hour<12)"上午" else "下午"
}
@Composable
fun IosAlarmPage() {
    val ctx=LocalContext.current
    var items by remember {mutableStateOf(NextAlarmStore.all(ctx))}
    var editing by remember {mutableStateOf<NextAlarm?>(null)}
    var showEditor by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    var notificationGranted by remember {mutableStateOf(Build.VERSION.SDK_INT<33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)}
    val notifyLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted -> notificationGranted=granted
        message=if(granted)"通知已授權，請繼續檢查精確鬧鐘權限。" else "通知未授權，鬧鐘可能無法正常提醒。"
    }
    BackHandler(showEditor){showEditor=false}
    fun persist(updated:List<NextAlarm>) {
        NextAlarmStore.save(ctx,updated)
        items=NextAlarmStore.all(ctx)
        message=if(!NextAlarmScheduler.canExact(ctx))"尚未授權精確鬧鐘；請到系統設定允許後才能排程。"
            else "已儲存；請使用手機測試響鈴及鎖屏通知。"
    }
    if(showEditor) {
        IosAlarmEditor(editing,onClose={showEditor=false},onSave={alarm ->
            val next=items.filterNot{it.id==alarm.id}+alarm
            persist(next)
            if(alarm.enabled && !NextAlarmScheduler.schedule(ctx,alarm)) message="鬧鐘尚未成功排程；請檢查精確鬧鐘權限。"
            else if(!alarm.enabled)NextAlarmScheduler.cancel(ctx,alarm.id)
            showEditor=false
        },onDelete={id ->
            NextAlarmScheduler.cancel(ctx,id)
            persist(items.filterNot{it.id==id});showEditor=false
        })
        return
    }
    Column(Modifier.fillMaxSize().background(IosColor.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=19.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            IosText("編輯",17,color=IosColor.orange,modifier=Modifier.clickable {
                if(items.isNotEmpty()){editing=items.first();showEditor=true}
            })
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50))
                .background(IosColor.orange).clickable{
                    val time=LocalTime.now().plusMinutes(10)
                    editing=NextAlarm(NextAlarmStore.freshId(),time.hour,time.minute)
                    showEditor=true
                },contentAlignment=Alignment.Center) {IosText("+",29,color=Color.Black)}
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
            IosText("鬧鐘",37,FontWeight.Bold)
            Spacer(Modifier.height(27.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                IosText("睡眠｜起床",21,FontWeight.SemiBold)
                IosText("◑",25,color=IosColor.orange)
            }
            Spacer(Modifier.height(12.dp))
            IosText("睡眠排程尚未連結；請使用下方的一般鬧鐘。",14,color=IosColor.muted)
            Spacer(Modifier.height(22.dp))
            IosText("其他",22,FontWeight.SemiBold)
            if(items.isEmpty()) {
                Spacer(Modifier.height(35.dp))
                IosText("尚未設定鬧鐘",19,color=IosColor.secondary)
                Spacer(Modifier.height(11.dp))
                IosText("點一下右上角 + 建立新的鬧鐘。",15,color=IosColor.muted)
            } else {
                items.sortedWith(compareBy({it.hour},{it.minute})).forEach {alarm->
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().heightIn(min=85.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable{editing=alarm;showEditor=true}.padding(vertical=5.dp)) {
                            Row(verticalAlignment=Alignment.Bottom) {
                                val (time,ampm)=displayTime(alarm)
                                IosText(time,51,FontWeight.Light,if(alarm.enabled)Color.White else IosColor.muted)
                                Spacer(Modifier.width(5.dp))
                                IosText(ampm,17,color=if(alarm.enabled)Color.White else IosColor.muted,
                                    modifier=Modifier.padding(bottom=8.dp))
                            }
                            IosText(alarm.label.ifBlank{"鬧鐘"} + " · " + if(alarm.daysMask==0)"永不重複" else "每週重複",
                                14,color=IosColor.muted)
                        }
                        IosSwitch(alarm.enabled,{ enabled ->
                            val new=alarm.copy(enabled=enabled)
                            persist(items.map{if(it.id==alarm.id)new else it})
                            if(enabled && !NextAlarmScheduler.schedule(ctx,new))message="精確鬧鐘權限不足，尚未排程。"
                            if(!enabled)NextAlarmScheduler.cancel(ctx,alarm.id)
                        })
                    }
                    Spacer(Modifier.fillMaxWidth().height(.5.dp).background(IosColor.divider))
                }
            }
            Spacer(Modifier.height(25.dp))
            if(!notificationGranted && Build.VERSION.SDK_INT>=33) {
                IosText("需要允許鬧鐘通知",17,FontWeight.Medium,color=Color(0xFFFFCA81))
                Spacer(Modifier.height(9.dp))
                Box(Modifier.clip(RoundedCornerShape(11.dp)).background(IosColor.group)
                    .clickable{notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)}.padding(15.dp)) {
                    IosText("授權鬧鐘通知  ›",16,color=IosColor.orange)
                }
                Spacer(Modifier.height(15.dp))
            }
            if(!NextAlarmScheduler.canExact(ctx)) {
                IosText("需要精確鬧鐘權限",17,FontWeight.Medium,color=Color(0xFFFFCA81))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.clip(RoundedCornerShape(11.dp)).background(IosColor.group)
                    .clickable {
                        if(Build.VERSION.SDK_INT>=31)runCatching{
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${ctx.packageName}")))
                        }
                    }.padding(15.dp)) {IosText("前往系統授權  ›",16,color=IosColor.orange)}
                Spacer(Modifier.height(13.dp))
            }
            if(message.isNotEmpty())IosText(message,14,color=Color(0xFFFFCA81))
            Spacer(Modifier.height(16.dp))
            IosText("這是獨立測試版的 Android 原生鬧鐘。鎖屏響鈴、精確時間和省電情境尚需實機驗證；重要鬧鐘請保留原本的備援。",
                14,color=IosColor.muted)
        }
    }
}

@Composable
private fun IosAlarmEditor(alarm:NextAlarm?,onClose:()->Unit,onSave:(NextAlarm)->Unit,onDelete:(Int)->Unit) {
    val initial=alarm?:return
    var isPm by remember(initial.id){mutableStateOf(initial.hour>=12)}
    var hour by remember(initial.id){mutableIntStateOf(AlarmTimePolicy.hour12(initial.hour))}
    var minute by remember(initial.id){mutableIntStateOf(initial.minute)}
    var label by remember(initial.id){mutableStateOf(initial.label)}
    var daysMask by remember(initial.id){mutableIntStateOf(initial.daysMask)}
    var snooze by remember(initial.id){mutableStateOf(initial.snooze)}
    val hourItems=remember{(1..12).map{it.toString()}}
    val minuteItems=remember{(0..59).map{"%02d".format(it)}}
    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
        .verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.clickable{onClose()}.padding(8.dp)){IosText("取消",17,color=IosColor.orange)}
            Spacer(Modifier.weight(1f))
            IosText("編輯鬧鐘",18,FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clickable{
                val h24=AlarmTimePolicy.hour24(hour,isPm)
                onSave(initial.copy(hour=h24,minute=minute,label=label.trim().take(50),daysMask=daysMask,snooze=snooze,enabled=true))
            }.padding(8.dp)){IosText("完成",17,FontWeight.SemiBold,IosColor.orange)}
        }
        Spacer(Modifier.height(25.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.Center) {
            WheelPicker(listOf("上午","下午"),if(isPm)1 else 0,{isPm=it==1},Modifier.width(85.dp))
            WheelPicker(hourItems,hour-1,{hour=it+1},Modifier.width(93.dp))
            WheelPicker(minuteItems,minute,{minute=it},Modifier.width(93.dp))
        }
        Spacer(Modifier.height(27.dp))
        IosGroup {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically) {
                IosText("重複",16,modifier=Modifier.weight(1f))
                IosText(if(daysMask==0)"永不" else "每週指定日期",14,color=IosColor.muted)
            }
            IosSeparator()
            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                listOf("一","二","三","四","五","六","日").forEachIndexed {i,day->
                    val on=(daysMask and (1 shl i))!=0
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(50))
                        .background(if(on)IosColor.orange else Color(0xFF3A3A3D))
                        .clickable {daysMask=daysMask xor (1 shl i)},contentAlignment=Alignment.Center) {
                        IosText(day,15,FontWeight.Medium,if(on)Color.Black else Color.White)
                    }
                }
            }
            IosSeparator()
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically) {
                IosText("標籤",16,modifier=Modifier.width(80.dp))
                BasicTextField(label,onValueChange={label=it.take(50)},singleLine=true,
                    textStyle=TextStyle(color=Color.White,fontSize=16.sp),modifier=Modifier.weight(1f))
            }
            IosSeparator()
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically) {
                IosText("提示聲",16,modifier=Modifier.weight(1f))
                IosText("系統鬧鐘鈴聲",14,color=IosColor.muted)
            }
            IosSeparator()
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically) {
                IosText("稍後提醒",16,modifier=Modifier.weight(1f))
                IosSwitch(snooze,{snooze=it})
            }
        }
        Spacer(Modifier.height(20.dp))
        IosText("上下滑動三個時間欄位。AM／PM 欄位位置固定，不會與小時互換。",14,color=IosColor.muted)
        Spacer(Modifier.height(27.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(IosColor.group)
            .clickable{onDelete(initial.id)}.padding(15.dp),contentAlignment=Alignment.Center) {
            IosText("刪除鬧鐘",17,color=Color(0xFFFF453A))
        }
        Spacer(Modifier.height(28.dp))
    }
}
/** Fixed-column, swipe-driven wheel; the labels never change columns when AM/PM toggles. */
@Composable
private fun WheelPicker(values:List<String>,selected:Int,onChange:(Int)->Unit,modifier:Modifier=Modifier) {
    var totalDrag by remember {mutableFloatStateOf(0f)}
    Column(modifier.height(156.dp).pointerInput(values.size,selected) {
        detectVerticalDragGestures(onDragStart={totalDrag=0f},onVerticalDrag={change,drag->
            change.consume();totalDrag+=drag
        },onDragEnd={
            val steps=(kotlin.math.abs(totalDrag)/45f).toInt().coerceIn(1,8)
            if(kotlin.math.abs(totalDrag)>12f)onChange((selected+if(totalDrag<0)steps else -steps)
                .coerceIn(0,values.lastIndex))
            totalDrag=0f
        })
    },horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(43.dp).clickable{onChange((selected-1).coerceAtLeast(0))},contentAlignment=Alignment.Center) {
            IosText(values.getOrElse(selected-1){""},21,color=Color.White.copy(alpha=.29f))
        }
        Box(Modifier.fillMaxWidth().height(65.dp).clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF333336)),contentAlignment=Alignment.Center) {
            IosText(values[selected],27,FontWeight.Medium)
        }
        Box(Modifier.fillMaxWidth().height(43.dp).clickable{onChange((selected+1).coerceAtMost(values.lastIndex))},contentAlignment=Alignment.Center) {
            IosText(values.getOrElse(selected+1){""},21,color=Color.White.copy(alpha=.29f))
        }
    }
}
