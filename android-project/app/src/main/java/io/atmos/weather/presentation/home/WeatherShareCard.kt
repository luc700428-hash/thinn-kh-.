package io.atmos.weather.presentation.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.atmos.weather.R
import io.atmos.weather.comfort.ComfortMessages
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.model.WeatherDescriptions
import io.atmos.weather.domain.model.WeatherSnapshot
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 今日天氣卡片: a 1080×1350 image (the 4:5 size LINE and Instagram show whole) with the sky, the Q
 * buddy, today's numbers and the next twelve hours in one sentence. Drawn with plain Canvas so it
 * works off the main thread and looks the same on every phone.
 */
object WeatherShareCard {
    const val W = 1080
    const val H = 1350

    /** Which buddy drawing matches the sky. */
    fun buddy(condition: WeatherCondition, day: Boolean): Int = when (condition) {
        WeatherCondition.CLEAR -> if (day) R.drawable.buddy_vector_still_sun_day else R.drawable.buddy_vector_still_moon_night
        WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY -> if (day) R.drawable.buddy_vector_still_cloud_day else R.drawable.buddy_vector_still_cloud_night
        WeatherCondition.RAIN, WeatherCondition.FREEZING_RAIN, WeatherCondition.SLEET -> if (day) R.drawable.buddy_vector_still_rain_day else R.drawable.buddy_vector_still_rain_night
        WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL -> if (day) R.drawable.buddy_vector_still_storm_day else R.drawable.buddy_vector_still_storm_night
        WeatherCondition.SNOW -> if (day) R.drawable.buddy_vector_still_snow_day else R.drawable.buddy_vector_still_snow_night
        WeatherCondition.FOG, WeatherCondition.HAZE, WeatherCondition.DUST, WeatherCondition.DUST_STORM ->
            if (day) R.drawable.buddy_vector_still_mist_day else R.drawable.buddy_vector_still_mist_night
        WeatherCondition.UNKNOWN -> if (day) R.drawable.buddy_vector_still_unknown_day else R.drawable.buddy_vector_still_unknown_night
    }

