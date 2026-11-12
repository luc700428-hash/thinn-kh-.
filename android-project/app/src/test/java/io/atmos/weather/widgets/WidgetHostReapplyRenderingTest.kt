package io.atmos.weather.widgets

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.ViewFlipper
import io.atmos.weather.R
import io.atmos.weather.domain.model.WeatherCondition
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/** A launcher reuses its existing tree. Fresh inflation alone cannot catch update accumulation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rTW-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
open class WidgetHostReapplyRenderingTest {
    protected val context get()=RuntimeEnvironment.getApplication()
    protected val reading get()=OpticalWidgetData("嘉義縣 · 興村里","20°",WeatherCondition.PARTLY_CLOUDY,true,
        System.currentTimeMillis(),WidgetPresentationRules.Freshness.FRESH,"20%","最高 23°  最低 18°",
        "72%","2.8 m/s","32","3.2",emptyList(),"尚未設定鬧鐘",null,"沒有事項","尚無近期地震推播","",false)
    protected fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{descendants(v.getChildAt(it))}else emptyList()
    @Before fun prepareHost(){
        ShadowChoreographer.setPaused(true)
        context.getSharedPreferences("thinn_settings",0).edit().clear().commit()
        Settings.System.putString(context.contentResolver,Settings.System.TIME_12_24,"12")
    }
    @Test fun repeatedUpdatesReplaceTheExistingRegionsInsteadOfStackingThem(){
        val plan=WidgetLayoutPlan.create(320f,180f)
        val colors=WidgetGlassRenderer.palette(0)
        val remote=adaptiveWidgetRemote(context,8,reading,plan,colors,true)
        val root=remote.apply(context,FrameLayout(context))
        val expected=AdaptiveWidgetLayout.create(plan,8,reading,false).size
        repeat(12){
            remote.reapply(context,root)
            assertEquals("A reused launcher retained old content after update $it",expected,root.findViewById<ViewGroup>(R.id.widget_adaptive_root).childCount)
            assertEquals(1,descendants(root).count{v->v.id==R.id.buddy_clock_time})
            assertEquals(1,descendants(root).count{v->v.id==R.id.buddy_root})
        }
    }
    @Test fun resizingAndChangingEveryFamilyDropsOldTextAndKeepsTheTreeBounded(){
        val colors=WidgetGlassRenderer.palette(0)
        val plan=WidgetLayoutPlan.create(320f,180f)
        val initial=adaptiveWidgetRemote(context,0,reading,plan,colors,false)
        val root=initial.apply(context,FrameLayout(context))
        for(family in 0..8)for((w,h) in listOf(320f to 180f,96f to 800f,800f to 96f,320f to 320f)){
            val latest=reading.copy(city="最新地點 $family",temperature="${21+family}°")
            val resized=WidgetLayoutPlan.create(w,h,widgetScale=1.6f)
            val next=adaptiveWidgetRemote(context,family,latest,resized,colors,false)
            next.reapply(context,root)
            val children=descendants(root)
            assertEquals(AdaptiveWidgetLayout.create(resized,family,latest,false).size,root.findViewById<ViewGroup>(R.id.widget_adaptive_root).childCount)
            assertFalse("Old city survived a resize",children.filterIsInstance<TextView>().any{it.text.toString()==reading.city})
            assertTrue("View count grew without a content limit",children.size<100)
        }
    }
    @Test fun anAlreadyDuplicatedR4TreeIsRepairedOnItsFirstUpdate(){
        val plan=WidgetLayoutPlan.create(320f,180f);val colors=WidgetGlassRenderer.palette(0)
        val remote=adaptiveWidgetRemote(context,8,reading,plan,colors,false)
        val root=remote.apply(context,FrameLayout(context))
        val content=root.findViewById<ViewGroup>(R.id.widget_adaptive_root)
        // The broken release kept appending its prior regions to the same FrameLayout.
        repeat(4){content.addView(TextView(context).apply{text="舊版本殘影 $it"})}
        remote.reapply(context,root)
        assertEquals(AdaptiveWidgetLayout.create(plan,8,reading,false).size,content.childCount)
        assertFalse(descendants(root).filterIsInstance<TextView>().any{it.text.startsWith("舊版本殘影")})
    }
    @Test fun detachedOldAnimationsCannotKeepAddingLauncherWork(){
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try{
            val activity=controller.get();val parent=FrameLayout(activity);activity.setContentView(parent)
            val plan=WidgetLayoutPlan.create(320f,180f);val colors=WidgetGlassRenderer.palette(0)
            val moving=adaptiveWidgetRemote(activity,8,reading.copy(condition=WeatherCondition.RAIN),plan,colors,true)
            val root=moving.apply(activity,parent);parent.addView(root)
            val old=descendants(root).filterIsInstance<ViewFlipper>()
            assertTrue(old.isNotEmpty())
            val still=adaptiveWidgetRemote(activity,8,reading,plan,colors,false)
            still.reapply(activity,root)
            assertTrue("Removed animation is still attached",old.all{!it.isAttachedToWindow})
            val frames=old.map{it.displayedChild}
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
            assertEquals("Detached animation timers kept advancing",frames,old.map{it.displayedChild})
            assertFalse(descendants(root).any{it.id==R.id.buddy_eyes})
        }finally{controller.pause().stop().destroy()}
    }
    @Test fun reducedMotionAndUnknownWeatherCreateNoHiddenAnimationTimers(){
        val plan=WidgetLayoutPlan.create(400f,400f);val colors=WidgetGlassRenderer.palette(0)
        for(family in 0..8)for(condition in listOf(WeatherCondition.PARTLY_CLOUDY,WeatherCondition.UNKNOWN)){
            val data=reading.copy(condition=condition)
            val remote=adaptiveWidgetRemote(context,family,data,plan,colors,false)
            val root=remote.apply(context,FrameLayout(context))
            assertTrue("Static family $family retained a hidden animation",descendants(root).none{it is ViewFlipper})
        }
        for(family in listOf(0,7,8)){
            val remote=adaptiveWidgetRemote(context,family,reading.copy(condition=WeatherCondition.UNKNOWN),plan,colors,true)
            assertTrue("Unknown weather must stay neutral and still",descendants(remote.apply(context,FrameLayout(context))).none{it is ViewFlipper})
        }
    }
}
