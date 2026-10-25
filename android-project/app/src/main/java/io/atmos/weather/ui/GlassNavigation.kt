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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.atmos.weather.ui.AppleNavSymbols
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

private val ink:Color @Composable get()=MaterialTheme.colorScheme.onSurface
private val soft:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
private val outline=Color.White.copy(alpha=.28f)

/** Content surfaces deliberately remain calm; only floating controls use the glass lens.
 * Honors reduce transparency, with emergency text always kept high contrast.
 */
@Composable
fun GlassSurface(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    val opaque=prefs.getBoolean("reduce_transparency",false)
AppleGlassCard(modifier=modifier,radius=ThinnDesignTokens.radiusCard,
        contentPadding=PaddingValues(18.dp),content=content)
}

@Composable
fun GlassAction(title:String,subtitle:String="",modifier:Modifier=Modifier,onClick:()->Unit) {
    val feedback=LocalHapticEngine.current
    val context=LocalContext.current
    val reduce=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val interaction=remember {MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val highlight by androidx.compose.animation.animateColorAsState(
        if(pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        tween(if(pressed)80 else 250),label="選項按壓背景")
    Row(modifier.fillMaxWidth().heightIn(min=56.dp)
        .clip(RoundedCornerShape(ThinnDesignTokens.radiusRow)).background(highlight)
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
    NavTab("天氣",AppleNavSymbols.weather),
    NavTab("地震",AppleNavSymbols.quake),
    NavTab("工具",AppleNavSymbols.tools),
    NavTab("設定",AppleNavSymbols.settings)
)

@Composable
fun AppGlassNavigation(selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    // NEXT navigation remains aligned to the existing four main Pager pages.
    // Fixed canonical destinations; migrate away from the old saved ordering.
    val context=LocalContext.current
    val feedback=LocalHapticEngine.current
    val prefs=remember(context) {context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    val canonical=listOf("weather","quake","tools","settings")
    val tabs=listOf(
        io.atmos.weather.next.NextToolAction("weather","天氣",AppleNavSymbols.weather),
        io.atmos.weather.next.NextToolAction("quake","地震",AppleNavSymbols.quake),
        io.atmos.weather.next.NextToolAction("tools","工具",AppleNavSymbols.tools),
        io.atmos.weather.next.NextToolAction("settings","設定",AppleNavSymbols.settings)
    )
    LaunchedEffect(prefs) {prefs.edit().remove("next_navigation_order").apply()}
    io.atmos.weather.next.NextReorderableToolDock(
        actions=tabs,
        selectedId=canonical.getOrNull(selected),
        onSelected={ id ->
            val page=canonical.indexOf(id)
            if(page>=0) {
                if(page != selected) feedback?.play(HapticEngine.Event.TAP)
                onSelect(page)
            }
        },
        onOrderChanged={ _ -> /* Main tabs have a fixed canonical order. */ },
        maxVisible=4,
        allowReorder=false,
        reduceMotion=rememberAppleReduceMotion(),
        reduceTransparency=rememberAppleReduceTransparency(),
        modifier=modifier.fillMaxWidth().padding(horizontal=24.dp).height(68.dp)
    )
}

@Composable
fun AppGlassToolsPage(onBriefings:()->Unit,onUpdates:()->Unit,onDiagnostics:()->Unit,
    onSeismic:()->Unit,onTasks:()->Unit={},onWeatherAlarms:()->Unit={},onTyphoons:()->Unit={}) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding().padding(bottom=104.dp)
        .appleBouncingScroll().verticalScroll(rememberScrollState())
        .padding(start=16.dp,end=16.dp,top=18.dp,bottom=22.dp)) {
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

@Composable
fun AppGlassSettingsPage(appearance:WeatherAppearance,onAppearance:(WeatherAppearance)->Unit,
    automaticUpdates:Boolean,onUpdates:(Boolean)->Unit,onAdvanced:()->Unit,onDiagnostics:()->Unit,
    onLifestyle:()->Unit,onPushSettings:()->Unit,onUpdateCenter:()->Unit={},
    onWeatherAlarms:()->Unit={},onTyphoons:()->Unit={},onNaturalVoice:()->Unit={},onDetailVisibility:(Boolean)->Unit={}) {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE) }
    var reduceMotion by remember { mutableStateOf(prefs.getBoolean("reduce_motion",false)) }
    var reduceTransparency by remember { mutableStateOf(prefs.getBoolean("reduce_transparency",false)) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var accessibilityOpen by remember {mutableStateOf(false)}
    var lastDetail by remember {mutableStateOf("appearance")}
    val settingsScroll=rememberScrollState()
    val currentAutomatic=rememberBooleanSetting("check_updates_automatically",automaticUpdates)
    val detailsVisible=appearanceOpen || accessibilityOpen
    SideEffect {onDetailVisibility(detailsVisible)}
    DisposableEffect(Unit) {onDispose {onDetailVisibility(false)}}
    androidx.activity.compose.BackHandler(enabled=detailsVisible) {appearanceOpen=false;accessibilityOpen=false}
    val appearanceContent:@Composable ()->Unit = {
        IOS27AppearancePage(onBack={appearanceOpen=false},advancedSettings={
            ThinnSection("進階外觀與動態") {
                GlassToggle("降低透明度","加強文字對比",reduceTransparency) {
                    reduceTransparency=it; prefs.edit().putBoolean("reduce_transparency",it).apply()
                }
                GlassDivider()
                GlassToggle("減少動態效果","簡化轉場和位移",reduceMotion) {
                    reduceMotion=it; prefs.edit().putBoolean("reduce_motion",it).apply()
                }
                GlassDivider()
                GlassTrackingSlider("玻璃透明感",appearance.glassOpacity) { onAppearance(appearance.copy(glassOpacity=it)) }
                GlassTrackingSlider("玻璃霧化",appearance.glassFrost) { onAppearance(appearance.copy(glassFrost=it)) }
                GlassTrackingSlider("邊緣光效",appearance.edgeLight) { onAppearance(appearance.copy(edgeLight=it)) }
                GlassTrackingSlider("流動速度",appearance.liquidMotion) { onAppearance(appearance.copy(liquidMotion=it)) }
            }
        })
    }
    val palette=MaterialTheme.colorScheme
    var search by remember {mutableStateOf("")}
    var searching by remember {mutableStateOf(false)}
    val match:(String)->Boolean={search.isBlank() || it.contains(search.trim(),ignoreCase=true)}
    val accessibilityContent:@Composable ()->Unit = {
        Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal=16.dp),contentAlignment=Alignment.Center) {
                IOS27RoundControl("‹",{accessibilityOpen=false},Modifier.align(Alignment.CenterStart))
                Text("輔助使用",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
            }
            Column(Modifier.padding(16.dp)) {
                IOSV25SettingsGroup {
                    GlassToggle("減少動態效果","簡化轉場、掃光與位移",reduceMotion) {
                        reduceMotion=it;prefs.edit().putBoolean("reduce_motion",it).apply()
                    }
                    GlassDivider()
                    GlassToggle("降低透明度","以實色加強文字對比",reduceTransparency) {
                        reduceTransparency=it;prefs.edit().putBoolean("reduce_transparency",it).apply()
                    }
                }
            }
        }
    }
    AppleParallaxStack(nested=detailsVisible,onBack={appearanceOpen=false;accessibilityOpen=false},
        modifier=Modifier.fillMaxSize(),root={
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal=16.dp),contentAlignment=Alignment.Center) {
            Text("設定",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=palette.onSurface)
            Box(Modifier.align(Alignment.CenterEnd).size(44.dp).clip(RoundedCornerShape(9999.dp))
                .clickable {searching=!searching;if(!searching)search=""},contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.Search,"搜尋設定",Modifier.size(20.dp),tint=palette.onSurfaceVariant)
            }
        }
        Box(Modifier.fillMaxWidth().height(.5.dp).background(if(palette.background.red>.5f)
            ThinnDesignTokens.separatorLight else ThinnDesignTokens.separatorDark))
        Column(Modifier.fillMaxSize().appleBouncingScroll().verticalScroll(settingsScroll)
            .padding(horizontal=16.dp)) {
            if(searching) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(palette.surfaceVariant).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search,null,Modifier.size(18.dp),tint=palette.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if(search.isEmpty())Text("搜尋設定",fontSize=16.sp,color=palette.onSurfaceVariant)
                        androidx.compose.foundation.text.BasicTextField(search,{search=it},singleLine=true,
                            textStyle=androidx.compose.ui.text.TextStyle(color=palette.onSurface,fontSize=16.sp),
                            modifier=Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            val serviceRows=listOf(
                SettingsEntry("天氣資料",SettingsSymbols.weather,Color(0xFF34C759),onDiagnostics),
                SettingsEntry("地震警報",SettingsSymbols.notification,Color(0xFFFF3B30),onPushSettings),
                SettingsEntry("天氣鬧鐘",SettingsSymbols.alarm,Color(0xFFFF9500),onWeatherAlarms),
                SettingsEntry("自然語音",SettingsSymbols.voice,Color(0xFF007AFF),onNaturalVoice)
            ).filter {match(it.title)}
            if(serviceRows.isNotEmpty()) {
                IOSV25SettingsGroup {
                    serviceRows.forEachIndexed {index,row -> IOSV25SettingsCell(row.title,"",row.icon,row.color,
                        last=index==serviceRows.lastIndex,onClick=row.action)}
                }
                Spacer(Modifier.height(32.dp))
            }
            val mainRows=listOf(
                SettingsEntry("一般",SettingsSymbols.general,Color(0xFF8E8E93),onAdvanced),
                SettingsEntry("輔助使用",SettingsSymbols.accessibility,Color(0xFF007AFF),{lastDetail="accessibility";accessibilityOpen=true}),
                SettingsEntry("外觀與亮度",SettingsSymbols.display,Color(0xFF007AFF),{lastDetail="appearance";appearanceOpen=true}),
                SettingsEntry("背景與動態",SettingsSymbols.wallpaper,Color(0xFF32ADE6),onAdvanced),
                SettingsEntry("颱風與官方特報",SettingsSymbols.cyclone,Color(0xFF007AFF),onTyphoons),
                SettingsEntry("智慧穿搭",SettingsSymbols.lifestyle,Color(0xFF5856D6),onLifestyle),
                SettingsEntry("軟體更新",SettingsSymbols.update,Color(0xFF8E8E93),onUpdateCenter),
                SettingsEntry("權限與系統健康",SettingsSymbols.controls,Color(0xFF8E8E93),onDiagnostics)
            ).filter {match(it.title)}
            if(mainRows.isNotEmpty()) {
                IOSV25SettingsGroup {
                    mainRows.forEachIndexed {index,row -> IOSV25SettingsCell(row.title,"",row.icon,row.color,
                        last=index==mainRows.lastIndex,onClick=row.action)}
                }
                Spacer(Modifier.height(32.dp))
            }
            if(match("自動檢查更新")) IOSV25SettingsGroup {
                IOSV25SettingsCell("自動檢查更新","",SettingsSymbols.update,Color(0xFF34C759),
                    trailing=if(currentAutomatic)"開啟" else "關閉",last=true,onClick={onUpdates(!currentAutomatic)})
            }
            Spacer(Modifier.height(22.dp))
            Text("thinn-khì",color=palette.onSurfaceVariant,fontSize=12.sp,
                modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(120.dp))
        }
    }},detail={if(lastDetail=="appearance")appearanceContent() else accessibilityContent()})
}
private data class SettingsEntry(val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,
    val color:Color,val action:()->Unit)
