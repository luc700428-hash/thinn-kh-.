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
private val outline=Color.White.copy(alpha=.28f)

/** Content surfaces deliberately remain calm; only floating controls use the glass lens.
 * Honors reduce transparency, with emergency text always kept high contrast.
 */
@Composable
fun GlassSurface(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    val opaque=prefs.getBoolean("reduce_transparency",false)
    Column(modifier.clip(RoundedCornerShape(ThinnDesignTokens.radiusCard))
        .background(if(opaque) ThinnOpticalMaterial.opaque
            else ThinnOpticalMaterial.surface)
        .border(1.dp,Color.White.copy(alpha=.17f),RoundedCornerShape(ThinnDesignTokens.radiusCard))
        .padding(18.dp),content=content)
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
    // NEXT navigation remains aligned to the existing four main Pager pages.
    // Persist stable tool IDs rather than array positions so reordering never
    // changes the semantic destination of a tab or breaks a deep link.
    val context=LocalContext.current
    val feedback=LocalHapticEngine.current
    val prefs=remember(context) {context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    val canonical=listOf("weather","quake","typhoon","alarm","settings")
    val tabs=listOf(
        io.atmos.weather.next.NextToolAction("weather","天氣",Icons.Rounded.WbSunny),
        io.atmos.weather.next.NextToolAction("quake","地震",Icons.Rounded.Waves),
        io.atmos.weather.next.NextToolAction("typhoon","颱風",Icons.Rounded.Cyclone),
        io.atmos.weather.next.NextToolAction("alarm","鬧鐘",Icons.Rounded.Alarm),
        io.atmos.weather.next.NextToolAction("settings","設定",Icons.Rounded.Settings)
    )
    fun normalized(saved:List<String>):List<String> =
        saved.filter { it in canonical }.distinct() + canonical.filter { it !in saved }
    var toolOrder by remember(prefs) {
        mutableStateOf(normalized(prefs.getString("next_navigation_order",null)?.split(",") ?: emptyList()))
    }
    val orderedActions=toolOrder.mapNotNull { id -> tabs.firstOrNull { it.id == id } }
    io.atmos.weather.next.NextReorderableToolDock(
        actions=orderedActions,
        selectedId=canonical.getOrNull(selected),
        onSelected={ id ->
            val page=canonical.indexOf(id)
            if(page>=0) {
                if(page != selected) feedback?.play(HapticEngine.Event.TAP)
                onSelect(page)
            }
        },
        onOrderChanged={ changed ->
            val valid=normalized(changed)
            toolOrder=valid
            prefs.edit().putString("next_navigation_order",valid.joinToString(",")).apply()
        },
        maxVisible=5,
        reduceMotion=prefs.getBoolean("reduce_motion",false),
        reduceTransparency=prefs.getBoolean("reduce_transparency",false),
        modifier=modifier.fillMaxWidth().padding(horizontal=18.dp).height(74.dp)
    )
}

@Composable
fun AppGlassToolsPage(onBriefings:()->Unit,onUpdates:()->Unit,onDiagnostics:()->Unit,
    onSeismic:()->Unit,onTasks:()->Unit={},onWeatherAlarms:()->Unit={},onTyphoons:()->Unit={}) {
    Column(Modifier.fillMaxSize().background(ThinnOpticalMaterial.page)
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,top=18.dp,bottom=118.dp)) {
        Text("工具",color=ink,style=androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text("常用功能與你的生活事項",color=soft,fontSize=15.sp)
        Spacer(Modifier.height(17.dp))
        ThinnSection("日常生活") {
            ThinnRow("我的事項","新增、完成與安排提醒",Icons.Rounded.Checklist,onClick=onTasks)
            GlassDivider()
            ThinnRow("天氣鬧鐘","鬧鐘、重複星期與起床天氣語音",Icons.Rounded.Alarm,onClick=onWeatherAlarms)
            GlassDivider()
            ThinnRow("智慧天氣定時通知","最多 5 組週間與時間提醒",Icons.Rounded.NotificationsActive,onClick=onBriefings)
        }
        Spacer(Modifier.height(8.dp))
        ThinnSection("天氣與安全") {
            ThinnRow("臺灣地震監測","即時觀測與歷史地震",Icons.Rounded.Waves,onClick=onSeismic)
            GlassDivider()
            ThinnRow("颱風動向與警報","官方颱風警報、路徑及最新資訊",Icons.Rounded.Cyclone,onClick=onTyphoons)
        }
        Spacer(Modifier.height(8.dp))
        ThinnSection("系統與維護") {
            ThinnRow("系統更新中心","版本內容與安全更新",Icons.Rounded.SystemUpdate,onClick=onUpdates)
            GlassDivider()
            ThinnRow("系統健康檢查","通知、定位與排程狀態",Icons.Rounded.FactCheck,onClick=onDiagnostics)
        }
        Spacer(Modifier.height(20.dp))
        Text("地震警示與演示會明確區別；災害資訊請以官方發布為準。",color=soft,fontSize=14.sp,lineHeight=18.sp)
    }
}

/** iOS-inspired grouped preference controls, built with original Android Compose primitives. */
@Composable
private fun IosSettingsGroup(title:String, content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title,color=soft,fontSize=14.sp,modifier=Modifier.padding(start=13.dp,bottom=9.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
            .background(ThinnOpticalMaterial.surface)
            .border(1.dp,Color.White.copy(alpha=.15f),RoundedCornerShape(21.dp)),content=content)
    }
}

