package io.atmos.weather.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Presentation-only animation tokens. Never alter repository or domain state. */
object AppleMotion {
    // Direction-preserving, non-linear 0.15,1.0,0.3,1.0 transition curve.
    val fluid: Easing = CubicBezierEasing(0.15f, 1.0f, 0.3f, 1.0f)
    val snap: Easing = CubicBezierEasing(.22f, 1f, .36f, 1f)
    val popover: Easing = CubicBezierEasing(.2f, .9f, .3f, 1f)
    const val pressedScale: Float = 0.96f
    const val pressedAlpha: Float = 0.92f
    const val springDamping: Float = 0.72f
    const val springStiffness: Float = 190f
    const val glassBlurDp: Int = 30 // Visual backdrop, only when a backdrop slot is supplied.
    const val glowDurationMillis: Int = 9000

    /**
     * Apple's springs converted exactly: stiffness = (2π / duration)², dampingRatio = 1 − bounce.
     * smooth/snappy/bouncy are SwiftUI's presets at 0.5 s; interactive follows the finger;
     * zoom and glass-morph values were measured frame by frame on iOS 26/27.
     */
    fun <T> appleSpring(duration:Float=0.5f,bounce:Float=0f):androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio=if(bounce>=0f)1f-bounce else 1f/(1f+bounce),
            stiffness=(2f*Math.PI.toFloat()/duration.coerceAtLeast(.05f)).let{it*it})
    fun <T> smooth()=appleSpring<T>(0.5f,0f)
    fun <T> snappy()=appleSpring<T>(0.5f,0.15f)
    fun <T> bouncy()=appleSpring<T>(0.5f,0.30f)
    fun <T> interactive()=androidx.compose.animation.core.spring<T>(dampingRatio=0.86f,stiffness=1754.6f)
    fun <T> zoomOpen()=androidx.compose.animation.core.spring<T>(dampingRatio=0.975f,stiffness=281f)
    fun <T> zoomClose()=androidx.compose.animation.core.spring<T>(dampingRatio=0.98f,stiffness=365f)
    /** iOS 26/27 glass controls grow under the finger instead of sinking. */
    const val pressedSwell: Float = 1.06f
    /** UIAlertController-style entrance: from 1.15× to 1× with no overshoot. */
    const val alertStartScale: Float = 1.15f
    fun <T> alert()=appleSpring<T>(0.4f,0f)
    fun <T> sheet()=appleSpring<T>(0.5f,0f)
    /** Navigation push and pop: critically damped, about 0.35 s. */
    fun <T> push()=appleSpring<T>(0.35f,0f)
    fun <T> glassMorph()=androidx.compose.animation.core.spring<T>(dampingRatio=0.87f,stiffness=503.5f)
}

/** Preferences are observable so changing accessibility affects the dock and controls immediately. */
@Composable
internal fun rememberBooleanSetting(key:String,default:Boolean=false):Boolean {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("thinn_settings",android.content.Context.MODE_PRIVATE)}
    var value by androidx.compose.runtime.remember(prefs,key) {
        androidx.compose.runtime.mutableStateOf(prefs.getBoolean(key,default))
    }
    androidx.compose.runtime.DisposableEffect(prefs,key) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,changed ->
            if(changed==key || changed==null)value=prefs.getBoolean(key,default)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return value
}
@Composable
internal fun rememberAppleReduceMotion():Boolean=rememberBooleanSetting("reduce_motion")
@Composable
internal fun rememberAppleReduceTransparency():Boolean=rememberBooleanSetting("reduce_transparency")
