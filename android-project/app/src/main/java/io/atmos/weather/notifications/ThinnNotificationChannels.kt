package io.atmos.weather.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.app.NotificationCompat
import io.atmos.weather.MainActivity

/** Eight original notification tones, selected once for all app-authored notifications.
 * Every new selection uses its own sound-specific Android notification channel IDs.
 * Android 8+ treats channel sounds as user-controlled and immutable once created.
 */
object ThinnNotificationChannels {
    const val PREF_TONE = "notification_tone_id"
    const val DEFAULT_TONE = "clear_bell"

    enum class Type { WARNINGS, UPDATES, DEMO, EARTHQUAKES, BRIEFINGS }
    data class Tone(val id: String, val title: String, val description: String, val resource: String)

    val tones = listOf(
        Tone("clear_bell", "清鈴", "兩聲清亮，乾淨俐落", "thinn_notify_clear_bell"),
        Tone("crystal", "水晶", "單聲通透，極簡", "thinn_notify_crystal"),
        Tone("morning", "晨光", "輕巧雙聲，溫暖清晰", "thinn_notify_morning"),
        Tone("raindrop", "雨滴", "兩顆晶亮的水滴聲", "thinn_notify_raindrop"),
        Tone("starlight", "星點", "短促三連，細緻閃亮", "thinn_notify_starlight"),
        Tone("breeze", "微風", "溫柔上行，安靜耐聽", "thinn_notify_breeze"),
        Tone("clear_sky", "晴空", "明快雙聲，清爽有朝氣", "thinn_notify_clear_sky"),
        Tone("ripple", "漣漪", "低高交錯，清透水感", "thinn_notify_ripple")
    )

    fun selectedToneId(context: Context): String {
        val saved = context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
            .getString(PREF_TONE, DEFAULT_TONE)
        return saved?.takeIf { id -> tones.any { it.id == id } } ?: DEFAULT_TONE
    }

    fun tone(id: String): Tone = tones.firstOrNull { it.id == id } ?: tones.first()

    fun selectedTone(context: Context): Tone = tone(selectedToneId(context))

    fun channelId(context: Context, type: Type): String {
        val id = selectedToneId(context)
        // Preserve V11.2 channels, including any Android-level sound/mute preferences.
        if (id == DEFAULT_TONE) return when (type) {
            Type.WARNINGS -> "thinn_cwa_clear_bell_v2"
            Type.UPDATES -> "thinn_release_clear_bell_v2"
            Type.DEMO -> "thinn_test_clear_bell_v2"
            Type.EARTHQUAKES -> "thinn_cwa_earthquake_report_clear_bell_v15"
            Type.BRIEFINGS -> "thinn_briefing_clear_bell_v154"
        }
        return "thinn_${type.name.lowercase()}_tone_v3_$id"
    }

    /** Persists selection; creates each relevant channel with its matching sound.
     * Does not override system-level mute, DND, or a user's custom channel setting.
     */
    fun select(context: Context, id: String) {
        require(tones.any { it.id == id }) { "Unknown notification tone" }
        context.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
            .edit().putString(PREF_TONE, id).apply()
        ensure(context)
    }

    private fun soundUri(context: Context, resource: String): Uri =
        Uri.parse("android.resource://${context.packageName}/raw/$resource")

    fun ensure(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chosen = selectedTone(context)
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        listOf(
            Triple(Type.WARNINGS, "天氣警特報", NotificationManager.IMPORTANCE_HIGH),
            Triple(Type.UPDATES, "版本更新", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(Type.DEMO, "通知測試", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(Type.EARTHQUAKES, "地震報告（非預警）", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(Type.BRIEFINGS, "定時智慧天氣提醒", NotificationManager.IMPORTANCE_DEFAULT)
        ).forEach { (type, name, importance) ->
            val id = channelId(context, type)
            if (manager.getNotificationChannel(id) == null) {
                manager.createNotificationChannel(NotificationChannel(id, "$name · ${chosen.title}", importance).apply {
                    description = "thinn-khì 專屬通知音：${chosen.title}。可於手機通知設定調整或靜音。"
                    setSound(soundUri(context, chosen.resource), attributes)
                    enableVibration(true)
                })
            }
        }
    }

    // Prevent overlapping sound previews on quick repeated taps.
    private var previewPlayer: MediaPlayer? = null

    @Synchronized
    fun playPreview(context: Context, id: String = selectedToneId(context)) {
        previewPlayer?.runCatching { stop(); release() }
        previewPlayer = null
        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(attrs)
            player.setDataSource(context.applicationContext, soundUri(context, tone(id).resource))
            player.setOnCompletionListener { completed ->
                synchronized(this) {
                    if (previewPlayer === completed) previewPlayer = null
                    completed.release()
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                synchronized(this) {
                    if (previewPlayer === failed) previewPlayer = null
                    failed.release()
                }
                true
            }
            player.prepare()
            previewPlayer = player
            player.start()
        } catch (_: Exception) {
            if (previewPlayer === player) previewPlayer = null
            player.release()
        }
    }

    /** Send a real notification using the exact currently selected notification channel. */
    fun sendTest(context: Context): Boolean {
        ensure(context)
        val click = PendingIntent.getActivity(context, 293,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = "thinn-khì · 通知音測試"
        val detail = "${selectedTone(context).title} 已選用。這不是正式氣象警報。"
        val message = NotificationCompat.Builder(context, channelId(context, Type.DEMO))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).setContentIntent(click).build()
        return WeatherNotificationDispatcher.post(context, 201102, message, title, detail,
            XiaomiFocusBridge.Kind.USER_TEST)
    }
}
