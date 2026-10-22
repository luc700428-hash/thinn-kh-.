package io.atmos.weather.next.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.design.IosText
import java.util.concurrent.TimeUnit

class NextAlarmRingActivity:ComponentActivity() {
    private var player:MediaPlayer?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id=intent.getIntExtra("alarm_id",-1)
        val alarm=NextAlarmStore.byId(this,id)
        runCatching {
            val uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            player=MediaPlayer.create(this,uri, null)?.apply{isLooping=true;start()}
        }
        setContent {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(30.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly) {
                IosText("鬧鐘",22,color=Color(0xFFB9B9BE))
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    IosText("%02d:%02d".format(alarm?.hour?:0,alarm?.minute?:0),78,FontWeight.Light)
                    IosText(alarm?.label?:"鬧鐘",19)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                    if(alarm?.snooze==true) Box(Modifier.weight(1f).height(72.dp).background(Color(0xFF39393C),RoundedCornerShape(38.dp))
                        .clickable{stop(id);snooze(id);finish()},contentAlignment=Alignment.Center) {
                        IosText("稍後提醒",19,FontWeight.Medium)
                    }
                    Box(Modifier.weight(1f).height(72.dp).background(Color(0xFFFF9F0A),RoundedCornerShape(38.dp))
                        .clickable{stop(id);finish()},contentAlignment=Alignment.Center) {
                        IosText("停止",19,FontWeight.SemiBold,color=Color.Black)
                    }
                }
            }
        }
    }
    private fun stop(id:Int) {
        player?.stop();player?.release();player=null
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }
    private fun snooze(id:Int) {
        val previous=NextAlarmStore.byId(this,id)?:return
        // A snooze is a separate absolute pending intent; it does not change the repeat rule.
        val intent=android.content.Intent(this,NextAlarmReceiver::class.java).putExtra("alarm_id",id).putExtra("snooze",true)
        val pending=android.app.PendingIntent.getBroadcast(this,id xor 0x40000000,intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val mgr=getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        runCatching {if(NextAlarmScheduler.canExact(this)) mgr.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(9),pending)}
    }
    override fun onDestroy(){player?.release();player=null;super.onDestroy()}
}
