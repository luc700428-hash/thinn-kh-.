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
    enum class Template(val label: String, val glyph: String) {
        MAGAZINE("雜誌", "雜"), GLASS("玻璃卡", "玻"), BIG("大溫度", "°"), POLAROID("拍立得", "拍"), FILM("底片", "底"), MINIMAL("極簡", "簡"), NONE("不加", "無")
    }
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
        val photo = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(photo)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix(e)) }
        c.drawBitmap(src, crop, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        if (e.vignette > 0f) {
            val r = max(w, h) * .75f
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = RadialGradient(w / 2f, h / 2f, r, intArrayOf(0x00000000, 0x00000000, Color.argb((200 * e.vignette).roundToInt().coerceIn(0, 255), 0, 0, 0)),
                    floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            })
        }
        if (e.template == Template.POLAROID) return polaroid(ctx, photo, e, facts, zone)
        caption(ctx, c, photo, w, h, e, facts, zone)
        return photo
    }

    /**
     * The caption alone on a transparent picture of the viewfinder's size, laid over the live
     * camera so you see the watermark before you take the photo.
     */
    fun overlay(ctx: Context?, w: Int, h: Int, e: Edit, facts: LifeCards.SnapFacts, zone: ZoneId = ZoneId.of("Asia/Taipei")): Bitmap {
        val out = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (e.template == Template.POLAROID) {
            val b = min(w, h) * .045f; val bottom = min(w, h) * .2f
            val frame = Paint().apply { color = 0xE6F7F5F0.toInt() }
            c.drawRect(0f, 0f, w.toFloat(), b, frame); c.drawRect(0f, 0f, b, h.toFloat(), frame); c.drawRect(w - b, 0f, w.toFloat(), h.toFloat(), frame)
            c.drawRect(0f, h - bottom, w.toFloat(), h.toFloat(), frame)
            polaroidText(c, b, h - bottom, w - b, h.toFloat(), min(w, h) / 1080f, e, facts, zone)
        } else caption(ctx, c, null, w, h, e, facts, zone)
        return out
    }

    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val thin: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private fun text(size: Float, color: Int, face: Typeface = Typeface.DEFAULT, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = face; letterSpacing = spacing
    }
    private fun Paint.shadow(u: Float, a: Int = 0x66) = apply { setShadowLayer(14f * u, 0f, 3f * u, Color.argb(a, 0, 0, 0)) }

    private class Facts(val temp: String?, val condition: String?, val place: String?, val details: List<String>, val date: String?, val clock: String?)
    private fun facts(e: Edit, f: LifeCards.SnapFacts, zone: ZoneId): Facts {
        val has = e.fields; val t = f.time.atZone(zone)
        return Facts(
            if (Field.TEMP in has) f.temperature?.let { "$it°" } else null,
            f.condition.takeIf { Field.CONDITION in has },
            f.place.takeIf { Field.PLACE in has },
            listOfNotNull(f.pressure?.takeIf { Field.PRESSURE in has }?.let { "$it hPa" }, f.humidity?.takeIf { Field.HUMIDITY in has }?.let { "濕度 $it%" },
                f.altitude?.takeIf { Field.ALTITUDE in has }?.let { "海拔 $it m" }),
            if (Field.TIME in has) DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.TAIWAN).format(t) else null,
            if (Field.TIME in has) DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN).format(t) else null)
    }

    /** The weather caption in the chosen design. [photo] (when given) lets 玻璃卡 frost what is behind it. */
    private fun caption(ctx: Context?, c: Canvas, photo: Bitmap?, w: Int, h: Int, e: Edit, f: LifeCards.SnapFacts, zone: ZoneId) {
        if (e.template == Template.NONE || e.template == Template.POLAROID) return
        val u = min(w, h) / 1080f
        val x = facts(e, f, zone)
        val top = e.position == Position.TOP
        val pad = 70f * u
        when (e.template) {
            Template.MAGAZINE -> {
                // A cover line: small brand caps, a huge thin temperature, the sky in words, a hairline, then where and when.
                val band = h * .5f
                val y0 = if (top) 0f else h - band
                c.drawRect(0f, y0, w.toFloat(), y0 + band, Paint().apply {
                    shader = if (top) LinearGradient(0f, 0f, 0f, band, 0xA6000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
                    else LinearGradient(0f, y0, 0f, h.toFloat(), 0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP)
                })
                val kicker = text(26f * u, 0xCCFFFFFF.toInt(), medium, .32f)
                val big = text(250f * u, Color.WHITE, thin, -.02f)
                val sky = text(56f * u, Color.WHITE, medium)
                val where = text(40f * u, Color.WHITE, bold, .04f)
                val small = text(31f * u, 0xD9FFFFFF.toInt())
                val lines = mutableListOf<Pair<String, Paint>>()
                lines += "THINN-KHÌ  WEATHER" to kicker
                x.temp?.let { lines += it to big }
                x.condition?.let { lines += it to sky }
                val tail = mutableListOf<Pair<String, Paint>>()
                x.place?.let { tail += it.take(18) to where }
                if (x.details.isNotEmpty()) tail += x.details.joinToString("   ") to small
                if (top) {
                    var y = pad
                    (lines + tail).forEachIndexed { i, (s, p) ->
                        y += p.textSize * .95f; c.drawText(s, pad, y, p); y += p.textSize * .22f
                        if (i == lines.size - 1 && tail.isNotEmpty()) { c.drawRect(pad, y + 8f * u, pad + 120f * u, y + 11f * u, Paint().apply { color = 0xCCFFFFFF.toInt() }); y += 26f * u }
                    }
                } else {
                    var y = h - pad
                    tail.asReversed().forEach { (s, p) -> c.drawText(s, pad, y, p); y -= p.textSize * 1.3f }
                    if (tail.isNotEmpty()) { c.drawRect(pad, y + 4f * u, pad + 120f * u, y + 7f * u, Paint().apply { color = 0xCCFFFFFF.toInt() }); y -= 30f * u }
                    lines.asReversed().forEach { (s, p) -> c.drawText(s, pad - (if (p === big) 10f * u else 0f), y, p); y -= p.textSize * (if (p === big) .98f else 1.25f) }
                }
                val stamp = listOfNotNull(x.date, x.clock).joinToString("  ")
                if (stamp.isNotEmpty()) c.drawText(stamp, w - pad, if (top) h - pad else pad + 30f * u, text(30f * u, 0xE6FFFFFF.toInt(), medium, .08f).apply { textAlign = Paint.Align.RIGHT }.shadow(u))
            }
            Template.GLASS -> {
                // A frosted card: the photo behind it blurred (shrunk and stretched), lightened, with a bright rim.
                val cw = min(w - pad * 2, 860f * u); val ch = 270f * u
                val r = RectF(pad, if (top) pad else h - pad - ch, pad + cw, (if (top) pad else h - pad - ch) + ch)
                val radius = 54f * u
                val clip = android.graphics.Path().apply { addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW) }
                c.save(); c.clipPath(clip)
                if (photo != null) {
                    val src = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                    val tiny = Bitmap.createScaledBitmap(Bitmap.createBitmap(photo, src.left.coerceAtLeast(0), src.top.coerceAtLeast(0),
                        src.width().coerceAtMost(photo.width - src.left.coerceAtLeast(0)), src.height().coerceAtMost(photo.height - src.top.coerceAtLeast(0))),
                        max(1, (src.width() / 24f).roundToInt()), max(1, (src.height() / 24f).roundToInt()), true)
                    c.drawBitmap(tiny, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
                    c.drawRect(r, Paint().apply { color = 0x33FFFFFF })
                } else c.drawRect(r, Paint().apply { color = 0x59000000 })
                c.drawRect(r.left, r.top, r.right, r.top + ch * .5f, Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.top + ch * .5f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) })
                c.restore()
                c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * u
                    shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xB3FFFFFF.toInt(), 0x33FFFFFF, Shader.TileMode.CLAMP) })
                var tx = r.left + 40f * u
                if (ctx != null) f.buddy?.let { id -> ContextCompat.getDrawable(ctx, id)?.let { d ->
                    d.setBounds(tx.toInt(), (r.top + 50f * u).toInt(), (tx + 170f * u).toInt(), (r.top + 220f * u).toInt()); d.draw(c); tx += 196f * u } }
                val big = text(124f * u, Color.WHITE, bold).shadow(u, 0x40)
                x.temp?.let { c.drawText(it, tx, r.top + 152f * u, big); tx += big.measureText(it) + 30f * u }
                x.condition?.let { c.drawText(it, tx, r.top + 104f * u, text(44f * u, Color.WHITE, bold).shadow(u, 0x40)) }
                x.place?.let { c.drawText(it.take(12), tx, r.top + 156f * u, text(34f * u, 0xF2FFFFFF.toInt(), medium).shadow(u, 0x40)) }
                val foot = (listOfNotNull(x.date, x.clock) + x.details.take(1)).joinToString("  ·  ")
                if (foot.isNotEmpty()) c.drawText(foot, r.left + 40f * u, r.bottom - 34f * u, text(28f * u, 0xE6FFFFFF.toInt(), medium, .04f).shadow(u, 0x40))
            }
            Template.BIG -> {
                val big = text(min(w, h) * .36f, Color.WHITE, thin, -.03f).apply { textAlign = Paint.Align.CENTER }.shadow(u * 2, 0x59)
                val line = text(46f * u, Color.WHITE, medium, .06f).apply { textAlign = Paint.Align.CENTER }.shadow(u)
                val small = text(30f * u, 0xE6FFFFFF.toInt(), medium, .2f).apply { textAlign = Paint.Align.CENTER }.shadow(u)
                val y = if (top) pad + big.textSize * .82f else h - pad - line.textSize * 2.6f
                x.temp?.let { c.drawText(it, w / 2f + big.textSize * .08f, y, big) }
                val under = listOfNotNull(x.condition, x.place?.take(14)).joinToString("   ")
                if (under.isNotEmpty()) c.drawText(under, w / 2f, y + line.textSize * 1.45f, line)
                val stamp = listOfNotNull(x.date, x.clock).joinToString("  ")
                if (stamp.isNotEmpty()) c.drawText(stamp, w / 2f, y + line.textSize * 2.6f, small)
            }
            Template.MINIMAL -> {
                // One quiet line and a hairline, centred: the photo does the talking.
                val p = text(32f * u, Color.WHITE, medium, .12f).apply { textAlign = Paint.Align.CENTER }.shadow(u, 0x80)
                val s = listOfNotNull(x.temp, x.condition, x.place?.take(12), listOfNotNull(x.date, x.clock).joinToString(" ").ifBlank { null }).joinToString("   ·   ")
                val y = if (top) pad + 30f * u else h - pad
                c.drawRect(w / 2f - 40f * u, y - p.textSize * 1.55f, w / 2f + 40f * u, y - p.textSize * 1.55f + 3f * u, Paint().apply { color = 0xCCFFFFFF.toInt() })
                if (s.isNotEmpty()) c.drawText(s, w / 2f, y, p)
            }
            Template.FILM -> {
                val face = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                val stamp = text(48f * u, 0xFFFF9F2E.toInt(), face).apply { setShadowLayer(8f * u, 0f, 0f, 0xCCFF5A00.toInt()); textAlign = Paint.Align.RIGHT }
                val right = w - 64f * u
                val y = if (top) 116f * u else h - 72f * u
                val t = f.time.atZone(zone)
                if (Field.TIME in e.fields) c.drawText(DateTimeFormatter.ofPattern("''yy  M  d", Locale.US).format(t), right, y, stamp)
                val small = text(34f * u, 0xFFFFB25C.toInt(), face).apply { textAlign = Paint.Align.RIGHT; setShadowLayer(6f * u, 0f, 0f, 0xB3FF5A00.toInt()) }
                val line = listOfNotNull(x.temp, f.pressure?.takeIf { Field.PRESSURE in e.fields }?.let { "${it}hPa" }, x.place?.take(10)).joinToString("  ")
                if (line.isNotEmpty()) c.drawText(line, right, if (top) y + 58f * u else y - 62f * u, small)
            }
            else -> {}
        }
    }

    /** 拍立得: the photo on an off-white print with a deep bottom margin, written on like a keepsake. */
    private fun polaroid(ctx: Context?, photo: Bitmap, e: Edit, f: LifeCards.SnapFacts, zone: ZoneId): Bitmap {
        val w = photo.width; val h = photo.height
        val b = min(w, h) * .045f; val bottom = min(w, h) * .22f
        val out = Bitmap.createBitmap((w + b * 2).roundToInt(), (h + b + bottom).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFFF7F5F0.toInt())
        c.drawBitmap(photo, b, b, null)
        c.drawRect(b, b, b + w, b + h, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0x22000000 })
        polaroidText(c, b, b + h, b + w, out.height.toFloat(), min(w, h) / 1080f, e, f, zone)
        return out
    }

    private fun polaroidText(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, u: Float, e: Edit, f: LifeCards.SnapFacts, zone: ZoneId) {
        val x = facts(e, f, zone)
        val ink = 0xFF2B2A28.toInt(); val soft = 0xB32B2A28.toInt()
        val mid = (top + bottom) / 2
        val big = text(96f * u, ink, bold)
        var tx = left + 10f * u
        x.temp?.let { c.drawText(it, tx, mid + big.textSize * .36f, big); tx += big.measureText(it) + 24f * u }
        x.condition?.let { c.drawText(it, tx, mid - 4f * u, text(40f * u, ink, medium)) }
        val note = listOfNotNull(x.place?.take(12)).joinToString("")
        if (note.isNotEmpty()) c.drawText(note, tx, mid + 44f * u, text(32f * u, soft, serif))
        val stamp = listOfNotNull(x.date, x.clock).joinToString("  ")
        if (stamp.isNotEmpty()) c.drawText(stamp, right - 10f * u, mid + 44f * u, text(30f * u, soft, serif).apply { textAlign = Paint.Align.RIGHT; textSkewX = -.18f })
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
