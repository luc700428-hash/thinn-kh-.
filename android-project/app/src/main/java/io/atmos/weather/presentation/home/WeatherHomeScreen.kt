package io.atmos.weather.presentation.home

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
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.window.Dialog
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
import io.atmos.weather.lifestyle.LifestyleAiSettingsPage
import io.atmos.weather.domain.visual.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

private val White = Color.White
private val TextSoft = Color(0xFFE5F2FC)
private val GlassShape = RoundedCornerShape(30.dp)

/** Center the temperature DIGITS; position the degree glyph on their right. */
@Composable
private fun CenteredTemperature(value: Int, appearance: WeatherAppearance) {
    var digitsWidthPx by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth().height(145.dp),contentAlignment=Alignment.Center) {
        Text("$value",style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"),
            fontSize=(104f*appearance.fontScale).coerceIn(80f,126f).sp,lineHeight=133.sp,
            fontWeight=FontWeight(appearance.fontWeight.roundToInt()),fontFamily=appearance.family,
            color=White,textAlign=TextAlign.Center,onTextLayout={ digitsWidthPx=it.size.width })
        Text("°",fontSize=(62f*appearance.fontScale).coerceIn(48f,78f).sp,
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
    val stormy = snapshot?.condition == WeatherCondition.THUNDERSTORM
    val rainy = snapshot?.condition in setOf(WeatherCondition.RAIN,WeatherCondition.THUNDERSTORM,WeatherCondition.FREEZING_RAIN,WeatherCondition.HAIL)
    val heavyRain = snapshot?.weatherCode in setOf(65,67,82,95,96,99)
    val snow = (snapshot?.temperatureC ?: 100.0) <= 5.0 && snapshot?.condition in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)
    val hazy = (snapshot?.aerosolOpticalDepth ?: 0.0) >= .45 || snapshot?.condition in setOf(WeatherCondition.HAZE, WeatherCondition.DUST, WeatherCondition.DUST_STORM)
    val colors = when {
        snapshot?.condition == WeatherCondition.DUST_STORM -> listOf(Color(0xFF57412F), Color(0xFFAB8055), Color(0xFFE0B884))
        snapshot?.condition == WeatherCondition.DUST -> listOf(Color(0xFF78654E),Color(0xFFB89B74),Color(0xFFE7D7B6))
        stormy -> listOf(Color(0xFF0C1930), Color(0xFF273E59), Color(0xFF526E86))
        isNight -> listOf(Color(0xFF071328), Color(0xFF1B3556), Color(0xFF465776))
        snapshot?.condition == WeatherCondition.FOG -> listOf(Color(0xFF677E8F), Color(0xFF9AABB4), Color(0xFFC4CDD1))
        rainy -> listOf(Color(0xFF102F48), Color(0xFF386883), Color(0xFF82A6B6))
        hazy -> listOf(Color(0xFF738497), Color(0xFF9BA3AA), Color(0xFFD5C8B9))
        snow -> listOf(Color(0xFF4A7296), Color(0xFF88AEC8), Color(0xFFCAE2F0))
        else -> listOf(Color(0xFF1F67A0), Color(0xFF64B5DD), Color(0xFFA8DDEC))
    }
    val blendTop by animateColorAsState(colors[0], animationSpec=tween(1600), label="sky top")
    val blendMid by animateColorAsState(colors[1], animationSpec=tween(1600), label="sky middle")
    val blendLow by animateColorAsState(colors[2], animationSpec=tween(1600), label="sky lower")
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
    LaunchedEffect(appearance) {
        appearance.persist(context)
        delay(800L) // avoid redrawing 25 widgets on every drag frame
        WidgetSync.refreshAll(context)
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
    val mainPagerScope = rememberCoroutineScope()
    fun showMainTab(index: Int) {
        mainPagerScope.launch { mainPager.animateScrollToPage(index.coerceIn(0,3)) }
    }
    LaunchedEffect(fromEarthquakeNotification) { if (fromEarthquakeNotification) (context as? android.app.Activity)?.intent?.removeExtra("open_earthquake_center") }
    val fromUpdateNotification = remember { (context as? android.app.Activity)?.intent?.getBooleanExtra("open_update_center",false) == true }
    var updatePageOpen by remember { mutableStateOf(fromUpdateNotification) }
    var typhoonPageOpen by remember { mutableStateOf(false) }
    var naturalVoiceOpen by remember { mutableStateOf(false) }
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
                val snapshot=io.atmos.weather.typhoon.CwaWindProbability.read(context)
                value=snapshot?.at(state.city.coordinate.longitude,state.city.coordinate.latitude)
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
    var weatherAlarmOpen by remember { mutableStateOf(false) }
    LaunchedEffect(fromUpdateNotification) { if (fromUpdateNotification) (context as? android.app.Activity)?.intent?.removeExtra("open_update_center") }
    // Android predictive-back / gesture returns to the previous UI layer,
    // not to Activity.finish() while tools, seismic, settings or sheets are open.
    BackHandler(enabled=locationOpen || settingsOpen || lifestyleSettingsOpen ||
        pushSettingsOpen || diagnosticsOpen || briefingOpen || tasksOpen ||
        updatePageOpen || earthquakePageOpen || typhoonPageOpen || weatherAlarmOpen || naturalVoiceOpen ||
        mainPager.currentPage != 0) {
        when {
            naturalVoiceOpen -> naturalVoiceOpen=false
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
    var soundOn by remember { mutableStateOf(settingsPrefs.getBoolean("sound_enabled", false)) }
    var soundVolume by remember { mutableFloatStateOf(settingsPrefs.getFloat("sound_volume", .50f)) }
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
    var lightningAlpha by remember { mutableFloatStateOf(0f) }
    var lightningBolt by remember { mutableIntStateOf(0) }
    var thunderCue by remember { mutableIntStateOf(0) }
    // Lightning is not periodic: a single lifecycle-aware clock also schedules
    // the delayed thunder cue. Both sound and flash derive from one event.
    LaunchedEffect(lifecycleOwner, stormy) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (stormy) {
                while (isActive) {
                    delay(kotlin.random.Random.nextLong(9_000L, 24_000L))
                    lightningBolt++
                    lightningAlpha = .28f
                    delay(60L)
                    lightningAlpha = .035f
                    delay(90L)
                    lightningAlpha = .38f
                    delay(75L)
                    lightningAlpha = 0f
                    // Lightning is seen before thunder; vary simulated distance.
                    delay(kotlin.random.Random.nextLong(400L, 1_800L))
                    thunderCue++
                    if (hapticSettings.enabled && hapticSettings.thunder) {
                        hapticEngine.play(HapticEngine.Event.THUNDER)
                    }
                }
            } else {
                lightningAlpha = 0f
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
                                    lightningBolt += 1
                                    lightningAlpha = .52f
                                    demoThunderCue += 1
                                    hapticEngine.play(HapticEngine.Event.THUNDER, preview=true, variation=.85f)
                                } else {
                                    lightningAlpha = .10f
                                    hapticEngine.play(HapticEngine.Event.RAIN_DROP, preview=true, variation=.62f)
                                }
                            }
                            else -> {
                                demoDescription = "雨勢漸緩 · 天氣恢復"
                                demoCondition = WeatherCondition.RAIN
                                lightningAlpha = 0f
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
                    lightningAlpha = 0f
                }
            }
        }
    }
    LaunchedEffect(demoCompleted) {
        if (demoCompleted) { delay(2500L); demoCompleted = false }
    }
    NextWeatherAmbience(demoCondition ?: snapshot?.condition, soundOn || demoRemaining > 0,
        if(demoRemaining > 0) soundVolume.coerceAtLeast(.5f) else soundVolume,
        if (demoCondition == WeatherCondition.THUNDERSTORM) 95 else if (demoCondition == WeatherCondition.RAIN) 61 else snapshot?.weatherCode,
        snapshot?.visibilityM, thunderCue + demoThunderCue)
    var details by remember { mutableStateOf<Pair<String,String>?>(null) }
    var showLifestyleDetails by remember { mutableStateOf(false) }
    val reducedMotion = settingsPrefs.getBoolean("reduce_motion",false)
    val transition = rememberInfiniteTransition(label="sky")
    // Keep the animation value as State. Reading it in this parent would recompose the
    // complete forecast screen every frame (including scrolling and lists).
    val motionState: State<Float> = if(reducedMotion) rememberUpdatedState(0f)
        else transition.animateFloat(0f,1f,infiniteRepeatable(
            tween((18000 - appearance.liquidMotion * 13000).toInt(), easing=LinearEasing)),label="weather motion")
    // At most a handful of impacts are sampled, tied to actual particle wraps.
    // Screen weather audio and haptic effects stop when this lifecycle stops.
    LaunchedEffect(lifecycleOwner, snapshot?.condition, heavyRain,
        hapticSettings.enabled, hapticSettings.rainDrops, hapticSettings.thunder) {
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

    CompositionLocalProvider(
        LocalAppearance provides appearance,
        LocalHapticEngine provides hapticEngine
    ) {
    io.atmos.weather.ui.GlassAppTheme {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(blendTop,blendMid,blendLow)))) {
            // Avoid a large offscreen blur layer during scrolling: gradient already supplies depth.
            HorizontalPager(
                state = mainPager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                userScrollEnabled = !locationOpen && !settingsOpen && !briefingOpen && !tasksOpen && !updatePageOpen && !diagnosticsOpen
            ) { page ->
            when (page) {
            0 -> {
                // Each Pager item MUST have exactly one layout root. The former
                // SkyEffects full-screen canvases and forecast Column were emitted
                // as independent page roots, allowing the scenery to occupy the
                // page while all of the foreground forecast was laid out offscreen.
                Box(Modifier.fillMaxSize()) {
                    // Full-screen background layer. It never captures gestures.
                    SkyEffects(demoCondition ?: snapshot?.condition, isNight,
                        heavyRain || demoCondition == WeatherCondition.THUNDERSTORM,
                        hazy, appearance.liquidMotion, motionState, if(reducedMotion)0f else lightningAlpha,
                        lightningBolt, snapshot?.windKmh ?: 0.0, snapshot?.weatherCode, snapshot?.visibilityM)
                    // A fixed dark scrim prevents white copy washing out against bright daylight skies.
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                        Color(0x9807192C),Color(0x4207192C),Color(0x9B07192C)))))
                    // Keep location, temperature, cards, loading and errors above
                    // the background, independently of whether weather data loads.
                    Column(Modifier.fillMaxSize().zIndex(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal=20.dp).statusBarsPadding().padding(top=18.dp,bottom=124.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.brand_symbol),"thinn-khì 透明品牌圖示",
                            modifier=Modifier.size(37.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("thinn-khì",color=White,fontFamily=appearance.family,fontWeight=FontWeight(appearance.fontWeight.roundToInt()),fontSize=(21f*appearance.fontScale).coerceIn(20f,29f).sp,letterSpacing=.4.sp)
                            Text("現在的天氣",color=TextSoft,fontSize=14.sp)
                        }
                    }
                    Row {
                        if(io.atmos.weather.CrashDiagnostics.last(context)!=null) {
                            HeaderButton(Icons.Rounded.BugReport,"檢視上次閃退紀錄") { diagnosticsOpen=true }
                            Spacer(Modifier.width(6.dp))
                        }
                        HeaderButton(Icons.Rounded.Search,"搜尋城市") { locationOpen=true }
                        Spacer(Modifier.width(8.dp))
                        if (state.loading) {
                            Box(Modifier.size(37.dp),contentAlignment=Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp),color=White,strokeWidth=2.dp)
                            }
                        } else HeaderButton(Icons.Rounded.Refresh,"更新資料") {
                            refreshRequested = true
                            refreshText = "正在取得最新天氣…"
                            viewModel.onIntent(WeatherIntent.Refresh)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = demoRemaining > 0 || demoCompleted,
                    enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut(tween(200)) + androidx.compose.animation.shrinkVertically()
                ) {
                    Column(Modifier.fillMaxWidth().padding(top=12.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF355D7A).copy(alpha=.78f))
                        .border(1.dp,White.copy(alpha=.36f),RoundedCornerShape(19.dp))
                        .padding(13.dp)) {
                        Text(if(demoRemaining > 0) "模擬天氣演示中 · 剩餘 $demoRemaining 秒" else "10 秒天氣示範已結束",
                            color=White,fontWeight=FontWeight.SemiBold,fontSize=16.sp)
                        if(demoRemaining > 0) {
                            Text(demoDescription,color=TextSoft,fontSize=14.sp)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress={ (10-demoRemaining)/10f },
                                modifier=Modifier.fillMaxWidth(),color=Color(0xFF9ADFFF),trackColor=White.copy(alpha=.22f))
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
                        .padding(top=10.dp).background(White.copy(alpha=.13f),RoundedCornerShape(14.dp))
                        .padding(horizontal=12.dp, vertical=8.dp),color=White,fontSize=15.sp,textAlign=TextAlign.Center)
                }
                Spacer(Modifier.height(28.dp))
                // Weather and real place name form ONE immersive hero.
                Row(Modifier.fillMaxWidth().clickable { locationOpen=true },
                    horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place,null,tint=White.copy(alpha=.87f),modifier=Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(io.atmos.weather.location.LocationLabels.display(state.city.name),maxLines=2,
                        overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=White,fontSize=(23f*appearance.fontScale).coerceIn(21f,32f).sp,fontFamily=appearance.family,fontWeight=FontWeight(appearance.fontWeight.roundToInt()),textAlign=TextAlign.Center)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown,null,tint=White,modifier=Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (snapshot != null) {
                    io.atmos.weather.next.V25HomeHero(
                        temperature=snapshot.temperatureC.roundToInt(),
                        condition=snapshot.condition.name,
                        conditionLabel=if(heavyRain && !stormy) "強降雨" else snapshot.condition.label(),
                        high=snapshot.maxTempC?.roundToInt(),
                        low=snapshot.minTempC?.roundToInt(),
                        feelsLike=snapshot.feelsLikeC?.roundToInt(),
                        isNight=!snapshot.isDay,
                        rainProbability=snapshot.nextHourRainProbabilityPct
                    )
                } else if(state.loading) {
                    Spacer(Modifier.height(60.dp)); CircularProgressIndicator(color=White,modifier=Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text("尚無天氣資料，請檢查網路連線",color=White,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                }
                // Show official sea/land warnings on the red banner, and only Taiwan-bound
                // forecast tracks on the separate tile. Never make the tile permanent.
                verifiedTyphoonWarning?.let { official ->
                    Column(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF6D1F32),Color(0xFF2B253B))))
                        .border(1.dp,Color(0xFFFF9E9C).copy(alpha=.65f),RoundedCornerShape(24.dp))
                        .clickable { typhoonPageOpen=true }
                        .padding(17.dp)) {
                        val land=official.level==io.atmos.weather.typhoon.TyphoonWarningLevel.LAND
                        Text(if(land) "中央氣象署 · 陸上颱風警報" else "中央氣象署 · 海上颱風警報",
                            fontSize=20.sp,fontWeight=FontWeight.Bold,color=White)
                        Spacer(Modifier.height(5.dp))
                        Text("官方發布：${official.issuedAt.atZone(java.time.ZoneId.of("Asia/Taipei"))}",
                            fontSize=14.sp,color=White.copy(alpha=.83f))
                        Spacer(Modifier.height(9.dp))
                        val regions=if(land)official.landCounties else official.seaAreas
                        Text(if(land)"陸上警戒縣市" else "海上警戒海域",fontSize=15.sp,color=White)
                        Spacer(Modifier.height(6.dp))
                        if(regions.isEmpty()) {
                            Text("官方公告未提供可核對的警戒區域清單；請開啟官方警報地圖。",
                                color=White.copy(alpha=.9f),fontSize=14.sp)
                        } else {
                            // Red verified-region BLOCKS, not fabricated county polygons.
                            regions.chunked(3).forEach { group ->
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    group.forEach { area ->
                                        Box(Modifier.padding(vertical=3.dp)
                                            .background(Color(0xFFC0393E),RoundedCornerShape(10.dp))
                                            .padding(horizontal=8.dp,vertical=7.dp)) {
                                            Text(area,color=White,fontSize=14.sp)
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
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF17405D),Color(0xFF1F5675))))
                        .border(1.dp, Color(0xFF94D7EE).copy(alpha=.56f), RoundedCornerShape(22.dp))
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
                                Text("已發布正式颱風警報 · 點選查看",color=Color(0xFFFFD8B9),fontSize=14.sp)
                            else Text("尚非正式海上／陸上颱風警報",color=Color(0xFFBDE4F6),fontSize=14.sp)
                            Text("七級風暴風圈通過機率 · ${io.atmos.weather.location.LocationLabels.microcity(state.city.name)}：${homeWindProbability ?: "暫無官方機率"}",
                                color=Color(0xFFFFDEA7),fontSize=14.sp,maxLines=2)

                        }
                        Icon(Icons.Rounded.ChevronRight,contentDescription="查看颱風動向",tint=TextSoft)
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    // NEXT_INTEGRATED_TYPOON_NEUTRAL_ENTRY: ordinary navigation, not an emergency alert.
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF183C57),Color(0xFF214860))))
                        .border(1.dp,Color(0xFFB1E3F5).copy(alpha=.35f),RoundedCornerShape(22.dp))
                        .clickable { typhoonPageOpen = true }
                        .padding(horizontal=17.dp,vertical=15.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Cyclone,null,tint=Color(0xFF9ED8F1),modifier=Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("颱風資訊",fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=White)
                            Text("目前未顯示臺灣相關有效颱風警報 · 點選查看官方資料",
                                fontSize=14.sp,color=TextSoft,maxLines=2)
                        }
                        Icon(Icons.Rounded.ChevronRight,"開啟颱風資訊",tint=TextSoft)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // A recent verified POST-event report is a time-limited, source-labelled
                // red home card. Never describe third-party data as CWA-issued.
                recentEarthquake?.let { report ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7C2540),Color(0xFF431E32))))
                        .border(1.dp,Color(0xFFFFB6B2).copy(alpha=.7f),RoundedCornerShape(21.dp))
                        .padding(15.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber,contentDescription=null,tint=Color.White)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if(report.sourceOfficial) "中央氣象署 · 最新地震報告" else "最新地震報告 · 第三方彙整",
                                    fontSize=17.sp,fontWeight=FontWeight.Bold,color=White)
                                Text("震後資料，並非提前地震預警",fontSize=14.sp,color=White.copy(alpha=.9f))
                            }
                            TextButton(onClick={
                                RecentEarthquakeNoticeStore.dismiss(context,report.id)
                                recentEarthquake=null
                            }) { Text("關閉",fontSize=14.sp,color=White) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${report.location} · 規模 ${"%.1f".format(java.util.Locale.US,report.magnitude)}",
                            color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("收到後顯示 30 分鐘；有新的已驗證報告會自動更新。",
                            color=White.copy(alpha=.85f),fontSize=14.sp)
                        if(report.sourceOfficial && report.officialUrl.startsWith("https://")) {
                            TextButton(onClick={uriHandler.openUri(report.officialUrl)}) {
                                Text("查看官方報告",color=White,fontSize=15.sp)
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7C2540),Color(0xFF431E32))))
                        .border(1.dp,Color(0xFFFFBCAC).copy(alpha=.72f),RoundedCornerShape(20.dp))
                        .clickable { uriHandler.openUri(io.atmos.weather.alerts.CwaCountyHazardFeed.OFFICIAL_URL) }
                        .padding(16.dp)) {
                        Text("中央氣象署 · ${hazard.title}",color=White,
                            fontSize=18.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        val end = hazard.expiresAt.atZone(java.time.ZoneId.of("Asia/Taipei"))
                        Text("${hazard.county} · 有效至 ${end.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))}",
                            color=White.copy(alpha=.9f),fontSize=14.sp)
                        Text("查看警戒區域與防護建議 ↗",
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
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7C2540),Color(0xFF431E32))))
                        .border(1.dp,Color(0xFFFFB4AE).copy(alpha=.64f),RoundedCornerShape(22.dp))
                        .clickable { uriHandler.openUri(notice.link) }.padding(17.dp)) {
                        Text("中央氣象署 · ${notice.title}",color=White,
                            fontSize=18.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
                        Spacer(Modifier.height(7.dp))
                        Text(notice.summary.ifBlank { "查看公告內容與受影響地區" }.take(98),
                            color=White.copy(alpha=.94f),fontSize=14.sp,maxLines=3)
                        Spacer(Modifier.height(7.dp))
                        Text("${notice.issuedAt.atZone(java.time.ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))} 發布 · 查看詳情 ↗",
                            color=Color(0xFFFFDBD7),fontSize=14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (snapshot != null) {
                    // Weather always takes precedence over decor: show real six-hour facts
                    // and daily clothing/rain advice WITHOUT a tap or a hidden details sheet.
                    Spacer(Modifier.height(16.dp))
                    HomeKeyWeatherCard(snapshot, state.stale)
                }
                Spacer(Modifier.height(13.dp))
                // Official alerts and the six-hour forecast are visible before outfit advice.
                if(snapshot != null) {
                    DailyGuidanceCard(snapshot,state.stale) { showLifestyleDetails=true }
                }
                // Atmospheric light and live data replace all 2D landmark galleries and
                // non-verifiable landmark imagery. This content never claims to be a photo.
                if(snapshot != null) {
                    Spacer(Modifier.height(15.dp))
                    io.atmos.weather.ui.ThinnAtmosphereFocus(
                        snapshot=snapshot,
                        location=io.atmos.weather.location.LocationLabels.display(state.city.name),
                        reducedMotion=reduceCityMotion
                    )
                    Spacer(Modifier.height(14.dp))
                }
                if(snapshot != null) {
                    GlassCard {
                        // Horizontally draggable curve with a synchronized selected-hour readout.
                        InteractiveForecast(snapshot.nextHours.take(24),snapshot.timeZoneId, sunrise=snapshot.sunrise, sunset=snapshot.sunset)
                    }
                    Spacer(Modifier.height(15.dp))
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
                    // All six weather indicators (including pressure and sunrise/sunset)
                    // are now members of the same long-press sortable card grid.
                    // Air-quality card deliberately remains the last metric section.
                    GlassCard {
                        Text("空氣品質與能見度",color=White,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
                        Spacer(Modifier.height(10.dp))
                        AirQualityInsightCard(snapshot)
                        Spacer(Modifier.height(12.dp))
                        VisibilityInsightCard(snapshot, hazy)
                        Spacer(Modifier.height(10.dp))
                        Text("空氣品質為模型推估，非環境部官方 AQI。",color=TextSoft,fontSize=15.sp)
                    }
                }
                if(state.loading && snapshot!=null) {
                    Spacer(Modifier.height(14.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(),color=White)
                }
                if(state.stale || state.error != null) {
                    Spacer(Modifier.height(13.dp))
                    Text(if(state.stale) "目前顯示離線快取，可能已過時" else "更新失敗：${state.error}",
                        color=Color(0xFFFFE4C6),fontSize=15.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                }
                Spacer(Modifier.height(22.dp))
                Text("數據提供：Open-Meteo.com  ·  依其授權條款使用",color=TextSoft,fontSize=14.sp,
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("天氣是預測資訊，請以當地官方警報為準。",color=TextSoft,fontSize=14.sp,
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                    } // forecast Column: always overlays the animation
                } // page Box: one measurable root for HorizontalPager
            } // page 0
        // 四頁由單一 Foundation HorizontalPager 承接，頁面與底部分頁保持同步。
            1 -> SeismicPrimaryTab(onExit = { showMainTab(0) })
            2 -> AppGlassToolsPage(
                onBriefings = { briefingOpen = true },
                onUpdates = { updatePageOpen = true },
                onDiagnostics = { diagnosticsOpen = true },
                onSeismic = { showMainTab(1) },
                onTasks = { tasksOpen = true },
                onWeatherAlarms = { weatherAlarmOpen = true },
                onTyphoons = { typhoonPageOpen = true })
            3 -> AppGlassSettingsPage(
                appearance = appearance,
                onAppearance = { appearance = it },
                automaticUpdates = automaticUpdateNotices,
                onUpdates = { enabled ->
                    automaticUpdateNotices = enabled
                    settingsPrefs.edit().putBoolean("check_updates_automatically", enabled).apply()
                    io.atmos.weather.update.UpdateCheckWorker.schedule(context)
                },
                onAdvanced = { settingsOpen = true },
                onDiagnostics = { diagnosticsOpen = true },
                onLifestyle = { lifestyleSettingsOpen = true },
                onPushSettings = { pushSettingsOpen = true },
                onUpdateCenter = { updatePageOpen = true },
                onWeatherAlarms = { weatherAlarmOpen = true },
                onTyphoons = { typhoonPageOpen = true },
                onNaturalVoice = { naturalVoiceOpen=true },
                onWeather = { showMainTab(0) },
                onTasks = { tasksOpen=true },
                onBriefings = { briefingOpen=true },
                onTools = { showMainTab(2) })
            } // when(page)
            } // HorizontalPager
        if (locationOpen) LocationSheet(state.city,state.savedCities,state.searchResults,state.searching,
            state.searchExecuted,state.searchError,state.locationFeedback,onLocate=onLocate,
            onQuery=viewModel::search,
            onSelect={ viewModel.addSavedCity(it);viewModel.onIntent(WeatherIntent.SelectCity(it));locationOpen=false },
            onRemove=viewModel::removeSavedCity,onClose={locationOpen=false})
        if (settingsOpen) Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0A2139),Color(0xFF225875))))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal=24.dp).padding(top=54.dp,bottom=130.dp)) {
                Text("進階偏好設定",fontSize=26.sp,fontWeight=FontWeight.Bold,color=White)
                Text("這是獨立設定畫面；功能清單請到「工具」分頁。",fontSize=14.sp,color=TextSoft)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                io.atmos.weather.ui.GlassAction("返回設定首頁",onClick={settingsOpen=false})
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick={settingsOpen=false;diagnosticsOpen=true},modifier=Modifier.fillMaxWidth()) {
                    Text("檢查設定是否真的生效 · 權限／排程／地震",color=White)
                }
                Spacer(Modifier.height(22.dp))
                GlassToggle("天氣環境音", "晴天、雨天、雷雨與降雪",soundOn) {
                    soundOn=it;settingsPrefs.edit().putBoolean("sound_enabled",it).apply()
                }
                GlassTrackingSlider("環境音量",soundVolume) {
                    soundVolume=it;settingsPrefs.edit().putFloat("sound_volume",it).apply()
                }
                Text("僅在 App 前景播放。首次安裝預設開啟、音量 50%；可隨時關閉。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color=White.copy(alpha=.13f))
                Spacer(Modifier.height(20.dp))
                Text("天氣資訊更新頻率",fontSize=19.sp,color=White,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("App 開啟期間每 $refreshMinutes 分鐘自動更新",color=TextSoft,fontSize=15.sp)
                Spacer(Modifier.height(12.dp))
                GlassTrackingSlider("更新頻率",(refreshMinutes-5)/115f) { fraction ->
                    refreshMinutes=(5+fraction*115).roundToInt().coerceIn(5,120)
                    settingsPrefs.edit().putInt("refresh_minutes",refreshMinutes).apply()
                    WidgetSync.schedule(context)
                }
                Spacer(Modifier.height(15.dp))
                HorizontalDivider(color=White.copy(alpha=.13f))
                Spacer(Modifier.height(17.dp))
                Text("字體與玻璃視覺",fontSize=19.sp,color=White,fontWeight=FontWeight.SemiBold)
                Text("文字最低維持清晰可讀大小，支援 100–150% 調整；首頁與其他頁面共用新版字階。部分特殊地圖圖例仍需要實機確認。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(10.dp))
                AppearanceSlider("字體大小",appearance.fontScale,1f..1.5f,
                    "${(appearance.fontScale*100).roundToInt()}%") {appearance=appearance.copy(fontScale=it)}
                AppearanceSlider("字體粗細",appearance.fontWeight,100f..900f,
                    "${appearance.fontWeight.roundToInt()}") {appearance=appearance.copy(fontWeight=it)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf("系統","襯線","等寬","手寫").forEachIndexed { index,title ->
                        val chosen=index==appearance.fontChoice
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                            .background(White.copy(alpha=if(chosen) .23f else .07f))
                            .clickable {appearance=appearance.copy(fontChoice=index)}
                            .padding(vertical=11.dp),contentAlignment=Alignment.Center) {
                            Text(title,color=White,fontSize=14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AppearanceSlider("玻璃霧化",appearance.glassFrost,0f..1f,
                    "${(appearance.glassFrost*100).roundToInt()}%") {appearance=appearance.copy(glassFrost=it)}
                AppearanceSlider("玻璃透明感",appearance.glassOpacity,0f..1f,
                    "${(appearance.glassOpacity*100).roundToInt()}%") {appearance=appearance.copy(glassOpacity=it)}
                AppearanceSlider("玻璃邊緣高光",appearance.edgeLight,0f..1f,
                    "${(appearance.edgeLight*100).roundToInt()}%") {appearance=appearance.copy(edgeLight=it)}
                AppearanceSlider("液態流動速度",appearance.liquidMotion,0f..1f,
                    "${(appearance.liquidMotion*100).roundToInt()}%") {appearance=appearance.copy(liquidMotion=it)}
                AppearanceSlider("手勢回彈彈性",appearance.springiness,0f..1f,
                    "${(appearance.springiness*100).roundToInt()}%") {appearance=appearance.copy(springiness=it)}
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color=White.copy(alpha=.13f))
                Spacer(Modifier.height(18.dp))
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
                        fontWeight=FontWeight(appearance.fontWeight.roundToInt()),color=White)
                    Spacer(Modifier.height(6.dp))
                    Text("輕輕向左右拖曳這張卡片，感受回彈與玻璃高光變化。",color=TextSoft,fontSize=14.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick={appearance=WeatherAppearance()}) {
                    Text("恢復預設外觀",color=White)
                }
                Spacer(Modifier.height(9.dp))
                Text("桌面與背景更新最快約每 15 分鐘排程一次，實際可能受系統省電措施延遲；Widget 會同步最近一次天氣快照。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(20.dp))
                Text("空氣品質",color=White,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("顯示所在地的空氣品質資訊。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color=White.copy(alpha=.13f))
                Spacer(Modifier.height(18.dp))
                Text("天氣警特報提醒",color=White,fontSize=17.sp)
                Text("APP 通知統一優先使用焦點通知／超級島；是否上島仍由系統授權與小米核准情境決定。未獲支援時以一般通知呈現，避免漏接警特報。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(13.dp))
                var selectedNotificationTone by remember { mutableStateOf(ThinnNotificationChannels.selectedToneId(context)) }
                Text("專屬通知音 · 8 款任選",color=White,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                Text("選擇你喜歡的清脆提示音。天氣警特報、版本更新與測試通知會一起套用，並記住你的選擇。",color=TextSoft,fontSize=14.sp)
                Spacer(Modifier.height(9.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(White.copy(alpha=.075f))
                    .border(1.dp,White.copy(alpha=.13f),RoundedCornerShape(22.dp))
                    .padding(vertical=4.dp)) {
                    ThinnNotificationChannels.tones.forEach { tone ->
                        val selected = selectedNotificationTone == tone.id
                        Row(Modifier.fillMaxWidth()
                            .clickable {
                                hapticEngine.play(HapticEngine.Event.TAP)
                                selectedNotificationTone = tone.id
                                ThinnNotificationChannels.select(context, tone.id)
                            }
                            .padding(start=9.dp,end=9.dp,top=4.dp,bottom=4.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(CircleShape)
                                .border(1.4.dp,if(selected) Color(0xFF8EDFFF) else TextSoft,CircleShape)
                                .background(if(selected) Color(0xFF3486B5) else Color.Transparent),
                                contentAlignment=Alignment.Center) {
                                if(selected) Box(Modifier.size(9.dp).background(White,CircleShape))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(tone.title,color=White,fontSize=16.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
                                Text(tone.description,color=TextSoft,fontSize=14.sp)
                            }
                            Box(Modifier.size(45.dp).clip(RoundedCornerShape(18.dp))
                                .background(White.copy(alpha=.15f))
                                .clickable {
                                    hapticEngine.play(HapticEngine.Event.TAP)
                                    ThinnNotificationChannels.playPreview(context,tone.id)
                                },contentAlignment=Alignment.Center) {
                                Text("▶",color=Color(0xFF8EDFFF),fontSize=18.sp)
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
                TextButton(onClick={uriHandler.openUri(CwaWarningFeed.OFFICIAL_URL)}) { Text("查看中央氣象署官方警報",color=White) }
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
        }
        if (pushSettingsOpen) io.atmos.weather.push.SeismicPushSettingsPage(onClose={pushSettingsOpen=false})
        if (lifestyleSettingsOpen) LifestyleAiSettingsPage(onClose={lifestyleSettingsOpen=false})
        if (showLifestyleDetails && snapshot != null) {
            ModalBottomSheet(onDismissRequest={showLifestyleDetails=false}) {
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
        if (naturalVoiceOpen) io.atmos.weather.voice.NaturalVoiceSettingsPage(onClose={naturalVoiceOpen=false})
        if (diagnosticsOpen) SettingsDiagnosticsPage(onClose={diagnosticsOpen=false})
        if (briefingOpen) WeatherBriefingSettingsPage(onClose={briefingOpen=false})
        if (tasksOpen) io.atmos.weather.task.TaskCenterPage(onClose={tasksOpen=false})
        if (weatherAlarmOpen) io.atmos.weather.alarm.WeatherAlarmPage(onClose={weatherAlarmOpen=false})
        if (typhoonPageOpen) io.atmos.weather.typhoon.TyphoonCenterPage(
            warnings=state.cwaWarnings,onClose={typhoonPageOpen=false},
            initialBackup=gdacsCyclones, primaryUnavailable=typhoonPrimaryUnavailable,
            currentLat=state.city.coordinate.latitude,currentLon=state.city.coordinate.longitude,
            currentName=state.city.name)
        if (updatePageOpen) {
            AppUpdatePage(onClose={updatePageOpen=false})
        }
        if (earthquakePageOpen) {
            EarthquakeCenterPage(onClose={earthquakePageOpen=false})
        }
        AppGlassNavigation(
            selected = when {
                typhoonPageOpen -> 2
                weatherAlarmOpen -> 3
                mainPager.currentPage == 3 -> 4
                mainPager.currentPage == 2 -> 4
                else -> mainPager.currentPage
            },
            onSelect = { tab ->
                settingsOpen=false
                if(tab != 2) typhoonPageOpen=false
                if(tab != 3) weatherAlarmOpen=false
                when(tab) {
                    0 -> showMainTab(0)
                    1 -> showMainTab(1)
                    2 -> typhoonPageOpen=true
                    3 -> weatherAlarmOpen=true
                    4 -> showMainTab(3)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
        details?.let { info -> AlertDialog(onDismissRequest={details=null},
            confirmButton={ TextButton(onClick={details=null}) { Text("了解") } },
            title={Text(info.first)},text={Text(info.second)}) }
        } // 根 Box：底部分頁列為 BoxScope 子元素，Modifier.align 在此合法。
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
    display: String, onChange: (Float)->Unit) {
    Spacer(Modifier.height(10.dp))
    val denom=(range.endInclusive-range.start).coerceAtLeast(.001f)
    GlassTrackingSlider(label,((current-range.start)/denom).coerceIn(0f,1f)) {
        onChange(range.start + it*denom)
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
        colors=SliderDefaults.colors(thumbColor=White,activeTrackColor=Color(0xFF3779FA),
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
    Box(Modifier.fillMaxWidth()
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
        .background(io.atmos.weather.ui.ThinnOpticalMaterial.surface)
        .border(1.dp,Color.White.copy(alpha=.27f),shape)
    ) {
        // Specular sheen follows the direction of a drag; it is not a false claim of live wallpaper blur.
        Canvas(Modifier.matchParentSize()) {
            val x=(horizontalShift.value/52f).coerceIn(-1f,1f)
            val center=Offset(size.width*(.20f+x*.25f),size.height*.10f)
            drawCircle(
                brush=Brush.radialGradient(listOf(White.copy(alpha=appearance.edgeLight*.18f),Color.Transparent),
                    center=center,radius=size.width*.86f),
                radius=size.width*.86f,center=center
            )
        }
        Column(Modifier.fillMaxWidth().padding(19.dp),content=content)
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String,
                       symbol: androidx.compose.ui.graphics.vector.ImageVector,
                       modifier: Modifier=Modifier, onClick:()->Unit) {
    val appearance=LocalAppearance.current
    Column(modifier.clip(RoundedCornerShape(io.atmos.weather.ui.ThinnDesignTokens.radiusCard))
        .background(io.atmos.weather.ui.ThinnOpticalMaterial.surface)
        .border(1.dp,White.copy(alpha=.19f+appearance.edgeLight*.26f),RoundedCornerShape(io.atmos.weather.ui.ThinnDesignTokens.radiusCard))
        .clickable(onClick=onClick).padding(17.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(symbol,contentDescription=null,tint=TextSoft,modifier=Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(title,color=TextSoft,fontSize=14.sp)
        }
        Spacer(Modifier.height(17.dp))
        Text(value,color=White,fontSize=28.sp,fontWeight=FontWeight.Light,style=androidx.compose.ui.text.TextStyle(fontFeatureSettings="tnum"))
        Text(subtitle,color=TextSoft,fontSize=14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSheet(selected: WeatherCity, saved: List<WeatherCity>, results: List<WeatherCity>,
    searching: Boolean, searchExecuted:Boolean,searchError:String?, locationFeedback: LocationFeedback,
    onLocate: ()->Unit, onQuery:(String)->Unit,onSelect:(WeatherCity)->Unit,
    onRemove:(String)->Unit,onClose:()->Unit) {
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        val cleaned=query.trim()
        if(cleaned.length>=2) {
            kotlinx.coroutines.delay(380L)
            submittedQuery=cleaned
            onQuery(cleaned)
        }
    }
    ModalBottomSheet(onDismissRequest=onClose,containerColor=Color(0xFF102A44),contentColor=White) {
        Column(Modifier.fillMaxWidth().padding(horizontal=22.dp).padding(bottom=28.dp)) {
            Text("地點與城市",fontSize=23.sp,fontWeight=FontWeight.Bold,color=White)
            Spacer(Modifier.height(5.dp))
            Text("首頁優先顯示 GPS 位置，可保存其他地點供切換查看",fontSize=14.sp,color=TextSoft)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=query,onValueChange={query=it;submittedQuery=null},singleLine=true,
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={submittedQuery=query.trim();onQuery(query.trim())}),
                    leadingIcon={Icon(Icons.Rounded.Search,null)},
                    label={Text("搜尋鄉鎮、城市或國外地點")},modifier=Modifier.weight(1f),
                    colors=OutlinedTextFieldDefaults.colors(focusedTextColor=White,unfocusedTextColor=White,
                        focusedBorderColor=White.copy(alpha=.65f),unfocusedBorderColor=White.copy(alpha=.22f),
                        focusedLabelColor=White,unfocusedLabelColor=TextSoft))
                Button(onClick={submittedQuery=query.trim();onQuery(query.trim())},
                    enabled=!searching,modifier=Modifier.height(54.dp),contentPadding=PaddingValues(horizontal=15.dp)) {
                    Text("搜尋")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(White.copy(alpha=.14f))
                .clickable(enabled=!locationFeedback.inProgress,onClick=onLocate).padding(15.dp),
                verticalAlignment=Alignment.CenterVertically) {
                if(locationFeedback.inProgress) CircularProgressIndicator(
                    Modifier.size(22.dp),color=Color(0xFF9CE4FF),strokeWidth=2.dp)
                else Icon(Icons.Rounded.MyLocation,null,tint=White)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if(locationFeedback.inProgress) "正在取得目前位置…" else "回到目前 GPS 位置",
                        color=White,fontWeight=FontWeight.Medium)
                    Text(if(locationFeedback.inProgress) "請稍候，定位完成後會自動更新天氣"
                        else "自動辨識台灣 · 縣市 · 鄉鎮市區",color=TextSoft,fontSize=14.sp)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = locationFeedback.phase != LocationPhase.IDLE,
                enter = androidx.compose.animation.fadeIn(tween(180)) +
                    androidx.compose.animation.expandVertically(tween(240)),
                exit = androidx.compose.animation.fadeOut(tween(180))
            ) {
                LocationFeedbackBanner(locationFeedback, onRetry=onLocate)
            }
            if(searching) { Spacer(Modifier.height(12.dp));LinearProgressIndicator(Modifier.fillMaxWidth(),color=White) }
            if(submittedQuery != null && (searchExecuted || searchError != null)) {
                Spacer(Modifier.height(14.dp))
                Text("搜尋結果",color=TextSoft,fontSize=14.sp)
                if(searchError!=null) Text(searchError,color=Color(0xFFFFD094),fontSize=14.sp)
                if(!searching && searchError==null && results.isEmpty())
                    Text("沒有找到相符地點，可試試地區中文、英文或附近城市",color=TextSoft,fontSize=14.sp)
                LazyColumn(modifier=Modifier.heightIn(max=220.dp)) {
                    items(results,key={"search-${it.id}"}) { city ->
                        Row(Modifier.fillMaxWidth().clickable{onSelect(city)}.padding(vertical=10.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Place,null,tint=TextSoft,modifier=Modifier.size(18.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(city.name,color=White,modifier=Modifier.weight(1f),fontSize=15.sp,maxLines=2)
                            Icon(Icons.Rounded.Add,null,tint=Color(0xFF8ED8FF))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("已儲存地點（最多 30 個）",color=TextSoft,fontSize=14.sp)
            Spacer(Modifier.height(6.dp))
            val cities=if(saved.isEmpty()) SampleCities.all.take(4) else saved
            LazyColumn(modifier=Modifier.heightIn(max=250.dp)) {
                items(cities,key={"saved-${it.id}"}) { city ->
                    Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable{onSelect(city)}.padding(vertical=5.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Place,null,tint=TextSoft,modifier=Modifier.size(17.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(city.name,color=White,modifier=Modifier.weight(1f),fontSize=16.sp,maxLines=2)
                            if(city.id==selected.id) Icon(Icons.Rounded.Check,null,tint=Color(0xFF83E3E5),modifier=Modifier.size(18.dp))
                        }
                        if(saved.any{it.id==city.id}) IconButton(onClick={onRemove(city.id)}) {
                            Icon(Icons.Rounded.Close,"移除儲存地點",tint=TextSoft,modifier=Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color=White.copy(alpha=.10f))
                }
            }
        }
    }
}

/** Visible progress while GPS resolves; success and error are never silently swallowed. */
@Composable
private fun LocationFeedbackBanner(status: LocationFeedback, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val busy = status.inProgress
    Row(
        Modifier.fillMaxWidth().padding(top=10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (status.phase == LocationPhase.ERROR || status.phase == LocationPhase.DENIED ||
                status.phase == LocationPhase.DISABLED) Color(0xFF82432E).copy(alpha=.80f)
                else White.copy(alpha=.16f))
            .padding(horizontal=12.dp, vertical=13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(modifier=Modifier.size(20.dp),color=Color(0xFF9CE4FF),strokeWidth=2.dp)
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
    WeatherSurfaceOverlay(weatherSurface, windKmh.toFloat(), Modifier.fillMaxSize())
    // NEXT secondary layer. Existing sky, brand, forecast layout and pager remain unchanged.
    NextWeatherBackdrop(WeatherScenes.classify(condition,weatherCode,visibilityM),phase,night)
    if (condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL, WeatherCondition.FREEZING_RAIN)) {
        ContinuousRain(heavyRain || condition == WeatherCondition.THUNDERSTORM, Modifier.fillMaxSize())
    } else if (condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET)) {
        ContinuousSnow(Modifier.fillMaxSize())
    }
    if (condition == WeatherCondition.THUNDERSTORM && lightningAlpha > 0f) {
        Canvas(Modifier.fillMaxSize()) {
            val w=size.width; val h=size.height
            val x=w*(.22f + (boltSeed % 5)*.13f)
            drawRect(Color(0xFFDCE8FF).copy(alpha=lightningAlpha))
            val p=Path().apply {
                moveTo(x,h*.045f)
                lineTo(x+w*.035f,h*.18f)
                lineTo(x-w*.021f,h*.32f)
                lineTo(x+w*.042f,h*.37f)
                lineTo(x-w*.031f,h*.56f)
            }
            drawPath(p,Color(0xFFE8F4FF).copy(alpha=(lightningAlpha*2.1f).coerceAtMost(.83f)),
                style=Stroke(width=2.8.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}

@Composable
private fun AtmosphereCanvas(condition:WeatherCondition, isNight:Boolean, phase:Float, modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        if (!isNight && condition != WeatherCondition.THUNDERSTORM) {
            val sun=Offset(w*.80f + sin(phase*6.283f)*w*.025f,h*.12f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4B5).copy(alpha=.38f),Color.Transparent),center=sun,radius=w*.57f),radius=w*.57f,center=sun)
            drawCircle(Color(0xFFFFF1BE).copy(alpha=if(condition==WeatherCondition.CLEAR) .76f else .28f),radius=w*.07f,center=sun)
        }
        if (isNight) {
            val moon = Offset(w*.77f,h*.13f)
            drawCircle(Color(0xFFEDF4FB).copy(alpha=.72f),radius=w*.043f,center=moon)
            drawCircle(Color(0xFF143259),radius=w*.037f,center=Offset(moon.x+w*.017f,moon.y-w*.011f))
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
    Canvas(modifier) {
        val t = RainParticles.secondsAt(frameNanos.longValue)
        val width=size.width
        val height=size.height
        val density=size.minDimension.coerceAtLeast(1f)
        val count=if (heavy) 178 else 90
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
            drawLine(
                color=Color(0xFFD8ECFF).copy(alpha=alpha),
                start=Offset(x,y),end=Offset(x-dx,y+len),
                strokeWidth=(d.thickness * density / 400f).coerceIn(.55f,2.2f),
                cap=StrokeCap.Round
            )
            // Occasional physically-inspired contacts: each selected drop hits a
            // different glass/card plane and releases a tiny rebound plus ripple.
            // Sampling, not every drop, avoids expensive full-screen particles.
            if (d.depth > .83f && i % 11 == 0) {
                val plane = when (i % 4) {0 -> .43f;1 -> .67f;2 -> .84f;else -> .965f}
                if (yn in plane..(plane+.085f)) {
                    val progress = ((yn-plane)/.085f).coerceIn(0f,1f)
                    val yImpact = plane*height
                    val opacity=(1f-progress)*.23f
                    drawCircle(Color(0xFFE6F7FF).copy(alpha=opacity),
                        radius=(1.5f+progress*9f)*density/400f,
                        center=Offset(x-dx*.66f,yImpact),
                        style=Stroke(width=(.7f*density/400f).coerceAtLeast(.5f)))
                    val bounce = kotlin.math.sin(progress*kotlin.math.PI.toFloat()) * (5f + d.depth*8f)
                    drawCircle(Color(0xFFE6F7FF).copy(alpha=opacity*.8f),
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
    Canvas(modifier) {
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
        drawRect(Color(0xFFF5FAFF).copy(alpha=.18f+.36f*accumulated),
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
private fun DailyGuidanceCard(weather:WeatherSnapshot,stale:Boolean,onOpen:()->Unit) {
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
    val preferences=remember(ctx){LifestylePreferenceStore.load(ctx)}
    val mode=remember(ctx){OutingMode.entries.firstOrNull {
        it.name==ctx.getSharedPreferences("thinn_lifestyle_ai",0).getString("mode","DAILY")
    } ?: OutingMode.DAILY}
    val plan=remember(weather,stale,mode,preferences){
        LifestyleAdvisor.recommend(weather,mode,stale,preferences=preferences)
    }
    val critical=remember(weather){
        io.atmos.weather.domain.model.WeatherAdviceEngine.suggest(weather).firstOrNull{it.priority>=8}
    }
    io.atmos.weather.ui.ThinnAtmospherePanel(accent=Color(0xFFFFD69B)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lightbulb,contentDescription=null,tint=Color(0xFFFFD690),modifier=Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Text("今天出門",color=White,fontSize=19.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        }
        Spacer(Modifier.height(17.dp))
        Text(when {
            landHere -> "${localWarning?.name ?: "颱風"} · 所在地已列入陸上颱風警報"
            localWarning?.level==io.atmos.weather.typhoon.TyphoonWarningLevel.LAND -> "中央氣象署已發布陸上颱風警報"
            localWarning!=null -> "中央氣象署已發布海上颱風警報"
            stale -> "天氣資料待更新"
            else -> critical?.heading ?: plan.headline
        },color=White,fontSize=23.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold,maxLines=4)
        Spacer(Modifier.height(5.dp))
        Text(when {
            landHere -> "今日受颱風影響，${ownCounty}已列入陸上警戒。請盡量避免非必要外出，關好門窗、固定陽台物品並做好防颱準備；如須出門，請留意強風、豪雨與道路安全。"
            localWarning?.level==io.atmos.weather.typhoon.TyphoonWarningLevel.LAND ->
                "部分縣市已列入陸上警戒，但尚無足夠官方區域資料可確認您的所在地是否列入。出門前請查閱中央氣象署及地方政府最新公告。"
            localWarning!=null -> "已發布海上颱風警報。避免海邊、港口與海上活動，持續關注氣象署是否升級陸警。"
            stale -> "目前顯示舊資料，請重新整理後再確認外出安排。"
            else -> critical?.detail ?: plan.reminder
        },color=Color(0xFFF0F6FB),fontSize=16.sp,lineHeight=24.sp,maxLines=7)
        if(localWarning!=null) {
            Spacer(Modifier.height(6.dp))
            Text("依中央氣象署已核對公告產生的規則式安全提醒；非 AI 推測警戒範圍。",
                color=Color(0xFFFFDECE),fontSize=14.sp)
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha=.14f)))
        Spacer(Modifier.height(17.dp))
        // These are not optional fine print: clothing and rain decisions are always visible.
        HomeAdviceRow("穿著建議",if(stale) "待更新" else plan.top)
        Spacer(Modifier.height(9.dp))
        HomeAdviceRow("雨具建議",if(stale) "待更新" else plan.umbrella)
        if (!stale && plan.layer.isNotBlank() && plan.layer != "按體感帶薄外套") {
            Spacer(Modifier.height(9.dp))
            HomeAdviceRow("外套／防曬",plan.layer)
        }
        Spacer(Modifier.height(9.dp))
        TextButton(onClick=onOpen, modifier=Modifier.align(Alignment.End)) {
            Text("調整個人偏好與計算依據",color=Color(0xFFC9EBFF),fontSize=15.sp,fontWeight=FontWeight.Medium)
        }
        }
    }
}

@Composable
private fun HomeAdviceRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.Top) {
        Text(label,color=Color(0xFFBBDDF1),fontSize=16.sp,fontWeight=FontWeight.SemiBold,
            modifier=Modifier.width(84.dp))
        Text(value,color=White,fontSize=16.sp,lineHeight=24.sp,modifier=Modifier.weight(1f))
    }
}

@Composable
private fun HomeKeyWeatherCard(weather: WeatherSnapshot, stale: Boolean) {
    // The four actionable facts are ALWAYS on the homepage: no collapsed detail sheet.
    val highlights = remember(weather) { HomeWeatherBriefing.build(weather) }
    io.atmos.weather.ui.ThinnAtmospherePanel(accent=Color(0xFF9ADAFE)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("現在最重要的天氣", color = White, fontSize = 21.sp,
                lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
            Text("未來 6 小時", color = Color(0xFFC4E8FC), fontSize = 14.sp)
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
                        Text(fact.label, color = Color(0xFFD6EAF7), fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, lineHeight = 22.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(fact.value, color = White, fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold, lineHeight = 35.sp,
                            maxLines = 2)
                        Spacer(Modifier.height(3.dp))
                        Text(fact.note, color = Color(0xFFBED6E5), fontSize = 14.sp,
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


private data class StatusBadge(val valueText:String, val label:String, val advice:String, val accent:Color)

@Composable
private fun AirQualityInsightCard(snapshot: WeatherSnapshot) {
    val status = remember(snapshot.usAqi, snapshot.pm25) { airQualityBadge(snapshot.usAqi, snapshot.pm25) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = .10f))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("空氣品質", color = White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(snapshot.city.name.substringAfterLast("·", snapshot.city.name).trim().ifBlank { snapshot.city.name }, color = TextSoft, fontSize=14.sp)
            }
            Text("Open-Meteo", color = TextSoft, fontSize=14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(status.valueText, color = status.accent, fontSize = 58.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp)
            Spacer(Modifier.width(8.dp))
            Text(status.label, color = status.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(status.advice, color = TextSoft, fontSize=15.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(14.dp))
        PollutantRow(
            listOf(
                "PM2.5" to pollutantText(snapshot.pm25),
                "PM10" to pollutantText(snapshot.pm10),
                "SO₂" to pollutantText(snapshot.sulfurDioxide),
                "NO₂" to pollutantText(snapshot.nitrogenDioxide),
                "O₃" to pollutantText(snapshot.ozone),
                "CO" to pollutantText(snapshot.carbonMonoxide)
            )
        )
    }
}

@Composable
private fun VisibilityInsightCard(snapshot: WeatherSnapshot, hazy: Boolean) {
    val status = remember(snapshot.visibilityM, snapshot.condition, hazy) { visibilityBadge(snapshot.visibilityM, snapshot.condition, hazy) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = .10f))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("能見度建議", color = White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("即時路況請以實際目視與官方資訊為準", color = TextSoft, fontSize=14.sp)
            }
            Icon(Icons.Rounded.Visibility, null, tint = status.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(status.valueText, color = status.accent, fontSize = 54.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
            Spacer(Modifier.width(8.dp))
            Text(status.label, color = status.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(status.advice, color = TextSoft, fontSize=15.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(14.dp))
        PollutantRow(
            listOf(
                "濕度" to (snapshot.humidityPct?.let { "$it%" } ?: "—"),
                "風速" to (snapshot.windKmh?.let { "${it.roundToInt()} km/h" } ?: "—"),
                "UV" to (snapshot.currentUv?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—"),
                "氣壓" to (snapshot.pressureHpa?.let { "${it.roundToInt()}" } ?: "—"),
                "體感" to (snapshot.feelsLikeC?.let { "${it.roundToInt()}°" } ?: "—"),
                "天氣" to weatherShortLabel(snapshot.condition)
            )
        )
    }
}

@Composable
private fun PollutantRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(value, color = White.copy(alpha = .92f), fontSize=14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
                Text(label, color = TextSoft, fontSize=14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun pollutantText(value: Double?): String = value?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—"

private fun airQualityBadge(usAqi: Int?, pm25: Double?): StatusBadge {
    val score = usAqi ?: pm25?.roundToInt()
    val display = score?.toString() ?: "--"
    return when {
        score == null -> StatusBadge(display, "待更新", "目前尚未取得空氣品質資料，稍後將自動再同步。", Color(0xFFB8DFFF))
        score <= 50 -> StatusBadge(display, "良", "目前空氣品質大致良好，適合一般戶外活動。", Color(0xFF6FE3A7))
        score <= 100 -> StatusBadge(display, "普通", "空氣品質尚可，敏感族群外出可留意身體反應。", Color(0xFFF7C85A))
        score <= 150 -> StatusBadge(display, "不佳", "敏感族群建議減少長時間戶外活動，外出可佩戴口罩。", Color(0xFFFF9E57))
        score <= 200 -> StatusBadge(display, "差", "空氣品質偏差，建議減少戶外停留時間並加強防護。", Color(0xFFFF7B7B))
        else -> StatusBadge(display, "很差", "空氣品質明顯不佳，建議儘量留在室內並減少劇烈活動。", Color(0xFFC78BFF))
    }
}

private fun visibilityBadge(visibilityM: Double?, condition: WeatherCondition, hazy: Boolean): StatusBadge {
    val km = visibilityM?.div(1000.0)
    val display = km?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "--"
    return when {
        km == null -> StatusBadge(display, "待更新", "目前尚未取得能見度資料，請稍後再查看。", Color(0xFFB8DFFF))
        condition == WeatherCondition.FOG || km < 1.0 -> StatusBadge(display, "低", "目前可能有霧或低雲，行車請減速慢行並保持安全距離。", Color(0xFFFF9E57))
        km < 3.0 -> StatusBadge(display, "偏低", "能見度偏低，外出與行車請留意路況，必要時開啟車燈。", Color(0xFFF7C85A))
        hazy -> StatusBadge(display, "普通", "視線大致可接受，但空氣中懸浮微粒較多，遠景可能略顯灰霧。", Color(0xFFF7C85A))
        km < 8.0 -> StatusBadge(display, "尚可", "能見度普通，戶外活動基本不受影響，請仍留意現場狀況。", Color(0xFF7FD7F7))
        else -> StatusBadge(display, "良好", "目前能見度良好，戶外活動與行車視線大致清楚。", Color(0xFF6FE3A7))
    }
}

private fun weatherShortLabel(condition: WeatherCondition): String = when (condition) {
    WeatherCondition.CLEAR -> "晴"
    WeatherCondition.PARTLY_CLOUDY -> "多雲"
    WeatherCondition.CLOUDY -> "陰"
    WeatherCondition.FOG -> "霧"
    WeatherCondition.HAZE -> "霾"
    WeatherCondition.DUST -> "揚塵"
    WeatherCondition.DUST_STORM -> "強烈揚塵"
    WeatherCondition.SLEET -> "雨夾雪"
    WeatherCondition.FREEZING_RAIN -> "凍雨"
    WeatherCondition.HAIL -> "冰雹"
    WeatherCondition.RAIN -> "雨"
    WeatherCondition.SNOW -> "雪"
    WeatherCondition.THUNDERSTORM -> "雷雨"
    else -> "未知"
}
