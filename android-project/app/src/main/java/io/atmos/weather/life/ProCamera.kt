@file:androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package io.atmos.weather.life

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Range
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.effects.OverlayEffect
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.GlassButton
import io.atmos.weather.ui.GlassOutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/** The camera's modes, in the order of the ribbon above the shutter. */
internal enum class CamMode(val label: String) { NIGHT("超級夜景"), PHOTO("拍照"), VIDEO("錄影"), PRO("專業"), AUDIO("錄音") }

/** 拍照 looks served by the phone maker's own processing (CameraX extensions), when the phone has them. */
internal enum class PhotoLook(val label: String, val ext: Int?) {
    STANDARD("標準", null), AUTO("智慧優化", ExtensionMode.AUTO), HDR("HDR", ExtensionMode.HDR), PORTRAIT("人像", ExtensionMode.BOKEH), BEAUTY("美顏", ExtensionMode.FACE_RETOUCH)
}

internal enum class WhiteBalance(val label: String, val mode: Int) {
    AUTO("自動", CaptureRequest.CONTROL_AWB_MODE_AUTO), DAYLIGHT("日光", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("陰天", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT), SHADE("陰影", CaptureRequest.CONTROL_AWB_MODE_SHADE),
    TUNGSTEN("鎢絲燈", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT), FLUORESCENT("日光燈", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
}

private enum class Flash(val mode: Int) { OFF(ImageCapture.FLASH_MODE_OFF), AUTO(ImageCapture.FLASH_MODE_AUTO), ON(ImageCapture.FLASH_MODE_ON) }
private enum class ProDial(val label: String) { EV("曝光"), ISO("ISO"), SHUTTER("快門"), WB("白平衡"), FOCUS("對焦") }

/** Manual exposure, white balance and focus for 專業. Null means automatic. */
private data class ProSettings(val ev: Int = 0, val iso: Int? = null, val shutterNs: Long? = null, val wb: WhiteBalance = WhiteBalance.AUTO, val focus: Float? = null)

/** What the bound camera can do, read once it is open. */
private class CameraAbility(val evRange: Range<Int>, val evStep: Float, val isoRange: Range<Int>?, val shutterRange: Range<Long>?, val minFocus: Float,
    val manualSensor: Boolean, val lowFps: Range<Int>?)

private val SHUTTERS = listOf(1_000_000_000L / 8000, 1_000_000_000L / 4000, 1_000_000_000L / 2000, 1_000_000_000L / 1000, 1_000_000_000L / 500,
    1_000_000_000L / 250, 1_000_000_000L / 125, 1_000_000_000L / 60, 1_000_000_000L / 30, 1_000_000_000L / 15, 1_000_000_000L / 8,
    1_000_000_000L / 4, 1_000_000_000L / 2, 1_000_000_000L)
private val ISOS = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
private fun shutterLabel(ns: Long) = if (ns >= 1_000_000_000L) "${ns / 1_000_000_000L}\"" else "1/${(1_000_000_000L / ns)}"

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.awaitMain(ctx: Context): T =
    suspendCancellableCoroutine { c -> addListener({ runCatching { get() }.onSuccess { c.resume(it) }.onFailure { c.cancel(it) } }, ContextCompat.getMainExecutor(ctx)) }

private suspend fun ImageCapture.shot(ctx: Context): ImageProxy? = suspendCancellableCoroutine { c ->
    takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) { if (c.isActive) c.resume(image) else image.close() }
        override fun onError(e: ImageCaptureException) { if (c.isActive) c.resume(null) }
    })
}

/** A captured frame as an upright bitmap no larger than [maxSide]. */
private fun ImageProxy.upright(maxSide: Int, mirror: Boolean): Bitmap? = runCatching {
    val raw = toBitmap()
    val s = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
    val scaled = if (s < 1f) Bitmap.createScaledBitmap(raw, (raw.width * s).roundToInt(), (raw.height * s).roundToInt(), true).also { if (it !== raw) raw.recycle() } else raw
    SnapPixels.orient(scaled, imageInfo.rotationDegrees, mirror).also { if (it !== scaled) scaled.recycle() }
}.getOrNull()

@Composable
private fun RoundTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(if (active) Color(0xFFFFD60A) else Color.Black.copy(alpha = .38f))
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(24.dp), tint = if (active) Color.Black else Color.White)
    }
}

@Composable
private fun Pill(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label, color = when { !enabled -> Color.White.copy(alpha = .35f); on -> Color.Black; else -> Color.White }, fontSize = 15.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Color(0xFFFFD60A) else Color.Black.copy(alpha = .45f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp))
}

/**
 * 天空浮水印相機 — a full camera: 超級夜景 (the phone maker's night mode when there is one, otherwise
 * the app's own multi-frame stacking), 拍照 with HDR / 人像 / 美顏 / 智慧優化 where offered, 錄影 up to
 * 4K 60 fps with the weather watermark burned into the video (and 8K on phones that record it),
 * 專業 with manual exposure, ISO, shutter, white balance and focus plus a histogram, and 錄音.
 */
