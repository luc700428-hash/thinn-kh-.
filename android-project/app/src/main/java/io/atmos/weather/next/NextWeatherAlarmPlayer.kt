package io.atmos.weather.next
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.atmos.weather.alarm.AlarmWeatherReading
import io.atmos.weather.alarm.WeatherAlarmNarration
/** Twenty musical palettes share the alarm stream, gentle rise and sample-accurate loops. */
class NextWeatherAlarmPlayer private constructor(private val melody:NextLoopTrack,private val ambience:NextLoopTrack?,private val scene:WeatherScene?,private val preview:Boolean){
 companion object{
  fun scene(context:Context,reading:AlarmWeatherReading?):WeatherScene?{
   val reliable=reading?.takeIf{it.fetchedEpochMs>0&&System.currentTimeMillis()-it.fetchedEpochMs in 0L..WeatherAlarmNarration.MAX_WEATHER_AGE_MS}?:return null
   val p=context.getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE)
   val code=p.getInt("next_weather_code",-1).takeIf{it>=0&&p.getLong("next_weather_code_fetched",-1L)==reliable.fetchedEpochMs}
   val visibility=p.getFloat("next_weather_visibility",-1f).takeIf{it>=0f&&p.getLong("next_weather_code_fetched",-1L)==reliable.fetchedEpochMs}?.toDouble()
   return WeatherScenes.fromCachedName(reliable.condition,code,visibility)
  }
  fun start(context:Context,ringtoneUri:String?,reading:AlarmWeatherReading?,preview:Boolean=false):NextWeatherAlarmPlayer?{
   val theme=WeatherWakePolicy.theme(ringtoneUri)?:return null;val scene=scene(context,reading);val path=WeatherScenes.musicAsset(theme,scene)?:return null
   val music=NextLoopTrack.open(context,path,AudioAttributes.USAGE_ALARM)?:return null
   val nature=scene?.let{NextLoopTrack.open(context,WeatherScenes.ambienceAsset(it),AudioAttributes.USAGE_ALARM)}
   return try{music.start(0f);nature?.start(0f);NextWeatherAlarmPlayer(music,nature,scene,preview).apply{begin()}}catch(_:Exception){music.close();nature?.close();null}
  }
 }
 private val handler=Handler(Looper.getMainLooper());private var started=SystemClock.elapsedRealtime();private var duck=1f;private var closed=false
 private fun volume(){val gain=if(preview).75f else WeatherWakePolicy.gain(SystemClock.elapsedRealtime()-started);melody.volume(gain*duck*.88f);ambience?.volume(gain*duck*WeatherWakePolicy.ambienceGain(scene))}
 private val fade=object:Runnable{override fun run(){if(closed)return;if(preview&&SystemClock.elapsedRealtime()-started>=40_000){close();return};volume();handler.postDelayed(this,if(SystemClock.elapsedRealtime()-started<18_000)80L else 250L)}}
 private fun begin(){started=SystemClock.elapsedRealtime();fade.run()}
 val isPlaying get()=!closed&&melody.isPlaying
 fun setMasterVolume(value:Float){duck=value.coerceIn(0f,1f);if(!closed)volume()}
 fun close(){if(closed)return;closed=true;handler.removeCallbacks(fade);melody.close();ambience?.close()}
}
