package io.atmos.weather.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.DpSize
import io.atmos.weather.MainActivity

class SafetyWidget:GlanceAppWidget() {
    override val sizeMode=SizeMode.Responsive(setOf(DpSize(150.dp,90.dp),DpSize(245.dp,115.dp),DpSize(320.dp,180.dp)))
    override suspend fun provideGlance(context:Context,id:GlanceId) {
        val weather=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE)
        val push=context.getSharedPreferences("thinn_push_widget",Context.MODE_PRIVATE)
        val age=System.currentTimeMillis()-push.getLong("issued",0L)
        val recent=push.getLong("issued",0L)>0 && age in 0..(60L*60_000L)
        val temp=if(weather.getLong("widget_time",0)>0) "${weather.getInt("widget_temp",0)}°" else "—"
        val city=weather.getString("widget_city","臺灣天氣")?:"臺灣天氣"
        val notice=if(recent) push.getString("title","最近地震事件") ?: "最近地震事件" else "目前無近期地震推播"
        val last=if(recent) push.getString("detail","請注意安全")?:"請注意安全" else "以官方災防告警為準"
        provideContent {SafetyWidgetView(context,city,temp,notice,last,recent)}
    }
}
class SafetyWidgetReceiver:GlanceAppWidgetReceiver() {override val glanceAppWidget:GlanceAppWidget=SafetyWidget()}
@Composable
private fun SafetyWidgetView(context:Context, city:String,temp:String,notice:String,detail:String, recent:Boolean){
    val size=LocalSize.current
    val wide=size.width>=235.dp
    val bg=ColorProvider(if(recent)Color(0xFF4C2630) else Color(0xFF123F5E))
    Column(GlanceModifier.fillMaxSize().background(bg)
        .clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("open_earthquake_center",true)))
        .padding(14.dp)) {
        Text("thinn-khì  ·  天氣與安心",style=TextStyle(color=ColorProvider(Color(0xFFDAF0FA)),fontSize=14.sp))
        Spacer(GlanceModifier.height(7.dp))
        Row(verticalAlignment=Alignment.Vertical.CenterVertically) {
            Text(temp,style=TextStyle(color=ColorProvider(Color.White),fontSize=if(wide)27.sp else 22.sp,fontWeight=FontWeight.Bold))
            Spacer(GlanceModifier.width(10.dp))
            Text(city, maxLines=1,style=TextStyle(color=ColorProvider(Color.White),fontSize=14.sp))
        }
        Spacer(GlanceModifier.height(7.dp))
        Text(if(recent) "● $notice" else "○ $notice",maxLines=1,
            style=TextStyle(color=ColorProvider(if(recent)Color(0xFFFFDB9E) else Color(0xFFDCF2FA)),fontSize=14.sp,fontWeight=FontWeight.Medium))
        if(size.height>=140.dp){
           Spacer(GlanceModifier.height(5.dp))
           Text(detail,maxLines=2,style=TextStyle(color=ColorProvider(Color(0xFFECF4F9)),fontSize=14.sp))
        }
    }
}
