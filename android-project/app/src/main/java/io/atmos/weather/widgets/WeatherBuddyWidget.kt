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
    // An earthquake warning or a fresh earthquake report: the buddy becomes the quake bull.
    if(d.events.any{it.kind==WidgetEvent.Kind.EEW||it.kind==WidgetEvent.Kind.QUAKE})return bullRemote(context,d,motion)
    val kind=WeatherBuddyArt.kind(d.condition,d.day)
    // GONE auto-start ViewFlippers still own timers in a visible launcher window.
    // Static/unknown weather has no animation views at all.
    if(!motion||kind==WeatherBuddyArt.Kind.UNKNOWN){
        return RemoteViews(context.packageName,R.layout.widget_buddy_still).apply{
            setImageViewResource(R.id.buddy_still,BuddyVectorResources.still(kind,d.day))
            setContentDescription(R.id.buddy_root,"天氣夥伴 · ${d.description()}")
        }
    }
    val side=sizeDp.takeIf{it.isFinite()}?.coerceIn(16f,1000f)?:112f
    val wet=kind in listOf(WeatherBuddyArt.Kind.RAIN,WeatherBuddyArt.Kind.STORM,WeatherBuddyArt.Kind.SNOW)
    val unit=android.util.TypedValue.COMPLEX_UNIT_DIP
    fun RemoteViews.rect(id:Int,left:Float,top:Float,width:Float,height:Float) {
        setViewLayoutWidth(id,side*width,unit);setViewLayoutHeight(id,side*height,unit)
        setViewLayoutMargin(id,RemoteViews.MARGIN_LEFT,side*left,unit)
        setViewLayoutMargin(id,RemoteViews.MARGIN_TOP,side*top,unit)
    }
    return RemoteViews(context.packageName,if(wet)layoutId else R.layout.widget_buddy_scene_dry).apply {
        setContentDescription(R.id.buddy_root,"天氣夥伴 · ${d.description()}")
        if(wet)setViewVisibility(R.id.buddy_ground,View.VISIBLE)
        removeAllViews(R.id.buddy_float);removeAllViews(R.id.buddy_rain)
        if(wet)removeAllViews(R.id.buddy_ground)
        val face=RemoteViews(context.packageName,R.layout.widget_buddy_face).apply {
            setImageViewResource(R.id.buddy_body,BuddyVectorResources.body(kind,d.day))
            val eyeTop=when(kind){WeatherBuddyArt.Kind.SUN->.34f;WeatherBuddyArt.Kind.MOON->.36f;else->.42f}
            rect(R.id.buddy_eyes,.28f,eyeTop,.47f,.28f)
            setViewVisibility(R.id.buddy_eyes,if(kind==WeatherBuddyArt.Kind.UNKNOWN)View.GONE else View.VISIBLE)
            removeAllViews(R.id.buddy_eyes)
            val random=kotlin.random.Random(seed)
            repeat(2) {
                BuddyVectorResources.eyes(kind,random.nextInt(6)).forEach {resource->
                    addView(R.id.buddy_eyes,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply {
                        setImageViewResource(R.id.buddy_frame,resource)
                    })
                }
            }
            setInt(R.id.buddy_eyes,"setFlipInterval",90+Math.floorMod(seed,26))
            setInt(R.id.buddy_eyes,"setDisplayedChild",0)
        }
        addView(R.id.buddy_float,face)
        // Living layers behind the buddy: a weather-coloured glow on its own 2.6 s rhythm and a
        // ground shadow that tightens as the 3.6 s float lifts the buddy.
        rect(R.id.buddy_halo,.04f,.02f,.92f,.92f)
        setImageViewResource(R.id.buddy_halo_image,when(kind){
            WeatherBuddyArt.Kind.SUN->R.drawable.widget_buddy_halo_sun;WeatherBuddyArt.Kind.MOON->R.drawable.widget_buddy_halo_moon
            WeatherBuddyArt.Kind.RAIN->R.drawable.widget_buddy_halo_rain;WeatherBuddyArt.Kind.SNOW->R.drawable.widget_buddy_halo_snow
            WeatherBuddyArt.Kind.STORM->R.drawable.widget_buddy_halo_storm;WeatherBuddyArt.Kind.MIST->R.drawable.widget_buddy_halo_mist
            else->R.drawable.widget_buddy_halo_cloud})
        setInt(R.id.buddy_halo,"setFlipInterval",2600)
        rect(R.id.buddy_shadow,.24f,.84f,.52f,.09f)
        setInt(R.id.buddy_shadow,"setFlipInterval",3600)
        setInt(R.id.buddy_float,"setFlipInterval",3600)
        // Tight layer bounds keep a large character sharp without full-card animated bitmaps.
        rect(R.id.buddy_rain,0f,if(wet).64f else 0f,1f,if(wet).34f else 1f)
        rect(R.id.buddy_ground,0f,.86f,1f,.10f)
        BuddyVectorResources.effects(kind).forEach {resource->
            addView(R.id.buddy_rain,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply {setImageViewResource(R.id.buddy_frame,resource)})
        }
        BuddyVectorResources.ground(kind).forEach {resource->
            addView(R.id.buddy_ground,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply {setImageViewResource(R.id.buddy_frame,resource)})
        }
        val period=if(wet)35 else 90
        setInt(R.id.buddy_rain,"setFlipInterval",period)
        setInt(R.id.buddy_rain,"setDisplayedChild",0)
        if(wet){setInt(R.id.buddy_ground,"setFlipInterval",period);setInt(R.id.buddy_ground,"setDisplayedChild",0)}
    }
}
/**
 * The quake bull: 地牛 (the earth ox) rearing up and stamping: the ground splits with a glow, stones jump, dust rolls out,
 * 24 vector frames (tools/make_quake_bull.py) looping every 1.68 s without a seam.
 */
