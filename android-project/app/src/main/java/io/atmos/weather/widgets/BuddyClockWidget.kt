package io.atmos.weather.widgets

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
    val w=p.innerWidth;val h=p.innerHeight;val growth=p.typeGrowth
    val custom=p.textScale
    val scale=context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getFloat("v4_fontscale",1f)
        .coerceIn(1f,1.5f)*context.resources.configuration.fontScale.coerceIn(1f,1.5f)*custom
    val tall=h>=240&&h>=w*.85f
    val citySize=min(16f*scale*growth,if(h<135)12f*custom else 20f*growth*custom)
    val cityH=citySize*(if(h<135)1.5f else 1.8f)+if(h<135)1f else 3f
    val bodySize=if(h<135)min(11f*custom,h*.15f) else min(16f*scale*growth,19f*growth*custom)
    val weatherH=bodySize*1.8f+4
    val tempSize=if(tall)minOf(44f*scale*growth,w*.24f*custom,h*.10f*custom) else if(w>=500&&h>300)min(42f*scale*growth,50f*growth*custom) else bodySize
    val tempH=if(tall||tempSize>bodySize)tempSize*1.8f+3 else weatherH
    val minimumArt=if(tall)min(w*.44f,h*.20f).coerceAtLeast(24f) else 0f
    val wideFooter=if(!tall&&h>=340)24f else 0f
    val hourDigits=if(android.text.format.DateFormat.is24HourFormat(context))3.2f else 4.8f
    val dateLine=(if(h<135)min(10f*custom,h*.14f) else min(bodySize,14f*growth*custom))*1.65f
    val timeBudget=(h-dateLine-cityH-3-tempH-(if(tall)weatherH else 0f)-minimumArt-wideFooter-10)/1.65f
    val timeSize=if(tall)minOf(76f*scale*growth,w/hourDigits,h*.14f*custom,timeBudget).coerceAtLeast(12f)
        else minOf((if(w>=500)96f else 54f)*scale*growth,w*.61f/hourDigits,timeBudget).coerceAtLeast(12f)
    val timeH=timeSize*1.65f
    val timeY=cityH+3
    val dateY=timeY+timeH
    val line=bodySize*1.8f+3
    var extra=(h-cityH-3-timeH-tempH-weatherH-minimumArt-12).coerceAtLeast(0f)
    val dateH=dateLine
    val footer=if(tall&&h>=340&&extra>=24){extra-=24;24f} else wideFooter
    val infoH=if(tall&&h>310&&extra>=line)line else 0f
    val bottomBlock=if(tall)tempH+weatherH+infoH+footer else tempH+footer
    val bottomY=h-bottomBlock
    val artTop=if(tall)dateY+dateH+4 else cityH+2
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
    text(R.id.buddy_clock_city,city,citySize,0f,0f,w,cityH)
    box(R.id.buddy_clock_scene,artX,artTop,art,art)
    box(R.id.buddy_clock_time,0f,timeY,if(tall)w else w-art-6,timeH)
    result.setTextViewTextSize(R.id.buddy_clock_time,dip,timeSize)
    result.setTextColor(R.id.buddy_clock_time,colors.ink)
    result.setCharSequence(R.id.buddy_clock_time,"setFormat12Hour","h:mm a")
    result.setCharSequence(R.id.buddy_clock_time,"setFormat24Hour","HH:mm")
    result.setContentDescription(R.id.buddy_clock_time,"目前時間，依手機時鐘自動更新")
    result.setViewVisibility(R.id.buddy_clock_date,if(dateH>0)View.VISIBLE else View.GONE)
    box(R.id.buddy_clock_date,0f,dateY,if(tall)w else (w-art-6),dateH)
    result.setTextViewTextSize(R.id.buddy_clock_date,dip,if(h<135)min(10f*custom,h*.14f) else min(bodySize,14f*growth*custom))
    result.setTextColor(R.id.buddy_clock_date,colors.secondary)
    result.setCharSequence(R.id.buddy_clock_date,"setFormat12Hour","M月d日")
    result.setCharSequence(R.id.buddy_clock_date,"setFormat24Hour","M月d日")
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
    text(R.id.buddy_clock_updated,"${when(d.age){WidgetPresentationRules.Freshness.FRESH->"天氣更新";WidgetPresentationRules.Freshness.CACHED->"快取";WidgetPresentationRules.Freshness.EXPIRED->"資料過期";else->"天氣"}} $stamp",min(12f*custom,(footer/1.5f).coerceAtLeast(8f)),0f,h-footer,w,footer,true)
    result.setViewVisibility(R.id.buddy_clock_refresh,View.GONE)
    return result
}
class BuddyClockRefreshReceiver:android.content.BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent){WidgetSync.request(context)}
}
