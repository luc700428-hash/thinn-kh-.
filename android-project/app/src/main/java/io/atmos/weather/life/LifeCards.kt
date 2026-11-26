package io.atmos.weather.life

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.presentation.home.WeatherShareCard
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Bitmaps people send: the family care card and the sky watermark photo. */
object LifeCards {
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private fun text(size: Float, color: Int, b: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (b) bold else Typeface.DEFAULT
    }

    /** 天氣關心小卡: a warm square card with their place, the weather and a caring line. */
    fun careCard(ctx: Context, person: FamilyCare.Person, w: WeatherSnapshot, message: String): Bitmap {
        val size = 1080
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val day = w.isDay
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), if (day) 0xFFFFE9D6.toInt() else 0xFF2B2440.toInt(),
                if (day) 0xFFFFC9B5.toInt() else 0xFF14112A.toInt(), Shader.TileMode.CLAMP)
        })
        val ink = if (day) 0xFF3B2A24.toInt() else Color.WHITE
        val soft = if (day) 0xB33B2A24.toInt() else 0xB3FFFFFF.toInt()
        val pad = 90f
        c.drawText("給${person.nickname.ifBlank { person.relation }}的天氣關心", pad, 170f, text(54f, ink, true))
        c.drawText(person.place.take(16), pad, 236f, text(40f, soft))
        ContextCompat.getDrawable(ctx, WeatherShareCard.buddy(w.condition, day))?.let { d -> d.setBounds(size - 400, 150, size - 70, 480); d.draw(c) }
        c.drawText("${w.temperatureC.roundToInt()}°", pad - 8f, 470f, text(210f, ink, true))
        val facts = listOfNotNull(w.maxTempC?.let { "高 ${it.roundToInt()}°" }, w.minTempC?.let { "低 ${it.roundToInt()}°" },
            w.nextHourRainProbabilityPct?.let { "降雨 $it%" }).joinToString("  ·  ")
        c.drawText(facts, pad, 548f, text(42f, soft))
        val box = RectF(pad - 20f, 610f, size - pad + 20f, 930f)
        c.drawRoundRect(box, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (day) 0x66FFFFFF else 0x22FFFFFF })
        val line = text(48f, ink)
        var y = 690f
        WeatherShareCard.wrap(message, line, box.width() - 80f).take(4).forEach { c.drawText(it, box.left + 40f, y, line); y += 66f }
        c.drawText("thinn-khì 安心天氣", pad, size - 70f, text(34f, soft, true))
        return bmp
    }

    /** Opens LINE with the card when it is installed, otherwise the share sheet. */
    fun sendCare(ctx: Context, bmp: Bitmap, caption: String): Boolean = runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "thinn-khi-care-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.update.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, caption)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val line = Intent(send).setPackage("jp.naver.line.android")
        val target = if (line.resolveActivity(ctx.packageManager) != null) line else Intent.createChooser(send, "傳送天氣關心")
        ctx.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        true
    }.getOrDefault(false)

    enum class SnapStyle(val label: String) { MAGAZINE("雜誌風"), FILM("底片風"), MINIMAL("極簡") }
    data class SnapFacts(val place: String, val time: Instant, val temperature: Int?, val condition: String?, val pressure: Int?, val altitude: Int?, val humidity: Int?)

    /** 天空浮水印: the photo with a quiet weather caption laid over it. */
    fun watermark(photo: Bitmap, facts: SnapFacts, style: SnapStyle, zone: ZoneId = ZoneId.of("Asia/Taipei")): Bitmap {
        val maxSide = 2400f
        val scale = minOf(1f, maxSide / maxOf(photo.width, photo.height))
        val w = (photo.width * scale).roundToInt(); val h = (photo.height * scale).roundToInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(Bitmap.createScaledBitmap(photo, w, h, true), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val u = minOf(w, h) / 1080f
        val t = facts.time.atZone(zone)
        val temp = facts.temperature?.let { "$it°" } ?: "--°"
        val details = listOfNotNull(facts.condition, facts.pressure?.let { "$it hPa" }, facts.altitude?.let { "海拔 $it m" }, facts.humidity?.let { "濕度 $it%" })
        when (style) {
            SnapStyle.MAGAZINE -> {
                c.drawRect(0f, h * .62f, w.toFloat(), h.toFloat(), Paint().apply {
                    shader = LinearGradient(0f, h * .62f, 0f, h.toFloat(), 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
                })
                val pad = 64f * u
                c.drawText(temp, pad, h - 250f * u, text(200f * u, Color.WHITE, true))
                c.drawText(facts.place.take(18), pad, h - 160f * u, text(54f * u, Color.WHITE, true))
                c.drawText(details.joinToString("  ·  "), pad, h - 100f * u, text(36f * u, 0xDDFFFFFF.toInt()))
                c.drawText(DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm", Locale.TAIWAN).format(t), pad, h - 50f * u, text(32f * u, 0xBBFFFFFF.toInt()))
                val brand = text(30f * u, 0xBBFFFFFF.toInt(), true).apply { textAlign = Paint.Align.RIGHT }
                c.drawText("thinn-khì", w - pad, h - 50f * u, brand)
            }
            SnapStyle.FILM -> {
                // A date stamp in the corner, the way film cameras printed it.
                val stamp = text(46f * u, 0xFFFFB13B.toInt(), true).apply {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setShadowLayer(5f * u, 1.5f * u, 1.5f * u, 0xCC3A1200.toInt())
                }
                val right = w - 60f * u
                stamp.textAlign = Paint.Align.RIGHT
                c.drawText(DateTimeFormatter.ofPattern("''yy  M  d", Locale.US).format(t), right, h - 70f * u, stamp)
                val small = text(34f * u, 0xFFFFC46B.toInt(), true).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT; setShadowLayer(4f * u, 1f * u, 1f * u, 0xCC3A1200.toInt()) }
                c.drawText("$temp  ${facts.pressure?.let { "${it}hPa" } ?: ""}  ${facts.place.take(10)}", right, h - 130f * u, small)
            }
            SnapStyle.MINIMAL -> {
                val pad = 56f * u
                val bar = RectF(pad, h - 190f * u, w - pad, h - pad)
                c.drawRoundRect(bar, 36f * u, 36f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 })
                c.drawText(temp, bar.left + 36f * u, bar.bottom - 44f * u, text(84f * u, Color.WHITE, true))
                c.drawText(facts.place.take(16), bar.left + 220f * u, bar.top + 66f * u, text(38f * u, Color.WHITE, true))
                c.drawText((details.take(2) + DateTimeFormatter.ofPattern("M/d HH:mm").format(t)).joinToString(" · "),
                    bar.left + 220f * u, bar.top + 116f * u, text(30f * u, 0xDDFFFFFF.toInt()))
            }
        }
        return out
    }

    /** Saves into Pictures/thinn-khi and returns the content uri. */
    fun saveToGallery(ctx: Context, bmp: Bitmap): android.net.Uri? = runCatching {
        val name = "thinn-khi-sky-${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/thinn-khi"); put(MediaStore.Images.Media.IS_PENDING, 1) }
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri
    }.getOrNull()

    fun shareImage(ctx: Context, uri: android.net.Uri, caption: String) = runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, caption)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "分享天空照").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