internal val BULL_FRAMES=intArrayOf(R.drawable.widget_bull_f00,R.drawable.widget_bull_f01,R.drawable.widget_bull_f02,R.drawable.widget_bull_f03,
    R.drawable.widget_bull_f04,R.drawable.widget_bull_f05,R.drawable.widget_bull_f06,R.drawable.widget_bull_f07,R.drawable.widget_bull_f08,
    R.drawable.widget_bull_f09,R.drawable.widget_bull_f10,R.drawable.widget_bull_f11,R.drawable.widget_bull_f12,R.drawable.widget_bull_f13,
    R.drawable.widget_bull_f14,R.drawable.widget_bull_f15,R.drawable.widget_bull_f16,R.drawable.widget_bull_f17,R.drawable.widget_bull_f18,
    R.drawable.widget_bull_f19,R.drawable.widget_bull_f20,R.drawable.widget_bull_f21,R.drawable.widget_bull_f22,R.drawable.widget_bull_f23)
internal fun bullRemote(context:Context,d:OpticalWidgetData,motion:Boolean):RemoteViews {
    val eew=d.events.any{it.kind==WidgetEvent.Kind.EEW}
    val label=if(eew)"地震速報中 · 地牛" else "最新地震報告 · 地牛"
    if(!motion)return RemoteViews(context.packageName,R.layout.widget_buddy_still).apply{
        setImageViewResource(R.id.buddy_still,R.drawable.widget_bull_still);setContentDescription(R.id.buddy_root,label)
    }
    return RemoteViews(context.packageName,R.layout.widget_buddy_bull).apply{
        setContentDescription(R.id.buddy_root,label)
        removeAllViews(R.id.buddy_bull)
        BULL_FRAMES.forEach{frame->addView(R.id.buddy_bull,RemoteViews(context.packageName,R.layout.widget_buddy_frame).apply{setImageViewResource(R.id.buddy_frame,frame)})}
        setInt(R.id.buddy_bull,"setFlipInterval",70)
        setInt(R.id.buddy_bull,"setDisplayedChild",0)
    }
}
@Composable internal fun BuddyScene(context:Context,d:OpticalWidgetData,size:Float,motion:Boolean) {
    val seed=remember(d.stamp,d.condition,d.day){(d.stamp xor (d.stamp ushr 32)).toInt()}
    val remote=remember(d.condition,d.day,d.stamp,motion,size,d.events){buddyRemote(context,d,motion,seed,size)}
    AndroidRemoteViews(remote,GlanceModifier.width(size.dp).height(size.dp))
}
