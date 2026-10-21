package io.atmos.weather.alarm

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** iOS-inspired fullscreen alarm presentation, drawn by Compose Foundation.
 * Android/HyperOS controls the actual lockscreen availability and vibration hardware.
 * The ringtone and weather briefing belong to WeatherAlarmAudioService, not this view.
 */
class WeatherAlarmRingActivity:ComponentActivity() {
    private var alarmId=-1
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(android.os.Build.VERSION.SDK_INT>=27) {setShowWhenLocked(true);setTurnScreenOn(true)}
        alarmId=intent.getIntExtra("alarm_id",-1)
        val info=WeatherAlarmStore.load(this).firstOrNull {it.id==alarmId}
        if(info==null){finish();return}
        window.statusBarColor=android.graphics.Color.BLACK
        window.navigationBarColor=android.graphics.Color.BLACK
        setContent {
            val haptic=LocalHapticFeedback.current
            Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal=30.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(.40f))
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("◴  鬧鐘",fontSize=21.sp,color=Color(0xFFAEB1B8))
                    Spacer(Modifier.height(10.dp))
                    Text(String.format(Locale.TAIWAN,"%02d:%02d",info.hour,info.minute),
                        fontSize=93.sp,color=Color.White,fontWeight=FontWeight.SemiBold,
                        letterSpacing=(-3).sp,maxLines=1)
                }
                Spacer(Modifier.weight(1.0f))
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(info.snooze) {
                        Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(48.dp))
                            .background(Color(0xFFFF962E))
                            .border(1.dp,Color(0xFFFFC57E),RoundedCornerShape(48.dp))
                            .clickable {haptic.performHapticFeedback(HapticFeedbackType.LongPress);close(true)},
                            contentAlignment=Alignment.Center) {
                            Text("稍後提醒",fontSize=25.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
                        }
                    }
                    // The control follows the finger and springs back unless released near the end.
                    var width by remember { mutableIntStateOf(0) }
                    var drag by remember { mutableFloatStateOf(0f) }
                    var thresholdReached by remember { mutableStateOf(false) }
                    val density=LocalDensity.current
                    val max=(width-with(density){78.dp.toPx()}).coerceAtLeast(1f)
                    val offset by animateFloatAsState(drag,animationSpec=spring(dampingRatio=.73f,stiffness=360f),label="slide to stop")
                    Box(Modifier.fillMaxWidth().height(85.dp)
                        .onSizeChanged {width=it.width}
                        .clip(RoundedCornerShape(45.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp,Color.White.copy(alpha=.13f),RoundedCornerShape(45.dp))
                        .pointerInput(max) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag={change,delta->
                                    change.consume()
                                    drag=(drag+delta).coerceIn(0f,max)
                                    if(drag>=max*.76f && !thresholdReached){
                                        thresholdReached=true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if(drag<max*.7f) thresholdReached=false
                                },
                                onDragEnd={if(drag>=max*.76f)close(false) else drag=0f},
                                onDragCancel={drag=0f}
                            )
                        }.semantics {contentDescription="滑動停止鬧鐘，向右拖曳"},contentAlignment=Alignment.Center) {
                        Text("滑動來停止",color=Color(0xFF8E8E93),fontSize=21.sp,
                            modifier=Modifier.padding(start=64.dp))
                        Box(Modifier.align(Alignment.CenterStart).padding(5.dp)
                            .offset {IntOffset(offset.roundToInt(),0)}
                            .size(75.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFF555153),Color(0xFF222224))))
                            .border(1.dp,Color(0xFFFFB463).copy(alpha=.65f),CircleShape),contentAlignment=Alignment.Center) {
                            Box(Modifier.size(23.dp).background(Color.White,RoundedCornerShape(5.dp)))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("無法滑動？點此停止鬧鐘",color=Color(0xFFA6A6AB),fontSize=14.sp,
                    modifier=Modifier.clickable {close(false)}.padding(16.dp),textAlign=TextAlign.Center)
                Spacer(Modifier.height(36.dp))
            }
        }
    }
    private fun close(snooze:Boolean) {
        val action=if(snooze) WeatherAlarmAudioService.ACTION_SNOOZE else WeatherAlarmAudioService.ACTION_STOP
        runCatching {startService(android.content.Intent(this,WeatherAlarmAudioService::class.java)
            .setAction(action).putExtra("alarm_id",alarmId))}
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(740000+alarmId)
        finish()
    }
    @Deprecated("Use the on-screen stop action")
    override fun onBackPressed(){close(false)}
}
