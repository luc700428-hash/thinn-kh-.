package io.atmos.weather.widgets

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import android.text.format.DateFormat
import androidx.compose.runtime.*
import androidx.datastore.preferences.core.Preferences
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Rebuilt content tree; stable receiver identities upgrade placed widgets in situ. */
open class AtmosWidget(private val variant:Int):GlanceAppWidget() {
    override val sizeMode:SizeMode=SizeMode.Exact
    override val stateDefinition=PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context:Context,id:GlanceId) {
        provideContent {
            val revision=currentState<Preferences>()[WidgetSync.VIEW_REVISION]?:0L
            val size=LocalSize.current
            val data=remember(revision,size){OpticalWidgetData.read(context)}
            OpticalWidgetContent(context,variant,data)
        }
    }
}
class WidgetRefreshAction:ActionCallback {
    override suspend fun onAction(context:Context,glanceId:GlanceId,parameters:ActionParameters) {WidgetSync.request(context)}
}
/** Every installed receiver now uses the same responsive native regions. */
@Composable internal fun OpticalWidgetContent(context:Context,variant:Int,data:OpticalWidgetData){
    val size=LocalSize.current;val prefs=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
    val plan=WidgetLayoutPlan.create(size.width.value,size.height.value,prefs.getFloat("v4_fontscale",1f),context.resources.configuration.fontScale,WidgetTypography.read(context))
    val family=when(variant){25->5;26->6;27->7;28->8;else->variant/5}
    val index=when(variant){25->0;26->3;27,28->if(data.day)0 else 1;else->variant%5}
    val alert=data.alertRecent&&(family==4||family==5)
    val colors=WidgetGlassRenderer.palette(index,alert);val density=context.resources.displayMetrics.density
    val corner=minOf(28f,plan.width*.16f,plan.height*.16f)
    val bitmap=remember(size,index,colors,density,prefs.getFloat("v4_opacity",.66f),prefs.getFloat("v4_edge",.65f)){
        WidgetGlassRenderer.background((plan.width*density).toInt(),(plan.height*density).toInt(),index,
            prefs.getFloat("v4_edge",.65f),prefs.getFloat("v4_opacity",.66f),alert,corner*density)
    }
    val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val animated=prefs.getBoolean("widget_motion_enabled",true)&&!prefs.getBoolean("reduce_motion",false)&&!power.isPowerSaveMode&&
        runCatching{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}.getOrDefault(true)
    val open=Intent(context,MainActivity::class.java).apply{
        if(family==4||family==5)putExtra("open_earthquake_center",true)
        if(family in listOf(2,6,8))putExtra("open_weather_alarms",true)
    }
    val remote=remember(data,plan,colors,animated,family,DateFormat.is24HourFormat(context),prefs.getFloat("v4_fontweight",400f),prefs.getBoolean("widget_show_updated",true),prefs.getBoolean("widget_show_agenda",true)){
        adaptiveWidgetRemote(context,family,data,plan,colors,animated,prefs.getBoolean("widget_show_updated",true),prefs.getBoolean("widget_show_agenda",true))
    }
    Box(GlanceModifier.fillMaxSize().cornerRadius(corner.dp).clickable(actionStartActivity(open))){
        Image(ImageProvider(bitmap),null,GlanceModifier.fillMaxSize(),ContentScale.FillBounds)
        AndroidRemoteViews(remote,GlanceModifier.fillMaxSize().padding(plan.padding.dp))
    }
}
internal fun WeatherCondition.label()=when(this){
    WeatherCondition.CLEAR->"晴朗";WeatherCondition.PARTLY_CLOUDY->"晴時多雲";WeatherCondition.CLOUDY->"多雲";WeatherCondition.FOG->"有霧"
    WeatherCondition.HAZE->"霾";WeatherCondition.DUST,WeatherCondition.DUST_STORM->"揚塵";WeatherCondition.RAIN->"降雨";WeatherCondition.SNOW->"下雪"
    WeatherCondition.THUNDERSTORM->"雷雨";WeatherCondition.SLEET->"雨夾雪";WeatherCondition.FREEZING_RAIN->"凍雨";WeatherCondition.HAIL->"冰雹";else->"等待同步"
}
internal fun OpticalWidgetData.description()=io.atmos.weather.domain.model.WeatherDescriptions.label(condition,weatherCode,null,day)
