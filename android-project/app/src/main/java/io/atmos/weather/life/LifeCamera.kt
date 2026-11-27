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
import androidx.compose.ui.layout.onSizeChanged
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

/**
 * A sample photo to show the designs before any is taken: a dusk sky over layered mountains,
 * with a low sun and soft clouds (drawn, so it works offline and needs no picture file).
 */
private fun sampleSky(w: Int = 864, h: Int = 1080): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val P = { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), P().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF233E7A.toInt(), 0xFF5C6FB0.toInt(), 0xFFE7908A.toInt(), 0xFFF7C58B.toInt()),
            floatArrayOf(0f, .35f, .62f, .78f), android.graphics.Shader.TileMode.CLAMP)
    })
    // The sun and its glow.
    c.drawCircle(w * .68f, h * .66f, w * .55f, P().apply {
        shader = android.graphics.RadialGradient(w * .68f, h * .66f, w * .55f, intArrayOf(0x88FFE2A8.toInt(), 0x00FFE2A8), null, android.graphics.Shader.TileMode.CLAMP) })
    c.drawCircle(w * .68f, h * .66f, w * .07f, P().apply { color = 0xFFFFF1CC.toInt() })
    // Soft clouds.
    val cloud = P().apply { color = 0x55FFFFFF; maskFilter = android.graphics.BlurMaskFilter(w * .04f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
    listOf(Triple(.22f, .22f, .26f), Triple(.7f, .3f, .3f), Triple(.45f, .46f, .22f)).forEach { (x, y, r) ->
        c.drawOval(w * (x - r), h * (y - r * .18f), w * (x + r), h * (y + r * .18f), cloud)
    }
    // Three ridges, far to near.
    listOf(Triple(.70f, 0xFF7B6FA0.toInt(), 1.3f), Triple(.76f, 0xFF4E4A78.toInt(), 2.1f), Triple(.84f, 0xFF262640.toInt(), 3.0f)).forEach { (base, color, f) ->
        val path = android.graphics.Path().apply {
            moveTo(0f, h.toFloat()); lineTo(0f, h * base)
            var x = 0f
            while (x <= w) { lineTo(x, h * base - h * .05f * kotlin.math.sin(x / w * Math.PI * f + base * 9).toFloat() - h * .02f * kotlin.math.sin(x / w * 13.0 + f).toFloat()); x += 6f }
            lineTo(w.toFloat(), h.toFloat()); close()
        }
        c.drawPath(path, P().apply { this.color = color })
    }
    return b
}

// ---------------------------------------------------------------- the page

@Composable
internal fun SnapPageV2(snapshot: WeatherSnapshot?) {
    val ctx = LocalContext.current
    val p = MaterialTheme.colorScheme
    val facts = rememberSnapFacts(snapshot)
    var cameraOpen by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf(CamMode.PHOTO) }
    var editing by remember { mutableStateOf<Pair<Bitmap, SnapRender.Edit>?>(null) }
    var status by remember { mutableStateOf("") }
    val pageScope = rememberCoroutineScope()
    val styles = SnapRender.Template.entries.filter { it != SnapRender.Template.NONE }
    val pager = androidx.compose.foundation.pager.rememberPagerState { styles.size }
    val template = styles[pager.currentPage]
    // Decoding a full photo takes a moment: never on the main thread, so the page keeps animating.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { pageScope.launch {
            val b = withContext(Dispatchers.IO) { SnapRender.decode(ctx, it) }
            if (b != null) editing = b to SnapRender.Edit(template = template) else status = "無法開啟這張照片"
        } }
    }
    val sky = remember { sampleSky() }
    val previews by produceState<Map<SnapRender.Template, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap(), facts) {
        value = withContext(Dispatchers.Default) { styles.associateWith { t -> SnapRender.render(ctx, sky, SnapRender.Edit(template = t), facts, 864).asImageBitmap() } }
    }
    // The stage: swipe through the designs on a real-looking photo, like a camera app's styles.
    Box(Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(30.dp)).background(Color(0xFF15161B))) {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val t = styles[page]
            previews[t]?.let { Image(it, t.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Image(remember { sky.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Row(Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .35f))
            .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(16.dp), tint = Color.White); Spacer(Modifier.width(6.dp))
            Text("天空浮水印", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End) {
            Text(template.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .35f)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    // Page dots, then the style names as a scrolling ribbon.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        styles.indices.forEach { i ->
            val on = i == pager.currentPage
            Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 18.dp else 6.dp).clip(RoundedCornerShape(99.dp))
                .background(if (on) p.onSurface else p.onSurface.copy(alpha = .25f)))
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEachIndexed { i, t ->
            val on = i == pager.currentPage
            Text(t.label, color = if (on) p.surface else p.onSurface, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) p.onSurface else p.onSurface.copy(alpha = .07f))
                    .clickable { pageScope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
    // The camera row: library · shutter · how it works.
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(p.onSurface.copy(alpha = .07f))
                .clickable(onClickLabel = "從相簿選") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(26.dp), tint = p.onSurface) }
            Spacer(Modifier.height(6.dp)); Text("相簿", color = p.onSurfaceVariant, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clip(CircleShape).border(5.dp, Brush.sweepGradient(listOf(Color(0xFFFFB86B), Color(0xFFFF6B8B), Color(0xFF8B7CFF), Color(0xFFFFB86B))), CircleShape)
                .padding(9.dp).clip(CircleShape).background(p.onSurface).clickable(onClickLabel = "開啟相機") { cameraMode = CamMode.PHOTO; cameraOpen = true },
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(34.dp), tint = p.surface) }
            Spacer(Modifier.height(6.dp)); Text("開啟相機", color = p.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(p.onSurface.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
                Text("${facts.temperature ?: "--"}°", color = p.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp)); Text(facts.condition ?: "現在", color = p.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
        }
    }
    if (status.isNotEmpty()) Note(status)
    // Straight into a mode: the camera's other modes, one tap away.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(Icons.Rounded.NightsStay, "超級夜景", CamMode.NIGHT), Triple(Icons.Rounded.Videocam, "錄影 4K", CamMode.VIDEO),
            Triple(Icons.Rounded.Tune, "專業", CamMode.PRO), Triple(Icons.Rounded.Mic, "錄音", CamMode.AUDIO)).forEach { (icon, label, m) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(p.onSurface.copy(alpha = .06f)).clickable(onClickLabel = label) { cameraMode = m; cameraOpen = true }
                .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, Modifier.size(22.dp), tint = p.onSurface.copy(alpha = .85f))
                Spacer(Modifier.height(5.dp))
                Text(label, color = p.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
    Note("相機有超級夜景、錄影（最高 4K 60 fps，支援的手機可 8K）、專業手動與錄音；編輯有 ${SnapRender.Filter.entries.size - 1} 種濾鏡、13 種調整。照片、影片與錄音只存在你的手機。")
    if (cameraOpen) CameraScreen(facts, template, cameraMode, onClose = { cameraOpen = false; cameraMode = CamMode.PHOTO }, onPhoto = { b, edit -> cameraOpen = false; editing = b to edit })
    editing?.let { (b, e) -> EditorScreen(b, e, facts, onClose = { editing = null }) }
}

