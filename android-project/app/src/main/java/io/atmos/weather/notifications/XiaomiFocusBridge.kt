package io.atmos.weather.notifications

import android.app.Notification
import android.app.KeyguardManager
import android.os.PowerManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import io.atmos.weather.MainActivity
import io.atmos.weather.R
import org.json.JSONObject

/**
 * Client-side Xiaomi HyperOS 2 Focus Notification / HyperOS 3 Super Island integration.
 * Only sends vendor extensions if the actual device and *app* are granted focus permission.
 * Otherwise retains a fully functional standard Android notification.
 *
 * Official guides:
 * https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
 * https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2146
 * A vendor permission and an approved notification scenario may be required.
 */
object XiaomiFocusBridge {
    enum class Availability { NOT_XIAOMI, UNSUPPORTED, PERMISSION_NOT_GRANTED, ENABLED }

    /** Every in-app notification is classified before its native Android delivery.
     * Xiaomi independently approves which real-world scenarios may use focus/island UI. */
    enum class Kind {
        WEATHER_WARNING, WARNING_SUMMARY, USER_TEST, APP_UPDATE, EARTHQUAKE_REPORT,
        WEATHER_BRIEFING, SEISMIC_EEW, STATION_DETECTION,
        ALARM_RINGING, TIMER_RUNNING, VERIFIED_MINUTE_RAIN, TASK_REMINDER, AMBIENT_WEATHER
    }

    private val guard = Any()
    private var activeIsland: ActiveIsland? = null
    // State is in-process and bounded; we never tick notifications purely to animate.
    private val motionByEvent = LinkedHashMap<String, IslandMotionFrame>()

    internal fun rank(kind: Kind): IslandPriority = when(kind) {
        Kind.SEISMIC_EEW -> IslandPriority.SEISMIC
        Kind.WEATHER_WARNING, Kind.WARNING_SUMMARY -> IslandPriority.WEATHER_ALERT
        Kind.ALARM_RINGING, Kind.TIMER_RUNNING, Kind.VERIFIED_MINUTE_RAIN -> IslandPriority.TIME_CRITICAL
        Kind.TASK_REMINDER -> IslandPriority.REMINDER
        else -> IslandPriority.AMBIENT
    }

    /** No full-screen alarm takeover while unlocked on an approved HyperOS 3 device. */
    fun shouldUseAlarmIsland(context: Context): Boolean {
        if (protocol(context) < 3 || availability(context) != Availability.ENABLED) return false
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
    }

    /** Release arbitration when the alarm/timer is explicitly stopped. */
    fun finish(notificationId: Int) = synchronized(guard) {
        if (activeIsland?.key?.endsWith(":$notificationId") == true) activeIsland = null
        motionByEvent.keys.removeAll { it.endsWith(":$notificationId") }
    }

    fun isXiaomi(): Boolean {
        val name = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return listOf("xiaomi", "redmi", "poco").any { name.contains(it) }
    }

