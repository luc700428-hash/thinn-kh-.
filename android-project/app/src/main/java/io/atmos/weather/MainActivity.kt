package io.atmos.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import io.atmos.weather.location.LocationLabels
import android.os.Bundle
import io.atmos.weather.domain.visual.WeatherScene
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.presentation.home.*
import io.atmos.weather.widgets.WidgetSync
import io.atmos.weather.location.ReverseGeocoder
import io.atmos.weather.alerts.CwaAlertWorker
import io.atmos.weather.briefing.WeatherBriefingScheduler
import io.atmos.weather.update.UpdateCheckWorker
import io.atmos.weather.earthquake.EarthquakeReportWorker
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.permission.PermissionGuide
import io.atmos.weather.permission.FirstRunGuide
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var locationJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashDiagnostics.install(this)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            CrashDiagnostics.collectPreviousExit(applicationContext)
            io.atmos.weather.voice.NaturalVoiceHub.retire(this@MainActivity)
        }
        // Lock-screen activation is governed by OS notification policy. Do not
        // enable wake from an exported Activity intent extra that another app can forge.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        io.atmos.weather.ui.HighRefreshWindow.apply(window,window.decorView)
        ThinnNotificationChannels.ensure(this)
        // Start a foreground seismic stream only after the user has explicitly opted in.
        // Android may still deny or terminate the foreground service; never promise delivery.
        if (getSharedPreferences("thinn_seismic_v15",MODE_PRIVATE)
                .getBoolean("auto_start_realtime_on_app_open",true) &&
            PermissionGuide.canNotify(this)) {
            runCatching { io.atmos.weather.earthquake.ExpTechSeismicService.start(this) }
        }
        WidgetSync.schedule(this)
        CwaAlertWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
        EarthquakeReportWorker.schedule(this)
        WeatherBriefingScheduler.reschedule(this)
        io.atmos.weather.alarm.WeatherAlarmScheduler.reconcile(this)
        // Recalculate future personal reminders after app restart / time-zone changes.
        runCatching { io.atmos.weather.task.TaskReminderScheduler.sync(
            this,io.atmos.weather.task.TaskStore.load(this)) }
        CwaAlertWorker.checkNow(this)
        setContent {
            val vm: WeatherViewModel = viewModel(factory=WeatherViewModel.Factory(AppContainer.repository(applicationContext), applicationContext))
            // Queue runtime requests sequentially. Two simultaneous launchers may cause
            // the second system dialog to be dropped on some OEM Android variants.
            val consent = remember { getSharedPreferences("thinn_settings", MODE_PRIVATE) }
            var introComplete by remember { mutableStateOf(consent.getBoolean("v8_intro_completed",false)) }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    consent.edit().putBoolean("notification_ever_granted",true)
                        .putBoolean("notification_revoke_notice_shown",false).apply()
                    CwaAlertWorker.checkNow(this@MainActivity)
                }
            }
            val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                if (results.values.any { it }) currentLocation(vm)
                else vm.setLocationFeedback(LocationPhase.DENIED,
                    "未取得定位權限；請在設定開啟權限，或改用城市搜尋。")
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    !io.atmos.weather.permission.PermissionGuide.hasNotifications(this@MainActivity) &&
                    !consent.getBoolean("alert_permission_asked",false)) {
                    consent.edit().putBoolean("alert_permission_asked",true).apply()
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LaunchedEffect(introComplete) {
                if (!introComplete) return@LaunchedEffect
                val hasLocation=io.atmos.weather.permission.PermissionGuide.hasLocation(this@MainActivity)
                val lastId=getSharedPreferences("atmos_settings", MODE_PRIVATE).getString("city_id",null)
                // GPS is always the homepage source on launch when permission is granted.
                if (hasLocation) currentLocation(vm)
                if (!hasLocation && !consent.getBoolean("location_permission_asked",false)) {
                    consent.edit().putBoolean("location_permission_asked",true).apply()
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    !io.atmos.weather.permission.PermissionGuide.hasNotifications(this@MainActivity) &&
                    !consent.getBoolean("alert_permission_asked",false)) {
                    consent.edit().putBoolean("alert_permission_asked",true).apply()
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // Presentation tree only. Does not change WeatherViewModel or app state.
            io.atmos.weather.ui.ThinnAppTheme {
            if (!introComplete) FirstRunGuide(onContinue={
                consent.edit().putBoolean("v8_intro_completed",true).apply()
                introComplete = true
            }) else WeatherHomeScreen(viewModel=vm, onLocate={
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) currentLocation(vm)
                else {
                    vm.setLocationFeedback(LocationPhase.PERMISSION,"正在等待定位權限…")
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                }
            })
            io.atmos.weather.earthquake.SeismicForegroundAlertHost()
            } // ThinnAppTheme
        }
    }
    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("thinn_settings", MODE_PRIVATE)
        val nowAllowed = io.atmos.weather.permission.PermissionGuide.canNotify(this)
        val locationAllowed = io.atmos.weather.permission.PermissionGuide.hasLocation(this)
        if (locationAllowed && !prefs.getBoolean("location_ever_granted",false)) {
            prefs.edit().putBoolean("location_ever_granted",true).apply()
        }
        if (locationAllowed) prefs.edit().putBoolean("location_revoke_notice_shown",false).apply()
        if (!locationAllowed && prefs.getBoolean("location_ever_granted",false) &&
            !prefs.getBoolean("location_revoke_notice_shown",false)) {
            android.widget.Toast.makeText(this,
                "thinn-khì 定位權限已關閉；可從設定重新開啟定位天氣。",
                android.widget.Toast.LENGTH_LONG).show()
            prefs.edit().putBoolean("location_revoke_notice_shown",true).apply()
        }
        if (nowAllowed && !prefs.getBoolean("notification_ever_granted", false)) {
            prefs.edit().putBoolean("notification_ever_granted", true).apply()
        }
        if (nowAllowed) prefs.edit().putBoolean("notification_revoke_notice_shown",false).apply()
        if (!nowAllowed && prefs.getBoolean("notification_ever_granted", false) &&
            !prefs.getBoolean("notification_revoke_notice_shown", false)) {
            android.widget.Toast.makeText(this,
                "thinn-khì 通知權限已關閉，天氣特報提醒可能無法送達；可到設定重新開啟。",
                android.widget.Toast.LENGTH_LONG).show()
            prefs.edit().putBoolean("notification_revoke_notice_shown",true).apply()
        }
    }

    /** Android 12+ location lookup with cancellation, a timeout, and visible error states. */
    @SuppressLint("MissingPermission")
    private fun currentLocation(vm: WeatherViewModel) {
        locationJob?.cancel()
        if (!PermissionGuide.hasLocation(this)) {
            vm.setLocationFeedback(LocationPhase.DENIED,"缺少定位權限，請允許位置存取後再試。")
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val available = manager.getProviders(true)
        val preferred = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        ).filter { it in available }
        if (preferred.isEmpty()) {
            vm.setLocationFeedback(LocationPhase.DISABLED,"手機定位服務已關閉，請先開啟定位功能。")
            return
        }
        vm.setLocationFeedback(LocationPhase.LOCATING,"正在取得 GPS 位置…")
        locationJob = lifecycleScope.launch {
            try {
                // Every provider request has a deadline; slow GPS must not leave an infinite spinner.
                var fix: Location? = null
                for ((index, provider) in preferred.withIndex()) {
                    vm.setLocationFeedback(LocationPhase.LOCATING,
                        if (index == 0) "正在取得 GPS 位置…" else "正在使用其他定位方式嘗試…")
                    fix = withTimeoutOrNull(if (index==0) 12_000L else 5_000L) {
                        readOneLocation(manager, provider)
                    }
                    if (fix != null) break
                }
                var recent = false
                if (fix == null) {
                    fix = available.mapNotNull { provider ->
                        try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
                    }.filter {
                        val age = System.currentTimeMillis() - it.time
                        age in 0L..600_000L
                    }.maxByOrNull { it.time }
                    recent = fix != null
                }
                val coordinate = fix ?: run {
                    vm.setLocationFeedback(LocationPhase.ERROR,
                        "無法取得目前位置。請到空曠處、檢查 GPS 與網路後重試。")
                    return@launch
                }
                vm.setLocationFeedback(LocationPhase.GEOCODING,"定位完成，正在辨識縣市與鄉鎮地名…")
                val point = GeoPoint(coordinate.latitude, coordinate.longitude)
                val label = withTimeoutOrNull(8_000L) {
                    ReverseGeocoder.resolve(applicationContext, point)
                } ?: LocationLabels.fallback(point)
                vm.useGpsCity(WeatherCity("gps", label, point), recent)
            } catch (_: SecurityException) {
                vm.setLocationFeedback(LocationPhase.DENIED,"定位權限已被關閉，請至系統設定重新授權。")
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                vm.setLocationFeedback(LocationPhase.ERROR,"定位發生錯誤，請確認網路與手機定位服務後重試。")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readOneLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                manager.getCurrentLocation(provider, cancellation, mainExecutor) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    override fun onDestroy() {
        locationJob?.cancel()
        super.onDestroy()
    }
}
