package io.atmos.weather.earthquake

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassButton
import io.atmos.weather.ui.GlassOutlinedButton
import io.atmos.weather.ui.GlassSlider
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosHeartbeat
import io.atmos.weather.ui.iosJellyPress

/**
 * 回放與演練, rebuilt around one decision per screen, the way DPIP does it:
 *  - no replay running: pick an earthquake from the list (or let the app pick), nothing else;
 *  - replay running: one player card with a big play button, a scrubber, the bulletin number,
 *    speed and voice, and the map above it showing the waves;
 *  - drills: two large tiles, each says what will happen and how long it takes.
 */
@Composable internal fun SeismicReplayPicker(reports:List<EarthquakeReport>,status:String,busy:Boolean,
    onPick:(EarthquakeReport)->Unit,onRandom:()->Unit) {
    val palette=MaterialTheme.colorScheme
    val candidates=reports.filter{SeismicGeo.visible(it.latitude,it.longitude)}.sortedByDescending{it.origin}.take(8)
    Column(Modifier.fillMaxWidth().testTag("seismic-replay-picker"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.History,null,tint=palette.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("地震回放",color=palette.onSurface,fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text("選一筆地震，重看當時的速報與地震波",color=palette.onSurfaceVariant,fontSize=14.sp)
            }
            GlassOutlinedButton(onClick=onRandom,enabled=candidates.isNotEmpty()&&!busy,
                contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
                Icon(Icons.Rounded.Shuffle,null,Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("隨機挑選",fontSize=14.sp)
            }
        }
        if(busy)Text("正在尋找這筆地震的速報紀錄…",color=palette.primary,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        else if(status.isNotBlank())Text(status,color=palette.primary,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        if(candidates.isEmpty())Text("尚未取得地震報告，連上網路後就能選擇要回放的地震。",color=palette.onSurfaceVariant,fontSize=14.sp)
        candidates.forEachIndexed{index,report->
            Row(Modifier.fillMaxWidth().iosAppear(index).iosJellyPress().clip(RoundedCornerShape(18.dp))
                .clickable(enabled=!busy,role=Role.Button){onPick(report)}
                .semantics{contentDescription="回放 ${report.shortLocation} ${SeismicDisplayFormat.magnitude(report.magnitude)}"}
                .padding(horizontal=6.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                SeismicIntensityTile(report.maxIntensity,40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(report.shortLocation,color=palette.onSurface,fontSize=16.sp,fontWeight=FontWeight.SemiBold,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${reportTime(report)} · ${SeismicDisplayFormat.magnitude(report.magnitude)}",color=palette.onSurfaceVariant,fontSize=14.sp)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(palette.primary.copy(alpha=.14f)),contentAlignment=Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow,null,tint=palette.primary)
                }
            }
        }
        Text("只使用可核對的歷史速報，不會發送真實警報；語音受手機靜音、勿擾與系統 TTS 引擎控制。",
            color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=18.sp)
    }
}

@Composable internal fun SeismicReplayPlayer(report:EarthquakeReport?,serial:Int?,position:Float,duration:Float,playing:Boolean,
    finished:Boolean,speed:Float,onSeek:(Float)->Unit,onToggle:()->Unit,onSpeed:(Float)->Unit,onVoice:(()->Unit)?,onStop:()->Unit) {
    val palette=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("seismic-replay-player"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            SeismicIntensityTile(report?.maxIntensity,44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if(finished)"回放完成（非真實預警）" else "歷史回放中 · 非即時預警",color=seismicCautionInk,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Text(report?.shortLocation?:"歷史地震",color=palette.onSurface,fontSize=18.sp,fontWeight=FontWeight.Bold,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${report?.let{reportTime(it,"yyyy/MM/dd HH:mm")}?:""} · ${SeismicDisplayFormat.magnitude(report?.magnitude)}",
                    color=palette.onSurfaceVariant,fontSize=14.sp)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(role=Role.Button,onClick=onStop)
                .semantics{contentDescription="結束回放"},contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.Close,null,tint=palette.onSurfaceVariant)
            }
        }
        // Where the replay is: elapsed seconds since the quake and which bulletin was out then.
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("震後 ${position.toInt()} 秒",color=palette.onSurface,fontSize=16.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            // Each new bulletin gives the label a small beat, so the change is noticed.
            Text(serial?.let{"速報第 $it 報"}?:"速報尚未發布",color=if(serial!=null)seismicCautionInk else palette.onSurfaceVariant,
                fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.iosHeartbeat(serial,strong=true))
        }
        GlassSlider(value=position,onValueChange=onSeek,valueRange=0f..duration.coerceAtLeast(1f),modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            GlassButton(onClick=onToggle,contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),
                modifier=Modifier.semantics{contentDescription=if(playing)"暫停回放" else "播放回放"}) {
                androidx.compose.animation.Crossfade(playing,animationSpec=androidx.compose.animation.core.tween(180),label="播放鍵形變") {p->
                    Icon(if(p)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null)
                }
                Spacer(Modifier.width(6.dp))
                Text(if(playing)"暫停" else if(finished)"重新播放" else "繼續",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            listOf(1f,2f,4f).forEach{s->
                val on=s==speed
                Text("${s.toInt()}×",color=if(on)palette.onPrimary else palette.onSurface,fontSize=15.sp,fontWeight=FontWeight.SemiBold,
                    modifier=Modifier.clip(RoundedCornerShape(999.dp)).background(if(on)palette.primary else palette.onSurface.copy(alpha=.07f))
                        .clickable(role=Role.RadioButton){onSpeed(s)}.semantics{contentDescription="${s.toInt()} 倍速"}
                        .padding(horizontal=12.dp,vertical=8.dp))
            }
        }
        if(onVoice!=null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role=Role.Button,onClick=onVoice)
            .padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.RecordVoiceOver,null,tint=palette.primary,modifier=Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("重播語音",color=palette.primary,fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            Text("明確聲明非即時地震",color=palette.onSurfaceVariant,fontSize=14.sp)
        }
    }
}

/** Two drills as large tiles; each says what happens and how long it lasts before it starts. */
@Composable internal fun SeismicDrillPanel(onGeneral:()->Unit,onChiChi:()->Unit) {
    val palette=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("seismic-drill-panel"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("防災演練",fontSize=20.sp,color=palette.onSurface,fontWeight=FontWeight.Bold)
        Text("演練會以全螢幕速報畫面與警報音模擬真實情況，全程標示演練，不會通知其他人。",fontSize=14.sp,color=palette.onSurfaceVariant)
        DrillTile("一般演練","虛構的中度地震，約 1 分鐘，適合每天練習",Color(0xFF3FA7F5),onGeneral,0)
        DrillTile("臺灣 921 大地震演練","1999 集集 M 7.3 官方震源與震度，所在地震度以本機模型示意",Color(0xFFE5484D),onChiChi,1)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.onSurface.copy(alpha=.05f)).padding(12.dp),
            horizontalArrangement=Arrangement.SpaceEvenly) {
            listOf("趴下","掩護","穩住").forEachIndexed{i,step->
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("${i+1}",color=palette.primary,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text(step,color=palette.onSurface,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun DrillTile(title:String,detail:String,accent:Color,onStart:()->Unit,index:Int) {
    val palette=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().iosAppear(index).iosJellyPress().clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha=.12f))
        .clickable(role=Role.Button,onClick=onStart).semantics{contentDescription="開始$title"}.padding(14.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(accent),contentAlignment=Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow,null,tint=Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title,color=palette.onSurface,fontSize=17.sp,fontWeight=FontWeight.Bold)
            Text(detail,color=palette.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp)
        }
    }
}
