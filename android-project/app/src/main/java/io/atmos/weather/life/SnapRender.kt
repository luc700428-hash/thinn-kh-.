package io.atmos.weather.life

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.ContextCompat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 天空浮水印相機 — the photo pipeline: crop to a frame, a filter and fine adjustments through one
 * colour matrix, a vignette, then the weather caption in one of several templates. Pure Android
 * graphics, so it runs the same for the live preview thumbnails and the full-size save.
 */
object SnapRender {
    enum class Filter(val label: String) { ORIGINAL("原圖"), CLEAR("晴空"), WARM("暖陽"), SUNSET("黃昏"), COOL("藍調"), FILM("底片"), VIVID("鮮豔"), CLOUD("雲海"), MONO("黑白") }
    enum class Aspect(val label: String, val ratio: Float?) { ORIGINAL("原始", null), SQUARE("1:1", 1f), PORTRAIT("4:5", .8f), STORY("9:16", 9f / 16f), WIDE("16:9", 16f / 9f), CLASSIC("3:4", .75f) }
    enum class Template(val label: String) { MAGAZINE("雜誌"), CARD("卡片"), BIG("大溫度"), MINIMAL("極簡"), FILM("底片戳記"), NONE("不加") }
    enum class Position(val label: String) { BOTTOM("下方"), TOP("上方") }
    enum class Field(val label: String) { TEMP("溫度"), CONDITION("天氣"), PLACE("地點"), TIME("時間"), PRESSURE("氣壓"), ALTITUDE("海拔"), HUMIDITY("濕度") }

    data class Edit(
        val filter: Filter = Filter.ORIGINAL,
        val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f, val warmth: Float = 0f, val vignette: Float = 0f,
        val aspect: Aspect = Aspect.ORIGINAL,
        val template: Template = Template.MAGAZINE,
        val position: Position = Position.BOTTOM,
        val fields: Set<Field> = setOf(Field.TEMP, Field.CONDITION, Field.PLACE, Field.TIME, Field.PRESSURE),
    )

    /** The filter's own colour matrix (before the adjustments). */
    fun filterMatrix(f: Filter): ColorMatrix {
        val m = ColorMatrix()
        fun sat(s: Float) = ColorMatrix().apply { setSaturation(s) }
        fun scale(r: Float, g: Float, b: Float) = ColorMatrix().apply { setScale(r, g, b, 1f) }
        fun lift(v: Float) = ColorMatrix(floatArrayOf(1 - v / 255f, 0f, 0f, 0f, v, 0f, 1 - v / 255f, 0f, 0f, v, 0f, 0f, 1 - v / 255f, 0f, v, 0f, 0f, 0f, 1f, 0f))
        when (f) {
            Filter.ORIGINAL -> {}
            Filter.CLEAR -> { m.postConcat(sat(1.18f)); m.postConcat(scale(.97f, 1.0f, 1.07f)) }
            Filter.WARM -> { m.postConcat(sat(1.08f)); m.postConcat(scale(1.08f, 1.02f, .9f)) }
            Filter.SUNSET -> { m.postConcat(sat(1.25f)); m.postConcat(scale(1.14f, .98f, .84f)) }
            Filter.COOL -> { m.postConcat(sat(.95f)); m.postConcat(scale(.92f, 1.0f, 1.12f)) }
            Filter.FILM -> { m.postConcat(sat(.82f)); m.postConcat(scale(1.05f, 1.0f, .93f)); m.postConcat(lift(22f)) }
            Filter.VIVID -> { m.postConcat(sat(1.45f)); m.postConcat(contrast(.12f)) }
            Filter.CLOUD -> { m.postConcat(sat(.9f)); m.postConcat(contrast(-.18f)); m.postConcat(lift(14f)); m.postConcat(scale(.98f, 1f, 1.04f)) }
            Filter.MONO -> { m.postConcat(sat(0f)); m.postConcat(contrast(.15f)) }
        }
        return m
    }

