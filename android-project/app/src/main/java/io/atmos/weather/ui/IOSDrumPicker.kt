package io.atmos.weather.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
internal val LocalDrumEnabled=compositionLocalOf {true}
private val drumRow @Composable get()=maxOf(38f,LocalDensity.current.fontScale*27f).dp
@Composable
fun IOSDrumColumn(value:Int,count:Int,onValue:(Int)->Unit,modifier:Modifier=Modifier,label:String="",wrap:Boolean=true,display:(Int)->String={"%02d".format(it)}) {
 require(count>0)
 val row=drumRow;val px=with(LocalDensity.current){row.toPx()};val haptic=LocalHapticFeedback.current
 val scope=rememberCoroutineScope();val reduced=rememberAppleReduceMotion();val enabled=LocalDrumEnabled.current
 var selected by remember(count){mutableIntStateOf(value.coerceIn(0,count-1))}
 var sent by remember(count){mutableIntStateOf(selected)};var offset by remember(count){mutableFloatStateOf(0f)}
 var motion by remember {mutableStateOf<Job?>(null)};val report by rememberUpdatedState(onValue)
 fun choose(v:Int){val next=v.coerceIn(0,count-1);if(next!=selected){selected=next;sent=next;report(next);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}
 fun move(delta:Float){val next=DrumWheelRules.move(selected,offset,delta/px,count,wrap);offset=next.offset;choose(next.value)}
 fun detent(delta:Int):Boolean{if(!enabled)return false;motion?.cancel();offset=0f;choose(if(wrap)Math.floorMod(selected+delta,count) else (selected+delta).coerceIn(0,count-1));return true}
 DisposableEffect(count){onDispose{motion?.cancel()}}
 LaunchedEffect(value,count){if(value!=sent){motion?.cancel();selected=value.coerceIn(0,count-1);sent=selected;offset=0f}}
 LaunchedEffect(enabled){if(!enabled){motion?.cancel();offset=0f}}
 Box(modifier.height(row*5.6f).clip(RoundedCornerShape(18.dp)).semantics(mergeDescendants=true){
  contentDescription=label;stateDescription=display(selected)
  customActions=listOf(CustomAccessibilityAction("增加"){detent(1)},CustomAccessibilityAction("減少"){detent(-1)})
  progressBarRangeInfo=ProgressBarRangeInfo(selected.toFloat(),0f..(count-1).toFloat(),(count-2).coerceAtLeast(0))
  setProgress{v->if(enabled){motion?.cancel();offset=0f;choose(v.roundToInt());true}else false}
 }.onPreviewKeyEvent{e->if(e.type!=KeyEventType.KeyDown)false else when(e.key){Key.DirectionUp->detent(1);Key.DirectionDown->detent(-1);else->false}}.focusable()
 .pointerInput(count,px,wrap,enabled,reduced){if(!enabled)return@pointerInput;var tracker=VelocityTracker()
  detectVerticalDragGestures(onDragStart={motion?.cancel();tracker=VelocityTracker()},onVerticalDrag={c,dy->c.consume();tracker.addPosition(c.uptimeMillis,c.position);move(dy)},onDragEnd={
   val velocity=tracker.calculateVelocity().y.coerceIn(-px*36,px*36)
   motion=scope.launch{
    if(!reduced&&abs(velocity)>px*1.4f){var before=0f;AnimationState(0f,initialVelocity=velocity).animateDecay(exponentialDecay(frictionMultiplier=3.5f)){move(this.value-before);before=this.value;if(!wrap&&abs(offset)>.54f)cancelAnimation()}}
    if(!reduced)Animatable(offset).animateTo(0f,spring(.82f,420f)){offset=this.value};offset=0f
   }
  },onDragCancel={motion=scope.launch{if(!reduced)Animatable(offset).animateTo(0f,spring(.86f,420f)){offset=this.value};offset=0f}})
 },contentAlignment=Alignment.Center){
  Layout(content={for(i in -3..3){val candidate=selected+i;val valid=wrap||candidate in 0 until count;val item=if(wrap)Math.floorMod(candidate,count) else candidate;val d=i+offset
   Box(Modifier.fillMaxWidth().height(row).graphicsLayer{rotationX=(-d*20f).coerceIn(-62f,62f);cameraDistance=12.dp.toPx();scaleX=(1-abs(d)*.06f).coerceAtLeast(.78f);alpha=(1-abs(d)*.35f).coerceIn(.025f,1f)}
    .then(if(valid&&enabled)Modifier.clickable{motion?.cancel();offset=0f;choose(item)} else Modifier),contentAlignment=Alignment.Center){if(valid)Text(display(item),color=Color.White,fontSize=22.sp,maxLines=1,style=TextStyle(fontFeatureSettings="tnum"))}
  }},modifier=Modifier.fillMaxSize()){items,c->val h=row.roundToPx();val cells=items.map{it.measure(Constraints.fixed(c.maxWidth,h))};layout(c.maxWidth,c.maxHeight){val center=(c.maxHeight-h)/2;cells.forEachIndexed{i,p->p.placeRelative(0,center+(i-3)*h+(offset*h).roundToInt())}}}
 }
}
@Composable fun IOSWheelRow(modifier:Modifier=Modifier,content:@Composable RowScope.()->Unit){
 val row=drumRow
 Box(modifier.fillMaxWidth().height(row*5.6f),contentAlignment=Alignment.Center){
  Box(Modifier.fillMaxWidth().height(row).clip(RoundedCornerShape(9999.dp)).thinnBackdrop(rememberAppleReduceTransparency(),9999.dp).background(Color(0xFF2C2C2E).copy(alpha=if(rememberAppleReduceTransparency())1f else .88f)).thinnSpecularEdge(9999.dp))
  Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,content=content)
 }
}
@Composable fun IOSClockDrums(hour:Int,minute:Int,onHour:(Int)->Unit,onMinute:(Int)->Unit,modifier:Modifier=Modifier){
 val h=hour.coerceIn(0,23)
 IOSWheelRow(modifier){Spacer(Modifier.weight(.3f));IOSDrumColumn(h/12,2,{onHour(h%12+it*12)},Modifier.weight(1.15f),"AM 上午或 PM 下午",false,display={if(it==0)"AM" else "PM"});IOSDrumColumn(h%12,12,{onHour(h/12*12+it)},Modifier.weight(.85f),"小時",display={if(it==0)"12" else "$it"});IOSDrumColumn(minute.coerceIn(0,59),60,onMinute,Modifier.weight(.85f),"分鐘");Spacer(Modifier.weight(.3f))}
}
@Composable fun IOSDateDrums(date:LocalDate,onDate:(LocalDate)->Unit){
 IOSWheelRow{IOSDrumColumn(date.year-1900,201,{onDate(DrumWheelRules.date(it+1900,date.monthValue,date.dayOfMonth))},Modifier.weight(1.3f),"年",false,display={"${it+1900}年"});IOSDrumColumn(date.monthValue-1,12,{onDate(DrumWheelRules.date(date.year,it+1,date.dayOfMonth))},Modifier.weight(1f),"月",display={"${it+1}月"});IOSDrumColumn(date.dayOfMonth-1,date.lengthOfMonth(),{onDate(date.withDayOfMonth(it+1))},Modifier.weight(1f),"日",display={"${it+1}日"})}
}
@Composable fun IOSDurationDrums(value:Int,onValue:(Int)->Unit,min:Int=1,max:Int=120,unit:String="分鐘"){
 IOSWheelRow{IOSDrumColumn((value-min).coerceIn(0,max-min),max-min+1,{onValue(it+min)},Modifier.weight(1f),unit,false,display={"${it+min} $unit"})}
}
