package io.atmos.weather.life

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.atmos.weather.ui.AppText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One saved recording in Recordings/thinn-khi. */
internal data class Recording(val uri: Uri, val name: String, val durationMs: Long, val addedSec: Long)

/**
 * 錄音: a voice and sound recorder. AAC in an .m4a file, saved to the phone's Recordings/thinn-khi
 * folder (where the system's file and music apps find it). Live level meter, pause and resume,
 * and the latest recordings to play back or share.
 */
internal class SoundRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var file: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    var paused = false; private set
    val active get() = recorder != null

    /** Starts recording; false when the microphone is busy or the file cannot be made. The panel asks for the microphone first. */
    @android.annotation.SuppressLint("MissingPermission")
    fun start(high: Boolean): Boolean {
        if (recorder != null) return true
        val name = "thinn-khi-錄音-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US).format(LocalDateTime.now()) + ".m4a"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name); put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Recordings/thinn-khi"); put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val target = runCatching { ctx.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) }.getOrNull() ?: return false
        val fd = runCatching { ctx.contentResolver.openFileDescriptor(target, "w") }.getOrNull()
        if (fd == null) { runCatching { ctx.contentResolver.delete(target, null, null) }; return false }
        // High quality asks for stereo; phones with one microphone refuse it, so fall back to mono.
        for (channels in if (high) listOf(2, 1) else listOf(1)) {
            val r = MediaRecorder(ctx)
            val ok = runCatching {
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(if (high) 48_000 else 44_100)
                r.setAudioEncodingBitRate(if (high) 256_000 else 128_000)
                r.setAudioChannels(channels)
                r.setOutputFile(fd.fileDescriptor)
                r.prepare(); r.start()
            }.isSuccess
            if (ok) { recorder = r; file = fd; uri = target; paused = false; return true }
            runCatching { r.release() }
        }
        runCatching { fd.close() }; runCatching { ctx.contentResolver.delete(target, null, null) }
        return false
    }

    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)
    fun pause() { runCatching { recorder?.pause(); paused = true } }
    fun resume() { runCatching { recorder?.resume(); paused = false } }

    /** Stops and keeps the file; returns where it is (null when nothing was kept). */
    fun stop(): Uri? {
        val r = recorder ?: return null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }; recorder = null
        runCatching { file?.close() }; file = null
        val u = uri; uri = null
        if (u == null) return null
        return if (ok) { runCatching { ctx.contentResolver.update(u, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) }; u }
        else { runCatching { ctx.contentResolver.delete(u, null, null) }; null }
    }

    companion object {
        /** The latest recordings this app made, newest first. */
        fun list(ctx: Context, limit: Int = 12): List<Recording> = runCatching {
            val out = mutableListOf<Recording>()
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ctx.contentResolver.query(collection,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED),
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("Recordings/thinn-khi%"), "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out += Recording(ContentUris.withAppendedId(collection, c.getLong(0)), c.getString(1) ?: "錄音", c.getLong(2), c.getLong(3))
                }
            }
            out
        }.getOrDefault(emptyList())

        fun duration(ms: Long): String { val s = ms / 1000; return String.format(Locale.US, "%d:%02d", s / 60, s % 60) }
    }
}

@Composable
internal fun AudioRecorderPanel(high: Boolean, onHigh: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val rec = remember { SoundRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }
    var list by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<Uri?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    LaunchedEffect(refresh) { list = withContext(Dispatchers.IO) { SoundRecorder.list(ctx) } }
    // The level meter and clock, 20 times a second while recording.
    LaunchedEffect(recording, paused) {
        if (!recording || paused) return@LaunchedEffect
        var last = System.nanoTime()
        while (isActive) {
            delay(50)
            val now = System.nanoTime(); elapsed += (now - last) / 1_000_000; last = now
            levels.add(rec.level()); if (levels.size > 90) levels.removeAt(0)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            rec.stop()
            runCatching { player[0]?.release() }; player[0] = null
        }
    }
    fun stopPlayback() { runCatching { player[0]?.release() }; player[0] = null; playing = null }
    fun play(r: Recording) {
        if (playing == r.uri) { stopPlayback(); return }
        stopPlayback()
        player[0] = runCatching { MediaPlayer().apply { setDataSource(ctx, r.uri); setOnCompletionListener { stopPlayback() }; prepare(); start() } }.getOrNull()
        playing = if (player[0] != null) r.uri else null
        if (player[0] == null) status = "無法播放這段錄音"
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The meter: bars of the last few seconds, red while recording.
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp)) {
                val n = 90; val gap = size.width / n
                for (i in 0 until n) {
                    val v = levels.getOrNull(levels.size - n + i) ?: 0f
                    val hgt = (size.height * (.04f + .96f * kotlin.math.sqrt(v))).coerceAtMost(size.height)
                    drawRoundRect(if (recording && !paused) Color(0xFFFF453A) else Color.White.copy(alpha = .35f), Offset(i * gap, (size.height - hgt) / 2),
                        Size(gap * .55f, hgt), CornerRadius(gap))
                }
            }
        }
        Text(SoundRecorder.duration(elapsed), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
        Text(when { !granted -> "需要麥克風權限才能錄音"; recording && paused -> "已暫停"; recording -> "錄音中…"; else -> if (high) "高音質 · 48 kHz 256 kbps" else "標準 · 44.1 kHz 128 kbps" },
            color = Color.White.copy(alpha = .7f), fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            // Quality (before recording) or pause/resume (while recording).
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(role = Role.Button, onClickLabel = if (recording) "暫停" else "音質") {
                if (recording) { if (paused) rec.resume() else rec.pause(); paused = rec.paused } else onHigh(!high)
            }, contentAlignment = Alignment.Center) {
                if (recording) Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, tint = Color.White)
                else Text(if (high) "HQ" else "標準", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(84.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(if (recording) RoundedCornerShape(14.dp) else CircleShape)
                .background(Color(0xFFFF3B30)).clickable(role = Role.Button, onClickLabel = if (recording) "停止錄音" else "開始錄音") {
                    if (!granted) { ask.launch(Manifest.permission.RECORD_AUDIO); return@clickable }
                    if (recording) {
                        val u = rec.stop(); recording = false; paused = false
                        status = if (u != null) "已存到 錄音/thinn-khi" else "錄音太短，沒有存檔"; refresh++
                    } else {
                        stopPlayback(); levels.clear(); elapsed = 0
                        recording = rec.start(high)
                        if (!recording) status = "麥克風正在被使用，請稍後再試"
                    }
                })
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(role = Role.Button, onClickLabel = "重新整理") { refresh++ },
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Refresh, null, tint = Color.White) }
        }
        if (status.isNotEmpty()) Text(status, color = Color.White, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 6.dp))
        if (list.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .07f)).padding(vertical = 4.dp)) {
            list.take(5).forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(.5.dp).background(Color.White.copy(alpha = .12f)))
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "播放") { play(r) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (playing == r.uri) Icons.Rounded.StopCircle else Icons.Rounded.PlayCircle, null, Modifier.size(32.dp), tint = Color(0xFFFF453A))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name.removePrefix("thinn-khi-").removeSuffix(".m4a"), color = Color.White, fontSize = 15.sp, maxLines = 1)
                        Text(SoundRecorder.duration(r.durationMs), color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
                    }
                    Icon(Icons.Rounded.IosShare, "分享", Modifier.size(22.dp).clickable {
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, r.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(Intent.createChooser(send, "分享錄音").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }, tint = Color.White.copy(alpha = .85f))
                }
            }
        }
    }
}
