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
    val scale by animateFloatAsState(if(pressed&&!reduce)AppleMotion.pressedScale else 1f,
        animationSpec=if(reduce)tween(0) else if(pressed)tween(100,easing=AppleMotion.fluid) else spring(dampingRatio=AppleMotion.springDamping,stiffness=AppleMotion.springStiffness),label="選項回饋")
    Row(modifier.fillMaxWidth().heightIn(min=56.dp)
        .graphicsLayer{scaleX=scale;scaleY=scale;alpha=if(pressed&&!reduce)AppleMotion.pressedAlpha else 1f}
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
    NavTab("天氣",AppleNavSymbols.weather),
    NavTab("地震",AppleNavSymbols.quake),
    NavTab("工具",AppleNavSymbols.tools),
    NavTab("設定",AppleNavSymbols.settings)
)

@Composable
fun AppGlassNavigation(selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    // NEXT navigation remains aligned to the existing four main Pager pages.
    // Persist stable tool IDs rather than array positions so reordering never
    // changes the semantic destination of a tab or breaks a deep link.
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
        maxVisible=4,
        reduceMotion=prefs.getBoolean("reduce_motion",false),
        reduceTransparency=prefs.getBoolean("reduce_transparency",false),
        modifier=modifier.fillMaxWidth().padding(horizontal=20.dp).height(68.dp)
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
    onWeatherAlarms:()->Unit={},onTyphoons:()->Unit={},onNaturalVoice:()->Unit={}) {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE) }
    var reduceMotion by remember { mutableStateOf(prefs.getBoolean("reduce_motion",false)) }
    var reduceTransparency by remember { mutableStateOf(prefs.getBoolean("reduce_transparency",false)) }
    var appearanceOpen by remember { mutableStateOf(false) }
    if(appearanceOpen) {
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
        return
    }
    val palette=MaterialTheme.colorScheme
    var search by remember { mutableStateOf("") }
    val match:(String)->Boolean={ label -> search.isBlank() || label.contains(search.trim(),ignoreCase=true) }
    // Actual four-tab settings screen; reserve the dock safe area INSIDE scroll content.
    Column(Modifier.fillMaxSize().background(palette.background)
        .statusBarsPadding().navigationBarsPadding()
        .appleBouncingScroll().verticalScroll(rememberScrollState())
        .padding(horizontal=16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("設定",fontSize=34.sp,fontWeight=FontWeight.Bold,
            color=palette.onBackground,letterSpacing=(-.8).sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(43.dp)
            .clip(RoundedCornerShape(13.dp)).background(palette.surfaceVariant)
            .padding(horizontal=13.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search,null,Modifier.size(20.dp),tint=palette.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if(search.isEmpty()) Text("搜尋設定",fontSize=16.sp,color=palette.onSurfaceVariant)
                androidx.compose.foundation.text.BasicTextField(value=search,onValueChange={search=it},
                    singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(
                        color=palette.onSurface,fontSize=16.sp),modifier=Modifier.fillMaxWidth())
            }
            if(search.isNotEmpty()) Text("×",color=palette.onSurfaceVariant,fontSize=20.sp,
                modifier=Modifier.clickable{search=""})
        }
        Spacer(Modifier.height(20.dp))
        if(search.isBlank()) {
            // App identity only: no pretend Apple ID, mobile data or VPN settings.
            IOSV25SettingsGroup {
                IOSV25SettingsCell("thinn-khì","天氣・地震・日常提醒",Icons.Rounded.Cloud,
                    Color(0xFF5BA4CF),last=true,onClick=onDiagnostics)
            }
            Spacer(Modifier.height(20.dp))
        }
        if(listOf("外觀","自然語音","地震速報與推播安全","颱風與官方特報").any(match)) {
            IOSV25SettingsGroup {
                if(match("外觀")) IOSV25SettingsCell("外觀","深淺色、背景與玻璃效果",Icons.Rounded.Palette,
                    Color(0xFF007AFF),onClick={appearanceOpen=true})
                if(match("自然語音")) IOSV25SettingsCell("自然語音","播報聲線與試聽",Icons.Rounded.RecordVoiceOver,
                    Color(0xFFAF52DE),onClick=onNaturalVoice)
                if(match("地震速報與推播安全")) IOSV25SettingsCell("地震速報與推播安全","通知門檻與警報",Icons.Rounded.Shield,
                    Color(0xFFFF3B30),onClick=onPushSettings)
                if(match("颱風與官方特報")) IOSV25SettingsCell("颱風與官方特報","路徑、長浪與警特報",Icons.Rounded.Cyclone,
                    Color(0xFFFF9500),last=true,onClick=onTyphoons)
            }
            Spacer(Modifier.height(20.dp))
        }
        if(listOf("天氣鬧鐘","系統更新中心","權限與健康檢查","進階視覺、環境音與觸感").any(match)) {
            IOSV25SettingsGroup {
                if(match("天氣鬧鐘")) IOSV25SettingsCell("天氣鬧鐘","時間、重複與播報",Icons.Rounded.Alarm,
                    Color(0xFFFF9500),onClick=onWeatherAlarms)
                if(match("系統更新中心")) IOSV25SettingsCell("系統更新中心","真正的版本下載與驗證",Icons.Rounded.SystemUpdate,
                    Color(0xFF34C759),onClick=onUpdateCenter)
                if(match("權限與健康檢查")) IOSV25SettingsCell("權限與健康檢查","通知、定位與排程",Icons.Rounded.FactCheck,
                    Color(0xFF007AFF),onClick=onDiagnostics)
                if(match("進階視覺、環境音與觸感")) IOSV25SettingsCell("進階視覺、環境音與觸感","動畫、觸感與環境音",Icons.Rounded.Tune,
                    Color(0xFF5856D6),last=true,onClick=onAdvanced)
            }
            Spacer(Modifier.height(20.dp))
        }
        if(listOf("智慧穿搭與開源 AI","自動檢查更新").any(match)) {
            IOSV25SettingsGroup {
                if(match("智慧穿搭與開源 AI")) IOSV25SettingsCell("智慧穿搭與開源 AI","雨具與個人化建議",Icons.Rounded.Checkroom,
                    Color(0xFF8E8E93),onClick=onLifestyle)
                if(match("自動檢查更新")) IOSV25SettingsCell("自動檢查更新",
                    if(automaticUpdates) "開啟" else "關閉",
                    Icons.Rounded.SystemUpdate,Color(0xFF34C759),last=true,
                    onClick={onUpdates(!automaticUpdates)})
            }
        }
        Spacer(Modifier.height(27.dp))
        Text("thinn-khì V25",color=palette.onSurfaceVariant,fontSize=14.sp,
            modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        // This spacer scrolls above the FIXED Dock instead of the Dock obscuring rows.
        Spacer(Modifier.height(120.dp))
    }

}