    fun protocol(context: Context): Int {
        if (!isXiaomi()) return 0
        return runCatching { Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) }
            .getOrDefault(0).coerceIn(0, 3)
    }

    /** Does not attempt to bypass Xiaomi's application allowlist. Call from a worker/background thread. */
    fun availability(context: Context): Availability {
        if (!isXiaomi()) return Availability.NOT_XIAOMI
        if (protocol(context) < 2) return Availability.UNSUPPORTED
        return try {
            val extras = Bundle().apply { putString("package", context.packageName) }
            val answer = context.contentResolver.call(
                Uri.parse("content://miui.statusbar.notification.public"),
                "canShowFocus", null, extras
            )
            if (answer?.getBoolean("canShowFocus", false) == true) Availability.ENABLED
            else Availability.PERMISSION_NOT_GRANTED
        } catch (_: Exception) { Availability.PERMISSION_NOT_GRANTED }
    }

    /** A weather-specific HyperOS template. Visible only if Xiaomi enables this app + scenario.
     * We always retain the original Android title, text, channel and actions as fallback.
     * Do not use the official agency's identity for app-owned tests or update notices.
     */
    internal fun focusJson(protocol: Int, title: String, detail: String, kind: Kind, information:IslandInformation?=null): String {
        val heading = (information?.title ?: title).take(64)
        val body = (information?.detail ?: detail).take(400)
        val isWarning = rank(kind).rank <= IslandPriority.WEATHER_ALERT.rank
        val headingColor = if (isWarning) "#E74747" else "#70CCFA"
        val shortLabel = when(kind) {
            Kind.SEISMIC_EEW -> "地震速報"
            Kind.WEATHER_WARNING, Kind.WARNING_SUMMARY -> "天氣警報"
            Kind.ALARM_RINGING -> "鬧鐘"
            Kind.TIMER_RUNNING -> "計時器"
            Kind.VERIFIED_MINUTE_RAIN -> "即時降雨"
            Kind.TASK_REMINDER -> "待辦提醒"
            Kind.AMBIENT_WEATHER -> "目前天氣"
            Kind.USER_TEST -> "測試"
            Kind.APP_UPDATE -> "更新"
            Kind.EARTHQUAKE_REPORT -> "地震報告"
            Kind.STATION_DETECTION -> "測站檢知"
            Kind.WEATHER_BRIEFING -> "天氣"
        }
        val params = JSONObject().apply {
            put("protocol", 1)
            put("business", "weather")
            // Request expansion for an initial ringing alarm only; SystemUI decides.
            put("islandFirstFloat", isWarning || kind == Kind.ALARM_RINGING)
            put("enableFloat", false)
            put("updatable", kind == Kind.ALARM_RINGING || kind == Kind.TIMER_RUNNING ||
                (kind == Kind.APP_UPDATE && information?.progress!=null))
            put("timeout", if (kind == Kind.USER_TEST) 2 else if (kind == Kind.ALARM_RINGING) 10 else 45)
            // Never filter out normal Android notifications because Xiaomi approval is missing.
            put("filterWhenNoPermission", false)
            put("ticker", heading.take(18))  // HyperOS 2 state bar
            put("tickerPic", "miui.focus.pic_alert")
            put("tickerPicDark", "miui.focus.pic_alert")
            put("aodTitle", heading.take(24))
            put("aodPic", "miui.focus.pic_aod")
            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", heading)
                put("content", body)
                put("colorTitle", headingColor)
            })
            if (protocol >= 3) {
                put("param_island", JSONObject().apply {
                    put("islandProperty", if (kind == Kind.ALARM_RINGING || kind == Kind.TIMER_RUNNING) 2 else 1)
                    put("islandTimeout", if (kind == Kind.USER_TEST) 10 else if (kind == Kind.ALARM_RINGING) 600 else rank(kind).holdMillis / 1000L)
                    put("smallIslandArea", JSONObject().apply {
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_alert")
                        })
                    })
                    put("bigIslandArea", JSONObject().apply {
                        put("imageTextInfoLeft", JSONObject().apply {
                            put("type", 1)
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                put("pic", "miui.focus.pic_alert")
                            })
                            // Exact key appears in Xiaomi's HyperOS 3 published sample.
                            put("textInfo", JSONObject().apply {
                                put("frontTitle", shortLabel)
                                put("title", shortLabel.take(4))
                                put("content", IslandInformationPolicy.short(heading,4))
                                put("showHighlightColor", isWarning)
                            })
                        })
                        when {
                            information?.deadlineMillis!=null -> put("sameWidthDigitInfo",JSONObject().apply {
                                put("timerInfo",JSONObject().put("timerType",-1).put("timerWhen",information.deadlineMillis).put("timerSystemCurrent",System.currentTimeMillis()))
                                put("content","剩餘");put("showHighlightColor",false)
                            })
                            information?.progress!=null -> put("progressTextInfo",JSONObject().apply {
                                put("progressInfo",JSONObject().put("progress",information.progress).put("colorReach","#0A84FF").put("colorUnReach","#48484A"))
                                put("textInfo",JSONObject().put("title","${information.progress}%").put("showHighlightColor",false))
                            })
                            !information?.primary.isNullOrBlank() -> put("sameWidthDigitInfo",JSONObject().put("digit",information?.primary).put("content",if(kind==Kind.ALARM_RINGING)"鬧鐘" else "").put("showHighlightColor",false))
                            else -> put("textInfo",JSONObject().put("title",IslandInformationPolicy.short(body,4)).put("showHighlightColor",isWarning))
                        }
                    })
                })
            }
        }
        return JSONObject().put("param_v2", params).toString()
    }

    /** Adds vendor fields only if the device reports the app has focus permission.
     * This is a request to the OS, NOT a guarantee that Xiaomi will show a focus card.
     */
    fun decorate(
        context: Context, notification: Notification, title: String, detail: String,
        kind: Kind = Kind.WEATHER_WARNING, notificationId: Int = 0
    ): Boolean {
        if (availability(context) != Availability.ENABLED) return false
        val category = rank(kind)
        val key = "${kind.name}:$notificationId"
        val now = System.currentTimeMillis()
        synchronized(guard) {
            if (!IslandPriorityPolicy.shouldPresent(activeIsland,key,category,now)) return false
        }
        return runCatching {
            val p = protocol(context)
            // Motion frames only change on *real* notification updates; no redraw loop.
            val scene = IslandIconClassifier.choose(kind.name,title,detail)
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val interactive = power.isInteractive && !power.isPowerSaveMode
            val appReduce = context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
                .getBoolean("reduce_motion",false)
            val systemReduce = runCatching { Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
            val signature = "${scene.name}:${title.take(60)}:${detail.take(90)}"
            val frame = synchronized(guard) { IslandIconMotionPolicy.next(
                motionByEvent[key],signature,now,interactive,appReduce || systemReduce
            ) }
            // Icons change only on real source updates; Xiaomi owns island animation timing.
            val nowTime=System.currentTimeMillis()
            val producerPrimary=notification.extras.getString("thinn.island.primary")
            val deadline=IslandInformationPolicy.deadline(notification.extras.getLong("thinn.island.deadline",0),nowTime)
            val progress=IslandInformationPolicy.progress(notification.extras.getInt(Notification.EXTRA_PROGRESS,0),notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX,0),notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false))
            val cached=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE)
            val weatherStamp=minOf(cached.getLong("widget_time",0),cached.getLong("widget_observed_time",cached.getLong("widget_time",0)));val temperature=cached.getInt("widget_temp",Int.MIN_VALUE)
            val showWeather=kind in setOf(Kind.ALARM_RINGING,Kind.AMBIENT_WEATHER,Kind.WEATHER_BRIEFING) &&
                context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getBoolean("island_weather_details",true) && IslandInformationPolicy.freshWeather(weatherStamp,nowTime,temperature)
            val weatherLine=if(showWeather){
                val at=java.time.Instant.ofEpochMilli(weatherStamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                val rain=cached.getInt("widget_rain",-1).takeIf{it in 0..100}?.let{"降雨 $it%"}
                val wind=cached.getFloat("widget_wind",-1f).takeIf{it.isFinite()&&it>=0f}?.let{"風速 ${"%.1f".format(it)} m/s"}
                listOfNotNull("${cached.getString("widget_city","")} · $temperature° · ${IslandInformationPolicy.weatherLabel(cached.getString("widget_condition","")?:"")}",rain,wind,"更新 $at").joinToString(" · ")
            } else ""
            val info=IslandInformation(title,listOf(detail,weatherLine).filter{it.isNotBlank()}.joinToString("\n"),producerPrimary,deadline,progress)
            notification.extras.putString("miui.focus.param", focusJson(p,title,detail,kind,info))
            // The system may show one static state; Xiaomi has NOT documented a
            // general-purpose animated-vector playback contract inside compact islands.
            val pics = Bundle().apply {
                putParcelable("miui.focus.pic_alert",Icon.createWithBitmap(IslandGlyphPainter.bitmap(context,scene,frame.frame)))
                putParcelable("miui.focus.pic_aod",Icon.createWithBitmap(IslandGlyphPainter.bitmap(context,scene,0,true)))
            }
            notification.extras.putBundle("miui.focus.pics", pics)
            // Officially documented action bridge; the actions already belong to this notification.
            if (p >= 3 && (kind == Kind.ALARM_RINGING || kind == Kind.TIMER_RUNNING || kind == Kind.APP_UPDATE || kind == Kind.TASK_REMINDER)) {
                val nativeActions = notification.actions.orEmpty().take(2)
                if (nativeActions.isNotEmpty()) {
                    val actions = Bundle()
                    val json = JSONObject(notification.extras.getString("miui.focus.param").orEmpty())
                    val body = json.getJSONObject("param_v2")
                    val refs = org.json.JSONArray()
                    nativeActions.forEachIndexed { index, action ->
                        val actionKey = "miui.focus.action_${index + 1}"
                        actions.putParcelable(actionKey, action)
                        refs.put(JSONObject().put("action",actionKey))
                    }
                    body.put("actions", refs)
                    notification.extras.putString("miui.focus.param",json.toString())
                    notification.extras.putBundle("miui.focus.actions", actions)
                }
            }
            synchronized(guard) {
                if(motionByEvent.size >= 64 && key !in motionByEvent) {
                    motionByEvent.remove(motionByEvent.keys.first())
                }
                motionByEvent[key] = frame
                activeIsland = IslandPriorityPolicy.next(key,category,now)
            }
            true
        }.getOrDefault(false)
    }

    /** Explicit user-triggered demonstration only. Never impersonate a real CWA warning. */
    fun sendTest(context: Context): Availability {
        val state = availability(context)
        ThinnNotificationChannels.ensure(context)
        val channelId = ThinnNotificationChannels.channelId(context, ThinnNotificationChannels.Type.DEMO)
        val intent = PendingIntent.getActivity(context, 2001,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("thinn-khì · 焦點通知測試")
            .setContentText("這是測試訊息，不是中央氣象署發布的警報。")
            .setStyle(NotificationCompat.BigTextStyle().bigText("僅用於確認 HyperOS 焦點通知／超級島相容性，這不是正式氣象警報。"))
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        // Go through the same route as every other app-created notification.
        WeatherNotificationDispatcher.post(
            context, 201101, n, "thinn-khì 焦點通知測試",
            "這是測試訊息，非中央氣象署警報", Kind.USER_TEST
        )
        return state
    }
}
