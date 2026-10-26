package io.atmos.weather.widgets
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.action.ActionParameters
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import io.atmos.weather.MainActivity
import io.atmos.weather.R
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.notifications.IslandIconClassifier
import io.atmos.weather.notifications.IslandIconScene
import io.atmos.weather.notifications.IslandMotionResources
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Existing 25 receiver identities are retained; every family has a new responsive layout. */
open class AtmosWidget(private val variant:Int):GlanceAppWidget(){
 override val sizeMode:SizeMode=SizeMode.Exact
 override suspend fun provideGlance(context:Context,id:GlanceId){
  val data=OpticalWidgetData.read(context)
  provideContent{OpticalWidgetContent(context,variant,data)}
 }
}
class WidgetRefreshAction:ActionCallback{
 override suspend fun onAction(context:Context,glanceId:GlanceId,parameters:ActionParameters){WidgetSync.request(context)}
}
@Composable internal fun OpticalWidgetContent(context:Context,variant:Int,data:OpticalWidgetData){
 val size=LocalSize.current;val width=size.width.value;val tall=size.height.value>=165;val wide=width>=230;val tiny=size.height.value<120
 val prefs=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
 val scale=prefs.getFloat("v4_fontscale",1f).coerceIn(1f,1.5f);val weight=prefs.getFloat("v4_fontweight",400f)
 val index=if(variant==25)2 else variant%5;val family=if(variant==25)5 else variant/5;val p=WidgetGlassRenderer.palette(index)
 val ink=Color(p.ink);val soft=Color(p.secondary);val accent=Color(p.accent)
 val density=context.resources.displayMetrics.density
 val bitmap=remember(size,index,prefs.getFloat("v4_opacity",.66f),prefs.getFloat("v4_edge",.65f)){
  WidgetGlassRenderer.background(WidgetPresentationRules.pixels(width,density),WidgetPresentationRules.pixels(size.height.value,density),index,prefs.getFloat("v4_edge",.65f),prefs.getFloat("v4_opacity",.66f))
 }
 val scene=when{data.condition==WeatherCondition.CLEAR&&!data.day->IslandIconScene.MOON;data.condition==WeatherCondition.FOG->IslandIconScene.FOG;else->IslandIconClassifier.choose("AMBIENT_WEATHER",data.condition.label(),"")}
 val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
 val animated=prefs.getBoolean("widget_motion_enabled",true)&&!prefs.getBoolean("reduce_motion",false)&&!power.isPowerSaveMode&&runCatching{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}.getOrDefault(true)
 val open=Intent(context,MainActivity::class.java).apply{if(family>=4)putExtra("open_earthquake_center",true)}
 Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).clickable(actionStartActivity(open))){
  Image(ImageProvider(bitmap),null,GlanceModifier.fillMaxSize(),ContentScale.FillBounds)
  Column(GlanceModifier.fillMaxSize().padding(if(tiny)8.dp else if(wide)16.dp else 12.dp)){
   Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically){
    Column(GlanceModifier.defaultWeight()){
     WText(if(family==5)"天氣與安心" else if(family==2)"本機時間" else (if(data.age==WidgetPresentationRules.Freshness.CACHED)"快取 · " else if(data.age==WidgetPresentationRules.Freshness.EXPIRED)"過期 · " else "")+data.city,ink,if(wide)16 else 14,true,scale,weight)
     if(family==2){
      val clock=RemoteViews(context.packageName,R.layout.widget_live_clock).apply{setTextColor(R.id.widget_clock,p.ink);setTextViewTextSize(R.id.widget_clock,android.util.TypedValue.COMPLEX_UNIT_SP,minOf(46*scale,(width-32)/2.85f))}
      AndroidRemoteViews(clock,GlanceModifier.fillMaxWidth().height(54.dp))
     }else WText(if(family>=4&&size.height.value/scale<155)data.alert else data.temperature,ink,if(family>=4&&size.height.value/scale<155)14 else if(tiny)28 else if(family==0)40 else 32,false,scale,weight)
    }
    val glyph=RemoteViews(context.packageName,R.layout.widget_motion_glyph).apply{
     setImageViewResource(R.id.widget_glyph_a,IslandMotionResources.drawable(scene,0));setImageViewResource(R.id.widget_glyph_b,IslandMotionResources.drawable(scene,1));setImageViewResource(R.id.widget_glyph_static,IslandMotionResources.drawable(scene,0))
     setViewVisibility(R.id.widget_glyph_motion,if(animated)View.VISIBLE else View.GONE);setViewVisibility(R.id.widget_glyph_static,if(animated)View.GONE else View.VISIBLE)
    }
    AndroidRemoteViews(glyph,GlanceModifier.width(if(wide)62.dp else 48.dp).height(if(wide)62.dp else 48.dp))
   }
   if(!tiny&&(family==0||family==2))WText(if(family==2)"${data.city} ${data.temperature} · ${data.condition.label()}" else data.condition.label(),soft,13,false,scale,weight)
   Spacer(GlanceModifier.height(8.dp))
   if(size.height.value/scale>=155)when(family){
    0->{WText(data.highLow,soft,12,false,scale,weight);if(size.height.value/scale>=220) {Spacer(GlanceModifier.height(8.dp));WText("降雨 ${data.rain}",accent,15,true,scale,weight)}}
    1->{
     if(data.hours.isEmpty())WText("小時預報尚未同步",soft,12,false,scale,weight)
     else Row(GlanceModifier.fillMaxWidth()) {data.hours.take(if(wide)4 else 3).forEach{h->Column(GlanceModifier.defaultWeight(),horizontalAlignment=Alignment.Horizontal.CenterHorizontally){WText(h.time,soft,11,false,scale,weight);Spacer(GlanceModifier.height(4.dp));WText(h.temperature,ink,18,true,scale,weight);WText(h.rain,accent,11,false,scale,weight)}}}
    }
    2->{
     WText("下一個鬧鐘",soft,11,false,scale,weight);WText(data.alarm,ink,13,true,scale,weight)
     val base=data.alarmAt?.let{WidgetPresentationRules.countdownBase(it,System.currentTimeMillis(),SystemClock.elapsedRealtime())}
     if(size.height.value/scale>=240&&base!=null){val timer=RemoteViews(context.packageName,R.layout.widget_live_countdown).apply{setChronometer(R.id.widget_countdown,base,null,true);setChronometerCountDown(R.id.widget_countdown,true);setTextColor(R.id.widget_countdown,p.accent)};AndroidRemoteViews(timer,GlanceModifier.fillMaxWidth().height(24.dp))}
     if(size.height.value/scale>=240&&prefs.getBoolean("widget_show_agenda",true)){Spacer(GlanceModifier.height(6.dp));WText(data.task,soft,12,false,scale,weight)}
    }
    3->{
     Row(GlanceModifier.fillMaxWidth()){Metric("濕度",data.humidity,ink,soft,scale,weight,GlanceModifier.defaultWeight());Metric("風速",data.wind,ink,soft,scale,weight,GlanceModifier.defaultWeight())}
     if(size.height.value/scale>=230){Spacer(GlanceModifier.height(8.dp));Row(GlanceModifier.fillMaxWidth()){Metric("AQI",data.air,ink,soft,scale,weight,GlanceModifier.defaultWeight());Metric("紫外線",data.uv,ink,soft,scale,weight,GlanceModifier.defaultWeight())}}
    }
    else->{WText(data.alert,if(data.alertRecent)Color(0xFFFFC1AD) else ink,13,true,scale,weight);if(tall)Text(data.alertDetail,maxLines=2,style=TextStyle(color=ColorProvider(soft),fontSize=(12*scale).sp))}
   }
   if(size.height.value/scale>=(if(family==2)210 else 180)&&prefs.getBoolean("widget_show_updated",true)){
    Spacer(GlanceModifier.defaultWeight())
    Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically){
     val stamp=if(data.stamp>0)Instant.ofEpochMilli(data.stamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) else "待同步"
     WText(when(data.age){WidgetPresentationRules.Freshness.CACHED->"快取 · $stamp";WidgetPresentationRules.Freshness.EXPIRED->"資料已過期";WidgetPresentationRules.Freshness.MISSING->"等待同步";else->"更新 $stamp"},soft,10,false,scale,weight,GlanceModifier.defaultWeight())
     Text("↻",modifier=GlanceModifier.clickable(actionRunCallback<WidgetRefreshAction>()).padding(6.dp),style=TextStyle(color=ColorProvider(accent),fontSize=18.sp))
    }
   }
  }
 }
}
@Composable private fun WText(t:String,color:Color,size:Int,bold:Boolean,scale:Float,weight:Float,modifier:GlanceModifier=GlanceModifier){Text(t,modifier,maxLines=1,style=TextStyle(color=ColorProvider(color),fontSize=(size*scale).sp,fontWeight=if(bold||weight>=620)FontWeight.Bold else FontWeight.Normal))}
@Composable private fun Metric(label:String,value:String,ink:Color,soft:Color,scale:Float,weight:Float,modifier:GlanceModifier){Column(modifier){WText(label,soft,11,false,scale,weight);WText(value,ink,16,true,scale,weight)}}
internal fun WeatherCondition.label()=when(this){WeatherCondition.CLEAR->"晴朗";WeatherCondition.PARTLY_CLOUDY->"晴時多雲";WeatherCondition.CLOUDY->"多雲";WeatherCondition.FOG->"有霧";WeatherCondition.HAZE->"霾";WeatherCondition.DUST,WeatherCondition.DUST_STORM->"揚塵";WeatherCondition.RAIN->"降雨";WeatherCondition.SNOW->"下雪";WeatherCondition.THUNDERSTORM->"雷雨";WeatherCondition.SLEET->"雨夾雪";WeatherCondition.FREEZING_RAIN->"凍雨";WeatherCondition.HAIL->"冰雹";else->"天氣更新中"}
