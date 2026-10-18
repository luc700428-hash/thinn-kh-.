package io.atmos.weather.alarm

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import java.util.Locale
import kotlin.math.roundToInt

/** User-created alarm, never a government warning / system lock screen spoof.
 * An ongoing foreground alarm service controls audio independently of this Activity.
 */
class WeatherAlarmRingActivity:ComponentActivity() {
    private var alarmId=-1
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(android.os.Build.VERSION.SDK_INT>=27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.statusBarColor=android.graphics.Color.BLACK
        window.navigationBarColor=android.graphics.Color.BLACK
        alarmId=intent.getIntExtra("alarm_id",-1)
        val info=WeatherAlarmStore.load(this).firstOrNull {it.id==alarmId}
        if(info==null){finish();return}
        setContent {
            val haptic=LocalHapticFeedback.current
            val reduceMotion=remember { getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
                .getBoolean("reduce_motion",false) }
            val orb=rememberInfiniteTransition(label="alarm dimmed glow")
            val glow by orb.animateFloat(initialValue=.09f,targetValue=.19f,
                animationSpec=infiniteRepeatable(tween(3400),RepeatMode.Reverse),label="soft optical light")
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxWidth().height(440.dp).align(Alignment.Center)
                    .graphicsLayer { alpha=if(reduceMotion) .10f else glow }
                    .background(Brush.radialGradient(listOf(Color(0xFFB26531),Color(0xFF0C1018),Color.Black))))
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal=28.dp,vertical=30.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.SpaceBetween) {
                    Spacer(Modifier.height(16.dp))
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("◴  鬧鐘",fontSize=20.sp,color=Color(0xFFB7BAC0))
                        Spacer(Modifier.height(18.dp))
                        Text(String.format(Locale.TAIWAN,"%02d:%02d",info.hour,info.minute),
                            fontSize=88.sp,color=Color.White,fontWeight=FontWeight.Light,
                            letterSpacing=(-3).sp,maxLines=1)
                        if(info.title.isNotBlank() && info.title!="鬧鐘") {
                            Spacer(Modifier.height(10.dp))
                            Text(info.title,color=Color(0xFFCACAD1),fontSize=17.sp,maxLines=1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                        if(info.snooze) {
                            Box(Modifier.fillMaxWidth().height(78.dp)
                                .clip(RoundedCornerShape(42.dp))
                                .background(Brush.verticalGradient(listOf(
                                    Color(0xFFFFB246),Color(0xFFFF932C),Color(0xFFFF9D2E))))
                                .border(1.dp,Color(0xFFFFDB93).copy(alpha=.68f),RoundedCornerShape(42.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    close(true)
                                },contentAlignment=Alignment.Center) {
                                Text("稍後提醒",fontSize=23.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
                            }
                        }
                        var trackWidth by remember { mutableIntStateOf(0) }
                        var dragging by remember { mutableFloatStateOf(0f) }
                        val density=LocalDensity.current
                        val maxDrag=(trackWidth-with(density){76.dp.toPx()}).coerceAtLeast(1f)
                        val offset by animateFloatAsState(dragging,
                            animationSpec=spring(dampingRatio=.76f,stiffness=375f),
                            label="stop thumb spring")
                        Box(Modifier.fillMaxWidth().height(78.dp)
                            .onSizeChanged {trackWidth=it.width}
                            .clip(RoundedCornerShape(44.dp))
                            .background(Brush.horizontalGradient(listOf(
                                Color(0xFF25262A),Color(0xFF18191C),Color(0xFF202127))))
                            .border(1.dp,Color.White.copy(alpha=.17f),RoundedCornerShape(44.dp))
                            .pointerInput(maxDrag) {
                                detectHorizontalDragGestures(
                                    onDragEnd={
                                        if(dragging >= maxDrag*.79f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            close(false)
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            dragging=0f
                                        }
                                    },onDragCancel={dragging=0f},
                                    onHorizontalDrag={change,dx->
                                        change.consume()
                                        dragging=(dragging+dx).coerceIn(0f,maxDrag)
                                    }
                                )
                            },contentAlignment=Alignment.Center) {
                            Text("滑動來停止  ›",fontSize=20.sp,color=Color(0xFFD0D0D2),
                                modifier=Modifier.padding(start=44.dp),textAlign=TextAlign.Center)
                            Box(Modifier.align(Alignment.CenterStart).padding(5.dp)
                                .offset {IntOffset(offset.roundToInt(),0)}
                                .size(68.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF55565A),Color(0xFF25262B))))
                                .border(1.dp,Color.White.copy(alpha=.42f),CircleShape),
                                contentAlignment=Alignment.Center) {
                                Text("■",fontSize=26.sp,color=Color.White)
                            }
                        }
                        // Accessible, explicit alternative to a drag gesture.
                        Text("無法滑動？輕觸這裡停止鬧鐘",fontSize=14.sp,
                            color=Color(0xFFA6A6AC),textAlign=TextAlign.Center,
                            modifier=Modifier.fillMaxWidth().clickable {close(false)}.padding(vertical=10.dp))
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
    private fun close(snooze:Boolean) {
        val action=if(snooze) WeatherAlarmAudioService.ACTION_SNOOZE
            else WeatherAlarmAudioService.ACTION_STOP
        runCatching {
            startService(android.content.Intent(this,WeatherAlarmAudioService::class.java)
                .setAction(action).putExtra("alarm_id",alarmId))
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(740000+alarmId)
        finish()
    }
    @Deprecated("Use visible controls")
    override fun onBackPressed(){ close(false) }
}
