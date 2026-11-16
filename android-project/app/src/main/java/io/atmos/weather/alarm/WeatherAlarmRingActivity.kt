package io.atmos.weather.alarm

import io.atmos.weather.ui.brandColor

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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.CornerRadius
import io.atmos.weather.ui.thinnSpecularEdge
import io.atmos.weather.ui.AppleMotion
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.glassTouchTracker
import io.atmos.weather.ui.liquidGlassSurface
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
    private var closing = false

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
        val reduceMotion = getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
            .getBoolean("reduce_motion", false)

        setContent {
            var clock by remember {mutableStateOf(java.time.LocalTime.now())}
            LaunchedEffect(Unit) {
                while(true) {
                    kotlinx.coroutines.delay(60_000L-System.currentTimeMillis()%60_000L)
                    clock=java.time.LocalTime.now()
                }
            }
            val displayHour=if(DateFormat.is24HourFormat(this@WeatherAlarmRingActivity)) clock.hour
                else (clock.hour%12).let {if(it==0)12 else it}
            val timeText=String.format(Locale.TAIWAN,"%02d:%02d",displayHour,clock.minute)
            // AndroidX handles both gesture navigation and physical Back keys.
            androidx.activity.compose.BackHandler(enabled=true) { /* Keep explicit stop/snooze controls. */ }
            val reading = remember { WeatherAlarmSpeechCache.load(this@WeatherAlarmRingActivity) }
            AlarmFullScreen(
                timeText = timeText,
                label = info.title.ifBlank {"鬧鐘"},
                allowSnooze = info.snooze,
                reduceMotion = reduceMotion,
                onSnooze = { close(true) },
                onStop = { close(false) },
                weatherLine = alarmWeatherLine(reading)
            )
        }
    }

    private fun close(snooze: Boolean) {
        if(closing) return
        closing=true
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

}

