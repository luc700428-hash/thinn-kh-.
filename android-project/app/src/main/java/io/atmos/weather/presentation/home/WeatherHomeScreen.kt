package io.atmos.weather.presentation.home

import io.atmos.weather.ui.thinnGlass
import io.atmos.weather.ui.lightningScreenEffect

import io.atmos.weather.ui.brandColor
import io.atmos.weather.ui.iosAppear
import io.atmos.weather.ui.iosJellyPress
import io.atmos.weather.ui.BrandPalette
import io.atmos.weather.ui.WeatherSkyPalette
import io.atmos.weather.ui.WeatherDayPhase
import io.atmos.weather.ui.WeatherPhysicsStyle
import io.atmos.weather.ui.LocalWeatherSceneInk
import io.atmos.weather.ui.weatherCollisionViewport
import io.atmos.weather.ui.IOSActivityIndicator as CircularProgressIndicator
import io.atmos.weather.ui.IOSLinearProgressIndicator as LinearProgressIndicator

import io.atmos.weather.ui.*
import io.atmos.weather.ui.appleBouncingScroll
import io.atmos.weather.ui.thinnBackdrop
import io.atmos.weather.ui.LocalWeatherCardPalette
import io.atmos.weather.ui.WeatherCardPalettes
import io.atmos.weather.ui.rememberWeatherCardPalette
import io.atmos.weather.ui.weatherCollisionSurface

import io.atmos.weather.ui.GlassDivider as HorizontalDivider
import io.atmos.weather.ui.GlassModalBottomSheet as ModalBottomSheet
import io.atmos.weather.ui.GlassAlertDialog as AlertDialog
import io.atmos.weather.ui.GlassOutlinedTextField as OutlinedTextField
import io.atmos.weather.ui.GlassButton as Button
import io.atmos.weather.ui.GlassOutlinedButton as OutlinedButton
import io.atmos.weather.ui.GlassTextButton as TextButton
import io.atmos.weather.ui.GlassIconButton as IconButton
import io.atmos.weather.ui.GlassSwitch as Switch
import io.atmos.weather.ui.GlassSlider as Slider
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import io.atmos.weather.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import io.atmos.weather.widgets.WidgetSync
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import io.atmos.weather.ui.ThinnDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import io.atmos.weather.haptics.HapticEngine
import io.atmos.weather.haptics.HapticSettings
import io.atmos.weather.haptics.LocalHapticEngine
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import io.atmos.weather.R
import io.atmos.weather.next.NextWeatherAmbience
import io.atmos.weather.next.NextWeatherBackdrop
import io.atmos.weather.next.WeatherScenes
import io.atmos.weather.briefing.WeatherBriefingSettingsPage
import io.atmos.weather.update.AppUpdatePage
import io.atmos.weather.earthquake.EarthquakeCenterPage
import io.atmos.weather.earthquake.SeismicPrimaryTab
import io.atmos.weather.ui.AppGlassNavigation
import io.atmos.weather.ui.AppGlassSettingsPage
import io.atmos.weather.ui.AppGlassToolsPage
import io.atmos.weather.ui.RadarThreeHourCard
import io.atmos.weather.ui.GlassTrackingSlider
import io.atmos.weather.ui.GlassToggle
import io.atmos.weather.demo.WeatherDemoDispatcher
import io.atmos.weather.demo.WeatherDemoEvent
import kotlinx.coroutines.flow.collectLatest
import io.atmos.weather.alerts.CwaWarningFeed
import io.atmos.weather.earthquake.RecentEarthquakeNoticeStore
import io.atmos.weather.earthquake.RecentEarthquakeNotice
import io.atmos.weather.permission.PermissionGuide
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.notifications.ThinnNotificationChannels
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.atmos.weather.domain.model.*
import io.atmos.weather.lifestyle.LifestyleAdviceCard
import io.atmos.weather.lifestyle.LifestyleAdvisor
import io.atmos.weather.lifestyle.LifestylePreferenceStore
import io.atmos.weather.lifestyle.OutingMode
import io.atmos.weather.lifestyle.LifestyleSettingsPage
import io.atmos.weather.domain.visual.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

private val White get()=WeatherCardPalettes.current.ink
private val TextSoft get()=WeatherCardPalettes.current.secondary
private val GlassShape = RoundedCornerShape(30.dp)

