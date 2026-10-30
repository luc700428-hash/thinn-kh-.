package io.atmos.weather.earthquake
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
        "thinn_eq_${s.key}_v27_${if (soundEnabled(ctx,s)) "audio" else "silent"}"

    /** Channel sound is immutable on Android O+. We select a distinct ID for on/off. */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        Scenario.entries.filter { it.available }.forEach { s ->
            val id = channelId(ctx, s)
            if (nm.getNotificationChannel(id) == null) {
                val oldBase="thinn_eq_${s.key}_v151_"
                val legacy=nm.getNotificationChannel(oldBase+(if(soundEnabled(ctx,s))"audio" else "silent"))
                    ?:nm.getNotificationChannel(oldBase+(if(soundEnabled(ctx,s))"silent" else "audio"))
                val importance = legacy?.importance ?: when(s) {
                    Scenario.EEW_FIRST, Scenario.EEW_URGENT, Scenario.EEW_UPDATE -> NotificationManager.IMPORTANCE_HIGH
                    else -> NotificationManager.IMPORTANCE_DEFAULT
                }
                val channel = NotificationChannel(id, "地震 · ${s.title}", importance).apply {
                    description = "第三方地震資料；警報並不保證送達，演練與實際事件須明確區分。可於 Android 通知設定修改。"
                    if (soundEnabled(ctx,s)) {
                        val stable=Uri.parse("android.resource://${ctx.packageName}/raw/${ctx.resources.getResourceEntryName(s.sound)}")
                        setSound(if(legacy?.hasUserSetSound()==true)legacy.sound else stable, attr)
                        enableVibration(legacy?.shouldVibrate()?:true)
                        vibrationPattern = legacy?.vibrationPattern ?: longArrayOf(0,230,140,310)
                    } else {
                        setSound(null, null)
                        enableVibration(false)
                    }
                }
                nm.createNotificationChannel(channel)
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
            val next=MediaPlayer.create(ctx.applicationContext,scenario.sound) ?: return@runCatching false
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
private val blue = Color(0xFF0D2C48)

/** Earthquake-specific settings, separate from the weather app's eight ordinary tones. */
@Composable
fun EarthquakeSettingsPage(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pref = remember(ctx) { ctx.getSharedPreferences("thinn_seismic_v15", Context.MODE_PRIVATE) }
    val realtime by ExpTechSeismicService.live.collectAsState()
    var tab by remember { mutableIntStateOf(0) } // Show monitoring status before sound controls
    var generalWatch by remember { mutableStateOf(pref.getBoolean("earthquake_foreground_watch", true)) }
    var autoStartRealtime by remember { mutableStateOf(pref.getBoolean("auto_start_realtime_on_app_open", true)) }
    var reportNotify by remember { mutableStateOf(pref.getBoolean("earthquake_report_notify", false)) }
    var eewVoice by remember { mutableStateOf(SeismicSpeech.enabled(ctx,"eew_voice_enabled")) }
    var reportVoice by remember { mutableStateOf(SeismicSpeech.enabled(ctx,"report_voice_enabled")) }
    var replayVoice by remember { mutableStateOf(SeismicSpeech.enabled(ctx,"replay_voice_enabled")) }
    var fullscreen by remember { mutableStateOf(pref.getBoolean("allow_seismic_full_screen",false)) }
    var chosenCounty by remember { mutableStateOf(SeismicUserLocation.chosen(ctx)) }
    var locationMenu by remember { mutableStateOf(false) }
    var magnitude by remember { mutableFloatStateOf(pref.getFloat("report_min_magnitude", 4.5f)) }
    var minimum by remember{mutableStateOf(SeismicAlertThreshold.chosen(ctx))}
    var thresholdMenu by remember{mutableStateOf(false)}
    var unknown by remember{mutableStateOf(SeismicAlertThreshold.unknownAllowed(ctx))}
    val scroll=rememberScrollState()
    LaunchedEffect(tab){scroll.scrollTo(0)}
    DisposableEffect(Unit) { onDispose { SeismicPreviewPlayer.stop() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false),adaptiveContentWidth=null) {
        io.atmos.weather.ui.AdaptiveWindowProvider(Modifier.background(MaterialTheme.colorScheme.background)) {
        io.atmos.weather.ui.AdaptivePageFrame(maxContentWidth=840.dp) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            io.atmos.weather.ui.IOSSettingsHeader("地震監測設定",onClose)
            Spacer(Modifier.height(8.dp))
            val monitorTabs=listOf("一般","警報","音效","關於")
            io.atmos.weather.next.NextReorderableToolDock(
                actions=monitorTabs.mapIndexed {i,title->io.atmos.weather.next.NextToolAction(title,title,
                    when(i){0->Icons.Rounded.Tune;1->Icons.Rounded.Warning;2->Icons.Rounded.VolumeUp;else->Icons.Rounded.Info})},
                selectedId=monitorTabs[tab],onSelected={id->tab=monitorTabs.indexOf(id).coerceAtLeast(0);SeismicPreviewPlayer.stop()},
                onOrderChanged={},maxVisible=4,allowReorder=false,
                reduceMotion=io.atmos.weather.ui.rememberAppleReduceMotion(),
                reduceTransparency=io.atmos.weather.ui.rememberAppleReduceTransparency(),
                modifier=Modifier.padding(horizontal=16.dp).fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f).appleBouncingScroll().verticalScroll(scroll).padding(horizontal=20.dp)) {
                when(tab) {
                    0 -> {
                        SectionName("監測服務")
                        SettingsGlass {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end=10.dp)) {
                                    Text("即時串流監測",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                                    Text(SeismicFeedHealth.eewLabel(SeismicFeedHealth.eew(realtime.enabled,realtime.eewStatus)),color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                                    Text("前景串流監測會顯示常駐通知；Android 仍可能停止連線。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                                }
                                Button(onClick={
                                    runCatching {
                                        if(realtime.enabled) ExpTechSeismicService.stop(ctx)
                                        else ExpTechSeismicService.start(ctx,promoteLive=true)
                                    }
                                }) { Text(if(realtime.enabled) "停止" else "啟動") }
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.14f),modifier=Modifier.padding(vertical=12.dp))
                            SettingsSwitch("開啟 APP 後自動啟動即時監測", "每次開啟 APP 時嘗試建立前景監測連線；會消耗網路與電量，Android 仍可能停止服務",autoStartRealtime) {
                                autoStartRealtime=it
                                pref.edit().putBoolean("auto_start_realtime_on_app_open",it).apply()
                                if(it) runCatching { ExpTechSeismicService.start(ctx) }
                                else runCatching { ExpTechSeismicService.stop(ctx) }
                            }
                            Spacer(Modifier.height(10.dp))
                            SettingsSwitch("前景地震報告更新", "監測頁開啟時約每 60 秒查詢震後報告", generalWatch) {
                                generalWatch=it; pref.edit().putBoolean("earthquake_foreground_watch",it).apply()
                            }
                        }
                        SectionName("預警依據位置")
                        SettingsGlass {
                            val location=SeismicUserLocation.current(ctx)
                            Text("目前：${location?.name ?: "定位資料尚未取得"}",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                            Text(location?.source ?: "預設手機定位；請先在天氣首頁允許並取得 GPS 定位",color=pale,fontSize=14.sp)
                            Spacer(Modifier.height(7.dp))
                            Box {
                                OutlinedButton(onClick={locationMenu=true}) {
                                    Text(if(chosenCounty.isBlank()) "跟隨手機定位（預設）" else "手動：$chosenCounty")
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
                            Text("未手動選擇時優先使用最近 15 分鐘內的手機定位。無可靠定位就不宣稱預估當地震度。",color=pale,fontSize=14.sp)
                        }
                        SectionName("接收狀態")
                        SettingsGlass {SeismicDeliveryReadiness()}
                    }
                    1 -> {
                        SectionName("地震速報 · 所在地震度")
                        SettingsGlass {
                            Text("提醒門檻",fontSize=18.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.fillMaxWidth())
                            Text("所在地預估震度達門檻，才啟動速報畫面、通知與語音。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Box {
                                Button(onClick={thresholdMenu=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                                    Icon(Icons.Rounded.Check,null);Spacer(Modifier.width(7.dp))
                                    Text("已選擇：${SeismicDisplayFormat.symbol(minimum)} 級以上",modifier=Modifier.weight(1f));Icon(Icons.Rounded.ArrowDropDown,null)
                                }
                                DropdownMenu(expanded=thresholdMenu,onDismissRequest={thresholdMenu=false}) {
                                    SeismicAlertThreshold.grades.forEach{grade->DropdownMenuItem(text={Text((if(grade==minimum)"✓ " else "")+SeismicDisplayFormat.symbol(grade)+" 級以上"+(if(grade=="3")"（預設）" else ""))},onClick={
                                        minimum=grade;SeismicAlertThreshold.choose(ctx,grade);thresholdMenu=false
                                    })}
                                }
                            }
                            Text("最低 1 級 · 預設 3 級。震度與規模 M 不同。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                            HorizontalDivider(modifier=Modifier.padding(vertical=14.dp),color=MaterialTheme.colorScheme.outlineVariant)
                            SettingsSwitch("所在地震度未知時仍提醒","缺少來源震度或可靠位置時顯示『待來源提供』，不以其他縣市或規模代替",unknown){unknown=it;pref.edit().putBoolean("eew_notify_unknown_local",it).apply()}
                        }
                        SectionName("速報通知")
                        SettingsGlass {
                            listOf(SeismicAlertPolicy.Scenario.EEW_FIRST,SeismicAlertPolicy.Scenario.EEW_URGENT,SeismicAlertPolicy.Scenario.EEW_UPDATE).forEachIndexed { i,s ->
                                AlertRow(s)
                                if(i<2) HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.12f), modifier=Modifier.padding(vertical=10.dp))
                            }
                        }
                        SectionName("鎖定畫面與焦點通知")
                        SettingsGlass {
                            SettingsSwitch("允許鎖定時全螢幕警示", "只對有效且達提醒門檻的真實速報提出喚醒請求，需系統授權",fullscreen) {
                                fullscreen=it;pref.edit().putBoolean("allow_seismic_full_screen",it).apply()
                            }
                            Spacer(Modifier.height(6.dp))
                            SeismicDeliveryReadiness(fullScreen=true)
                        }
                        SectionName("震後報告 · 規模門檻")
                        SettingsGlass {
                            SettingsSwitch("震後報告提醒", "由背景工作定期查詢；無法提前預警", reportNotify) {
                                reportNotify=it; pref.edit().putBoolean("earthquake_report_notify",it).apply()
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("規模門檻：M ${"%.1f".format(java.util.Locale.US,magnitude)}",color=MaterialTheme.colorScheme.onSurface)
                            Slider(value=magnitude,onValueChange={magnitude=it},valueRange=3f..7f,steps=7,
                                onValueChangeFinished={pref.edit().putFloat("report_min_magnitude",magnitude).apply()})
                            Text("一般使用者不需要申請 CWA 授權碼；震後報告由公開的 ExpTech 資料來源查詢。",color=pale,fontSize=14.sp,lineHeight=21.sp)
                        }
                        SectionName("測站檢知 · 進階選項")
                        SettingsGlass {
                            Text("預設關閉。啟用後，只在第三方 RTS 測站資料連續三筆達門檻時發出本機觀測提醒；兩分鐘內不重複。這不是官方地震預警，也不能預測即將發生地震。",color=pale,fontSize=14.sp,lineHeight=19.sp)
                            Spacer(Modifier.height(10.dp))
                            listOf(SeismicAlertPolicy.Scenario.DETECT_WEAK,SeismicAlertPolicy.Scenario.DETECT_MEDIUM,
                                SeismicAlertPolicy.Scenario.DETECT_STRONG,SeismicAlertPolicy.Scenario.GAL_MEDIUM,
                                SeismicAlertPolicy.Scenario.GAL_STRONG).forEach { scenario ->
                                AlertRow(scenario)
                                HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.1f),modifier=Modifier.padding(vertical=7.dp))
                            }
                        }
                    }
                    2 -> {
                        SectionName("震後報告 · 短促提示音")
                        SettingsGlass {
                            Text("固定音高的三聲電子提示，沒有音樂旋律。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp));SoundRow(SeismicAlertPolicy.Scenario.REPORT)
                        }
                        SectionName("即時地震語音")
                        SettingsGlass {
                            SettingsSwitch("速報語音播報", "只唸來源確實提供的本地預估震度；其餘唸震央、規模及避難指引",eewVoice) {
                                eewVoice=it;SeismicSpeech.setEnabled(ctx,"eew_voice_enabled",it)
                            }
                            Spacer(Modifier.height(9.dp))
                            SettingsSwitch("震後報告語音播報", "收到新震後報告時播報；不宣稱第三方彙整為官方已驗證報告",reportVoice) {
                                reportVoice=it;SeismicSpeech.setEnabled(ctx,"report_voice_enabled",it)
                            }
                            Spacer(Modifier.height(9.dp))
                            SettingsSwitch("歷史回放語音", "播放歷史速報首報時語音提醒『這是歷史回放，不是現在發生地震』",replayVoice) {
                                replayVoice=it;SeismicSpeech.setEnabled(ctx,"replay_voice_enabled",it)
                            }
                        }
                        SectionName("即時速報音效")
                        SettingsGlass {
                            listOf(SeismicAlertPolicy.Scenario.EEW_FIRST,SeismicAlertPolicy.Scenario.EEW_URGENT,SeismicAlertPolicy.Scenario.EEW_UPDATE).forEachIndexed { index,s ->
                                SoundRow(s)
                                if(index<2) HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.12f),modifier=Modifier.padding(vertical=12.dp))
                            }
                        }
                        SectionName("測站觀測提示音")
                        SettingsGlass {
                            val items=listOf(SeismicAlertPolicy.Scenario.DETECT_WEAK,SeismicAlertPolicy.Scenario.DETECT_MEDIUM,SeismicAlertPolicy.Scenario.DETECT_STRONG,SeismicAlertPolicy.Scenario.GAL_MEDIUM,SeismicAlertPolicy.Scenario.GAL_STRONG)
                            items.forEachIndexed { index,s ->
                                SoundRow(s)
                                if(index<items.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.12f),modifier=Modifier.padding(vertical=12.dp))
                            }
                        }

                        Text("音效與檢知警報為獨立開關；需在『警報 › 測站檢知』同時啟用提醒。RTS 提醒屬第三方測站觀測、不是官方 EEW。",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.padding(12.dp))
                    }
                    else -> {
                        SectionName("資料來源與安全")
                        SettingsGlass {
                            Text("thinn-khì · 臺灣即時地震監測",color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,fontWeight=FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("EEW / RTS：ExpTech DPIP 公開即時串流；震後報告：ExpTech 公開 API。此功能為獨立實作，並非官方 TREM-Lite App。",color=pale,fontSize=14.sp,lineHeight=19.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("第三方資料與 Android 背景工作均無法保證即時送達。真正感覺搖晃時，請立刻趴下、掩護、穩住；保留手機官方災防告警。",color=ice,fontSize=14.sp,lineHeight=19.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("連線診斷\nEEW：${realtime.eewStatus}\nRTS：${realtime.rtsStatus}",color=pale,fontSize=14.sp,lineHeight=21.sp,modifier=Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            Text("音效受到 Android 通知權限、通知分類、靜音與勿擾模式管理。使用者可在系統設定改變每個分類的聲音。",color=pale,fontSize=14.sp,lineHeight=21.sp)
                        }
                    }
                }
                Spacer(Modifier.height(25.dp))
            }
        }
        }
        }
    }
}

@Composable private fun SectionName(title:String) {
    Text(title,color=ice,fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp,bottom=12.dp,start=4.dp))
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
    SettingsSwitch(s.title,s.subtitle,checked) {
        checked=it; SeismicAlertPolicy.setAlertEnabled(ctx,s,it)
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
