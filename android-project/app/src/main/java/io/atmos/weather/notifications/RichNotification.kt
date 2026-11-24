package io.atmos.weather.notifications

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import io.atmos.weather.R
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.presentation.home.WeatherShareCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Every notification the app writes gets two faces, like iOS:
 *  - collapsed: a picture (the buddy for weather, a symbol otherwise), a bold headline, one line
 *    under it and two small facts on the right, so it can be read without opening;
 *  - expanded: the same headline, up to six facts in tiles, a progress bar when something is under
 *    way, the full text and where/when it came from.
 * The expand arrow of the system header stays, and the plain title/text stay on the notification
 * for watches, screen readers and the lock screen.
 *
 * Callers can describe the card through extras ([card]); otherwise one is made from the title, the
 * text and the latest weather the widgets know.
 */
object RichNotification {
    data class Card(
        val headline: String,
        val subtitle: String = "",
        val facts: List<Pair<String, String>> = emptyList(),
        val details: String = "",
        val footer: String = "",
        val icon: Int = 0,
        val accent: Int = 0,
        val progress: Int = -1,
    )

    private const val K = "thinn.card."

    /** Extras describing the card; add to a builder with [NotificationCompat.Builder.addExtras]. */
    fun card(card: Card): Bundle = Bundle().apply {
        putString(K + "headline", card.headline); putString(K + "subtitle", card.subtitle)
        putStringArray(K + "facts", card.facts.map { "${it.first}\t${it.second}" }.toTypedArray())
        putString(K + "details", card.details); putString(K + "footer", card.footer)
        putInt(K + "icon", card.icon); putInt(K + "accent", card.accent); putInt(K + "progress", card.progress)
    }

    fun NotificationCompat.Builder.richCard(card: Card): NotificationCompat.Builder = addExtras(card(card))

