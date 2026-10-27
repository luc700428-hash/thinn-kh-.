package io.atmos.weather.widgets

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import io.atmos.weather.R

class WeatherBuddyWidget:AtmosWidget(27)
class WeatherBuddyWidgetReceiver:GlanceAppWidgetReceiver(){override val glanceAppWidget:GlanceAppWidget=WeatherBuddyWidget()}

/** The launcher runs these animations; no service, alarm or per-frame app wakeup is needed. */
internal fun buddyRemote(context:Context,d:OpticalWidgetData,motion:Boolean,seed:Int):RemoteViews {
    val kind=WeatherBuddyArt.kind(d.condition,d.day);val px=112
    return RemoteViews(context.packageName,R.layout.widget_buddy_scene).apply {
        setContentDescription(R.id.buddy_root,"天氣夥伴 · ${d.description()}")
        setViewVisibility(R.id.buddy_float,if(motion)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.buddy_rain,if(motion)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.buddy_ground,if(motion)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.buddy_still,if(motion)View.GONE else View.VISIBLE)
        if(!motion){setImageViewBitmap(R.id.buddy_still,WeatherBuddyArt.still(kind,d.day,px));return@apply}
        removeAllViews(R.id.buddy_float);removeAllViews(R.id.buddy_rain);removeAllViews(R.id.buddy_ground)
        repeat(1){page->
            val remote=RemoteViews(context.packageName,R.layout.widget_buddy_face).apply {
                setImageViewBitmap(R.id.buddy_body,WeatherBuddyArt.body(kind,d.day,px))
                removeAllViews(R.id.buddy_eyes)
                repeat(24){f->addView(R.id.buddy_eyes,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply{
                    setImageViewBitmap(R.id.buddy_frame,WeatherBuddyArt.eyes(kind,px,f/24f,seed))
                })}
                setInt(R.id.buddy_eyes,"setFlipInterval",180+Math.floorMod(seed,55))
                setInt(R.id.buddy_eyes,"setDisplayedChild",Math.floorMod(seed,24))
            }
            addView(R.id.buddy_float,remote)
        }
        setInt(R.id.buddy_float,"setFlipInterval",3800+Math.floorMod(seed,800))
        repeat(24){f->
            addView(R.id.buddy_rain,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply{setImageViewBitmap(R.id.buddy_frame,WeatherBuddyArt.rain(kind,80,f,false))})
            addView(R.id.buddy_ground,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply{setImageViewBitmap(R.id.buddy_frame,WeatherBuddyArt.rain(kind,80,f,true))})
        }
        // Same phase and period keep each drop and its ground impact connected.
        setInt(R.id.buddy_rain,"setDisplayedChild",0);setInt(R.id.buddy_ground,"setDisplayedChild",0)
    }
}

@Composable internal fun BuddyScene(context:Context,d:OpticalWidgetData,size:Float,motion:Boolean) {
    val remote=remember(d.condition,d.day,d.stamp,motion){buddyRemote(context,d,motion,(System.currentTimeMillis() xor d.stamp).toInt())}
    AndroidRemoteViews(remote,GlanceModifier.width(size.dp).height(size.dp))
}
