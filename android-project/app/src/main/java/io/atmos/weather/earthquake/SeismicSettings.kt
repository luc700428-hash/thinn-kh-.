package io.atmos.weather.earthquake

import io.atmos.weather.ui.brandColor

import androidx.compose.ui.platform.testTag
import io.atmos.weather.ui.appleBouncingScroll

import io.atmos.weather.ui.GlassDivider as HorizontalDivider
import io.atmos.weather.ui.GlassDropdownMenu as DropdownMenu
import io.atmos.weather.ui.GlassDropdownMenuItem as DropdownMenuItem
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassTextButton as TextButton
import io.atmos.weather.ui.GlassIconButton as IconButton
import io.atmos.weather.ui.GlassSwitch as Switch
import io.atmos.weather.ui.GlassSlider as Slider
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.WorkspacePageDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.R

/** Distinct seismic categories, not the eight ordinary weather notification tones. */
object SeismicAlertPolicy {
    enum class Scenario(val key: String, val title: String, val subtitle: String, val sound: Int, val available: Boolean = true) {
        EEW_FIRST("eew_first", "地震預警", "收到有效的新地震速報", R.raw.thinn_eq_eew_primary),
        EEW_URGENT("eew_urgent", "緊急地震速報", "來源發布警報時，使用獨立的緊急提醒", R.raw.thinn_eq_eew_urgent),
        EEW_UPDATE("eew_update", "預警更新", "同一地震事件發出新報次", R.raw.thinn_eq_eew_update),
        DETECT_WEAK("detect_weak", "檢知・弱", "第三方測站震度 1 以上 · 需連續三筆確認", R.raw.thinn_eq_detect_weak),
        DETECT_MEDIUM("detect_medium", "檢知・中", "第三方測站震度 2 以上 · 需連續三筆確認", R.raw.thinn_eq_detect_medium),
        DETECT_STRONG("detect_strong", "檢知・強", "第三方測站震度 4 以上 · 需連續三筆確認", R.raw.thinn_eq_detect_strong),
        GAL_MEDIUM("gal_medium", "加速度・中", "第三方測站大於等於 8 gal · 連續三筆確認", R.raw.thinn_eq_gal_medium),
        GAL_STRONG("gal_strong", "加速度・強", "第三方測站大於等於 200 gal · 連續三筆確認", R.raw.thinn_eq_gal_strong),
        REPORT("report", "震後地震報告", "震後報告通知；不是提前預警", R.raw.thinn_eq_report_signal)
    }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("thinn_seismic_v15", Context.MODE_PRIVATE)
    /** Sound style for warnings and reports: classic (original set) or one of the r18 styles. */
    val soundStyles = listOf("classic" to "經典", "bright" to "輕柔", "warm" to "溫暖", "modern" to "空靈")
    fun soundStyle(ctx: Context) = prefs(ctx).getString("seismic_sound_style", "classic")?.takeIf { s -> soundStyles.any { it.first == s } } ?: "classic"
    fun setSoundStyle(ctx: Context, style: String) { prefs(ctx).edit().putString("seismic_sound_style", style).apply(); ensureChannels(ctx) }
    /** The resource actually played for [s]; detection tones keep their dedicated sounds. */
    fun sound(ctx: Context, s: Scenario): Int = when (soundStyle(ctx) to s) {
        "bright" to Scenario.EEW_FIRST -> R.raw.thinn_quake_bright_first; "bright" to Scenario.EEW_URGENT -> R.raw.thinn_quake_bright_urgent
        "bright" to Scenario.EEW_UPDATE -> R.raw.thinn_quake_bright_update; "bright" to Scenario.REPORT -> R.raw.thinn_quake_bright_report
        "warm" to Scenario.EEW_FIRST -> R.raw.thinn_quake_warm_first; "warm" to Scenario.EEW_URGENT -> R.raw.thinn_quake_warm_urgent
        "warm" to Scenario.EEW_UPDATE -> R.raw.thinn_quake_warm_update; "warm" to Scenario.REPORT -> R.raw.thinn_quake_warm_report
        "modern" to Scenario.EEW_FIRST -> R.raw.thinn_quake_modern_first; "modern" to Scenario.EEW_URGENT -> R.raw.thinn_quake_modern_urgent
        "modern" to Scenario.EEW_UPDATE -> R.raw.thinn_quake_modern_update; "modern" to Scenario.REPORT -> R.raw.thinn_quake_modern_report
        else -> s.sound
    }
    private fun styleSuffix(ctx: Context, s: Scenario) = if (sound(ctx, s) == s.sound) "" else "_${soundStyle(ctx)}"
    fun soundEnabled(ctx: Context, s: Scenario) = prefs(ctx).getBoolean("scenario_sound_${s.key}", true)
    fun setSoundEnabled(ctx: Context, s: Scenario, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("scenario_sound_${s.key}", enabled).apply()
        ensureChannels(ctx)
    }
    fun alertEnabled(ctx: Context, s: Scenario): Boolean =
        s.available && prefs(ctx).getBoolean("scenario_alert_${s.key}",
            s !in setOf(Scenario.DETECT_WEAK,Scenario.DETECT_MEDIUM,Scenario.DETECT_STRONG,
                Scenario.GAL_MEDIUM,Scenario.GAL_STRONG))
    fun setAlertEnabled(ctx: Context, s: Scenario, enabled: Boolean) =
        prefs(ctx).edit().putBoolean("scenario_alert_${s.key}", enabled).apply()
    fun channelId(ctx: Context, s: Scenario): String =
        if(SeismicCriticalMode.applies(ctx,s))"thinn_eq_${s.key}_v28_critical_${if(soundEnabled(ctx,s))"audio" else "silent"}"+styleSuffix(ctx,s)
        else "thinn_eq_${s.key}_v27_${if (soundEnabled(ctx,s)) "audio" else "silent"}"+styleSuffix(ctx,s)

