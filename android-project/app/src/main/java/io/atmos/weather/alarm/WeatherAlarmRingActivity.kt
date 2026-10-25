package io.atmos.weather.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import kotlin.math.roundToInt

/** Independently drawn clock-inspired alarm UI. Not an iOS system screen.
 * The actual ringtone, alarm stream volume and speech stay in WeatherAlarmAudioService.
 * The full-screen activity only handles display and the user's stop/snooze actions.
 */
class WeatherAlarmRingActivity : ComponentActivity() {
    private var alarmId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Physical volume keys must adjust the OS alarm stream, never media volume.
        setVolumeControlStream(AudioManager.STREAM_ALARM)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The reference screen is intentionally immersive, with only the gesture bar visible.
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())

        alarmId = intent.getIntExtra("alarm_id", -1)
        val info = WeatherAlarmStore.load(this).firstOrNull { it.id == alarmId }
        if (info == null) { finish(); return }
        val displayHour = if (DateFormat.is24HourFormat(this)) info.hour
            else (info.hour % 12).let { if (it == 0) 12 else it }
        val timeText = String.format(Locale.TAIWAN, "%02d:%02d", displayHour, info.minute)
        val reduceMotion = getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
            .getBoolean("reduce_motion", false)

        setContent {
            AlarmFullScreen(
                timeText = timeText,
                allowSnooze = info.snooze,
                reduceMotion = reduceMotion,
                onSnooze = { close(true) },
                onStop = { close(false) }
            )
        }
    }

    private fun close(snooze: Boolean) {
        val action = if (snooze) WeatherAlarmAudioService.ACTION_SNOOZE
            else WeatherAlarmAudioService.ACTION_STOP
        runCatching {
            startService(android.content.Intent(this, WeatherAlarmAudioService::class.java)
                .setAction(action).putExtra("alarm_id", alarmId))
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(740000 + alarmId)
        finish()
    }

    // Back should not accidentally dismiss a ringing alarm. The slider, accessible
    // action and notification STOP action remain explicit ways to silence it.
    @Deprecated("Use visible controls")
    override fun onBackPressed() { /* Intentionally do nothing while ringing. */ }
}

@Composable
private fun AlarmFullScreen(
    timeText: String,
    allowSnooze: Boolean,
    reduceMotion: Boolean,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()
    ) {
        val compact = maxHeight < 620.dp
        val titleTop = maxHeight * (if (compact) .12f else .20f)
        val controlsBottom = maxHeight * (if (compact) .045f else .085f)
        val buttonHeight = 68.dp
        val trackHeight = 68.dp

        // Pure-black background and uncluttered clock face, matching the reference hierarchy.
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = titleTop),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AlarmOutlineIcon()
                androidx.compose.material3.Text("鬧鐘", fontSize = 18.sp,
                    color = Color(0xFF99999F), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(22.dp))
            androidx.compose.material3.Text(
                timeText,
                fontSize = 88.sp,
                letterSpacing = (-3).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 28.dp).padding(bottom = controlsBottom),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (allowSnooze) {
                val presses = remember { MutableInteractionSource() }
                val pressed by presses.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) .966f else 1f,
                    animationSpec = if (reduceMotion) snap() else tween(105),
                    label = "snooze press"
                )
                Box(
                    Modifier.fillMaxWidth().height(buttonHeight)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(44.dp))
                        .background(Brush.verticalGradient(listOf(
                            Color(0xFFFFA338), Color(0xFFFF922A), Color(0xFFFF9731))))
                        .border(.7.dp, Color(0xFFFFC876).copy(alpha = .62f), RoundedCornerShape(44.dp))
                        .clickable(interactionSource = presses, indication = null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSnooze()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text("稍後提醒", color = Color.White,
                        fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            var trackWidth by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            val insetPx = with(density) { 5.dp.toPx() }
            val thumbPx = with(density) { (trackHeight - 10.dp).toPx() }
            val maxDrag = (trackWidth - thumbPx - insetPx * 2f).coerceAtLeast(1f)
            var dragPx by remember { mutableFloatStateOf(0f) }
            var fingerDown by remember { mutableStateOf(false) }
            var thresholdReached by remember { mutableStateOf(false) }
            // Finger movement is unfiltered; the spring runs only when an incomplete drag is released.
            val offset by animateFloatAsState(
                targetValue = dragPx,
                animationSpec = if (fingerDown || reduceMotion) snap()
                    else spring(dampingRatio = .68f, stiffness = 280f),
                label = "stop thumb return"
            )
            val progress = (dragPx / maxDrag).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(trackHeight)
                    .onSizeChanged { trackWidth = it.width }
                    .clip(RoundedCornerShape(48.dp))
                    .background(Color(0xFF1F1F20))
                    .border(.7.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(48.dp))
                    .semantics {
                        // Accessibility stop alternative without a visually separate Android-style button.
                        onClick(label = "無法滑動？輕觸這裡停止鬧鐘") {
                            onStop(); true
                        }
                    }
                    .pointerInput(maxDrag) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                fingerDown = true
                                thresholdReached = false
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragPx = (dragPx + amount).coerceIn(0f, maxDrag)
                                if (!thresholdReached && dragPx >= maxDrag * .75f) {
                                    thresholdReached = true
                                    fingerDown = false
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStop() // Action is idempotent; service owns real stop.
                                }
                            },
                            onDragEnd = {
                                fingerDown = false
                                if (!thresholdReached) dragPx = 0f
                            },
                            onDragCancel = {
                                fingerDown = false
                                dragPx = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    "滑動來停止",
                    fontSize = 20.sp,
                    color = Color(0xFFA2A2A7),
                    modifier = Modifier.padding(start = 53.dp)
                        .graphicsLayer { alpha = (1f - progress * .78f).coerceIn(0f, 1f) },
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Box(
                    Modifier.align(Alignment.CenterStart).padding(5.dp)
                        .offset { IntOffset(offset.roundToInt(), 0) }
                        .size(trackHeight - 10.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(
                            Color(0xFF333438), Color(0xFF242528))))
                        .border(.8.dp, Color.White.copy(alpha = .24f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text("■", fontSize = 23.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AlarmOutlineIcon() {
    Canvas(Modifier.size(23.dp)) {
        val tint = Color(0xFF9B9BA0)
        val u = size.minDimension
        drawCircle(tint, radius = u * .33f,
            center = Offset(u * .50f, u * .52f),
            style = Stroke(width = u * .075f))
        drawLine(tint, Offset(u * .50f, u * .52f), Offset(u * .50f, u * .31f),
            strokeWidth = u * .075f, cap = StrokeCap.Round)
        drawLine(tint, Offset(u * .50f, u * .52f), Offset(u * .66f, u * .61f),
            strokeWidth = u * .075f, cap = StrokeCap.Round)
        drawArc(tint, 192f, 90f, false,
            topLeft = Offset(u * .01f, u * .00f),
            size = Size(u * .50f, u * .40f),
            style = Stroke(width = u * .085f, cap = StrokeCap.Round))
        drawArc(tint, 259f, 90f, false,
            topLeft = Offset(u * .49f, u * .00f),
            size = Size(u * .50f, u * .40f),
            style = Stroke(width = u * .085f, cap = StrokeCap.Round))
        drawLine(tint, Offset(u * .31f, u * .81f), Offset(u * .23f, u * .96f),
            strokeWidth = u * .075f, cap = StrokeCap.Round)
        drawLine(tint, Offset(u * .69f, u * .81f), Offset(u * .77f, u * .96f),
            strokeWidth = u * .075f, cap = StrokeCap.Round)
    }
}