@Composable
private fun IosSettingsRow(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,
    tint:Color,detail:String="",onClick:()->Unit) {
    val haptic=LocalHapticEngine.current
    Row(Modifier.fillMaxWidth().heightIn(min=61.dp)
        .clickable { haptic?.play(HapticEngine.Event.TAP);onClick() }
        .padding(horizontal=17.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(tint),
            contentAlignment=Alignment.Center) {
            Icon(icon,contentDescription=null,tint=Color.White,modifier=Modifier.size(21.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(label,color=Color.White,fontSize=17.sp,modifier=Modifier.weight(1f))
        if(detail.isNotBlank()) {
            Text(detail,color=soft,fontSize=14.sp)
            Spacer(Modifier.width(7.dp))
        }
        Icon(Icons.Rounded.ChevronRight,contentDescription="開啟$label",
            tint=soft,modifier=Modifier.size(20.dp))
    }
}

@Composable
fun AppGlassSettingsPage(appearance:WeatherAppearance,onAppearance:(WeatherAppearance)->Unit,
    automaticUpdates:Boolean,onUpdates:(Boolean)->Unit,onAdvanced:()->Unit,onDiagnostics:()->Unit,
    onLifestyle:()->Unit,onPushSettings:()->Unit,onUpdateCenter:()->Unit={},
    onWeatherAlarms:()->Unit={},onTyphoons:()->Unit={},onNaturalVoice:()->Unit={},
    onWeather:()->Unit={},onTasks:()->Unit={},onBriefings:()->Unit={},onTools:()->Unit={}) {
    val ctx=LocalContext.current
    val prefs=remember(ctx){ctx.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    var reduceMotion by remember {mutableStateOf(prefs.getBoolean("reduce_motion",false))}
    var reduceTransparency by remember {mutableStateOf(prefs.getBoolean("reduce_transparency",false))}
    var showAppearance by remember {mutableStateOf(false)}
    var showAbout by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().background(ThinnOpticalMaterial.page)
        .statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(start=17.dp,end=17.dp,top=22.dp,bottom=124.dp)) {
        Text("設定",color=Color.White,fontSize=34.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(26.dp))
        IosSettingsGroup("APP 設定") {
            IosSettingsRow("天氣",Icons.Rounded.WbCloudy,Color(0xFF1689EE),onClick=onWeather)
            GlassDivider()
            IosSettingsRow("鬧鐘與聲音",Icons.Rounded.Alarm,Color(0xFFFFA112),onClick=onWeatherAlarms)
            GlassDivider()
            IosSettingsRow("通知與即時動態",Icons.Rounded.NotificationsActive,Color(0xFF6656CF),onClick=onPushSettings)
        }
        Spacer(Modifier.height(24.dp))
        IosSettingsGroup("顯示與觸覺") {
            IosSettingsRow("外觀與動態",Icons.Rounded.Tune,Color(0xFF8E8E93),
                detail=if(showAppearance) "收起" else "設定",onClick={showAppearance=!showAppearance})
            if(showAppearance) {
                GlassDivider()
                GlassToggle("降低透明度","提高文字對比",reduceTransparency) {
                    reduceTransparency=it;prefs.edit().putBoolean("reduce_transparency",it).apply()
                }
                GlassDivider()
                GlassToggle("減少動態效果","依偏好減少動態",reduceMotion) {
                    reduceMotion=it;prefs.edit().putBoolean("reduce_motion",it).apply()
                }
                GlassTrackingSlider("玻璃透明感",appearance.glassOpacity) {onAppearance(appearance.copy(glassOpacity=it))}
                GlassTrackingSlider("玻璃霧化",appearance.glassFrost) {onAppearance(appearance.copy(glassFrost=it))}
                GlassTrackingSlider("邊緣光效",appearance.edgeLight) {onAppearance(appearance.copy(edgeLight=it))}
                GlassTrackingSlider("流動速度",appearance.liquidMotion) {onAppearance(appearance.copy(liquidMotion=it))}
            }
            GlassDivider()
            IosSettingsRow("觸覺回饋",Icons.Rounded.Vibration,Color(0xFFFF375F),onClick=onAdvanced)
        }
        Spacer(Modifier.height(24.dp))
        IosSettingsGroup("隱私與資訊") {
            IosSettingsRow("資料來源與隱私",Icons.Rounded.Shield,Color(0xFF32ADE6),onClick=onDiagnostics)
            GlassDivider()
            IosSettingsRow("關於 thinn-khì",Icons.Rounded.Info,Color(0xFF8E8E93),onClick={showAbout=true})
        }
        Spacer(Modifier.height(24.dp))
        IosSettingsGroup("更多工具") {
            IosSettingsRow("我的事項",Icons.Rounded.Checklist,Color(0xFF3CAC8C),onClick=onTasks)
            GlassDivider()
            IosSettingsRow("天氣定時通知",Icons.Rounded.Notifications,Color(0xFF487AD7),onClick=onBriefings)
            GlassDivider()
            IosSettingsRow("自然語音",Icons.Rounded.RecordVoiceOver,Color(0xFFA15DD0),onClick=onNaturalVoice)
            GlassDivider()
            IosSettingsRow("颱風資訊",Icons.Rounded.Cyclone,Color(0xFF329CCE),onClick=onTyphoons)
            GlassDivider()
            IosSettingsRow("更多功能",Icons.Rounded.Apps,Color(0xFF64758E),onClick=onTools)
        }
        Spacer(Modifier.height(24.dp))
        IosSettingsGroup("系統與更新") {
            GlassToggle("自動檢查更新","僅檢查，不會擅自安裝",automaticUpdates,onUpdates)
            GlassDivider()
            IosSettingsRow("軟體更新",Icons.Rounded.SystemUpdate,Color(0xFF1689EE),onClick=onUpdateCenter)
            GlassDivider()
            IosSettingsRow("權限與健康檢查",Icons.Rounded.FactCheck,Color(0xFF8E8E93),onClick=onDiagnostics)
        }
        Spacer(Modifier.height(24.dp))
        Text("thinn-khì · 原生 Android · iOS 風格設計",color=soft,fontSize=14.sp,
            modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
    if(showAbout) androidx.compose.ui.window.Dialog(onDismissRequest={showAbout=false}) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(ThinnOpticalMaterial.opaque).padding(20.dp)) {
            Text("關於 thinn-khì",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("版本：${io.atmos.weather.BuildConfig.VERSION_NAME}",color=soft,fontSize=16.sp)
            Text("Android 套件：io.atmos.weather",color=soft,fontSize=14.sp)
            Spacer(Modifier.height(12.dp))
            Text("原生 Android 應用程式，以 Apple 的公開互動與介面設計原則為參考。",
                color=Color.White,fontSize=14.sp)
            Spacer(Modifier.height(18.dp))
            Text("完成",color=Color(0xFF76C9FF),fontSize=17.sp,
                modifier=Modifier.align(Alignment.End).clickable {showAbout=false}.padding(8.dp))
        }
    }
}
