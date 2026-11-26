package io.atmos.weather.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * The reading fonts. Chinese fonts are large (5–28 MB), so only the system font ships with the
 * app; the others are open-licence (SIL OFL) fonts from the Google Fonts collection, downloaded
 * once when chosen and kept on the phone. Until a download finishes the system font is used.
 */
object FontLibrary {
    /** [index] is the saved choice (0–3 keep their old meaning: system, serif, monospace, handwriting). */
    data class Choice(val index: Int, val label: String, val note: String, val file: String?, val megabytes: Int, val system: FontFamily? = null)

    private const val BASE = "https://raw.githubusercontent.com/google/fonts/main/ofl/"
    val choices = listOf(
        Choice(0, "系統字體", "手機內建，最省空間", null, 0, FontFamily.SansSerif),
        Choice(4, "思源黑體", "端正清楚的黑體", "notosanstc/NotoSansTC%5Bwght%5D.ttf", 12),
        Choice(1, "思源宋體", "書本般的明體", "notoseriftc/NotoSerifTC%5Bwght%5D.ttf", 17),
        Choice(3, "霞鶩文楷", "溫潤的手寫楷體", "lxgwwenkaitc/LXGWWenKaiTC-Regular.ttf", 13),
        Choice(5, "粉圓", "圓潤可愛", "huninn/Huninn-Regular.ttf", 5),
        Choice(6, "芫荽", "清秀的手寫感", "iansui/Iansui-Regular.ttf", 10),
        Choice(7, "仙人掌明體", "古典明體", "cactusclassicalserif/CactusClassicalSerif-Regular.ttf", 28),
        Choice(2, "等寬", "數字對齊的等寬字", null, 0, FontFamily.Monospace),
    )
    const val MAX_INDEX = 7

    @Volatile private var dir: File? = null
    private val families = ConcurrentHashMap<Int, FontFamily>()
    /** Bumped when a font finishes downloading, so screens pick it up. */
    val version = mutableIntStateOf(0)

    fun init(ctx: Context) { if (dir == null) dir = File(ctx.applicationContext.filesDir, "fonts") }
    fun choice(index: Int) = choices.firstOrNull { it.index == index } ?: choices.first()
    private fun fileOf(c: Choice): File? = c.file?.let { name -> dir?.let { File(it, name.substringAfterLast('/').replace("%5B", "[").replace("%5D", "]")) } }
    fun downloaded(index: Int): Boolean = choice(index).let { c -> c.file == null || fileOf(c)?.let { it.isFile && it.length() > 100_000 } == true }

    /** The Compose family for [index]; the system font while a chosen font is not on the phone yet. */
    fun family(index: Int): FontFamily {
        val c = choice(index)
        c.system?.let { return it }
        families[index]?.let { return it }
        val f = fileOf(c)?.takeIf { it.isFile && it.length() > 100_000 } ?: return if (index == 1 || index == 7) FontFamily.Serif else FontFamily.SansSerif
        return runCatching { FontFamily(Font(f)) }.getOrNull()?.also { families[index] = it } ?: FontFamily.SansSerif
    }

    /** The Android typeface (for the big temperature drawn by hand). */
    fun typeface(index: Int): Typeface {
        val c = choice(index)
        fileOf(c)?.takeIf { it.isFile && it.length() > 100_000 }?.let { f -> runCatching { Typeface.createFromFile(f) }.getOrNull()?.let { return it } }
        return when (index) { 1, 7 -> Typeface.SERIF; 2 -> Typeface.MONOSPACE; else -> Typeface.SANS_SERIF }
    }

    /** Downloads [index] (once), reporting 0–100. True when the font is ready. */
    suspend fun download(ctx: Context, index: Int, progress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        init(ctx)
        val c = choice(index)
        val name = c.file ?: return@withContext true
        val target = fileOf(c) ?: return@withContext false
        if (downloaded(index)) return@withContext true
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val ok = runCatching {
            val conn = (URL(BASE + name).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true }
            try {
                if (conn.responseCode != 200) return@runCatching false
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: (c.megabytes * 1_000_000L)
                var got = 0L; var last = -1
                conn.inputStream.use { input -> part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); got += n
                        val pct = (got * 100 / total).toInt().coerceIn(0, 99)
                        if (pct != last) { last = pct; withContext(Dispatchers.Main) { progress(pct) } }
                    }
                } }
                // A real font file opens as a typeface; anything else is thrown away.
                Typeface.createFromFile(part) != null && part.length() > 100_000 && part.renameTo(target)
            } finally { conn.disconnect() }
        }.getOrDefault(false)
        if (!ok) part.delete()
        if (ok) { families.remove(index); withContext(Dispatchers.Main) { progress(100); version.intValue++ } }
        ok
    }
}

/**
 * The font list in 外觀: each font shows a sample line in itself (once on the phone), its size
 * to download, a progress bar while downloading, and a check on the one in use.
 */
@Composable
fun FontPicker(current: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = MaterialTheme.colorScheme
    FontLibrary.init(ctx)
    val version = FontLibrary.version.intValue
    var loading by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var failed by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.onSurface.copy(alpha = .05f))) {
        FontLibrary.choices.forEachIndexed { i, c ->
            val on = c.index == current
            val ready = remember(version, c.index) { FontLibrary.downloaded(c.index) }
            val progress = loading?.takeIf { it.first == c.index }?.second
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(.5.dp).background(p.onSurface.copy(alpha = .1f)))
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = c.label) {
                    if (loading != null) return@clickable
                    if (ready) { onPick(c.index); return@clickable }
                    failed = null; loading = c.index to 0
                    scope.launch {
                        val ok = FontLibrary.download(ctx, c.index) { pct -> loading = c.index to pct }
                        loading = null
                        if (ok) onPick(c.index) else failed = c.index
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val family = if (ready) FontLibrary.family(c.index) else null
                    AppText(c.label, color = p.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = family)
                    AppText(if (ready) "今天天氣晴朗，氣溫 25°，適合出門走走" else "${c.note} · 約 ${c.megabytes} MB",
                        color = p.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, fontFamily = family)
                    if (progress != null) {
                        Spacer(Modifier.height(6.dp))
                        IOSLinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    if (failed == c.index) AppText("下載失敗，請確認網路後再試", color = p.error, fontSize = 14.sp)
                }
                Spacer(Modifier.width(10.dp))
                when {
                    progress != null -> AppText("$progress%", color = p.primary, fontSize = 14.sp)
                    on -> Icon(Icons.Rounded.CheckCircle, "使用中", tint = p.primary)
                    !ready -> Icon(Icons.Rounded.CloudDownload, "下載", tint = p.onSurfaceVariant)
                }
            }
        }
    }
}
