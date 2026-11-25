package io.atmos.weather.festival

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-xhdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FestivalWordsRenderingTest {
    private val w=412*2; private val h=900*2
    private fun at(y:Int,m:Int,d:Int,hh:Int,mm:Int,ss:Int)=LocalDateTime.of(y,m,d,hh,mm,ss).atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli()
    private fun nodes(view:View):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    private fun pump(view:View):Bitmap {
        repeat(3){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)}
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{view.draw(Canvas(it))}
    }
    @Test fun greetingCountdownAndMidnightLineAreLaidOutInsideTheScreen() {
        val cases=listOf(
            "greeting" to at(2026,12,24,21,0,0),
            "countdown" to at(2026,12,31,23,55,30),
            "last-seconds" to at(2026,12,31,23,59,56),
            "midnight" to at(2027,1,1,0,0,2))
        for((name,time) in cases) {
            FestivalDirector.stopPreview()
            FestivalDirector.observe("test-$name",time-5_000)
            FestivalDirector.starts(FestivalDirector.Pass.BANNER,time-3_000)
            val clock=mutableLongStateOf(time)
            val c=Robolectric.buildActivity(ComponentActivity::class.java).setup();val a=c.get();val view=ComposeView(a)
            try {
                view.setContent{ Box(Modifier.fillMaxSize().background(Color(0xFFDCE8F5))){ FestivalWords(clock) } }
                val parent=FrameLayout(a).apply{addView(view,ViewGroup.LayoutParams(w,h))};a.setContentView(parent)
                val bmp=pump(parent)
                val texts=nodes(parent).filter{it.config.contains(SemanticsProperties.Text)}
                assertTrue("$name shows words",texts.isNotEmpty())
                texts.forEach{n->val b=n.boundsInRoot
                    assertTrue("$name ${n.config[SemanticsProperties.Text]} $b",b.left>=0f&&b.right<=w.toFloat()&&b.top>=0f&&b.bottom<=h.toFloat())}
                // Nothing at the very top, where every page keeps its title and back button.
                if(name=="greeting"||name=="countdown") texts.forEach{assertTrue("$name ${it.boundsInRoot}",it.boundsInRoot.top>h*.15f)}
                File("build/festival").apply{mkdirs()}.let{File(it,"words-$name.png").outputStream().use{s->bmp.compress(Bitmap.CompressFormat.PNG,100,s)}}
            } finally {view.disposeComposition();c.pause().stop().destroy()}
        }
    }
}
