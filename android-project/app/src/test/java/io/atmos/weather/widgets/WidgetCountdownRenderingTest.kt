package io.atmos.weather.widgets
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextClock
import io.atmos.weather.R
import io.atmos.weather.domain.model.WeatherCondition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rTW-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetCountdownRenderingTest{
    private fun views(v:View):List<View> =listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Test fun clockFamiliesOnlyShowACountdownForAValidAlarmWithinTheNextDay(){
        val ctx=RuntimeEnvironment.getApplication();val now=System.currentTimeMillis()
        val data=OpticalWidgetData("臺北市","24°",WeatherCondition.CLEAR,true,now,WidgetPresentationRules.Freshness.FRESH,
            "20%","最高 26°  最低 21°","68%","2.8 m/s","32","3.2",emptyList(),"下次鬧鐘",null,"事項","尚無近期地震推播","",false)
        val plan=WidgetLayoutPlan.create(400f,800f)
        for(family in listOf(2,6,8))for((target,expected) in listOf(now-60_000 to false,now+300_000 to true,now+86_400_000 to true,now+172_800_000 to false)){
            val remote=adaptiveWidgetRemote(ctx,family,data.copy(alarmAt=target),plan,WidgetGlassRenderer.palette(0),false)
            val root=remote.apply(ctx,FrameLayout(ctx))
            val children=views(root)
            assertTrue("Native time disappeared",children.filterIsInstance<TextClock>().any{it.format24Hour.toString()=="HH:mm"})
            assertEquals("Invalid alarm rendered a misleading timer: family=$family target=$target",expected,children.any{it is Chronometer&&it.id==R.id.widget_countdown})
        }
    }
}
