package io.atmos.weather.earthquake

import io.atmos.weather.ui.GlassSurfaceBox as Surface
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassSwitch as Switch
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.R
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import java.util.Locale

private val navy = Color(0xFF071C34)
private val ice = Color(0xFF8AE9FF)
private val amber = Color(0xFFFFC577)

/** Original app-owned sounds. Never used as an unverified earthquake warning. */
object SeismicSound {
    private var active: MediaPlayer? = null
    @Synchronized fun stop() {
        active?.runCatching { if (isPlaying) stop(); release() }
        active = null
    }
    @Synchronized fun play(context: Context, kind: Kind) {
        stop()
        val res = when (kind) {
            Kind.DRILL_START -> R.raw.thinn_seismic_drill_alert
            Kind.STEP -> R.raw.thinn_seismic_step
            Kind.COMPLETE -> R.raw.thinn_seismic_allclear
            Kind.EEW_PREVIEW -> R.raw.thinn_eew_warning
        }
        runCatching {
            MediaPlayer.create(context, res)?.apply {
                setOnCompletionListener { done ->
                    synchronized(SeismicSound) { if (active === done) active = null }
                    done.release()
                }
                active = this
                start()
            }
        }
    }
    /** Bounded local-only simulation tone, never used in a real alert notification. */
    @Synchronized fun practiceLoop(context:Context) {
        stop()
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if(audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL ||
            nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) return
        runCatching {
            MediaPlayer.create(context,R.raw.thinn_seismic_drill_alert)?.apply {
                isLooping=true
                setVolume(.42f,.42f)
                active=this
                start()
            }
        }
    }
    enum class Kind { DRILL_START, STEP, COMPLETE, EEW_PREVIEW }
}

/** Local-device accelerometer illustration, never a TREM-Net observation or a quake detector. */
@Composable
fun LocalMotionTrace(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Float>() }
    var sensorAvailable by remember { mutableStateOf(true) }
    DisposableEffect(enabled, context) {
        if (!enabled) {
            onDispose { }
        } else {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            sensorAvailable = accelerometer != null
            var gx = 0f; var gy = 0f; var gz = 9.8f
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.values.size < 3) return
                    gx = .86f * gx + .14f * event.values[0]
                    gy = .86f * gy + .14f * event.values[1]
                    gz = .86f * gz + .14f * event.values[2]
                    val dx = event.values[0] - gx
                    val dy = event.values[1] - gy
                    val dz = event.values[2] - gz
                    val motion = (sqrt(dx*dx + dy*dy + dz*dz) / 9.8f).coerceIn(0f, 1f)
                    amplitudes.add(motion)
                    if (amplitudes.size > 70) amplitudes.removeAt(0)
                }
            }
            if (accelerometer != null) manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            onDispose { manager.unregisterListener(listener) }
        }
    }
    Column(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("手機動態感測示意", color=Color.White, fontWeight=FontWeight.Bold, fontSize=16.sp)
                Text("僅顯示本機加速度變化，不是地震觀測站或地震偵測器",color=Color.White.copy(alpha=.68f),fontSize=14.sp,lineHeight=16.sp)
            }
            Switch(checked=enabled,onCheckedChange={enabled=it; if (!it) amplitudes.clear()})
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(112.dp).background(Color(0xFF05243B),RoundedCornerShape(19.dp))
            .border(1.dp,ice.copy(alpha=.22f),RoundedCornerShape(19.dp))) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                for (i in 1..3) drawLine(ice.copy(alpha=.12f),Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1.dp.toPx())
                val values = amplitudes.toList()
                if (values.size > 1 && enabled) {
                    val path=Path()
                    for (i in values.indices) {
                        val x=size.width*i/69f
                        val sign=if(i%2==0) 1f else -1f
                        val y=size.height/2f + sign*values[i]*size.height*.36f
                        if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
                    }
                    drawPath(path,ice,style=Stroke(2.3.dp.toPx()))
                } else drawLine(ice.copy(alpha=.5f),Offset(0f,size.height/2),Offset(size.width,size.height/2),2.dp.toPx())
            }
            Text(if(!enabled) "已暫停" else if(!sensorAvailable) "裝置沒有可用的加速度感測器" else "本機動態 · 即時顯示",color=Color.White.copy(alpha=.7f),fontSize=14.sp,
                modifier=Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
    }
}

