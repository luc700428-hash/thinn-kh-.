package io.atmos.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.LocalHapticEngine
import io.atmos.weather.presentation.home.WeatherAppearance
import kotlin.math.roundToInt

private val ink=Color.White
private val soft=ThinnDesignTokens.secondaryInkDark
private val outline=Color.White.copy(alpha=.20f)

/** All surfaces share the same blur-free translucent material; reduce-transparency is honoured.
 * Native rendering prioritises scrolling performance and text contrast over fake blur.
 */
@Composable
fun GlassSurface(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    val opaque=prefs.getBoolean("reduce_transparency",false)
    Column(modifier.clip(RoundedCornerShape(ThinnDesignTokens.radiusCard))
        .background(if(opaque) Brush.verticalGradient(listOf(Color(0xFF24384E),Color(0xFF1C2C40)))
            else Brush.verticalGradient(listOf(Color(0xDB29465D),Color(0xEE172D43))))
        .border(1.dp,outline,RoundedCornerShape(ThinnDesignTokens.radiusCard))
        .padding(16.dp),content=content)
}

@Composable
fun GlassAction(title:String,subtitle:String="",modifier:Modifier=Modifier,onClick:()->Unit) {
    val feedback=LocalHapticEngine.current
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed&&!reduce).985f else 1f,
        animationSpec=tween(if(reduce)0 else 120),label="選項回饋")
    Row(modifier.fillMaxWidth().heightIn(min=56.dp)
        .graphicsLayer{scaleX=scale;scaleY=scale}
        .clip(RoundedCornerShape(ThinnDesignTokens.radiusRow))
        .clickable(interactionSource=interaction,indication=null) {
            feedback?.play(HapticEngine.Event.TAP);onClick()
        }
        .padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,color=ink,fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.Medium)
            if(subtitle.isNotBlank())Text(subtitle,color=soft,fontSize=14.sp,lineHeight=17.sp)
        }
        Icon(Icons.Rounded.ChevronRight,contentDescription=null,tint=soft,modifier=Modifier.size(19.dp))
    }
}

@Composable
fun GlassToggle(label:String,description:String,value:Boolean,change:(Boolean)->Unit) {
    ThinnRow(title=label,subtitle=description,trailing={
        GlassSwitch(checked=value,onCheckedChange=change,
            modifier=Modifier.semantics { contentDescription=label })
    })
}

@Composable
fun GlassTrackingSlider(title:String,value:Float,change:(Float)->Unit) {
    val bounded=value.coerceIn(0f,1f)
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title,color=ink,fontSize=15.sp)
            Text("${(bounded*100f).roundToInt()}%",color=soft,fontSize=15.sp)
        }
        GlassSlider(bounded,change,modifier=Modifier.fillMaxWidth()
            .semantics { contentDescription=title })
    }
}

private data class NavTab(val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
private val navTabs=listOf(
    NavTab("天氣",Icons.Rounded.WbSunny),
    NavTab("地震",Icons.Rounded.Waves),
    NavTab("工具",Icons.Rounded.GridView),
    NavTab("設定",Icons.Rounded.Settings)
)

@Composable
fun AppGlassNavigation(selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    val feedback=LocalHapticEngine.current
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    val reduced=prefs.getBoolean("reduce_motion",false)
    val opaque=prefs.getBoolean("reduce_transparency",false)
    Row(modifier.fillMaxWidth().padding(horizontal=17.dp)
        .height(70.dp).clip(RoundedCornerShape(ThinnDesignTokens.radiusNav))
        .background(if(opaque) Color(0xFF1E3149) else Color(0xED172E48))
        .border(1.dp,outline,RoundedCornerShape(ThinnDesignTokens.radiusNav))
        .padding(5.dp),verticalAlignment=Alignment.CenterVertically) {
        navTabs.forEachIndexed { index,tab ->
            val active=selected==index
            val filled by animateFloatAsState(if(active)1f else 0f,
                animationSpec=tween(if(reduced)0 else 210),label="分頁選取")
            Column(Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(23.dp))
                .background(ThinnDesignTokens.accent.copy(alpha=.18f*filled))
                .semantics { contentDescription="${tab.label}分頁" }
                .selectable(selected=active,role=Role.Tab,onClick={
                    if(selected!=index)feedback?.play(HapticEngine.Event.TAP)
                    onSelect(index)
                }),verticalArrangement=Arrangement.Center,
                horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(tab.icon,null,modifier=Modifier.size(24.dp),
                    tint=if(active)Color.White else soft.copy(alpha=.85f))
                Spacer(Modifier.height(2.dp))
                Text(tab.label,fontSize=16.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Medium,
                    color=if(active)Color.White else soft)
            }
        }
    }
}

