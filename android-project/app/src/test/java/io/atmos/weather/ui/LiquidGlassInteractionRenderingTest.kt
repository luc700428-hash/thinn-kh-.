package io.atmos.weather.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.unit.dp
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

/** iOS 27 interactive glass: held controls lift into clear lenses and settle back on release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LiquidGlassInteractionRenderingTest {
    private val w=400;private val h=760
    private fun nodes(view:View):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    private fun tagged(view:View,tag:String)=nodes(view).filter{
        it.config.contains(SemanticsProperties.TestTag)&&it.config[SemanticsProperties.TestTag]==tag}
    private fun pump(view:View,ms:Long=100):Bitmap {
        repeat(3){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)}
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{view.draw(Canvas(it))}
    }
    private fun save(b:Bitmap,name:String){val dir=File("build/liquid-glass-renders").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    private var downAt=0L
    private fun touch(view:View,action:Int,x:Float,y:Float) {
        val now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN)downAt=now
        val e=MotionEvent.obtain(downAt,now,action,x,y,0);view.dispatchTouchEvent(e);e.recycle()
    }

    @Test fun heldGlassLiftsIntoLensesAndSettlesBackOnRelease() {
        for(dark in listOf(false,true)) {
            val ctx=RuntimeEnvironment.getApplication()
            ctx.getSharedPreferences("thinn_settings",0).edit().clear().putBoolean("reduce_motion",false)
                .putBoolean("reduce_transparency",false).putBoolean("sound_enabled",false).commit()
            IOS27AppearanceMode.update(ctx,if(dark)"dark" else "light")
            val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup();val a=controller.get();val view=ComposeView(a)
            try {
                view.setContent{ThinnAppTheme{
                    var value by remember{mutableFloatStateOf(.55f)}
                    var on by remember{mutableStateOf(true)}
                    var tab by remember{mutableIntStateOf(0)}
                    val scene=rememberThinnBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        ThinnBackdropCapture(scene,Modifier.matchParentSize()) {
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF3A7BD5),Color(0xFFF2C14E),Color(0xFF2E8B57)))))
                        }
                        CompositionLocalProvider(LocalThinnBackdrop provides scene) {
                        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(28.dp)) {
                            GlassSlider(value,{value=it},Modifier.testTag("slider"))
                            GlassSwitch(on,{on=it},Modifier.testTag("switch"))
                            GlassButton({}, Modifier.testTag("button")){AppText("重新整理")}
                        }
                        AppGlassNavigation(tab,{tab=it},Modifier.align(Alignment.BottomCenter).padding(bottom=24.dp).testTag("dock"))
                    }}
                }}
                val parent=FrameLayout(a).apply{addView(view,ViewGroup.LayoutParams(w,h))};a.setContentView(parent)
                save(pump(parent),"rest-$dark")
                assertTrue("No lens before touch",tagged(view,"dock-liquid-lens").isEmpty())
                // Hold the Dock on the third tab: the clear lens appears and pokes out of the bar.
                val dock=tagged(view,"dock").single().boundsInRoot
                touch(view,MotionEvent.ACTION_DOWN,dock.left+dock.width*.62f,dock.center.y)
                touch(view,MotionEvent.ACTION_MOVE,dock.left+dock.width*.66f,dock.center.y)
                save(pump(parent,60),"dock-held-$dark")
                val lens=tagged(view,"dock-liquid-lens").single().boundsInRoot
                val pill=tagged(view,"dock-selection-lens").single().boundsInRoot
                assertTrue("Held lens must be taller than the resting pill: $lens vs $pill",lens.height>pill.height*1.15f)
                assertTrue("Lens follows the finger",lens.center.x>dock.left+dock.width*.5f)
                touch(view,MotionEvent.ACTION_UP,dock.left+dock.width*.66f,dock.center.y)
                repeat(10){pump(parent).recycle()}
                assertTrue("Lens settles away after release",tagged(view,"dock-liquid-lens").isEmpty())
                // Hold the slider knob and drag it: the knob turns into a magnifying lens.
                val slider=tagged(view,"slider").single().boundsInRoot
                val knobX=slider.left+30f+(slider.width-60f)*.55f
                touch(view,MotionEvent.ACTION_DOWN,knobX,slider.center.y)
                touch(view,MotionEvent.ACTION_MOVE,knobX+30f,slider.center.y)
                touch(view,MotionEvent.ACTION_MOVE,knobX+60f,slider.center.y)
                save(pump(parent,40),"slider-held-$dark")
                touch(view,MotionEvent.ACTION_UP,knobX+60f,slider.center.y)
                val sw=tagged(view,"switch").single().boundsInRoot
                touch(view,MotionEvent.ACTION_DOWN,sw.right-14f,sw.center.y)
                val b=tagged(view,"button").single().boundsInRoot
                save(pump(parent,60),"switch-held-$dark")
                touch(view,MotionEvent.ACTION_CANCEL,sw.right-14f,sw.center.y)
                touch(view,MotionEvent.ACTION_DOWN,b.left+b.width*.8f,b.center.y)
                save(pump(parent,60),"button-held-$dark")
                touch(view,MotionEvent.ACTION_UP,b.left+b.width*.8f,b.center.y)
                repeat(6){pump(parent).recycle()}
                save(pump(parent),"after-$dark")
            } finally {view.disposeComposition();controller.pause().stop().destroy()}
        }
    }
}
