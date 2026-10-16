package io.atmos.weather.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Opened by the alarm notification. No claim to bypass device lock screen or Do Not Disturb. */
class WeatherAlarmRingActivity:ComponentActivity(), TextToSpeech.OnInitListener {
    private var sound:MediaPlayer?=null
    private var speaker:TextToSpeech?=null
    private var vibration:Vibrator?=null
    private var alarmId=-1
    private var alarm:WeatherAlarm?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId=intent.getIntExtra("alarm_id",-1)
        alarm=WeatherAlarmStore.load(this).firstOrNull {it.id==alarmId}
        val info=alarm
        if(info==null){finish();return}
        runCatching {
            val uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            sound=MediaPlayer.create(this,uri)?.apply {isLooping=true;start()}
        }
        runCatching {
            vibration=if(android.os.Build.VERSION.SDK_INT>=31)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                else (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            vibration?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,450,380),0))
        }
        if(info.weatherVoice) speaker=TextToSpeech(this,this)
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
                    if(info.snooze) Button(onClick={close(true)},modifier=Modifier.fillMaxWidth().height(72.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFA939)),shape=RoundedCornerShape(36.dp)) {
                        Text("稍後提醒 · 10 分鐘",fontSize=21.sp,color=Color.Black)
                    }
                    OutlinedButton(onClick={close(false)},modifier=Modifier.fillMaxWidth().height(72.dp),
                        shape=RoundedCornerShape(36.dp)) {Text("停止鬧鐘",fontSize=21.sp)}
                }
            }
        }
    }
    private fun close(snooze:Boolean) {
        if(snooze) WeatherAlarmScheduler.scheduleSnooze(this,alarmId)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(740000+alarmId)
        release()
        finish()
    }
    override fun onInit(status:Int) {
        if(status!=TextToSpeech.SUCCESS || isFinishing) return
        speaker?.language=Locale.TAIWAN
        val prefs=getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE)
        val stamp=prefs.getLong("latest_weather_at",0L)
        val fresh=System.currentTimeMillis()-stamp in 0L..(3*60*60_000L)
        val message=if(fresh) prefs.getString("latest_weather_summary",null)
            ?:"早安，請查看今天最新的天氣預報。"
            else "早安。天氣資料尚未更新，請打開天氣 App 確認今日預報及警特報。"
        speaker?.speak(message,TextToSpeech.QUEUE_FLUSH,null,"wake_weather")
    }
    private fun release() {
        runCatching {sound?.stop();sound?.release()};sound=null
        runCatching {speaker?.stop();speaker?.shutdown()};speaker=null
        vibration?.cancel()
    }
    override fun onDestroy(){release();super.onDestroy()}
    override fun onBackPressed(){close(false)}
}