private data class SafetyStep(val name: String,val english: String,val subtitle: String)
private val safetySteps=listOf(
    SafetyStep("趴下","DROP","降低重心，避免跌倒"),
    SafetyStep("掩護","COVER","躲在堅固桌下，保護頭部及頸部"),
    SafetyStep("穩住","HOLD ON","抓穩桌腳，直到搖晃停止")
)

/** A user-initiated 12-second educational drill: explicit demo label, cancel anytime. */
@Composable
fun EarthquakeSafetyDrill(onClose: () -> Unit) {
    val context=LocalContext.current
    val haptics=LocalHapticEngine.current
    var secondsLeft by remember { mutableIntStateOf(12) }
    var voiceEnabled by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val phase = when {
        secondsLeft > 8 -> 0
        secondsLeft > 4 -> 1
        else -> 2
    }
    val active=safetySteps[phase]
    val pulse = rememberInfiniteTransition(label="drill pulse")
    val ring by pulse.animateFloat(0f,1f,infiniteRepeatable(tween(1450,easing=LinearEasing)),label="wave")
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.TAIWAN
                tts = engine
            }
        }
        onDispose { engine?.stop(); engine?.shutdown(); SeismicSound.stop() }
    }
    LaunchedEffect(Unit) {
        SeismicSound.play(context,SeismicSound.Kind.DRILL_START)
        haptics?.play(HapticEngine.Event.RELEASE)
        while(secondsLeft>0) {
            delay(1000)
            secondsLeft--
        }
        SeismicSound.play(context,SeismicSound.Kind.COMPLETE)
    }
    LaunchedEffect(phase,voiceEnabled) {
        if (secondsLeft<=0) return@LaunchedEffect
        if (phase>0) SeismicSound.play(context,SeismicSound.Kind.STEP)
        haptics?.play(HapticEngine.Event.TAP)
        if(voiceEnabled) tts?.speak(active.name,TextToSpeech.QUEUE_FLUSH,null,"seismic-step-$phase")
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(navy,Color(0xFF18415E),navy)))) {
            Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Surface(color=amber.copy(alpha=.18f),shape=RoundedCornerShape(50)) {
                    Text("教學演示 · 非真實地震警報",color=amber,fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=16.dp,vertical=9.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text("地震防護演練",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold)
                Text("趴下 · 掩護 · 穩住",color=Color.White.copy(alpha=.75f),fontSize=15.sp)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.size(226.dp),contentAlignment=Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r=size.minDimension/2f
                        for(i in 0..2) {
                            val p=(ring+i/3f)%1f
                            drawCircle(if(phase==2) ice.copy(alpha=(1-p)*.36f) else amber.copy(alpha=(1-p)*.35f),
                                r*(.25f+.74f*p),style=Stroke(width=3.dp.toPx()))
                        }
                    }
                    Surface(Modifier.size(145.dp),shape=CircleShape,color=Color.White.copy(alpha=.13f),border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.4f))) {
                        Box(contentAlignment=Alignment.Center) {
                            Icon(when(phase) {0 -> Icons.Rounded.AccessibilityNew; 1 -> Icons.Rounded.Security; else -> Icons.Rounded.PanTool},
                                null,tint=Color.White,modifier=Modifier.size(78.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                AnimatedContent(targetState=phase,label="seismic instruction") { index ->
                    val s=safetySteps[index]
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(s.name,color=Color.White,fontSize=52.sp,fontWeight=FontWeight.Black)
                        Text(s.english,color=ice,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(7.dp))
                        Text(s.subtitle,color=Color.White.copy(alpha=.85f),fontSize=15.sp,textAlign=TextAlign.Center,lineHeight=22.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                LinearProgressIndicator(progress={((12-secondsLeft)/12f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=ice,trackColor=Color.White.copy(alpha=.2f))
                Text(if(secondsLeft>0) "演示剩餘 $secondsLeft 秒" else "演示結束 · 做好防震準備",color=Color.White,fontSize=15.sp,
                    modifier=Modifier.padding(12.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("語音朗讀動作",color=Color.White.copy(alpha=.8f),fontSize=15.sp,modifier=Modifier.weight(1f))
                    Switch(checked=voiceEnabled,onCheckedChange={voiceEnabled=it})
                }
                Spacer(Modifier.weight(1f))
                Text("真正發生地震時請立即就地保護頭頸部，不要為了查看手機而延誤避難。",color=Color.White.copy(alpha=.82f),fontSize=14.sp,lineHeight=19.sp,textAlign=TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick=onClose,modifier=Modifier.fillMaxWidth()) {Text("結束演示")}
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
