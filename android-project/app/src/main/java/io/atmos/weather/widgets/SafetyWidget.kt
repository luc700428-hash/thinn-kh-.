package io.atmos.weather.widgets
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.*
class SafetyWidget:GlanceAppWidget(){
 override val sizeMode:SizeMode=SizeMode.Exact
 override suspend fun provideGlance(context:Context,id:GlanceId){val data=OpticalWidgetData.read(context);provideContent{OpticalWidgetContent(context,25,data)}}
}
class SafetyWidgetReceiver:GlanceAppWidgetReceiver(){override val glanceAppWidget:GlanceAppWidget=SafetyWidget()}