@Composable
internal fun CameraScreen(facts: LifeCards.SnapFacts, startTemplate: SnapRender.Template, startMode: CamMode = CamMode.PHOTO, onClose: () -> Unit, onPhoto: (Bitmap, SnapRender.Edit) -> Unit) {
    val ctx = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    LaunchedEffect(Unit) { if (!granted && startMode != CamMode.AUDIO) ask.launch(Manifest.permission.CAMERA) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { withContext(Dispatchers.IO) { SnapRender.decode(ctx, it) }?.let { b -> onPhoto(b, SnapRender.Edit(template = startTemplate)) } } }
    }
    var prefs by remember { mutableStateOf(CameraPrefs.load(ctx)) }
    fun update(p: CameraPrefs) { prefs = p; p.save(ctx) }
    var mode by rememberSaveable { mutableStateOf(startMode) }
    var look by remember { mutableStateOf(PhotoLook.STANDARD) }
    var flash by remember { mutableStateOf(Flash.OFF) }
    var timer by remember { mutableIntStateOf(0) }
    var aspect by remember { mutableStateOf(SnapRender.Aspect.CLASSIC) }
    var template by remember { mutableStateOf(startTemplate) }
    var front by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var trayOpen by remember { mutableStateOf(false) }
    var pro by remember { mutableStateOf(ProSettings()) }
    var dial by remember { mutableStateOf(ProDial.EV) }
    var ability by remember { mutableStateOf<CameraAbility?>(null) }
    var looks by remember { mutableStateOf(setOf(PhotoLook.STANDARD)) }
    var vendorNight by remember { mutableStateOf(false) }
    var videoQualities by remember { mutableStateOf<List<String>>(emptyList()) }
    var fps60Ok by remember { mutableStateOf(false) }
    val eightK = remember { HighResVideo.find(ctx) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recSeconds by remember { mutableLongStateOf(0L) }
    var recPaused by remember { mutableStateOf(false) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var histogram by remember { mutableStateOf<FloatArray?>(null) }
    var roll by remember { mutableFloatStateOf(0f) }
    val blink = remember { Animatable(0f) }
    LaunchedEffect(message) { if (message.isNotEmpty()) { delay(3500); message = "" } }

    val previewView = remember { PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var extensions by remember { mutableStateOf<ExtensionsManager?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // Video watermark frames are drawn off the main thread, so the controls stay smooth while recording.
    val markThread = remember { android.os.HandlerThread("thinn-mark").apply { start() } }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown(); markThread.quitSafely() } }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val p = runCatching { ProcessCameraProvider.getInstance(ctx).awaitMain(ctx) }.getOrNull() ?: run { message = "相機無法啟動"; return@LaunchedEffect }
        extensions = runCatching { ExtensionsManager.getInstanceAsync(ctx, p).awaitMain(ctx) }.getOrNull()
        provider = p
    }
    // The watermark burned into video frames, refreshed every minute (the clock on it moves on).
    val markRef = remember { AtomicReference<Bitmap?>(null) }
    val markAspect = remember { AtomicReference(9f / 16f) }
    val eightKActive = mode == CamMode.VIDEO && prefs.video == "8K" && eightK != null && !front
    val burn = mode == CamMode.VIDEO && prefs.burnMark && template != SnapRender.Template.NONE && !eightKActive
    LaunchedEffect(burn, template, facts) {
        if (!burn) { markRef.set(null); return@LaunchedEffect }
        var lastAspect = 0f; var lastMinute = -1L
        while (isActive) {
            val minute = System.currentTimeMillis() / 60_000; val a = markAspect.get()
            if (minute != lastMinute || abs(a - lastAspect) > .01f) {
                lastMinute = minute; lastAspect = a
                val w = 1080; val h = (1080 / a).roundToInt()
                markRef.set(withContext(Dispatchers.Default) { SnapRender.overlay(ctx, w, h, SnapRender.Edit(template = template, aspect = SnapRender.Aspect.ORIGINAL), facts.copy(time = Instant.now())) })
            }
            delay(1000)
        }
    }

    // Bind the camera for the mode. Rebinding is how CameraX switches lens, mode and extension.
    DisposableEffect(provider, granted, mode, front, look, prefs.video, prefs.fps60, prefs.stabilize, prefs.bestQuality, burn, prefs.histogram, eightKActive) {
        val p = provider
        var effect: OverlayEffect? = null
        camera = null; imageCapture = null; videoCapture = null; ability = null; histogram = null
        if (p != null && granted && mode != CamMode.AUDIO && !eightKActive) {
            val base = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val ext = extensions
            looks = PhotoLook.entries.filter { it.ext == null || runCatching { ext?.isExtensionAvailable(base, it.ext) }.getOrNull() == true }.toSet()
            val wantExt = when (mode) { CamMode.NIGHT -> ExtensionMode.NIGHT; CamMode.PHOTO -> look.ext; else -> null }
            vendorNight = mode == CamMode.NIGHT && ext != null && runCatching { ext.isExtensionAvailable(base, ExtensionMode.NIGHT) }.getOrDefault(false)
            val selector = if (wantExt != null && ext != null && runCatching { ext.isExtensionAvailable(base, wantExt) }.getOrDefault(false))
                runCatching { ext.getExtensionEnabledCameraSelector(base, wantExt) }.getOrDefault(base) else base
            val info = runCatching { base.filter(p.availableCameraInfos).firstOrNull() }.getOrNull()
            if (info != null) {
                videoQualities = runCatching { Recorder.getVideoCapabilities(info).getSupportedQualities(DynamicRange.SDR) }.getOrDefault(emptyList())
                    .mapNotNull { q -> when (q) { Quality.HD -> "HD"; Quality.FHD -> "FHD"; Quality.UHD -> "UHD"; else -> null } }
                fps60Ok = runCatching { info.supportedFrameRateRanges.any { it.upper >= 60 } }.getOrDefault(false)
            }
            fun bind(simple: Boolean): Camera? {
                p.unbindAll()
                val preview = Preview.Builder().setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(
                    if (mode == CamMode.VIDEO) androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                    else androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                    .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val group = UseCaseGroup.Builder().addUseCase(preview)
                if (mode == CamMode.VIDEO) {
                    val q = when (prefs.video) { "HD" -> Quality.HD; "UHD", "8K" -> Quality.UHD; else -> Quality.FHD }
                    val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q))).build()
                    val vb = VideoCapture.Builder(recorder)
                    if (!simple) {
                        vb.setTargetFrameRate(if (prefs.fps60 && fps60Ok) Range(60, 60) else Range(30, 30))
                        vb.setVideoStabilizationEnabled(prefs.stabilize)
                    }
                    val vc = vb.build(); videoCapture = vc; group.addUseCase(vc)
                    if (burn && !simple) {
                        effect = OverlayEffect(CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE, 0, Handler(markThread.looper)) { }.also { fx ->
                            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                            fx.setOnDrawListener { frame ->
                                val canvas = frame.overlayCanvas
                                canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                val crop = frame.cropRect; val rot = frame.rotationDegrees
                                val uw = if (rot % 180 == 0) crop.width() else crop.height(); val uh = if (rot % 180 == 0) crop.height() else crop.width()
                                if (uw > 0 && uh > 0) markAspect.set(uw.toFloat() / uh)
                                markRef.get()?.let { bmp ->
                                    canvas.save()
                                    canvas.translate(crop.exactCenterX(), crop.exactCenterY())
                                    canvas.rotate(-rot.toFloat())
                                    if (frame.isMirroring) canvas.scale(-1f, 1f)
                                    canvas.drawBitmap(bmp, null, android.graphics.RectF(-uw / 2f, -uh / 2f, uw / 2f, uh / 2f), paint)
                                    canvas.restore()
                                }
                                true
                            }
                            group.addEffect(fx)
                        }
                    }
                } else {
                    val ib = ImageCapture.Builder()
                        .setCaptureMode(if (mode == CamMode.NIGHT && !vendorNight) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            else if (prefs.bestQuality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(if (prefs.bestQuality) 100 else 95)
                        .setFlashMode(flash.mode)
                    val ic = ib.build(); imageCapture = ic; group.addUseCase(ic)
                    if (mode == CamMode.PRO && prefs.histogram && !simple) {
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                                ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()).build()
                        var lastAt = 0L
                        analysis.setAnalyzer(analysisExecutor) { img ->
                            val now = System.currentTimeMillis()
                            if (now - lastAt > 200) {
                                lastAt = now
                                runCatching {
                                    val plane = img.planes[0]; val buf = plane.buffer; val bins = FloatArray(64)
                                    val rowStride = plane.rowStride; val w = img.width; val h = img.height
                                    var y = 0
                                    while (y < h) { var x = 0; while (x < w) { bins[(buf.get(y * rowStride + x).toInt() and 255) shr 2]++; x += 4 }; y += 4 }
                                    val peak = bins.max().coerceAtLeast(1f); for (i in bins.indices) bins[i] /= peak
                                    previewView.post { histogram = bins }
                                }
                            }
                            img.close()
                        }
                        group.addUseCase(analysis)
                    }
                }
                return p.bindToLifecycle(owner, selector, group.build())
            }
            val cam = runCatching { bind(false) }.recoverCatching {
                effect?.close(); effect = null
                message = if (mode == CamMode.VIDEO) "這支手機不支援這組錄影設定，已改用一般錄影" else "已改用一般模式"
                bind(true)
            }.getOrNull()
            if (cam == null) message = "相機無法啟動"
            camera = cam
            cam?.let { c ->
                val c2 = runCatching { Camera2CameraInfo.from(c.cameraInfo) }.getOrNull()
                fun <T> ch(key: CameraCharacteristics.Key<T>): T? = runCatching { c2?.getCameraCharacteristic(key) }.getOrNull()
                val caps = ch(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList() ?: emptyList()
                ability = CameraAbility(c.cameraInfo.exposureState.exposureCompensationRange, c.cameraInfo.exposureState.exposureCompensationStep.toFloat(),
                    ch(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE), ch(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                    ch(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps,
                    ch(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.minWithOrNull(compareBy<Range<Int>>({ it.lower }, { it.upper })))
                maxZoom = c.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            }
        }
        onDispose {
            runCatching { recording?.stop() }
            runCatching { p?.unbindAll() }
            runCatching { effect?.close() }
        }
    }
    // Zoom follows the camera (pinch, buttons).
    DisposableEffect(camera) {
        val c = camera
        val watch = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { z -> if (z != null) { zoom = z.zoomRatio; maxZoom = z.maxZoomRatio } }
        c?.cameraInfo?.zoomState?.observeForever(watch)
        onDispose { c?.cameraInfo?.zoomState?.removeObserver(watch) }
    }
    LaunchedEffect(flash, imageCapture) { runCatching { imageCapture?.flashMode = flash.mode } }
    // 專業 and 超級夜景 settings go straight into the camera's capture requests.
    LaunchedEffect(camera, pro, mode, vendorNight, ability) {
        val c = camera ?: return@LaunchedEffect; val a = ability
        val c2 = runCatching { Camera2CameraControl.from(c.cameraControl) }.getOrNull() ?: return@LaunchedEffect
        val b = CaptureRequestOptions.Builder()
        when {
            mode == CamMode.PRO -> {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, pro.wb.mode)
                if (a?.manualSensor == true && (pro.iso != null || pro.shutterNs != null)) {
                    val shutter = (pro.shutterNs ?: 1_000_000_000L / 60).let { s -> a.shutterRange?.let { s.coerceIn(it.lower, it.upper) } ?: s }
                    val iso = (pro.iso ?: 200).let { i -> a.isoRange?.let { i.coerceIn(it.lower, it.upper) } ?: i }
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutter)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(shutter, 33_333_333L))
                }
                val focus = pro.focus
                if (focus != null && a != null && a.minFocus > 0f) {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focus * a.minFocus)
                }
            }
            mode == CamMode.NIGHT && !vendorNight -> {
                // Let each frame expose longer and clean it as well as the phone can.
                a?.lowFps?.let { b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                b.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                b.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            }
        }
        runCatching { c2.setCaptureRequestOptions(b.build()) }
        if (mode == CamMode.PRO && a != null && pro.iso == null && pro.shutterNs == null)
            runCatching { c.cameraControl.setExposureCompensationIndex(pro.ev.coerceIn(a.evRange.lower, a.evRange.upper)) }
        else if (mode != CamMode.PRO) runCatching { c.cameraControl.setExposureCompensationIndex(0) }
    }
    // The spirit level: how far the phone leans from upright.
    DisposableEffect(prefs.level, mode) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravity = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (!prefs.level || mode == CamMode.AUDIO || sm == null || gravity == null) return@DisposableEffect onDispose { }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val r = Math.toDegrees(kotlin.math.atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
                // Measure against the nearest edge, so holding the phone sideways also reads level.
                val near = ((r + 45f + 360f) % 90f) - 45f
                roll += (near - roll) * .25f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, gravity, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    // Recording clock.
    LaunchedEffect(recording, recPaused) {
        if (recording == null) return@LaunchedEffect
        while (isActive && !recPaused) { delay(1000); recSeconds++ }
    }

    // ---- the 8K recorder (Camera2), used instead of CameraX when 8K is chosen
    val hires = remember(eightKActive) { if (eightKActive) HighResVideo(ctx) { message = it } else null }
    var hiresRecording by remember { mutableStateOf(false) }
    DisposableEffect(hires) { onDispose { hires?.close() } }
    LaunchedEffect(hiresRecording) { if (!hiresRecording) return@LaunchedEffect; recSeconds = 0; while (isActive) { delay(1000); recSeconds++ } }
    val rotation = remember { runCatching { @Suppress("DEPRECATION") (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation * 90 }.getOrDefault(0) }

    fun takePhoto() {
        val ic = imageCapture ?: return
        if (busy) return
        busy = true
        scope.launch {
            for (s in timer downTo 1) { countdown = s; delay(1000) }
            countdown = 0
            val mirror = front && prefs.mirrorFront
            if (mode == CamMode.NIGHT && !vendorNight) {
                // 超級夜景: several frames, aligned and averaged.
                val stacker = SnapPixels.Stacker()
                val n = prefs.nightFrames
                for (i in 1..n) {
                    progress = "超級夜景 $i / $n · 請保持手機穩定"
                    val img = ic.shot(ctx) ?: break
                    val bmp = withContext(Dispatchers.Default) { img.upright(2400, mirror) }; img.close()
                    if (bmp != null) withContext(Dispatchers.Default) { stacker.add(bmp) }
                }
                progress = "合成中…"
                val result = withContext(Dispatchers.Default) { runCatching { stacker.result() }.getOrNull() }
                progress = ""; busy = false
                blink.snapTo(1f); launch { blink.animateTo(0f, tween(260)) }
                if (result != null) onPhoto(result, SnapRender.Edit(aspect = aspect, template = template)) else message = "夜景拍攝失敗，請再試一次"
                return@launch
            }
            if (mode == CamMode.NIGHT) progress = "夜景處理中，請保持穩定…"
            val file = File(File(ctx.cacheDir, "snap").apply { mkdirs() }, "shot-${System.currentTimeMillis()}.jpg")
            ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    scope.launch {
                        progress = ""
                        blink.snapTo(1f); launch { blink.animateTo(0f, tween(260)) }
                        val bmp = withContext(Dispatchers.IO) { SnapRender.decode(ctx, Uri.fromFile(file), maxSide = 4000, mirror = mirror) }
                        file.delete(); busy = false
                        if (bmp != null) onPhoto(bmp, SnapRender.Edit(aspect = aspect, template = template)) else message = "照片讀取失敗，請再拍一次"
                    }
                }
                override fun onError(e: ImageCaptureException) { busy = false; progress = ""; message = "拍照失敗，請再試一次" }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleVideo() {
        if (eightKActive) {
            val h = hires ?: return
            if (hiresRecording) { val u = h.stop(); hiresRecording = false; message = if (u != null) "已存到相簿（Movies/thinn-khi）" else "錄影失敗" }
            else { hiresRecording = h.start(rotation); if (!hiresRecording) message = "8K 錄影無法開始" }
            return
        }
        recording?.let { it.stop(); return }
        val vc = videoCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "thinn-khi-${System.currentTimeMillis()}.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/thinn-khi")
        }
        val out = MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        var pending = vc.output.prepareRecording(ctx, out)
        if (prefs.sound && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recSeconds = 0; recPaused = false
        recording = runCatching {
            pending.start(ContextCompat.getMainExecutor(ctx)) { e ->
                if (e is VideoRecordEvent.Finalize) {
                    recording = null; recPaused = false
                    message = if (e.hasError() && e.error != VideoRecordEvent.Finalize.ERROR_NONE && e.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE)
                        "錄影沒有存好（代碼 ${e.error}）" else "已存到相簿（Movies/thinn-khi）"
                }
            }
        }.getOrElse { message = "錄影無法開始"; null }
    }

    var root by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = LocalDensity.current
    val insetTop = WindowInsets.statusBars.getTop(density)
    val photoLike = mode == CamMode.PHOTO || mode == CamMode.NIGHT || mode == CamMode.PRO
    // Where the camera's picture sits: like the iOS camera, the whole sensor picture (4:3 for photos,
    // 16:9 for video) under the top bar, so what you frame is exactly what is taken.
    val stream = if (mode == CamMode.VIDEO) 9f / 16f else 3f / 4f
    val previewRect = remember(root, insetTop, stream) {
        val W = root.width.toFloat(); val H = root.height.toFloat()
        val top = insetTop + with(density) { 64.dp.toPx() }
        var pw = W; var ph = W / stream
        if (top + ph > H) { ph = (H - top).coerceAtLeast(1f); pw = ph * stream }
        val left = (W - pw) / 2
        androidx.compose.ui.geometry.Rect(left, top, left + pw, top + ph)
    }
    val frameRect = remember(previewRect, aspect, photoLike) {
        val r = if (photoLike) aspect.ratio else null
        val pw = previewRect.width; val ph = previewRect.height
        if (r == null || pw <= 0f || ph <= 0f) previewRect else {
            val (fw, fh) = if (pw / ph > r) ph * r to ph else pw to pw / r
            androidx.compose.ui.geometry.Rect(previewRect.center.x - fw / 2, previewRect.center.y - fh / 2, previewRect.center.x + fw / 2, previewRect.center.y + fh / 2)
        }
    }
    val showMark = prefs.liveMark && template != SnapRender.Template.NONE && mode != CamMode.AUDIO && mode != CamMode.VIDEO
    val overlay by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, frameRect, template, facts, showMark) {
        if (frameRect.width < 2f || !showMark) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) { SnapRender.overlay(ctx, (frameRect.width / 2).roundToInt(), (frameRect.height / 2).roundToInt(), SnapRender.Edit(aspect = aspect, template = template), facts).asImageBitmap() }
    }
    fun Modifier.at(r: androidx.compose.ui.geometry.Rect) = this.offset { androidx.compose.ui.unit.IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
        .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { root = it }) {
            if (mode == CamMode.AUDIO) {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 86.dp).verticalScroll(rememberScrollState()).padding(bottom = 170.dp)) {
                    AudioRecorderPanel(prefs.audioHigh, { update(prefs.copy(audioHigh = it)) })
                }
            } else if (granted && root.width > 0) {
                if (eightKActive) AndroidView({ c -> TextureView(c).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(t: android.graphics.SurfaceTexture, w: Int, h: Int) { hires?.open(this@apply, t, rotation) }
                        override fun onSurfaceTextureSizeChanged(t: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(t: android.graphics.SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(t: android.graphics.SurfaceTexture) {}
                    }
                } }, Modifier.at(previewRect))
                else AndroidView({ previewView }, Modifier.at(previewRect)
                    .pointerInput(camera, mode, pro.focus) {
                        detectTapGestures { pos ->
                            val c = camera ?: return@detectTapGestures
                            if (mode == CamMode.PRO && pro.focus != null) return@detectTapGestures
                            val point = previewView.meteringPointFactory.createPoint(pos.x, pos.y)
                            runCatching { c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build()) }
                            focusAt = Offset(pos.x + previewRect.left, pos.y + previewRect.top)
                        }
                    }
                    .pointerInput(camera) {
                        detectTransformGestures { _, _, z, _ ->
                            val c = camera ?: return@detectTransformGestures
                            if (z != 1f) runCatching { c.cameraControl.setZoomRatio((zoom * z).coerceIn(c.cameraInfo.zoomState.value?.minZoomRatio ?: 1f, maxZoom)) }
                        }
                    })
                // Shade outside the photo's frame, grid, level and focus square.
                Canvas(Modifier.fillMaxSize()) {
                    val l = frameRect.left; val t = frameRect.top; val fw = frameRect.width; val fh = frameRect.height
                    val shade = Color.Black.copy(alpha = .6f)
                    val pr = previewRect
                    if (t > pr.top) { drawRect(shade, Offset(pr.left, pr.top), androidx.compose.ui.geometry.Size(pr.width, t - pr.top)); drawRect(shade, Offset(pr.left, t + fh), androidx.compose.ui.geometry.Size(pr.width, pr.bottom - t - fh)) }
                    if (l > pr.left) { drawRect(shade, Offset(pr.left, t), androidx.compose.ui.geometry.Size(l - pr.left, fh)); drawRect(shade, Offset(l + fw, t), androidx.compose.ui.geometry.Size(pr.right - l - fw, fh)) }
                    val line = Color.White.copy(alpha = .45f); val px = 1.dp.toPx()
                    when (prefs.grid) {
                        1 -> for (i in 1..2) { drawLine(line, Offset(l + fw * i / 3, t), Offset(l + fw * i / 3, t + fh), px); drawLine(line, Offset(l, t + fh * i / 3), Offset(l + fw, t + fh * i / 3), px) }
                        2 -> for (k in listOf(.382f, .618f)) { drawLine(line, Offset(l + fw * k, t), Offset(l + fw * k, t + fh), px); drawLine(line, Offset(l, t + fh * k), Offset(l + fw, t + fh * k), px) }
                        3 -> { val cx = l + fw / 2; val cy = t + fh / 2; val s = 22.dp.toPx(); drawLine(line, Offset(cx - s, cy), Offset(cx + s, cy), px * 1.5f); drawLine(line, Offset(cx, cy - s), Offset(cx, cy + s), px * 1.5f) }
                    }
                    if (prefs.level && mode != CamMode.VIDEO) {
                        val level = abs(roll) < 1f
                        val c = frameRect.center; val len = 60.dp.toPx()
                        val a = Math.toRadians(-roll.toDouble())
                        val dx = (kotlin.math.cos(a) * len).toFloat(); val dy = (kotlin.math.sin(a) * len).toFloat()
                        drawLine(if (level) Color(0xFFFFD60A) else Color.White.copy(alpha = .85f), Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), 2.dp.toPx())
                        if (!level) { drawLine(Color.White.copy(alpha = .45f), Offset(c.x - len * 1.25f, c.y), Offset(c.x - len * 1.08f, c.y), 2.dp.toPx()); drawLine(Color.White.copy(alpha = .45f), Offset(c.x + len * 1.08f, c.y), Offset(c.x + len * 1.25f, c.y), 2.dp.toPx()) }
                    }
                    focusAt?.let { f -> val s = 36.dp.toPx(); drawRect(Color(0xFFFFD60A), Offset(f.x - s, f.y - s), androidx.compose.ui.geometry.Size(s * 2, s * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) }
                }
                LaunchedEffect(focusAt) { if (focusAt != null) { delay(1200); focusAt = null } }
                overlay?.let { img -> Image(img, "浮水印預覽：${template.label}", Modifier.at(frameRect), contentScale = ContentScale.FillBounds) }
                // 專業: the histogram, at the frame's top-right corner.
                if (mode == CamMode.PRO && prefs.histogram) histogram?.let { bins ->
                    Canvas(Modifier.offset { androidx.compose.ui.unit.IntOffset((frameRect.right - with(density) { 132.dp.toPx() }).roundToInt(), (frameRect.top + with(density) { 12.dp.toPx() }).roundToInt()) }
                        .size(118.dp, 56.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .45f)).padding(6.dp)) {
                        val bw = size.width / bins.size
                        bins.forEachIndexed { i, v -> drawRect(Color.White.copy(alpha = .8f), Offset(i * bw, size.height * (1 - v)), androidx.compose.ui.geometry.Size(bw, size.height * v)) }
                    }
                }
            } else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.NoPhotography, null, Modifier.size(56.dp), tint = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(if (asked) "沒有相機權限" else "需要相機權限", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("可以改從相簿選照片、改用錄音，或到系統設定允許相機。", color = Color.White.copy(alpha = .8f), fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("允許相機") }
                    GlassOutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("從相簿選") }
                }
            }

            // ---- top bar
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundTool(Icons.Rounded.Close, "關閉", onClick = onClose)
                Spacer(Modifier.weight(1f))
                if (recording != null || hiresRecording) {
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(java.util.Locale.US, "%02d:%02d:%02d", recSeconds / 3600, recSeconds / 60 % 60, recSeconds % 60), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (mode != CamMode.AUDIO) {
                    if (photoLike) {
                        RoundTool(when (flash) { Flash.OFF -> Icons.Rounded.FlashOff; Flash.AUTO -> Icons.Rounded.FlashAuto; Flash.ON -> Icons.Rounded.FlashOn }, "閃光燈", flash == Flash.ON) {
                            flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size]
                        }
                        Spacer(Modifier.width(8.dp))
                        RoundTool(when (timer) { 3 -> Icons.Rounded.Timer3; 10 -> Icons.Rounded.Timer10; else -> Icons.Rounded.TimerOff }, "倒數", timer > 0) {
                            timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.height(44.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .38f)).clickable {
                            val list = listOf(SnapRender.Aspect.CLASSIC, SnapRender.Aspect.SQUARE, SnapRender.Aspect.PORTRAIT, SnapRender.Aspect.STORY, SnapRender.Aspect.WIDE)
                            aspect = list[(list.indexOf(aspect) + 1) % list.size]
                        }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(aspect.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    } else if (mode == CamMode.VIDEO) {
                        val label = when (prefs.video) { "HD" -> "720p"; "UHD" -> "4K"; "8K" -> "8K"; else -> "1080p" } + if (prefs.fps60 && prefs.video != "8K") " · 60" else " · 30"
                        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .38f)).clickable { settingsOpen = true }.padding(horizontal = 14.dp, vertical = 11.dp))
                    }
                    if (photoLike) { Spacer(Modifier.width(8.dp)); RoundTool(Icons.Rounded.TextFields, "浮水印樣式", trayOpen) { trayOpen = !trayOpen } }
                    Spacer(Modifier.width(8.dp))
                    RoundTool(Icons.Rounded.Settings, "相機設定") { settingsOpen = true }
                }
            }
            if (countdown > 0) Text("$countdown", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            if (progress.isNotEmpty()) Text(progress, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center)
                .clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 16.dp, vertical = 10.dp))
            if (message.isNotEmpty()) Text(message, color = Color.White, fontSize = 15.sp, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp)
                .clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .7f)).padding(horizontal = 14.dp, vertical = 8.dp))

            // Zoom stops float at the bottom of the picture, as on the iOS camera.
            if (granted && mode != CamMode.AUDIO && !eightKActive && camera != null && frameRect.height > 0f) Row(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(0, (minOf(frameRect.bottom, previewRect.bottom) - with(density) { 58.dp.toPx() }).roundToInt()) }.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                val stops = listOf(1f, 2f, 5f, 10f).filter { it <= maxZoom + .01f }
                val active = stops.lastOrNull { zoom >= it - .05f } ?: 1f
                stops.forEach { z ->
                    val on = z == active
                    Box(Modifier.size(if (on) 42.dp else 36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)).clickable(onClickLabel = "${z.roundToInt()} 倍") {
                        runCatching { camera?.cameraControl?.setZoomRatio(z) }
                    }, contentAlignment = Alignment.Center) {
                        val label = if (on) (if (abs(zoom - z) < .05f) "${z.roundToInt()}×" else String.format(java.util.Locale.US, "%.1f×", zoom)) else "${z.roundToInt()}"
                        Text(label, color = if (on) Color(0xFFFFD60A) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            // ---- bottom: options for the mode, the watermark styles, the mode ribbon, then gallery · shutter · flip
            if (mode != CamMode.AUDIO) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (granted && mode != CamMode.AUDIO && !eightKActive && recording == null) {
                    // The watermark styles, when their tray is open (the button in the top bar), take this row.
                    if (trayOpen && photoLike) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        SnapRender.Template.entries.forEach { t ->
                            val on = t == template
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClickLabel = t.label) { template = t }) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(if (on) Color.White.copy(alpha = .28f) else Color.Black.copy(alpha = .42f))
                                    .border(if (on) 2.5.dp else 1.dp, if (on) Color(0xFFFFD60A) else Color.White.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(t.glyph, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(t.label, color = if (on) Color(0xFFFFD60A) else Color.White.copy(alpha = .8f), fontSize = 14.sp, maxLines = 1)
                            }
                        }
                    } else when (mode) {
                        CamMode.PHOTO -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhotoLook.entries.forEach { l -> Pill(l.label, l == look, enabled = l in looks) { look = l } }
                        }
                        CamMode.NIGHT -> Text(if (vendorNight) "使用手機原廠夜景演算法" else "${prefs.nightFrames} 張連拍對齊疊合 · 降噪提亮", color = Color.White.copy(alpha = .85f), fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 12.dp, vertical = 6.dp))
                        CamMode.PRO -> ProControls(pro, dial, ability, { pro = it }, { dial = it })
                        else -> {}
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(12.dp))
                // The mode ribbon, like the iOS camera.
                if (recording == null && !hiresRecording) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CamMode.entries.forEach { m ->
                        val on = m == mode
                        Text(m.label, color = if (on) Color(0xFFFFD60A) else Color.White.copy(alpha = .85f), fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(99.dp)).clickable(onClickLabel = m.label) {
                                if (m == CamMode.VIDEO && !micGranted && prefs.sound) askMic.launch(Manifest.permission.RECORD_AUDIO)
                                mode = m
                            }.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (mode != CamMode.AUDIO) Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Color.White, RoundedCornerShape(12.dp)).clickable {
                        if (recording != null) { recording?.let { r -> if (recPaused) r.resume() else r.pause(); recPaused = !recPaused } }
                        else pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, contentAlignment = Alignment.Center) {
                        Icon(if (recording != null) (if (recPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause) else Icons.Rounded.PhotoLibrary,
                            if (recording != null) (if (recPaused) "繼續錄影" else "暫停錄影") else "相簿", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    val isVideo = mode == CamMode.VIDEO
                    val rec = recording != null || hiresRecording
                    Box(Modifier.size(84.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(if (rec) 22.dp else 7.dp)
                        .clip(if (rec) RoundedCornerShape(8.dp) else CircleShape)
                        .background(if (isVideo) Color(0xFFFF3B30) else if (busy) Color.White.copy(alpha = .45f) else Color.White)
                        .clickable(enabled = granted && (!busy || isVideo), role = Role.Button, onClickLabel = if (isVideo) (if (rec) "停止錄影" else "開始錄影") else "拍照") {
                            if (isVideo) toggleVideo() else takePhoto()
                        })
                    Spacer(Modifier.weight(1f))
                    if (!rec) RoundTool(Icons.Rounded.Cameraswitch, "切換鏡頭") { front = !front } else Spacer(Modifier.size(44.dp))
                }
            }
            if (blink.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = blink.value)))
            if (settingsOpen) CameraSettingsSheet(prefs, videoQualities, fps60Ok, eightK != null, { update(it) }, { settingsOpen = false })
        }
    }
}

