package io.atmos.weather.next

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.atmos.weather.alarm.AlarmWeatherReading
import io.atmos.weather.alarm.WeatherAlarmNarration

/**
 * Two adaptive alarm themes using actual bundled audio: 14 melodic arrangements
 * per theme + independently looped matching ambience.
 * Existing alarm service, snooze, full-screen notification, TTS and vibration stay intact.
 */
class NextWeatherAlarmPlayer private constructor(
    private val melody: NextLoopTrack,
    private val ambience: NextLoopTrack
) {
    companion object {
        fun start(context: Context, ringtoneUri: String?, reading: AlarmWeatherReading?): NextWeatherAlarmPlayer? {
            val theme = when(ringtoneUri) {
                "thinn://alarm/dawn" -> "dawn"
                "thinn://alarm/rain" -> "rain"
                else -> return null
            }
            // Prefer an honest old-system fallback rather than assigning an incorrect sunny soundtrack.
            val reliable = reading?.takeIf {
                it.fetchedEpochMs > 0 && System.currentTimeMillis() - it.fetchedEpochMs in 0L..WeatherAlarmNarration.MAX_WEATHER_AGE_MS
            } ?: return null
            val prefs=context.getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE)
            val code=prefs.getInt("next_weather_code",-1).takeIf {it>=0}
            val codeWhen=prefs.getLong("next_weather_code_fetched",-1L)
            val scene=WeatherScenes.fromCachedName(reliable.condition, if(codeWhen==reliable.fetchedEpochMs) code else null) ?: return null
            val music=NextLoopTrack.open(context,WeatherScenes.musicAsset(theme,scene) ?: return null,
                AudioAttributes.USAGE_ALARM) ?: return null
            val nature=NextLoopTrack.open(context,WeatherScenes.ambienceAsset(scene),AudioAttributes.USAGE_ALARM)
                ?: run {music.close();return null}
            return try {
                music.start(0.0f)
                nature.start(0.0f)
                NextWeatherAlarmPlayer(music,nature).apply { beginFade() }
            } catch (_:Exception) {
                music.close(); nature.close(); null
            }
        }
    }
    private val handler=Handler(Looper.getMainLooper())
    private var startMs=SystemClock.elapsedRealtime()
    private var voiceDuck=1f
    private var closed=false
    private fun applyVolume() {
        val elapsed=(SystemClock.elapsedRealtime()-startMs).coerceAtLeast(0)
        val fraction=(elapsed/9000f).coerceIn(0f,1f)
        val gain=.08f + .92f*fraction*fraction*(3f-2f*fraction)
        melody.volume(gain*voiceDuck*.76f)
        ambience.volume(gain*voiceDuck*.22f)
    }
    private val fade=object:Runnable {
        override fun run() {
            if(closed) return
            applyVolume()
            if(SystemClock.elapsedRealtime()-startMs < 9000L) handler.postDelayed(this,120L)
        }
    }
    private fun beginFade() {startMs=SystemClock.elapsedRealtime();fade.run()}
    val isPlaying get()=melody.isPlaying
    fun setMasterVolume(volume:Float) {
        // TTS ducks both tracks, without disrupting the gentle wake-up envelope.
        voiceDuck=volume.coerceIn(0f,1f)
        applyVolume()
    }
    fun close() {closed=true;handler.removeCallbacks(fade);melody.close();ambience.close()}
}
