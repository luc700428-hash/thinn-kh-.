package io.atmos.weather.life

import android.content.Context

/** The camera's lasting choices (kept between visits), like a camera app's settings page. */
internal data class CameraPrefs(
    val grid: Int = 1,              // 0 none, 1 thirds, 2 golden ratio, 3 centre cross
    val level: Boolean = true,      // 水平儀
    val mirrorFront: Boolean = true,
    val bestQuality: Boolean = true,
    val liveMark: Boolean = true,   // watermark over the viewfinder
    val histogram: Boolean = true,  // in 專業
    val video: String = "FHD",      // HD, FHD, UHD or 8K
    val fps60: Boolean = false,
    val stabilize: Boolean = true,
    val sound: Boolean = true,      // record sound with video
    val burnMark: Boolean = true,   // watermark into the video itself
    val nightFrames: Int = 6,
    val audioHigh: Boolean = true,  // 錄音 high quality (stereo 48 kHz 256 kbps)
) {
    fun save(ctx: Context) {
        ctx.getSharedPreferences(FILE, 0).edit()
            .putInt("grid", grid).putBoolean("level", level).putBoolean("mirror", mirrorFront).putBoolean("best", bestQuality)
            .putBoolean("live_mark", liveMark).putBoolean("histogram", histogram).putString("video", video).putBoolean("fps60", fps60)
            .putBoolean("stabilize", stabilize).putBoolean("sound", sound).putBoolean("burn", burnMark).putInt("night_frames", nightFrames)
            .putBoolean("audio_high", audioHigh).apply()
    }

    companion object {
        private const val FILE = "snap_camera"
        fun load(ctx: Context): CameraPrefs = ctx.getSharedPreferences(FILE, 0).let { p ->
            CameraPrefs(p.getInt("grid", 1).coerceIn(0, 3), p.getBoolean("level", true), p.getBoolean("mirror", true), p.getBoolean("best", true),
                p.getBoolean("live_mark", true), p.getBoolean("histogram", true), p.getString("video", "FHD") ?: "FHD", p.getBoolean("fps60", false),
                p.getBoolean("stabilize", true), p.getBoolean("sound", true), p.getBoolean("burn", true), p.getInt("night_frames", 6).coerceIn(3, 8),
                p.getBoolean("audio_high", true))
        }
    }
}