    /** Channel sound is immutable on Android O+. We select a distinct ID for on/off. */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        Scenario.entries.filter { it.available }.forEach { s ->
            val id = channelId(ctx, s)
            val critical=SeismicCriticalMode.applies(ctx,s)
            if (nm.getNotificationChannel(id) == null) {
                val oldBase="thinn_eq_${s.key}_v151_"
                val standardLegacy=if(critical)nm.getNotificationChannel("thinn_eq_${s.key}_v27_${if(soundEnabled(ctx,s))"audio" else "silent"}") else null
                val legacy=standardLegacy?:nm.getNotificationChannel(oldBase+(if(soundEnabled(ctx,s))"audio" else "silent"))
                    ?:nm.getNotificationChannel(oldBase+(if(soundEnabled(ctx,s))"silent" else "audio"))
                val importance = legacy?.importance ?: when(s) {
                    Scenario.EEW_FIRST, Scenario.EEW_URGENT, Scenario.EEW_UPDATE -> NotificationManager.IMPORTANCE_HIGH
                    else -> NotificationManager.IMPORTANCE_DEFAULT
                }
                val channel = NotificationChannel(id, "地震 · ${s.title}"+(if(critical)" · 緊急模式" else ""), importance).apply {
                    description = "第三方地震資料；警報並不保證送達，演練與實際事件須明確區分。可於 Android 通知設定修改。"
                    if (soundEnabled(ctx,s)) {
                        val stable=Uri.parse("android.resource://${ctx.packageName}/raw/${ctx.resources.getResourceEntryName(sound(ctx,s))}")
                        val attributes=if(critical)AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build() else attr
                        // A newly picked style always uses its own sound; only the classic set inherits older channel sounds.
                        setSound(if(styleSuffix(ctx,s).isNotEmpty())stable else if(standardLegacy!=null)standardLegacy.sound else if(legacy?.hasUserSetSound()==true)legacy.sound else stable, attributes)
                        enableVibration(legacy?.shouldVibrate()?:true)
                        vibrationPattern = legacy?.vibrationPattern ?: longArrayOf(0,230,140,310)
                    } else {
                        setSound(null, null)
                        enableVibration(false)
                    }
                }
                if(critical&&nm.isNotificationPolicyAccessGranted&&importance!=NotificationManager.IMPORTANCE_NONE)channel.setBypassDnd(true)
                runCatching{nm.createNotificationChannel(channel)}.onFailure{
                    channel.setBypassDnd(false);runCatching{nm.createNotificationChannel(channel)}
                }
            } else if(critical&&nm.isNotificationPolicyAccessGranted) {
                // A user-edited channel stays authoritative. Never delete/recreate to evade a block.
                val channel=nm.getNotificationChannel(id)
                if(channel.importance!=NotificationManager.IMPORTANCE_NONE&&!channel.canBypassDnd()) {
                    channel.setBypassDnd(true);runCatching{nm.createNotificationChannel(channel)}
                }
            }
        }
    }
}

