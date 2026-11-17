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
    /** New installs (and anyone who never picked) get the r18 clear chime. */
    const val DEFAULT_TONE = "bright_1"
    /** The V11.2 default keeps its original channel IDs so user channel tweaks survive. */
    private const val LEGACY_TONE = "clear_bell"

    enum class Type { WARNINGS, UPDATES, DEMO, EARTHQUAKES, BRIEFINGS, SMART, COMMUTE }
    data class Tone(val id: String, val title: String, val description: String, val resource: String, val style: String = "classic")

    /** Style tabs in the picker. */
    val styles = listOf("bright" to "輕柔", "warm" to "溫暖", "modern" to "空靈", "classic" to "經典")

    val tones = listOf(
        Tone("bright_1", "清鈴", "鋼片琴下行雙音，柔和清楚", "thinn_tone_bright_1", "bright"),
        Tone("bright_2", "水滴", "木琴三連，圓潤輕快", "thinn_tone_bright_2", "bright"),
        Tone("bright_3", "晨光", "顫音琴上行三音", "thinn_tone_bright_3", "bright"),
        Tone("bright_4", "漣漪", "鋼片琴輕輕回落", "thinn_tone_bright_4", "bright"),
        Tone("warm_1", "木鈴", "拇指琴雙音，溫潤", "thinn_tone_warm_1", "warm"),
        Tone("warm_2", "琴音", "絨布鋼琴三和弦", "thinn_tone_warm_2", "warm"),
        Tone("warm_3", "撥弦", "豎琴琶音", "thinn_tone_warm_3", "warm"),
        Tone("warm_4", "微風", "拇指琴下行，安靜耐聽", "thinn_tone_warm_4", "warm"),
        Tone("modern_1", "光點", "玻璃音雙聲，輕盈", "thinn_tone_modern_1", "modern"),
        Tone("modern_2", "脈衝", "電鋼琴三連，柔和", "thinn_tone_modern_2", "modern"),
        Tone("modern_3", "霓虹", "玻璃音起伏三聲", "thinn_tone_modern_3", "modern"),
        Tone("modern_4", "星環", "電鋼琴上行和弦", "thinn_tone_modern_4", "modern"),
        Tone("clear_bell", "清鈴（經典）", "兩聲柔和，乾淨俐落", "thinn_notify_clear_bell"),
        Tone("crystal", "水晶（經典）", "單聲通透，極簡", "thinn_notify_crystal"),
        Tone("morning", "晨光（經典）", "輕巧雙聲，溫暖清晰", "thinn_notify_morning"),
        Tone("raindrop", "雨滴（經典）", "兩顆圓潤的水滴聲", "thinn_notify_raindrop"),
        Tone("starlight", "星點（經典）", "輕巧三連，細緻溫柔", "thinn_notify_starlight"),
        Tone("breeze", "微風（經典）", "溫柔上行，安靜耐聽", "thinn_notify_breeze"),
        Tone("clear_sky", "晴空（經典）", "雙聲上行，清爽不刺耳", "thinn_notify_clear_sky"),
        Tone("ripple", "漣漪（經典）", "低高交錯，柔和水感", "thinn_notify_ripple")
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
        if (id == LEGACY_TONE) when (type) {
            Type.WARNINGS -> return "thinn_cwa_clear_bell_v2"
            Type.UPDATES -> return "thinn_release_clear_bell_v2"
            Type.DEMO -> return "thinn_test_clear_bell_v2"
            Type.EARTHQUAKES -> return "thinn_cwa_earthquake_report_clear_bell_v15"
            Type.BRIEFINGS -> return "thinn_briefing_clear_bell_v154"
            else -> {}
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
            Triple(Type.BRIEFINGS, "每日天氣早晚報", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(Type.SMART, "智慧天氣提醒", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(Type.COMMUTE, "通勤天氣", NotificationManager.IMPORTANCE_DEFAULT)
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
