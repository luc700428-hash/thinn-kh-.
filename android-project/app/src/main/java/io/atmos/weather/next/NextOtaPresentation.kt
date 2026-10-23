package io.atmos.weather.next

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppleGlassCard
import io.atmos.weather.ui.AppleIntelligenceGlow
import io.atmos.weather.ui.AppleMotion
import io.atmos.weather.ui.AppleSpringButton
import kotlin.math.PI
import kotlin.math.sin

/** Visual state ONLY: AppUpdater controls signing, validation, download and installation. */
sealed interface NextOtaState {
    data object Idle : NextOtaState
    data object Checking : NextOtaState
    data class Available(val version: String, val notes: List<String>) : NextOtaState
    data class Downloading(val progress: Float) : NextOtaState
    data object Verifying : NextOtaState
    data object ReadyToInstall : NextOtaState
    data object Installing : NextOtaState
    data object Completed : NextOtaState
    data class Failed(val message: String) : NextOtaState
}

private val soft = Color(0xFFE4ECF4)
private val fluidColors = listOf(Color(0xFF0A84FF),Color(0xFFBF5AF2),Color(0xFF5AC8FA))

@Composable
private fun AppleUpdateCore(checking:Boolean, isAvailable:Boolean, reduceMotion:Boolean) {
    val cycle = rememberInfiniteTransition(label="update-breath")
    val t by cycle.animateFloat(0f,(2*PI).toFloat(),infiniteRepeatable(
        tween(2400,easing=AppleMotion.fluid),RepeatMode.Restart), label="phase")
    val morph by animateFloatAsState(if(checking)1f else .65f,
        animationSpec=tween(550,easing=AppleMotion.fluid),label="update-core-morph")
    val breathe = if (checking && !reduceMotion) sin(t)*6f else 0f
    AppleIntelligenceGlow(modifier=Modifier.size(85.dp),radius=24.dp,enabled=checking || isAvailable) {
        Box(Modifier.fillMaxSize()
            .graphicsLayer { scaleX=morph;scaleY=morph;translationY=breathe.dp.toPx() }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF82BDE0),
                Color(0xFF405884),Color(0xFF24334D)))),
            contentAlignment=Alignment.Center) {
            // Original cross-platform update emblem, not a copied Apple asset.
            Canvas(Modifier.size(42.dp)) {
                val c=androidx.compose.ui.geometry.Offset(size.width/2f,size.height/2f)
                val r=size.minDimension*.38f
                drawCircle(Color.White.copy(alpha=.86f),radius=r,center=c,style=Stroke(3.dp.toPx()))
                drawLine(Color.White,androidx.compose.ui.geometry.Offset(c.x,c.y-r*.75f),androidx.compose.ui.geometry.Offset(c.x,c.y+r*.1f),3.dp.toPx())
                drawLine(Color.White,androidx.compose.ui.geometry.Offset(c.x-r*.38f,c.y-r*.02f),
                    androidx.compose.ui.geometry.Offset(c.x,c.y+r*.3f),3.dp.toPx())
                drawLine(Color.White,androidx.compose.ui.geometry.Offset(c.x,c.y+r*.3f),
                    androidx.compose.ui.geometry.Offset(c.x+r*.38f,c.y-r*.02f),3.dp.toPx())
            }
        }
    }
}

@Composable
private fun AppleLiquidProgress(progress:Float,reduceMotion:Boolean) {
    val bounded=progress.coerceIn(0f,1f)
    val flowing=rememberInfiniteTransition(label="liquid-progress-flow")
    val phase by flowing.animateFloat(0f,1f,infiniteRepeatable(
        tween(3200,easing=AppleMotion.fluid),RepeatMode.Restart),label="glow-translation")
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("正在準備更新項目⋯",color=soft,fontSize=14.sp)
            Text("${(bounded*100).toInt()}%",color=Color.White,fontSize=15.sp,
                style=TextStyle(fontFeatureSettings="tnum"))
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(bounded,0f..1f) }) {
            drawRoundRect(Color(0x664B647A),cornerRadius=androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            val fullWidth=size.width*bounded
            if(fullWidth>0f){
                val offset=if(reduceMotion)0f else phase*size.width
                drawRoundRect(Brush.linearGradient(fluidColors,
                    start=androidx.compose.ui.geometry.Offset(-size.width+offset,0f),
                    end=androidx.compose.ui.geometry.Offset(size.width+offset,size.height)),
                    size=androidx.compose.ui.geometry.Size(fullWidth,size.height),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                drawCircle(Color.White.copy(alpha=.95f), radius=4.dp.toPx(),
                    center=androidx.compose.ui.geometry.Offset(fullWidth.coerceIn(4.dp.toPx(),size.width),size.height/2f))
            }
        }
    }
}