/** Never dispatches a system earthquake alarm; only plays local sound samples. */
object SeismicPreviewPlayer {
    private var player: MediaPlayer? = null
    @Synchronized fun stop() { player?.runCatching { if (isPlaying) stop(); release() }; player = null }
    @Synchronized fun play(ctx: Context, scenario: SeismicAlertPolicy.Scenario): Boolean {
        stop()
        return runCatching {
            val next=MediaPlayer.create(ctx.applicationContext,SeismicAlertPolicy.sound(ctx,scenario)) ?: return@runCatching false
            next.setOnCompletionListener { p ->
                synchronized(SeismicPreviewPlayer) { if (player === p) player = null }
                p.release()
            }
            player=next
            next.start()
            true
        }.getOrDefault(false)
    }
}

private val ice:Color @Composable get()=MaterialTheme.colorScheme.primary
private val pale:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
private val blue get() = brandColor(0xFF113027)

/**
 * Earthquake settings, regrouped by what people actually want to change (DPIP-style):
 *  速報 – where I am, how strong before I am alerted, how I am alerted, and the voice;
 *  報告 – post-quake reports: notify, magnitude, tone, voice;
 *  監測 – the live connection, its health and the opt-in station detections;
 *  關於 – sources and limits.
 * Each alert type keeps its switch, its tone and its preview on ONE row instead of being split
 * across an "alerts" tab and a "sounds" tab.
 */
