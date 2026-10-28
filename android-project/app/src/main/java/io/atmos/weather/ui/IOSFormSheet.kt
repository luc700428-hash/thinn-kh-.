package io.atmos.weather.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
internal val LocalOpticalFormDark=compositionLocalOf {false}
@Composable fun IOSFormSheet(title:String,onDismiss:()->Unit,onDone:(()->Unit)?=null,validate:()->Boolean={true},accent:Color=Color(0xFF0A84FF),content:@Composable ColumnScope.()->Unit){
 val reduced=rememberAppleReduceMotion();val scope=rememberCoroutineScope();val screen=LocalConfiguration.current.screenHeightDp.dp;val hidden=with(LocalDensity.current){(screen+64.dp).toPx()}
 var shown by remember{mutableStateOf(false)};var closing by remember{mutableStateOf(false)};var dragging by remember{mutableStateOf(false)};var drag by remember{mutableFloatStateOf(0f)}
 LaunchedEffect(Unit){shown=true}
 val y by animateFloatAsState(if(shown)drag else hidden,if(reduced||dragging)snap() else if(closing)tween(240,easing=AppleMotion.snap) else spring(.84f,390f),label="共用表單進退場")
 fun finish(commit:Boolean){if(closing||(commit&&!validate()))return;closing=true;shown=false;scope.launch{if(!reduced)delay(240);if(commit){onDone?.invoke();closing=false;shown=true} else onDismiss()}}
 ThinnDialog(onDismissRequest={finish(false)},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(),contentAlignment=Alignment.BottomCenter){
   CompositionLocalProvider(LocalDrumEnabled provides !closing,LocalOpticalFormDark provides (MaterialTheme.colorScheme.background.red<.5f)){
    Column(Modifier.widthIn(max=600.dp).fillMaxWidth().heightIn(max=screen-28.dp).graphicsLayer{translationY=y}.clip(RoundedCornerShape(topStart=32.dp,topEnd=32.dp)).background(MaterialTheme.colorScheme.background).thinnSpecularEdge(32.dp).navigationBarsPadding()){
     Box(Modifier.fillMaxWidth().height(24.dp).pointerInput(hidden){detectVerticalDragGestures(onDragStart={dragging=true},onVerticalDrag={c,d->c.consume();drag=(drag+d).coerceAtLeast(0f)},onDragEnd={dragging=false;if(drag>hidden*.15f)finish(false) else drag=0f},onDragCancel={dragging=false;drag=0f})},contentAlignment=Alignment.Center){Box(Modifier.size(36.dp,5.dp).clip(RoundedCornerShape(50)).background(Color(0xFF636366)))}
     Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){IOS27RoundControl("×",onClick={finish(false)},fill=MaterialTheme.colorScheme.surfaceVariant,foreground=MaterialTheme.colorScheme.onSurface);Text(title,color=MaterialTheme.colorScheme.onSurface,fontSize=18.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,modifier=Modifier.weight(1f));if(onDone!=null)IOS27RoundControl("✓",onClick={finish(true)},fill=accent,foreground=Color.White) else Spacer(Modifier.size(42.dp))}
     Column(Modifier.fillMaxWidth().weight(1f,fill=false).appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
   }
  }
 }
}
@Composable fun IOSDurationSetting(label:String,value:Int,onValue:(Int)->Unit,min:Int=1,max:Int=120,unit:String="分鐘"){
 var open by remember{mutableStateOf(false)};var draft by remember(value){mutableIntStateOf(value)}
 Row(Modifier.fillMaxWidth().iosJellyPress().clip(RoundedCornerShape(16.dp)).thinnBackdrop(rememberAppleReduceTransparency(),16.dp).background(MaterialTheme.colorScheme.surface.copy(alpha=if(rememberAppleReduceTransparency())1f else .94f)).thinnSpecularEdge(16.dp).clickable{draft=value;open=true}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(label,color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,modifier=Modifier.weight(1f));Text("$value $unit",color=MaterialTheme.colorScheme.primary,fontSize=17.sp);Spacer(Modifier.width(9.dp));SettingsChevron()}
 if(open)IOSFormSheet(label,onDismiss={open=false},onDone={onValue(draft);open=false}){IOSDurationDrums(draft,{draft=it},min,max,unit)}
}