/**
 * CTA callbacks and OTA progression are inputs; never substitute fake progress or
 * claim installation when the Android installer was merely opened.
 */
@Composable
fun NextOtaStatusCard(
    state:NextOtaState,
    onCheck:()->Unit,
    onDownload:()->Unit,
    onInstall:()->Unit,
    modifier:Modifier=Modifier,
    reduceTransparency:Boolean=false
){
    val reduced=androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
        .getBoolean("reduce_motion",false)
    var expanded by remember(state) { mutableStateOf(false) }
    val checking=state==NextOtaState.Checking
    val available=state is NextOtaState.Available
    val title=when(state){
        NextOtaState.Idle->"系統更新"
        NextOtaState.Checking->"正在檢查更新項目⋯"
        is NextOtaState.Available->"發現新版 ${state.version}"
        is NextOtaState.Downloading->"正在下載更新"
        NextOtaState.Verifying->"正在驗證安裝檔"
        NextOtaState.ReadyToInstall->"已準備安裝"
        NextOtaState.Installing->"等待 Android 安裝確認"
        NextOtaState.Completed->"更新完成"
        is NextOtaState.Failed->"無法完成更新"
    }
    AppleGlassCard(modifier=modifier.fillMaxWidth(),radius=22.dp,
        contentPadding=PaddingValues(20.dp)) {
        Text("V25 · 更新狀態",fontSize=14.sp,color=soft)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            AppleUpdateCore(checking,available,reduced)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(title,fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                val subtitle=when(state){
                    NextOtaState.Idle->"檢查官方正式版本"
                    NextOtaState.Checking->"正在安全比對 GitHub Release"
                    is NextOtaState.Available->"新版本已通過發行檢查"
                    is NextOtaState.Downloading->"下載量來自實際網路傳輸"
                    NextOtaState.Verifying->"檢查來源與完整性"
                    NextOtaState.ReadyToInstall->"等待系統安裝"
                    NextOtaState.Installing->"請在系統安裝畫面確認；尚未確認完成升級"
                    NextOtaState.Completed->"版本檢查已完成"
                    is NextOtaState.Failed->state.message
                }
                Text(subtitle,fontSize=14.sp,color=soft,lineHeight=20.sp)
            }
        }
        AnimatedVisibility(visible=state is NextOtaState.Available,
            enter=expandVertically(animationSpec=tween(550,easing=AppleMotion.fluid))+fadeIn(),
            exit=shrinkVertically()+fadeOut()) {
            val notes=(state as? NextOtaState.Available)?.notes.orEmpty()
            Column(Modifier.fillMaxWidth().padding(top=16.dp)
                .animateContentSize(animationSpec=tween(320,easing=AppleMotion.fluid)),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("更新內容",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                notes.take(if(expanded)40 else 4).forEach { entry ->
                    Text("• $entry",fontSize=14.sp,color=soft,lineHeight=21.sp)
                }
                if(notes.size>4) Text(if(expanded)"收合" else "瞭解更多⋯",
                    modifier=Modifier.clickable { expanded=!expanded }.padding(vertical=4.dp),
                    color=Color(0xFF8FD6FF),fontSize=14.sp)
            }
        }
        if(state is NextOtaState.Downloading) {
            val p = state.progress.coerceIn(0f, 1f)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }) {
                AppleLiquidProgress(p,reduced)
            }
        }
        val action:(()->Unit)?=when(state){
            NextOtaState.Idle,is NextOtaState.Failed->onCheck
            is NextOtaState.Available->onDownload
            NextOtaState.ReadyToInstall->onInstall
            else->null
        }
        if(action!=null){
            Spacer(Modifier.height(18.dp))
            AppleIntelligenceGlow(modifier=Modifier.fillMaxWidth().height(54.dp),radius=18.dp,
                enabled=available) {
                AppleSpringButton(onPressed=action,modifier=Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(fluidColors))) {
                    Text(when(state){
                        is NextOtaState.Available->"立即下載並安裝"
                        NextOtaState.ReadyToInstall->"開始安裝"
                        else->"重新檢查更新"
                    },fontWeight=FontWeight.SemiBold,fontSize=16.sp,color=Color.White)
                }
            }
        }
    }
}
