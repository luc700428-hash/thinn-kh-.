package io.atmos.weather.widgets

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.provider.Settings
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.atmos.weather.R
import io.atmos.weather.domain.model.WeatherCondition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/** These are real Glance translations, RemoteViews inflation and native Android raster output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetRenderingTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val output=File("build/widget-renders").apply{mkdirs()}
    private val data=OpticalWidgetData("臺北","24°",WeatherCondition.PARTLY_CLOUDY,true,System.currentTimeMillis(),
        WidgetPresentationRules.Freshness.FRESH,"40%","最高 26°  最低 21°","68%","2.8 m/s","32","3.2",
        listOf(0,2,61,95,71,3).mapIndexed{i,code->WidgetHour("${14+i}:00","${24-i}°","${i*10}%",code,i<4)},
        "10/28 07:30 · 早安",System.currentTimeMillis()+3600000,"15:30 · 出門走走","尚無近期地震推播","請以官方災防告警為準",false)
    @Before fun prepare() {
        RuntimeEnvironment.setFontScale(1f)
        SystemClock.setCurrentTimeMillis(Instant.parse("2026-10-27T01:41:00Z").toEpochMilli())
        context.getSharedPreferences("thinn_settings",0).edit().clear().putBoolean("reduce_motion",true).commit()
        Settings.System.putString(context.contentResolver,Settings.System.TIME_12_24,"24")
    }
    @Test(timeout=180_000) fun allTwentySevenWidgetsHaveNewRenderedPreviews() {
        for(variant in 0..26) {
            val family=if(variant==25)5 else if(variant==26)6 else variant/5
            val width=if(family in 1..5)320 else 180
            render(variant,width,180,1f,"catalogue_${variant.toString().padStart(2,'0')}")
        }
    }
    @Test(timeout=240_000) fun everyFamilyFitsSmallWideTallAndLargeText() {
        val failures=mutableListOf<String>()
        for(variant in listOf(0,5,10,15,20,25,26)) {
            for((width,height) in listOf(100 to 60,160 to 160,320 to 180,180 to 320,360 to 360)) {
                for(scale in listOf(1f,1.5f)) {
                    try {render(variant,width,height,scale,"v${variant}_${width}x${height}_${(scale*100).toInt()}")}
                    catch(error:AssertionError){failures+=error.message?:error.toString()}
                }
            }
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
    @Test fun unknownWeatherIsNeutralAndArtworkMemoryIsBounded() {
        val unknown=WidgetWeatherArt.render(WeatherCondition.UNKNOWN,true,1000,0)
        assertEquals(192,unknown.width)
        val clear=WidgetWeatherArt.render(WeatherCondition.CLEAR,true,192,0)
        assertFalse(unknown.sameAs(clear))
        for(condition in WeatherCondition.entries)for(day in listOf(true,false))for(frame in 0..1) {
            val art=WidgetWeatherArt.render(condition,day,96,frame)
            assertEquals(96,art.width);assertEquals(96,art.height)
            assertSame(art,WidgetWeatherArt.render(condition,day,96,frame))
        }
    }
    @Test fun clocksAreNativeAndSupportBothSystemHourFormats() {
        val clock=RemoteViews(context.packageName,R.layout.widget_live_clock).apply(context,FrameLayout(context))
        assertTrue(clock.findViewById<View>(R.id.widget_clock) is TextClock)
        val text=clock.findViewById<TextClock>(R.id.widget_clock)
        assertEquals("h:mm a",text.format12Hour.toString());assertEquals("HH:mm",text.format24Hour.toString())
        val analog=RemoteViews(context.packageName,R.layout.widget_analog_clock).apply(context,FrameLayout(context))
        assertTrue(analog is AnalogClock)
        Settings.System.putString(context.contentResolver,Settings.System.TIME_12_24,"12")
        render(10,160,160,1.5f,"clock_12h");render(26,320,180,1.5f,"analog_12h")
    }
    @Test fun systemLargeTextAndRecentAlertKeepContentWithinTheCard() {
        RuntimeEnvironment.setFontScale(1.5f)
        render(0,160,160,1.5f,"system_large_weather")
        render(10,320,180,1.5f,"system_large_digital")
        render(26,180,180,1.5f,"system_large_analog")
        render(20,320,180,1.5f,"system_large_alert",data.copy(alertRecent=true,alert="地震報告 · 測試資料",alertDetail="測試資料，不是即時警報"))
    }
    private fun render(variant:Int,width:Int,height:Int,scale:Float,name:String,reading:OpticalWidgetData=data) {
        context.getSharedPreferences("thinn_settings",0).edit().putFloat("v4_fontscale",scale).commit()
        val remote=runBlocking {GlanceRemoteViews().compose(context,DpSize(width.dp,height.dp)) {OpticalWidgetContent(context,variant,reading)}.remoteViews}
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity=controller.get();val parent=FrameLayout(activity)
            val view=remote.apply(activity,parent);parent.addView(view,ViewGroup.LayoutParams(width,height))
            activity.setContentView(parent)
            parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            parent.layout(0,0,width,height)
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);parent.draw(Canvas(bitmap))
            File(output,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            fun inspect(v:View) {
                if(v.visibility!=View.VISIBLE)return
                if(v is TextView&&v.text.isNotEmpty()) {
                    val rect=Rect(0,0,v.width,v.height);parent.offsetDescendantRectToMyCoords(v,rect)
                    val pad=WidgetLayoutPlan.create(width.toFloat(),height.toFloat(),scale,context.resources.configuration.fontScale).padding
                    assertTrue("$name clipped ${v.text}: $rect in ${width}x$height, safe inset $pad",rect.top>=pad-1&&rect.left>=pad-1&&rect.right<=width-pad+1&&rect.bottom<=height-pad+1)
                    assertTrue("$name hid ${v.text}",v.width>0&&v.height>0)
                    if(v.text.toString()==data.temperature&&v.layout!=null)assertEquals("$name truncated temperature",0,v.layout.getEllipsisCount(0))
                }
                if(v is ViewGroup)for(i in 0 until v.childCount)inspect(v.getChildAt(i))
            }
            inspect(view)
        } finally {controller.pause().stop().destroy()}
    }
}