// ---------------------------------------------------------------- live camera

@Composable
private fun FullScreen(onClose: () -> Unit, content: @Composable BoxScope.() -> Unit) =
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
    }

// ---------------------------------------------------------------- editor

private enum class Tab(val label: String) { FILTER("濾鏡"), ADJUST("調整"), MARK("浮水印"), CROP("裁切") }
private enum class Tool(val label: String) {
    BRIGHT("亮度"), EXPOSURE("曝光"), CONTRAST("對比"), HIGHLIGHTS("高光"), SHADOWS("陰影"), SATURATION("飽和度"), VIBRANCE("自然飽和"),
    WARMTH("色溫"), TINT("色調"), SHARPEN("銳利度"), FADE("褪色"), GRAIN("顆粒"), VIGNETTE("暈影");
    val positiveOnly get() = this == VIGNETTE || this == FADE || this == GRAIN
    fun read(e: SnapRender.Edit) = when (this) {
        BRIGHT -> e.brightness; EXPOSURE -> e.exposure; CONTRAST -> e.contrast; HIGHLIGHTS -> e.highlights; SHADOWS -> e.shadows
        SATURATION -> e.saturation; VIBRANCE -> e.vibrance; WARMTH -> e.warmth; TINT -> e.tint; SHARPEN -> e.sharpen; FADE -> e.fade
        GRAIN -> e.grain; VIGNETTE -> e.vignette
    }
    fun write(e: SnapRender.Edit, x: Float) = when (this) {
        BRIGHT -> e.copy(brightness = x); EXPOSURE -> e.copy(exposure = x); CONTRAST -> e.copy(contrast = x); HIGHLIGHTS -> e.copy(highlights = x)
        SHADOWS -> e.copy(shadows = x); SATURATION -> e.copy(saturation = x); VIBRANCE -> e.copy(vibrance = x); WARMTH -> e.copy(warmth = x)
        TINT -> e.copy(tint = x); SHARPEN -> e.copy(sharpen = x); FADE -> e.copy(fade = x); GRAIN -> e.copy(grain = x); VIGNETTE -> e.copy(vignette = x)
    }
}

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
            Column(Modifier.fillMaxWidth().height(176.dp), verticalArrangement = Arrangement.Center) {
                when (tab) {
                    Tab.FILTER -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SnapRender.Filter.entries.forEach { f ->
                                val on = edit.filter == f
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { edit = edit.copy(filter = f, strength = if (on) edit.strength else 1f) }) {
                                    Text(f.label, color = if (on) Color.White else Color.White.copy(alpha = .7f), fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                    Spacer(Modifier.height(6.dp))
                                    Image(filterThumbs.getValue(f), f.label, Modifier.size(if (edit.filter == SnapRender.Filter.ORIGINAL) 76.dp else 62.dp).clip(RoundedCornerShape(10.dp))
                                        .border(if (on) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                        if (edit.filter != SnapRender.Filter.ORIGINAL) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("強度 ${(edit.strength * 100).roundToInt()}", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(72.dp))
                            GlassSlider(edit.strength, { edit = edit.copy(strength = it) }, Modifier.weight(1f), valueRange = 0f..1f)
                        }
                    }
                    Tab.ADJUST -> {
                        val v = tool.read(edit)
                        Text("${tool.label}  ${(v * 100).roundToInt()}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally)
                            .clickable(onClickLabel = "歸零") { edit = tool.write(edit, 0f) })
                        GlassSlider(v, { x -> edit = tool.write(edit, x) }, Modifier.fillMaxWidth().padding(horizontal = 18.dp), valueRange = if (tool.positiveOnly) 0f..1f else -1f..1f)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tool.entries.forEach { t -> SnapPill(t.label + if (t.read(edit) != 0f) " •" else "", t == tool) { tool = t } }
                        }
                    }
                    Tab.MARK -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SnapRender.Template.entries.forEach { t -> SnapPill(t.label, edit.template == t) { edit = edit.copy(template = t) } }
                            Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = .3f)))
                            SnapRender.Position.entries.forEach { p -> SnapPill(p.label, edit.position == p) { edit = edit.copy(position = p) } }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SnapRender.Field.entries.forEach { f -> SnapPill(f.label, f in edit.fields) {
                                edit = edit.copy(fields = if (f in edit.fields) edit.fields - f else edit.fields + f)
                            } }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (SnapRender.Field.NOTE in edit.fields) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            androidx.compose.foundation.text.BasicTextField(edit.note, { edit = edit.copy(note = it.take(24)) }, singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth(), decorationBox = { inner -> if (edit.note.isEmpty()) Text("寫一句話，例如：和家人的週末", color = Color.White.copy(alpha = .45f), fontSize = 16.sp); inner() })
                        } else Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("大小", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                            GlassSlider(edit.markScale, { edit = edit.copy(markScale = it) }, Modifier.weight(1f), valueRange = .6f..1.6f)
                            Spacer(Modifier.width(10.dp))
                            Text("透明", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                            GlassSlider(edit.markOpacity, { edit = edit.copy(markOpacity = it) }, Modifier.weight(1f), valueRange = .3f..1f)
                        }
                    }
                    Tab.CROP -> {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SnapRender.Aspect.entries.forEach { a -> SnapPill(a.label, edit.aspect == a) { edit = edit.copy(aspect = a) } }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SnapPill("向右轉 90°", false) { edit = edit.copy(turns = (edit.turns + 1) % 4) }
                            SnapPill("左右翻轉", edit.mirror) { edit = edit.copy(mirror = !edit.mirror) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEach { t ->
                    val on = t == tab
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = t.label) { tab = t }.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(when (t) { Tab.FILTER -> Icons.Rounded.AutoAwesome; Tab.ADJUST -> Icons.Rounded.Tune; Tab.MARK -> Icons.Rounded.TextFields; Tab.CROP -> Icons.Rounded.Crop },
                            null, Modifier.size(24.dp), tint = if (on) Color(0xFFFFD60A) else Color.White.copy(alpha = .6f))
                        Spacer(Modifier.height(3.dp))
                        Text(t.label, color = if (on) Color.White else Color.White.copy(alpha = .6f), fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                    }
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
