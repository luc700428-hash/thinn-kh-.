package io.atmos.weather.briefing
import io.atmos.weather.ui.appleBouncingScroll

import io.atmos.weather.ui.GlassModalBottomSheet as ModalBottomSheet
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassTextButton as TextButton
import io.atmos.weather.ui.GlassSwitch as Switch
import io.atmos.weather.ui.GlassClockDial
import io.atmos.weather.ui.GlassTextButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.domain.model.WeatherBriefingPlan
import io.atmos.weather.domain.model.WeatherBriefingSlot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ink:Color @Composable get()=MaterialTheme.colorScheme.onSurface
private val muted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
private val azure:Color @Composable get()=MaterialTheme.colorScheme.primary
private val weekNames=listOf("一","二","三","四","五","六","日")
private fun dayNames(mask:Int):String = if(mask==127)"每天" else weekNames.indices.filter{mask and (1 shl it)!=0}.joinToString("、"){"週${weekNames[it]}"}

/** Neutral optical presentation with a shared time cylinder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherBriefingSettingsPage(onClose:()->Unit) {
    val context=LocalContext.current
    var slots by remember { mutableStateOf(WeatherBriefingStore.load(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WeatherBriefingSlot?>(null) }
    fun change(next:List<WeatherBriefingSlot>) { WeatherBriefingStore.save(context,next);slots=next }
    ModalBottomSheet(onDismissRequest=onClose,containerColor=MaterialTheme.colorScheme.background,contentColor=ink) {
        Column(Modifier.fillMaxWidth().heightIn(max=750.dp).appleBouncingScroll().verticalScroll(rememberScrollState())
            .padding(horizontal=22.dp).padding(bottom=36.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            io.atmos.weather.ui.IOSSettingsHeader("天氣定時通知",onClose)
            Text("最多設定 5 組星期與時分。每次只分析從這次提醒到下一次提醒間的天氣，提供降雨、穿搭、溫差及外出建議。",
                fontSize=14.sp,color=muted)
            Text("使用上下滾輪選擇時間。背景通知可能因 Android 省電策略延遲，不能保證精確分鐘。",
                fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            slots.sortedWith(compareBy({it.hour},{it.minute},{it.id})).forEach { slot ->
                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.09f),RoundedCornerShape(20.dp))
                    .border(1.dp,Color.White.copy(alpha=.13f),RoundedCornerShape(20.dp)).padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccessTime,null,tint=azure)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("%02d:%02d".format(Locale.US,slot.hour,slot.minute),fontSize=25.sp,color=ink,fontWeight=FontWeight.Bold)
                            Text(dayNames(slot.weekdays),fontSize=14.sp,color=muted)
                        }
                        Switch(checked=slot.enabled,onCheckedChange={on ->change(slots.map{if(it.id==slot.id)it.copy(enabled=on) else it})})
                    }
                    if(slot.enabled) {
                        val next=WeatherBriefingPlan.upcoming(listOf(slot),Instant.now(),ZoneId.systemDefault())
                        Text("下次預計：${next?.time?.format(DateTimeFormatter.ofPattern("MM/dd 週E HH:mm",Locale.TAIWAN)) ?: "未排程"}",
                            fontSize=14.sp,color=azure)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={editing=slot;showAdd=true}) {Text("編輯時間",color=ink,fontSize=14.sp)}
                        OutlinedButton(onClick={change(slots.filterNot{it.id==slot.id})}) {Text("刪除",color=ink,fontSize=14.sp)}
                    }
                }
            }
            Button(onClick={editing=null;showAdd=true},modifier=Modifier.fillMaxWidth(),enabled=slots.size<WeatherBriefingPlan.MAX_SLOTS) {
                Text(if(slots.size<5)"＋ 新增定時通知（${slots.size}/5）" else "已達 5 組通知上限")
            }
            Text("通知會使用你選擇的 8 款 thinn-khì 專屬音效，並嘗試相容裝置的焦點通知；無法保證顯示在超級島。",
                color=muted,fontSize=14.sp)
            Text("資料來源為 Open-Meteo 預報。若下一次提醒超出目前預報範圍，會明確標示資料尚未涵蓋，不會虛構。",
                color=muted,fontSize=14.sp)
        }
    }
    if(showAdd) {
        var selectedHour by remember(editing) { mutableIntStateOf(editing?.hour ?: 8) }
        var selectedMinute by remember(editing) { mutableIntStateOf(editing?.minute ?: 0) }
        var days by remember(editing) { mutableIntStateOf(editing?.weekdays ?: 127) }
        var error by remember { mutableStateOf("") }
        io.atmos.weather.ui.IOSFormSheet(if(editing==null)"新增天氣提醒" else "編輯天氣提醒",onDismiss={showAdd=false},validate={
            error=when{days==0->"請至少選一個星期";slots.any{it.id!=editing?.id&&it.hour==selectedHour&&it.minute==selectedMinute&&it.weekdays and days!=0}->"相同時間的星期不可重複，請調整設定";else->""};error.isBlank()
        },onDone={
            val next=if(editing!=null)slots.map{if(it.id==editing?.id)it.copy(hour=selectedHour,minute=selectedMinute,weekdays=days) else it}
                else slots+WeatherBriefingSlot((1..5).first{used->slots.none{it.id==used}},selectedHour,selectedMinute,days)
            change(next);showAdd=false
        }){
            io.atmos.weather.ui.IOSClockDrums(selectedHour,selectedMinute,{selectedHour=it},{selectedMinute=it})
            Text("重複",color=ink,fontSize=17.sp)
            io.atmos.weather.ui.GlassWeekdayChoices(weekNames,weekNames.indices.filter{days and (1 shl it)!=0}.toSet()){index->days=days xor (1 shl index)}
            if(error.isNotBlank())Text(error,color=Color(0xFFFF9F91),fontSize=14.sp)
        }

    }
}
