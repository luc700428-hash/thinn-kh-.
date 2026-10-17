package io.atmos.weather.task

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
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
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
        Column(Modifier.fillMaxSize()
            .background(io.atmos.weather.ui.ThinnOpticalMaterial.page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal=20.dp,vertical=32.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                GlassIconButton(onClick=onClose) {Icon(Icons.Rounded.ArrowBack,"返回",tint=Color.White)}
                Spacer(Modifier.width(10.dp))
                Text("我的事項",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                GlassIconButton(onClick={editing=null;editingOpen=true}) {
                    Icon(Icons.Rounded.Add,"新增事項",tint=ThinnDesignTokens.accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("本機私人清單 · 可安排日期、時間與提醒",color=ThinnDesignTokens.secondaryInkDark,fontSize=15.sp)
            Spacer(Modifier.height(17.dp))
            if(error.isNotBlank())Text(error,color=ThinnDesignTokens.critical)
            if(tasks.isEmpty()) {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Text("還沒有事項",fontSize=21.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                    Text("點選右上角的 ＋，新增你的第一個事項。",fontSize=16.sp,
                        color=ThinnDesignTokens.secondaryInkDark)
                    Spacer(Modifier.height(13.dp))
                    GlassButton(onClick={editing=null;editingOpen=true},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add,null,tint=Color.White)
                        Spacer(Modifier.width(6.dp));Text("新增事項",color=Color.White)
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
                                Text(task.title,color=if(task.completed) ThinnDesignTokens.secondaryInkDark else Color.White,
                                    fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                Text(task.displayDate(),color=ThinnDesignTokens.secondaryInkDark,fontSize=16.sp)
                                if(task.note.isNotBlank())Text(task.note,color=ThinnDesignTokens.secondaryInkDark,
                                    fontSize=14.sp,maxLines=2)
                            }
                            Icon(Icons.Rounded.Edit,"編輯 ${task.title}",tint=ThinnDesignTokens.accent,
                                modifier=Modifier.size(20.dp))
                        }
                        if(i<tasks.lastIndex) GlassDivider(color=Color.White.copy(alpha=.13f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("事項會儲存在這台手機。若開啟提醒，Android 可能因省電設定延遲通知；系統通知權限必須已開啟。",
                color=ThinnDesignTokens.secondaryInkDark,fontSize=14.sp,lineHeight=18.sp)
        }
    }
    if(editingOpen) TaskEditor(existing=editing,onDismiss={editingOpen=false},
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
private fun TaskEditor(existing:ThinnTask?,onDismiss:()->Unit,onSave:(ThinnTask)->Unit,onDelete:(()->Unit)?) {
    val today=remember{LocalDateTime.now().plusHours(1)}
    var name by remember(existing?.id){mutableStateOf(existing?.title?:"")}
    var note by remember(existing?.id){mutableStateOf(existing?.note?:"")}
    var year by remember(existing?.id){mutableIntStateOf(existing?.year?:today.year)}
    var month by remember(existing?.id){mutableIntStateOf(existing?.month?:today.monthValue)}
    var day by remember(existing?.id){mutableIntStateOf(existing?.day?:today.dayOfMonth)}
    var hour by remember(existing?.id){mutableIntStateOf(existing?.hour?:today.hour)}
    var minute by remember(existing?.id){mutableIntStateOf(existing?.minute?:today.minute)}
    var alert by remember(existing?.id){mutableStateOf(existing?.notify?:true)}
    var error by remember(existing?.id){mutableStateOf("")}
    fun daysInMonth()=LocalDate.of(year,month,1).lengthOfMonth()
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth(.95f).widthIn(max=470.dp).heightIn(max=760.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF36516D),Color(0xFF12283E))))
            .border(1.dp,Color.White.copy(alpha=.24f),RoundedCornerShape(28.dp))
            .verticalScroll(rememberScrollState()).padding(19.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(existing==null)"新增事項" else "編輯事項",color=Color.White,
                fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("事項名稱",color=ThinnDesignTokens.secondaryInkDark,fontSize=15.sp)
            GlassOutlinedTextField(name,{name=it.take(100)},label={Text("例如：帶雨傘、領包裹",color=Color.White.copy(alpha=.6f))})
            Text("備註（選填）",color=ThinnDesignTokens.secondaryInkDark,fontSize=15.sp)
            GlassOutlinedTextField(note,{note=it.take(500)},singleLine=false,
                label={Text("簡單記下重要資訊",color=Color.White.copy(alpha=.6f))})
            GlassDivider()
            Text("選擇日期",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                ThinnNumberWheel("年",year,2024,2100,{year=it;day=day.coerceAtMost(daysInMonth())},Modifier.weight(1.15f))
                ThinnNumberWheel("月",month,1,12,{month=it;day=day.coerceAtMost(daysInMonth())},Modifier.weight(1f))
                ThinnNumberWheel("日",day,1,daysInMonth(),{day=it},Modifier.weight(1f))
            }
            GlassDivider()
            GlassClockDial(hour,minute,onHour={hour=it},onMinute={minute=it})
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("提醒我（可能因省電限制延遲）",color=Color.White,fontSize=16.sp,
                    modifier=Modifier.weight(1f))
                GlassSwitch(alert,{alert=it})
            }
            if(error.isNotBlank())Text(error,color=ThinnDesignTokens.critical,fontSize=14.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                GlassOutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f)) {Text("取消",color=Color.White)}
                GlassButton(onClick={
                    error=when {
                        name.isBlank()->"請輸入事項名稱"
                        runCatching{LocalDate.of(year,month,day)}.isFailure->"日期不正確"
                        else->""
                    }
                    if(error.isEmpty())onSave(ThinnTask(id=existing?.id ?: java.util.UUID.randomUUID().toString(),
                        title=name.trim(),note=note.trim(),year=year,month=month,day=day,hour=hour,minute=minute,
                        completed=existing?.completed?:false,notify=alert))
                },modifier=Modifier.weight(1f)) {Text("儲存",color=Color.White)}
            }
            onDelete?.let{delete->
                GlassOutlinedButton(onClick=delete,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Delete,null,tint=ThinnDesignTokens.critical)
                    Spacer(Modifier.width(5.dp));Text("刪除事項",color=ThinnDesignTokens.critical)
                }
            }
        }
    }
}
