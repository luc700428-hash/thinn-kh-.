package io.atmos.weather.alarm

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Opened by the alarm notification. No claim to bypass device lock screen or Do Not Disturb. */
class WeatherAlarmRingActivity:ComponentActivity() {
    private var alarmId=-1
    private var alarm:WeatherAlarm?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // Android platform controls whether fullscreen alarm launch is permitted.
        if(android.os.Build.VERSION.SDK_INT>=27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        alarmId=intent.getIntExtra("alarm_id",-1)
        alarm=WeatherAlarmStore.load(this).firstOrNull {it.id==alarmId}
        val info=alarm
        if(info==null){finish();return}
        // A foreground service owns the ringtone, vibration and weather TTS.
        // The alarm must keep ringing even when a locked device does not launch this UI.
        setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF080C12)).statusBarsPadding()
                .navigationBarsPadding().padding(26.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween) {
                Spacer(Modifier.height(48.dp))
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("鬧鐘",fontSize=22.sp,color=Color(0xFFDDEAF3))
                    Text(String.format(Locale.TAIWAN,"%02d:%02d",info.hour,info.minute),
                        fontSize=82.sp,color=Color.White,fontWeight=FontWeight.Light)
                    Text(info.title,color=Color.White,fontSize=24.sp)
                    Text(if(info.weatherVoice)"起床天氣語音" else "日常提醒",color=Color(0xFFAFE5FC),fontSize=18.sp)
                }
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(15.dp)) {
                    if(info.snooze) Box(Modifier.fillMaxWidth().height(76.dp)
                        .clip(RoundedCornerShape(38.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(
                            Color(0xFFFFC254),Color(0xFFFF982F))))
                        .border(1.dp,Color(0xFFFFD785),RoundedCornerShape(38.dp))
                        .clickable {close(true)},contentAlignment=Alignment.Center) {
                        Text("稍後提醒",fontSize=23.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
                    }
                    // Horizontal slide gesture, with spring-back if not completed.
                    // Separate button below preserves access for TalkBack / switch access.
                    val haptic=LocalHapticFeedback.current
                    var trackWidth by remember { mutableIntStateOf(0) }
                    var dragging by remember { mutableFloatStateOf(0f) }
                    val density=LocalDensity.current
                    val maxDrag=(trackWidth-with(density){80.dp.toPx()}).coerceAtLeast(1f)
                    val offset by animateFloatAsState(dragging,animationSpec=spring(
                        dampingRatio=.76f, stiffness=430f),label="alarm swipe stop")
                    Box(Modifier.fillMaxWidth().height(76.dp)
                        .onSizeChanged {trackWidth=it.width}
                        .background(Color.White.copy(alpha=.10f),RoundedCornerShape(40.dp))
                        .border(1.dp,Color.White.copy(alpha=.25f),RoundedCornerShape(40.dp))
                        .pointerInput(maxDrag) {
                            detectHorizontalDragGestures(
                                onDragEnd={
                                    if(dragging >= maxDrag*.72f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        close(false)
                                    } else dragging=0f
                                },
                                onDragCancel={ dragging=0f },
                                onHorizontalDrag={change,dx->
                                    change.consume()
                                    dragging=(dragging+dx).coerceIn(0f,maxDrag)
                                }
                            )
                        }, contentAlignment=Alignment.Center) {
                        Text("滑動停止鬧鐘  →",color=Color(0xFFDDEAF3),fontSize=18.sp)
                        Box(Modifier.align(Alignment.CenterStart)
                            .padding(5.dp).offset {IntOffset(offset.roundToInt(),0)}
                            .size(66.dp).background(Color(0xFF30353B),CircleShape)
                            .border(1.dp,Color(0xFFDAE7F1).copy(alpha=.38f),CircleShape),
                            contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Stop,contentDescription="向右滑動停止",tint=Color.White,
                                modifier=Modifier.size(26.dp))
                        }
                    }
                    Text("無法滑動？點此停止鬧鐘",color=Color(0xFFD1DFEA),fontSize=15.sp,
                        modifier=Modifier.fillMaxWidth().clickable {close(false)}.padding(16.dp),
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    private fun close(snooze:Boolean) {
        val action = if (snooze) WeatherAlarmAudioService.ACTION_SNOOZE
            else WeatherAlarmAudioService.ACTION_STOP
        runCatching {
            startService(android.content.Intent(this,WeatherAlarmAudioService::class.java)
                .setAction(action).putExtra("alarm_id",alarmId))
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(740000+alarmId)
        finish()
    }
    override fun onDestroy() { super.onDestroy() }
    @Deprecated("Use the on-screen stop action")
    override fun onBackPressed(){close(false)}
}
