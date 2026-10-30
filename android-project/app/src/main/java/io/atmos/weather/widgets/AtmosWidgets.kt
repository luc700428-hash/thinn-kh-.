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
private class WidgetStyle(val plan:WidgetLayoutPlan,val colors:WidgetGlassRenderer.Palette,val bold:Boolean) {
    val ink=Color(colors.ink);val soft=Color(colors.secondary);val accent=Color(colors.accent)
}
@Composable internal fun OpticalWidgetContent(context:Context,variant:Int,data:OpticalWidgetData) {
    val size=LocalSize.current
    val prefs=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
    val plan=WidgetLayoutPlan.create(size.width.value,size.height.value,prefs.getFloat("v4_fontscale",1f),context.resources.configuration.fontScale)
    val family=when(variant){25->5;26->6;27->7;28->8;else->variant/5}
    val index=when(variant){25->0;26->3;27,28->if(data.day)0 else 1;else->variant%5}
    val alert=data.alertRecent&&(family==4||family==5)
    val palette=WidgetGlassRenderer.palette(index,alert)
    val style=WidgetStyle(plan,palette,prefs.getFloat("v4_fontweight",400f)>=620f)
    val density=context.resources.displayMetrics.density
    val cornerDp=minOf(28f,plan.width*.16f,plan.height*.16f)
    val bitmap=remember(size,index,palette,density,prefs.getFloat("v4_opacity",.66f),prefs.getFloat("v4_edge",.65f)) {
        WidgetGlassRenderer.background((plan.width*density).toInt(),(plan.height*density).toInt(),index,
            prefs.getFloat("v4_edge",.65f),prefs.getFloat("v4_opacity",.66f),alert,cornerRadiusPx=cornerDp*density)
    }
    val power=context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val animated=prefs.getBoolean("widget_motion_enabled",true)&&!prefs.getBoolean("reduce_motion",false)&&!power.isPowerSaveMode&&
        runCatching {Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}.getOrDefault(true)
    val open=Intent(context,MainActivity::class.java).apply {
        if(family==4||family==5)putExtra("open_earthquake_center",true)
        if(family==2||family==6)putExtra("open_weather_alarms",true)
    }
    Box(GlanceModifier.fillMaxSize().cornerRadius(cornerDp.dp).clickable(actionStartActivity(open))) {
        Image(ImageProvider(bitmap),null,GlanceModifier.fillMaxSize(),ContentScale.FillBounds)
        if(family==8) {
            val remote=remember(data,plan,palette,animated){buddyClockRemote(context,data,plan,palette,animated)}
            AndroidRemoteViews(remote,GlanceModifier.fillMaxSize().padding(plan.padding.dp))
        } else Column(GlanceModifier.fillMaxSize().padding(plan.padding.dp)) {
            if(family==7) BuddyContent(context,data,style,animated)
            else if(plan.strip) CompactWidget(context,family,data,style,animated)
            else when(family) {
                1->ForecastWidget(context,data,style)
                2->DigitalClockWidget(context,data,style,animated)
                3->MetricsWidget(context,data,style,animated)
                4,5->SafetyContent(context,data,style,animated)
                6->PrecisionClockWidget(context,data,style,animated)
                else->CurrentWidget(context,data,style,animated)
            }
            if(plan.showFooter&&prefs.getBoolean("widget_show_updated",true)) {
                Spacer(GlanceModifier.defaultWeight());Footer(data,style)
            }
        }
    }
}
@Composable private fun BuddyContent(context:Context,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean) {
    val p=s.plan
    val location=io.atmos.weather.location.LocationLabels.microcity(d.city).ifBlank{d.city}
    val shortRange=d.highLow.replace("最高 ","高").replace("最低 ","低")
    val visualScale=minOf(p.innerWidth/300f,p.innerHeight/230f).coerceIn(1f,1.6f)
    val glyphs=location.sumOf {if(it.code>=0x2e80)1.0 else .58}.toFloat().coerceAtLeast(1f)
    val citySize=minOf(14f*visualScale,maxOf(p.city,14f*visualScale),p.innerWidth/glyphs).coerceAtLeast(10f)
    val cityLines=if(glyphs*citySize>p.innerWidth)2 else 1
    val headerHeight=citySize*1.5f*cityLines
    val descSize=minOf(14f,p.body)*visualScale;val captionSize=minOf(11f,p.caption)*visualScale
    val footer=if(p.showFooter)27f else 0f
    if(p.innerHeight<94f) {
        Row(GlanceModifier.fillMaxSize(),verticalAlignment=Alignment.Vertical.CenterVertically) {
            BuddyScene(context,d,minOf(p.innerHeight,p.innerWidth*.36f),motion)
            Spacer(GlanceModifier.width(6.dp))
            Column(GlanceModifier.defaultWeight(),verticalAlignment=Alignment.Vertical.CenterVertically) {
                WText(location.substringBefore(" · "),s,s.soft,minOf(12f,p.innerHeight*.23f))
                WText(d.temperature,s,s.ink,minOf(32f,p.innerHeight*.48f),true)
                if(p.innerWidth>=210f)WText(d.description(),s,s.soft,p.caption)
            }
        }
        return
    }
    WText(location,s,s.ink,citySize,true,lines=cityLines)
    Spacer(GlanceModifier.height(4.dp))
    if(p.innerWidth/p.innerHeight>=1.35f) {
        val scene=minOf(p.innerHeight-headerHeight-footer-10f,p.innerWidth*.44f).coerceAtLeast(35f)
        val temperatureSize=minOf(72f*visualScale,p.hero*1.25f*visualScale,(p.innerWidth-scene-8f)/(d.temperature.length*.60f))
        Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
            BuddyScene(context,d,scene,motion);Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                WText(d.temperature,s,s.ink,temperatureSize,true)
                WText(d.description(),s,s.soft,descSize,lines=1)
                if(p.innerHeight>=130f){Spacer(GlanceModifier.height(4.dp));WText(shortRange,s,s.soft,captionSize)}
                if(p.innerHeight>=230f){Spacer(GlanceModifier.height(7.dp));LiveClock(context,s,28f,false,p.innerWidth*.48f)}
            }
        }
        val textHeight=temperatureSize*1.5f+descSize*1.5f+(if(p.innerHeight>=130f)4f+captionSize*1.5f else 0f)+(if(p.innerHeight>=230f)51f else 0f)
        val remaining=p.innerHeight-headerHeight-4f-maxOf(scene,textHeight)-footer
        if(remaining>=105f&&d.hours.isNotEmpty()){Spacer(GlanceModifier.height(10.dp));HourlyRow(context,d,s,remaining-10f)}
        else if(remaining>=60f){Spacer(GlanceModifier.height(10.dp));StatRow(listOf("降雨" to d.rain,"風速" to d.wind),s)}
    } else if(p.innerHeight>=230f) {
        val clock=p.innerHeight>=280f
        val stats=p.innerHeight>=375f
        val hourly=p.innerHeight>=500f
        val valueSize=minOf(52f,p.innerWidth*.27f)
        val rowHeight=maxOf(valueSize*1.5f,descSize*1.5f+captionSize*1.6f)
        val reserve=headerHeight+rowHeight+(if(clock)54f else 0f)+(if(stats)55f else 0f)+(if(hourly)100f else 0f)+footer+23f
        val scene=minOf(p.innerWidth*.90f,p.innerHeight-reserve,280f).coerceAtLeast(40f)
        Box(GlanceModifier.fillMaxWidth(),contentAlignment=Alignment.Center){BuddyScene(context,d,scene,motion)}
        Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
            WText(d.temperature,s,s.ink,valueSize,true);Spacer(GlanceModifier.width(9.dp))
            Column(GlanceModifier.defaultWeight()) {
                WText(d.description(),s,s.soft,descSize,lines=2);WText(shortRange,s,s.soft,captionSize,lines=2)
            }
        }
        if(clock){Spacer(GlanceModifier.height(6.dp));LiveClock(context,s,minOf(30f,p.innerWidth/4.1f),false)}
        if(stats){Spacer(GlanceModifier.height(10.dp));StatRow(listOf("降雨" to d.rain,"風速" to d.wind),s)}
        if(hourly){Spacer(GlanceModifier.height(10.dp));HourlyRow(context,d,s)}
    } else {
        val clock=p.innerHeight>=180f
        val reserve=headerHeight+descSize*1.5f+captionSize*1.6f+footer+16f+(if(clock)46f else 0f)
        val scene=minOf(p.innerWidth*.60f,p.innerHeight-reserve).coerceAtLeast(28f)
        Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
            BuddyScene(context,d,scene,motion)
            WText(d.temperature,s,s.ink,minOf(42f,(p.innerWidth-scene)/(d.temperature.length*.60f)),true,GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(2.dp));WText(d.description(),s,s.soft,descSize)
        Spacer(GlanceModifier.height(3.dp));WText(shortRange,s,s.soft,captionSize)
        if(clock){Spacer(GlanceModifier.height(6.dp));LiveClock(context,s,25f,false)}
    }
}
@Composable private fun CompactWidget(context:Context,family:Int,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean) {
    val p=s.plan
    if((family==2||family==6)&&p.innerWidth<150f) {
        Column(GlanceModifier.fillMaxSize(),verticalAlignment=Alignment.Vertical.CenterVertically) {
            LiveClock(context,s,minOf(30f,(p.innerHeight-minOf(p.caption,p.innerHeight*.24f)*1.5f)/1.55f),false)
            WText("${d.temperature} · ${d.description()}",s,s.soft,minOf(p.caption,p.innerHeight*.24f))
        }
        return
    }
    Row(GlanceModifier.fillMaxSize(),verticalAlignment=Alignment.Vertical.CenterVertically) {
        val icon=minOf(p.icon,p.innerWidth*.30f)
        if(family==6) AnalogFace(context,minOf(p.innerHeight,58f,icon)) else WeatherGlyph(context,d.condition,d.day,icon,motion)
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight(),verticalAlignment=Alignment.Vertical.CenterVertically) {
            if(family==2||family==6) {
                LiveClock(context,s,minOf(32f,p.innerHeight*.54f),false,p.innerWidth-icon-8f)
                WText("${d.temperature}  ${d.description()}",s,s.soft,minOf(p.caption,p.innerHeight*.24f))
            } else if((family==4||family==5)&&d.alertRecent) {
                WText("最新地震",s,s.soft,p.caption);WText(d.alert,s,s.ink,p.body,true)
            } else {
                WText(d.city,s,s.soft,minOf(p.city,p.innerHeight*.27f))
                Row(verticalAlignment=Alignment.Vertical.Bottom) {
                    WText(d.temperature,s,s.ink,minOf(p.hero,(p.innerHeight-minOf(p.city,p.innerHeight*.27f)*1.5f)/1.5f,(p.innerWidth-icon-8f)/(d.temperature.length*.60f)))
                    if(p.innerWidth>=240f) {Spacer(GlanceModifier.width(8.dp));WText(d.description(),s,s.soft,p.body)}
                }
            }
        }
    }
}
@Composable private fun Header(d:OpticalWidgetData,s:WidgetStyle,label:String?=null) {
    Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
        WText(d.city,s,s.ink,s.plan.city,true,GlanceModifier.defaultWeight())
        if(label!=null&&s.plan.wide) {Spacer(GlanceModifier.width(8.dp));WText(label,s,s.soft,s.plan.caption)}
    }
}
@Composable private fun Hero(context:Context,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean,compact:Boolean=false) {
    val p=s.plan
    val compactLimit=if(p.height<185f)34f else 40f
    val glyph=minOf(if(compact)compactLimit+6f else p.icon,p.innerWidth*.42f)
    val temperatureSize=minOf(if(compact)minOf(compactLimit,p.hero) else p.hero,(p.innerWidth-glyph)/(d.temperature.length*.60f))
    Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            WText(d.temperature,s,s.ink,temperatureSize)
            if(!compact)WText(d.description(),s,s.soft,p.body)
        }
        WeatherGlyph(context,d.condition,d.day,glyph,motion)
    }
}
@Composable private fun CurrentWidget(context:Context,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean) {
    val p=s.plan
    Header(d,s);Spacer(GlanceModifier.height(5.dp));Hero(context,d,s,motion)
    if(p.showHighLow) {Spacer(GlanceModifier.height(5.dp));WText(d.highLow,s,s.soft,p.caption)}
    if(p.height>=340f&&d.hours.isNotEmpty()) {Spacer(GlanceModifier.height(12.dp));HourlyRow(context,d,s)}
    else if(p.height>=235f) {Spacer(GlanceModifier.height(10.dp));StatRow(listOf("降雨" to d.rain,"濕度" to d.humidity),s)}
}
@Composable private fun ForecastWidget(context:Context,d:OpticalWidgetData,s:WidgetStyle) {
    val p=s.plan
    Header(d,s,"逐時天氣");Spacer(GlanceModifier.height(5.dp))
    WText("${d.temperature}  ${d.description()}",s,s.soft,p.body)
    val gap=if(p.height>=210f)14f else 7f
    val later=(if(p.height>=310f)14f+p.caption*1.5f+3f+p.body*1.25f*1.5f else 0f)+(if(p.showFooter)p.caption*1.5f+9f else 0f)
    val hourlyBudget=p.innerHeight-p.city*1.5f-5f-p.body*1.5f-gap-later
    Spacer(GlanceModifier.height(gap.dp));HourlyRow(context,d,s,hourlyBudget)
    if(p.height>=310f) {Spacer(GlanceModifier.height(14.dp));StatRow(listOf("降雨" to d.rain,"風速" to d.wind),s)}
}
@Composable private fun HourlyRow(context:Context,d:OpticalWidgetData,s:WidgetStyle,availableHeight:Float=Float.MAX_VALUE) {
    val p=s.plan
    val temperatureSize=minOf(20f,p.body*1.4f)
    val textHeight=p.caption*1.5f+temperatureSize*1.5f+8f
    val idealGlyph=if(p.height>=210f)32f else 25f
    val rain=p.height>=172f&&textHeight+idealGlyph+3f+p.caption*1.5f<=availableHeight
    val glyph=minOf(idealGlyph,(availableHeight-textHeight-(if(rain)3f+p.caption*1.5f else 0f)).coerceAtLeast(12f))
    if(d.hours.isEmpty()) {WText("開啟 App 同步逐時預報",s,s.soft,p.body);return}
    Row(GlanceModifier.fillMaxWidth()) {
        d.hours.take(p.hours).forEach {h->
            Column(GlanceModifier.defaultWeight(),horizontalAlignment=Alignment.Horizontal.CenterHorizontally) {
                WText(h.time,s,s.soft,p.caption);Spacer(GlanceModifier.height(4.dp))
                WeatherGlyph(context,WidgetWeatherArt.condition(h.code),h.day,glyph,false)
                Spacer(GlanceModifier.height(4.dp));WText(h.temperature,s,s.ink,temperatureSize,true)
                if(rain) {Spacer(GlanceModifier.height(3.dp));WText(h.rain,s,s.accent,p.caption)}
            }
        }
    }
}
@Composable private fun DigitalClockWidget(context:Context,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean) {
    val p=s.plan
    val date=p.height>=164f
    val alarm=p.height>=280f
    val reserve=p.city*1.5f+6f+7f+maxOf(28f,p.body*1.5f)+(if(alarm)12f+p.caption*1.5f+p.body*1.5f else 0f)+
        (if(p.showFooter)p.caption*1.5f+9f else 0f)+(if(p.showAgenda)24f+8f+p.caption*1.5f else 0f)
    val clockSize=minOf(p.clock,((p.innerHeight-reserve-(if(date)p.caption*1.5f else 0f))/1.55f).coerceAtLeast(12f))
    Header(d,s,"本機時間");Spacer(GlanceModifier.height(6.dp));LiveClock(context,s,clockSize,date)
    Spacer(GlanceModifier.height(7.dp))
    Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
        WeatherGlyph(context,d.condition,d.day,28f,motion);Spacer(GlanceModifier.width(5.dp))
        WText("${d.temperature}  ${d.description()}",s,s.ink,p.body)
    }
    if(alarm) {Spacer(GlanceModifier.height(12.dp));WText("下一個鬧鐘",s,s.soft,p.caption);WText(d.alarm,s,s.ink,p.body,true)}
    val base=d.alarmAt?.let{WidgetPresentationRules.countdownBase(it,System.currentTimeMillis(),SystemClock.elapsedRealtime())}
    if(p.showAgenda&&base!=null) {
        val timer=RemoteViews(context.packageName,R.layout.widget_live_countdown).apply {
            setChronometer(R.id.widget_countdown,base,null,true);setChronometerCountDown(R.id.widget_countdown,true);setTextColor(R.id.widget_countdown,s.colors.accent)
        }
        AndroidRemoteViews(timer,GlanceModifier.fillMaxWidth().height(24.dp))
    }
    if(p.showAgenda&&context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getBoolean("widget_show_agenda",true)) {
        Spacer(GlanceModifier.height(8.dp));WText(d.task,s,s.soft,p.caption)
    }
}
@Composable private fun MetricsWidget(context:Context,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean) {
    val p=s.plan
    Header(d,s,"氣象概況");Spacer(GlanceModifier.height(5.dp));Hero(context,d,s,motion,true)
    Spacer(GlanceModifier.height(6.dp));StatRow(listOf("濕度" to d.humidity,"風速" to d.wind),s)
    if(p.height>=255f) {Spacer(GlanceModifier.height(12.dp));StatRow(listOf("空氣 AQI" to d.air,"紫外線" to d.uv),s)}
    if(p.height>=430f) {Spacer(GlanceModifier.height(14.dp));HourlyRow(context,d,s)}
}
@Composable private fun StatRow(values:List<Pair<String,String>>,s:WidgetStyle) {
    Row(GlanceModifier.fillMaxWidth()) {
        values.forEach {(label,value)->Column(GlanceModifier.defaultWeight().padding(end=4.dp)) {
            WText(label,s,s.soft,s.plan.caption);Spacer(GlanceModifier.height(3.dp))
            val fitted=minOf(s.plan.body*1.2f,(s.plan.innerWidth/values.size-6f)/(value.length*.60f))
            WText(value,s,s.ink,fitted,true)
        }}
    }
}
@Composable private fun SafetyContent(context:Context,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean) {
    val p=s.plan
    WText(if(d.alertRecent)"最新地震報告" else "天氣與安心",s,s.ink,p.city,true)
    Spacer(GlanceModifier.height(6.dp));Hero(context,d,s,motion,true);Spacer(GlanceModifier.height(8.dp))
    WText(d.alert,s,s.ink,p.body,true,lines=if(p.height>=220f)2 else 1)
    if(p.height>=250f) {Spacer(GlanceModifier.height(6.dp));WText(d.alertDetail,s,s.soft,p.caption,lines=2)}
    if(p.height>=350f) {Spacer(GlanceModifier.height(12.dp));WText(d.highLow,s,s.soft,p.body)}
}
@Composable private fun PrecisionClockWidget(context:Context,d:OpticalWidgetData,s:WidgetStyle,motion:Boolean) {
    val p=s.plan
    if(p.wide) {
        Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
            AnalogFace(context,minOf(p.innerHeight-(if(p.showFooter)28f else 0f),p.innerWidth*.46f,210f))
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                WText(d.city,s,s.ink,p.city,true)
                LiveClock(context,s,minOf(if(p.height<220f)32f else 42f,p.innerWidth*.14f),p.height>=220f)
                Spacer(GlanceModifier.height(8.dp));WeatherGlyph(context,d.condition,d.day,if(p.height<220f)24f else 32f,motion)
                WText("${d.temperature}  ${d.description()}",s,s.ink,p.body)
            }
        }
        if(p.height>=410f) {Spacer(GlanceModifier.height(12.dp));HourlyRow(context,d,s)}
    } else {
        WText(d.city,s,s.ink,p.city,true);Spacer(GlanceModifier.height(5.dp))
        Box(GlanceModifier.fillMaxWidth(),contentAlignment=Alignment.Center) {AnalogFace(context,p.dial.coerceAtMost(220f))}
        Spacer(GlanceModifier.height(5.dp));WText("${d.temperature}  ${d.description()}",s,s.ink,p.body)
    }
}
@Composable private fun LiveClock(context:Context,s:WidgetStyle,size:Float,date:Boolean,availableWidth:Float=s.plan.innerWidth) {
    val p=s.plan
    val fitted=minOf(size,availableWidth/(if(DateFormat.is24HourFormat(context))3.05f else 5.4f))
    val remote=RemoteViews(context.packageName,R.layout.widget_live_clock).apply {
        setTextColor(R.id.widget_clock,s.colors.ink)
        setTextViewTextSize(R.id.widget_clock,android.util.TypedValue.COMPLEX_UNIT_SP,p.sp(fitted))
        setTextColor(R.id.widget_clock_date,s.colors.secondary)
        setTextViewTextSize(R.id.widget_clock_date,android.util.TypedValue.COMPLEX_UNIT_SP,p.sp(p.caption))
        setViewVisibility(R.id.widget_clock_date,if(date)View.VISIBLE else View.GONE)
        setCharSequence(R.id.widget_clock,"setFormat12Hour","h:mm a");setCharSequence(R.id.widget_clock,"setFormat24Hour","HH:mm")
    }
    AndroidRemoteViews(remote,GlanceModifier.fillMaxWidth().height((fitted*1.55f+if(date)p.caption*1.5f else 0f).dp))
}
@Composable private fun AnalogFace(context:Context,size:Float) {
    AndroidRemoteViews(RemoteViews(context.packageName,R.layout.widget_analog_clock),GlanceModifier.width(size.dp).height(size.dp))
}
@Composable private fun WeatherGlyph(context:Context,condition:WeatherCondition,day:Boolean,size:Float,motion:Boolean) {
    val px=(size*context.resources.displayMetrics.density).toInt().coerceIn(24,192)
    val glyph=RemoteViews(context.packageName,R.layout.widget_motion_glyph).apply {
        setImageViewBitmap(R.id.widget_glyph_a,WidgetWeatherArt.render(condition,day,px,0))
        setImageViewBitmap(R.id.widget_glyph_b,WidgetWeatherArt.render(condition,day,px,1))
        setImageViewBitmap(R.id.widget_glyph_static,WidgetWeatherArt.render(condition,day,px,0))
        setContentDescription(R.id.widget_glyph_motion,condition.label());setContentDescription(R.id.widget_glyph_static,condition.label())
        setViewVisibility(R.id.widget_glyph_motion,if(motion&&condition!=WeatherCondition.UNKNOWN)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_glyph_static,if(motion&&condition!=WeatherCondition.UNKNOWN)View.GONE else View.VISIBLE)
    }
    AndroidRemoteViews(glyph,GlanceModifier.width(size.dp).height(size.dp))
}
@Composable private fun Footer(d:OpticalWidgetData,s:WidgetStyle) {
    val stamp=if(d.stamp>0)Instant.ofEpochMilli(d.stamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) else "待同步"
    val label=when(d.age) {WidgetPresentationRules.Freshness.CACHED->"快取 · $stamp";WidgetPresentationRules.Freshness.EXPIRED->"資料已過期";WidgetPresentationRules.Freshness.MISSING->"等待同步";else->"更新 $stamp"}
    Row(GlanceModifier.fillMaxWidth().clickable(actionRunCallback<WidgetRefreshAction>()).padding(top=6.dp,bottom=2.dp),verticalAlignment=Alignment.Vertical.CenterVertically) {
        WText(label,s,s.soft,s.plan.caption,modifier=GlanceModifier.defaultWeight());WText("↻ 更新",s,s.accent,s.plan.caption,true)
    }
}
@Composable private fun WText(t:String,s:WidgetStyle,color:Color,size:Float,bold:Boolean=false,modifier:GlanceModifier=GlanceModifier,lines:Int=1) {
    Text(t,modifier,maxLines=lines,style=TextStyle(color=ColorProvider(color),fontSize=s.plan.sp(size).sp,fontWeight=if(bold||s.bold)FontWeight.Bold else FontWeight.Normal))
}
internal fun WeatherCondition.label()=when(this) {
    WeatherCondition.CLEAR->"晴朗";WeatherCondition.PARTLY_CLOUDY->"晴時多雲";WeatherCondition.CLOUDY->"多雲";WeatherCondition.FOG->"有霧"
    WeatherCondition.HAZE->"霾";WeatherCondition.DUST,WeatherCondition.DUST_STORM->"揚塵";WeatherCondition.RAIN->"降雨";WeatherCondition.SNOW->"下雪"
    WeatherCondition.THUNDERSTORM->"雷雨";WeatherCondition.SLEET->"雨夾雪";WeatherCondition.FREEZING_RAIN->"凍雨";WeatherCondition.HAIL->"冰雹";else->"等待同步"
}

internal fun OpticalWidgetData.description()=io.atmos.weather.domain.model.WeatherDescriptions.label(condition,weatherCode,null,day)