@Composable
fun AppGlassToolsPage(onBriefings:()->Unit,onUpdates:()->Unit,onDiagnostics:()->Unit,
    onSeismic:()->Unit,onTasks:()->Unit={}) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1A2B),Color(0xFF254766))))
        .verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=57.dp,bottom=118.dp)) {
        Text("工具",color=ink,style=androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text("常用功能與你的生活事項",color=soft,fontSize=15.sp)
        Spacer(Modifier.height(17.dp))
        ThinnSection("日常生活") {
            ThinnRow("我的事項","新增、完成與安排提醒",Icons.Rounded.Checklist,onClick=onTasks)
            GlassDivider()
            ThinnRow("智慧天氣定時通知","最多 5 組週間與時間提醒",Icons.Rounded.NotificationsActive,onClick=onBriefings)
        }
        Spacer(Modifier.height(8.dp))
        ThinnSection("天氣與安全") {
            ThinnRow("臺灣地震監測","即時觀測與歷史地震",Icons.Rounded.Waves,onClick=onSeismic)
            GlassDivider()
            ThinnRow("系統更新中心","版本內容與安全更新",Icons.Rounded.SystemUpdate,onClick=onUpdates)
            GlassDivider()
            ThinnRow("系統健康檢查","通知、定位與排程狀態",Icons.Rounded.FactCheck,onClick=onDiagnostics)
        }
        Spacer(Modifier.height(20.dp))
        Text("地震警示與演示會明確區別；災害資訊請以官方發布為準。",color=soft,fontSize=14.sp,lineHeight=18.sp)
    }
}

@Composable
fun AppGlassSettingsPage(appearance:WeatherAppearance,onAppearance:(WeatherAppearance)->Unit,
    automaticUpdates:Boolean,onUpdates:(Boolean)->Unit,onAdvanced:()->Unit,onDiagnostics:()->Unit,
    onLifestyle:()->Unit,onPushSettings:()->Unit,onUpdateCenter:()->Unit={}) {
    val ctx=LocalContext.current
    val prefs=remember(ctx){ctx.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    var reduceMotion by remember {mutableStateOf(prefs.getBoolean("reduce_motion",false))}
    var reduceTransparency by remember {mutableStateOf(prefs.getBoolean("reduce_transparency",false))}
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A1D30),Color(0xFF254869))))
        .verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=57.dp,bottom=124.dp)) {
        Text("設定",color=ink,style=androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text("所有選項皆使用統一的導覽、觸感與視覺語言",fontSize=15.sp,color=soft)
        Spacer(Modifier.height(16.dp))
        ThinnSection("外觀與易讀性") {
            GlassToggle("降低透明度","加強文字與材質的對比",reduceTransparency) {
                reduceTransparency=it;prefs.edit().putBoolean("reduce_transparency",it).apply()
            }
            GlassDivider()
            GlassToggle("減少動態效果","簡化轉場、放大及移動動畫",reduceMotion) {
                reduceMotion=it;prefs.edit().putBoolean("reduce_motion",it).apply()
            }
            GlassDivider()
            GlassTrackingSlider("玻璃透明感",appearance.glassOpacity) {onAppearance(appearance.copy(glassOpacity=it))}
            GlassTrackingSlider("玻璃霧化",appearance.glassFrost) {onAppearance(appearance.copy(glassFrost=it))}
            GlassTrackingSlider("邊緣光效",appearance.edgeLight) {onAppearance(appearance.copy(edgeLight=it))}
            GlassTrackingSlider("流動速度",appearance.liquidMotion) {onAppearance(appearance.copy(liquidMotion=it))}
        }
        Spacer(Modifier.height(8.dp))
        ThinnSection("個人偏好") {
            ThinnRow("智慧穿搭與開源 AI","穿搭、雨具與個人化選擇",Icons.Rounded.Checkroom,onClick=onLifestyle)
            GlassDivider()
            ThinnRow("進階視覺、環境音與觸感","主觸覺強度、音效、實境演示",Icons.Rounded.Tune,onClick=onAdvanced)
        }
        Spacer(Modifier.height(8.dp))
        ThinnSection("安全與版本") {
            ThinnRow("地震速報與推播安全","警報權限、測站及來源狀態",Icons.Rounded.Shield,onClick=onPushSettings)
            GlassDivider()
            GlassToggle("自動檢查更新","背景檢查時間由 Android 排定",automaticUpdates,onUpdates)
            GlassDivider()
            ThinnRow("系統更新中心","查看版本、更新內容與下載進度",Icons.Rounded.SystemUpdate,onClick=onUpdateCenter)
            GlassDivider()
            ThinnRow("權限與健康檢查","背景、省電、定位及通知診斷",Icons.Rounded.FactCheck,onClick=onDiagnostics)
        }
        Spacer(Modifier.height(18.dp))
        Text("thinn-khì · Android 專用介面 · 以 iOS 互動原則重新設計",color=soft,fontSize=14.sp)
    }
}
