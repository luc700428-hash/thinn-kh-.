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
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private val ink:Color @Composable get()=LocalWeatherCardPalette.current?.ink?:MaterialTheme.colorScheme.onSurface
private val soft:Color @Composable get()=LocalWeatherCardPalette.current?.secondary?:MaterialTheme.colorScheme.onSurfaceVariant
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
        .iosJellyPress()
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
fun GlassTrackingSlider(title:String,value:Float,valueLabel:String?=null,onFinished:(()->Unit)?=null,change:(Float)->Unit) {
    val bounded=value.coerceIn(0f,1f)
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title,color=ink,fontSize=15.sp)
            Text(valueLabel?:"${(bounded*100f).roundToInt()}%",color=soft,fontSize=15.sp)
        }
        GlassSlider(bounded,change,onValueChangeFinished=onFinished,modifier=Modifier.fillMaxWidth()
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
fun AppGlassNavigation(selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier,vertical:Boolean=false,edgeSized:Boolean=false,darkBackdrop:Boolean?=null,
    minimized:Boolean=false,onExpand:()->Unit={},accessory:io.atmos.weather.next.NextToolAction?=null,onAccessory:()->Unit={}) {
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
        allowReorder=false,vertical=vertical,fillAvailableHeight=true,darkBackdrop=darkBackdrop,
        minimized=minimized,onExpand=onExpand,accessory=accessory,onAccessory=onAccessory,
        reduceMotion=rememberAppleReduceMotion(),
        reduceTransparency=rememberAppleReduceTransparency(),
        modifier=if(edgeSized)modifier else modifier.widthIn(max=(FloatingDockContent.length(4,androidx.compose.ui.platform.LocalDensity.current.fontScale)+32f).dp)
            .fillMaxWidth().padding(horizontal=16.dp).height(FloatingDockContent.thickness(androidx.compose.ui.platform.LocalDensity.current.fontScale).dp)
    )
}