@Composable
fun EarthquakeSettingsPage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pref = remember(ctx) { ctx.getSharedPreferences("thinn_seismic_v15", Context.MODE_PRIVATE) }
    val realtime by ExpTechSeismicService.live.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var generalWatch by remember { mutableStateOf(pref.getBoolean("earthquake_foreground_watch", true)) }
    var autoStartRealtime by remember { mutableStateOf(pref.getBoolean("auto_start_realtime_on_app_open", true)) }
    var reportNotify by remember { mutableStateOf(pref.getBoolean("earthquake_report_notify", false)) }
    var eewVoice by remember { mutableStateOf(SeismicSpeech.enabled(ctx,"eew_voice_enabled")) }
    var reportVoice by remember { mutableStateOf(SeismicSpeech.enabled(ctx,"report_voice_enabled")) }
    var replayVoice by remember { mutableStateOf(SeismicSpeech.enabled(ctx,"replay_voice_enabled")) }
    var fullscreen by remember { mutableStateOf(pref.getBoolean("allow_seismic_full_screen",true)) }
    var critical by remember {mutableStateOf(SeismicCriticalMode.enabled(ctx))}
    var chosenCounty by remember { mutableStateOf(SeismicUserLocation.chosen(ctx)) }
    var locationMenu by remember { mutableStateOf(false) }
    var magnitude by remember { mutableFloatStateOf(pref.getFloat("report_min_magnitude", 4.5f)) }
    var minimum by remember{mutableStateOf(SeismicAlertThreshold.chosen(ctx))}
    var unknown by remember{mutableStateOf(SeismicAlertThreshold.unknownAllowed(ctx))}
    var stationsOpen by remember{mutableStateOf(false)}
    var torch by remember{mutableStateOf(SeismicTorch.enabled(ctx))}
    var presetRevision by remember{mutableIntStateOf(0)}
    fun applyPreset(name:String) {
        // DPIP-style quick presets; every switch stays individually editable afterwards.
        val all=SeismicAlertPolicy.Scenario.entries
        val on:Set<SeismicAlertPolicy.Scenario> = when(name) {
            "全部"->all.toSet()
            "僅緊急"->setOf(SeismicAlertPolicy.Scenario.EEW_FIRST,SeismicAlertPolicy.Scenario.EEW_URGENT)
            else->setOf(SeismicAlertPolicy.Scenario.EEW_FIRST,SeismicAlertPolicy.Scenario.EEW_URGENT,SeismicAlertPolicy.Scenario.EEW_UPDATE,SeismicAlertPolicy.Scenario.REPORT)
        }
        all.filter{it!=SeismicAlertPolicy.Scenario.REPORT}.forEach{SeismicAlertPolicy.setAlertEnabled(ctx,it,it in on)}
        val grade=when(name){"全部"->"1";"僅緊急"->"4";else->"3"}
        minimum=grade;SeismicAlertThreshold.choose(ctx,grade)
        reportNotify=SeismicAlertPolicy.Scenario.REPORT in on;pref.edit().putBoolean("earthquake_report_notify",reportNotify).putString("seismic_preset",name).apply()
        presetRevision++
    }
    var preset by remember{mutableStateOf(pref.getString("seismic_preset","")?:"")}
    val scroll=rememberScrollState()
    LaunchedEffect(tab){scroll.scrollTo(0)}
    DisposableEffect(Unit) { onDispose { SeismicPreviewPlayer.stop() } }
    val divider=MaterialTheme.colorScheme.onSurface.copy(alpha=.12f)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false),adaptiveContentWidth=null) {
        io.atmos.weather.ui.AdaptiveWindowProvider(Modifier.background(MaterialTheme.colorScheme.background)) {
        io.atmos.weather.ui.AdaptivePageFrame(maxContentWidth=1120.dp) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            io.atmos.weather.ui.IOSSettingsHeader("地震設定",onClose)
            Spacer(Modifier.height(8.dp))
            val monitorTabs=listOf("速報","報告","監測","關於")
            io.atmos.weather.next.NextReorderableToolDock(
                actions=monitorTabs.mapIndexed {i,title->io.atmos.weather.next.NextToolAction(title,title,
                    when(i){0->Icons.Rounded.Warning;1->Icons.Rounded.Description;2->Icons.Rounded.Sensors;else->Icons.Rounded.Info})},
                selectedId=monitorTabs[tab],onSelected={id->tab=monitorTabs.indexOf(id).coerceAtLeast(0);SeismicPreviewPlayer.stop()},
                onOrderChanged={},maxVisible=4,allowReorder=false,
                reduceMotion=io.atmos.weather.ui.rememberAppleReduceMotion(),
                reduceTransparency=io.atmos.weather.ui.rememberAppleReduceTransparency(),
                modifier=Modifier.align(Alignment.CenterHorizontally).widthIn(max=io.atmos.weather.ui.TabletDockGeometry.contentLength(4,androidx.compose.ui.platform.LocalDensity.current.fontScale).dp).fillMaxWidth().padding(horizontal=8.dp))
            Spacer(Modifier.height(6.dp))
            SeismicSettingsFlow(scroll,Modifier.weight(1f)) {
                key(presetRevision) {
                when(tab) {
                    0 -> {
                        SettingsPanel("速報能不能跳出來") {
                            SeismicDeliveryReadiness(fullScreen=true,criticalEnabled=critical)
                        }
                        SettingsPanel("快速設定") {
                            Text("一鍵套用常用組合，之後仍可逐項調整。",color=pale,fontSize=14.sp,lineHeight=20.sp)
                            Spacer(Modifier.height(10.dp))
                            io.atmos.weather.ui.GlassChoiceGroup(listOf("推薦","全部","僅緊急"),listOf("推薦","全部","僅緊急").indexOf(preset),
                                {i->val name=listOf("推薦","全部","僅緊急")[i];preset=name;applyPreset(name)})
                            Text(when(preset){"全部"->"震度 1 級以上、所有速報、測站檢知與報告都通知";"僅緊急"->"只在你這裡預估 4 級以上時跳出緊急速報";"推薦"->"震度 3 級以上的速報與更新，加上地震報告";else->"目前為自訂組合"},
                                color=pale,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.padding(top=8.dp))
                        }
                        SettingsPanel("我的位置") {
                            val location=SeismicUserLocation.current(ctx)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MyLocation,null,tint=ice)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(location?.name ?: "尚未取得位置",color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                                    Text(location?.source ?: "請在天氣首頁允許定位，或手動選擇縣市",color=pale,fontSize=14.sp,lineHeight=20.sp)
                                }
                                Box {
                                    OutlinedButton(onClick={locationMenu=true}) {
                                        Text(if(chosenCounty.isBlank()) "跟隨定位" else chosenCounty)
                                        Icon(Icons.Rounded.ArrowDropDown,null)
                                    }
                                    DropdownMenu(expanded=locationMenu,onDismissRequest={locationMenu=false}) {
                                        SeismicUserLocation.options().forEach { city ->
                                            DropdownMenuItem(text={Text(city)},onClick={
                                                val next=if(city=="跟隨手機定位") "" else city
                                                SeismicUserLocation.choose(ctx,next)
                                                chosenCounty=next; locationMenu=false
                                            })
                                        }
                                    }
                                }
                            }
                            Text("速報會依此位置估算你這裡的震度與抵達秒數。",color=pale,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.padding(top=8.dp))
                        }
                        SettingsPanel("提醒門檻") {
                            Text("已選擇：${SeismicDisplayFormat.symbol(minimum)} 級以上",fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.fillMaxWidth())
                            Text("你這裡的預估震度達到門檻，才會跳出速報畫面、通知與語音。",color=pale,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                SeismicAlertThreshold.grades.forEach{grade->
                                    val on=grade==minimum
                                    val fill=SeismicIntensityColors.color(grade)
                                    val grow by androidx.compose.animation.core.animateFloatAsState(if(on)1.08f else 1f,
                                        androidx.compose.animation.core.spring(.6f,500f),label="門檻色塊")
                                    Box(Modifier.size(44.dp).graphicsLayer{scaleX=grow;scaleY=grow}.clip(RoundedCornerShape(12.dp))
                                        .background(if(on)fill else fill.copy(alpha=.22f))
                                        .border(if(on)2.dp else 0.dp,if(on)MaterialTheme.colorScheme.onSurface else Color.Transparent,RoundedCornerShape(12.dp))
                                        .clickable(role=androidx.compose.ui.semantics.Role.RadioButton){minimum=grade;SeismicAlertThreshold.choose(ctx,grade)}
                                        .semantics{contentDescription="${SeismicDisplayFormat.symbol(grade)} 級以上";selected=on},
                                        contentAlignment=Alignment.Center) {
                                        Text(SeismicDisplayFormat.symbol(grade),fontSize=16.sp,fontWeight=FontWeight.Bold,
                                            color=if(on)seismicInkOn(fill) else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            Text("最低 1 級 · 預設 3 級。震度與規模 M 不同。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                            HorizontalDivider(modifier=Modifier.padding(vertical=12.dp),color=divider)
                            SettingsSwitch("震度未知時也提醒","來源沒有你這裡的震度時，仍顯示速報並標示未知",unknown){unknown=it;pref.edit().putBoolean("eew_notify_unknown_local",it).apply()}
                        }
                        SettingsPanel("提醒方式") {
                            SettingsSwitch("勿擾時也響","速報以鬧鐘音量播放警報音與語音；手機開勿擾也聽得到（完全靜音除外）",critical) {
                                critical=it;SeismicCriticalMode.setEnabled(ctx,it);if(it)fullscreen=true
                            }
                            HorizontalDivider(modifier=Modifier.padding(vertical=12.dp),color=divider)
                            SettingsSwitch("鎖定畫面時全螢幕顯示", "達門檻時喚醒螢幕，需系統授權",fullscreen) {
                                fullscreen=it;pref.edit().putBoolean("allow_seismic_full_screen",it).apply()
                            }
                            HorizontalDivider(modifier=Modifier.padding(vertical=12.dp),color=divider)
                            SettingsSwitch("速報語音播報", "播報你這裡的震度、秒數與避難指引",eewVoice) {
                                eewVoice=it;SeismicSpeech.setEnabled(ctx,"eew_voice_enabled",it)
                            }
                            HorizontalDivider(modifier=Modifier.padding(vertical=12.dp),color=divider)
                            SettingsSwitch("強震時閃爍手電筒","你這裡預估 4 級以上時閃爍閃光燈，最多 30 秒",torch) {
                                torch=it;SeismicTorch.setEnabled(ctx,it)
                            }
                        }
                        SettingsPanel("速報種類與提示音") {
                            var quakeStyle by remember{mutableStateOf(SeismicAlertPolicy.soundStyle(ctx))}
                            Text("提示音風格",color=MaterialTheme.colorScheme.onSurface,fontSize=16.sp,fontWeight=FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            io.atmos.weather.ui.GlassChoiceGroup(SeismicAlertPolicy.soundStyles.map{it.second},SeismicAlertPolicy.soundStyles.indexOfFirst{it.first==quakeStyle},
                                {i->quakeStyle=SeismicAlertPolicy.soundStyles[i].first;SeismicAlertPolicy.setSoundStyle(ctx,quakeStyle)
                                    SeismicPreviewPlayer.play(ctx,SeismicAlertPolicy.Scenario.EEW_FIRST)})
                            Text("速報、緊急速報、更新與地震報告會一起換成這個風格；點選即試聽。",color=pale,fontSize=14.sp,lineHeight=20.sp,modifier=Modifier.padding(top=8.dp))
                            HorizontalDivider(color=divider,modifier=Modifier.padding(vertical=12.dp))
                            val eew=listOf(SeismicAlertPolicy.Scenario.EEW_FIRST,SeismicAlertPolicy.Scenario.EEW_URGENT,SeismicAlertPolicy.Scenario.EEW_UPDATE)
                            eew.forEachIndexed { i,scenario ->
                                AlertRow(scenario)
                                if(i<eew.lastIndex) HorizontalDivider(color=divider, modifier=Modifier.padding(vertical=10.dp))
                            }
                        }
                        SettingsPanel("雲端推播（備援）") { io.atmos.weather.push.SeismicPushPanel() }
                    }
                    1 -> {
                        SettingsPanel("震後報告") {
                            SettingsSwitch("新地震報告通知", "中央氣象署發布報告後通知；不是提前預警", reportNotify) {
                                reportNotify=it; pref.edit().putBoolean("earthquake_report_notify",it).apply()
                            }
                            HorizontalDivider(modifier=Modifier.padding(vertical=12.dp),color=divider)
                            Text("只通知規模 M ${"%.1f".format(java.util.Locale.US,magnitude)} 以上",color=MaterialTheme.colorScheme.onSurface,fontSize=16.sp,fontWeight=FontWeight.Medium)
                            Slider(value=magnitude,onValueChange={magnitude=it},valueRange=3f..7f,steps=7,
                                onValueChangeFinished={pref.edit().putFloat("report_min_magnitude",magnitude).apply()})
                            Text("此門檻只影響震後報告，不影響即時速報。",color=pale,fontSize=14.sp,lineHeight=21.sp)
                            HorizontalDivider(modifier=Modifier.padding(vertical=12.dp),color=divider)
                            SettingsSwitch("報告語音播報", "收到新震後報告時播報",reportVoice) {
                                reportVoice=it;SeismicSpeech.setEnabled(ctx,"report_voice_enabled",it)
                            }
                        }
                        SettingsPanel("報告提示音") {
                            Text("固定音高的三聲電子提示，沒有音樂旋律。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp));SoundRow(SeismicAlertPolicy.Scenario.REPORT)
                        }
                        SettingsPanel("地震頁面") {
                            SettingsSwitch("自動更新地震報告", "地震頁開啟時約每 60 秒查詢新報告", generalWatch) {
                                generalWatch=it; pref.edit().putBoolean("earthquake_foreground_watch",it).apply()
                            }
                            HorizontalDivider(modifier=Modifier.padding(vertical=12.dp),color=divider)
                            SettingsSwitch("歷史回放語音", "回放時播報，並明確說明為歷史事件",replayVoice) {
                                replayVoice=it;SeismicSpeech.setEnabled(ctx,"replay_voice_enabled",it)
                            }
                        }
                    }
                    2 -> {
                        SettingsPanel("即時監測") {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end=10.dp)) {
                                    Text(if(realtime.enabled)"監測中" else "未啟動",color=if(realtime.enabled)seismicHealthyInk else MaterialTheme.colorScheme.onSurface,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                                    Text(SeismicFeedHealth.eewLabel(SeismicFeedHealth.eew(realtime.enabled,realtime.eewStatus)),color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                                }
                                Button(onClick={
                                    runCatching {
                                        if(realtime.enabled) ExpTechSeismicService.stop(ctx)
                                        else ExpTechSeismicService.start(ctx,promoteLive=true)
                                    }
                                }) { Text(if(realtime.enabled) "停止" else "啟動") }
                            }
                            Text("監測時會顯示常駐通知；Android 省電機制仍可能中斷連線。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth().padding(top=6.dp))
                            HorizontalDivider(color=divider,modifier=Modifier.padding(vertical=12.dp))
                            SettingsSwitch("開啟 APP 時自動監測", "會消耗網路與電量",autoStartRealtime) {
                                autoStartRealtime=it
                                pref.edit().putBoolean("auto_start_realtime_on_app_open",it).apply()
                                if(it) runCatching { ExpTechSeismicService.start(ctx) }
                                else runCatching { ExpTechSeismicService.stop(ctx) }
                            }
                        }
                        SettingsPanel("速報紀錄") {
                            if(realtime.recentIncidents.isEmpty())Text("最近沒有收到速報。",color=pale,fontSize=14.sp)
                            realtime.recentIncidents.take(10).forEachIndexed{i,event->
                                if(i>0)HorizontalDivider(color=divider,modifier=Modifier.padding(vertical=8.dp))
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(SeismicDisplayFormat.location(event.epicenter),color=MaterialTheme.colorScheme.onSurface,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                        Text("${java.text.SimpleDateFormat("MM/dd HH:mm:ss",java.util.Locale.TAIWAN).format(java.util.Date(event.issuedAtMs))} · 第 ${event.serial} 報",color=pale,fontSize=14.sp)
                                    }
                                    Text(when{event.test->"演練";event.cancelled->"已取消";else->SeismicDisplayFormat.magnitude(event.magnitude)},
                                        color=if(event.cancelled)pale else MaterialTheme.colorScheme.onSurface,fontSize=16.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                        SettingsPanel("測站檢知 · 進階") {
                            Row(Modifier.fillMaxWidth().clickable{stationsOpen=!stationsOpen},verticalAlignment=Alignment.CenterVertically) {
                                Text("第三方測站連續三筆達門檻時提醒，預設關閉。這不是地震預警。",color=pale,fontSize=14.sp,lineHeight=19.sp,modifier=Modifier.weight(1f))
                                Icon(if(stationsOpen)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,if(stationsOpen)"收合" else "展開",tint=pale)
                            }
                            if(stationsOpen) {
                                Spacer(Modifier.height(10.dp))
                                val stations=listOf(SeismicAlertPolicy.Scenario.DETECT_WEAK,SeismicAlertPolicy.Scenario.DETECT_MEDIUM,
                                    SeismicAlertPolicy.Scenario.DETECT_STRONG,SeismicAlertPolicy.Scenario.GAL_MEDIUM,
                                    SeismicAlertPolicy.Scenario.GAL_STRONG)
                                stations.forEachIndexed { i,scenario ->
                                    AlertRow(scenario)
                                    if(i<stations.lastIndex) HorizontalDivider(color=divider,modifier=Modifier.padding(vertical=8.dp))
                                }
                            }
                        }
                    }
                    else -> {
                        SettingsPanel("資料來源與安全") {
                            Text("thinn-khì · 臺灣即時地震監測",color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,fontWeight=FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("速報與測站：ExpTech 公開即時串流；地震報告：中央氣象署開放資料。此功能為獨立實作，並非官方 App。",color=pale,fontSize=14.sp,lineHeight=19.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("第三方資料與 Android 背景工作均無法保證即時送達。真正感覺搖晃時，請立刻趴下、掩護、穩住；保留手機官方災防告警。",color=ice,fontSize=14.sp,lineHeight=19.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("連線診斷\nEEW：${realtime.eewStatus}\nRTS：${realtime.rtsStatus}",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Text("音效受到 Android 通知權限、通知分類、靜音與勿擾模式管理。使用者可在系統設定改變每個分類的聲音。",color=pale,fontSize=14.sp,lineHeight=21.sp)
                        }
                    }
                }
                }
                Spacer(Modifier.fillMaxWidth().height(25.dp))
            }
        }
        }
        }
    }
}

@Composable private fun SettingsPanel(title:String,content:@Composable ColumnScope.()->Unit) {
    val width=LocalSeismicSettingsCellWidth.current
    Column((if(width==androidx.compose.ui.unit.Dp.Unspecified)Modifier.fillMaxWidth() else Modifier.width(width)).testTag("seismic-settings-panel-$title")){
        SectionName(title);SettingsGlass(content)
    }
}

/** Each panel's heading carries its own coloured symbol, as in the iOS Settings app. */
@Composable private fun SectionName(title:String) {
    val C=io.atmos.weather.ui.IOSColors
    val (icon,color)=when(title) {
        "速報能不能跳出來"->Icons.Rounded.VerifiedUser to C.green; "快速設定"->Icons.Rounded.AutoAwesome to C.indigo
        "我的位置"->Icons.Rounded.MyLocation to C.blue; "提醒門檻"->Icons.Rounded.Tune to C.orange
        "提醒方式"->Icons.Rounded.NotificationsActive to C.red; "速報種類與提示音"->Icons.Rounded.MusicNote to C.pink
        "雲端推播（備援）"->Icons.Rounded.Cloud to C.cyan; "震後報告"->Icons.Rounded.Description to C.blue
        "報告提示音"->Icons.Rounded.VolumeUp to C.pink; "地震頁面"->Icons.Rounded.Map to C.teal
        "即時監測"->Icons.Rounded.Sensors to C.green; "速報紀錄"->Icons.Rounded.History to C.gray
        "測站檢知 · 進階"->Icons.Rounded.Science to C.purple; else->Icons.Rounded.Info to C.gray
    }
    Row(Modifier.padding(top=18.dp,bottom=10.dp,start=4.dp),verticalAlignment=Alignment.CenterVertically) {
        io.atmos.weather.ui.IOSIconTile(icon,color,26.dp)
        Spacer(Modifier.width(9.dp))
        Text(title,color=MaterialTheme.colorScheme.onSurface,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
    }
}
@Composable private fun SettingsGlass(content:@Composable ColumnScope.()->Unit) {
    io.atmos.weather.ui.IOSV25SettingsGroup {
        Column(Modifier.padding(horizontal=16.dp,vertical=15.dp),content=content)
    }
}
@Composable private fun SettingsSwitch(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=10.dp)) {
            Text(title,color=MaterialTheme.colorScheme.onSurface,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium,modifier=Modifier.fillMaxWidth())
            Text(description,color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
        }
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
@Composable private fun AlertRow(s:SeismicAlertPolicy.Scenario) {
    val ctx=LocalContext.current
    var checked by remember(s) { mutableStateOf(SeismicAlertPolicy.alertEnabled(ctx,s)) }
    var sound by remember(s) { mutableStateOf(SeismicAlertPolicy.soundEnabled(ctx,s)) }
    Column(Modifier.fillMaxWidth()) {
        SettingsSwitch(s.title,s.subtitle,checked) {
            checked=it; SeismicAlertPolicy.setAlertEnabled(ctx,s,it)
        }
        if(checked) Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            val on=MaterialTheme.colorScheme.primary
            Text(if(sound)"提示音：開" else "提示音：靜音",fontSize=14.sp,fontWeight=FontWeight.SemiBold,
                color=if(sound)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier=Modifier.clip(RoundedCornerShape(999.dp)).background(if(sound)on else MaterialTheme.colorScheme.onSurface.copy(alpha=.08f))
                    .clickable(role=androidx.compose.ui.semantics.Role.Switch){sound=!sound;SeismicAlertPolicy.setSoundEnabled(ctx,s,sound)}
                    .padding(horizontal=12.dp,vertical=7.dp))
            Text("▶ 試聽",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=ice,
                modifier=Modifier.clip(RoundedCornerShape(999.dp)).background(ice.copy(alpha=.12f)).clickable(role=androidx.compose.ui.semantics.Role.Button){
                    if(!SeismicPreviewPlayer.play(ctx,s)) android.widget.Toast.makeText(ctx,
                        "無法播放試聽音，請檢查音量及音效資源",android.widget.Toast.LENGTH_SHORT).show()
                }.padding(horizontal=12.dp,vertical=7.dp))
        }
    }
}
@Composable private fun SoundRow(s:SeismicAlertPolicy.Scenario) {
    val ctx=LocalContext.current
    var checked by remember(s) { mutableStateOf(SeismicAlertPolicy.soundEnabled(ctx,s)) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Column(Modifier.weight(1f).padding(end=10.dp)) {
            Text(s.title,color=MaterialTheme.colorScheme.onSurface,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.fillMaxWidth())
            Text(s.subtitle,color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick={
            if(!SeismicPreviewPlayer.play(ctx,s)) android.widget.Toast.makeText(ctx,
                "無法播放試聽音，請檢查音量及音效資源",android.widget.Toast.LENGTH_SHORT).show()
        },contentPadding=PaddingValues(horizontal=8.dp,vertical=2.dp),modifier=Modifier.heightIn(min=44.dp)) {
            Icon(Icons.Rounded.PlayArrow,"試聽",modifier=Modifier.size(17.dp),tint=ice)
            Text("試聽",fontSize=14.sp,color=ice)
        }
        Switch(checked=checked,onCheckedChange={checked=it;SeismicAlertPolicy.setSoundEnabled(ctx,s,it)},
            modifier=Modifier.height(40.dp))
    }
}