    private fun contrast(c: Float): ColorMatrix {
        val k = 1f + c; val t = 128f * (1f - k)
        return ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    /** Filter, then the person's brightness, contrast, saturation and warmth (each −1…1). */
    fun matrix(e: Edit): ColorMatrix {
        val m = filterMatrix(e.filter)
        if (e.saturation != 0f) m.postConcat(ColorMatrix().apply { setSaturation(1f + e.saturation) })
        if (e.contrast != 0f) m.postConcat(contrast(e.contrast * .5f))
        if (e.warmth != 0f) m.postConcat(ColorMatrix().apply { setScale(1f + .12f * e.warmth, 1f + .02f * e.warmth, 1f - .12f * e.warmth, 1f) })
        if (e.brightness != 0f) {
            val v = 60f * e.brightness
            m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, v, 0f, 1f, 0f, 0f, v, 0f, 0f, 1f, 0f, v, 0f, 0f, 0f, 1f, 0f)))
        }
        return m
    }

    /** The centred crop of a w×h picture for an aspect ratio (width / height). */
    fun cropRect(w: Int, h: Int, ratio: Float?): android.graphics.Rect {
        if (ratio == null || w <= 0 || h <= 0) return android.graphics.Rect(0, 0, w, h)
        val current = w.toFloat() / h
        return if (current > ratio) {
            val nw = (h * ratio).roundToInt().coerceIn(1, w); val x = (w - nw) / 2
            android.graphics.Rect(x, 0, x + nw, h)
        } else {
            val nh = (w / ratio).roundToInt().coerceIn(1, h); val y = (h - nh) / 2
            android.graphics.Rect(0, y, w, y + nh)
        }
    }

    fun render(ctx: Context?, src: Bitmap, e: Edit, facts: LifeCards.SnapFacts, maxSide: Int, zone: ZoneId = ZoneId.of("Asia/Taipei")): Bitmap {
        val crop = cropRect(src.width, src.height, e.aspect.ratio)
        val scale = min(1f, maxSide.toFloat() / max(crop.width(), crop.height()))
        val w = max(1, (crop.width() * scale).roundToInt()); val h = max(1, (crop.height() * scale).roundToInt())
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix(e)) }
        c.drawBitmap(src, crop, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        if (e.vignette > 0f) {
            val r = max(w, h) * .75f
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = RadialGradient(w / 2f, h / 2f, r, intArrayOf(0x00000000, 0x00000000, Color.argb((200 * e.vignette).roundToInt().coerceIn(0, 255), 0, 0, 0)),
                    floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            })
        }
        caption(ctx, c, w, h, e, facts, zone)
        return out
    }

    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val thin: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private fun text(size: Float, color: Int, face: Typeface = Typeface.DEFAULT) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }

    private fun caption(ctx: Context?, c: Canvas, w: Int, h: Int, e: Edit, f: LifeCards.SnapFacts, zone: ZoneId) {
        if (e.template == Template.NONE) return
        val u = min(w, h) / 1080f
        val has = e.fields
        val t = f.time.atZone(zone)
        val temp = if (Field.TEMP in has) f.temperature?.let { "$it°" } else null
        val details = listOfNotNull(f.condition.takeIf { Field.CONDITION in has }, f.pressure?.takeIf { Field.PRESSURE in has }?.let { "$it hPa" },
            f.altitude?.takeIf { Field.ALTITUDE in has }?.let { "海拔 $it m" }, f.humidity?.takeIf { Field.HUMIDITY in has }?.let { "濕度 $it%" })
        val place = f.place.takeIf { Field.PLACE in has }
        val time = if (Field.TIME in has) DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm", Locale.TAIWAN).format(t) else null
        val top = e.position == Position.TOP
        val pad = 64f * u
        when (e.template) {
            Template.MAGAZINE -> {
                val band = h * .42f
                val y0 = if (top) 0f else h - band
                c.drawRect(0f, y0, w.toFloat(), y0 + band, Paint().apply {
                    shader = if (top) LinearGradient(0f, 0f, 0f, band, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
                    else LinearGradient(0f, y0, 0f, h.toFloat(), 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
                })
                val lines = mutableListOf<Pair<String, Paint>>()
                temp?.let { lines += it to text(190f * u, Color.WHITE, bold) }
                place?.let { lines += it.take(18) to text(54f * u, Color.WHITE, bold) }
                if (details.isNotEmpty()) lines += details.joinToString("  ·  ") to text(36f * u, 0xDDFFFFFF.toInt())
                time?.let { lines += it to text(32f * u, 0xBBFFFFFF.toInt()) }
                drawStack(c, lines, pad, top, h, pad, u)
                val brand = text(30f * u, 0xBBFFFFFF.toInt(), bold).apply { textAlign = Paint.Align.RIGHT }
                c.drawText("thinn-khì", w - pad, if (top) pad + 30f * u else h - pad + 4f * u, brand)
            }
            Template.CARD -> {
                val cw = min(w - pad * 2, 760f * u); val ch = 236f * u
                val left = pad; val y = if (top) pad else h - pad - ch
                val r = RectF(left, y, left + cw, y + ch)
                c.drawRoundRect(r, 44f * u, 44f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73000000 })
                c.drawRoundRect(r, 44f * u, 44f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF; style = Paint.Style.STROKE; strokeWidth = 2.5f * u })
                var x = r.left + 36f * u
                if (ctx != null) f.buddy?.let { id -> ContextCompat.getDrawable(ctx, id)?.let { d -> d.setBounds(x.toInt(), (y + 34f * u).toInt(), (x + 168f * u).toInt(), (y + 202f * u).toInt()); d.draw(c); x += 190f * u } }
                temp?.let { c.drawText(it, x, y + 128f * u, text(110f * u, Color.WHITE, bold)) }
                val tx = x + (temp?.let { text(110f * u, Color.WHITE, bold).measureText(it) + 26f * u } ?: 0f)
                place?.let { c.drawText(it.take(12), tx, y + 84f * u, text(40f * u, Color.WHITE, bold)) }
                if (details.isNotEmpty()) c.drawText(details.take(2).joinToString(" · "), tx, y + 136f * u, text(32f * u, 0xDDFFFFFF.toInt()))
                time?.let { c.drawText(it, r.left + 36f * u, y + ch - 30f * u, text(28f * u, 0xBBFFFFFF.toInt())) }
            }
            Template.BIG -> {
                val big = text(min(w, h) * .32f, Color.WHITE, thin).apply { textAlign = Paint.Align.CENTER; setShadowLayer(24f * u, 0f, 4f * u, 0x66000000) }
                val small = text(44f * u, Color.WHITE, bold).apply { textAlign = Paint.Align.CENTER; setShadowLayer(12f * u, 0f, 2f * u, 0x66000000) }
                val y = if (top) pad + big.textSize * .9f else h - pad - small.textSize * 2.4f
                temp?.let { c.drawText(it, w / 2f, y, big) }
                val under = listOfNotNull(place?.take(14), details.firstOrNull()).joinToString("  ·  ")
                if (under.isNotEmpty()) c.drawText(under, w / 2f, y + small.textSize * 1.4f, small)
                time?.let { c.drawText(it, w / 2f, y + small.textSize * 2.5f, small.apply { textSize = 32f * u; typeface = Typeface.DEFAULT }) }
            }
            Template.MINIMAL -> {
                val bar = if (top) RectF(pad, pad, w - pad, pad + 140f * u) else RectF(pad, h - 196f * u, w - pad, h - pad)
                c.drawRoundRect(bar, 36f * u, 36f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 })
                temp?.let { c.drawText(it, bar.left + 36f * u, bar.bottom - 40f * u, text(84f * u, Color.WHITE, bold)) }
                val tx = bar.left + if (temp != null) 220f * u else 36f * u
                place?.let { c.drawText(it.take(16), tx, bar.top + 62f * u, text(38f * u, Color.WHITE, bold)) }
                val rest = (details.take(2) + listOfNotNull(time?.substring(5))).joinToString(" · ")
                if (rest.isNotEmpty()) c.drawText(rest, tx, bar.top + 110f * u, text(30f * u, 0xDDFFFFFF.toInt()))
            }
            Template.FILM -> {
                val stamp = text(46f * u, 0xFFFFB13B.toInt(), Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)).apply {
                    setShadowLayer(5f * u, 1.5f * u, 1.5f * u, 0xCC3A1200.toInt()); textAlign = Paint.Align.RIGHT
                }
                val right = w - 60f * u
                val y = if (top) 110f * u else h - 70f * u
                if (Field.TIME in has) c.drawText(DateTimeFormatter.ofPattern("''yy  M  d", Locale.US).format(t), right, y, stamp)
                val small = text(34f * u, 0xFFFFC46B.toInt(), Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)).apply {
                    textAlign = Paint.Align.RIGHT; setShadowLayer(4f * u, 1f * u, 1f * u, 0xCC3A1200.toInt())
                }
                val line = listOfNotNull(temp, f.pressure?.takeIf { Field.PRESSURE in has }?.let { "${it}hPa" }, place?.take(10)).joinToString("  ")
                if (line.isNotEmpty()) c.drawText(line, right, if (top) y + 56f * u else y - 60f * u, small)
            }
            Template.NONE -> {}
        }
    }

    /** Lines stacked from the bottom (or top) edge, biggest first when at the top. */
    private fun drawStack(c: Canvas, lines: List<Pair<String, Paint>>, x: Float, top: Boolean, h: Int, pad: Float, u: Float) {
        if (top) {
            var y = pad
            lines.forEach { (s, p) -> y += p.textSize * .92f; c.drawText(s, x, y, p); y += p.textSize * .22f }
        } else {
            var y = h - pad
            lines.asReversed().forEach { (s, p) -> c.drawText(s, x, y, p); y -= p.textSize * 1.12f }
        }
    }

    /** Opens a photo at most [maxSide] px on its long side, upright (EXIF rotation applied). */
    fun decode(ctx: Context, uri: Uri, maxSide: Int = 3200, mirror: Boolean = false): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val deg = ctx.contentResolver.openInputStream(uri)?.use { s ->
            when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        } ?: 0f
        if (deg == 0f && !mirror) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg); if (mirror) postScale(-1f, 1f) }, true)
    }.getOrNull()
}
