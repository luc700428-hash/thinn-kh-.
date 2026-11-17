package io.atmos.weather.widgets

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.atmos.weather.domain.model.WeatherCondition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** The five Q-version widgets drawn for real, from 2×2 to 10×10. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QBuddyWidgetRenderingTest {
    private fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{descendants(v.getChildAt(it))} else emptyList()
    @Test fun everyBuddyWidgetDrawsItsTextWithoutCuttingItAtEachSize() {
        val renderer=WidgetRenderingTest();renderer.prepare()
        val reading=OpticalWidgetData("台灣 · 新北市 · 板橋區","24°",WeatherCondition.PARTLY_CLOUDY,true,System.currentTimeMillis(),
            WidgetPresentationRules.Freshness.FRESH,"40%","最高 26°  最低 21°","68%","2.8 m/s","32","3",
            (0..7).map{WidgetHour("${14+it}:00","${24-it}°","20%",2,true)},"11/18 07:30 · 早安",System.currentTimeMillis()+3_600_000,"11/18 09:00 · 開會",
            "花蓮縣近海 規模 5.2","最大震度 4 級 · 深度 18 km",true,2,
            listOf("今天","明天","週四","週五","週六","週日","週一").mapIndexed{i,l->WidgetDay(l,"${18+i}–${25+i}°",if(i%2==0)"60%" else "",if(i%2==0)61 else 2)},
            "預計 15:00 前後可能下雨","明天 18–23°，比今天涼 3°","帶把折疊傘比較安心 · 長袖或薄外套")
        for(variant in 27..31)for((w,h) in listOf(150 to 170,320 to 170,320 to 360,170 to 360,480 to 540,640 to 360,800 to 1000)) {
            renderer.render(variant,w,h,1f,"q_buddy_${variant}_${w}x$h",reading){view->
                val texts=descendants(view).filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE&&it.width>0}
                assertTrue("variant $variant ${w}x$h shows no text",texts.isNotEmpty())
                assertTrue("variant $variant ${w}x$h lost the temperature",texts.any{it.text.toString().startsWith("24°")})
                // Single-line readings are never cut to nothing.
                texts.filter{it.maxLines==1&&it.layout!=null&&it.text.length<=6}.forEach{
                    assertEquals("variant $variant ${w}x$h cut '${it.text}'",0,it.layout.getEllipsisCount(0))
                }
            }
        }
    }
}
