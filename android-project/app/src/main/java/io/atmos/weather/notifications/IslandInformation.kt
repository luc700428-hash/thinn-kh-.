package io.atmos.weather.notifications
/** Only producer supplied values enter clocks/progress. No inferred warning numbers. */
data class IslandInformation(val title:String,val detail:String,val primary:String?=null,val deadlineMillis:Long?=null,val progress:Int?=null)
object IslandInformationPolicy {
 fun short(text:String,count:Int):String=text.substring(0,text.offsetByCodePoints(0,minOf(count.coerceAtLeast(0),text.codePointCount(0,text.length))))
 fun progress(value:Int,max:Int,indeterminate:Boolean):Int?=if(!indeterminate&&max>0&&value in 0..max)(value.toLong()*100/max).toInt() else null
 fun freshWeather(stamp:Long,now:Long,temperature:Int):Boolean=stamp>0&&now-stamp in 0..1_800_000L&&temperature in -50..60
 fun deadline(value:Long,now:Long):Long?=value.takeIf{it-now in 1..86_400_000L}
 fun label(kind:String)=when(kind){"ALARM_RINGING"->"鬧鐘";"TIMER_RUNNING"->"計時器";"SEISMIC_EEW"->"地震速報";"EARTHQUAKE_REPORT"->"地震報告";"APP_UPDATE"->"軟體更新";"TASK_REMINDER"->"事項提醒";"VERIFIED_MINUTE_RAIN"->"即時降雨";"AMBIENT_WEATHER","WEATHER_BRIEFING"->"目前天氣";"USER_TEST"->"測試";"STATION_DETECTION"->"測站檢知";else->"天氣警報"}
 fun weatherLabel(value:String)=when(value){"CLEAR"->"晴朗";"PARTLY_CLOUDY"->"晴時多雲";"CLOUDY"->"多雲";"RAIN"->"降雨";"THUNDERSTORM"->"雷雨";"SNOW"->"下雪";"FOG"->"有霧";"HAZE"->"霾";"DUST","DUST_STORM"->"揚塵";"SLEET"->"雨夾雪";"FREEZING_RAIN"->"凍雨";"HAIL"->"冰雹";else->"天氣資料"}
}
