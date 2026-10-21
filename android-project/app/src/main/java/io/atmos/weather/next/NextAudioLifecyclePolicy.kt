package io.atmos.weather.next

/** Pure audio lifecycle gate; Android-specific ringer lookup stays in the controller. */
internal object NextAudioLifecyclePolicy {
    fun canPlay(foreground:Boolean,enabled:Boolean,normalRinger:Boolean):Boolean =
        foreground && enabled && normalRinger

    fun gain(foreground:Boolean,enabled:Boolean,normalRinger:Boolean,level:Float):Float =
        if(canPlay(foreground,enabled,normalRinger)) level.coerceIn(0f,.55f) else 0f
}
