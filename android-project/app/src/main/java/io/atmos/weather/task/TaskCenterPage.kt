package io.atmos.weather.task
import io.atmos.weather.ui.appleBouncingScroll

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.ui.*
import java.time.LocalDate
import java.time.LocalDateTime

/** Real offline CRUD for personal items, with editable dates/times and optional inexact notification. */
@Composable
fun TaskCenterPage(onClose:()->Unit) {
    val context=LocalContext.current
    var tasks by remember {mutableStateOf(TaskStore.load(context))}
    var editing by remember {mutableStateOf<ThinnTask?>(null)}
    var editingOpen by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    fun persist(next:List<ThinnTask>) {
        runCatching{TaskStore.save(context,next)}
            .onSuccess{tasks=next;error=""}
            .onFailure{error="儲存失敗，請確認裝置空間是否足夠"}
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        AppSystemBars(MaterialTheme.colorScheme.background.red<.5f)
        Column(Modifier.fillMaxSize()
            .background(io.atmos.weather.ui.ThinnOpticalMaterial.page)
            .appleBouncingScroll().verticalScroll(rememberScrollState())
            .padding(horizontal=20.dp,vertical=32.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                GlassIconButton(onClick=onClose) {Icon(Icons.Rounded.ArrowBack,"返回",tint=MaterialTheme.colorScheme.onSurface)}
                Spacer(Modifier.width(10.dp))
                Text("我的事項",color=MaterialTheme.colorScheme.onSurface,fontSize=28.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                GlassIconButton(onClick={editing=null;editingOpen=true}) {
                    Icon(Icons.Rounded.Add,"新增事項",tint=ThinnDesignTokens.accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("本機私人清單 · 可安排日期、時間與提醒",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp)
            Spacer(Modifier.height(17.dp))
            if(error.isNotBlank())Text(error,color=ThinnDesignTokens.critical)
            if(tasks.isEmpty()) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Text("還沒有事項",fontSize=21.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                    Text("點選右上角的 ＋，新增你的第一個事項。",fontSize=16.sp,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(13.dp))
                    GlassButton(onClick={editing=null;editingOpen=true},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add,null,tint=MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp));Text("新增事項",color=MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                val pending=tasks.count{!it.completed}
                ThinnSection("待完成 · $pending") {
                    tasks.sortedWith(compareBy<ThinnTask> {it.completed}.thenBy {it.dueMillis()}).forEachIndexed {i,task->
                        Row(Modifier.fillMaxWidth().heightIn(min=76.dp)
                            .clickable{editing=task;editingOpen=true}.padding(13.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            GlassSwitch(task.completed,onCheckedChange={checked->
                                persist(tasks.map{if(it.id==task.id)it.copy(completed=checked)else it})
                            })
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title,color=if(task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                Text(task.displayDate(),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)
                                if(task.note.isNotBlank())Text(task.note,color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize=14.sp,maxLines=2)
                            }
                            Icon(Icons.Rounded.Edit,"編輯 ${task.title}",tint=ThinnDesignTokens.accent,
                                modifier=Modifier.size(20.dp))
                        }
                        if(i<tasks.lastIndex) GlassDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.13f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("事項會儲存在這台手機。若開啟提醒，Android 可能因省電設定延遲通知；系統通知權限必須已開啟。",
                color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=18.sp)
        }
    }
    if(editingOpen) TaskEditor(existing=editing,persistenceError=error,onDismiss={editingOpen=false},
        onSave={item->
            val updated=if(editing==null) tasks+item else tasks.map{if(it.id==editing?.id)item else it}
            persist(updated)
            if(error.isEmpty())editingOpen=false
        },onDelete=if(editing==null)null else {{
            persist(tasks.filterNot{it.id==editing?.id})
            if(error.isEmpty())editingOpen=false
        }})
}

@Composable
private fun TaskEditor(existing:ThinnTask?,persistenceError:String,onDismiss:()->Unit,onSave:(ThinnTask)->Unit,onDelete:(()->Unit)?) {
 val today=remember{LocalDateTime.now().plusHours(1)}
 var name by remember(existing?.id){mutableStateOf(existing?.title?:"")};var note by remember(existing?.id){mutableStateOf(existing?.note?:"")}
 var date by remember(existing?.id){mutableStateOf(existing?.let{LocalDate.of(it.year,it.month,it.day)}?:today.toLocalDate())}
 var hour by remember(existing?.id){mutableIntStateOf(existing?.hour?:today.hour)};var minute by remember(existing?.id){mutableIntStateOf(existing?.minute?:today.minute)}
 var alert by remember(existing?.id){mutableStateOf(existing?.notify?:true)};var error by remember{mutableStateOf("")}
 var dateOpen by remember{mutableStateOf(false)};var timeOpen by remember{mutableStateOf(false)};var pending by remember{mutableStateOf<ThinnTask?>(null)}
 io.atmos.weather.ui.IOSFormSheet(if(existing==null)"新增事項" else "編輯事項",onDismiss=onDismiss,validate={
  error=if(name.isBlank())"請輸入事項名稱" else ""
  if(error.isBlank())pending=ThinnTask(id=existing?.id?:java.util.UUID.randomUUID().toString(),title=name.trim(),note=note.trim(),year=date.year,month=date.monthValue,day=date.dayOfMonth,hour=hour,minute=minute,completed=existing?.completed?:false,notify=alert)
  error.isBlank()
 },onDone={pending?.let(onSave)}){
  Text("事項名稱",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
  GlassOutlinedTextField(name,{name=it.take(100)},label={Text("例如：帶雨傘、領包裹",color=MaterialTheme.colorScheme.onSurfaceVariant)})
  Text("備註（選填）",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
  GlassOutlinedTextField(note,{note=it.take(500)},singleLine=false,label={Text("簡單記下重要資訊",color=MaterialTheme.colorScheme.onSurfaceVariant)})
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)){
   TaskEditorRow("日期","${date.year}年${date.monthValue}月${date.dayOfMonth}日"){dateOpen=true}
   Box(Modifier.padding(start=16.dp).fillMaxWidth().height(.5.dp).background(MaterialTheme.colorScheme.outline))
   TaskEditorRow("時間",(if(hour<12)"上午 " else "下午 ")+"%d:%02d".format(if(hour%12==0)12 else hour%12,minute)){timeOpen=true}
   Box(Modifier.padding(start=16.dp).fillMaxWidth().height(.5.dp).background(MaterialTheme.colorScheme.outline))
   Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("提醒我",color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,modifier=Modifier.weight(1f));GlassSwitch(alert,{alert=it})}
  }
  Text("背景提醒可能因手機省電設定延遲。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
  val displayedError=error.ifBlank{persistenceError}
  if(displayedError.isNotBlank())Text(displayedError,color=ThinnDesignTokens.critical,fontSize=14.sp)
  onDelete?.let{delete->GlassOutlinedButton(onClick=delete,modifier=Modifier.fillMaxWidth()){Text("刪除事項",color=ThinnDesignTokens.critical)}}
 }
 if(dateOpen){var draft by remember{mutableStateOf(date)};io.atmos.weather.ui.IOSFormSheet("選擇日期",onDismiss={dateOpen=false},onDone={date=draft;dateOpen=false}){io.atmos.weather.ui.IOSDateDrums(draft,{draft=it})}}
 if(timeOpen){var h by remember{mutableIntStateOf(hour)};var m by remember{mutableIntStateOf(minute)};io.atmos.weather.ui.IOSFormSheet("選擇時間",onDismiss={timeOpen=false},onDone={hour=h;minute=m;timeOpen=false}){io.atmos.weather.ui.IOSClockDrums(h,m,{h=it},{m=it})}}
}
@Composable private fun TaskEditorRow(label:String,value:String,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable(onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,modifier=Modifier.weight(1f));Text(value,color=MaterialTheme.colorScheme.primary,fontSize=16.sp);Spacer(Modifier.width(9.dp));io.atmos.weather.ui.SettingsChevron()}
}
