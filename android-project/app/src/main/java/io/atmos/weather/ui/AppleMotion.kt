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
