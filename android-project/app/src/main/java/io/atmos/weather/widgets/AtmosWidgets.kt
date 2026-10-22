package io.atmos.weather.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.atmos.weather.MainActivity
import io.atmos.weather.domain.model.WeatherCondition
import io.atmos.weather.domain.visual.WeatherScene
import io.atmos.weather.location.LocationLabels
import io.atmos.weather.visual.WeatherArtRenderer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 25 launchable entries; each shares the same active city/scene/weather snapshot as the app. */
open class AtmosWidget(private val variant: Int):GlanceAppWidget() {
    override val sizeMode:SizeMode=SizeMode.Exact
    override suspend fun provideGlance(context:Context,id:GlanceId) {
        val prefs=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE)
        val stamp=prefs.getLong("widget_time",0L)
        val freshness=WidgetFreshness.state(stamp,System.currentTimeMillis())
        val city=LocationLabels.clean(prefs.getString("widget_city","台灣 · 臺北") ?: "台灣 · 臺北")
        val temp=if(stamp<=0L) "--°" else "${prefs.getInt("widget_temp",0)}°"
        val rain=prefs.getInt("widget_rain",-1).let{if(it<0) "--" else "$it%"}
        val cond=runCatching{ WeatherCondition.valueOf(prefs.getString("widget_condition","UNKNOWN") ?: "UNKNOWN") }.getOrDefault(WeatherCondition.UNKNOWN)
        val chanceText = if(cond in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)) {
            val snowChance=prefs.getInt("widget_snow",-1)
            if (snowChance>=0) "$snowChance%" else "—"
        } else rain
        val scene=WeatherScene(
            wetness=if(cond in setOf(WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM,WeatherCondition.FREEZING_RAIN,WeatherCondition.HAIL)) .8f else 0f,
            snowAccumulation=if(cond in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)) .8f else 0f,
            nightLight=if(prefs.getBoolean("widget_day",true)) 0f else 1f,
            precipitationIntensity=if(cond in setOf(WeatherCondition.RAIN,WeatherCondition.SNOW,WeatherCondition.THUNDERSTORM,WeatherCondition.SLEET,WeatherCondition.FREEZING_RAIN,WeatherCondition.HAIL)) .7f else 0f,
            showLightning=cond==WeatherCondition.THUNDERSTORM,
            county=prefs.getString("widget_county", "") ?: "",
            locality=prefs.getString("widget_locality", "") ?: "")
        val visuals=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE)
        val fontScale=visuals.getFloat("v4_fontscale",1f).coerceIn(1f,1.5f)
        val fontWeight=visuals.getFloat("v4_fontweight",400f).coerceIn(100f,900f)
        val opacity=visuals.getFloat("v4_opacity",.66f).coerceIn(0f,1f)
        val edge=visuals.getFloat("v4_edge",.65f).coerceIn(0f,1f)
        provideContent { WeatherWidgetContent(context,variant,city,temp,chanceText,cond,scene,stamp,freshness,fontScale,fontWeight,opacity,edge) }
    }
}

