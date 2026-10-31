package io.atmos.weather.ui

import android.app.Application
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.InputDevice
import androidx.compose.ui.node.RootForTest
import android.view.View
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.atmos.weather.domain.model.SampleCities
import io.atmos.weather.presentation.home.WeatherAppearance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

abstract class TabletDockFixture:IPadFixture() {
    @Composable protected fun DockHost(selected:Int,onSelect:(Int)->Unit,content:@Composable ()->Unit) {
        CompositionLocalProvider(LocalDevicePosture provides DevicePosture(DeviceKind.TABLET)) {
            TabletWorkspaceShell(selected,onSelect,{},SampleCities.all.take(2),SampleCities.all.first(),{},"24° · 小雨",content)
        }
    }
    protected fun composeOwner(v:View):View?=if(v is RootForTest)v else if(v is ViewGroup)(0 until v.childCount).firstNotNullOfOrNull{composeOwner(v.getChildAt(it))} else null
    protected fun tabs(view:View)=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab}
    protected fun assertDockOutsideContent(view:View) {
        val dock=tagged(view,"tablet-navigation-dock").boundsInRoot
        val body=tagged(view,"tablet-main-content").boundsInRoot
        visible(dock);visible(body);assertFalse("Dock must not obscure readable content",dock.overlaps(body))
        assertEquals(4,tabs(view).size)
        assertEquals(1,tabs(view).count{it.config[SemanticsProperties.Selected]})
        tabs(view).forEach {val result=layout(it);assertFalse("Clipped navigation label ${result.layoutInput.text}, size=${result.size}, constraints=${result.layoutInput.constraints}, style=${result.layoutInput.style}",result.hasVisualOverflow)}
        assertTrue(nodes(view).none{it.config.contains(SemanticsProperties.ContentDescription)&&"開啟側邊欄" in it.config[SemanticsProperties.ContentDescription]})
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletDockPositionsRenderingTest:TabletDockFixture() {
    @Test fun allFourEdgesStayReadableInBothThemesAndRetainLiveFormState() {
        val context=RuntimeEnvironment.getApplication()
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            var starts=0;var selected by mutableIntStateOf(0)
            windowWidth=1280;windowHeight=800
            render(dark,scale,"tablet-dock-edges",{DockHost(selected,{selected=it}) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    LaunchedEffect(Unit){starts++}
                    var query by remember{mutableStateOf("嘉義")};var value by remember{mutableFloatStateOf(.5f)}
                    BasicTextField(query,{query=it},Modifier.fillMaxWidth().testTag("dock-query"))
                    GlassSlider(value,{value=it},Modifier.testTag("dock-slider"))
                }
            }}){view,_->
                assertEquals(TabletDockPosition.RIGHT,TabletDockPreference.read(context))
                assertTrue(tagged(view,"dock-query").config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("阿里山")))
                assertTrue(tagged(view,"dock-slider").config[SemanticsActions.SetProgress].action!!.invoke(.8f));pump(view).recycle()
                for((w,h) in listOf(1280 to 800,768 to 1024,600 to 960,1200 to 360)) {
                    resize(view,w,h)
                    for(position in TabletDockPosition.entries) {
                        TabletDockPreference.save(context,position);pump(view).recycle()
                        assertDockOutsideContent(view)
                        val dock=tagged(view,"tablet-navigation-dock").boundsInRoot
                        when(position){
                            TabletDockPosition.RIGHT->assertTrue(dock.center.x>w*.75f)
                            TabletDockPosition.LEFT->assertTrue(dock.center.x<w*.25f)
                            TabletDockPosition.TOP->assertTrue(dock.center.y<h*.25f)
                            TabletDockPosition.BOTTOM->assertTrue(dock.center.y>h*.75f)
                        }
                        tapVisible(view,"設定");assertEquals(3,selected)
                        assertEquals("阿里山",tagged(view,"dock-query").config[SemanticsProperties.EditableText].text)
                        assertEquals(.8f,tagged(view,"dock-slider").config[SemanticsProperties.ProgressBarRangeInfo].current,.01f)
                        save(view,"tablet-dock-${position.id}-${w}x$h-$dark-$scale")
                    }
                }
                resize(view,1280,800);TabletDockPreference.save(context,TabletDockPosition.RIGHT);pump(view).recycle()
                for(inset in listOf(400,600,0)) {
                    val keyboard=androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,inset))
                        .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),inset>0).build()
                    androidx.core.view.ViewCompat.dispatchApplyWindowInsets(composeOwner(view)!!,keyboard);pump(view).recycle()
                    assertDockOutsideContent(view)
                    assertTrue("Keyboard hides Dock",tagged(view,"tablet-navigation-dock").boundsInRoot.bottom<=800-inset+1)
                    assertEquals("阿里山",tagged(view,"dock-query").config[SemanticsProperties.EditableText].text)
                }
                assertEquals(1,starts)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletDockSettingsRenderingTest:TabletDockFixture() {
    @Test fun settingsOfferVisibleExclusiveChoicesAndMoveTheLiveDock() {
        val context=RuntimeEnvironment.getApplication()
        render(false,1.5f,"tablet-dock-settings",{DockHost(3,{}) {
            AppGlassSettingsPage(WeatherAppearance(),{},false,{}, {}, {}, {}, {})
        }}){view,_->
            tapVisible(view,"平板 Dock 位置")
            for(position in TabletDockPosition.entries) {
                val choice=nodes(view).single{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.RadioButton&&
                    it.config.contains(SemanticsProperties.Text)&&it.config[SemanticsProperties.Text].any{t->t.text.startsWith(position.title)}}
                assertTrue(choice.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
                assertEquals(position,TabletDockPreference.read(context));assertDockOutsideContent(view)
                val choices=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.RadioButton}
                assertEquals(4,choices.size);assertEquals(1,choices.count{it.config[SemanticsProperties.Selected]})
                assertTrue(choices.single{it.config[SemanticsProperties.Selected]}.config[SemanticsProperties.Text].any{it.text.startsWith(position.title)})
                save(view,"tablet-dock-settings-${position.id}")
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletDockGestureRenderingTest:TabletDockFixture() {
    @Test fun verticalAndHorizontalScrubbingNavigateOnceAndCancellationKeepsThePage() {
        val context=RuntimeEnvironment.getApplication();var selected by mutableIntStateOf(0);var commits=0;var observedReduce=true
        render(true,1f,"tablet-dock-gesture",{DockHost(selected,{selected=it;commits++}) {
            val appReduce=rememberAppleReduceMotion();SideEffect{observedReduce=appReduce}
            AppText("目前頁面 $selected")
        }}){view,_->
            val input=composeOwner(view)!!
            fun send(action:Int,point:Offset,down:Long) {
                val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point.x,point.y,0)
                e.source=InputDevice.SOURCE_TOUCHSCREEN;input.dispatchTouchEvent(e);e.recycle();pump(view).recycle()
            }
            fun gesture(from:Offset,to:Offset,cancel:Boolean) {
                val down=SystemClock.uptimeMillis();send(MotionEvent.ACTION_DOWN,from,down)
                for(i in 1..10)send(MotionEvent.ACTION_MOVE,from+(to-from)*(i/10f),down)
                send(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,to,down)
            }
            for(position in TabletDockPosition.entries) {
                TabletDockPreference.save(context,position);selected=0;pump(view).recycle()
                val first=tabs(view).single{text(it,"天氣")}.boundsInRoot.center
                val last=tabs(view).single{text(it,"設定")}.boundsInRoot.center
                val before=commits;save(view,"tablet-dock-gesture-${position.id}-before");gesture(first,last,false);save(view,"tablet-dock-gesture-${position.id}-after")
                assertEquals("Drag did not switch $position",3,selected);assertEquals(before+1,commits)
                gesture(last,first,true);assertEquals(3,selected);assertEquals(before+1,commits)
            }

            // Robolectric otherwise auto advances all vsync callbacks through the complete animation.
            val wasPaused=org.robolectric.shadows.ShadowChoreographer.isPaused()
            val frameDelay=org.robolectric.shadows.ShadowChoreographer.getFrameDelay()
            org.robolectric.shadows.ShadowChoreographer.setPaused(true)
            org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
            try {
                android.provider.Settings.Global.putFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)
                context.getSharedPreferences("thinn_settings",0).edit().putBoolean("reduce_motion",false).apply()
                TabletDockPreference.save(context,TabletDockPosition.RIGHT);repeat(4){pump(view).recycle()}
                assertFalse(observedReduce)
                val start=tagged(view,"tablet-navigation-dock").boundsInRoot
                TabletDockPreference.save(context,TabletDockPosition.LEFT)
                var captured=false;val trace=mutableListOf<String>()
                for(i in 0..40) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
                    view.measure(View.MeasureSpec.makeMeasureSpec(windowWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(windowHeight,View.MeasureSpec.EXACTLY))
                    view.layout(0,0,windowWidth,windowHeight)
                    val bitmap=android.graphics.Bitmap.createBitmap(windowWidth,windowHeight,android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap));bitmap.recycle()
                    val middle=tagged(view,"tablet-navigation-dock").boundsInRoot
                    if(i%4==0)trace+="$i:${middle.left}"
                    if(middle.left>13f&&middle.left<start.left-1f){captured=true;break}
                }
                assertTrue("Position must move continuously, not jump: $trace",captured)
                save(view,"tablet-dock-linear-travel")
                settle(view){tagged(view,"tablet-navigation-dock").boundsInRoot.left<13f}
                assertDockOutsideContent(view)
            } finally {
                org.robolectric.shadows.ShadowChoreographer.setFrameDelay(frameDelay)
                org.robolectric.shadows.ShadowChoreographer.setPaused(wasPaused)
            }
        }
    }
}
