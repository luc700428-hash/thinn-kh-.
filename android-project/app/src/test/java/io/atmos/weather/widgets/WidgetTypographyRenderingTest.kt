package io.atmos.weather.widgets

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.atmos.weather.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetTypographyRenderingTest {
    private fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{descendants(v.getChildAt(it))} else emptyList()
    @Test fun independentPreferenceGrowsWeatherAndClockTextAndFitsEveryFamily(){
        val renderer=WidgetRenderingTest();renderer.prepare()
        io.atmos.weather.ui.initializeWorkspaceWorkers(RuntimeEnvironment.getApplication())
        val ctx=RuntimeEnvironment.getApplication();val p=ctx.getSharedPreferences("thinn_settings",0)
        val sizes=mutableMapOf<Pair<Int,Float>,Float>()
        for(custom in listOf(.8f,1f,1.6f)){
            p.edit().putFloat(WidgetTypography.KEY,custom).commit()
            for(variant in listOf(0,5,10,15,20,25,26,27,28)){
                for((w,h) in listOf(180 to 180,320 to 320,320 to 600)){
                    renderer.render(variant,w,h,1f,"font_${variant}_${w}x${h}_${custom}"){view->
                        if(w==320&&h==600&&variant==27)assertTrue("Font adjustment must preserve the high/low summary when it fits",descendants(view).filterIsInstance<TextView>().any{it.text.toString().replace(" ","").let{text->text.contains("高26°")&&text.contains("低21°")}})
                        if(w==320&&h==600&&variant in listOf(0,27,28)){
                            val temp=descendants(view).filterIsInstance<TextView>().first{it.text.toString()=="24°"}
                            sizes[variant to custom]=temp.textSize
                        }
                        if(variant==28)assertEquals(View.GONE,view.findViewById<View>(R.id.buddy_clock_refresh).visibility)
                    }
                }
            }
            renderer.render(27,320,320,1.5f,"font_buddy_with_app_large_$custom")
            renderer.render(28,320,600,1.5f,"font_buddy_clock_with_app_large_$custom")
        }
        for(variant in listOf(0,27,28)){
            assertTrue("$variant did not shrink",sizes[variant to .8f]!!<sizes[variant to 1f]!!)
            assertTrue("$variant did not grow",sizes[variant to 1.6f]!!>sizes[variant to 1f]!!)
        }
        WidgetTypography.save(ctx,1.3f)
        assertEquals(1.3f,WidgetTypography.read(ctx),.001f)
        assertEquals("Widget size must not overwrite app reading size",1.5f,p.getFloat("v4_fontscale",1f),.001f)
    }
}
