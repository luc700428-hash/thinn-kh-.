package io.atmos.weather.next

import io.atmos.weather.ui.brandColor

import android.text.format.Formatter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import io.atmos.weather.BuildConfig
import io.atmos.weather.ui.AppleSpringButton
import io.atmos.weather.ui.thinnSpecularEdge

/** Actual transfer/install state. Handoff to the installer never implies success. */
sealed interface NextOtaState {
    data object Idle:NextOtaState
    data object UpToDate:NextOtaState
    data object Checking:NextOtaState
    data class Available(val version:String,val notes:List<String>):NextOtaState
    data class Downloading(val progress:Float,val indeterminate:Boolean=false):NextOtaState
    data object Verifying:NextOtaState
    data object ReadyToInstall:NextOtaState
    data object Installing:NextOtaState
    data object Completed:NextOtaState
    data class Failed(val message:String):NextOtaState
}

@Composable
private fun UpdateVersionArtwork(version:String) {
    Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF40382F),Color(0xFFB6A18A),
            brandColor(0xFF2E3734),brandColor(0xFF718C85)))).thinnSpecularEdge(14.dp),
        contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w=size.width;val h=size.height
            val ribbon=Path().apply {
                moveTo(w*.35f,0f);cubicTo(w*.95f,h*.20f,-w*.20f,h*.53f,w*.15f,h)
                lineTo(w*.50f,h);cubicTo(w*.05f,h*.58f,w*1.20f,h*.28f,w*.72f,0f);close()
            }
            drawPath(ribbon,Brush.linearGradient(listOf(Color.White.copy(alpha=.40f),Color.Transparent)))
        }
        Text(version.removePrefix("v").substringBefore('.'),fontSize=38.sp,color=Color(0xFFF3E9DB),
            fontWeight=FontWeight.SemiBold,fontStyle=FontStyle.Italic,letterSpacing=(-2).sp)
    }
}

@Composable
private fun TransferProgress(state:NextOtaState.Downloading) {
    val p = state.progress.coerceIn(0f, 1f)
    val reduce=LocalContext.current.getSharedPreferences("thinn_settings",0).getBoolean("reduce_motion",false)
    val phase=if(state.indeterminate && !reduce) {
        val animated by rememberInfiniteTransition(label="unknown-transfer-size").animateFloat(
            -0.3f,1f,infiniteRepeatable(tween(1400,easing=LinearEasing)),label="transfer-travel")
        animated
    } else 0f
    // Like iOS: "正在下載… 剩下約 3 分鐘", estimated from the measured transfer rate.
    val started=remember{longArrayOf(0L)};val startedAt=remember{floatArrayOf(0f)}
    val now=android.os.SystemClock.elapsedRealtime()
    if(started[0]==0L||p<startedAt[0]){started[0]=now;startedAt[0]=p}
    val elapsed=(now-started[0])/1000f;val gained=p-startedAt[0]
    val remaining=if(!state.indeterminate&&elapsed>3f&&gained>.01f)((1f-p)*elapsed/gained) else null
    val shown by animateFloatAsState(p,if(reduce)snap() else spring(1f,120f),label="transfer-bar")
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(9999.dp))
            .semantics {progressBarRangeInfo=if(state.indeterminate)ProgressBarRangeInfo.Indeterminate
                else ProgressBarRangeInfo(p, 0f..1f)}) {
            drawRoundRect(Color(0x338E8E93),cornerRadius=CornerRadius(size.height/2))
            val start=if(state.indeterminate)phase*size.width else 0f
            val length=if(state.indeterminate)size.width*.3f else size.width*shown
            drawRoundRect(brandColor(0xFF328E74),Offset(start,0f),Size(length,size.height),CornerRadius(size.height/2))
        }
        Text(when {
            state.indeterminate->"正在下載…"
            remaining==null->"正在下載… 正在估計剩餘時間"
            remaining<60f->"正在下載… 剩下不到 1 分鐘"
            else->"正在下載… 剩下約 "+(remaining/60f).roundToInt().coerceAtLeast(1)+" 分鐘"
        }+if(state.indeterminate)"" else "（"+(p*100).toInt()+"%）",
            color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=19.sp)
    }
}

/**
 * iOS 27 Software Update. With nothing to install the page shows only a centred status, as
 * Settings does; once an update exists it becomes one grouped card: icon, name, publisher and
 * size, the notes with a "learn more" link, then the blue capsule and the "update tonight" text.
 */
