package io.atmos.weather.haptics

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Fine-grained adaptive haptics: prefer the device's calibrated haptic primitives.
 * Fine motor support is hardware-specific. Every event has a safe amplitude fallback.
 * This engine is only invoked by foreground UI interactions / sampled weather frames.
 */
class HapticEngine(context: Context) {
    enum class Event { TAP, DRAG, RELEASE, RAIN_DROP, THUNDER, ALERT }

    private val app = context.applicationContext
    private val vibrator: Vibrator? =
        (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    private val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    @Volatile private var settings = HapticSettings.load(app)
    private val lastPlayed = LongArray(Event.entries.size)
    private var lastGlobal: Long = 0L
    private val lock = Any()

    val available: Boolean get() = vibrator?.hasVibrator() == true
    val amplitudeSupported: Boolean get() = vibrator?.hasAmplitudeControl() == true
    val precisePrimitivesSupported: Boolean get() = supports(
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        VibrationEffect.Composition.PRIMITIVE_TICK
    )

    fun configure(value: HapticSettings) { settings = value }

    /** Normalised visible-drop size, used for rare rain-on-glass impacts. */
    fun rainImpact(size: Float) {
        play(Event.RAIN_DROP, variation = (0.22f + 0.78f * size.coerceIn(0f, 1f)))
    }

    fun play(event: Event, preview: Boolean = false, variation: Float = 1f) {
        val prefs = settings
        if (!prefs.enabled || !available) return
        if (!preview && event == Event.RAIN_DROP && !prefs.rainDrops) return
        if (!preview && event == Event.THUNDER && !prefs.thunder) return
        if (prefs.respectSilentMode && audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        if (prefs.reduceInPowerSaver && power?.isPowerSaveMode == true &&
            event in setOf(Event.DRAG, Event.RAIN_DROP, Event.THUNDER)) return

        // Event profiles are fixed for realism (tiny droplets, stronger thunder).
        // There are no second-level intensity sliders: the visible master controls all events.
        val category = when(event) {
            Event.TAP -> .72f
            Event.DRAG -> .40f
            Event.RELEASE -> .63f
            Event.RAIN_DROP -> .38f
            Event.THUNDER -> .96f
            Event.ALERT -> .90f
        }
        // The common 0..100% slider scales EVERY event before the hardware waveform.
        val force = (prefs.master.coerceIn(0f,1f) * category *
            variation.coerceIn(0f,1f)).pow(1.25f)
        if (force < 0.003f) return

        val minimumInterval = when(event) {
            Event.TAP -> 55L
            Event.DRAG -> 135L
            Event.RELEASE -> 120L
            Event.RAIN_DROP -> 1000L
            Event.THUNDER -> 5500L
            Event.ALERT -> 3500L
        }
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            // Do not accumulate background queues or overwhelm a device's actuator.
            if (now - lastPlayed[event.ordinal] < minimumInterval) return
            if (now - lastGlobal < 36L && event != Event.ALERT) return
            lastPlayed[event.ordinal] = now
            lastGlobal = now
        }

        val effect = makePrimitiveEffect(event, force) ?: makeAmplitudeFallback(event, force)
        try {
            vibrator?.vibrate(effect)
        } catch (_: SecurityException) {
            // Respect OEM/security restrictions; never crash the forecast UI.
        } catch (_: IllegalArgumentException) {
            // Defensive fallback for devices rejecting a waveform.
        } catch (_: UnsupportedOperationException) {
            // Some vendor haptic stacks expose capabilities but reject specific compositions.
        }
    }

    private fun supports(vararg primitive: Int): Boolean = try {
        vibrator?.let { device -> primitive.all { value ->
            // Keep the public IntDef contract explicit; unknown values fail closed.
            when(value) {
                VibrationEffect.Composition.PRIMITIVE_CLICK -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
                VibrationEffect.Composition.PRIMITIVE_THUD -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
                VibrationEffect.Composition.PRIMITIVE_SPIN -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SPIN)
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
                VibrationEffect.Composition.PRIMITIVE_TICK -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK -> device.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                else -> false
            }
        }} == true
    } catch (_: Exception) { false }

    private fun makePrimitiveEffect(event: Event, force: Float): VibrationEffect? {
        // Choose a manufacturer-calibrated primitive whenever all necessary parts are supported.
        val plan: List<Pair<Int, Float>> = when(event) {
            Event.TAP -> listOf(VibrationEffect.Composition.PRIMITIVE_CLICK to 0.65f)
            Event.DRAG -> listOf(VibrationEffect.Composition.PRIMITIVE_LOW_TICK to 0.33f)
            Event.RELEASE -> listOf(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL to .35f, VibrationEffect.Composition.PRIMITIVE_TICK to .30f)
            Event.RAIN_DROP -> listOf(VibrationEffect.Composition.PRIMITIVE_LOW_TICK to .26f)
            Event.THUNDER -> listOf(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE to .35f, VibrationEffect.Composition.PRIMITIVE_THUD to .55f)
            Event.ALERT -> listOf(VibrationEffect.Composition.PRIMITIVE_CLICK to .65f, VibrationEffect.Composition.PRIMITIVE_TICK to .42f)
        }
        if (!supports(*plan.map { it.first }.toIntArray())) return null
        val builder = VibrationEffect.startComposition()
        for ((index, item) in plan.withIndex()) {
            val delay = if (index == 0) 0 else when(event) {
                Event.THUNDER -> 45
                Event.ALERT -> 60
                else -> 12
            }
            builder.addPrimitive(item.first, (item.second * force * 1.4f).coerceIn(0.005f,1f), delay)
        }
        return builder.compose()
    }

    private fun makeAmplitudeFallback(event: Event, force: Float): VibrationEffect {
        // Use short single pulses instead of high-frequency buzzing on basic motors.
        val duration = when(event) {
            Event.TAP -> 10L
            Event.DRAG -> 7L
            Event.RELEASE -> 14L
            Event.RAIN_DROP -> 8L
            Event.THUNDER -> 38L
            Event.ALERT -> 42L
        }
        val amplitude = if (amplitudeSupported)
            (8 + 175 * force).roundToInt().coerceIn(1, 210)
        else VibrationEffect.DEFAULT_AMPLITUDE
        return VibrationEffect.createOneShot(duration, amplitude)
    }
}
