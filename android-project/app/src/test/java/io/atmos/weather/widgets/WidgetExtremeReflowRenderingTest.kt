package io.atmos.weather.widgets
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextClock
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rTW-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetExtremeReflowRenderingTest{
    private fun views(v:View):List<View> =listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Test(timeout=240_000) fun allFamiliesRenderWideTowersAndLargeTypeWithoutInvertedPrimarySizes(){
        val renderer=WidgetRenderingTest();renderer.prepare();val ctx=RuntimeEnvironment.getApplication()
        for(variant in listOf(0,5,10,15,20,25,26,27,28))for((w,h) in listOf(96 to 800,800 to 96,180 to 600,600 to 180,320 to 320)){
            var previous=0f
            for(custom in listOf(.8f,1f,1.6f)){
                ctx.getSharedPreferences("thinn_settings",0).edit().putFloat(WidgetTypography.KEY,custom).commit()
                renderer.render(variant,w,h,1f,"extreme-${variant}-${w}x$h-$custom"){view->
                    val children=views(view).filterIsInstance<TextView>().filter{it.visibility==View.VISIBLE&&it.text.isNotBlank()}
                    val primary=if(variant in listOf(10,26,28))children.filterIsInstance<TextClock>().firstOrNull{it.format24Hour.toString()=="HH:mm"}
                        else children.firstOrNull{it.text.toString()=="24°"}
                    if(variant !in listOf(5,15)){
                        assertNotNull("Missing primary $variant ${w}x$h $custom",primary)
                        assertTrue("Primary font shrank $variant ${w}x$h $custom",primary!!.textSize>previous);previous=primary.textSize
                    }
                }
            }
        }
        ctx.getSharedPreferences("thinn_settings",0).edit().putFloat(WidgetTypography.KEY,1f).commit()
        renderer.render(28,320,180,1f,"buddy_clock_picker")
        RuntimeEnvironment.setQualifiers("zh-rTW-xxhdpi")
        try{renderer.render(28,320,180,1f,"buddy_clock_picker_hd")}finally{RuntimeEnvironment.setQualifiers("zh-rTW-mdpi")}
    }
}
