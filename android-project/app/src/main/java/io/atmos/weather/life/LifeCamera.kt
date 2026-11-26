package io.atmos.weather.life

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.presentation.home.WeatherShareCard
import io.atmos.weather.ui.*
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt

/** The weather facts printed on the photo, read once when the page opens. */
@Composable
private fun rememberSnapFacts(snapshot: WeatherSnapshot?): LifeCards.SnapFacts {
    val ctx = LocalContext.current
    return remember(snapshot) {
        val alt = runCatching {
            val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)?.takeIf { it.hasAltitude() }?.altitude?.roundToInt() else null
        }.getOrNull()
        LifeCards.SnapFacts(io.atmos.weather.location.LocationLabels.display(snapshot?.city?.name ?: "台灣"), Instant.now(),
            snapshot?.temperatureC?.roundToInt(), snapshot?.let { io.atmos.weather.domain.model.WeatherDescriptions.current(it) },
            snapshot?.pressureHpa?.roundToInt(), alt, snapshot?.humidityPct, snapshot?.let { WeatherShareCard.buddy(it.condition, it.isDay) })
    }
}

/** A sky gradient to show the templates before any photo is taken. */
private fun sampleSky(w: Int = 540, h: Int = 720): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF2B4C8C.toInt(), 0xFFE58A5B.toInt(), 0xFFF6C68A.toInt()), floatArrayOf(0f, .62f, 1f), android.graphics.Shader.TileMode.CLAMP)
    })
    c.drawCircle(w * .7f, h * .58f, w * .12f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE6B0.toInt() })
    c.drawRect(0f, h * .78f, w.toFloat(), h.toFloat(), android.graphics.Paint().apply { color = 0xFF1F2A3A.toInt() })
    return b
}

// ---------------------------------------------------------------- the page

