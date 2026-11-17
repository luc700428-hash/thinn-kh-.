package io.atmos.weather.next
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.atmos.weather.alarm.AlarmWeatherReading
import io.atmos.weather.alarm.R10AlarmTones
import io.atmos.weather.alarm.WeatherAlarmNarration

/**
 * Twenty weather alarm themes on the alarm stream, each with a clear / rain / snow / neutral
 * arrangement from the r22 soft sound library (res/raw Ogg loops), a gentle 18 s rise and the
 * matching nature ambience underneath when the weather is known.
 */
class NextWeatherAlarmPlayer private constructor(private val melody:MediaPlayer,private val ambience:NextLoopTrack?,private val scene:WeatherScene?,private val preview:Boolean){
 companion object{
  fun scene(context:Context,reading:AlarmWeatherReading?):WeatherScene?{
   val reliable=reading?.takeIf{it.fetchedEpochMs>0&&System.currentTimeMillis()-it.fetchedEpochMs in 0L..WeatherAlarmNarration.MAX_WEATHER_AGE_MS}?:return null
   val p=context.getSharedPreferences("thinn_alarms",Context.MODE_PRIVATE)
   val code=p.getInt("next_weather_code",-1).takeIf{it>=0&&p.getLong("next_weather_code_fetched",-1L)==reliable.fetchedEpochMs}
   val visibility=p.getFloat("next_weather_visibility",-1f).takeIf{it>=0f&&p.getLong("next_weather_code_fetched",-1L)==reliable.fetchedEpochMs}?.toDouble()
   return WeatherScenes.fromCachedName(reliable.condition,code,visibility)
  }
  /** The soft loop for [theme] in this weather; null for anything that is not a weather theme. */
  fun musicResource(theme:String,reading:AlarmWeatherReading?):Int?=
   if(theme in WeatherWakePolicy.themes)R10AlarmTones.resource(R10AlarmTones.prefix+theme,reading,System.currentTimeMillis()) else null
  fun start(context:Context,ringtoneUri:String?,reading:AlarmWeatherReading?,preview:Boolean=false):NextWeatherAlarmPlayer?{
   val theme=WeatherWakePolicy.theme(ringtoneUri)?:return null;val scene=scene(context,reading)
   val res=musicResource(theme,reading)?:return null
   val music=runCatching{
    MediaPlayer().apply{
     setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
     context.resources.openRawResourceFd(res).use{fd->setDataSource(fd.fileDescriptor,fd.startOffset,fd.length)}
     isLooping=true;setVolume(0f,0f);prepare()
    }
   }.getOrNull()?:return null
   val nature=scene?.let{NextLoopTrack.open(context,WeatherScenes.ambienceAsset(it),AudioAttributes.USAGE_ALARM)}
   return try{music.start();nature?.start(0f);NextWeatherAlarmPlayer(music,nature,scene,preview).apply{begin()}}catch(_:Exception){runCatching{music.release()};nature?.close();null}
  }
 }
 private val handler=Handler(Looper.getMainLooper());private var started=SystemClock.elapsedRealtime();private var duck=1f;private var closed=false
 private fun volume(){val gain=if(preview).75f else WeatherWakePolicy.gain(SystemClock.elapsedRealtime()-started);val v=(gain*duck*.92f).coerceIn(0f,1f)
  runCatching{melody.setVolume(v,v)};ambience?.volume(gain*duck*WeatherWakePolicy.ambienceGain(scene))}
 private val fade=object:Runnable{override fun run(){if(closed)return;if(preview&&SystemClock.elapsedRealtime()-started>=40_000){close();return};volume();handler.postDelayed(this,if(SystemClock.elapsedRealtime()-started<18_000)80L else 250L)}}
 private fun begin(){started=SystemClock.elapsedRealtime();fade.run()}
 val isPlaying get()=!closed&&runCatching{melody.isPlaying}.getOrDefault(false)
 fun setMasterVolume(value:Float){duck=value.coerceIn(0f,1f);if(!closed)volume()}
 fun close(){if(closed)return;closed=true;handler.removeCallbacks(fade);runCatching{melody.stop()};runCatching{melody.release()};ambience?.close()}
}
