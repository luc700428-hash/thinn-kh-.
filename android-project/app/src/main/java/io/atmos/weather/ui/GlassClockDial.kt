package io.atmos.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import kotlin.math.roundToInt

/** iOS-inspired wheel picker with explicit accessible increment/decrement controls.
 * onHour/onMinute remain the source of truth; no hidden state or inaccessible Canvas.
 */
@Composable
fun GlassClockDial(hour:Int,minute:Int,onHour:(Int)->Unit,onMinute:(Int)->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text("設定時間 · 24 小時制",fontSize=15.sp,color=ThinnDesignTokens.secondaryInkDark)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically) {
            ThinnNumberWheel("小時",hour.coerceIn(0,23),0,23,{onHour(it)},Modifier.weight(1f))
            Text(":",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
            ThinnNumberWheel("分鐘",minute.coerceIn(0,59),0,59,{onMinute(it)},Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text("上下滑動數字，或點選箭頭精確調整",fontSize=14.sp,color=ThinnDesignTokens.secondaryInkDark)
    }
}

@Composable
fun ThinnNumberWheel(label:String,value:Int,min:Int,max:Int,onChange:(Int)->Unit,modifier:Modifier=Modifier) {
    require(max>=min)
    val haptics=LocalHapticEngine.current
    val range=max-min+1
    fun next(delta:Int) {
        val updated=min+Math.floorMod(value-min+delta,range)
        if(updated!=value){onChange(updated);haptics?.play(HapticEngine.Event.RELEASE)}
    }
    var dragY by remember {mutableFloatStateOf(0f)}
    val wheelModifier=modifier.padding(horizontal=6.dp)
        .clip(RoundedCornerShape(17.dp))
        .background(Color.White.copy(alpha=.08f))
        .pointerInput(value,min,max) {
            detectVerticalDragGestures(onVerticalDrag={change,dy->
                dragY+=dy
                val steps=(dragY/32.dp.toPx()).toInt()
                if(steps!=0){next(-steps);dragY-=steps*32.dp.toPx()}
                change.consume()
            },onDragEnd={dragY=0f},onDragCancel={dragY=0f})
        }
    Column(wheelModifier,horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label,fontSize=14.sp,color=ThinnDesignTokens.secondaryInkDark,
            modifier=Modifier.padding(top=11.dp,bottom=3.dp))
        Box(Modifier.fillMaxWidth().height(33.dp)
            .clickable(role=Role.Button,onClick={next(-1)})
            .semantics { contentDescription="$label 減一" },contentAlignment=Alignment.Center) {
            Icon(Icons.Rounded.KeyboardArrowUp,null,tint=Color.White.copy(alpha=.85f))
        }
        Text("%02d".format(Math.floorMod(value-min-1,range)+min),fontSize=21.sp,
            color=Color.White.copy(alpha=.42f),modifier=Modifier.padding(vertical=2.dp))
        Box(Modifier.fillMaxWidth().height(47.dp).clip(RoundedCornerShape(11.dp))
            .background(ThinnDesignTokens.accent.copy(alpha=.19f)),contentAlignment=Alignment.Center) {
            Text("%02d".format(value),fontSize=28.sp,color=Color.White,fontWeight=FontWeight.Bold)
        }
        Text("%02d".format(Math.floorMod(value-min+1,range)+min),fontSize=21.sp,
            color=Color.White.copy(alpha=.42f),modifier=Modifier.padding(vertical=2.dp))
        Box(Modifier.fillMaxWidth().height(33.dp)
            .clickable(role=Role.Button,onClick={next(1)})
            .semantics { contentDescription="$label 加一" },contentAlignment=Alignment.Center) {
            Icon(Icons.Rounded.KeyboardArrowDown,null,tint=Color.White.copy(alpha=.85f))
        }
    }
}