@Composable
internal fun SnapPageV2(snapshot: WeatherSnapshot?) {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val facts = rememberSnapFacts(snapshot)
    var cameraOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Bitmap, SnapRender.Edit>?>(null) }
    var template by remember { mutableStateOf(SnapRender.Template.MAGAZINE) }
    var status by remember { mutableStateOf("") }
    val pageScope = rememberCoroutineScope()
    // Decoding a full photo takes a moment: never on the main thread, so the page keeps animating.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { pageScope.launch {
            val b = withContext(Dispatchers.IO) { SnapRender.decode(ctx, it) }
            if (b != null) editing = b to SnapRender.Edit(template = template) else status = "無法開啟這張照片"
        } }
    }
    val sky = remember { sampleSky() }
    val previews by produceState<Map<SnapRender.Template, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap(), facts) {
        value = withContext(Dispatchers.Default) { SnapRender.Template.entries.associateWith { t -> SnapRender.render(ctx, sky, SnapRender.Edit(template = t), facts, 540).asImageBitmap() } }
    }
    LifeHero(Color(0xFFE58A5B)) {
        Text("拍下天空，自動壓上當下天氣", color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("${facts.temperature?.let { "$it°" } ?: "--°"} · ${facts.place}" + (facts.pressure?.let { " · $it hPa" } ?: ""), color = dim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SnapRender.Template.entries.forEach { t ->
                val on = t == template
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.iosJellyPress().clickable { template = t }) {
                    val shape = Modifier.width(96.dp).height(128.dp).clip(RoundedCornerShape(14.dp))
                        .border(if (on) 3.dp else .5.dp, if (on) Color(0xFFE58A5B) else Color.White.copy(alpha = .3f), RoundedCornerShape(14.dp))
                    previews[t]?.let { Image(it, t.label, shape, contentScale = ContentScale.Crop) }
                        ?: Box(shape.background(Brush.verticalGradient(listOf(Color(0xFF5B8FD9), Color(0xFFF2A56B)))))
                    Text(t.label, color = if (on) MaterialTheme.colorScheme.onSurface else dim, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(onClick = { cameraOpen = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("開啟相機") }
            GlassOutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("從相簿選")
            }
        }
        if (status.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Note(status) }
    }
    SectionTitle("能做什麼", Icons.Rounded.AutoAwesome, IOSColors.orange)
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        listOf(Triple(Icons.Rounded.Camera, IOSColors.gray, "即時相機" to "閃光、倒數、格線、前後鏡頭、雙指縮放、點擊對焦"),
            Triple(Icons.Rounded.FilterVintage, IOSColors.pink, "${SnapRender.Filter.entries.size - 1} 種濾鏡" to "晴空、黃昏、底片、雲海、黑白…"),
            Triple(Icons.Rounded.Tune, IOSColors.blue, "5 種調整" to "亮度、對比、飽和度、色溫、暈影"),
            Triple(Icons.Rounded.Crop, IOSColors.green, "${SnapRender.Aspect.entries.size} 種比例" to "1:1、4:5、9:16 限動、16:9…"),
            Triple(Icons.Rounded.TextFields, IOSColors.orange, "${SnapRender.Template.entries.size - 1} 種浮水印" to "上方或下方，資料自己挑"),
            Triple(Icons.Rounded.IosShare, IOSColors.indigo, "存相簿、直接分享" to "Threads、IG 限動、LINE")).forEach { (icon, tint, t) ->
            Row(Modifier.weight(1f).frosted(18.dp).padding(12.dp), verticalAlignment = Alignment.Top) {
                IOSIconTile(icon, tint, 30.dp); Spacer(Modifier.width(10.dp))
                Column { Text(t.first, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(t.second, color = dim, fontSize = 14.sp, lineHeight = 18.sp) }
            }
        }
    }
    Note("相機只在這一頁開啟時使用；照片只存在你的手機相簿。")
    if (cameraOpen) CameraScreen(facts, template, onClose = { cameraOpen = false }, onPhoto = { b, edit -> cameraOpen = false; editing = b to edit })
    editing?.let { (b, e) -> EditorScreen(b, e, facts, onClose = { editing = null }) }
}

// ---------------------------------------------------------------- live camera

private enum class Flash(val mode: Int) { OFF(ImageCapture.FLASH_MODE_OFF), AUTO(ImageCapture.FLASH_MODE_AUTO), ON(ImageCapture.FLASH_MODE_ON) }

@Composable
private fun FullScreen(onClose: () -> Unit, content: @Composable BoxScope.() -> Unit) =
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
    }

@Composable
private fun RoundTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(if (active) Color(0xFFFFD60A) else Color.Black.copy(alpha = .38f))
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(24.dp), tint = if (active) Color.Black else Color.White)
    }
}

