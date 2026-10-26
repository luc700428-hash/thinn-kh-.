package io.atmos.weather.notifications

/**
 * Pure, testable motion semantics for Xiaomi-owned SystemUI notifications.
 * Xiaomi's public island contract supplies images and event updates, NOT a third-party
 * frame-render callback or an app-controlled spring/120-Hz animation surface.
 * No timer, repeating job, animation ticker, or continuous notification updates here.
 */
internal enum class IslandIconScene {
    ALARM, SUN, CLOUD, RAIN, SNOW, TIMER, QUAKE, TYPHOON, THUNDER,
    HOT, COLD, AIR, UV, NOTE, CALENDAR, GENERIC, MOON, FOG, UPDATE, DUST
}

internal object IslandIconClassifier {
    fun choose(kind: String, title: String, detail: String): IslandIconScene {
        val label = (title + " " + detail).lowercase()
        return when (kind) {
            "SEISMIC_EEW" -> IslandIconScene.QUAKE
            "ALARM_RINGING" -> IslandIconScene.ALARM
            "TIMER_RUNNING" -> IslandIconScene.TIMER
            "VERIFIED_MINUTE_RAIN" -> IslandIconScene.RAIN
            "TASK_REMINDER" -> if (listOf("行程", "日曆", "會議", "約會", "schedule").any(label::contains))
                IslandIconScene.CALENDAR else IslandIconScene.NOTE
            else -> when {
                listOf("沙塵", "揚塵", "dust").any(label::contains) -> IslandIconScene.DUST
                listOf("有霧", "大霧", "濃霧", "霧", "fog").any(label::contains) -> IslandIconScene.FOG

                listOf("颱風", "台風", "typhoon", "cyclone").any(label::contains) -> IslandIconScene.TYPHOON
                listOf("雷雨", "閃電", "雷暴", "thunder").any(label::contains) -> IslandIconScene.THUNDER
                listOf("豪雨", "大雨", "降雨", "陣雨", "下雨", "rain").any(label::contains) -> IslandIconScene.RAIN
                listOf("低溫", "寒流", "冷氣團", "冰凍", "freezing").any(label::contains) -> IslandIconScene.COLD
                listOf("高溫", "酷熱", "熱浪", "heat").any(label::contains) -> IslandIconScene.HOT
                listOf("aqi", "空氣品質", "懸浮微粒", "pm2.5").any(label::contains) -> IslandIconScene.AIR
                listOf("紫外線", "uv 指數", "uv index").any(label::contains) -> IslandIconScene.UV
                listOf("雪", "霰", "sleet").any(label::contains) -> IslandIconScene.SNOW
                listOf("多雲", "陰天", "雲", "cloud").any(label::contains) -> IslandIconScene.CLOUD
                listOf("晴", "陽光", "sunny", "sunshine").any(label::contains) -> IslandIconScene.SUN
                "EARTHQUAKE_REPORT" == kind -> IslandIconScene.QUAKE
                "APP_UPDATE" == kind -> IslandIconScene.UPDATE
                else -> IslandIconScene.GENERIC
            }
        }
    }
}

internal data class IslandMotionFrame(val signature: String, val frame: Int, val changedAtMillis: Long)

internal object IslandIconMotionPolicy {
    // This is a guard against rapid vendor-notification redraws, NOT animation FPS.
    const val MIN_FRAME_SPACING_MS = 12_000L

    fun next(previous: IslandMotionFrame?, signature: String, nowMillis: Long,
             interactive: Boolean, reduceMotion: Boolean): IslandMotionFrame {
        if (reduceMotion || !interactive) return IslandMotionFrame(signature, 0, nowMillis)
        if (previous == null || previous.signature.substringBefore(':') != signature.substringBefore(':'))
            return IslandMotionFrame(signature, 1, nowMillis)
        if (nowMillis - previous.changedAtMillis < MIN_FRAME_SPACING_MS)
            return previous.copy(signature=signature)
        // A genuine producer-driven notification update may advance the pictogram state.
        // Never schedule a second update merely to change frames.
        return IslandMotionFrame(signature, 1 - previous.frame, nowMillis)
    }
}