@Composable
fun AppGlassToolsPage(onBriefings:()->Unit,onUpdates:()->Unit,onDiagnostics:()->Unit,
    onSeismic:()->Unit,onTasks:()->Unit={},onWeatherAlarms:()->Unit={},onTyphoons:()->Unit={},onRadar:()->Unit={},onMarine:()->Unit={},onSky:()->Unit={},onAlerts:()->Unit={},onInsights:()->Unit={},onUtilityOpen:()->Unit={},onLife:(String)->Unit={}) {
    val palette=MaterialTheme.colorScheme
    var query by rememberSaveable {mutableStateOf("")}
    var utility by rememberSaveable {mutableStateOf<String?>(null)}
    data class Entry(val title:String,val subtitle:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val color:Color,val action:()->Unit)
    data class EntryGroup(val title:String,val entries:List<Entry>)
    // iOS Settings style: every row has its own system colour and a white filled glyph.
    val C=IOSColors
    val groups=listOf(
        EntryGroup("日常生活",listOf(Entry("天氣鬧鐘","重複星期、鈴聲與稍後提醒",Icons.Rounded.Alarm,C.orange,onWeatherAlarms),
            Entry("我的事項","待辦清單與個人提醒",Icons.Rounded.Checklist,C.blue,onTasks),
            Entry("天氣提醒與通知","早晚報、智慧提醒、通勤與通知中心",Icons.Rounded.NotificationsActive,C.red,onBriefings))),
        EntryGroup("天氣與防災",listOf(Entry("中央氣象署警特報","App 內閱讀完整公告與警戒地區",Icons.Rounded.Warning,C.red,onAlerts),
            Entry("出門時段與生活指數","什麼時候出門最好、洗車晾衣、著涼與防曬",Icons.Rounded.Air,C.teal,onInsights),
            Entry("颱風動向","官方警報、路徑與預報時間軸",Icons.Rounded.Cyclone,C.indigo,onTyphoons),
            Entry("地震監測","測站、震後報告與演練",Icons.Rounded.Sensors,C.orange,onSeismic),
            Entry("降雨雷達","App 內操作近兩小時降雨回波",Icons.Rounded.Radar,C.green,onRadar))),
        EntryGroup("個人化與自動化",listOf(
            Entry("氣壓不適預警","偏頭痛、關節痛的氣壓急降預警",Icons.Rounded.Speed,C.purple,{onLife("pressure")}),
            Entry("行事曆天氣","有地點的行程旁直接看天氣",Icons.Rounded.CalendarMonth,C.red,{onLife("calendar")}),
            Entry("自訂天氣規則","條件成立才通知，像天氣版 IFTTT",Icons.Rounded.AutoAwesome,C.indigo,{onLife("rules")}),
            Entry("家人天氣關懷","家人那裡變天時提醒，一鍵傳關心小卡",Icons.Rounded.Favorite,C.pink,{onLife("family")}),
            Entry("天空浮水印相機","拍天空，自動壓上溫度、氣壓與地點",Icons.Rounded.PhotoCamera,C.gray,{onLife("snap")}),
            Entry("火車、高鐵與駕車模式","搭車看下一站天氣，開車看前方會不會下雨",Icons.Rounded.DirectionsCar,C.blue,{onLife("transit")}))),
        EntryGroup("防災與水情",listOf(
            Entry("停班停課速查","全台各縣市即時公告",Icons.Rounded.School,C.orange,{onLife("closure")}),
            Entry("水庫與水情警戒","水庫蓄水率、淹水與土石流警戒",Icons.Rounded.WaterDrop,C.cyan,{onLife("water")}),
            Entry("避難收容處所","離線也能查最近的避難地點",Icons.Rounded.MapsHomeWork,C.green,{onLife("shelter")}),
            Entry("防災包清單","物資檢查與到期提醒",Icons.Rounded.Backpack,C.brown,{onLife("kit")}),
            Entry("災難省電模式","黑底大字、關閉動畫，停電時用",Icons.Rounded.BatterySaver,C.graphite,{onLife("disaster")}))),
        EntryGroup("海洋與天文",listOf(Entry("海象與浪況","浪高、浪向、湧浪、海流與海溫",Icons.Rounded.Waves,C.blue,onMarine),
            Entry("日月與月相","月相、日月方位、日出日落與月出月沒",Icons.Rounded.DarkMode,C.indigo,onSky))),
        EntryGroup("時間與換算",listOf(Entry("碼錶","開始、暫停、分圈與重置",Icons.Rounded.Timer,C.orange,{onUtilityOpen();utility="碼錶"}),
            Entry("世界時鐘","五個城市，依時區自動處理夏令時間",Icons.Rounded.Public,C.graphite,{onUtilityOpen();utility="世界時鐘"}),
            Entry("氣象單位換算","溫度、風速、雨量、距離與氣壓",Icons.Rounded.SwapHoriz,C.mint,{onUtilityOpen();utility="氣象單位換算"})))
    )
    Box(Modifier.fillMaxSize()) {
    AdaptivePageFrame(Modifier.background(palette.background).imePadding(),maxContentWidth=1280.dp) {
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding()
        .appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(top=18.dp,bottom=workspaceBottomPadding(120.dp)),
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Column {
            Text("工具",color=palette.onSurface,fontSize=34.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
            Text("把每天要用的功能放在一起",color=palette.onSurfaceVariant,fontSize=14.sp)
        }
        Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(palette.surfaceVariant).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search,null,tint=palette.onSurfaceVariant,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if(query.isBlank())Text("搜尋工具",color=palette.onSurfaceVariant,fontSize=16.sp)
                androidx.compose.foundation.text.BasicTextField(query,{query=it},singleLine=true,
                    textStyle=androidx.compose.ui.text.TextStyle(color=palette.onSurface,fontSize=16.sp),modifier=Modifier.fillMaxWidth())
            }
        }
        val filtered=groups.map{group->group to group.entries.filter {query.isBlank() || (it.title+it.subtitle).contains(query.trim(),ignoreCase=true)}}
            .filter{it.second.isNotEmpty()}
        val count=filtered.sumOf{it.second.size}
        AdaptiveCardGrid(filtered.size,itemKey={filtered[it].first.title}) {index->
            val (group,shown)=filtered[index]
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(group.title,color=palette.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(horizontal=6.dp))
                IOSV25SettingsGroup {
                    shown.forEachIndexed {i,item ->IOSV25SettingsCell(item.title,"",item.icon,item.color,
                        last=i==shown.lastIndex,selected=LocalWorkspaceDetailTitle.current==item.title,onClick=item.action)}
                }
            }
        }
        if(count==0)Text("找不到符合的工具",color=palette.onSurfaceVariant,fontSize=15.sp)
    }
    }
    EverydayToolsSheet(utility,onDismiss={utility=null})
    }
}