/** Center the temperature DIGITS; position the degree glyph on their right. */
@Composable
private fun CenteredTemperature(value: Int, appearance: WeatherAppearance) {
    var digitsWidthPx by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth().height(145.dp),contentAlignment=Alignment.Center) {
        Text("$value",style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"),
            fontSize=(104f).coerceIn(80f,126f).sp,lineHeight=133.sp,
            fontWeight=FontWeight.Normal,fontFamily=appearance.family,
            color=White,textAlign=TextAlign.Center,onTextLayout={ digitsWidthPx=it.size.width })
        Text("°",fontSize=(62f).coerceIn(48f,78f).sp,
            fontWeight=FontWeight.Light,color=White,
            modifier=Modifier.graphicsLayer {
                translationX=digitsWidthPx/2f+3.dp.toPx()
                translationY=-35.dp.toPx()
            })
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherHomeScreen(viewModel: WeatherViewModel, onLocate: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.weather
    val alarmContext = LocalContext.current
    // Cache time-stamped forecast facts for alarm-time narration. A greeting or stale
    // weather assertion must never be frozen into the home-screen cache.
    LaunchedEffect(snapshot, state.stale) {
        if (snapshot != null && !state.stale) {
            io.atmos.weather.alarm.WeatherAlarmSpeechCache.save(alarmContext, snapshot)
        }
    }
    val isNight = snapshot?.isDay == false
    val stormy = WeatherScenes.classify(snapshot?.condition,snapshot?.weatherCode,snapshot?.visibilityM) in setOf(io.atmos.weather.next.WeatherScene.THUNDERSTORM,io.atmos.weather.next.WeatherScene.SEVERE_THUNDERSTORM)
    val rainy = snapshot?.condition in setOf(WeatherCondition.RAIN,WeatherCondition.THUNDERSTORM,WeatherCondition.FREEZING_RAIN,WeatherCondition.HAIL)
    val heavyRain = snapshot?.weatherCode in setOf(65,67,82,95,96,99)
    val snow = (snapshot?.temperatureC ?: 100.0) <= 5.0 && snapshot?.condition in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)
    val hazy = (snapshot?.aerosolOpticalDepth ?: 0.0) >= .45 || snapshot?.condition in setOf(WeatherCondition.HAZE, WeatherCondition.DUST, WeatherCondition.DUST_STORM)
    val cityHour=runCatching{java.time.ZonedDateTime.now(ZoneId.of(snapshot?.timeZoneId?:"Asia/Taipei")).hour}.getOrDefault(12)
    val dayPhase=WeatherDayPhase.resolve(!isNight,cityHour)
    val nightBlend by animateFloatAsState(if(isNight)1f else 0f,
        animationSpec=if(io.atmos.weather.ui.rememberAppleReduceMotion())tween(0) else tween(1400),label="weather-day-night")
    val weatherSky=WeatherSkyPalette.create(dayPhase,nightBlend,if(stormy)1f else if(rainy).65f else if(hazy).4f else 0f)
    val blendTop=weatherSky.top;val blendMid=weatherSky.middle;val blendLow=weatherSky.bottom
    val darkAppearance=MaterialTheme.colorScheme.background.red<.5f
    val context = LocalContext.current
    val reduceCityMotion = remember(context) {
        context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)
            .getBoolean("reduce_motion",false)
    }
    var recentEarthquake by remember { mutableStateOf(RecentEarthquakeNoticeStore.current(context)) }
    LaunchedEffect(context) {
        while(true) {
            recentEarthquake=RecentEarthquakeNoticeStore.current(context)
            kotlinx.coroutines.delay(10_000L)
        }
    }
    val hapticEngine=remember(context) { HapticEngine(context) }
    var hapticSettings by remember { mutableStateOf(HapticSettings.load(context)) }
    hapticEngine.configure(hapticSettings)
    LaunchedEffect(hapticSettings) {
        delay(250L)
        hapticSettings.save(context)
    }
    var appearance by remember { mutableStateOf(WeatherAppearance.from(context)) }
    val appDensity = LocalDensity.current
    val appearanceScope=rememberCoroutineScope()
    val appearanceSaver=remember(context) {
        AppearanceSaveController(appearance,appearanceScope,{it.persist(context)},{WidgetSync.refreshCached(context)})
    }
    val latestAppearance by rememberUpdatedState(appearance)
    LaunchedEffect(appearance) {appearanceSaver.update(appearance)}
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        appearanceSaver.update(latestAppearance);appearanceSaver.flush()
    }
    DisposableEffect(appearanceSaver) {
        onDispose {appearanceSaver.update(latestAppearance);appearanceSaver.flush()}
    }
    val uriHandler = LocalUriHandler.current
    val previewScope = rememberCoroutineScope()
    val settingsPrefs = remember { context.getSharedPreferences("thinn_settings", android.content.Context.MODE_PRIVATE) }
    var automaticUpdateNotices by remember { mutableStateOf(settingsPrefs.getBoolean("check_updates_automatically",true)) }
    // Refresh official RSS while the app stays foregrounded. Background updates
    // remain limited by WorkManager scheduling and Android battery policy.
    val alertLifecycleOwner=LocalLifecycleOwner.current
    LaunchedEffect(alertLifecycleOwner) {
        alertLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                delay(5 * 60_000L)
                viewModel.refreshWarnings()
            }
        }
    }
    var locationOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.locationFeedback.phase) {
        when (state.locationFeedback.phase) {
            LocationPhase.SUCCESS -> {
                delay(1050L) // Let the user read the location confirmation before closing.
                locationOpen = false
                delay(2800L)
                viewModel.clearLocationFeedback(LocationPhase.SUCCESS)
            }
            else -> Unit
        }
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var advancedCategory by remember {mutableStateOf("聲音")}
    var settingsDetailOpen by remember {mutableStateOf(false)}
    var lifestyleSettingsOpen by remember { mutableStateOf(false) }
    var pushSettingsOpen by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    val fromBriefingNotice = remember { (context as? android.app.Activity)?.intent?.getBooleanExtra("open_briefing_settings",false) == true }
    var briefingOpen by remember { mutableStateOf(fromBriefingNotice) }
    val fromTaskNotification = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("open_tasks",false) == true
    }
    var tasksOpen by remember { mutableStateOf(fromTaskNotification) }
    LaunchedEffect(fromTaskNotification) {
        if(fromTaskNotification) (context as? android.app.Activity)?.intent?.removeExtra("open_tasks")
    }
    val fromEarthquakeNotification = remember { (context as? android.app.Activity)?.intent?.getBooleanExtra("open_earthquake_center",false) == true }
    var earthquakePageOpen by remember { mutableStateOf(false) }
    val mainPager = rememberPagerState(initialPage = if (fromEarthquakeNotification) 1 else 0, pageCount = { 4 })
    val weatherScrollState = rememberScrollState()
    val mainPagerScope = rememberCoroutineScope()
    var mainTabJob by remember {mutableStateOf<kotlinx.coroutines.Job?>(null)}
    var dockRequestedTab by remember {mutableStateOf<Int?>(null)}
    var dockMinimized by remember {mutableStateOf(false)}
    val dockMinimizeTravelPx=with(LocalDensity.current){28.dp.toPx()}
    // Changing page always shows the full bar again.
    LaunchedEffect(mainPager.currentPage){dockMinimized=false}
    val tabResetEpochs=remember {mutableStateListOf(0,0,0,0)}
    fun showMainTab(index:Int,exitDelay:Long=0L) {
        mainTabJob?.cancel()
        mainTabJob=mainPagerScope.launch {
            if(exitDelay>0)delay(exitDelay)
            if(reduceCityMotion)mainPager.scrollToPage(index.coerceIn(0,3))
            else mainPager.animateScrollToPage(index.coerceIn(0,3))
            dockRequestedTab=null
        }
    }
    LaunchedEffect(fromEarthquakeNotification) { if (fromEarthquakeNotification) (context as? android.app.Activity)?.intent?.removeExtra("open_earthquake_center") }
    val fromUpdateNotification = remember { (context as? android.app.Activity)?.intent?.getBooleanExtra("open_update_center",false) == true }
    var updatePageOpen by remember { mutableStateOf(fromUpdateNotification) }
    var typhoonPageOpen by remember { mutableStateOf(false) }
    var officialStormActive by remember { mutableStateOf(false) }
    var typhoonPrimaryUnavailable by remember { mutableStateOf(false) }
    var typhoonBackupUnavailable by remember { mutableStateOf(false) }
    var gdacsCyclones by remember { mutableStateOf<List<io.atmos.weather.typhoon.GdacsCyclone>>(emptyList()) }
    var verifiedTyphoonWarning by remember { mutableStateOf(io.atmos.weather.typhoon.TyphoonWarningStore.current(context)) }
    LaunchedEffect(context) {
        io.atmos.weather.alerts.CwaAlertWorker.checkNow(context)
        while(true) {
            verifiedTyphoonWarning=io.atmos.weather.typhoon.TyphoonWarningStore.current(context)
            delay(30_000L)
        }
    }
    // Official seven-BF wind-circle probabilities: only a real KML polygon may yield a value.
    val homeWindProbability by androidx.compose.runtime.produceState<String?>(
        initialValue=null,officialStormActive,verifiedTyphoonWarning,state.city.coordinate) {
        value=null
        if(officialStormActive || verifiedTyphoonWarning!=null) {
            while(true) {
                val snapshot=io.atmos.weather.typhoon.CwaWindProbability.read()
                value=snapshot?.at(state.city.coordinate.longitude,state.city.coordinate.latitude,state.city.name)
                kotlinx.coroutines.delay(20*60_000L)
            }
        }
    }
    var approachingStorm by remember { mutableStateOf<io.atmos.weather.typhoon.StormTimeline?>(null) }
    // Query official storm positions only while the weather tab is visible. When unavailable
    // fail closed (no invented typhoon); locally issued official warnings still show.
    LaunchedEffect(mainPager.currentPage) {
        if(mainPager.currentPage==0) {
            while(true) {
                officialStormActive = try {
                    val data=io.atmos.weather.earthquake.CwaOfficialGateway.read(context,"W-C0034-005")
                    typhoonPrimaryUnavailable=false
                    val tracks=io.atmos.weather.typhoon.OfficialStormTimeline.parse(data)
                    val approaching=tracks.firstOrNull {
                        io.atmos.weather.typhoon.TaiwanStormRelevance.isMovingTowardTaiwan(it)
                    }
                    approachingStorm=approaching
                    // Forecast updates change the card only; details open on an explicit tap.
                    // Only a CWA forecast trajectory that is heading toward Taiwan activates the tile.
                    // Only a CWA forecast trajectory that is heading toward Taiwan activates the tile.
                    approaching != null
                } catch (cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                  catch (_:Exception) {
                      typhoonPrimaryUnavailable=true
                      approachingStorm=null
                      false
                  }
                // Second, independent public feed. Failure is not equivalent to no storm.
                try {
                    gdacsCyclones=io.atmos.weather.typhoon.GdacsCycloneFeed.read()
                    typhoonBackupUnavailable=false
                } catch (cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                  catch (_:Exception) {
                    typhoonBackupUnavailable=true
                    gdacsCyclones=emptyList()
                }
                // 20-minute foreground poll; official issue cadence is independent.
                delay(20*60_000L)
            }
        }
    }
    val fromClockWidget=remember {(context as? android.app.Activity)?.intent?.getBooleanExtra("open_weather_alarms",false)==true}
    var weatherAlarmOpen by remember { mutableStateOf(fromClockWidget) }
    var marineOpen by remember {mutableStateOf(false)}
    var skyOpen by remember {mutableStateOf(false)}
    var insightsOpen by remember {mutableStateOf(false)}
    var alertsOpen by remember {mutableStateOf(false)}
    var alertTitle by remember {mutableStateOf<String?>(null)}
    var alertUrl by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(fromClockWidget) {
        if(fromClockWidget)(context as? android.app.Activity)?.intent?.removeExtra("open_weather_alarms")
    }
    LaunchedEffect(fromUpdateNotification) { if (fromUpdateNotification) (context as? android.app.Activity)?.intent?.removeExtra("open_update_center") }
    // Android predictive-back / gesture returns to the previous UI layer,
    // not to Activity.finish() while tools, seismic, settings or sheets are open.
    BackHandler(enabled=locationOpen || settingsOpen || lifestyleSettingsOpen ||
        pushSettingsOpen || diagnosticsOpen || briefingOpen || tasksOpen ||
        updatePageOpen || earthquakePageOpen || typhoonPageOpen || weatherAlarmOpen || marineOpen || skyOpen || alertsOpen || insightsOpen ||
        mainPager.currentPage != 0) {
        when {
            alertsOpen -> alertsOpen=false
            insightsOpen -> insightsOpen=false
            marineOpen -> marineOpen=false
            skyOpen -> skyOpen=false
            weatherAlarmOpen -> weatherAlarmOpen=false
            typhoonPageOpen -> typhoonPageOpen=false
            earthquakePageOpen -> earthquakePageOpen=false
            updatePageOpen -> updatePageOpen=false
            tasksOpen -> tasksOpen=false
            briefingOpen -> briefingOpen=false
            diagnosticsOpen -> diagnosticsOpen=false
            pushSettingsOpen -> pushSettingsOpen=false
            lifestyleSettingsOpen -> lifestyleSettingsOpen=false
            settingsOpen -> settingsOpen=false
            locationOpen -> locationOpen=false
            mainPager.currentPage != 0 -> showMainTab(0)
        }
    }
    var refreshRequested by remember { mutableStateOf(false) }
    var refreshInFlight by remember { mutableStateOf(false) }
    var refreshText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loading, state.error, refreshRequested) {
        if (refreshRequested && state.loading) refreshInFlight = true
        if (refreshRequested && refreshInFlight && !state.loading) {
            refreshRequested = false
            refreshInFlight = false
            refreshText = if (state.error == null) "天氣資訊已更新" else "更新失敗，請稍後再試"
        }
    }
    LaunchedEffect(refreshText) {
        if (refreshText != null) { delay(2300L); refreshText = null }
    }

    // Open-Meteo air-quality and forecast visibility are fetched in OpenMeteoProvider.
    // No account, extra API key, or duplicate network request is needed.
    var soundOn by remember { mutableStateOf(settingsPrefs.getBoolean("sound_enabled",true)) }
    var soundVolume by remember {mutableFloatStateOf(settingsPrefs.getFloat("sound_volume",.50f).takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.5f)}
    var soundPreviewActive by remember {mutableStateOf(false)}
    var soundRetryToken by remember {mutableIntStateOf(0)}
    LaunchedEffect(soundPreviewActive){if(soundPreviewActive){delay(8_000);soundPreviewActive=false}}
    LaunchedEffect(settingsPrefs){if(!settingsPrefs.contains("sound_enabled"))settingsPrefs.edit().putBoolean("sound_enabled",true).apply()}
    var refreshMinutes by remember { mutableIntStateOf(settingsPrefs.getInt("refresh_minutes", 30).coerceIn(5,120)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, refreshMinutes) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(refreshMinutes * 60_000L)
                viewModel.onIntent(WeatherIntent.Refresh)
            }
        }
    }
    val lightningFlash=remember{io.atmos.weather.ui.LightningFlashState()}
    val lightningAlpha=lightningFlash.alpha
    DisposableEffect(lightningFlash){onDispose{lightningFlash.stop()}}
    var lightningBolt by remember { mutableIntStateOf(0) }
    var thunderCue by remember { mutableIntStateOf(0) }
    // Lightning is not periodic: a single lifecycle-aware clock also schedules
    // the delayed thunder cue. Both sound and flash derive from one event.
    LaunchedEffect(lifecycleOwner, stormy) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (stormy) {
                while (isActive) {
                    delay(kotlin.random.Random.nextLong(9_000L, 24_000L))
                    lightningBolt=kotlin.random.Random.nextInt(1,Int.MAX_VALUE)
                    lightningFlash.trigger(this,lightningBolt).join()
                    // Lightning is seen before thunder; vary simulated distance.
                    delay(kotlin.random.Random.nextLong(400L, 1_800L))
                    thunderCue++
                    if (hapticSettings.enabled && hapticSettings.thunder) {
                        hapticEngine.play(HapticEngine.Event.THUNDER)
                    }
                }
            } else {
                lightningFlash.stop()
            }
        }
    }
    // Simulated weather-event bus: foreground UI/audio/haptics only.
    // Never generates a real CWA warning or a system emergency notification.
    var demoRemaining by remember { mutableIntStateOf(0) }
    var demoDescription by remember { mutableStateOf("") }
    var demoCondition by remember { mutableStateOf<WeatherCondition?>(null) }
    var demoThunderCue by remember { mutableIntStateOf(0) }
    var demoCompleted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        WeatherDemoDispatcher.events.collectLatest { request ->
            if (request == WeatherDemoEvent.Stop) {
                demoRemaining = 0
                demoCondition = null
                demoDescription = ""
                demoCompleted = false
            } else {
                demoCompleted = false
                try {
                    for (second in 10 downTo 1) {
                        demoRemaining = second
                        when(second) {
                            in 8..10 -> {
                                demoDescription = "毛毛雨逐漸形成 · 雨滴觸覺"
                                demoCondition = WeatherCondition.RAIN
                                hapticEngine.play(HapticEngine.Event.RAIN_DROP, preview=true, variation=.42f)
                            }
                            in 5..7 -> {
                                demoDescription = "雨勢加強 · 玻璃上的雨滴"
                                demoCondition = WeatherCondition.RAIN
                                hapticEngine.play(HapticEngine.Event.RAIN_DROP, preview=true, variation=.88f)
                            }
                            in 2..4 -> {
                                demoDescription = "雷雨接近 · 閃電與雷聲"
                                demoCondition = WeatherCondition.THUNDERSTORM
                                if(second == 4 || second == 2) {
                                    lightningBolt=kotlin.random.Random.nextInt(1,Int.MAX_VALUE)
                                    lightningFlash.trigger(this,lightningBolt)
                                    demoThunderCue += 1
                                    hapticEngine.play(HapticEngine.Event.THUNDER, preview=true, variation=.85f)
                                } else {
                                    hapticEngine.play(HapticEngine.Event.RAIN_DROP, preview=true, variation=.62f)
                                }
                            }
                            else -> {
                                demoDescription = "雨勢漸緩 · 天氣恢復"
                                demoCondition = WeatherCondition.RAIN
                                lightningFlash.stop()
                                hapticEngine.play(HapticEngine.Event.RELEASE, preview=true, variation=.48f)
                            }
                        }
                        delay(1000L)
                    }
                    demoCompleted = true
                } finally {
                    demoRemaining = 0
                    demoCondition = null
                    demoDescription = ""
                    lightningFlash.stop()
                }
            }
        }
    }
    LaunchedEffect(demoCompleted) {
        if (demoCompleted) { delay(2500L); demoCompleted = false }
    }
    val ambientPlayback=NextWeatherAmbience(demoCondition ?: snapshot?.condition ?: if(soundPreviewActive)WeatherCondition.CLEAR else null, soundOn || demoRemaining>0 || soundPreviewActive,
        if(demoRemaining>0)soundVolume.coerceAtLeast(.65f) else soundVolume,
        if (demoCondition == WeatherCondition.THUNDERSTORM) 95 else if (demoCondition == WeatherCondition.RAIN) 61 else snapshot?.weatherCode,
        snapshot?.visibilityM, thunderCue + demoThunderCue,isNight=snapshot?.isDay==false,retryToken=soundRetryToken)
    var nativeRadarOpen by remember {mutableStateOf(false)}
    var reportOpen by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Pair<String,String>?>(null) }
    var showLifestyleDetails by remember { mutableStateOf(false) }
    val reducedMotion = io.atmos.weather.ui.rememberAppleReduceMotion()
    val weatherSceneActive=mainPager.currentPage==0&&!settingsOpen&&!settingsDetailOpen&&!lifestyleSettingsOpen&&
        !pushSettingsOpen&&!diagnosticsOpen&&!briefingOpen&&!tasksOpen&&!updatePageOpen&&
        !earthquakePageOpen&&!typhoonPageOpen&&!weatherAlarmOpen&&!marineOpen&&!skyOpen&&!alertsOpen&&!insightsOpen&&!nativeRadarOpen
    // Keep the animation value as State. Reading it in this parent would recompose the
    // complete forecast screen every frame (including scrolling and lists).
    val motionState: State<Float> = if(reducedMotion||!weatherSceneActive) rememberUpdatedState(0f)
        else rememberInfiniteTransition(label="sky").animateFloat(0f,1f,infiniteRepeatable(
            tween((18000 - appearance.liquidMotion * 13000).toInt(), easing=LinearEasing)),label="weather motion")
    // At most a handful of impacts are sampled, tied to actual particle wraps.
    // Screen weather audio and haptic effects stop when this lifecycle stops.
    LaunchedEffect(lifecycleOwner, snapshot?.condition, heavyRain,
        hapticSettings.enabled, hapticSettings.rainDrops, hapticSettings.thunder,weatherSceneActive) {
        if(!weatherSceneActive)return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val raining = snapshot?.condition in setOf(WeatherCondition.RAIN,WeatherCondition.THUNDERSTORM)
            var previousTime = RainParticles.secondsNow()
            val samples = if (heavyRain) intArrayOf(7,23,49,83,111) else intArrayOf(7,23,49)
            while (isActive) {
                val now = RainParticles.secondsNow()
                if (raining && hapticSettings.enabled && hapticSettings.rainDrops) {
                    for (i in samples) {
                        val d=RainParticles.drop(i,heavyRain)
                        if (RainParticles.cycles(d,now) > RainParticles.cycles(d,previousTime)) {
                            hapticEngine.rainImpact(d.depth)
                            break
                        }
                    }
                }
                previousTime = now
                delay(110L)
            }
        }
    }

    val rootWindow=currentAppWindowLayout()
    val tabletWindow=LocalDevicePosture.current?.kind!=DeviceKind.PHONE&&rootWindow.width>=600f
    fun clearWorkspaceDetails() {
        marineOpen=false;skyOpen=false;alertsOpen=false;insightsOpen=false
        typhoonPageOpen=false;earthquakePageOpen=false;tasksOpen=false;briefingOpen=false
        diagnosticsOpen=false;pushSettingsOpen=false;lifestyleSettingsOpen=false;settingsOpen=false
        locationOpen=false;weatherAlarmOpen=false;updatePageOpen=false;nativeRadarOpen=false
        showLifestyleDetails=false;details=null;settingsDetailOpen=false
    }
    fun openWorkspaceDetail(name:String,category:String?=null) {
        clearWorkspaceDetails()
        when(name) {
            "marine"->marineOpen=true;"sky"->skyOpen=true;"alerts"->{alertTitle=null;alertUrl=null;alertsOpen=true}
            "insights"->insightsOpen=true;"typhoon"->typhoonPageOpen=true;"tasks"->tasksOpen=true
            "briefing"->briefingOpen=true;"diagnostics"->diagnosticsOpen=true;"push"->pushSettingsOpen=true
            "lifestyle"->lifestyleSettingsOpen=true;"alarm"->weatherAlarmOpen=true;"update"->updatePageOpen=true
            "radar"->nativeRadarOpen=true;"advanced"->{advancedCategory=category?:"聲音";settingsOpen=true}
        }
    }
    fun navigateWorkspace(requested:Int) {
        val leaving=marineOpen||skyOpen||alertsOpen||insightsOpen||typhoonPageOpen||earthquakePageOpen||
            tasksOpen||briefingOpen||diagnosticsOpen||pushSettingsOpen||lifestyleSettingsOpen||settingsOpen||
            settingsDetailOpen||weatherAlarmOpen||updatePageOpen||locationOpen||nativeRadarOpen
        clearWorkspaceDetails()
        val target=requested.coerceIn(0,3);dockRequestedTab=target
        if(!tabletWindow)setOf(mainPager.currentPage,target).forEach{tab->tabResetEpochs[tab]=tabResetEpochs[tab]+1}
        showMainTab(target,if(leaving&&!reducedMotion)160L else 0L)
    }
    val workspaceDetailTitle=when {
        marineOpen->"海象與浪況";skyOpen->"日月與月相";alertsOpen->"中央氣象署警特報";insightsOpen->"氣象詳解與外出時段"
        typhoonPageOpen->"颱風動向";tasksOpen->"我的事項";briefingOpen->"天氣提醒與通知";weatherAlarmOpen->"天氣鬧鐘"
        diagnosticsOpen->"權限與系統健康";pushSettingsOpen->"地震警報與通知";lifestyleSettingsOpen->"穿搭與生活偏好"
        updatePageOpen->"軟體更新";settingsOpen->when(advancedCategory){"外觀"->"字體與閱讀";"聲音"->"聲音與觸覺";"資料"->"資料更新頻率";else->"通知音效與背景提醒"}
        else->null
    }

    val renderedCondition=demoCondition ?: snapshot?.condition
    val renderedSnow=renderedCondition in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)
    val renderedRain=renderedCondition in setOf(WeatherCondition.RAIN,WeatherCondition.FREEZING_RAIN,WeatherCondition.HAIL,WeatherCondition.THUNDERSTORM)
    CompositionLocalProvider(
        LocalAppearance provides appearance,
        LocalHapticEngine provides hapticEngine
    ) {
    io.atmos.weather.ui.GlassAppTheme {
        val themedPage=mainPager.currentPage!=0||settingsOpen||settingsDetailOpen||lifestyleSettingsOpen||
            pushSettingsOpen||diagnosticsOpen||briefingOpen||tasksOpen||updatePageOpen||
            earthquakePageOpen||typhoonPageOpen||weatherAlarmOpen||marineOpen||skyOpen||alertsOpen||insightsOpen||nativeRadarOpen
        io.atmos.weather.ui.AppSystemBars(if(themedPage)darkAppearance else weatherSky.dark,transparentNavigation=!themedPage)
        val backdrop=io.atmos.weather.ui.rememberThinnBackdrop()
        val sceneBackdrop=io.atmos.weather.ui.rememberThinnBackdrop()
        androidx.compose.runtime.CompositionLocalProvider(
            io.atmos.weather.ui.LocalThinnBackdrop provides sceneBackdrop,
            io.atmos.weather.ui.TkCardWeatherStrength provides (if(renderedSnow) ((snapshot?.snowfallCm ?: .1).toFloat()/1.5f).coerceIn(.25f,1f) else if(heavyRain) 1f else .5f),
            io.atmos.weather.ui.TkCardWeatherMood provides when {
                renderedSnow -> io.atmos.weather.ui.TkPrecipitationMood.SNOW
                renderedRain -> io.atmos.weather.ui.TkPrecipitationMood.RAIN
                else -> io.atmos.weather.ui.TkPrecipitationMood.CLEAR
            }
        ) {
        TabletWorkspaceShell(selected=dockRequestedTab?:mainPager.targetPage,onSelect=::navigateWorkspace,
            modifier=Modifier.lightningScreenEffect(lightningAlpha,!themedPage&&mainPager.currentPage==0&&!reducedMotion,isNight),
            onSearch={locationOpen=true},cities=state.savedCities,selectedCity=state.city,
            onCity={clearWorkspaceDetails();viewModel.onIntent(WeatherIntent.SelectCity(it));showMainTab(0)},
            overview=snapshot?.let{"${it.temperatureC.toInt()}° · ${io.atmos.weather.domain.model.WeatherDescriptions.label(it.condition,it.weatherCode,it.visibilityM,it.isDay)}"}?:"正在更新天氣",
            background=if(themedPage)SolidColor(MaterialTheme.colorScheme.background) else Brush.verticalGradient(listOf(blendTop,blendMid,blendLow)),
            darkBackdrop=if(themedPage)null else weatherSky.dark,
            backgroundContent={
                if(!themedPage) {
                    SkyEffects(demoCondition ?: snapshot?.condition,isNight,
                        heavyRain || demoCondition==WeatherCondition.THUNDERSTORM,
                        hazy,appearance.liquidMotion,motionState,0f,
                        lightningBolt,snapshot?.windKmh ?: 0.0,snapshot?.weatherCode,snapshot?.visibilityM)
                }
            }) {
        val workspace=LocalWorkspaceLayout.current
        val workspaceSceneBackdrop=io.atmos.weather.ui.LocalThinnBackdrop.current
        val detailInset=if(workspace.listDetail&&mainPager.currentPage in 2..3&&workspaceDetailTitle!=null)
            (if(appDensity.fontScale>1.25f)366.dp else 316.dp) else 0.dp
        CompositionLocalProvider(LocalWorkspaceDetailInset provides detailInset,LocalWorkspaceDetailTitle provides workspaceDetailTitle) {
        // iOS 27 tab bar: condense while the user scrolls a page down, restore on the way up.
        val dockMinimizer=remember{object:androidx.compose.ui.input.nestedscroll.NestedScrollConnection{
            var travel=0f
            // Only a real finger (or the fling it started) condenses the bar. Accessibility
            // scrolling (TalkBack) reports the same source but must keep every tab reachable.
            var fingerDown=false;var releasedAt=0L
            override fun onPreScroll(available:androidx.compose.ui.geometry.Offset,source:androidx.compose.ui.input.nestedscroll.NestedScrollSource):androidx.compose.ui.geometry.Offset {
                val touchDriven=fingerDown||android.os.SystemClock.uptimeMillis()-releasedAt<1500L
                if(touchDriven&&kotlin.math.abs(available.y)>kotlin.math.abs(available.x)) {
                    if(travel!=0f&&kotlin.math.sign(travel)!=kotlin.math.sign(available.y))travel=0f
                    travel+=available.y
                    if(travel< -dockMinimizeTravelPx&&!dockMinimized)dockMinimized=true
                    if(travel> dockMinimizeTravelPx&&dockMinimized)dockMinimized=false
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }}
        val workspaceBrush=if(themedPage)
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background,MaterialTheme.colorScheme.background))
            else Brush.verticalGradient(listOf(blendTop,blendMid,blendLow))
        Box(Modifier.fillMaxSize().then(if(workspace.tablet)Modifier else Modifier.background(workspaceBrush))) {
            // Independent scene -> page capture -> dock. No control samples a
            // layer which contains itself, including during deferred RenderNode draws.
            if(!workspace.tablet)io.atmos.weather.ui.ThinnBackdropCapture(sceneBackdrop,Modifier.matchParentSize()) {
                Box(Modifier.matchParentSize().background(workspaceBrush))
            }
            io.atmos.weather.ui.ThinnBackdropCapture(backdrop,Modifier.fillMaxSize(),enabled=!workspace.tablet) {
            HorizontalPager(
                state = mainPager,
                modifier = (if(detailInset>0.dp)Modifier.width(detailInset-16.dp).fillMaxHeight() else Modifier.fillMaxSize())
                    .pointerInput(dockMinimizer){awaitEachGesture{
                        awaitFirstDown(requireUnconsumed=false,pass=androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        dockMinimizer.fingerDown=true
                        try{do{val e=awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)}while(e.changes.any{it.pressed})}
                        finally{dockMinimizer.fingerDown=false;dockMinimizer.releasedAt=android.os.SystemClock.uptimeMillis()}
                    }}
                    .nestedScroll(dockMinimizer)
                    .then(if(workspace.tablet)Modifier else Modifier.background(workspaceBrush)),
                beyondViewportPageCount = 0,
                userScrollEnabled = !workspace.tablet && !locationOpen && !settingsOpen && !settingsDetailOpen && !briefingOpen && !tasksOpen && !updatePageOpen && !diagnosticsOpen && !typhoonPageOpen && !earthquakePageOpen && !weatherAlarmOpen && !pushSettingsOpen && !lifestyleSettingsOpen && !nativeRadarOpen && !marineOpen && !skyOpen
            ) { page ->
            key(page,tabResetEpochs[page]) {
            when (page) {
            0 -> {
                // Each Pager item MUST have exactly one layout root. The former
                // SkyEffects full-screen canvases and forecast Column were emitted
                // as independent page roots, allowing the scenery to occupy the
                // page while all of the foreground forecast was laid out offscreen.
                val weatherBackdrop=io.atmos.weather.ui.rememberThinnBackdrop()
                val visualScene=WeatherScenes.classify(demoCondition ?: snapshot?.condition,
                    if(demoCondition!=null)null else snapshot?.weatherCode,snapshot?.visibilityM)
                val physicsStyle=WeatherPhysicsStyle.forScene(visualScene)
                val rainIntensity=physicsStyle.rain
                val county=io.atmos.weather.typhoon.TyphoonWarningStore.county(state.city.name)
                val windWarning=state.officialHazards.any {hazard->
                    // A published 強風特報 brings the wind and leaves in up to 12 h before it takes
                    // effect (預告), so the sky matches the red card the user is reading.
                    hazard.phenomenon.contains("強風")&&(county==null||hazard.county==county)&&
                    !java.time.Instant.now().isBefore(hazard.startsAt.minusSeconds(12*3600L))&&java.time.Instant.now().isBefore(hazard.expiresAt)}
                val strongWind=windWarning||(snapshot?.windKmh?:0.0)>=39.0||(snapshot?.windGustKmh?:0.0)>=50.0
                val windSpeed=maxOf((snapshot?.windKmh?:0.0),(snapshot?.windGustKmh?:0.0)*.72,
                    if(windWarning)45.0 else 0.0).takeIf{it.isFinite()}?.toFloat()?:0f
                val signedWind=windSpeed*(-kotlin.math.sin(Math.toRadians((snapshot?.windDirectionDeg?:270).toDouble()))).toFloat().let{
                    if(kotlin.math.abs(it)<.22f)1f else it}
                val weatherCards=rememberWeatherCardPalette()
                CompositionLocalProvider(io.atmos.weather.ui.LocalThinnBackdrop provides if(workspace.tablet)workspaceSceneBackdrop else weatherBackdrop,
                    LocalWeatherCardPalette provides weatherCards,LocalWeatherSceneInk provides weatherSky.ink) {
                io.atmos.weather.ui.RainCollisionScene(rainIntensity,signedWind,Modifier.fillMaxSize(),
                    style=physicsStyle,strongWind=strongWind,lightningEvent=lightningBolt,lightBackdrop=!weatherSky.dark,active=weatherSceneActive) {
                    if(!workspace.tablet)io.atmos.weather.ui.ThinnBackdropCapture(weatherBackdrop,Modifier.matchParentSize()) {
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(blendTop,blendMid,blendLow))))
                    // The place's landmarks stand on the horizon behind the weather (rain falls in front).
                    io.atmos.weather.visual.LandmarkScene(state.city.name,state.city.coordinate.latitude,state.city.coordinate.longitude,
                        blendMid,blendLow,isNight,if(stormy).8f else if(rainy).55f else if(hazy).6f else 0f,
                        Modifier.fillMaxWidth().fillMaxHeight(.62f))
                    // Full-screen background layer. It never captures gestures.
                    if(weatherSceneActive)SkyEffects(demoCondition ?: snapshot?.condition, isNight,
                        heavyRain || demoCondition == WeatherCondition.THUNDERSTORM,
                        hazy, appearance.liquidMotion, motionState, 0f,
                        lightningBolt, snapshot?.windKmh ?: 0.0, snapshot?.weatherCode, snapshot?.visibilityM)
                    }
                    // Keep location, temperature, cards, loading and errors above
                    // the background, independently of whether weather data loads.
                    io.atmos.weather.ui.WeatherRefreshContainer(state.loading,{
                        refreshRequested=true;refreshText="正在取得最新天氣…"
                        hapticEngine.play(HapticEngine.Event.RELEASE)
                        viewModel.onIntent(WeatherIntent.Refresh)
                    },Modifier.fillMaxSize().testTag("weather-scroll-viewport").weatherCollisionViewport().zIndex(1f)) {
                    io.atmos.weather.ui.AdaptiveScrollingPanes(
                        modifier=Modifier.fillMaxSize().zIndex(1f),scrollState=weatherScrollState,
                        horizontalPadding=20.dp,topPadding=18.dp,bottomPadding=maxOf(176.dp,io.atmos.weather.ui.floatingDockContentClearance()),
                        leading={
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min=54.dp).clip(RoundedCornerShape(999.dp))
                        .background(weatherSky.ink.copy(alpha=.12f)).border(1.dp,weatherSky.ink.copy(alpha=.32f),RoundedCornerShape(999.dp))
                        .clickable {locationOpen=true}.padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search,"搜尋城市",tint=weatherSky.ink,modifier=Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("搜尋城市、鄉鎮或地點",color=weatherSky.ink.copy(alpha=.95f),fontSize=15.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.brand_mark),"thinn-khì 標誌",Modifier.size(34.dp))
                        Spacer(Modifier.height(3.dp))
                        Text("thinn-khì",color=weatherSky.ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = demoRemaining > 0 || demoCompleted,
                    enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut(tween(200)) + androidx.compose.animation.shrinkVertically()
                ) {
                    Column(Modifier.fillMaxWidth().padding(top=12.dp)
                        .weatherCollisionSurface(19.dp).clip(RoundedCornerShape(19.dp))
                        .thinnGlass(io.atmos.weather.ui.rememberAppleReduceTransparency(),19.dp,weatherCards.fill)
                        .border(1.dp,White.copy(alpha=.36f),RoundedCornerShape(19.dp))
                        .padding(13.dp)) {
                        Text(if(demoRemaining > 0) "模擬天氣演示中 · 剩餘 $demoRemaining 秒" else "10 秒天氣示範已結束",
                            color=White,fontWeight=FontWeight.SemiBold,fontSize=16.sp)
                        if(demoRemaining > 0) {
                            Text(demoDescription,color=TextSoft,fontSize=14.sp)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress={ (10-demoRemaining)/10f },
                                modifier=Modifier.fillMaxWidth(),color=TextSoft,trackColor=White.copy(alpha=.22f))
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.locationFeedback.phase != LocationPhase.IDLE && !locationOpen,
                    enter = androidx.compose.animation.fadeIn(tween(180)) +
                        androidx.compose.animation.expandVertically(tween(220)),
                    exit = androidx.compose.animation.fadeOut(tween(180)) +
                        androidx.compose.animation.shrinkVertically(tween(200))
                ) {
                    LocationFeedbackBanner(state.locationFeedback, onRetry=onLocate)
                }
                androidx.compose.animation.AnimatedVisibility(visible=refreshText != null) {
                    Text(refreshText.orEmpty(), modifier=Modifier.fillMaxWidth()
                        .padding(top=10.dp).background(weatherCards.fill,RoundedCornerShape(14.dp))
                        .padding(horizontal=12.dp, vertical=8.dp),color=White,fontSize=15.sp,textAlign=TextAlign.Center)
                }
                Spacer(Modifier.height(28.dp))
                // Weather, real place name and landmark form ONE immersive hero.
                Row(Modifier.fillMaxWidth().clickable { locationOpen=true },
                    horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place,null,tint=weatherSky.ink.copy(alpha=.87f),modifier=Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(io.atmos.weather.location.LocationLabels.display(state.city.name),maxLines=2,
                        overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=weatherSky.ink,fontSize=(23f).coerceIn(21f,32f).sp,fontFamily=appearance.family,fontWeight=FontWeight.Normal,textAlign=TextAlign.Center)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown,null,tint=weatherSky.ink,modifier=Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (snapshot != null) {
                    io.atmos.weather.next.V25HomeHero(
                        temperature=snapshot.temperatureC.roundToInt(),
                        condition=snapshot.condition.name,
                        conditionLabel=WeatherDescriptions.current(snapshot),
                        high=snapshot.maxTempC?.roundToInt(),
                        low=snapshot.minTempC?.roundToInt(),
                        feelsLike=snapshot.feelsLikeC?.roundToInt(),
                        isNight=!snapshot.isDay,
                        rainProbability=snapshot.nextHourRainProbabilityPct,
                        modifier=Modifier.skyGlow(!snapshot.isDay,reduceCityMotion||!weatherSceneActive)
                    )
                    if(reportOpen) io.atmos.weather.report.WeatherReportSheet(snapshot,state.report,
                        onReport={viewModel.reportWeather(it)},onWithdraw={viewModel.withdrawReport()},onDismiss={reportOpen=false})
                    // 高鐵模式 / 火車模式: appears by itself while the phone moves along a railway.
                    io.atmos.weather.transit.RailModeCard(io.atmos.weather.transit.rememberRailTrip(weatherSceneActive),weatherSky.ink)
                    // Station name, distance and observation time are kept internal and not shown.
                } else if(state.loading) {
                    Spacer(Modifier.height(60.dp)); CircularProgressIndicator(color=weatherSky.ink,modifier=Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text("尚無天氣資料，請檢查網路連線",color=weatherSky.ink,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                }
                // Show official sea/land warnings on the red banner, and only Taiwan-bound
                // forecast tracks on the separate tile. Never make the tile permanent.
                verifiedTyphoonWarning?.let { official ->
                    // Keep real warning visibly separated from the weather hero.
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.fillMaxWidth()
                        .weatherCollisionSurface(24.dp).clip(RoundedCornerShape(24.dp))
                        .background(WeatherCardPalettes.alert.brush)
                        .border(1.dp,Color(0xFFFF9E9C).copy(alpha=.65f),RoundedCornerShape(24.dp))
                        .clickable { typhoonPageOpen=true }
                        .padding(17.dp)) {
                        val land=official.level==io.atmos.weather.typhoon.TyphoonWarningLevel.LAND
                        Text(if(land) "中央氣象署 · 陸上颱風警報" else "中央氣象署 · 海上颱風警報",
                            fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Spacer(Modifier.height(5.dp))
                        Text("官方發布：${official.issuedAt.atZone(java.time.ZoneId.of("Asia/Taipei"))}",
                            fontSize=14.sp,color=Color.White.copy(alpha=.83f))
                        Spacer(Modifier.height(9.dp))
                        val regions=if(land)official.landCounties else official.seaAreas
                        Text(if(land)"陸上警戒縣市" else "海上警戒海域",fontSize=15.sp,color=Color.White)
                        Spacer(Modifier.height(6.dp))
                        if(regions.isEmpty()) {
                            Text("官方公告未提供可核對的警戒區域清單；請開啟官方警報地圖。",
                                color=Color.White.copy(alpha=.9f),fontSize=14.sp)
                        } else {
                            // Red verified-region BLOCKS, not fabricated county polygons.
                            regions.chunked(3).forEach { group ->
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    group.forEach { area ->
                                        Box(Modifier.padding(vertical=3.dp)
                                            .background(Color(0xFFC0393E),RoundedCornerShape(10.dp))
                                            .padding(horizontal=8.dp,vertical=7.dp)) {
                                            Text(area,color=Color.White,fontSize=14.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Text("點選查看颱風動向 · 正式警戒範圍以氣象署為準 ↗",
                            color=Color(0xFFFFD8D6),fontSize=14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                val typhoonNotices = state.cwaWarnings.filter {
                    it.title.contains("颱風") || it.summary.contains("颱風警報")
                }
                if (officialStormActive || verifiedTyphoonWarning!=null) {
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth().weatherCollisionSurface(22.dp).clip(RoundedCornerShape(22.dp))
                        .thinnGlass(io.atmos.weather.ui.rememberAppleReduceTransparency(),22.dp,weatherCards.fill)
                        .border(1.dp, brandColor(0xFF97DAC7).copy(alpha=.56f), RoundedCornerShape(22.dp))
                        .clickable { typhoonPageOpen = true }
                        .padding(horizontal=17.dp, vertical=18.dp), verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Cyclone, contentDescription=null,
                            tint=Color(0xFFFFC47D),modifier=Modifier.size(25.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("颱風動向與官方路徑",fontSize=18.sp,
                                fontWeight=FontWeight.Bold,color=White)
                            approachingStorm?.let { storm ->
                                Text("${storm.name} · ${storm.typhoonNumber.ifBlank { "颱風" }}號",
                                    color=White,fontSize=16.sp,fontWeight=FontWeight.Medium)
                                val current=storm.fixes.firstOrNull()
                                Text(current?.officialDirection?.let { "移動方向 ${io.atmos.weather.typhoon.TyphoonDirection.zh(it) ?: "待確認"} · 預報接近台澎金馬" }
                                    ?: "中央氣象署預報：接近台澎金馬",
                                    color=TextSoft,fontSize=14.sp,maxLines=2)
                            }
                            if (verifiedTyphoonWarning!=null)
                                Text("已發布正式颱風警報 · 點選查看",color=MaterialTheme.colorScheme.error,fontSize=14.sp)
                            else Text("尚非正式海上／陸上颱風警報",color=TextSoft,fontSize=14.sp)
                            Text("七級風暴風圈通過機率 · ${io.atmos.weather.location.LocationLabels.microcity(state.city.name)}：${homeWindProbability ?: "暫無官方機率"}",
                                color=MaterialTheme.colorScheme.primary,fontSize=14.sp,maxLines=2)

                        }
                        Icon(Icons.Rounded.ChevronRight,contentDescription="查看颱風動向",tint=TextSoft)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // r19: with no storm the home stays quiet; 颱風動向 remains in 工具.
                // A recent verified POST-event report is a time-limited, source-labelled
                // red home card. Never describe third-party data as CWA-issued.
                recentEarthquake?.let { report ->
                    Column(Modifier.fillMaxWidth().weatherCollisionSurface(21.dp).clip(RoundedCornerShape(21.dp))
                        .background(WeatherCardPalettes.alert.brush)
                        .border(1.dp,Color(0xFFFFB6B2).copy(alpha=.7f),RoundedCornerShape(21.dp))
                        .padding(15.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber,contentDescription=null,tint=Color.White)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if(report.sourceOfficial) "中央氣象署 · 最新地震報告" else "最新地震報告 · 第三方彙整",
                                    fontSize=17.sp,fontWeight=FontWeight.Bold,color=Color.White)
                                Text("震後資料，並非提前地震預警",fontSize=14.sp,color=Color.White.copy(alpha=.9f))
                            }
                            TextButton(onClick={
                                RecentEarthquakeNoticeStore.dismiss(context,report.id)
                                recentEarthquake=null
                            }) { Text("關閉",fontSize=14.sp,color=Color.White) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${report.location} · 規模 ${"%.1f".format(java.util.Locale.US,report.magnitude)}",
                            color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("收到後顯示 30 分鐘；有新的已驗證報告會自動更新。",
                            color=Color.White.copy(alpha=.85f),fontSize=14.sp)
                        if(report.sourceOfficial && report.officialUrl.startsWith("https://")) {
                            TextButton(onClick={alertTitle="中央氣象署地震報告";alertUrl=report.officialUrl;alertsOpen=true}) {
                                Text("查看官方報告",color=Color.White,fontSize=15.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // 依本次需求，首頁不再顯示中央氣象署 RSS 特報摘要區塊，
                // 避免出現「中央氣象署特報 1 則所在地相關公告」與
                // 「資料來源：官方 RSS 最新公告……」等額外說明文字。

                // Active CWA county hazards are independent of the Taiwan-bound typhoon tile.
                state.officialHazards.forEach { hazard ->
                    Column(Modifier.fillMaxWidth()
                        .weatherCollisionSurface(20.dp).clip(RoundedCornerShape(20.dp))
                        .background(WeatherCardPalettes.alert.brush)
                        .border(1.dp,Color(0xFFFFBCAC).copy(alpha=.72f),RoundedCornerShape(20.dp))
                        .clickable {alertTitle=hazard.title;alertUrl=null;alertsOpen=true}
                        .padding(16.dp)) {
                        Text("中央氣象署 · ${hazard.title}",color=Color.White,
                            fontSize=18.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        val end = hazard.expiresAt.atZone(java.time.ZoneId.of("Asia/Taipei"))
                        val starts = hazard.startsAt.atZone(java.time.ZoneId.of("Asia/Taipei"))
                        if(java.time.Instant.now().isBefore(hazard.startsAt))Text(
                            "預告 · ${starts.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))} 起生效",
                            color=Color.White,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                        Text("${hazard.county} · 有效至 ${end.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))}",
                            color=Color.White.copy(alpha=.9f),fontSize=14.sp)
                        Text("在 App 內查看警戒區域與防護建議 ›",
                            color=Color(0xFFFFD5D0),fontSize=14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // RSS is a recent official notice, not a proven still-active CAP warning.
                val recentCwaNotices=state.cwaWarnings.filter { notice ->
                    val age=java.time.Instant.now().epochSecond-notice.issuedAt.epochSecond
                    age in 0L..(24L*3600L) &&
                        notice.link.startsWith("https://www.cwa.gov.tw/") &&
                        !state.officialHazards.any { notice.title.contains(it.phenomenon) } &&
                        listOf("解除","取消","測試","演練","颱風").none { notice.title.contains(it) }
                }.take(2)
                recentCwaNotices.forEach { notice ->
                    Column(Modifier.fillMaxWidth().weatherCollisionSurface(22.dp).clip(RoundedCornerShape(22.dp))
                        .background(WeatherCardPalettes.alert.brush)
                        .border(1.dp,Color(0xFFFFB4AE).copy(alpha=.64f),RoundedCornerShape(22.dp))
                        .clickable {alertTitle=notice.title;alertUrl=notice.link;alertsOpen=true}.padding(17.dp)) {
                        Text("中央氣象署 · ${notice.title}",color=Color.White,
                            fontSize=18.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
                        Spacer(Modifier.height(7.dp))
                        Text(notice.summary.ifBlank { "查看公告內容與受影響地區" }.take(98),
                            color=Color.White.copy(alpha=.94f),fontSize=14.sp,maxLines=3)
                        Spacer(Modifier.height(7.dp))
                        Text("${notice.issuedAt.atZone(java.time.ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))} 發布 · 在 App 內查看詳情 ›",
                            color=Color(0xFFFFDBD7),fontSize=14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                    },trailing={
                if (snapshot != null) {
                    // Cards follow the user's own order (編輯首頁), each settling in on the iOS curve.
                    val layoutRevision by HomeCardLayout.revision
                    val cardOrder = remember(layoutRevision) { HomeCardLayout.order(context) }
                    val cardHidden = remember(layoutRevision) { HomeCardLayout.hidden(context) }
                    Spacer(Modifier.height(16.dp))
                    cardOrder.filterNot { it in cardHidden }.forEachIndexed { cardIndex, cardId ->
                        androidx.compose.runtime.key(cardId) { Column(Modifier.fillMaxWidth().iosAppear(cardIndex)) { when (cardId) {
                    // Weather always takes precedence over decor: show real six-hour facts
                    // and daily clothing/rain advice WITHOUT a tap or a hidden details sheet.
                    "key" -> { HomeKeyWeatherCard(snapshot, state.stale); Spacer(Modifier.height(13.dp)) }
                    // Official alerts and the six-hour forecast are visible before outfit advice.
                    "outlook" -> { HomeOutlookCard(snapshot,io.atmos.weather.location.LocationLabels.display(state.city.name)){insightsOpen=true}; Spacer(Modifier.height(15.dp)) }
                    "outing" -> { DailyGuidanceCard(snapshot,state.stale) { showLifestyleDetails=true }; Spacer(Modifier.height(15.dp)) }
                    "commute" -> { HomeCommuteCard(snapshot.fetchedAt); if (io.atmos.weather.comfort.ComfortStore.place(context,"home")!=null && io.atmos.weather.comfort.ComfortStore.place(context,"work")!=null) Spacer(Modifier.height(15.dp)) }
                    "forecast" -> {
                    GlassCard {
                        // Horizontally draggable curve with a synchronized selected-hour readout.
                        InteractiveForecast(snapshot.nextHours.take(24),snapshot.timeZoneId, sunrise=snapshot.sunrise, sunset=snapshot.sunset)
                    }
                    Spacer(Modifier.height(15.dp))
                    }
                    // Restore the existing native CWA radar card to the weather home.
                    // When imagery is unavailable, the original card shows an honest
                    // unavailable/coverage status instead of silently removing the map.
                    "daily" -> { DailyForecastCard(snapshot); if(forecastDays(snapshot,snapshot.fetchedAt.atZone(java.time.ZoneId.of(snapshot.timeZoneId?:"Asia/Taipei")).toLocalDate()).size>=2) Spacer(Modifier.height(15.dp)) }
                    "radar" -> { RadarThreeHourCard(weather=snapshot); Spacer(Modifier.height(15.dp)) }
                    // Indicator tiles and air quality are one "details" section now.
                    "details" -> {
                    WeatherIndicatorGrid(snapshot)
                    if(snapshot.temperatureC <= 6.0 && snapshot.condition in
                        setOf(WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.FREEZING_RAIN)) {
                        Spacer(Modifier.height(8.dp))
                        GlassCard {
                            Text("❄ 降雪與冰凍預報",color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                            val chance=snapshot.nextHours.mapNotNull { it.snowfallProbabilityPct }.firstOrNull()
                            Text(if(chance!=null) "下一預報時段降雪機率 ${chance}%" else "目前模型未提供可靠的獨立降雪機率",color=TextSoft,fontSize=15.sp)
                            Text("預估降雪 ${snapshot.snowfallCm?.let { String.format(java.util.Locale.US,"%.1f",it) } ?: "—"} cm；留意結冰與海拔差異。",color=TextSoft,fontSize=14.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    }
                    } } }
                    }
                    // iOS-style "Edit" pill at the end of the cards.
                    var editingHome by remember { mutableStateOf(false) }
                    // Two matching pills on the sky: report the weather here, and edit the page.
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp,Alignment.CenterHorizontally)) {
                        @Composable fun pill(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onTap:()->Unit) =
                            Row(Modifier.iosJellyPress().clip(RoundedCornerShape(999.dp)).background(weatherSky.ink.copy(alpha=.12f))
                                .border(.7.dp,weatherSky.ink.copy(alpha=.28f),RoundedCornerShape(999.dp))
                                .clickable(onClick=onTap).padding(horizontal=16.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(icon,null,tint=weatherSky.ink,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp))
                                Text(label,color=weatherSky.ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                            }
                        if(snapshot!=null)pill(if(state.report!=null)"已回報 · 修改" else "回報天氣",
                            if(state.report!=null)Icons.Rounded.CheckCircle else Icons.Rounded.Campaign){reportOpen=true}
                        pill("編輯首頁",Icons.Rounded.Tune){editingHome=true}
                    }
                    if(editingHome) HomeCardEditor{editingHome=false}
                }
                if(state.loading && snapshot!=null) {
                    Spacer(Modifier.height(14.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(),color=weatherSky.ink,trackColor=weatherSky.ink.copy(alpha=.2f))
                }
                if(state.stale || state.error != null) {
                    Spacer(Modifier.height(13.dp))
                    Text(if(state.stale) "目前顯示離線快取，可能已過時" else "更新失敗：${state.error}",
                        color=weatherSky.ink,fontSize=15.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                }
                Spacer(Modifier.height(22.dp))
                Text(if(snapshot?.sourceId?.endsWith("cwa")==true)(if(snapshot.observationStation!=null)"觀測與預報：中央氣象署開放資料平台" else "預報：中央氣象署開放資料平台") else if(snapshot?.sourceId=="met-norway")"預報：挪威氣象局 MET Norway · CC BY 4.0（備援來源）" else if(snapshot?.observationStation!=null)"觀測：中央氣象署 · 預報：Open-Meteo" else "預報：Open-Meteo.com · 依其授權條款使用",color=weatherSky.secondary,fontSize=14.sp,
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("天氣是預測資訊，請以當地官方警報為準。",color=weatherSky.secondary,fontSize=14.sp,
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                    }) // forecast panes: one shared state, responsive single/dual columns
                    } // elastic pull-to-refresh
                } // page Box: one measurable root for HorizontalPager
                } // weather background material provider
            } // page 0
        // 四頁由單一 Foundation HorizontalPager 承接，頁面與底部分頁保持同步。
            1 -> SeismicPrimaryTab(onExit = { showMainTab(0) })
            2 -> AppGlassToolsPage(
                onBriefings = { openWorkspaceDetail("briefing") },
                onUpdates = { openWorkspaceDetail("update") },
                onDiagnostics = { openWorkspaceDetail("diagnostics") },
                onSeismic = { navigateWorkspace(1) },
                onTasks = { openWorkspaceDetail("tasks") },
                onWeatherAlarms = { openWorkspaceDetail("alarm") },
                onTyphoons = { openWorkspaceDetail("typhoon") },
                onRadar = {openWorkspaceDetail("radar")},
                onMarine={openWorkspaceDetail("marine")},onSky={openWorkspaceDetail("sky")},
                onAlerts={openWorkspaceDetail("alerts")},onInsights={openWorkspaceDetail("insights")},onUtilityOpen={clearWorkspaceDetails()})
            3 -> AppGlassSettingsPage(
                appearance = appearance,
                onAppearance = { appearance = it },
                automaticUpdates = automaticUpdateNotices,
                onUpdates = { enabled ->
                    automaticUpdateNotices = enabled
                    settingsPrefs.edit().putBoolean("check_updates_automatically", enabled).apply()
                    io.atmos.weather.update.UpdateCheckWorker.schedule(context)
                },
                onAdvanced = { openWorkspaceDetail("advanced","聲音") },
                onAdvancedCategory = {openWorkspaceDetail("advanced",it)},
                onDiagnostics = { openWorkspaceDetail("diagnostics") },
                onLifestyle = { openWorkspaceDetail("lifestyle") },
                onPushSettings = { openWorkspaceDetail("push") },
                onUpdateCenter = { openWorkspaceDetail("update") },
                onWeatherAlarms = { openWorkspaceDetail("alarm") },
                onTyphoons = { openWorkspaceDetail("typhoon") },
                onDetailVisibility = {if(it&&workspaceDetailTitle!=null)clearWorkspaceDetails();settingsDetailOpen=it})
            } // when(page)
            } // destination root identity
            } // HorizontalPager
            } // page capture; foreground remains a normal Compose layout
        if(nativeRadarOpen) io.atmos.weather.ui.RadarHistoryDialog(onClose={nativeRadarOpen=false},latitude=state.city.coordinate.latitude,longitude=state.city.coordinate.longitude)
        if (locationOpen) LocationSheet(state.city,state.savedCities,state.searchResults,state.searching,
            state.searchExecuted,state.searchError,state.locationFeedback,onLocate=onLocate,
            onQuery=viewModel::search,
            onSelect={ clearWorkspaceDetails();viewModel.addSavedCity(it);viewModel.onIntent(WeatherIntent.SelectCity(it));showMainTab(0) },
            onRemove=viewModel::removeSavedCity,onClose={locationOpen=false})
        io.atmos.weather.ui.MainDetailLayer(settingsOpen,{settingsOpen=false}) {
            val preferencePalette=androidx.compose.material3.MaterialTheme.colorScheme
            val White=preferencePalette.onSurface
            val TextSoft=preferencePalette.onSurfaceVariant
            Column(Modifier.fillMaxSize().background(preferencePalette.background).statusBarsPadding().navigationBarsPadding()) {
                io.atmos.weather.ui.IOSSettingsHeader(
                    when(advancedCategory){"外觀"->"字體與閱讀";"資料"->"資料更新頻率";"通知"->"通知音效與背景提醒";else->"聲音與觸覺"},
                    onBack={settingsOpen=false})
                Column(Modifier.weight(1f).appleBouncingScroll().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=16.dp,bottom=100.dp)) {
                    when(advancedCategory) {
                        "資料" -> {
                Text("天氣資訊更新頻率",fontSize=19.sp,color=White,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("App 開啟期間每 $refreshMinutes 分鐘自動更新",color=TextSoft,fontSize=15.sp)
                Spacer(Modifier.height(12.dp))
                io.atmos.weather.ui.IOSDurationSetting("更新頻率",refreshMinutes,{selected ->
                    refreshMinutes=selected;settingsPrefs.edit().putInt("refresh_minutes",refreshMinutes).apply();WidgetSync.schedule(context)
                },min=5,max=120)
                Spacer(Modifier.height(15.dp))
                HorizontalDivider(color=White.copy(alpha=.13f))
                Spacer(Modifier.height(17.dp))
                Text("桌面與背景更新由 Android 排程；Widget 同步最近一次天氣資料，實際更新可能受省電影響。",color=TextSoft,fontSize=14.sp)
                        }
                        "外觀" -> {
                Text("字體與閱讀",fontSize=19.sp,color=White,fontWeight=FontWeight.SemiBold)
                Text("調整文字大小、粗細與字體。文字大小支援 100–150%，變更會立即套用。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(10.dp))
                AppearanceSlider("字體大小",appearance.fontScale,1f..1.5f,
                    "${(appearance.fontScale*100).roundToInt()}%",commitOnRelease=true) {appearance=appearance.copy(fontScale=it)}
                AppearanceSlider("字體粗細",appearance.fontWeight,100f..900f,
                    "${appearance.fontWeight.roundToInt()}",commitOnRelease=true) {appearance=appearance.copy(fontWeight=it)}
                io.atmos.weather.ui.GlassChoiceGroup(listOf("系統","襯線","等寬","手寫"),
                    appearance.fontChoice,{appearance=appearance.copy(fontChoice=it)})
                Spacer(Modifier.height(10.dp))
                        }
                        "通知" -> {
                Text("天氣警特報提醒",color=White,fontSize=17.sp)
                Text("APP 通知統一優先使用焦點通知／超級島；是否上島仍由系統授權與小米核准情境決定。未獲支援時以一般通知呈現，避免漏接警特報。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(13.dp))
                var selectedNotificationTone by remember { mutableStateOf(ThinnNotificationChannels.selectedToneId(context)) }
                Text("通知音 · 20 款任選",color=White,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                Text("輕柔、溫暖、空靈三種柔和風格，以及經典音色。警特報、早晚報、智慧提醒與通勤通知會一起套用。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(9.dp))
                var toneStyle by remember { mutableStateOf(ThinnNotificationChannels.tone(selectedNotificationTone).style) }
                io.atmos.weather.ui.GlassChoiceGroup(ThinnNotificationChannels.styles.map{it.second},
                    ThinnNotificationChannels.styles.indexOfFirst{it.first==toneStyle},{toneStyle=ThinnNotificationChannels.styles[it].first})
                Spacer(Modifier.height(9.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(White.copy(alpha=.075f))
                    .border(1.dp,White.copy(alpha=.13f),RoundedCornerShape(22.dp))
                    .padding(vertical=4.dp)) {
                    androidx.compose.runtime.key(toneStyle) {
                    ThinnNotificationChannels.tones.filter{it.style==toneStyle}.forEachIndexed { toneIndex, tone ->
                        val selected = selectedNotificationTone == tone.id
                        Row(Modifier.fillMaxWidth().iosAppear(toneIndex)
                            .clickable {
                                hapticEngine.play(HapticEngine.Event.TAP)
                                selectedNotificationTone = tone.id
                                ThinnNotificationChannels.select(context, tone.id)
                            }
                            .padding(start=9.dp,end=9.dp,top=4.dp,bottom=4.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(CircleShape)
                                .border(1.4.dp,if(selected) brandColor(0xFFA6E0CF) else TextSoft,CircleShape)
                                .background(if(selected) brandColor(0xFF328D74) else Color.Transparent),
                                contentAlignment=Alignment.Center) {
                                if(selected) Box(Modifier.size(9.dp).background(White,CircleShape))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(tone.title,color=White,fontSize=16.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
                                Text(tone.description,color=TextSoft,fontSize=14.sp)
                            }
                            Box(Modifier.size(45.dp).clip(CircleShape)
                                .background(White.copy(alpha=.15f))
                                .clickable {
                                    hapticEngine.play(HapticEngine.Event.TAP)
                                    ThinnNotificationChannels.playPreview(context,tone.id)
                                },contentAlignment=Alignment.Center) {
                                Text("▶",color=MaterialTheme.colorScheme.primary,fontSize=18.sp)
                            }
                        }
                    }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text("目前使用：${ThinnNotificationChannels.tone(selectedNotificationTone).title} · 更換後的新通知生效",color=White,fontSize=14.sp)
                OutlinedButton(onClick={
                    hapticEngine.play(HapticEngine.Event.TAP)
                    previewScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            ThinnNotificationChannels.sendTest(context)
                        }
                    }
                },modifier=Modifier.fillMaxWidth()) { Text("使用所選音效發送測試通知",color=White,fontSize=14.sp) }
                Text("每個音效均使用獨立的 Android 通知通道。若沒有聲音，請確認通知權限、手機音量、靜音／勿擾及通知通道設定；系統仍有最終控制權。",color=TextSoft,fontSize=14.sp)

                if (XiaomiFocusBridge.isXiaomi()) {
                    Spacer(Modifier.height(10.dp))
                    val focusProtocol = remember { XiaomiFocusBridge.protocol(context) }
                    var focusStatus by remember { mutableStateOf<XiaomiFocusBridge.Availability?>(null) }
                    LaunchedEffect(settingsOpen) {
                        if (settingsOpen) {
                            focusStatus = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                XiaomiFocusBridge.availability(context)
                            }
                        }
                    }
                    Text("小米焦點通知／超級島",color=White,fontSize=15.sp)
                    Text(when (focusStatus) {
                        XiaomiFocusBridge.Availability.ENABLED -> if (focusProtocol >= 3) "系統已授權 · HyperOS 3 超級島候選" else "系統已授權 · HyperOS 2 焦點通知候選"
                        XiaomiFocusBridge.Availability.UNSUPPORTED -> "系統沒有回報支援的焦點通知協定"
                        XiaomiFocusBridge.Availability.PERMISSION_NOT_GRANTED -> "App 尚未取得小米焦點通知授權；使用一般 Android 通知"
                        else -> "正在檢查系統授權…"
                    }, color=TextSoft,fontSize=14.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            previewScope.launch {
                                focusStatus = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    XiaomiFocusBridge.availability(context)
                                }
                            }
                        }) { Text("檢查支援",color=White,fontSize=14.sp) }
                        OutlinedButton(onClick={
                            if (PermissionGuide.canNotify(context)) {
                                previewScope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        XiaomiFocusBridge.sendTest(context)
                                    }
                                }
                            } else (context as? android.app.Activity)?.let(PermissionGuide::openAppNotificationSettings)
                        }) { Text("測試通知",color=White,fontSize=14.sp) }
                    }
                    Text("單則警特報、特報摘要、測試通知均走統一發送入口。測試明確標註非正式警報。焦點通知與超級島需系統授權及小米對應情境審核，不保證每則上島。",color=TextSoft,fontSize=14.sp)
                }
                Text("官方 RSS 對照所在地，背景約每 15 分鐘排程檢查，實際可能延遲；並非緊急細胞廣播替代品。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick={settingsOpen=false;alertTitle=null;alertUrl=null;alertsOpen=true}) { Text("查看中央氣象署官方警報",color=White) }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color=White.copy(alpha=.13f))
                Spacer(Modifier.height(15.dp))
                Text("權限及背景提醒",fontSize=19.sp,color=White,fontWeight=FontWeight.SemiBold)
                Text("定位：${if(PermissionGuide.hasLocation(context)) "已允許" else "尚未允許"}；通知：${if(PermissionGuide.canNotify(context)) "已允許" else "已關閉或未允許"}",
                    color=TextSoft,fontSize=15.sp)
                if (!PermissionGuide.hasLocation(context) || !PermissionGuide.canNotify(context)) {
                    Text("權限關閉時不會反覆跳出系統授權視窗，可自行重新開啟。",color=TextSoft,fontSize=14.sp)
                    TextButton(onClick={ (context as? android.app.Activity)?.let(PermissionGuide::openAppDetails) }) {
                        Text("前往 App 權限設定",color=White)
                    }
                }
                if (PermissionGuide.needsBatteryGuidance(context)) {
                    Text("部分小米／OPPO／vivo 等手機會限制背景更新。若要提高提醒可靠度，請自行前往應用程式設定，視需要將省電策略改成「無限制」。耗電可能增加。",color=TextSoft,fontSize=14.sp)
                    TextButton(onClick={ (context as? android.app.Activity)?.let(PermissionGuide::openBatterySettings) }) {
                        Text("開啟省電策略設定指引",color=White)
                    }
                }
                        }
                        else -> {
                GlassToggle("天氣環境音", "晴天、雨天、雷雨與降雪",soundOn) {
                    soundOn=it;settingsPrefs.edit().putBoolean("sound_enabled",it).apply()
                }
                GlassTrackingSlider("環境音量",soundVolume) {
                    soundVolume=it
                    if(it>0)soundOn=true
                    settingsPrefs.edit().putFloat("sound_volume",it).putBoolean("sound_enabled",soundOn).apply()
                }
                Text(ambientPlayback.value.message,color=White,fontSize=14.sp)
                OutlinedButton(onClick={if(soundPreviewActive)soundPreviewActive=false else {soundRetryToken++;soundPreviewActive=true}},modifier=Modifier.fillMaxWidth()) {
                    Icon(if(soundPreviewActive)Icons.Rounded.Stop else Icons.Rounded.PlayArrow,null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(soundPreviewActive)"停止試聽" else "試聽目前天氣音效 · 8 秒")
                }
                Text("僅在 App 前景播放；媒體音量、音訊焦點與系統音訊設定會一併生效。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color=White.copy(alpha=.13f))
                Spacer(Modifier.height(20.dp))
                Text("觸覺回饋 · Haptic Engine",color=White,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                Text("所有天氣、點擊、拖曳與回彈觸覺統一調整；新安裝預設 100%，向左調至 0% 即關閉。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(12.dp))
                Text("觸覺回饋強度 ${(hapticSettings.master*100).roundToInt()}%",
                    fontSize=15.sp,fontWeight=FontWeight.Medium,color=White)
                GlassTrackingSlider("觸覺強度",hapticSettings.master) {
                    hapticSettings=hapticSettings.copy(master=it,enabled=true)
                }
                Text(if (hapticEngine.available) "裝置已支援觸覺馬達；實際強度受硬體與系統限制。"
                    else "此裝置目前未提供可用的震動馬達。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick={
                    WeatherDemoDispatcher.dispatch(WeatherDemoEvent.Start)
                },enabled=demoRemaining == 0,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PlayArrow,null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(demoRemaining > 0) "演示中 · 剩餘 $demoRemaining 秒" else "試震 10 秒 · 真實情境變化")
                }
                if(demoRemaining > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("模擬天氣演示中 · 剩餘 $demoRemaining 秒",color=White,fontSize=15.sp,fontWeight=FontWeight.Medium)
                    Text(demoDescription,color=TextSoft,fontSize=14.sp)
                    LinearProgressIndicator(progress={ (10-demoRemaining)/10f },modifier=Modifier.fillMaxWidth())
                    TextButton(onClick={WeatherDemoDispatcher.dispatch(WeatherDemoEvent.Stop)}) {
                        Text("提前結束演示",color=White)
                    }
                } else if(demoCompleted) {
                    Text("10 秒天氣演示已結束",color=TextSoft,fontSize=14.sp)
                }
                Text("示範事件僅在 App 內觸發畫面、雨聲和觸覺，不會發送虛構的官方氣象警報或手機緊急通知。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(17.dp))
                GlassCard(enableDrag=true) {
                    Text("thinn-khì · 即時預覽",fontSize=15.sp,fontFamily=appearance.family,
                        fontWeight=FontWeight.Normal,color=White)
                    Spacer(Modifier.height(6.dp))
                    Text("輕輕向左右拖曳這張卡片，感受回彈與玻璃高光變化。",color=TextSoft,fontSize=14.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick={appearance=WeatherAppearance()}) {
                    Text("恢復預設外觀",color=White)
                }
                Spacer(Modifier.height(9.dp))
                Text("桌面與背景更新最快約每 15 分鐘排程一次，實際可能受系統省電措施延遲；Widget 會同步最近一次天氣和地標快照。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
        io.atmos.weather.ui.MainDetailLayer(pushSettingsOpen,{pushSettingsOpen=false}) {io.atmos.weather.push.SeismicPushSettingsPage(onClose={pushSettingsOpen=false})}
        io.atmos.weather.ui.MainDetailLayer(lifestyleSettingsOpen,{lifestyleSettingsOpen=false}) {LifestyleSettingsPage(onClose={lifestyleSettingsOpen=false})}
        if (showLifestyleDetails && snapshot != null) {
            ModalBottomSheet(onDismissRequest={showLifestyleDetails=false}) {
                val White=MaterialTheme.colorScheme.onSurface
                val TextSoft=MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal=22.dp).padding(bottom=42.dp)) {
                    Text("今日穿搭與天氣依據",color=White,fontSize=25.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    LifestyleAdviceCard(snapshot,state.stale,onOpenSettings={showLifestyleDetails=false;lifestyleSettingsOpen=true})
                    Spacer(Modifier.height(22.dp))
                    Text("更多天氣提醒",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    io.atmos.weather.domain.model.WeatherAdviceEngine.suggest(snapshot).take(6).forEach { tip ->
                        Spacer(Modifier.height(14.dp))
                        Text(tip.heading,color=White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        Text(tip.detail,color=TextSoft,fontSize=16.sp,lineHeight=25.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("這些是氣象模型推估的生活建議；官方災害警報以中央氣象署發布為準。",
                        color=TextSoft,fontSize=15.sp,lineHeight=23.sp)
                }
            }
        }
        io.atmos.weather.ui.MainDetailLayer(diagnosticsOpen,{diagnosticsOpen=false}) {SettingsDiagnosticsPage(onClose={diagnosticsOpen=false})}
        io.atmos.weather.ui.MainDetailLayer(briefingOpen,{briefingOpen=false}) {WeatherBriefingSettingsPage(onClose={briefingOpen=false})}
        io.atmos.weather.ui.MainDetailLayer(tasksOpen,{tasksOpen=false}) {io.atmos.weather.task.TaskCenterPage(onClose={tasksOpen=false})}
        io.atmos.weather.ui.MainDetailLayer(weatherAlarmOpen,{weatherAlarmOpen=false}) {io.atmos.weather.alarm.WeatherAlarmPage(onClose={weatherAlarmOpen=false})}
        io.atmos.weather.ui.MainDetailLayer(typhoonPageOpen,{typhoonPageOpen=false}) { io.atmos.weather.typhoon.TyphoonCenterPage(
            warnings=state.cwaWarnings,onClose={typhoonPageOpen=false},
            initialBackup=gdacsCyclones, primaryUnavailable=typhoonPrimaryUnavailable,
            currentLat=state.city.coordinate.latitude,currentLon=state.city.coordinate.longitude,
            currentName=state.city.name) }
        io.atmos.weather.ui.MainDetailLayer(updatePageOpen,{updatePageOpen=false}) {
            AppUpdatePage(onClose={updatePageOpen=false})
        }
        io.atmos.weather.ui.MainDetailLayer(earthquakePageOpen,{earthquakePageOpen=false}) {
            EarthquakeCenterPage(onClose={earthquakePageOpen=false})
        }
        io.atmos.weather.ui.MainDetailLayer(marineOpen,{marineOpen=false}) {
            io.atmos.weather.ocean.MarinePage(state.city){marineOpen=false}
        }
        io.atmos.weather.ui.MainDetailLayer(alertsOpen,{alertsOpen=false}) {
            io.atmos.weather.alerts.CwaAlertsPage(alertTitle,alertUrl,state.officialHazards,state.cwaWarnings){alertsOpen=false}
        }
        io.atmos.weather.ui.MainDetailLayer(insightsOpen,{insightsOpen=false}) {
            io.atmos.weather.insights.WeatherInsightsPage(snapshot,state.stale){insightsOpen=false}
        }
        io.atmos.weather.ui.MainDetailLayer(skyOpen,{skyOpen=false}) {
            io.atmos.weather.sky.SkyPlannerPage(state.city,snapshot?.timeZoneId){skyOpen=false}
        }
        if(!workspace.tablet) CompositionLocalProvider(
            io.atmos.weather.ui.LocalThinnBackdrop provides backdrop) { AppGlassNavigation(
            selected = dockRequestedTab ?: mainPager.targetPage,
            onSelect = ::navigateWorkspace,
            darkBackdrop=if(themedPage)null else weatherSky.dark,
            minimized=dockMinimized,onExpand={dockMinimized=false},
            accessory=io.atmos.weather.next.NextToolAction("search","搜尋城市",Icons.Rounded.Search),
            onAccessory={locationOpen=true},
            modifier = Modifier.align(Alignment.BottomCenter).testTag("floating-navigation-dock").navigationBarsPadding().padding(bottom = 16.dp)) }
        details?.let { info -> AlertDialog(onDismissRequest={details=null},
            confirmButton={ TextButton(onClick={details=null}) { Text("了解") } },
            title={Text(info.first)},text={Text(info.second)}) }
        } // root page
        } // detail layout locals
        } // workspace shell
        } // Close precipitation CompositionLocalProvider inside GlassAppTheme.
    }
    }
}

@Composable
private fun HeaderButton(image: androidx.compose.ui.graphics.vector.ImageVector, description: String, click: ()->Unit) {
    val feedback=LocalHapticEngine.current
    Box(Modifier.size(48.dp).clip(CircleShape)
        .background(io.atmos.weather.ui.ThinnOpticalMaterial.raised)
        .border(1.dp,White.copy(alpha=.42f),CircleShape)
        .clickable { feedback?.play(HapticEngine.Event.TAP);click() }, contentAlignment=Alignment.Center) {
        Icon(image,contentDescription=description,tint=White,modifier=Modifier.size(19.dp))
    }
}

@Composable
private fun AppearanceSlider(label: String, current: Float, range: ClosedFloatingPointRange<Float>,
    display: String, commitOnRelease:Boolean=false,onChange: (Float)->Unit) {
    Spacer(Modifier.height(10.dp))
    val denom=(range.endInclusive-range.start).coerceAtLeast(.001f)
    var draft by remember(current) {mutableFloatStateOf(current)}
    val shown=if(commitOnRelease)if(range.endInclusive<=2f) "${(draft*100).roundToInt()}%" else "${draft.roundToInt()}" else display
    GlassTrackingSlider(label,((draft-range.start)/denom).coerceIn(0f,1f),shown,
        onFinished={if(commitOnRelease)onChange(draft)}) {
        draft=range.start+it*denom
        if(!commitOnRelease)onChange(draft)
    }
}

@Composable
private fun HapticSlider(label:String, value:Float, setValue:(Float)->Unit,
    event:HapticEngine.Event, engine:HapticEngine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,color=White,fontSize=15.sp)
        Text("${(value*100).roundToInt()}%",color=TextSoft,fontSize=15.sp)
    }
    Slider(value=value,valueRange=0f..1f,
        colors=SliderDefaults.colors(thumbColor=White,activeTrackColor=brandColor(0xFF328E74),
            inactiveTrackColor=White.copy(alpha=.21f)),onValueChange={
        setValue(it)
        engine.play(HapticEngine.Event.DRAG)
    },onValueChangeFinished={engine.play(event,preview=true)})
}

@Composable
private fun HapticToggle(label:String, checked:Boolean, onChange:(Boolean)->Unit) {
    val feedback=LocalHapticEngine.current
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=TextSoft,fontSize=15.sp,modifier=Modifier.weight(1f))
        Switch(checked=checked,onCheckedChange={onChange(it);feedback?.play(HapticEngine.Event.TAP)})
    }
}


@Composable
private fun GlassCard(enableDrag:Boolean=false, content: @Composable ColumnScope.()->Unit) {
    val feedback=LocalHapticEngine.current
    val appearance=LocalAppearance.current
    val context=LocalContext.current
    val reducedMotion=context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE).getBoolean("reduce_motion",false)
    val horizontalShift=remember { Animatable(0f) }
    val scope=rememberCoroutineScope()
    val damping=(.98f-appearance.springiness*.54f).coerceIn(.35f,.98f)
    val stiffness=Spring.StiffnessMediumLow + appearance.springiness*Spring.StiffnessMedium
    val shape=RoundedCornerShape(io.atmos.weather.ui.ThinnDesignTokens.radiusCard)
    // Weather surface layer is visual only. Condition comes from the real snapshot,
    // not from demo mode or a synthetic forecast. A shared weather store is read
    // without launching a second particle loop for every card.
    val cardMood=io.atmos.weather.ui.TkCardWeatherMood.current
    Box(Modifier.fillMaxWidth().weatherCollisionSurface(io.atmos.weather.ui.ThinnDesignTokens.radiusCard)
        .graphicsLayer {
            translationX=horizontalShift.value
            rotationZ=horizontalShift.value*.022f
        }
        // Horizontal gestures retain vertical parent scrolling on normal swipes.
        .then(if (enableDrag && !reducedMotion) Modifier.pointerInput(appearance.springiness) {
            detectHorizontalDragGestures(
                onDragEnd={feedback?.play(HapticEngine.Event.RELEASE);scope.launch {horizontalShift.animateTo(0f,spring(dampingRatio=damping,stiffness=stiffness))}},
                onDragCancel={feedback?.play(HapticEngine.Event.RELEASE);scope.launch {horizontalShift.animateTo(0f,spring(dampingRatio=damping,stiffness=stiffness))}}
            ) { change, dragAmount ->
                change.consume()
                feedback?.play(HapticEngine.Event.DRAG, variation=(kotlin.math.abs(dragAmount)/12f).coerceIn(.18f, 1f))
                scope.launch { horizontalShift.snapTo((horizontalShift.value+dragAmount*.64f).coerceIn(-52f,52f)) }
            }
        } else Modifier)
        .clip(shape)
        .thinnGlass(io.atmos.weather.ui.rememberAppleReduceTransparency(),io.atmos.weather.ui.ThinnDesignTokens.radiusCard,
            LocalWeatherCardPalette.current?.fill?:MaterialTheme.colorScheme.surface,refract=false,frostScale=1.5f)
        .border(1.dp,Color.White.copy(alpha=.27f),shape)
    ) {
        // Specular sheen follows the direction of a drag; it is not a false claim of live wallpaper blur.
        Canvas(Modifier.matchParentSize().clip(shape)) {
            val x=(horizontalShift.value/52f).coerceIn(-1f,1f)
            val center=Offset(size.width*(.20f+x*.25f),size.height*.10f)
            drawCircle(
                brush=Brush.radialGradient(listOf(White.copy(alpha=appearance.edgeLight*.18f),Color.Transparent),
                    center=center,radius=size.width*.86f),
                radius=size.width*.86f,center=center
            )
        }
        Column(Modifier.fillMaxWidth().clip(shape).padding(19.dp),content=content)
        io.atmos.weather.ui.TkPrecipitationCardLens(cardMood,modifier=Modifier.matchParentSize())
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String,
                       symbol: androidx.compose.ui.graphics.vector.ImageVector,
                       modifier: Modifier=Modifier, onClick:()->Unit) {
    val appearance=LocalAppearance.current
    Box(modifier.weatherCollisionSurface(io.atmos.weather.ui.ThinnDesignTokens.radiusCard)
        .clip(RoundedCornerShape(io.atmos.weather.ui.ThinnDesignTokens.radiusCard))
        .thinnGlass(io.atmos.weather.ui.rememberAppleReduceTransparency(),io.atmos.weather.ui.ThinnDesignTokens.radiusCard,
            LocalWeatherCardPalette.current?.fill?:MaterialTheme.colorScheme.surface,refract=false,frostScale=1.5f)
        .border(1.dp,White.copy(alpha=.19f+appearance.edgeLight*.26f),RoundedCornerShape(io.atmos.weather.ui.ThinnDesignTokens.radiusCard))
        .clickable(onClick=onClick)) {
        Column(Modifier.padding(17.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(symbol,contentDescription=null,tint=TextSoft,modifier=Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(title,color=TextSoft,fontSize=14.sp)
        }
        Spacer(Modifier.height(17.dp))
        Text(value,color=White,fontSize=28.sp,fontWeight=FontWeight.Light,style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"))
        Text(subtitle,color=TextSoft,fontSize=14.sp)
        }
        io.atmos.weather.ui.TkPrecipitationCardLens(io.atmos.weather.ui.TkCardWeatherMood.current,Modifier.matchParentSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSheet(selected: WeatherCity, saved: List<WeatherCity>, results: List<WeatherCity>,
    searching: Boolean, searchExecuted:Boolean,searchError:String?, locationFeedback: LocationFeedback,
    onLocate: ()->Unit, onQuery:(String)->Unit,onSelect:(WeatherCity)->Unit,
    onRemove:(String)->Unit,onClose:()->Unit) {
    io.atmos.weather.ui.CitySearchSheet(selected,saved,results,searching,searchError,locationFeedback,
        onLocate,onQuery,onSelect,onRemove,onClose)
}

/** Visible progress while GPS resolves; success and error are never silently swallowed. */
@Composable
private fun LocationFeedbackBanner(status: LocationFeedback, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val busy = status.inProgress
    Row(
        Modifier.fillMaxWidth().padding(top=10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalWeatherCardPalette.current?.fill ?: if (status.phase == LocationPhase.ERROR || status.phase == LocationPhase.DENIED ||
                status.phase == LocationPhase.DISABLED) Color(0xFF82432E).copy(alpha=.80f)
                else White.copy(alpha=.16f))
            .padding(horizontal=12.dp, vertical=13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(modifier=Modifier.size(20.dp),color=TextSoft,strokeWidth=2.dp)
        } else {
            val image = when (status.phase) {
                LocationPhase.SUCCESS -> Icons.Rounded.CheckCircle
                LocationPhase.DISABLED, LocationPhase.DENIED, LocationPhase.ERROR -> Icons.Rounded.ErrorOutline
                else -> Icons.Rounded.MyLocation
            }
            Icon(image,null,tint=White,modifier=Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (busy) "正在定位" else when(status.phase) {
                LocationPhase.SUCCESS -> "定位成功"
                LocationPhase.DISABLED -> "請開啟定位服務"
                LocationPhase.DENIED -> "需要定位權限"
                else -> "定位未完成"
            },color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            Text(status.detail,color=TextSoft,fontSize=14.sp,lineHeight=17.sp)
        }
        if (!busy && (status.phase==LocationPhase.ERROR || status.phase==LocationPhase.DENIED ||
            status.phase==LocationPhase.DISABLED)) {
            TextButton(onClick={
                if (status.phase==LocationPhase.DISABLED) {
                    try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    catch (_: Exception) { onRetry() }
                } else onRetry()
            }) { Text(if(status.phase==LocationPhase.DISABLED) "開啟設定" else "重試",color=White) }
        }
    }
}

@Composable
private fun TemperatureCurve(hours: List<HourlyWeather>, modifier: Modifier=Modifier) {
    val valid = hours.mapNotNull { it.temperatureC }
    Canvas(modifier) {
        if(valid.size<2) return@Canvas
        val min = valid.minOrNull() ?: 0.0
        val max = valid.maxOrNull() ?: 1.0
        val range = (max-min).coerceAtLeast(2.0)
        val points = valid.mapIndexed { i,value ->
            Offset(i.toFloat()/(valid.size-1)*size.width,
                size.height*.79f-((value-min)/range).toFloat()*size.height*.6f)
        }
        val path=Path().apply { moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)} }
        drawPath(path,White.copy(alpha=.8f),style=Stroke(width=3.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        points.forEach { drawCircle(White.copy(alpha=.87f),2.dp.toPx(),it) }
    }
}


/** Only the isolated sky layer observes frame-by-frame animation state. */
@Composable
private fun SkyEffects(condition:WeatherCondition?, night:Boolean, heavyRain:Boolean,
                       hazy:Boolean, liquidMotion:Float, motion: State<Float>,
                       lightningAlpha:Float, boltSeed:Int, windKmh:Double,
                       weatherCode:Int?, visibilityM:Double?) {
    // The sky animates every frame. Keeping it in its own render layer means a sky frame
    // re-records only the sky, never the backdrop capture or the forecast cards above it.
    Box(Modifier.fillMaxSize().graphicsLayer()) {
        SkyLayers(condition,night,heavyRain,hazy,liquidMotion,motion,lightningAlpha,boltSeed,windKmh,weatherCode,visibilityM)
    }
}

@Composable
private fun SkyLayers(condition:WeatherCondition?, night:Boolean, heavyRain:Boolean,
                       hazy:Boolean, liquidMotion:Float, motion: State<Float>,
                       lightningAlpha:Float, boltSeed:Int, windKmh:Double,
                       weatherCode:Int?, visibilityM:Double?) {
    val phase=if(liquidMotion > .001f) motion.value else 0f
    AtmosphereCanvas(condition ?: WeatherCondition.CLEAR, night, phase, Modifier.fillMaxSize())
    if (hazy && condition != WeatherCondition.FOG) Canvas(Modifier.fillMaxSize()) {
        val offset=sin(phase*6.283185f)*.20f*size.width
        drawOval(Brush.horizontalGradient(listOf(Color.Transparent,Color(0xFFD3CBC3).copy(alpha=.16f),Color.Transparent)),
            Offset(offset-size.width*.3f,size.height*.12f),Size(size.width*.85f,size.height*.49f))
    }
    val weatherSurface = when(condition) {
        WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL, WeatherCondition.FREEZING_RAIN -> WeatherSurface.Kind.RAIN
        WeatherCondition.SNOW, WeatherCondition.SLEET -> WeatherSurface.Kind.SNOW
        WeatherCondition.DUST, WeatherCondition.DUST_STORM -> WeatherSurface.Kind.SAND
        else -> WeatherSurface.Kind.NONE
    }
    // Single time-based scene: no abrupt restarting of particle clocks when the source changes.
    if(weatherSurface==WeatherSurface.Kind.SAND) WeatherSurfaceOverlay(weatherSurface, windKmh.toFloat(), Modifier.fillMaxSize())
    // NEXT secondary layer. Existing sky, brand, forecast layout and pager remain unchanged.
    NextWeatherBackdrop(WeatherScenes.classify(condition,weatherCode,visibilityM),phase,night,precipitation=false)
    if (condition == WeatherCondition.THUNDERSTORM && lightningAlpha > 0f) {
        Canvas(Modifier.fillMaxSize()) {
            val w=size.width; val h=size.height
            val a=(lightningAlpha/.22f).coerceIn(0f,1f)
            drawRect(Color(0xFFE3EEFF).copy(alpha=lightningAlpha*.8f))
            // A real branching channel from the cloud base to the horizon: a wide violet glow,
            // a bright halo and a white-hot core, with thinner forks that fade sooner.
            val seed=boltSeed.coerceAtLeast(1)
            val endX=w*(.18f+((seed ushr 3)%64)/64f*.64f)
            val g=io.atmos.weather.ui.LightningGeometry.create(seed,w,h,endX,h*(.50f+((seed ushr 9)%16)/16f*.12f))
            val top=g.trunk.first()
            drawCircle(Brush.radialGradient(listOf(Color(0xFFDCE6FF).copy(alpha=.42f*a),Color.Transparent),
                center=Offset(top.x,0f),radius=w*.62f),radius=w*.62f,center=Offset(top.x,0f))
            fun channel(points:List<io.atmos.weather.ui.RainCollisionWorld.Point>,scale:Float,fade:Float) {
                if(points.size<2)return
                val path=androidx.compose.ui.graphics.Path().apply{moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)}}
                val join=androidx.compose.ui.graphics.StrokeJoin.Round
                drawPath(path,Color(0xFFA8B6FF).copy(alpha=.16f*a*fade),style=Stroke((16f*scale).dp.toPx(),cap=StrokeCap.Round,join=join))
                drawPath(path,Color(0xFFD9E2FF).copy(alpha=.42f*a*fade),style=Stroke((5.5f*scale).dp.toPx(),cap=StrokeCap.Round,join=join))
                drawPath(path,Color.White.copy(alpha=.97f*a*fade),style=Stroke((1.8f*scale).dp.toPx(),cap=StrokeCap.Round,join=join))
            }
            g.branches.forEach{channel(it,.55f,a)}
            channel(g.trunk,1f,1f)
        }
    }
}

@Composable
private fun AtmosphereCanvas(condition:WeatherCondition, isNight:Boolean, phase:Float, modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        if (!isNight && condition in setOf(WeatherCondition.CLEAR,WeatherCondition.PARTLY_CLOUDY)) {
            val sun=Offset(w*.80f + sin(phase*6.283f)*w*.025f,h*.12f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4B5).copy(alpha=.38f),Color.Transparent),center=sun,radius=w*.57f),radius=w*.57f,center=sun)
            drawCircle(Color(0xFFFFF1BE).copy(alpha=if(condition==WeatherCondition.CLEAR) .76f else .28f),radius=w*.07f,center=sun)
        }
        if (isNight) {
            val moon = Offset(w*.77f,h*.13f)
            drawCircle(brandColor(0xFFE7F7F2).copy(alpha=.72f),radius=w*.043f,center=moon)
            drawCircle(brandColor(0xFF14382E),radius=w*.037f,center=Offset(moon.x+w*.017f,moon.y-w*.011f))
        }
        if (isNight) for(i in 0 until 34) {
            val x=((i*.618033f)%1f)*w
            val y=((i*.2679f)%1f)*h*.65f
            drawCircle(Color.White.copy(alpha=.18f+((i%5)*.10f)),(if(i%7==0) 1.7f else .7f).dp.toPx(),Offset(x,y))
        }
        // Slowly drifting stylized layers, not static gradients.
        for(i in 0..3){
            val x=(i*.38f-.28f)*w+sin(phase*6.283185f+i*.65f)*w*.06f
            val y=h*(.19f+i*.11f)
            drawOval(Color.White.copy(alpha=if(isNight) .035f else .065f),Offset(x,y),Size(w*.65f,h*.068f))
        }
        if (condition in setOf(WeatherCondition.FOG, WeatherCondition.HAZE, WeatherCondition.DUST_STORM)) {
            for (i in 0..6) {
                val x = ((i*.25f) - .35f) * w + sin(phase*6.283185f+i*.5f)*w*.10f
                drawOval(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha=.12f),Color.Transparent)),
                    Offset(x,h*(.09f+i*.115f)),Size(w*.88f,h*.13f))
            }
        }
    }
}

/**
 * Each raindrop has its own independent phase, fall speed, brightness and length.
 * Rain loops only when individual drops leave the screen. No batch restart, no
 * rigid diagonal rain grid. The clock state is read in Canvas drawing only.
 */
@Composable
private fun ContinuousRain(heavy: Boolean, modifier: Modifier = Modifier) {
    val frameNanos = remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameNanos.longValue = it }
        }
    }
    Canvas(modifier.graphicsLayer()) {
        val t = RainParticles.secondsAt(frameNanos.longValue)
        val width=size.width
        val height=size.height
        val density=size.minDimension.coerceAtLeast(1f)
        val count=if (heavy) 230 else 120
        // Heavy rain hangs in sheets: soft lighter bands drifting across the view.
        if(heavy) for(k in 0 until 3) {
            val bx=(((t*.035f+k*.37f)%1f)*1.6f-.3f)*width
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent,Color(0xFFDDE8F2).copy(alpha=.045f),Color.Transparent),startX=bx,endX=bx+width*.45f),
                Offset(bx,0f),Size(width*.45f,height))
        }
        for (i in 0 until count) {
            val d=RainParticles.drop(i,heavy)
            val yn=RainParticles.y(d,t)
            if (yn < -.04f || yn > 1.05f) continue
            val xn=RainParticles.x(d,t)
            val x=xn * width
            val y=yn * height
            val len=(d.length * height).coerceIn(5f,34f)
            val dx=d.slant * width
            val alpha=(d.alpha * (if (heavy) 1.36f else 1f)).coerceAtMost(.39f)
            // A falling drop is seen as a streak: faint at its tail, brightest at its head.
            // Near drops are longer, wider and softer; far ones are thin and faint.
            val head=Offset(x-dx,y+len*(1f+d.depth*.6f))
            drawLine(
                brush=Brush.linearGradient(listOf(Color(0x00E6F3FF),Color(0xFFE6F3FF).copy(alpha=alpha*.55f),Color(0xFFF4FAFF).copy(alpha=alpha*1.15f)),
                    start=Offset(x,y),end=head),
                start=Offset(x,y),end=head,
                strokeWidth=(d.thickness * density / 400f * (if(d.depth>.9f) 1.6f else 1f)).coerceIn(.5f,2.8f),
                cap=StrokeCap.Round
            )
            // Splashes where drops land at the bottom of the screen.
            if(i%5==0 && yn>.93f) {
                val p=((yn-.93f)/.12f).coerceIn(0f,1f)
                val gx=x-dx;val gy=height*.985f
                drawArc(Color(0xFFE6F3FF).copy(alpha=(1f-p)*.30f),200f,140f,false,
                    Offset(gx-(2f+p*9f)*density/400f,gy-(1f+p*3f)*density/400f),Size((4f+p*18f)*density/400f,(2f+p*6f)*density/400f),
                    style=Stroke((.8f*density/400f).coerceAtLeast(.5f)))
                drawCircle(Color(0xFFE6F3FF).copy(alpha=(1f-p)*.35f),(.8f*density/400f).coerceAtLeast(.6f),Offset(gx-p*6f,gy-kotlin.math.sin(p*3.1416f)*8f))
                drawCircle(Color(0xFFE6F3FF).copy(alpha=(1f-p)*.35f),(.8f*density/400f).coerceAtLeast(.6f),Offset(gx+p*7f,gy-kotlin.math.sin(p*3.1416f)*6f))
            }
            // Occasional physically-inspired contacts: each selected drop hits a
            // different glass/card plane and releases a tiny rebound plus ripple.
            // Sampling, not every drop, avoids expensive full-screen particles.
            if (d.depth > .83f && i % 11 == 0) {
                val plane = when (i % 4) {0 -> .43f;1 -> .67f;2 -> .84f;else -> .965f}
                if (yn in plane..(plane+.085f)) {
                    val progress = ((yn-plane)/.085f).coerceIn(0f,1f)
                    val yImpact = plane*height
                    val opacity=(1f-progress)*.23f
                    drawCircle(brandColor(0xFFE9F7F3).copy(alpha=opacity),
                        radius=(1.5f+progress*9f)*density/400f,
                        center=Offset(x-dx*.66f,yImpact),
                        style=Stroke(width=(.7f*density/400f).coerceAtLeast(.5f)))
                    val bounce = kotlin.math.sin(progress*kotlin.math.PI.toFloat()) * (5f + d.depth*8f)
                    drawCircle(brandColor(0xFFE9F7F3).copy(alpha=opacity*.8f),
                        radius=(.6f+d.depth)*density/400f,
                        center=Offset(x+progress*6f, yImpact-bounce))
                }
            }
        }
    }
}

/** Snowflakes drift independently, with a slowly thickening foreground snow shelf. */
@Composable
private fun ContinuousSnow(modifier:Modifier=Modifier) {
    val frame = remember { mutableLongStateOf(System.nanoTime()) }
    val entered = remember { System.nanoTime() }
    LaunchedEffect(Unit) {
        while(isActive) withFrameNanos { frame.longValue=it }
    }
    Canvas(modifier.graphicsLayer()) {
        val elapsed=((frame.longValue-entered).coerceAtLeast(0L)/1_000_000_000.0).toFloat()
        val w=size.width;val h=size.height
        for(i in 0 until 85){
            val d=RainParticles.drop(i+600,false)
            val speed=.014f+d.depth*.041f
            val y=((d.start + elapsed*speed)%1.2f-.10f)*h
            val x=(((d.x + kotlin.math.sin(elapsed*.27f+i*1.2f)*.018f)%1f+1f)%1f)*w
            if(y in 0f..h) drawCircle(Color.White.copy(alpha=.26f+.55f*d.depth),
                radius=(1.2f+d.depth*2.1f).dp.toPx(),center=Offset(x,y))
        }
        val accumulated=(elapsed/120f).coerceIn(0f,1f)
        val shelf=(.6f+3.6f*accumulated).dp.toPx()
        drawRect(brandColor(0xFFF4FBF9).copy(alpha=.18f+.36f*accumulated),
            topLeft=Offset(0f,h-shelf),size=Size(w,shelf))
    }
}

@Composable
private fun SkyParticles(condition: WeatherCondition, phase:Float, heavyRain:Boolean=false, modifier:Modifier=Modifier) {
    val rainy = condition in setOf(WeatherCondition.RAIN,WeatherCondition.THUNDERSTORM)
    val snowy = condition == WeatherCondition.SNOW
    if(!rainy && !snowy) return
    Canvas(modifier) {
        for(i in 0 until if(condition == WeatherCondition.THUNDERSTORM || heavyRain) 70 else if(rainy) 36 else 24) {
            val x=((i*0.6180339f+.1f)%1f)*size.width
            val y=(((i*0.381966f+phase*(if(rainy) 4f else 1f))%1f))*size.height
            if(rainy) drawLine(White.copy(alpha=.17f),Offset(x,y),Offset(x-6.dp.toPx(),y+12.dp.toPx()),1.dp.toPx())
            else drawCircle(White.copy(alpha=.44f),1.8.dp.toPx(),Offset(x,y))
        }
    }
}

@Composable
internal fun DailyGuidanceCard(weather:WeatherSnapshot,stale:Boolean,onOpen:()->Unit) {
    val ctx=LocalContext.current
    var localWarning by remember { mutableStateOf(io.atmos.weather.typhoon.TyphoonWarningStore.current(ctx)) }
    LaunchedEffect(ctx) {
        while(true) {
            localWarning=io.atmos.weather.typhoon.TyphoonWarningStore.current(ctx)
            delay(30_000L)
        }
    }
    val ownCounty=io.atmos.weather.typhoon.TyphoonWarningStore.county(
        ctx.getSharedPreferences("atmos_settings",android.content.Context.MODE_PRIVATE)
            .getString("city_name","").orEmpty())
    val landHere=localWarning?.localLandWarning(ownCounty)==true
    val preferenceRevision by LifestylePreferenceStore.changes.collectAsState()
    val preferences=remember(preferenceRevision){LifestylePreferenceStore.load(ctx)}
    val mode=remember(preferenceRevision){LifestylePreferenceStore.mode(ctx)}
    val plan=remember(weather,stale,mode,preferences){
        LifestyleAdvisor.recommend(weather,mode,stale,preferences=preferences)
    }
    val critical=remember(weather){
        io.atmos.weather.domain.model.WeatherAdviceEngine.suggest(weather).firstOrNull{it.priority>=8}
    }
    val glance=remember(weather,plan){TodayGlance.build(weather,plan)}
    val ink=HomeInk.primary;val soft=HomeInk.secondary
    val warningTone=landHere||localWarning!=null||critical!=null
    io.atmos.weather.ui.ThinnAtmospherePanel(Modifier.testTag("home-today"),accent=Color(0xFFFFD69B)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("今日概況",color=soft,fontSize=14.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                Text(if(stale)"待更新" else glance.mood,color=if(warningTone)Color(0xFFE5554F) else ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold,
                    modifier=Modifier.clip(RoundedCornerShape(999.dp)).background(HomeInk.tile).padding(horizontal=10.dp,vertical=4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(when {
                landHere -> "${localWarning?.name ?: "颱風"}：所在地已列入陸上警戒"
                localWarning?.level==io.atmos.weather.typhoon.TyphoonWarningLevel.LAND -> "中央氣象署已發布陸上颱風警報"
                localWarning!=null -> "中央氣象署已發布海上颱風警報"
                stale -> "天氣資料待更新"
                else -> critical?.heading ?: plan.headline
            },color=ink,fontSize=21.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
            Spacer(Modifier.height(4.dp))
            Text(when {
                landHere -> "盡量避免非必要外出，關好門窗、固定陽台物品；如須出門，留意強風豪雨與道路安全。"
                localWarning?.level==io.atmos.weather.typhoon.TyphoonWarningLevel.LAND -> "部分縣市已列入陸上警戒，出門前請查閱中央氣象署與地方政府公告。"
                localWarning!=null -> "避免海邊、港口與海上活動，持續關注是否升級陸上警報。"
                stale -> "目前顯示舊資料，請重新整理後再確認外出安排。"
                else -> critical?.detail ?: plan.reminder
            },color=soft,fontSize=15.sp,lineHeight=22.sp,maxLines=3)
            if(localWarning!=null) {
                Spacer(Modifier.height(4.dp))
                Text("依中央氣象署公告產生的規則式提醒，非 AI 推測警戒範圍。",color=soft,fontSize=14.sp)
            }
            Spacer(Modifier.height(14.dp))
            HomeTileRow {
                HomeTile("穿什麼",if(stale)"—" else glance.wear,glance.wearNote,Modifier.weight(1f).iosAppear(0),icon=Icons.Rounded.Checkroom,tint=Color(0xFFE58A5B))
                HomeTile("外套",if(stale)"—" else glance.coat,glance.coatNote,Modifier.weight(1f).iosAppear(1),icon=Icons.Rounded.Layers,tint=Color(0xFF7C8FD0))
                HomeTile("雨具",if(stale)"—" else glance.rain,glance.rainNote,Modifier.weight(1f).iosAppear(2),icon=Icons.Rounded.Umbrella,tint=Color(0xFF4F95E0))
            }
            if(!stale&&plan.bring.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text("記得帶",color=soft,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    plan.bring.forEachIndexed{i,item->
                        Text(item,color=ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold,
                            modifier=Modifier.iosAppear(i+3).clip(RoundedCornerShape(999.dp)).background(HomeInk.tile)
                                .padding(horizontal=11.dp,vertical=6.dp))
                    }
                }
            }
            // Everything else folds away like an iOS disclosure row, springing open on tap.
            var more by rememberSaveable { mutableStateOf(false) }
            val chevron by animateFloatAsState(if(more)180f else 0f,spring(.62f,420f),label="更多建議箭頭")
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(.5.dp).background(HomeInk.hairline))
            Row(Modifier.fillMaxWidth().padding(top=4.dp).iosJellyPress().clip(RoundedCornerShape(14.dp))
                .clickable{more=!more}.padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(more)"收合" else "穿搭細節、出門時機與方式",color=ink,fontSize=15.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore,null,tint=soft,modifier=Modifier.graphicsLayer{rotationZ=chevron})
            }
            androidx.compose.animation.AnimatedVisibility(more,
                enter=androidx.compose.animation.expandVertically(spring(.78f,380f))+androidx.compose.animation.fadeIn(),
                exit=androidx.compose.animation.shrinkVertically(spring(.9f,500f))+androidx.compose.animation.fadeOut()) {
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Spacer(Modifier.height(2.dp))
                    HomeAdviceRow("上衣下著",if(stale) "待更新" else "${plan.top}；${plan.bottoms}")
                    HomeAdviceRow("外套",if(stale) "待更新" else plan.coatDecision)
                    HomeAdviceRow("雨具",if(stale) "待更新" else plan.umbrella)
                    if(!stale) HomeAdviceRow("出門時機",plan.timeAdvice)
                    Text("出門方式",color=soft,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    io.atmos.weather.ui.GlassChoiceGroup(OutingMode.entries.map{it.title},mode.ordinal,
                        {LifestylePreferenceStore.mode(ctx,OutingMode.entries[it])})
                    TextButton(onClick=onOpen, modifier=Modifier.align(Alignment.End)) {
                        Text("完整穿搭清單與個人偏好",color=ink,fontSize=15.sp,fontWeight=FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAdviceRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.Top) {
        Text(label,color=HomeInk.secondary,fontSize=15.sp,fontWeight=FontWeight.SemiBold,
            modifier=Modifier.width((80*LocalDensity.current.fontScale).dp))
        Text(value,color=HomeInk.primary,fontSize=15.sp,lineHeight=22.sp,modifier=Modifier.weight(1f))
    }
}

@Composable
private fun HomeKeyWeatherCard(weather: WeatherSnapshot, stale: Boolean) {
    // The four actionable facts are ALWAYS on the homepage: no collapsed detail sheet.
    val highlights = remember(weather) { HomeWeatherBriefing.build(weather) }
    io.atmos.weather.ui.ThinnAtmospherePanel(accent=brandColor(0xFFA1DECD)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("現在最重要的天氣", color = White, fontSize = 21.sp,
                lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
            Text("未來 6 小時", color=TextSoft, fontSize = 14.sp)
        }
        if (stale) {
            Text("目前使用離線快取，資訊可能過時", color = Color(0xFFFFDB9F),
                fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        }
        highlights.chunked(2).forEachIndexed { rowIndex, facts ->
            if (rowIndex > 0) {
                HorizontalDivider(color = Color.White.copy(alpha = .11f))
            }
            Row(Modifier.fillMaxWidth()) {
                facts.forEach { fact ->
                    Column(Modifier.weight(1f)
                        .padding(horizontal = 16.dp, vertical = 15.dp)) {
                        Text(fact.label, color=TextSoft, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, lineHeight = 22.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(fact.value, color = White, fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold, lineHeight = 35.sp,
                            maxLines = 2)
                        Spacer(Modifier.height(3.dp))
                        Text(fact.note, color=TextSoft, fontSize = 14.sp,
                            lineHeight = 20.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

private fun WeatherCondition.label() = when(this){
    WeatherCondition.CLEAR -> "晴朗";WeatherCondition.PARTLY_CLOUDY -> "晴時多雲"
    WeatherCondition.CLOUDY -> "多雲";WeatherCondition.FOG -> "有霧"
    WeatherCondition.HAZE -> "霾";WeatherCondition.DUST -> "揚塵";WeatherCondition.DUST_STORM -> "強烈揚塵"
    WeatherCondition.HAIL -> "冰雹";WeatherCondition.SLEET -> "雨夾雪";WeatherCondition.FREEZING_RAIN -> "凍雨"
    WeatherCondition.RAIN -> "正在下雨";WeatherCondition.SNOW -> "正在下雪"
    WeatherCondition.THUNDERSTORM -> "雷雨";WeatherCondition.UNKNOWN -> "天氣資訊更新中"
}
private fun LandmarkId.label()=when(this){
    LandmarkId.TAIPEI_101 -> "臺北 101 · 微縮城市"
    LandmarkId.TOKYO_SKYTREE -> "東京晴空塔 · 微縮城市"
    LandmarkId.EIFFEL_TOWER -> "艾菲爾鐵塔 · 微縮城市"
    LandmarkId.WEATHER_COTTAGE -> "微縮氣象小屋"
    LandmarkId.TAIWAN_COUNTY -> "縣市微縮景觀"
}
