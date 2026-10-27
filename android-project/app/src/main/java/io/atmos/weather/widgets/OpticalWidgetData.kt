package io.atmos.weather.widgets
import android.content.Context
import io.atmos.weather.alarm.WeatherAlarmStore
import io.atmos.weather.alarm.WeatherAlarmTime
import io.atmos.weather.task.TaskStore
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.location.LocationLabels
import org.json.JSONArray
import java.time.*
import java.time.format.DateTimeFormatter

data class WidgetHour(val time:String,val temperature:String,val rain:String,val code:Int?,val day:Boolean=true)
data class OpticalWidgetData(val city:String,val temperature:String,val condition:WeatherCondition,val day:Boolean,val stamp:Long,
 val age:WidgetPresentationRules.Freshness,val rain:String,val highLow:String,val humidity:String,val wind:String,val air:String,val uv:String,
 val hours:List<WidgetHour>,val alarm:String,val alarmAt:Long?,val task:String,val alert:String,val alertDetail:String,val alertRecent:Boolean){
 companion object{
  fun read(context:Context):OpticalWidgetData{
   val p=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE);val now=System.currentTimeMillis();val stamp=p.getLong("widget_time",0);val age=WidgetPresentationRules.freshness(minOf(stamp,p.getLong("widget_observed_time",stamp)),now)
   val available=age==WidgetPresentationRules.Freshness.FRESH||age==WidgetPresentationRules.Freshness.CACHED
   val zone=runCatching{ZoneId.of(p.getString("widget_zone",null))}.getOrDefault(ZoneId.systemDefault());val fmt=DateTimeFormatter.ofPattern("HH:mm")
   val hours=runCatching{val a=JSONArray(p.getString("widget_hours","[]"));(0 until a.length()).mapNotNull{i->val h=a.getJSONObject(i);val at=h.optLong("at");if(at<now-30*60_000L||at>now+36*60*60_000L)null else WidgetHour(Instant.ofEpochMilli(at).atZone(zone).format(fmt),h.optDouble("temp",Double.NaN).let{if(it.isFinite())"${it.toInt()}°" else "—"},h.optInt("rain",-1).let{if(it in 0..100)"$it%" else "—"},h.optInt("code",-1).takeIf{it>=0},h.optBoolean("day",p.getBoolean("widget_day",true)))}.take(6)}.getOrDefault(emptyList())
   val alarm=WeatherAlarmStore.load(context).filter{it.enabled}.mapNotNull{a->WeatherAlarmTime.next(a,ZonedDateTime.now())?.let{it to a}}.minByOrNull{it.first}
   val task=TaskStore.load(context).filter{!it.completed&&it.dueMillis()>=now}.minByOrNull{it.dueMillis()}
   val push=context.getSharedPreferences("thinn_push_widget",Context.MODE_PRIVATE);val issued=push.getLong("issued",0);val recent=issued>0&&now-issued in 0..3_600_000L
   fun int(key:String,unit:String)=p.getInt(key,-1).let{if(available&&it>=0)"$it$unit" else "—"}
   return OpticalWidgetData(LocationLabels.clean(p.getString("widget_city","臺灣天氣")?:"臺灣天氣"),WidgetPresentationRules.temperature(p.getInt("widget_temp",Int.MIN_VALUE),available),
    if(available)runCatching{WeatherCondition.valueOf(p.getString("widget_condition","UNKNOWN")?:"UNKNOWN")}.getOrDefault(WeatherCondition.UNKNOWN) else WeatherCondition.UNKNOWN,p.getBoolean("widget_day",true),stamp,age,int("widget_rain","%"),
    if(available&&p.contains("widget_high"))"最高 ${p.getInt("widget_high",0)}°  最低 ${p.getInt("widget_low",0)}°" else "高低溫尚未同步",int("widget_humidity","%"),p.getFloat("widget_wind",-1f).let{if(available&&it>=0)"%.1f m/s".format(it) else "—"},int("widget_air",""),p.getFloat("widget_uv",-1f).let{if(available&&it>=0)"%.1f".format(it) else "—"},if(available)hours else emptyList(),
    alarm?.let{it.first.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))+" · "+it.second.title}?:"尚未設定鬧鐘",alarm?.first?.toEpochMilli(),task?.let{"${it.month}/${it.day} ${"%02d:%02d".format(it.hour,it.minute)} · ${it.title}"}?:"沒有即將到來的事項",
    if(recent)push.getString("title","最近地震報告")?:"最近地震報告" else "尚無近期地震推播",if(recent)push.getString("detail","")?:"" else "請以官方災防告警為準",recent)
  }
 }
}
