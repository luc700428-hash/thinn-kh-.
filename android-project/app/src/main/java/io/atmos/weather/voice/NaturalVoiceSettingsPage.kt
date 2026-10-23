package io.atmos.weather.voice
import io.atmos.weather.ui.appleBouncingScroll

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Foundation-only interface: no Material Design components or theme. */
@Composable
fun NaturalVoiceSettingsPage(onClose:()->Unit) {
    val ctx=LocalContext.current
    val pref=remember(ctx){ctx.getSharedPreferences("thinn_natural_voice",Context.MODE_PRIVATE)}
    var enabled by remember {mutableStateOf(NaturalVoiceHub.enabled(ctx))}
    var voice by remember {mutableStateOf(NaturalVoiceHub.voice(ctx))}
    var rate by remember {mutableFloatStateOf(NaturalVoiceHub.rate(ctx))}
    var volume by remember {mutableFloatStateOf(NaturalVoiceHub.volume(ctx))}
    var alerts by remember {mutableStateOf(pref.getBoolean("alerts",true))}
    var daily by remember {mutableStateOf(pref.getBoolean("daily",false))}
    var personal by remember {mutableStateOf(pref.getBoolean("personal",false))}
    var message by remember {mutableStateOf("Azure 試聽使用實際微軟聲線；未連線時不會播放冒充聲音。")}
    val white=Color(0xFFF8FAFF)
    Column(Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF091422),Color(0xFF183B5C))))
        .statusBarsPadding().navigationBarsPadding()
        .appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=18.dp)) {
        BasicText("‹ 返回",modifier=Modifier.clickable { onClose() }.padding(8.dp),
            style=TextStyle(color=Color(0xFF97DCFF),fontSize=17.sp))
        Spacer(Modifier.height(12.dp))
        BasicText("自然語音",style=TextStyle(color=white,fontSize=32.sp,fontWeight=FontWeight.SemiBold))
        BasicText("Microsoft Azure AI Speech · 台灣女聲",style=TextStyle(color=Color(0xFFB6D0DF),fontSize=15.sp))
        Spacer(Modifier.height(20.dp))
        SettingToggle("啟用自然語音",enabled){enabled=it}
        Spacer(Modifier.height(18.dp))
        BasicText("選擇聲線",style=TextStyle(color=white,fontSize=19.sp))
        Spacer(Modifier.height(8.dp))
        listOf("zh-TW-HsiaoChenNeural" to "曉臻 · 溫柔台灣女聲",
               "zh-TW-HsiaoYuNeural" to "曉雨 · 台灣女聲").forEach { (id,label) ->
            Box(Modifier.fillMaxWidth().padding(vertical=5.dp)
                .background(if(voice==id)Color.White.copy(alpha=.22f) else Color.White.copy(alpha=.08f),RoundedCornerShape(21.dp))
                .border(1.dp,Color.White.copy(alpha=if(voice==id).55f else .13f),RoundedCornerShape(21.dp))
                .clickable{voice=id}.padding(16.dp)) {
                BasicText((if(voice==id)"●  " else "○  ")+label,
                    style=TextStyle(color=white,fontSize=17.sp))
            }
        }
        Spacer(Modifier.height(10.dp))
        VoiceLevel("語速 ${"%.2f".format(java.util.Locale.US,rate)} 倍",rate,.8f..1.15f){rate=it}
        Spacer(Modifier.height(18.dp))
        VoiceLevel("日常語音音量 ${(volume*100).roundToInt()}%",volume,.2f..1f){volume=it}
        Spacer(Modifier.height(18.dp))
        SettingToggle("重大天氣警報語音",alerts){alerts=it}
        Spacer(Modifier.height(8.dp))
        SettingToggle("日常天氣／空氣品質播報",daily){daily=it}
        Spacer(Modifier.height(8.dp))
        SettingToggle("個人事項語音（解鎖才播）",personal){personal=it}
        Spacer(Modifier.height(8.dp))
        BasicText("地震緊急警示採本機音效與離線路徑，不受 Azure 開關及語速限制。鬧鐘音量由系統鬧鐘音量控制。",
            style=TextStyle(color=Color(0xFFD3E5F1),fontSize=14.sp,lineHeight=21.sp))
        Spacer(Modifier.height(18.dp))
        VoiceAction("儲存語音設定") {
            NaturalVoiceHub.save(ctx,enabled,voice,rate,volume,daily,personal,alerts)
            message="已儲存。Azure 後端未設定時，原有本機警報與鬧鐘功能照常運作。"
        }
        Spacer(Modifier.height(10.dp))
        VoiceAction("試聽 Microsoft 自然女聲") {
            NaturalVoiceHub.save(ctx,enabled,voice,rate,volume,daily,personal,alerts)
            message="正在連接 Azure；失敗時不會假裝播放曉臻或曉雨。"
            NaturalVoiceHub.preview(ctx){ok,text->message=text}
        }
        Spacer(Modifier.height(10.dp))
        VoiceAction("清除語音快取") {
            val count=NaturalVoiceHub.clearCache(ctx)
            message="已清除 $count 個本機語音快取。"
        }
        Spacer(Modifier.height(14.dp))
        BasicText(message,style=TextStyle(color=Color(0xFFBCE6FF),fontSize=14.sp,lineHeight=21.sp))
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingToggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.10f),RoundedCornerShape(20.dp))
        .clickable {onChange(!checked)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
        BasicText(label,Modifier.weight(1f),style=TextStyle(color=Color.White,fontSize=16.sp))
        Box(Modifier.size(47.dp,27.dp)
            .background(if(checked) Color(0xFF68CFFA) else Color(0xFF596779),RoundedCornerShape(18.dp))
            .padding(3.dp),contentAlignment=if(checked)Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(21.dp).background(Color.White,RoundedCornerShape(11.dp)))
        }
    }
}
@Composable
private fun VoiceAction(label:String,onClick:()->Unit) {
    Box(Modifier.fillMaxWidth().height(51.dp)
        .background(Color.White.copy(alpha=.17f),RoundedCornerShape(26.dp))
        .border(1.dp,Color.White.copy(alpha=.29f),RoundedCornerShape(26.dp))
        .clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        BasicText(label,style=TextStyle(color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold))
    }
}
@Composable
private fun VoiceLevel(title:String,value:Float,range:ClosedFloatingPointRange<Float>,onChange:(Float)->Unit) {
    val currentValue by rememberUpdatedState(value)
    val percent=((value-range.start)/(range.endInclusive-range.start)).coerceIn(0f,1f)
    Column {
        BasicText(title,style=TextStyle(color=Color.White,fontSize=16.sp))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(30.dp)
            .pointerInput(range) { detectHorizontalDragGestures {change,dx ->
                change.consume();onChange((currentValue+dx/size.width*(range.endInclusive-range.start))
                    .coerceIn(range.start,range.endInclusive))
            } }, contentAlignment=Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(6.dp)
                .background(Color.White.copy(alpha=.23f),RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(percent).height(6.dp)
                .background(Color(0xFF8CD9F8),RoundedCornerShape(4.dp)))
        }
    }
}