/** 專業: the dial being set (EV, ISO, shutter, white balance, focus) and its choices. */
@Composable
private fun ProControls(pro: ProSettings, dial: ProDial, a: CameraAbility?, onChange: (ProSettings) -> Unit, onDial: (ProDial) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val manualOk = a?.manualSensor == true
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (dial) {
                ProDial.EV -> {
                    val range = a?.evRange ?: Range(-6, 6); val step = a?.evStep ?: .33f
                    (range.lower..range.upper step maxOf(1, (1f / 3f / step).roundToInt())).forEach { i ->
                        Pill(String.format(java.util.Locale.US, "%+.1f", i * step), pro.ev == i, enabled = pro.iso == null && pro.shutterNs == null) { onChange(pro.copy(ev = i)) }
                    }
                }
                ProDial.ISO -> {
                    Pill("自動", pro.iso == null) { onChange(pro.copy(iso = null)) }
                    ISOS.filter { a?.isoRange?.contains(it) ?: true }.forEach { v -> Pill("$v", pro.iso == v, enabled = manualOk) { onChange(pro.copy(iso = v)) } }
                }
                ProDial.SHUTTER -> {
                    Pill("自動", pro.shutterNs == null) { onChange(pro.copy(shutterNs = null)) }
                    SHUTTERS.filter { a?.shutterRange?.contains(it) ?: true }.forEach { v -> Pill(shutterLabel(v), pro.shutterNs == v, enabled = manualOk) { onChange(pro.copy(shutterNs = v)) } }
                }
                ProDial.WB -> WhiteBalance.entries.forEach { w -> Pill(w.label, pro.wb == w) { onChange(pro.copy(wb = w)) } }
                ProDial.FOCUS -> {
                    Pill("自動", pro.focus == null) { onChange(pro.copy(focus = null)) }
                    listOf(0f to "無限遠", .1f to "遠景", .3f to "中景", .6f to "近拍", 1f to "微距").forEach { (v, l) ->
                        Pill(l, pro.focus == v, enabled = (a?.minFocus ?: 0f) > 0f) { onChange(pro.copy(focus = v)) }
                    }
                }
            }
        }
        if (!manualOk && (dial == ProDial.ISO || dial == ProDial.SHUTTER)) Text("這支手機沒有開放手動 ISO 與快門", color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProDial.entries.forEach { d ->
                val value = when (d) {
                    ProDial.EV -> String.format(java.util.Locale.US, "%+.1f", pro.ev * (a?.evStep ?: .33f))
                    ProDial.ISO -> pro.iso?.toString() ?: "A"
                    ProDial.SHUTTER -> pro.shutterNs?.let { shutterLabel(it) } ?: "A"
                    ProDial.WB -> if (pro.wb == WhiteBalance.AUTO) "A" else pro.wb.label
                    ProDial.FOCUS -> if (pro.focus == null) "AF" else "MF"
                }
                Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (d == dial) Color.White.copy(alpha = .22f) else Color.Black.copy(alpha = .4f))
                    .clickable(onClickLabel = d.label) { onDial(d) }.padding(horizontal = 10.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.label, color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
                    Text(value, color = if (d == dial) Color(0xFFFFD60A) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** 相機設定: a grouped list like the iOS Settings → Camera page. */
@Composable
private fun CameraSettingsSheet(p: CameraPrefs, qualities: List<String>, fps60: Boolean, has8K: Boolean, onChange: (CameraPrefs) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(onClickLabel = "關閉設定", onClick = onClose)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 620.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xFF1C1C1E)).clickable(enabled = false) {}.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("相機設定", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("完成", color = Color(0xFFFFD60A), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
            }
            SettingGroup("錄影") {
                ChoiceRow("解析度", listOfNotNull("HD" to "720p", "FHD" to "1080p", "UHD" to "4K", if (has8K) "8K" to "8K" else null)
                    .filter { it.first == "8K" || qualities.isEmpty() || it.first in qualities }, p.video) { onChange(p.copy(video = it)) }
                ToggleRow("每秒 60 格", if (!fps60) "這支手機的這顆鏡頭不支援" else if (p.video == "8K") "8K 以手機提供的格數錄影" else "動作更流暢", p.fps60 && fps60, fps60 && p.video != "8K") { onChange(p.copy(fps60 = it)) }
                ToggleRow("防手震", "走動時畫面更穩", p.stabilize) { onChange(p.copy(stabilize = it)) }
                ToggleRow("錄製聲音", "需要麥克風權限", p.sound) { onChange(p.copy(sound = it)) }
                ToggleRow("浮水印錄進影片", if (p.video == "8K") "8K 不支援即時浮水印" else "天氣資訊直接燒錄在影片裡", p.burnMark, p.video != "8K") { onChange(p.copy(burnMark = it)) }
            }
            SettingGroup("拍照") {
                ToggleRow("最高畫質", "最大解析度、JPEG 品質 100", p.bestQuality) { onChange(p.copy(bestQuality = it)) }
                ChoiceRow("超級夜景張數", listOf("4" to "4 張", "6" to "6 張", "8" to "8 張"), p.nightFrames.toString()) { onChange(p.copy(nightFrames = it.toInt())) }
                ToggleRow("前鏡頭鏡像", "自拍照與看到的一樣", p.mirrorFront) { onChange(p.copy(mirrorFront = it)) }
            }
            SettingGroup("構圖與輔助") {
                ChoiceRow("格線", listOf("0" to "無", "1" to "三分線", "2" to "黃金比例", "3" to "十字"), p.grid.toString()) { onChange(p.copy(grid = it.toInt())) }
                ToggleRow("水平儀", "拍照時顯示水平線，對齊時變黃", p.level) { onChange(p.copy(level = it)) }
                ToggleRow("取景時顯示浮水印", "拍之前就看到成品", p.liveMark) { onChange(p.copy(liveMark = it)) }
                ToggleRow("專業模式直方圖", "看亮部與暗部是否過曝", p.histogram) { onChange(p.copy(histogram = it)) }
            }
            Text("照片與影片只存在你的手機：照片在 Pictures/thinn-khi，影片在 Movies/thinn-khi，錄音在 Recordings/thinn-khi。",
                color = Color.White.copy(alpha = .55f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = Color.White.copy(alpha = .6f), fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF2C2C2E)).padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun ToggleRow(title: String, note: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!on) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else Color.White.copy(alpha = .4f), fontSize = 16.sp)
            Text(note, color = Color.White.copy(alpha = .5f), fontSize = 14.sp)
        }
        Box(Modifier.size(width = 50.dp, height = 30.dp).clip(RoundedCornerShape(99.dp)).background(if (on && enabled) Color(0xFF34C759) else Color.White.copy(alpha = .2f)).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White)) }
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (k, l) -> Pill(l, k == value) { onPick(k) } }
        }
    }
}
