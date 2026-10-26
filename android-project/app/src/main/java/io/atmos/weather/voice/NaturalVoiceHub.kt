package io.atmos.weather.voice

import android.content.Context
import java.io.File

/** Retired optional cloud narration module. Kept as a migration boundary for old workers.
 * No audio queue, Firebase authentication, HTTP or cloud TTS remains here.
 * Independent alarm tones and earthquake alert audio keep their own service lifecycles.
 */
object NaturalVoiceHub {
    enum class Priority(val rank:Int) { EARTHQUAKE(0), WEATHER_ALERT(1), DAILY(2), PERSONAL(3) }
    data class Request(val text:String,val priority:Priority,val dedupeKey:String,
        val privacySensitive:Boolean=false,val cacheable:Boolean=true,
        val alarmAudio:Boolean=false,val onFinished:(()->Unit)?=null,val onFailure:(()->Unit)?=null)
    fun enabled(@Suppress("UNUSED_PARAMETER") ctx:Context)=false
    fun offer(@Suppress("UNUSED_PARAMETER") ctx:Context,@Suppress("UNUSED_PARAMETER") req:Request)=false
    fun interruptForEarthquake()=Unit
    fun stopAlarm()=Unit
    /** Previously enabled installs must not retain cloud consent or cached narration. */
    fun retire(ctx:Context) {
        ctx.getSharedPreferences("thinn_natural_voice",Context.MODE_PRIVATE).edit().clear().apply()
        File(ctx.cacheDir,"natural_voice_v1").deleteRecursively()
    }
}