@Composable
private fun CameraScreen(facts: LifeCards.SnapFacts, startTemplate: SnapRender.Template, onClose: () -> Unit, onPhoto: (Bitmap, SnapRender.Edit) -> Unit) {
    val ctx = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { withContext(Dispatchers.IO) { SnapRender.decode(ctx, it) }?.let { b -> onPhoto(b, SnapRender.Edit(template = startTemplate)) } } }
    }
    var flash by remember { mutableStateOf(Flash.OFF) }
    var timer by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(true) }
    var aspect by remember { mutableStateOf(SnapRender.Aspect.CLASSIC) }
    var template by remember { mutableStateOf(startTemplate) }
    var front by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val blink = remember { Animatable(0f) }
    val controller = remember { LifecycleCameraController(ctx).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
    DisposableEffect(granted, owner) {
        if (granted) runCatching { controller.bindToLifecycle(owner) }.onFailure { error = "相機無法啟動" }
        onDispose { runCatching { controller.unbind() } }
    }
    LaunchedEffect(flash) { controller.imageCaptureFlashMode = flash.mode }
    DisposableEffect(controller) {
        val watch = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { z -> if (z != null) zoom = z.zoomRatio }
        controller.zoomState.observeForever(watch)
        onDispose { controller.zoomState.removeObserver(watch) }
    }
    LaunchedEffect(front) {
        val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (runCatching { controller.hasCamera(sel) }.getOrDefault(false)) controller.cameraSelector = sel
    }
    fun shoot() {
        if (busy) return
        busy = true
        scope.launch {
            for (s in timer downTo 1) { countdown = s; delay(1000) }
            countdown = 0
            val file = File(File(ctx.cacheDir, "snap").apply { mkdirs() }, "shot-${System.currentTimeMillis()}.jpg")
            controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    scope.launch {
                        blink.snapTo(1f); launch { blink.animateTo(0f, tween(260)) }
                        val bmp = withContext(Dispatchers.IO) { SnapRender.decode(ctx, Uri.fromFile(file), mirror = front) }
                        file.delete(); busy = false
                        if (bmp != null) onPhoto(bmp, SnapRender.Edit(aspect = aspect, template = template)) else error = "照片讀取失敗，請再拍一次"
                    }
                }
                override fun onError(e: ImageCaptureException) { busy = false; error = "拍照失敗，請再試一次" }
            })
        }
    }
    FullScreen(onClose) {
        if (granted) {
            AndroidView({ c -> PreviewView(c).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } },
                Modifier.fillMaxSize())
            // The frame of the chosen ratio, grid lines and a live look at the caption.
            Canvas(Modifier.fillMaxSize()) {
                val r = aspect.ratio
                val fw: Float; val fh: Float
                if (r == null) { fw = size.width; fh = size.height } else if (size.width / size.height > r) { fh = size.height; fw = fh * r } else { fw = size.width; fh = fw / r }
                val l = (size.width - fw) / 2; val t = (size.height - fh) / 2
                val shade = Color.Black.copy(alpha = .55f)
                if (t > 0) { drawRect(shade, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, t)); drawRect(shade, Offset(0f, t + fh), androidx.compose.ui.geometry.Size(size.width, size.height - t - fh)) }
                if (l > 0) { drawRect(shade, Offset.Zero, androidx.compose.ui.geometry.Size(l, size.height)); drawRect(shade, Offset(l + fw, 0f), androidx.compose.ui.geometry.Size(size.width - l - fw, size.height)) }
                if (grid) for (i in 1..2) {
                    drawLine(Color.White.copy(alpha = .45f), Offset(l + fw * i / 3, t), Offset(l + fw * i / 3, t + fh), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = .45f), Offset(l, t + fh * i / 3), Offset(l + fw, t + fh * i / 3), 1.dp.toPx())
                }
            }
            if (template != SnapRender.Template.NONE) Column(Modifier.align(Alignment.Center).padding(top = 220.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = .35f)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${facts.temperature?.let { "$it°" } ?: "--°"}  ${facts.place.take(12)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("浮水印：${template.label}（拍完可再調整）", color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
            }
        } else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.NoPhotography, null, Modifier.size(56.dp), tint = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(if (asked) "沒有相機權限" else "需要相機權限", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("可以改從相簿選照片，或到系統設定允許相機。", color = Color.White.copy(alpha = .8f), fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("允許相機") }
                GlassOutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("從相簿選") }
            }
        }
        // Top tools.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundTool(Icons.Rounded.Close, "關閉", onClick = onClose)
            Spacer(Modifier.weight(1f))
            RoundTool(when (flash) { Flash.OFF -> Icons.Rounded.FlashOff; Flash.AUTO -> Icons.Rounded.FlashAuto; Flash.ON -> Icons.Rounded.FlashOn }, "閃光燈", flash == Flash.ON) {
                flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size]
            }
            Spacer(Modifier.width(8.dp))
            RoundTool(when (timer) { 3 -> Icons.Rounded.Timer3; 10 -> Icons.Rounded.Timer10; else -> Icons.Rounded.TimerOff }, "倒數", timer > 0) {
                timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }
            }
            Spacer(Modifier.width(8.dp))
            RoundTool(if (grid) Icons.Rounded.Grid3x3 else Icons.Rounded.GridOff, "格線", grid) { grid = !grid }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .38f)).clickable {
                val list = listOf(SnapRender.Aspect.CLASSIC, SnapRender.Aspect.SQUARE, SnapRender.Aspect.PORTRAIT, SnapRender.Aspect.STORY, SnapRender.Aspect.WIDE)
                aspect = list[(list.indexOf(aspect) + 1) % list.size]
            }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(aspect.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        if (countdown > 0) Text("$countdown", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        if (error.isNotEmpty()) Text(error, color = Color.White, fontSize = 15.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp)
            .clip(RoundedCornerShape(99.dp)).background(Color(0xCCD5523F)).padding(horizontal = 14.dp, vertical = 8.dp))
        // Bottom: zoom, template strip, gallery · shutter · flip.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (granted) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val maxZ = runCatching { controller.zoomState.value?.maxZoomRatio }.getOrNull() ?: 1f
                val stops = listOf(1f, 2f, 5f).filter { it <= maxZ + .01f }
                // Like the iOS camera: the stop you are on shows the live ratio after a pinch (1.6×).
                val active = stops.lastOrNull { zoom >= it - .05f } ?: 1f
                stops.forEach { z ->
                    val on = z == active
                    Box(Modifier.size(if (on) 44.dp else 38.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)).clickable {
                        zoom = z; runCatching { controller.setZoomRatio(z) }
                    }, contentAlignment = Alignment.Center) {
                        val label = if (on) (if (kotlin.math.abs(zoom - z) < .05f) "${z.roundToInt()}×" else String.format(java.util.Locale.US, "%.1f×", zoom)) else "${z.roundToInt()}"
                        Text(label, color = if (on) Color(0xFFFFD60A) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SnapRender.Template.entries.forEach { t ->
                    Text(t.label, color = if (t == template) Color(0xFFFFD60A) else Color.White.copy(alpha = .8f), fontSize = 15.sp,
                        fontWeight = if (t == template) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.clickable { template = t }.padding(vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Color.White, RoundedCornerShape(12.dp)).clickable {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoLibrary, "相簿", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(82.dp).clip(CircleShape).border(5.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                    .background(if (busy) Color.White.copy(alpha = .5f) else Color.White).clickable(enabled = granted && !busy, role = Role.Button) { shoot() })
                Spacer(Modifier.weight(1f))
                RoundTool(Icons.Rounded.Cameraswitch, "切換鏡頭") { front = !front }
            }
        }
        if (blink.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = blink.value)))
    }
}

