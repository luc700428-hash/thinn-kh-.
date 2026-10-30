package io.atmos.weather.widgets

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import io.atmos.weather.R
import io.atmos.weather.domain.model.WeatherCondition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuddyClockRenderingTest {
    private fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{descendants(v.getChildAt(it))} else emptyList()
    @Test fun nativeClockAndVectorWeatherFitSmallSquareTallAndHighDensityHosts() {
        val renderer=WidgetRenderingTest();renderer.prepare()
        val reading=OpticalWidgetData("台灣 · 嘉義縣 · 阿里山鄉","24°",WeatherCondition.CLEAR,true,System.currentTimeMillis(),
            WidgetPresentationRules.Freshness.FRESH,"40%","最高 26°  最低 21°","68%","2.8 m/s","32","3.2",emptyList(),
            "尚未設定鬧鐘",null,"沒有事項","無警報","",false)
        fun render(w:Int,h:Int,scale:Float,name:String,night:Boolean=false) {
            renderer.render(28,w,h,scale,name,reading.copy(day=!night)){view->
                val children=descendants(view)
                val clock=children.single{it.id==R.id.buddy_clock_time} as TextClock
                assertEquals("HH:mm",clock.format24Hour.toString());assertEquals("h:mm a",clock.format12Hour.toString())
                assertTrue(clock.width>0&&clock.height>0)
                val temp=children.single{it.id==R.id.buddy_clock_temp} as TextView
                assertTrue(temp.text.startsWith("24°"))
                assertTrue("Temperature digits must remain readable",temp.layout.getEllipsisCount(0)==0||temp.layout.getEllipsisStart(0)>=3)
                val character=children.single{it.id==R.id.buddy_still} as ImageView
                assertTrue("$name: character=${character.drawable?.javaClass?.name}, visible=${character.visibility}, size=${character.width}x${character.height}",character.drawable is android.graphics.drawable.VectorDrawable)
                assertTrue(character.visibility==View.VISIBLE&&character.width>20&&character.height>20)
                if(h>=190||(w>=260&&h>=160))assertTrue(children.single{it.id==R.id.buddy_clock_refresh}.isClickable)
            }
        }
        for((w,h) in listOf(140 to 110,160 to 160,180 to 280,180 to 340,180 to 420,240 to 360,320 to 160,320 to 240,500 to 340,320 to 320,320 to 600,640 to 450,800 to 600))
            for(scale in listOf(1f,1.5f))render(w,h,scale,"buddy_clock_${w}x${h}_${(scale*100).toInt()}")
        android.provider.Settings.System.putString(RuntimeEnvironment.getApplication().contentResolver,android.provider.Settings.System.TIME_12_24,"12")
        render(160,160,1.5f,"buddy_clock_12h_small")
        render(320,240,1.5f,"buddy_clock_12h_wide")
        android.provider.Settings.System.putString(RuntimeEnvironment.getApplication().contentResolver,android.provider.Settings.System.TIME_12_24,"24")
        RuntimeEnvironment.setQualifiers("zh-rTW-xxhdpi")
        try{render(320,240,1.5f,"buddy_clock_high_density",true)}finally{RuntimeEnvironment.setQualifiers("zh-rTW-mdpi")}
    }
}