    /** Top and bottom of the sky: warm blue by day, deep indigo at night, grey-blue in rain. */
    fun sky(condition: WeatherCondition, day: Boolean): Pair<Int, Int> = when {
        condition in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM, WeatherCondition.FREEZING_RAIN, WeatherCondition.SLEET, WeatherCondition.HAIL) ->
            if (day) 0xFF4B6382.toInt() to 0xFF2C3B52.toInt() else 0xFF1F2A3D.toInt() to 0xFF0E141F.toInt()
        condition in setOf(WeatherCondition.CLOUDY, WeatherCondition.FOG, WeatherCondition.HAZE, WeatherCondition.DUST, WeatherCondition.DUST_STORM) ->
            if (day) 0xFF6F8BAA.toInt() to 0xFF3E5574.toInt() else 0xFF232E44.toInt() to 0xFF111827.toInt()
        else -> if (day) 0xFF3D8BEA.toInt() to 0xFF1C5BB5.toInt() else 0xFF1B2450.toInt() to 0xFF0B1028.toInt()
    }

    data class Tile(val label: String, val value: String, val caption: String)

    /** The three facts at the bottom: rain chance, air and UV or the next sunrise/sunset. */
    fun tiles(w: WeatherSnapshot, now: Instant, zone: ZoneId): List<Tile> = buildList {
        val next = w.hourly.filter { !it.at.isBefore(now) && it.at.isBefore(now.plusSeconds(12 * 3600)) }
        next.mapNotNull { it.precipitationProbabilityPct }.maxOrNull()?.let { add(Tile("降雨機率", "$it%", "未來 12 小時最高")) }
        w.usAqi?.let { add(Tile("空氣品質", it.toString(), ComfortMessages.aqiWord(it))) }
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val set = w.sunset; val rise = w.sunrise
        if (set != null && now.isBefore(set)) add(Tile("日落", hm.format(set.atZone(zone)), "今天"))
        else if (rise != null) add(Tile("日出", hm.format((if (rise.isBefore(now)) rise.plusSeconds(86400) else rise).atZone(zone)), "明天"))
        (w.currentUv ?: next.mapNotNull { it.uvIndex }.maxOrNull())?.let { add(Tile("紫外線", it.roundToInt().toString(), ComfortMessages.uvWord(it))) }
    }.take(3)

    fun render(ctx: Context, w: WeatherSnapshot, place: String, now: Instant = Instant.now()): Bitmap {
        val zone = runCatching { ZoneId.of(w.timeZoneId ?: "Asia/Taipei") }.getOrDefault(ZoneId.of("Asia/Taipei"))
        val day = w.isDay != false
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (top, bottom) = sky(w.condition, day)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        // Soft light where the sun or moon would be.
        val glow = if (day) 0x66FFE7A8 else 0x44BFD4FF
        c.drawCircle(W * .78f, H * .2f, W * .62f, Paint().apply {
            shader = RadialGradient(W * .78f, H * .2f, W * .62f, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fun text(size: Float, color: Int, b: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (b) bold else Typeface.DEFAULT
        }
        val white = Color.WHITE
        val soft = 0xD9FFFFFF.toInt()
        val faint = 0xA6FFFFFF.toInt()
        val pad = 84f
        c.drawText(place.take(16), pad, 170f, text(54f, white, true))
        val date = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.TAIWAN).format(now.atZone(zone))
        c.drawText(date, pad, 232f, text(38f, faint))
        // The buddy, large, on the right.
        ContextCompat.getDrawable(ctx, buddy(w.condition, day))?.let { d ->
            val size = 420
            d.setBounds(W - size - 40, 210, W - 40, 210 + size); d.draw(c)
        }
        c.drawText("${w.temperatureC.roundToInt()}°", pad - 10f, 560f, text(260f, white, true))
        c.drawText(WeatherDescriptions.current(w), pad, 650f, text(56f, white, true))
        val range = listOfNotNull(w.maxTempC?.let { "最高 ${it.roundToInt()}°" }, w.minTempC?.let { "最低 ${it.roundToInt()}°" }).joinToString("  ·  ")
        if (range.isNotEmpty()) c.drawText(range, pad, 722f, text(40f, soft))
        // One sentence about the next twelve hours, wrapped to two lines.
        val outlook = HomeOutlook.build(w, now)
        val line = text(44f, white)
        var y = 830f
        wrap(outlook.rain, line, W - pad * 2).take(2).forEach { c.drawText(it, pad, y, line); y += 60f }
        outlook.tomorrow?.let { c.drawText(it.take(24), pad, y + 6f, text(36f, faint)) }
        // Three glass tiles.
        val ts = tiles(w, now, zone)
        if (ts.isNotEmpty()) {
            val gap = 24f; val tw = (W - pad * 2 - gap * (ts.size - 1)) / ts.size; val tt = 1010f; val th = 210f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24FFFFFF }
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF; style = Paint.Style.STROKE; strokeWidth = 2f }
            ts.forEachIndexed { i, t ->
                val x = pad + i * (tw + gap)
                val r = RectF(x, tt, x + tw, tt + th)
                c.drawRoundRect(r, 40f, 40f, fill); c.drawRoundRect(r, 40f, 40f, rim)
                c.drawText(t.label, x + 30f, tt + 58f, text(32f, faint))
                c.drawText(t.value, x + 30f, tt + 132f, text(64f, white, true))
                c.drawText(t.caption.take(8), x + 30f, tt + 182f, text(30f, faint))
            }
        }
        c.drawText("thinn-khì 安心天氣", pad, H - 70f, text(34f, faint, true))
        return bmp
    }

    /** Greedy line wrap for CJK text (any character may break). */
    fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>(); var cur = StringBuilder()
        for (ch in text) {
            if (paint.measureText(cur.toString() + ch) > width && cur.isNotEmpty()) { lines += cur.toString(); cur = StringBuilder() }
            cur.append(ch)
        }
        if (cur.isNotEmpty()) lines += cur.toString()
        return lines
    }

    /** Saves the card and opens the share sheet with the image and a short caption. */
    fun share(ctx: Context, bmp: Bitmap, caption: String): Boolean = runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "thinn-khi-weather-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.update.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, caption)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "分享今日天氣").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        true
    }.getOrDefault(false)
}