    private fun read(extras: Bundle): Card? {
        val headline = extras.getString(K + "headline") ?: return null
        return Card(headline, extras.getString(K + "subtitle").orEmpty(),
            extras.getStringArray(K + "facts").orEmpty().mapNotNull { s -> s.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } },
            extras.getString(K + "details").orEmpty(), extras.getString(K + "footer").orEmpty(),
            extras.getInt(K + "icon"), extras.getInt(K + "accent"), extras.getInt(K + "progress", -1))
    }

    /** What the widgets last showed, for weather notifications that bring no facts of their own. */
    internal fun weatherFacts(ctx: Context): Pair<List<Pair<String, String>>, Int> {
        val p = ctx.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
        val facts = ArrayList<Pair<String, String>>()
        p.getInt("widget_temp", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { facts += "氣溫" to "$it°" }
        p.getInt("widget_rain", -1).takeIf { it >= 0 }?.let { facts += "降雨" to "$it%" }
        if (p.contains("widget_high")) facts += "高／低" to "${p.getInt("widget_high", 0)}°／${p.getInt("widget_low", 0)}°"
        p.getInt("widget_humidity", -1).takeIf { it >= 0 }?.let { facts += "濕度" to "$it%" }
        p.getFloat("widget_wind", -1f).takeIf { it >= 0 }?.let { facts += "風速" to "%.1f m/s".format(it) }
        p.getInt("widget_air", -1).takeIf { it >= 0 }?.let { facts += "空氣" to "AQI $it" }
        val condition = runCatching { WeatherCondition.valueOf(p.getString("widget_condition", "UNKNOWN") ?: "UNKNOWN") }.getOrDefault(WeatherCondition.UNKNOWN)
        return facts to WeatherShareCard.buddy(condition, p.getBoolean("widget_day", true))
    }

    internal fun accent(kind: XiaomiFocusBridge.Kind): Int = when (kind) {
        XiaomiFocusBridge.Kind.SEISMIC_EEW, XiaomiFocusBridge.Kind.STATION_DETECTION -> 0xFFE5484D.toInt()
        XiaomiFocusBridge.Kind.EARTHQUAKE_REPORT -> 0xFFF2994A.toInt()
        XiaomiFocusBridge.Kind.WEATHER_WARNING, XiaomiFocusBridge.Kind.WARNING_SUMMARY -> 0xFFF0A202.toInt()
        XiaomiFocusBridge.Kind.VERIFIED_MINUTE_RAIN -> 0xFF3D8BEA.toInt()
        XiaomiFocusBridge.Kind.APP_UPDATE -> 0xFF308970.toInt()
        XiaomiFocusBridge.Kind.TASK_REMINDER, XiaomiFocusBridge.Kind.ALARM_RINGING, XiaomiFocusBridge.Kind.TIMER_RUNNING -> 0xFF8E6CEF.toInt()
        else -> 0xFF308970.toInt()
    }

    private fun weatherish(kind: XiaomiFocusBridge.Kind) = kind in setOf(XiaomiFocusBridge.Kind.WEATHER_WARNING,
        XiaomiFocusBridge.Kind.WARNING_SUMMARY, XiaomiFocusBridge.Kind.WEATHER_BRIEFING, XiaomiFocusBridge.Kind.AMBIENT_WEATHER,
        XiaomiFocusBridge.Kind.VERIFIED_MINUTE_RAIN, XiaomiFocusBridge.Kind.ALARM_RINGING)

    /** The card for a notification: the caller's own, or one made from its text and the weather. */
    internal fun cardFor(ctx: Context, n: Notification, title: String, detail: String, kind: XiaomiFocusBridge.Kind): Card {
        read(n.extras)?.let { own ->
            return own.copy(icon = own.icon.takeIf { it != 0 } ?: iconFor(ctx, kind),
                accent = own.accent.takeIf { it != 0 } ?: accent(kind),
                footer = own.footer.ifBlank { footer(n) })
        }
        val big = n.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()
            ?: n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty()
        val facts = if (weatherish(kind)) weatherFacts(ctx).first else emptyList()
        return Card(title.ifBlank { n.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty() },
            detail, facts, if (big != detail) big else "", footer(n), iconFor(ctx, kind), accent(kind))
    }

    private fun iconFor(ctx: Context, kind: XiaomiFocusBridge.Kind): Int = when (kind) {
        XiaomiFocusBridge.Kind.SEISMIC_EEW, XiaomiFocusBridge.Kind.STATION_DETECTION, XiaomiFocusBridge.Kind.EARTHQUAKE_REPORT -> R.drawable.island_quake_0
        XiaomiFocusBridge.Kind.APP_UPDATE -> R.drawable.island_update_0
        XiaomiFocusBridge.Kind.ALARM_RINGING, XiaomiFocusBridge.Kind.TIMER_RUNNING, XiaomiFocusBridge.Kind.TASK_REMINDER -> R.drawable.island_alarm_0
        else -> weatherFacts(ctx).second
    }

    private fun footer(n: Notification): String {
        val at = if (n.`when` > 0) n.`when` else System.currentTimeMillis()
        return "thinn-khì · " + DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.of("Asia/Taipei")).format(Instant.ofEpochMilli(at))
    }

    internal fun collapsed(pkg: String, c: Card): RemoteViews = RemoteViews(pkg, R.layout.notif_card_collapsed).apply {
        if (c.icon != 0) setImageViewResource(R.id.notif_icon, c.icon) else setViewVisibility(R.id.notif_icon, View.GONE)
        setTextViewText(R.id.notif_headline, c.headline)
        setTextViewText(R.id.notif_subtitle, c.subtitle.ifBlank { c.details })
        listOf(R.id.notif_peek0, R.id.notif_peek1).forEachIndexed { i, id ->
            val f = c.facts.getOrNull(i)
            if (f == null) setViewVisibility(id, View.GONE) else setTextViewText(id, "${f.first} ${f.second}")
        }
        setContentDescription(R.id.notif_headline, c.headline)
    }

    internal fun expanded(pkg: String, c: Card): RemoteViews = RemoteViews(pkg, R.layout.notif_card_expanded).apply {
        if (c.icon != 0) setImageViewResource(R.id.notif_icon, c.icon) else setViewVisibility(R.id.notif_icon, View.GONE)
        setTextViewText(R.id.notif_headline, c.headline)
        if (c.subtitle.isBlank()) setViewVisibility(R.id.notif_subtitle, View.GONE) else setTextViewText(R.id.notif_subtitle, c.subtitle)
        if (c.accent != 0) setInt(R.id.notif_accent, "setColorFilter", c.accent)
        val chips = listOf(R.id.notif_chip0, R.id.notif_chip1, R.id.notif_chip2, R.id.notif_chip3, R.id.notif_chip4, R.id.notif_chip5)
        chips.forEachIndexed { i, id ->
            val f = c.facts.getOrNull(i)
            // A short row keeps equal tiles: missing ones hold their space but show nothing.
            if (f == null) setViewVisibility(id, View.INVISIBLE)
            else setTextViewText(id, "${f.second}\n${f.first}")
        }
        if (c.facts.isEmpty()) setViewVisibility(R.id.notif_row0, View.GONE)
        if (c.facts.size <= 3) setViewVisibility(R.id.notif_row1, View.GONE)
        if (c.progress in 0..100) { setViewVisibility(R.id.notif_progress, View.VISIBLE); setProgressBar(R.id.notif_progress, 100, c.progress, false) }
        if (c.details.isBlank()) setViewVisibility(R.id.notif_details, View.GONE) else setTextViewText(R.id.notif_details, c.details)
        setTextViewText(R.id.notif_footer, c.footer)
    }

    /**
     * The same notification with the two faces attached. Promoted live updates and notifications
     * that already draw their own views are left exactly as they are.
     */
    fun enrich(ctx: Context, n: Notification, title: String, detail: String, kind: XiaomiFocusBridge.Kind): Notification = runCatching {
        if (n.extras.getBoolean("android.requestPromotedOngoing", false)) return n
        @Suppress("DEPRECATION")
        if (n.contentView != null || n.bigContentView != null) return n
        val card = cardFor(ctx, n, title, detail, kind)
        if (card.headline.isBlank()) return n
        val b = Notification.Builder.recoverBuilder(ctx, n)
        b.setStyle(Notification.DecoratedCustomViewStyle())
        val small = collapsed(ctx.packageName, card)
        b.setCustomContentView(small)
        b.setCustomBigContentView(expanded(ctx.packageName, card))
        b.setCustomHeadsUpContentView(collapsed(ctx.packageName, card))
        b.build()
    }.getOrDefault(n)
}
