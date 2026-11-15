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
import androidx.compose.material.icons.rounded.Search
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

/** iOS 27 tab-bar minimize, glass split/merge with the search bubble, and menus grown from their value. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LiquidGlassMorphRenderingTest {
    private val w=400;private val h=760
    private fun nodes(view:View):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap{walk(it)}
        if(view is RootForTest)return walk(view.semanticsOwner.rootSemanticsNode)
        return if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList()
    }
    private fun described(view:View,text:String)=nodes(view).filter{
        it.config.contains(SemanticsProperties.ContentDescription)&&it.config[SemanticsProperties.ContentDescription].any{d->d.startsWith(text)}}
    private fun pump(view:View,ms:Long=100,times:Int=3):Bitmap {
        repeat(times){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)}
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{view.draw(Canvas(it))}
    }
    private fun save(b:Bitmap,name:String){val dir=File("build/liquid-glass-renders").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}

    @Test fun dockCondensesSplitsSearchAndRestores() {
        val ctx=RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("thinn_settings",0).edit().clear().putBoolean("reduce_motion",false).putBoolean("sound_enabled",false).commit()
        IOS27AppearanceMode.update(ctx,"light")
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup();val a=controller.get();val view=ComposeView(a)
        val minimized=mutableStateOf(false);var searched=false
        try {
            view.setContent{ThinnAppTheme{
                val scene=rememberThinnBackdrop()
                Box(Modifier.fillMaxSize()) {
                    ThinnBackdropCapture(scene,Modifier.matchParentSize()) {
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF3A7BD5),Color(0xFF2E8B57)))))
                    }
                    CompositionLocalProvider(LocalThinnBackdrop provides scene) {
                        Column(Modifier.padding(24.dp)) {
                            ApplePullDownMenu("單位",listOf("攝氏","華氏"),"攝氏",{})
                        }
                        AppGlassNavigation(0,{},Modifier.align(Alignment.BottomCenter).padding(bottom=24.dp).testTag("dock"),
                            minimized=minimized.value,onExpand={minimized.value=false},
                            accessory=io.atmos.weather.next.NextToolAction("search","搜尋城市",androidx.compose.material.icons.Icons.Rounded.Search),
                            onAccessory={searched=true})
                    }
                }
            }}
            val parent=FrameLayout(a).apply{addView(view,ViewGroup.LayoutParams(w,h))};a.setContentView(parent)
            save(pump(parent),"morph-rest")
            assertTrue(described(view,"展開分頁列").isEmpty())
            minimized.value=true
            save(pump(parent,16,4),"morph-splitting")
            save(pump(parent,120,6),"morph-minimized")
            val expand=described(view,"展開分頁列").single()
            assertTrue("Condensed bar must name the current tab",expand.config[SemanticsProperties.ContentDescription].first().endsWith("天氣"))
            assertTrue("Condensed bar must be small",expand.boundsInRoot.width<100f)
            val search=described(view,"搜尋城市").single()
            assertTrue("Search bubble sits at the trailing edge",search.boundsInRoot.center.x>w*.75f)
            assertTrue(search.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke());pump(parent)
            assertTrue(searched)
            assertTrue(expand.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke())
            save(pump(parent,16,4),"morph-merging")
            pump(parent,120,6).recycle()
            assertTrue("Bar restored with every tab",described(view,"設定").isNotEmpty()&&described(view,"展開分頁列").isEmpty())
            // The pull-down menu grows out of its value as one piece of glass.
            val menu=nodes(view).first{it.config.contains(SemanticsProperties.Text)&&it.config[SemanticsProperties.Text].any{t->t.text=="單位"}}
            val row=generateSequence(menu){it.parent}.first{it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)}
            assertTrue(row.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke())
            save(pump(parent,16,3),"menu-growing")
            save(pump(parent,120,6),"menu-open")
        } finally {view.disposeComposition();controller.pause().stop().destroy()}
    }
}
