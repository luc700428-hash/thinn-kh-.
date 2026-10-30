package io.atmos.weather.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import io.atmos.weather.R
import kotlin.math.min

class BuddyClockWidget:AtmosWidget(28)
class BuddyClockWidgetReceiver:SyncedWidgetReceiver(){override val glanceAppWidget=BuddyClockWidget()}

/** Native TextClock ticks independently of weather workers; native vector art stays sharp on resize. */
internal fun buddyClockRemote(context:Context,d:OpticalWidgetData,p:WidgetLayoutPlan,
    colors:WidgetGlassRenderer.Palette,motion:Boolean):RemoteViews {
    val w=p.innerWidth;val h=p.innerHeight
    val scale=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getFloat("v4_fontscale",1f)
        .coerceIn(1f,1.5f)*context.resources.configuration.fontScale.coerceIn(1f,1.5f)
    val tall=h>=240&&h>=w*.85f
    val refresh=h>=150||(h>=125&&w>=190)
    val citySize=min(16f*scale,if(h<135)14f else 20f)
    val cityH=citySize*1.8f+3
    val bodySize=if(h<135)14f else min(16f*scale,19f)
    val weatherH=bodySize*1.8f+4
    val tempSize=if(tall)minOf(44f*scale,w*.24f,h*.10f) else if(w>=500&&h>300)min(42f*scale,50f) else bodySize
    val tempH=if(tall||tempSize>bodySize)tempSize*1.8f+3 else weatherH
    val minimumArt=if(tall)min(w*.44f,h*.20f).coerceAtLeast(24f) else 0f
    val wideFooter=if(!tall&&h>=340)24f else 0f
    val hourDigits=if(android.text.format.DateFormat.is24HourFormat(context))3.2f else 4.8f
    val timeBudget=(h-cityH-3-tempH-(if(tall)weatherH else 0f)-minimumArt-wideFooter-10)/1.65f
    val timeSize=if(tall)minOf(76f*scale,w/hourDigits,h*.14f,timeBudget).coerceAtLeast(12f)
        else minOf((if(w>=500)96f else 54f)*scale,w*.61f/hourDigits,timeBudget).coerceAtLeast(12f)
    val timeH=timeSize*1.65f
    val timeY=cityH+3
    val dateY=timeY+timeH
    val line=bodySize*1.8f+3
    var extra=(h-cityH-3-timeH-tempH-weatherH-minimumArt-12).coerceAtLeast(0f)
    val dateH=if(tall&&extra>=line){extra-=line;line}
        else if(!tall&&h>180&&dateY+line<=h-tempH-wideFooter-4)line else 0f
    val footer=if(tall&&h>=340&&extra>=24){extra-=24;24f} else wideFooter
    val infoH=if(tall&&h>310&&extra>=line)line else 0f
    val bottomBlock=if(tall)tempH+weatherH+infoH+footer else tempH+footer
    val bottomY=h-bottomBlock
    val artTop=if(tall)dateY+dateH+4 else maxOf(cityH+2,if(refresh)42f else 0f)
    val artBudget=if(tall)bottomY-artTop-4 else h-artTop-footer-4
    val art=minOf(if(tall)w*.78f else w*.36f,artBudget).coerceAtLeast(16f)
    val artX=if(tall)(w-art)/2 else w-art
    val seed=(d.stamp xor(d.stamp ushr 32)).toInt()
    val dip=TypedValue.COMPLEX_UNIT_DIP
    val result=buddyRemote(context,d,motion,seed,art,layoutId=R.layout.widget_buddy_clock)
    fun box(id:Int,x:Float,y:Float,width:Float,height:Float) {
        result.setViewLayoutWidth(id,width.coerceAtLeast(1f),dip)
        result.setViewLayoutHeight(id,height.coerceAtLeast(1f),dip)
        result.setViewLayoutMargin(id,RemoteViews.MARGIN_LEFT,x,dip)
        result.setViewLayoutMargin(id,RemoteViews.MARGIN_TOP,y,dip)
    }
    fun text(id:Int,value:String,size:Float,x:Float,y:Float,width:Float,height:Float,soft:Boolean=false) {
        result.setTextViewText(id,value);result.setTextViewTextSize(id,dip,size)
        result.setTextColor(id,if(soft)colors.secondary else colors.ink)
        box(id,x,y,width,height)
    }
    val city=io.atmos.weather.location.LocationLabels.microcity(d.city).ifBlank{d.city}
    text(R.id.buddy_clock_city,city,citySize,0f,0f,if(refresh)(w-44).coerceAtLeast(50f) else w,cityH)
    box(R.id.buddy_clock_scene,artX,artTop,art,art)
    box(R.id.buddy_clock_time,0f,timeY,if(tall)w else w-art-6,timeH)
    result.setTextViewTextSize(R.id.buddy_clock_time,dip,timeSize)
    result.setTextColor(R.id.buddy_clock_time,colors.ink)
    result.setCharSequence(R.id.buddy_clock_time,"setFormat12Hour","h:mm a")
    result.setCharSequence(R.id.buddy_clock_time,"setFormat24Hour","HH:mm")
    result.setContentDescription(R.id.buddy_clock_time,"目前時間，依手機時鐘自動更新")
    result.setViewVisibility(R.id.buddy_clock_date,if(dateH>0)View.VISIBLE else View.GONE)
    box(R.id.buddy_clock_date,0f,dateY,if(tall)w else (w-art-6),dateH)
    result.setTextViewTextSize(R.id.buddy_clock_date,dip,bodySize)
    result.setTextColor(R.id.buddy_clock_date,colors.secondary)
    result.setCharSequence(R.id.buddy_clock_date,"setFormat12Hour","M月d日 EEE")
    result.setCharSequence(R.id.buddy_clock_date,"setFormat24Hour","M月d日 EEE")
    text(R.id.buddy_clock_temp,if(tall)d.temperature else "${d.temperature}  ${d.description()}",tempSize,
        0f,bottomY,if(tall)w else w-art-6,tempH)
    result.setViewVisibility(R.id.buddy_clock_condition,if(tall)View.VISIBLE else View.GONE)
    text(R.id.buddy_clock_condition,d.description(),bodySize,0f,bottomY+tempH,w,weatherH)
    result.setViewVisibility(R.id.buddy_clock_info,if(infoH>0)View.VISIBLE else View.GONE)
    text(R.id.buddy_clock_info,d.highLow.replace("最高 ","高 ").replace("最低 ","低 ")+" · 降雨 ${d.rain}",bodySize,
        0f,bottomY+tempH+weatherH,w,infoH,true)
    result.setViewVisibility(R.id.buddy_clock_updated,if(footer>0)View.VISIBLE else View.GONE)
    val stamp=if(d.stamp>0)java.time.Instant.ofEpochMilli(d.stamp).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern(if(d.age==WidgetPresentationRules.Freshness.FRESH)"HH:mm" else "MM/dd HH:mm")) else "尚未同步"
    text(R.id.buddy_clock_updated,"${when(d.age){WidgetPresentationRules.Freshness.FRESH->"天氣更新";WidgetPresentationRules.Freshness.CACHED->"快取";WidgetPresentationRules.Freshness.EXPIRED->"資料過期";else->"天氣"}} $stamp",12f,0f,h-footer,w,footer,true)
    result.setViewVisibility(R.id.buddy_clock_refresh,if(refresh)View.VISIBLE else View.GONE)
    box(R.id.buddy_clock_refresh,w-44,-3f,44f,44f)
    result.setInt(R.id.buddy_clock_refresh,"setColorFilter",colors.ink)
    val intent=Intent(context,BuddyClockRefreshReceiver::class.java).setAction("io.atmos.weather.WIDGET_REFRESH")
    result.setOnClickPendingIntent(R.id.buddy_clock_refresh,PendingIntent.getBroadcast(context,2801,intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    return result
}
class BuddyClockRefreshReceiver:android.content.BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent){WidgetSync.request(context)}
}