@Composable
fun AppGlassSettingsPage(appearance:WeatherAppearance,onAppearance:(WeatherAppearance)->Unit,
    automaticUpdates:Boolean,onUpdates:(Boolean)->Unit,onAdvanced:()->Unit,onDiagnostics:()->Unit,
    onLifestyle:()->Unit,onPushSettings:()->Unit,onUpdateCenter:()->Unit={},
    onWeatherAlarms:()->Unit={},onTyphoons:()->Unit={},onDetailVisibility:(Boolean)->Unit={},onAdvancedCategory:(String)->Unit={onAdvanced()}) {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE) }
    var reduceMotion by remember { mutableStateOf(prefs.getBoolean("reduce_motion",false)) }
    var reduceTransparency by remember { mutableStateOf(prefs.getBoolean("reduce_transparency",false)) }
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var themeOpen by rememberSaveable {mutableStateOf(false)}
    var accessibilityOpen by rememberSaveable {mutableStateOf(false)}
    var surfacesOpen by rememberSaveable {mutableStateOf(false)}
    var dockOpen by rememberSaveable {mutableStateOf(false)}
    var sponsorOpen by rememberSaveable {mutableStateOf(false)}
    val dockDevice=LocalDevicePosture.current?.kind
    val showDockPosition=dockDevice==DeviceKind.TABLET||dockDevice==DeviceKind.FOLDABLE||
        (dockDevice==null&&LocalWorkspaceLayout.current.tablet)
    LaunchedEffect(showDockPosition){if(!showDockPosition)dockOpen=false}
    val dockPosition=rememberTabletDockPosition()
    var lastDetail by rememberSaveable {mutableStateOf("appearance")}
    val settingsScroll=rememberScrollState()
    val detailsVisible=themeOpen || appearanceOpen || accessibilityOpen || surfacesOpen || sponsorOpen || (dockOpen&&showDockPosition)
    SideEffect {onDetailVisibility(detailsVisible)}
    DisposableEffect(Unit) {onDispose {onDetailVisibility(false)}}
    androidx.activity.compose.BackHandler(enabled=detailsVisible) {appearanceOpen=false;themeOpen=false;accessibilityOpen=false;surfacesOpen=false;dockOpen=false;sponsorOpen=false}
    val appearanceContent:@Composable ()->Unit = {
        IOS27AppearancePage(onBack={appearanceOpen=false},advancedSettings={
            ThinnSection("進階外觀與動態") {
                var glassChoice by remember { mutableStateOf(prefs.getInt(GlassQuality.SETTING,0)) }
                Text("玻璃效果",fontSize=17.sp,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.padding(top=4.dp))
                Text(when(glassChoice){1->"完整液態玻璃：所有卡片都有厚玻璃折射，需要較強的手機"
                    2->"省電：玻璃改為半透明色塊，最流暢、最省電"
                    else->"自動：依手機效能選擇，畫面不順時會自動降低"},fontSize=14.sp,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=6.dp))
                IOSSegmentedControl(listOf("自動","完整","省電"),glassChoice,{glassChoice=it;GlassQuality.choose(context,it)})
                Spacer(Modifier.height(8.dp))
                GlassDivider()
                GlassToggle("降低透明度","加強文字對比",reduceTransparency) {
                    reduceTransparency=it; prefs.edit().putBoolean("reduce_transparency",it).apply()
                }
                GlassDivider()
                GlassToggle("減少動態效果","簡化轉場和位移",reduceMotion) {
                    reduceMotion=it; prefs.edit().putBoolean("reduce_motion",it).apply()
                }
                GlassDivider()
                FestivalSettings(prefs)
                GlassDivider()
                GlassTrackingSlider("玻璃透明感",appearance.glassOpacity) { onAppearance(appearance.copy(glassOpacity=it)) }
                GlassTrackingSlider("玻璃霧化",appearance.glassFrost) { onAppearance(appearance.copy(glassFrost=it)) }
                GlassTrackingSlider("邊緣光效",appearance.edgeLight) { onAppearance(appearance.copy(edgeLight=it)) }
                GlassTrackingSlider("流動速度",appearance.liquidMotion) { onAppearance(appearance.copy(liquidMotion=it)) }
                GlassTrackingSlider("回彈彈性",appearance.springiness) { onAppearance(appearance.copy(springiness=it)) }
            }
        })
    }
    val palette=MaterialTheme.colorScheme
    var search by rememberSaveable {mutableStateOf("")}
    var searching by rememberSaveable {mutableStateOf(false)}
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
    AdaptivePageFrame(Modifier.background(palette.background).imePadding(),maxContentWidth=1280.dp) {
    WorkspaceSettingsStack(nested=detailsVisible,onBack={appearanceOpen=false;themeOpen=false;accessibilityOpen=false;surfacesOpen=false;dockOpen=false;sponsorOpen=false},
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
            if(match("贊助 thinn-khì")) {
                Box(Modifier.iosAppear(0)) {
                    io.atmos.weather.support.SponsorSettingsCard {
                        appearanceOpen=false;themeOpen=false;accessibilityOpen=false;surfacesOpen=false;dockOpen=false
                        lastDetail="sponsor";sponsorOpen=true
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            val categories=listOf(
                "外觀與操作" to listOfNotNull(
                    if(showDockPosition)SettingsEntry("平板 Dock 位置",Icons.Rounded.Dock,IOSColors.gray,{lastDetail="dock";dockOpen=true}) else null,
                    SettingsEntry("外觀",Icons.Rounded.LightMode,IOSColors.blue,{lastDetail="appearance";appearanceOpen=true}),
                    SettingsEntry("主題顏色",Icons.Rounded.Palette,IOSColors.cyan,{lastDetail="theme";themeOpen=true}),
                    SettingsEntry("字體與閱讀",Icons.Rounded.TextFields,IOSColors.indigo,{onAdvancedCategory("外觀")}),
                    SettingsEntry("聲音與觸覺",Icons.Rounded.VolumeUp,IOSColors.pink,{onAdvancedCategory("聲音")}),
                    SettingsEntry("輔助使用",Icons.Rounded.AccessibilityNew,IOSColors.blue,{lastDetail="accessibility";accessibilityOpen=true})),
                "桌面與即時資訊" to listOf(
                    SettingsEntry("小工具與動態島",Icons.Rounded.Widgets,IOSColors.indigo,{lastDetail="surfaces";surfacesOpen=true})),
                "提醒與生活" to listOf(
                    SettingsEntry("地震警報與通知",Icons.Rounded.NotificationsActive,IOSColors.red,onPushSettings),
                    SettingsEntry("通知音效與背景提醒",Icons.Rounded.MusicNote,IOSColors.orange,{onAdvancedCategory("通知")}),
                    SettingsEntry("穿搭與生活偏好",Icons.Rounded.Checkroom,IOSColors.green,onLifestyle)),
                "系統與資料" to listOf(
                    SettingsEntry("資料更新頻率",Icons.Rounded.Sync,IOSColors.cyan,{onAdvancedCategory("資料")}),
                    SettingsEntry("軟體更新",Icons.Rounded.SystemUpdate,IOSColors.gray,onUpdateCenter),
                    SettingsEntry("權限與系統健康",Icons.Rounded.PanTool,IOSColors.blue,onDiagnostics))
            )
            val filtered=categories.map{(title,rows)->title to rows.filter{match(it.title)}}.filter{it.second.isNotEmpty()}
            val resultCount=filtered.sumOf{it.second.size}
            AdaptiveCardGrid(filtered.size,itemKey={filtered[it].first}) {index->
                val (title,shown)=filtered[index]
                Column {
                    Text(title,fontSize=14.sp,color=palette.onSurfaceVariant,modifier=Modifier.padding(start=16.dp,bottom=8.dp))
                    IOSV25SettingsGroup {
                        shown.forEachIndexed {index,row ->IOSV25SettingsCell(row.title,"",row.icon,row.color,
                            trailing=if(row.title=="平板 Dock 位置")dockPosition.title else "",last=index==shown.lastIndex,selected=LocalWorkspaceDetailTitle.current==row.title||
                                (detailsVisible&&row.title==when(lastDetail){"appearance"->"外觀";"theme"->"主題顏色";"surfaces"->"小工具與動態島";"dock"->"平板 Dock 位置";else->"輔助使用"}),
                            onClick={appearanceOpen=false;themeOpen=false;accessibilityOpen=false;surfacesOpen=false;dockOpen=false;sponsorOpen=false;row.action()})}
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            if(resultCount==0 && search.isNotBlank())Text("找不到符合的設定",fontSize=15.sp,color=palette.onSurfaceVariant,modifier=Modifier.padding(16.dp))
            Spacer(Modifier.height(22.dp))
            Text("thinn-khì",color=palette.onSurfaceVariant,fontSize=12.sp,
                modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(workspaceBottomPadding(if(LocalWorkspaceLayout.current.tablet)24.dp else 120.dp)))
        }
    }},detail={AdaptivePageFrame(maxContentWidth=760.dp){when(lastDetail){"theme"->ThemeColorPage{themeOpen=false};"appearance"->appearanceContent();"surfaces"->IOSDeviceSurfacesSettings{surfacesOpen=false};"dock"->TabletDockSettingsPage{dockOpen=false};"sponsor"->io.atmos.weather.support.SponsorPage{sponsorOpen=false};else->accessibilityContent()}}})
    }
}
private data class SettingsEntry(val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,
    val color:Color,val action:()->Unit)

/** 節慶彩蛋: on/off, sounds, and a one-minute preview of any festival. */
@Composable private fun FestivalSettings(prefs:android.content.SharedPreferences) {
    var effects by remember { mutableStateOf(prefs.getBoolean(io.atmos.weather.festival.FestivalDirector.SETTING,true)) }
    var sound by remember { mutableStateOf(prefs.getBoolean(io.atmos.weather.festival.FestivalSound.SETTING,true)) }
    GlassToggle("節慶彩蛋","聖誕雪花與聖誕老人、跨年倒數煙火、春節燈籠等",effects) {
        effects=it; prefs.edit().putBoolean(io.atmos.weather.festival.FestivalDirector.SETTING,it).apply()
        if(!it) io.atmos.weather.festival.FestivalDirector.stopPreview()
    }
    GlassDivider()
    GlassToggle("節慶音效","鈴鐺、聖誕歌、煙火聲；手機靜音時不播放",sound) {
        sound=it; prefs.edit().putBoolean(io.atmos.weather.festival.FestivalSound.SETTING,it).apply()
    }
    if(effects) {
        GlassDivider()
        Text("預覽節慶彩蛋",fontSize=17.sp,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.padding(top=8.dp))
        Text("點一下即可播放一分鐘",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=6.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),modifier=Modifier.padding(bottom=8.dp)) {
            io.atmos.weather.festival.Festival.entries.forEach { f ->
                val on=io.atmos.weather.festival.FestivalDirector.preview?.festival==f
                Box(Modifier.clip(RoundedCornerShape(9999.dp))
                    .background(if(on)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { if(on) io.atmos.weather.festival.FestivalDirector.stopPreview() else io.atmos.weather.festival.FestivalDirector.startPreview(f) }
                    .padding(horizontal=14.dp,vertical=8.dp)) {
                    Text(f.label,fontSize=14.sp,color=if(on)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
