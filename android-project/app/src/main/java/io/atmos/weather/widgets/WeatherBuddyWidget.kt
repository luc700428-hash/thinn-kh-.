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
class WeatherBuddyWidgetReceiver:SyncedWidgetReceiver(){override val glanceAppWidget:GlanceAppWidget=WeatherBuddyWidget()}

/** Native vectors resolve at the launcher's final dimensions, including after a resize. */
internal fun buddyRemote(context:Context,d:OpticalWidgetData,motion:Boolean,seed:Int,sizeDp:Float=112f,layoutId:Int=R.layout.widget_buddy_scene):RemoteViews {
    val kind=WeatherBuddyArt.kind(d.condition,d.day)
    val side=sizeDp.takeIf{it.isFinite()}?.coerceIn(16f,1000f)?:112f
    val wet=kind in listOf(WeatherBuddyArt.Kind.RAIN,WeatherBuddyArt.Kind.STORM,WeatherBuddyArt.Kind.SNOW)
    val unit=android.util.TypedValue.COMPLEX_UNIT_DIP
    fun RemoteViews.rect(id:Int,left:Float,top:Float,width:Float,height:Float) {
        setViewLayoutWidth(id,side*width,unit);setViewLayoutHeight(id,side*height,unit)
        setViewLayoutMargin(id,RemoteViews.MARGIN_LEFT,side*left,unit)
        setViewLayoutMargin(id,RemoteViews.MARGIN_TOP,side*top,unit)
    }
    return RemoteViews(context.packageName,layoutId).apply {
        setContentDescription(R.id.buddy_root,"天氣夥伴 · ${d.description()}")
        setViewVisibility(R.id.buddy_float,if(motion)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.buddy_rain,if(motion)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.buddy_ground,if(motion&&wet)View.VISIBLE else View.GONE)
        setViewVisibility(R.id.buddy_still,if(motion)View.GONE else View.VISIBLE)
        if(!motion){setImageViewResource(R.id.buddy_still,BuddyVectorResources.still(kind,d.day));return@apply}
        removeAllViews(R.id.buddy_float);removeAllViews(R.id.buddy_rain);removeAllViews(R.id.buddy_ground)
        val face=RemoteViews(context.packageName,R.layout.widget_buddy_face).apply {
            setImageViewResource(R.id.buddy_body,BuddyVectorResources.body(kind,d.day))
            val eyeTop=when(kind){WeatherBuddyArt.Kind.SUN->.34f;WeatherBuddyArt.Kind.MOON->.36f;else->.42f}
            rect(R.id.buddy_eyes,.28f,eyeTop,.47f,.28f)
            setViewVisibility(R.id.buddy_eyes,if(kind==WeatherBuddyArt.Kind.UNKNOWN)View.GONE else View.VISIBLE)
            removeAllViews(R.id.buddy_eyes)
            val random=kotlin.random.Random(seed)
            repeat(4) {
                BuddyVectorResources.eyes(kind,random.nextInt(4)).forEach {resource->
                    addView(R.id.buddy_eyes,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply {
                        setImageViewResource(R.id.buddy_frame,resource)
                    })
                }
            }
            setInt(R.id.buddy_eyes,"setFlipInterval",140+Math.floorMod(seed,45))
            setInt(R.id.buddy_eyes,"setDisplayedChild",0)
        }
        addView(R.id.buddy_float,face);setInt(R.id.buddy_float,"setFlipInterval",3600)
        // Tight layer bounds keep a large character sharp without full-card animated bitmaps.
        rect(R.id.buddy_rain,0f,if(wet).64f else 0f,1f,if(wet).34f else 1f)
        rect(R.id.buddy_ground,0f,.86f,1f,.10f)
        BuddyVectorResources.effects(kind).forEach {resource->
            addView(R.id.buddy_rain,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply {setImageViewResource(R.id.buddy_frame,resource)})
        }
        BuddyVectorResources.ground(kind).forEach {resource->
            addView(R.id.buddy_ground,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply {setImageViewResource(R.id.buddy_frame,resource)})
        }
        val period=if(wet)70 else 180
        setInt(R.id.buddy_rain,"setFlipInterval",period);setInt(R.id.buddy_ground,"setFlipInterval",period)
        setInt(R.id.buddy_rain,"setDisplayedChild",0);setInt(R.id.buddy_ground,"setDisplayedChild",0)
    }
}
@Composable internal fun BuddyScene(context:Context,d:OpticalWidgetData,size:Float,motion:Boolean) {
    val seed=remember(d.stamp,d.condition,d.day){(d.stamp xor (d.stamp ushr 32)).toInt()}
    val remote=remember(d.condition,d.day,d.stamp,motion,size){buddyRemote(context,d,motion,seed,size)}
    AndroidRemoteViews(remote,GlanceModifier.width(size.dp).height(size.dp))
}