// ---------------------------------------------------------------- editor

private enum class Tab(val label: String) { FILTER("濾鏡"), ADJUST("調整"), MARK("浮水印"), CROP("裁切") }
private enum class Tool(val label: String) { BRIGHT("亮度"), CONTRAST("對比"), SATURATION("飽和度"), WARMTH("色溫"), VIGNETTE("暈影") }

@Composable
private fun EditorScreen(src: Bitmap, start: SnapRender.Edit, facts: LifeCards.SnapFacts, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(start) }
    var tab by remember { mutableStateOf(Tab.FILTER) }
    var tool by remember { mutableStateOf(Tool.BRIGHT) }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val small = remember(src) { val s = minOf(1f, 1280f / maxOf(src.width, src.height)); Bitmap.createScaledBitmap(src, maxOf(1, (src.width * s).roundToInt()), maxOf(1, (src.height * s).roundToInt()), true) }
    val thumb = remember(src) { val s = minOf(1f, 200f / maxOf(src.width, src.height)); Bitmap.createScaledBitmap(src, maxOf(1, (src.width * s).roundToInt()), maxOf(1, (src.height * s).roundToInt()), true) }
    val filterThumbs = remember(thumb) { SnapRender.Filter.entries.associateWith { f -> SnapRender.render(null, thumb, SnapRender.Edit(filter = f, template = SnapRender.Template.NONE), facts, 200).asImageBitmap() } }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(edit) {
        delay(30) // coalesce slider drags
        preview = withContext(Dispatchers.Default) { SnapRender.render(ctx, small, edit, facts, 1280).asImageBitmap() }
    }
    FullScreen(onClose) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("取消", color = Color.White, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
                Spacer(Modifier.weight(1f))
                Text("重設", color = Color.White.copy(alpha = .8f), fontSize = 17.sp, modifier = Modifier.clickable { edit = SnapRender.Edit(aspect = start.aspect, template = start.template) }.padding(8.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "儲存中…" else "儲存並分享", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0xFFFFD60A)).clickable(enabled = !saving) {
                        saving = true
                        scope.launch {
                            val full = withContext(Dispatchers.Default) { SnapRender.render(ctx, src, edit, facts, 2400) }
                            val uri = withContext(Dispatchers.IO) { LifeCards.saveToGallery(ctx, full) }
                            saving = false
                            status = if (uri == null) "儲存失敗，請確認空間" else "已存到相簿（Pictures/thinn-khi）"
                            uri?.let { LifeCards.shareImage(ctx, it, "${facts.place} ${facts.temperature ?: ""}° · thinn-khì") }
                        }
                    }.padding(horizontal = 16.dp, vertical = 9.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                preview?.let { Image(it, "編輯中的照片", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                if (status.isNotEmpty()) Text(status, color = Color.White, fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            // Tool area.
            Column(Modifier.fillMaxWidth().height(150.dp), verticalArrangement = Arrangement.Center) {
                when (tab) {
                    Tab.FILTER -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SnapRender.Filter.entries.forEach { f ->
                            val on = edit.filter == f
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { edit = edit.copy(filter = f) }) {
                                Text(f.label, color = if (on) Color.White else Color.White.copy(alpha = .7f), fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                Spacer(Modifier.height(6.dp))
                                Image(filterThumbs.getValue(f), f.label, Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
                                    .border(if (on) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                    Tab.ADJUST -> {
                        val v = when (tool) { Tool.BRIGHT -> edit.brightness; Tool.CONTRAST -> edit.contrast; Tool.SATURATION -> edit.saturation; Tool.WARMTH -> edit.warmth; Tool.VIGNETTE -> edit.vignette }
                        Text("${tool.label}  ${(v * 100).roundToInt()}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        GlassSlider(v, { x ->
                            edit = when (tool) { Tool.BRIGHT -> edit.copy(brightness = x); Tool.CONTRAST -> edit.copy(contrast = x); Tool.SATURATION -> edit.copy(saturation = x)
                                Tool.WARMTH -> edit.copy(warmth = x); Tool.VIGNETTE -> edit.copy(vignette = x) }
                        }, Modifier.fillMaxWidth().padding(horizontal = 18.dp), valueRange = if (tool == Tool.VIGNETTE) 0f..1f else -1f..1f)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tool.entries.forEach { t -> SnapPill(t.label, t == tool) { tool = t } }
                        }
                    }
                    Tab.MARK -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SnapRender.Template.entries.forEach { t -> SnapPill(t.label, edit.template == t) { edit = edit.copy(template = t) } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SnapRender.Position.entries.forEach { p -> SnapPill(p.label, edit.position == p) { edit = edit.copy(position = p) } }
                            Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = .3f)))
                            SnapRender.Field.entries.forEach { f -> SnapPill(f.label, f in edit.fields) {
                                edit = edit.copy(fields = if (f in edit.fields) edit.fields - f else edit.fields + f)
                            } }
                        }
                    }
                    Tab.CROP -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SnapRender.Aspect.entries.forEach { a -> SnapPill(a.label, edit.aspect == a) { edit = edit.copy(aspect = a) } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEach { t ->
                    Text(t.label, color = if (t == tab) Color.White else Color.White.copy(alpha = .55f), fontSize = 16.sp, fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.clickable { tab = t }.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SnapPill(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, color = if (on) Color.Black else Color.White, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Color.White else Color.White.copy(alpha = .14f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}
