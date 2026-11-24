package io.atmos.weather.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * How much real glass this phone draws. Liquid glass re-renders the scene behind every glass piece
 * on every frame, which a flagship GPU does easily and a mid-range one does not, so:
 *  - FULL: every card and control samples the scene, with the thick-rim lens;
 *  - BALANCED: everything samples and blurs, but only small controls bend light;
 *  - LIGHT: no sampling; glass is a translucent tint (still the same shapes, rims and motion).
 * "Auto" picks from the phone's memory and Android version, then steps down for good if real
 * frames keep missing their deadline. The person can also choose FULL or LIGHT in settings.
 */
object GlassQuality {
    enum class Level { FULL, BALANCED, LIGHT }

    const val SETTING = "glass_quality"          // 0 auto, 1 full, 2 light
    private const val CAP = "glass_auto_cap"      // 0 none, 1 balanced, 2 light (auto mode only)

    /** Pure choice from hardware facts, so it can be tested. */
    fun fromHardware(sdk: Int, totalMemBytes: Long, lowRam: Boolean, performanceClass: Int): Level = when {
        lowRam || sdk < 33 -> Level.LIGHT
        // Memory not reported (some builds and test runtimes): assume a typical modern phone.
        totalMemBytes <= 0L -> Level.BALANCED
        performanceClass >= 33 || totalMemBytes >= 11_000_000_000L -> Level.FULL
        totalMemBytes >= 7_500_000_000L -> Level.BALANCED
        totalMemBytes >= 5_500_000_000L -> Level.BALANCED
        else -> Level.LIGHT
    }

    /** The automatic level after any step-down learned from slow frames. */
    fun capped(level: Level, cap: Int): Level = when {
        cap >= 2 -> Level.LIGHT
        cap == 1 && level == Level.FULL -> Level.BALANCED
        else -> level
    }

    fun resolve(choice: Int, automatic: Level): Level = when (choice) {
        1 -> Level.FULL
        2 -> Level.LIGHT
        else -> automatic
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)

    fun hardware(ctx: Context): Level {
        val manager = ctx.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
        val performance = if (Build.VERSION.SDK_INT >= 31) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0
        return fromHardware(Build.VERSION.SDK_INT, memory.totalMem, manager?.isLowRamDevice == true, performance)
    }

    @Volatile private var hardwareCache: Level? = null

    fun automatic(ctx: Context): Level {
        val base = hardwareCache ?: hardware(ctx.applicationContext).also { hardwareCache = it }
        return capped(base, prefs(ctx).getInt(CAP, 0))
    }

    /** Observable current level; changes to the setting or the learned cap apply at once. */
    @Composable
    fun current(): Level {
        val ctx = LocalContext.current
        val prefs = remember(ctx) { prefs(ctx) }
        var revision by remember { mutableIntStateOf(0) }
        DisposableEffect(prefs) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == SETTING || key == CAP || key == null) revision++
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return remember(revision, ctx) { resolve(prefs.getInt(SETTING, 0), automatic(ctx)) }
    }

    /**
     * Frame-time judge, fed real frame durations. Over a window of frames that were actually
     * drawn (animation or scrolling), if more than a fifth took longer than one and a half refresh
     * intervals, the window counts as janky. Two janky windows in a row step the automatic level
     * down once. Idle screens produce no frames and so are never judged.
     */
    class JankJudge(private val window: Int = 240, private val janky: Float = .2f) {
        private var frames = 0
        private var slow = 0
        private var strikes = 0
        /** Returns true when the level should step down. */
        fun frame(durationNanos: Long, intervalNanos: Long): Boolean {
            if (durationNanos <= 0 || intervalNanos <= 0) return false
            frames++
            if (durationNanos > intervalNanos * 3 / 2) slow++
            if (frames < window) return false
            val bad = slow > frames * janky
            frames = 0; slow = 0
            strikes = if (bad) strikes + 1 else 0
            if (strikes >= 2) { strikes = 0; return true }
            return false
        }
    }

    private fun Context.activity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
        return null
    }

    /** Watches real frames while the app is open (auto mode only) and learns a lower level if needed. */
    @Composable
    fun Watch() {
        val ctx = LocalContext.current
        DisposableEffect(ctx) {
            val activity = ctx.activity()
            val window: Window? = activity?.window
            val prefs = prefs(ctx)
            if (window == null || prefs.getInt(SETTING, 0) != 0 || prefs.getInt(CAP, 0) >= 2) {
                onDispose { }
            } else {
                val judge = JankJudge()
                val handler = Handler(Looper.getMainLooper())
                val listener = Window.OnFrameMetricsAvailableListener { w, metrics, _ ->
                    val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                    val rate = w.decorView.display?.refreshRate?.takeIf { it in 30f..240f } ?: 60f
                    if (judge.frame(total, (1_000_000_000L / rate).toLong())) {
                        val cap = prefs.getInt(CAP, 0)
                        if (prefs.getInt(SETTING, 0) == 0 && cap < 2) prefs.edit().putInt(CAP, cap + 1).apply()
                    }
                }
                runCatching { window.addOnFrameMetricsAvailableListener(listener, handler) }
                onDispose { runCatching { window.removeOnFrameMetricsAvailableListener(listener) } }
            }
        }
    }

    /** Choosing a level by hand clears what auto mode learned. */
    fun choose(ctx: Context, choice: Int) {
        prefs(ctx).edit().putInt(SETTING, choice.coerceIn(0, 2)).putInt(CAP, 0).apply()
    }
}