@Composable
fun NextOtaStatusCard(
    state:NextOtaState,onCheck:()->Unit,onDownload:()->Unit,onInstall:()->Unit,
    modifier:Modifier=Modifier,reduceTransparency:Boolean=false,
    version:String=BuildConfig.VERSION_NAME,sizeBytes:Long?=null,releaseNotes:String="",
    status:String="",onTonight:(()->Unit)?=null,onPause:(()->Unit)?=null,
    hasPending:Boolean=false,resumeAvailable:Boolean=false
) {
    val palette=MaterialTheme.colorScheme;val context=LocalContext.current;val blue=palette.primary
    val uriHandler=LocalUriHandler.current
    val quiet=!hasPending&&(state==NextOtaState.Checking||state==NextOtaState.UpToDate||state==NextOtaState.Idle||state==NextOtaState.Completed)
    if(quiet) {
        Column(modifier.fillMaxWidth().padding(vertical=56.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(state==NextOtaState.Checking) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    io.atmos.weather.ui.IOSActivityIndicator(Modifier.size(18.dp))
                    Text("正在檢查軟體更新…",fontSize=15.sp,color=palette.onSurfaceVariant)
                }
            } else {
                Text("thinn-khì "+version.removePrefix("v"),fontSize=20.sp,lineHeight=25.sp,
                    color=palette.onSurface,fontWeight=FontWeight.SemiBold)
                Text(if(state==NextOtaState.UpToDate)"你的軟體已是最新版本。" else "此版本已安裝在你的手機。",
                    fontSize=15.sp,lineHeight=20.sp,color=palette.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                val action:(()->Unit)?=onCheck
                AppleSpringButton(onPressed={action?.invoke()},enabled=action!=null,
                    modifier=Modifier.height(44.dp).clip(RoundedCornerShape(9999.dp))
                        .background(palette.onSurface.copy(alpha=.08f)).padding(horizontal=22.dp)) {
                    Text("檢查更新",fontSize=15.sp,fontWeight=FontWeight.Medium,color=blue)
                }
            }
        }
        return
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(palette.surface)) {
        Column(Modifier.padding(start=16.dp,end=16.dp,top=16.dp,bottom=12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                UpdateVersionArtwork(version)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                    Text("thinn-khì "+version.removePrefix("v"),fontSize=17.sp,lineHeight=22.sp,
                        color=palette.onSurface,fontWeight=FontWeight.SemiBold)
                    Text("thinn-khì 團隊",fontSize=15.sp,lineHeight=20.sp,color=palette.onSurfaceVariant)
                    Text(sizeBytes?.let {Formatter.formatFileSize(context,it)}
                        ?: if(hasPending)"更新檔案" else "目前版本",fontSize=15.sp,lineHeight=20.sp,color=palette.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            if(state is NextOtaState.Downloading) {
                TransferProgress(state);Spacer(Modifier.height(14.dp))
            }
            Column(Modifier.fillMaxWidth().heightIn(max=260.dp)
                .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(releaseNotes.isNotBlank()) {
                    Text("這次更新帶來什麼",color=palette.onSurface,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                    releaseNotes.lineSequence().filter {it.isNotBlank() && !it.trim().startsWith("<!--")}.forEach {raw ->
                        val line=raw.trim().replace("**","").replace(Regex("^#{1,6}\\s+"),"")
                        if(line.startsWith("- ") || line.startsWith("* "))Row(verticalAlignment=Alignment.Top) {
                            Text("•",color=palette.onSurfaceVariant,fontSize=15.sp,lineHeight=21.sp);Spacer(Modifier.width(8.dp))
                            Text(line.drop(2),fontSize=15.sp,lineHeight=21.sp,color=palette.onSurface)
                        } else Text(line,fontSize=15.sp,lineHeight=21.sp,color=palette.onSurface)
                    }
                    Text("了解更多…",color=blue,fontSize=15.sp,
                        modifier=Modifier.clickable {uriHandler.openUri("https://github.com/luc700428-hash/thinn-kh-./releases/tag/v"+version.removePrefix("v"))}
                            .padding(vertical=4.dp))
                } else Text(when(state) {
                    NextOtaState.UpToDate->"你的軟體已是最新版本。"
                    NextOtaState.Checking->"正在檢查軟體更新…"
                    NextOtaState.Idle,NextOtaState.Completed->"此版本已安裝在你的手機。"
                    is NextOtaState.Failed->state.message
                    NextOtaState.Installing->"請在 Android 安裝畫面確認；尚未確認完成升級。"
                    NextOtaState.ReadyToInstall->"更新已準備完成，可以安裝。"
                    NextOtaState.Verifying->"正在驗證更新檔案…"
                    else->"正在準備更新內容…"
                },fontSize=15.sp,lineHeight=21.sp,color=palette.onSurface)
            }
            if(status.isNotBlank() && state !is NextOtaState.Downloading) {
                Spacer(Modifier.height(8.dp))
                Text(status,fontSize=14.sp,lineHeight=19.sp,color=palette.onSurfaceVariant)
            }
            if(state is NextOtaState.Failed && releaseNotes.isNotBlank())
                Text(state.message,fontSize=14.sp,lineHeight=19.sp,color=palette.error)
        }
        Box(Modifier.fillMaxWidth().padding(start=16.dp).height(.5.dp).background(palette.onSurface.copy(alpha=.12f)))
        Column(Modifier.padding(horizontal=16.dp).padding(top=12.dp,bottom=2.dp)) {
            val action:(()->Unit)?=when(state) {
                is NextOtaState.Available->onDownload
                NextOtaState.ReadyToInstall,NextOtaState.Installing->onInstall
                is NextOtaState.Downloading->onPause
                NextOtaState.Idle,NextOtaState.Completed,NextOtaState.UpToDate->onCheck
                is NextOtaState.Failed->if(hasPending)onDownload else onCheck
                else->null
            }
            val label=when(state) {
                is NextOtaState.Available->if(resumeAvailable)"繼續更新" else "立即更新"
                NextOtaState.ReadyToInstall->"立即安裝"
                NextOtaState.Installing->"再次開啟安裝程式"
                is NextOtaState.Downloading->"暫停下載"
                NextOtaState.Checking->"正在檢查更新"
                NextOtaState.Verifying->"正在驗證更新"
                is NextOtaState.Failed->"重試"
                else->"檢查更新"
            }
            val secondary=state is NextOtaState.Downloading
            AppleSpringButton(onPressed={action?.invoke()},enabled=action!=null,
                modifier=Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(9999.dp)).background(if(secondary)palette.onSurface.copy(alpha=.08f) else blue)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(action==null)io.atmos.weather.ui.IOSActivityIndicator(Modifier.size(16.dp),color=Color.White)
                    Text(label,fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=if(secondary)blue else Color.White)
                }
            }
            if(onTonight!=null)AppleSpringButton(onPressed=onTonight,
                modifier=Modifier.fillMaxWidth().height(54.dp)) {
                Text("今晚更新",color=blue,fontSize=17.sp)
            } else Spacer(Modifier.height(12.dp))
        }
    }
}
