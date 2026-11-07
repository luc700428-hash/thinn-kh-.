package io.atmos.weather.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

/** Compose the real catalogue, including offscreen groups visited by pager precomposition. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ToolsPageRenderingTest {
    private fun nodes(view:View):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    @Test fun completeCatalogueRendersEveryGroupInLightAndDark() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("thinn_settings",0).edit().clear()
            .putBoolean("reduce_motion",true).putBoolean("reduce_transparency",true).commit()
        for(dark in listOf(false,true)) {
            val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val activity=controller.get()
            val view=ComposeView(activity)
            try {
                view.setContent {
                    ThinnAppTheme {
                        MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()) {
                            AppGlassToolsPage(onBriefings={},onUpdates={},onDiagnostics={},onSeismic={})
                        }
                    }
                }
                val parent=FrameLayout(activity).apply{addView(view,ViewGroup.LayoutParams(400,1600))}
                activity.setContentView(parent)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
                parent.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY))
                parent.layout(0,0,400,1600)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
                val bitmap=Bitmap.createBitmap(400,1600,Bitmap.Config.ARGB_8888)
                parent.draw(Canvas(bitmap))
                // Each visible category must draw its own icons in the unified forest identity.
                val headings=listOf("日常生活","天氣與防災","海洋與天文","時間與換算").map{title->
                    nodes(parent).single{it.config.contains(SemanticsProperties.Text)&&
                        it.config[SemanticsProperties.Text].any{t->t.text==title}}.boundsInRoot
                }
                for((index,heading) in headings.withIndex()) {
                    val top=heading.bottom.toInt();val bottom=headings.getOrNull(index+1)?.top?.toInt()?:bitmap.height
                    assertTrue("Category was outside the visible catalogue",top>=0&&bottom<=bitmap.height&&bottom>top)
                    var pixels=0
                    for(y in top until bottom)for(x in 0 until 100)if(bitmap.getPixel(x,y)==0xff2f6858.toInt())pixels++
                    assertTrue("Missing green category icon $index in ${if(dark)"dark" else "light"} catalogue",pixels>100)
                }
                val output=File("build/tools-renders").apply{mkdirs()}
                File(output,"tools_${if(dark)"dark" else "light"}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            } finally {
                view.disposeComposition()
                controller.pause().stop().destroy()
            }
        }
    }
}
