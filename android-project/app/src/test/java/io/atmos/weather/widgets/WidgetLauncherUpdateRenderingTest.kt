package io.atmos.weather.widgets

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.atmos.weather.R
import io.atmos.weather.domain.model.WeatherCondition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File

/** Full Glance translation, Binder parcel transport and a persistent launcher host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class,qualifiers="zh-rTW-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetLauncherUpdateRenderingTest {
    private fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{descendants(v.getChildAt(it))}else emptyList()
    private fun transport(remote:RemoteViews):RemoteViews {
        val parcel=Parcel.obtain()
        return try{remote.writeToParcel(parcel,0);parcel.setDataPosition(0);RemoteViews.CREATOR.createFromParcel(parcel)}finally{parcel.recycle()}
    }
    private fun render(host:View,w:Int,h:Int):Bitmap {
        host.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));host.layout(0,0,w,h)
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{host.draw(Canvas(it))}
    }
    @Test(timeout=240_000) fun allTwentyNineWidgetsSurviveRealHostUpdatesAndExtremeResizesWithoutGhosts(){
        ShadowChoreographer.setPaused(true)
        val renderer=WidgetRenderingTest();renderer.prepare()
        val context=RuntimeEnvironment.getApplication()
        val reading=OpticalWidgetData("嘉義縣 · 興村里","20°",WeatherCondition.PARTLY_CLOUDY,true,System.currentTimeMillis(),
            WidgetPresentationRules.Freshness.FRESH,"20%","最高 23°  最低 18°","72%","2.8 m/s","32","3.2",emptyList(),"尚未設定鬧鐘",null,"沒有事項","尚無近期地震推播","",false)
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try{
            val activity=controller.get()
            for(variant in 0..28){
                val host=AppWidgetHostView(activity).apply{setExecutor(null);setAppWidget(variant+1,null)}
                activity.setContentView(host)
                for((pass,dimensions) in listOf(320 to 180,96 to 800,800 to 96,320 to 180).withIndex()){
                    val (w,h)=dimensions
                    val latest=reading.copy(city="最新地點 $variant/$pass",temperature="${20+pass}°")
                    val remote=runBlocking{GlanceRemoteViews().compose(context,DpSize(w.dp,h.dp)){OpticalWidgetContent(context,variant,latest)}.remoteViews}
                    host.updateAppWidget(transport(remote))
                    val family=when(variant){25->5;26->6;27->7;28->8;else->variant/5}
                    val content=host.findViewById<ViewGroup>(R.id.widget_adaptive_root)
                    assertNotNull("Host failed to inflate variant $variant",content)
                    val plan=WidgetLayoutPlan.create(w.toFloat(),h.toFloat())
                    val hour24=android.text.format.DateFormat.is24HourFormat(context)
                    assertEquals("Duplicate regions in variant $variant update $pass",AdaptiveWidgetLayout.create(plan,family,latest,hour24).size,content.childCount)
                    assertTrue("Static/reduced-motion widgets must not keep hidden timers",descendants(content).none{it is ViewFlipper})
                    val labels=descendants(content).filterIsInstance<TextView>().map{it.text.toString()}
                    if(pass>0)assertFalse(labels.contains("最新地點 $variant/${pass-1}"))
                    var actual=render(host,w,h)
                    try{
                        // TextClock and AnalogClock read the wall clock, so a tick between the two renders
                        // changes pixels without any ghost. Apply the same update again and compare again:
                        // only a difference that survives a repeated update is a real leftover.
                        var same=false
                        for(attempt in 0..2){
                            val fresh=AppWidgetHostView(activity).apply{setExecutor(null);setAppWidget(variant+101+attempt*100,null);updateAppWidget(transport(remote))}
                            val expected=render(fresh,w,h)
                            try{same=actual.sameAs(expected)}finally{expected.recycle()}
                            if(same)break
                            host.updateAppWidget(transport(remote))
                            actual.recycle();actual=render(host,w,h)
                        }
                        assertTrue("Reused launcher differs from a fresh presentation: $variant update $pass",same)
                        if(variant==28&&pass==3){
                            val output=File("build/widget-renders/r5-reused-buddy-clock.png");output.parentFile.mkdirs()
                            output.outputStream().use{actual.compress(Bitmap.CompressFormat.PNG,100,it)}
                        }
                    }finally{actual.recycle()}
                }
            }
        }finally{controller.pause().stop().destroy()}
    }
}
