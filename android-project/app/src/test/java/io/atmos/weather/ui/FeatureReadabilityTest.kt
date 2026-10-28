package io.atmos.weather.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.atmos.weather.domain.model.SampleCities
import io.atmos.weather.sky.SkyPlannerScreen
import kotlinx.coroutines.Dispatchers
import io.atmos.weather.typhoon.TyphoonPageHeader
import org.junit.Assert.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FeatureReadabilityTest {
    private fun nodes(view:View):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    private fun text(n:SemanticsNode,t:String)=n.config.contains(SemanticsProperties.Text)&&n.config[SemanticsProperties.Text].any{it.text==t}
    private fun pump(view:View):Bitmap {
        repeat(3){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
            view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1050,View.MeasureSpec.EXACTLY));view.layout(0,0,400,1050)}
        return Bitmap.createBitmap(400,1050,Bitmap.Config.ARGB_8888).also{view.draw(Canvas(it))}
    }
    private fun render(dark:Boolean,scale:Float,name:String,content:@Composable ()->Unit,check:(View)->Unit) {
        val ctx=RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("thinn_settings",0).edit().clear().putBoolean("reduce_motion",true).putBoolean("reduce_transparency",true)
            .putFloat("v4_fontscale",scale).putFloat("v4_fontweight",400f).commit()
        IOS27AppearanceMode.update(ctx,if(dark)"dark" else "light")
        val c=Robolectric.buildActivity(ComponentActivity::class.java).setup();val a=c.get();val view=ComposeView(a)
        try {view.setContent{ThinnAppTheme(content)}
            val parent=FrameLayout(a).apply{addView(view,ViewGroup.LayoutParams(400,1050))};a.setContentView(parent)
            pump(parent);check(parent)
            val dir=File("build/feature-renders").apply{mkdirs()}
            File(dir,name+".png").outputStream().use{pump(parent).compress(Bitmap.CompressFormat.PNG,100,it)}
        } finally {view.disposeComposition();c.pause().stop().destroy()}
    }
    @Test fun typhoonTitleStaysCenteredWithBothControlsAndLargeReadingSize() {
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            var back=false;var refreshed=false
            render(dark,scale,"typhoon-header-$dark-$scale",{Column(Modifier.fillMaxSize()){TyphoonPageHeader(false,{back=true},{refreshed=true})}}){view->
                val title=nodes(view).single{text(it,"颱風動向與警報")}
                assertEquals(200f,title.boundsInRoot.center.x,1f)
                for((label,callback) in listOf("返回" to {back},"更新官方颱風資料" to {refreshed})) {
                    val button=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&label in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
                    assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke());assertTrue(callback())
                }
            }
        }
    }
    @Test fun moonPageUsesReadableNightLabelsAndVisibleUnlitMoonContour() {
        render(true,1.5f,"moon-dark-150",{SkyPlannerScreen(SampleCities.all.first(),"Asia/Taipei",{},Dispatchers.Unconfined)}){view->
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while(nodes(view).none{text(it,"月相示意 · 依所選地點與時間計算")} && System.nanoTime()<deadline) {
                pump(view);Thread.sleep(10)
            }
            val found=nodes(view).filter{text(it,"月相示意 · 依所選地點與時間計算")}
            assertEquals("Sky content: "+nodes(view).mapNotNull{if(it.config.contains(SemanticsProperties.Text))it.config[SemanticsProperties.Text] else null},1,found.size)
            val label=found.single()
            val layouts=mutableListOf<TextLayoutResult>();assertTrue(label.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            val ink=layouts.first().layoutInput.style.color.luminance();val bg=Color(0xFF1C1C1E).luminance()
            assertTrue((ink+.05f)/(bg+.05f)>=4.5f)
            val image=pump(view);var contour=0
            for(y in 0 until image.height)for(x in 0 until image.width)if(image.getPixel(x,y)==0xffb7cbdd.toInt())contour++
            assertTrue("Dark moon lost its visible outline",contour>15)
        }
    }
}