@Composable
private fun WeatherWidgetContent(context:Context, variant:Int, city:String, temp:String, rain:String,
    condition:WeatherCondition,scene:WeatherScene, stamp:Long, freshness:WidgetFreshness.State,
    fontScale:Float,fontWeight:Float,opacity:Float,edge:Float) {
    val family=variant/5
    val palette=variant%5
    val viewSize=LocalSize.current
    val wide=viewSize.width>220.dp
    // Glance uses a bitmap because it cannot run a live 3D renderer or blur the launcher wallpaper.
    val lightPhase=((System.currentTimeMillis()/300_000L)%4).toInt()
    val bitmap=remember(variant,city,temp,condition,scene,viewSize,opacity,edge,lightPhase){
        WeatherArtRenderer.widgetBitmap(scene,condition,variant,glassOpacity=opacity,edgeLight=edge,
            lightPhase=lightPhase)
    }
    val mood=remember(condition,scene.nightLight,rain) {
        V25WidgetMood.forWeather(condition.name,scene.nightLight>.4f,
            rain.removeSuffix("%").toIntOrNull() ?: -1)
    }
    val widgetTypography = WidgetTypography(fontScale,fontWeight)
    val white=Color.White
    val soft=Color(0xFFE3F1F9)
    androidx.compose.runtime.CompositionLocalProvider(LocalWidgetTypography provides widgetTypography) {
    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context,MainActivity::class.java)))) {
        Image(provider=ImageProvider(bitmap),contentDescription="${city} ${condition.label()}",
            contentScale=ContentScale.FillBounds,modifier=GlanceModifier.fillMaxSize())
        Column(GlanceModifier.fillMaxSize().padding(if(wide) 15.dp else 10.dp)) {
            if(freshness != WidgetFreshness.State.RECENT) {
                WText(WidgetFreshness.detail(freshness),Color(0xFFFFD9A0),10,true)
            }
            when(family){
                0 -> {
                    WText("◈  thinn-khì  /  ${WidgetFreshness.label(freshness)}",soft,10,true)
                    Spacer(GlanceModifier.height(6.dp))
                    WText(city,white,if(wide) 14 else 11,true)
                    WText(temp,white,if(wide) 40 else 30,true)
                    WText(condition.label(),soft,12)
                    if(viewSize.height>100.dp){Spacer(GlanceModifier.height(8.dp));WText("${if(condition in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)) "降雪" else "降雨"} $rain",soft,11)}
                }
                1 -> {
                    WText("thinn-khì  ·  GLASS",soft,10,true)
                    Spacer(GlanceModifier.height(7.dp))
                    WText(city,white,12,true)
                    WText(temp,white,if(wide) 42 else 31,true)
                    WText("${condition.label()}    ☂ $rain",soft,11)
                }
                2 -> {
                    WText("◉  天氣儀表",soft,10,true)
                    Spacer(GlanceModifier.height(7.dp))
                    WText(city,white,12,true)
                    WText(temp,white,if(wide) 40 else 31,true)
                    WText("${if(condition in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)) "降雪" else "降雨"}機率  $rain",soft,11)
                    WText(condition.label(),soft,11)
                }
                3 -> {
                    WText(city,white,13,true)
                    Spacer(GlanceModifier.height(3.dp))
                    WText(temp,white,if(wide) 45 else 34,true)
                    WText(condition.label(),soft,11)
                }
                else -> {
                    WText("thinn-khì  ·  DAILY",soft,10,true)
                    Spacer(GlanceModifier.height(5.dp))
                    WText(city,white,12,true)
                    WText(temp,white,if(wide) 40 else 31,true)
                    WText("${condition.label()} / ${if(condition in setOf(WeatherCondition.SNOW,WeatherCondition.SLEET)) "降雪" else "降雨"} $rain",soft,11)
                }
            }
            if(viewSize.height>170.dp && wide) {
                Spacer(GlanceModifier.height(5.dp))
                WText("${mood.eyebrow} · ${mood.message}",soft,14)
            }
            if(viewSize.height>136.dp && wide){
                Spacer(GlanceModifier.height(6.dp))
                WText("${condition.label()}  ·  ${if(stamp>0) "下載 ${Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}" else "待同步"}",soft,10)
            }
        }
    }
}
}
private data class WidgetTypography(val scale:Float,val weight:Float)
private val LocalWidgetTypography = androidx.compose.runtime.compositionLocalOf { WidgetTypography(1f,400f) }

@Composable
private fun WText(t:String,color:Color,size:Int,bold:Boolean=false){
    val type=LocalWidgetTypography.current
    Text(t,maxLines=1,style=TextStyle(color=ColorProvider(color),
        fontSize=(size*type.scale).sp,
        fontWeight=if(bold || type.weight>=620f)FontWeight.Bold else FontWeight.Normal))
}
private fun WeatherCondition.label()=when(this){
    WeatherCondition.CLEAR->"晴朗";WeatherCondition.PARTLY_CLOUDY->"晴時多雲";
    WeatherCondition.CLOUDY->"多雲";WeatherCondition.FOG->"有霧";
    WeatherCondition.HAZE->"霾";WeatherCondition.DUST->"揚塵";WeatherCondition.DUST_STORM->"強烈揚塵";
    WeatherCondition.FREEZING_RAIN->"凍雨";WeatherCondition.SLEET->"雨夾雪";WeatherCondition.HAIL->"冰雹";
    WeatherCondition.RAIN->"降雨";WeatherCondition.SNOW->"下雪";
    WeatherCondition.THUNDERSTORM->"雷雨";else->"天氣更新中"
}