@Composable
internal fun AlarmFullScreen(
    timeText: String,
    label: String,
    allowSnooze: Boolean,
    reduceMotion: Boolean,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    weatherLine: String? = null
) {
    io.atmos.weather.ui.DevicePostureProvider { io.atmos.weather.ui.FoldSafeContent {
    val haptics = LocalHapticFeedback.current
    // iOS 27 entrance: the clock settles in on a soft spring, the controls rise after it.
    val enter = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = .74f, stiffness = 170f)) }
    // Ambient light breathing behind the clock, like the lock-screen glow of a ringing alarm.
    val breath = if (reduceMotion) .5f else {
        val b by rememberInfiniteTransition(label = "鬧鐘光暈").animateFloat(0f, 1f,
            infiniteRepeatable(tween(3600, easing = AppleMotion.fluid), androidx.compose.animation.core.RepeatMode.Reverse), label = "呼吸")
        b
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * .30f)
            val radius = size.minDimension * (.70f + .08f * breath)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9F0A).copy(alpha = .10f + .10f * breath * enter.value),
                Color(0xFFFF6A00).copy(alpha = .04f * enter.value), Color.Transparent), center, radius), radius, center)
            val low = Offset(size.width / 2f, size.height * 1.05f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5AC8FA).copy(alpha = .06f + .04f * (1f - breath)), Color.Transparent), low, size.width),
                size.width, low)
        }
        val compact = maxHeight < 620.dp
        val shortWide = maxWidth >= 600.dp && maxHeight < 420.dp
        val titleTop = maxHeight * (if (compact) .12f else .20f)
        val controlsBottom = maxHeight * (if (compact) .045f else .085f)
        // The new reference is proportional: large clock and deep capsules.
        // Compact landscape keeps the previous usable 68dp controls.
        val density=LocalDensity.current
        val rawClockSize=if(compact)92.sp else (maxWidth.value*.35f).coerceIn(92f,144f).sp
        val clockSize=rawClockSize/density.fontScale
        val labelSize=(if(compact)18f else (maxWidth.value*.06f).coerceIn(18f,24f)).sp
        val buttonHeight=if(compact)68.dp else (maxWidth*.24f).coerceIn(68.dp,96.dp)
        val trackHeight=buttonHeight
        val thumbSize=trackHeight-12.dp
        val controlsGap=if(compact)18.dp else (maxWidth*.06f).coerceIn(18.dp,24.dp)
        val clockIconSize=if(compact)23.dp else (maxWidth*.075f).coerceIn(23.dp,30.dp)

        // Pure-black background and uncluttered clock face, matching the reference hierarchy.
        Column(
            (if(shortWide)Modifier.align(Alignment.CenterStart).fillMaxWidth(.44f).padding(horizontal=16.dp)
                else Modifier.align(Alignment.TopCenter).widthIn(max=600.dp).fillMaxWidth(.90f).offset(y = titleTop))
                .graphicsLayer {
                    val e = enter.value
                    alpha = e.coerceIn(0f, 1f); val sc = .9f + .1f * e; scaleX = sc; scaleY = sc
                    translationY = (1f - e) * 28.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AlarmOutlineIcon(Modifier.size(clockIconSize))
                io.atmos.weather.ui.AppText(label, fontSize = labelSize,maxLines=2,
                    color = Color(0xFFA1A1A6), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            io.atmos.weather.ui.AppText(
                timeText,
                fontSize = clockSize,
                letterSpacing = (-3).sp,
                style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"),
                color = Color.White,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            if (weatherLine != null) {
                Spacer(Modifier.height(6.dp))
                io.atmos.weather.ui.AppText(weatherLine, fontSize = labelSize * .85f, color = Color(0xFFD1D1D6),
                    maxLines = 2, textAlign = TextAlign.Center)
            }
        }

        Column(
            (if(shortWide)Modifier.align(Alignment.CenterEnd).width((maxWidth*.52f).coerceAtMost(584.dp)).padding(end=24.dp)
                else Modifier.align(Alignment.BottomCenter).widthIn(max=560.dp).fillMaxWidth(.86f).padding(bottom = controlsBottom)),
            verticalArrangement = Arrangement.spacedBy(controlsGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val glassLight = io.atmos.weather.ui.GlassLight.current(follow = true)
            if (allowSnooze) {
                val presses = remember { MutableInteractionSource() }
                val pressed by presses.collectIsPressedAsState()
                val touch = remember { io.atmos.weather.ui.GlassTouch() }
                // iOS interactive glass: squash on contact, rebound with a little overshoot on release.
                val scale by animateFloatAsState(
                    targetValue = if (pressed) .955f else 1f,
                    animationSpec = if (reduceMotion) snap() else if (pressed) spring(.9f, 1400f) else spring(.52f, 520f),
                    label = "snooze press"
                )
                val glow by animateFloatAsState(if (pressed && !reduceMotion) 1f else 0f,
                    if (reduceMotion) snap() else spring(.7f, 600f), label = "snooze glow")
                Box(
                    Modifier.fillMaxWidth().height(buttonHeight).iosAppear(1)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Brush.verticalGradient(listOf(
                            Color(0xFFFF9500), Color(0xFFFF9500))))
                        .border(.7.dp, Color(0xFFFFC876).copy(alpha = .62f), RoundedCornerShape(9999.dp))
                        .glassTouchTracker(touch)
                        .liquidGlassSurface(9999.dp, { glow }, touch, true, glassLight)
                        .clickable(interactionSource = presses, indication = null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSnooze()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    io.atmos.weather.ui.AppText("稍後提醒", color = Color.White,
                        fontSize = (buttonHeight.value*.25f).coerceIn(20f,24f).sp, fontWeight = FontWeight.SemiBold)
                }
            }

            var trackWidth by remember { mutableIntStateOf(0) }
            val insetPx = with(density) { 6.dp.toPx() }
            val thumbPx = with(density) { thumbSize.toPx() }
            val maxDrag = (trackWidth - thumbPx - insetPx * 2f).coerceAtLeast(1f)
            var dragPx by remember { mutableFloatStateOf(0f) }
            var fingerDown by remember { mutableStateOf(false) }
            var thresholdReached by remember { mutableStateOf(false) }
            // Finger movement is unfiltered; the spring runs only when an incomplete drag is released.
            // Released early, the thumb springs home with a small iOS-style bounce.
            val offset by animateFloatAsState(
                targetValue = dragPx,
                animationSpec = if (fingerDown || reduceMotion) snap()
                    else spring(dampingRatio = .58f, stiffness = 360f),
                label = "stop thumb return"
            )
            // The thumb stretches a little along the track while it is moving fast.
            var lastDrag by remember { mutableFloatStateOf(0f) }
            val stretch by animateFloatAsState(if (fingerDown && !reduceMotion) (kotlin.math.abs(lastDrag) / 60f).coerceIn(0f, .18f) else 0f,
                if (reduceMotion) snap() else spring(.6f, 700f), label = "thumb stretch")
            val latestOffset by rememberUpdatedState(offset)
            val progress = (offset / maxDrag).coerceIn(0f, 1f)
            val shimmer=if(reduceMotion) .5f else {
                val phase by rememberInfiniteTransition(label="停止文字微光").animateFloat(-.5f,1.5f,
                    infiniteRepeatable(tween(2200,easing=LinearEasing)),label="停止微光位置")
                phase
            }
            Box(
                Modifier.fillMaxWidth().height(trackHeight).iosAppear(2)
                    .onSizeChanged { trackWidth = it.width }
                    .clip(RoundedCornerShape(9999.dp))
                    .background(brandColor(0xFF1B1D1C))
                    .border(.7.dp, Color.White.copy(alpha = .075f), RoundedCornerShape(9999.dp))
                    .thinnSpecularEdge(trackHeight/2)
                    .semantics {
                        // Accessibility stop alternative without a visually separate Android-style button.
                        onClick(label = "無法滑動？輕觸這裡停止鬧鐘") {
                            onStop(); true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Light fills the track behind the thumb as it travels.
                Canvas(Modifier.matchParentSize()) {
                    val reach = 6.dp.toPx() + offset + thumbPx / 2f
                    if (progress > .001f) drawRoundRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .02f),
                        Color.White.copy(alpha = .10f + .16f * progress)), 0f, reach),
                        size = Size(reach + thumbPx / 2f, size.height), cornerRadius = CornerRadius(size.height / 2f))
                }
                io.atmos.weather.ui.AppText(
                    "滑動來停止",
                    fontSize = 20.sp,
                    style=androidx.compose.ui.text.TextStyle(brush=Brush.linearGradient(
                        listOf(Color(0xFFA2A2A7),Color.White.copy(alpha=.85f),Color(0xFFA2A2A7)),
                        start=Offset(trackWidth*(shimmer-.3f),0f),end=Offset(trackWidth*(shimmer+.3f),0f))),
                    modifier = Modifier.graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) },
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Box(
                    Modifier.align(Alignment.CenterStart).padding(6.dp)
                        .offset { IntOffset(offset.roundToInt(), 0) }
                        .graphicsLayer { scaleX = 1f + stretch; scaleY = 1f - stretch * .35f
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, .5f) }
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(
                            Color(0xFF3B3935), brandColor(0xFF232524))))
                        .border(.8.dp, Color.White.copy(alpha = .24f), CircleShape)
                        .thinnSpecularEdge(thumbSize/2)
                    .pointerInput(maxDrag) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragPx=latestOffset
                                fingerDown = true
                                thresholdReached = false
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                lastDrag = amount
                                dragPx = (dragPx + amount).coerceIn(0f, maxDrag)
                                if (!thresholdReached && dragPx >= maxDrag * .75f) {
                                    thresholdReached = true
                                    fingerDown = false
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStop() // Action is idempotent; service owns real stop.
                                }
                            },
                            onDragEnd = {
                                lastDrag = 0f
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
                    Canvas(Modifier.size(thumbSize*.32f)) {
                        drawRoundRect(Color.White,cornerRadius=CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
    }
    }}
}

@Composable
private fun AlarmOutlineIcon(modifier:Modifier=Modifier.size(23.dp)) {
    Canvas(modifier) {
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

/** One calm line of today's weather under the alarm clock, only from fresh cached data. */
internal fun alarmWeatherLine(reading: AlarmWeatherReading?, now: Long = System.currentTimeMillis()): String? {
    val r = reading?.takeIf { it.fetchedEpochMs > 0 && now - it.fetchedEpochMs in 0L..WeatherAlarmNarration.MAX_WEATHER_AGE_MS } ?: return null
    val condition = when (r.condition) {
        "CLEAR", "SUNNY", "MOSTLY_CLEAR" -> "晴"; "PARTLY_CLOUDY", "FAIR" -> "多雲時晴"; "CLOUDY" -> "多雲"
        "RAIN", "DRIZZLE", "HEAVY_RAIN" -> "下雨"; "THUNDERSTORM" -> "雷雨"; "FOG" -> "有霧"; "SNOW" -> "下雪"; else -> null
    }
    val range = if (r.lowC != null && r.highC != null) " · ${r.lowC.roundToInt()}–${r.highC.roundToInt()}°" else ""
    val rain = r.hours.mapNotNull { it.rainChancePct }.maxOrNull()?.takeIf { it >= 40 }?.let { " · 記得帶傘" }.orEmpty()
    return listOfNotNull(r.city.takeIf { it.isNotBlank() }, "${r.temperatureC.roundToInt()}°" + (condition?.let { " $it" } ?: "")).joinToString(" · ") + range + rain
}
