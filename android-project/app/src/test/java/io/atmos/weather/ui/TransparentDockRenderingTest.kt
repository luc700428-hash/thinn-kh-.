package io.atmos.weather.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import io.atmos.weather.domain.model.*
import io.atmos.weather.domain.repository.*
import io.atmos.weather.presentation.home.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class TransparentDockFixture:TabletDockFixture() {
    override val reduceTransparency=false
    protected fun meanChannel(bitmap:Bitmap,x:Int,y:Int,channel:(Int)->Int):Float {
        var sum=0;var count=0
        for(dx in -2..2)for(dy in -1..1) {
            sum+=channel(bitmap.getPixel((x+dx).coerceIn(0,bitmap.width-1),(y+dy).coerceIn(0,bitmap.height-1)))
            count++
        }
        return sum.toFloat()/count
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TransparentDockTransmissionRenderingTest:TransparentDockFixture() {
    @Test fun liveBackgroundRemainsVisibleThroughEveryDockEdgeInBothThemes() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("thinn_crash_diagnostics",0).edit().clear().commit()
        for(dark in listOf(false,true))for(font in listOf(1f,1.5f)) {
            var scene by mutableStateOf(Color(0xFF123C78))
            var observedSource:ThinnBackdrop?=null
            windowWidth=1280;windowHeight=800
            render(dark,font,"transparent-dock-transmission",{
                CompositionLocalProvider(LocalDevicePosture provides DevicePosture(DeviceKind.TABLET)) {
                    TabletWorkspaceShell(0,{},{},SampleCities.all,SampleCities.all.first(),{},"24° · 晴",
                        background=SolidColor(scene),darkBackdrop=true) {
                        val source=LocalThinnBackdrop.current
                        SideEffect {observedSource=source}
                        Box(Modifier.fillMaxSize().padding(20.dp)){AppText("透明度檢查",color=Color.White)}
                    }
                }
            }){view,_->
                for(position in TabletDockPosition.entries) {
                    TabletDockPreference.save(context,position)
                    scene=Color(0xFF123C78);pump(view).recycle();assertDockLayout(view)
                    val dock=tagged(view,"tablet-navigation-dock").boundsInRoot
                    val vertical=dock.height>dock.width
                    val cell=tabs(view).single{text(it,"工具")}.boundsInRoot
                    val x=if(vertical)(dock.left+8).roundToInt() else cell.center.x.roundToInt()
                    val y=if(vertical)cell.center.y.roundToInt() else (dock.top+8).roundToInt()
                    val first=pump(view)
                    val a=meanChannel(first,x,y,android.graphics.Color::green);first.recycle()
                    scene=Color(0xFF237242)
                    val second=pump(view)
                    val b=meanChannel(second,x,y,android.graphics.Color::green)
                    assertTrue("Frosted glass lost its foreground contrast",colorContrast(BrandPalette.dark.onSurface,Color(second.getPixel(x,y)))>=4.5f)
                    second.recycle()
                    // Regular material transmits the live scene while retaining enough tint for labels.
                    assertTrue("Dock hides the changing background: ${position.id}, dark=$dark, font=$font, transmitted=${b-a}",b-a>=12f)
                    assertNotNull(observedSource)
                    assertTrue(observedSource!!.active&&observedSource!!.ready&&!observedSource!!.failed)
                    save(view,"transparent-dock-${position.id}-$dark-$font")
                }
                assertNull("Glass sampling must remain available",io.atmos.weather.CrashDiagnostics.graphics(context))
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class RealWeatherDockContinuityRenderingTest:TransparentDockFixture() {
    @Test fun realWeatherHasNoBackgroundSeamAlongAnyReservedDockLane() {
        installOfflineWorkspaceNetwork()
        val context=RuntimeEnvironment.getApplication();initializeWorkspaceWorkers(context)
        context.getSharedPreferences("thinn_crash_diagnostics",0).edit().clear().commit()
        val repo=object:WeatherDataRepository {
            override suspend fun load(city:WeatherCity,forceRefresh:Boolean)=WeatherLoadResult.Success(
                weather().copy(city=city,condition=WeatherCondition.CLEAR,isDay=true,weatherCode=0,windKmh=0.0),false,false)
        }
        for(dark in listOf(false,true)) {
            val store=ViewModelStore();val vm=WeatherViewModel(repo,context);store.put("weather",vm)
            windowWidth=1280;windowHeight=800
            try {
                render(dark,1f,"transparent-dock-real-weather",{
                    CompositionLocalProvider(LocalDevicePosture provides DevicePosture(DeviceKind.TABLET)) {
                        AdaptiveWindowProvider {WeatherHomeScreen(vm,{})}
                    }
                }){view,_->
                    settle(view){nodes(view).any{it.config.contains(SemanticsProperties.Text)&&
                        it.config[SemanticsProperties.Text].any{t->t.text.contains("現在的天氣")}}}
                    for(position in TabletDockPosition.entries) {
                        TabletDockPreference.save(context,position);pump(view).recycle();assertDockLayout(view)
                        val body=tagged(view,"tablet-main-content").boundsInRoot
                        val dock=tagged(view,"tablet-navigation-dock").boundsInRoot
                        val points=when(position) {
                            TabletDockPosition.BOTTOM->listOf(5 to (dock.top-15).roundToInt(),5 to (dock.top-9).roundToInt())
                            TabletDockPosition.TOP->listOf(5 to (body.top-3).roundToInt(),5 to (body.top+3).roundToInt())
                            TabletDockPosition.LEFT->listOf((body.left-3).roundToInt() to 5,(body.left+3).roundToInt() to 5)
                            TabletDockPosition.RIGHT->listOf((body.right-3).roundToInt() to 5,(body.right+3).roundToInt() to 5)
                        }
                        val bitmap=pump(view)
                        try {
                            val a=points[0];val b=points[1]
                            for(channel in listOf<(Int)->Int>(android.graphics.Color::red,android.graphics.Color::green,android.graphics.Color::blue)) {
                                val delta=abs(meanChannel(bitmap,a.first,a.second,channel)-meanChannel(bitmap,b.first,b.second,channel))
                                assertTrue("An opaque lane interrupts the real weather scene: ${position.id}, dark=$dark, channel jump=$delta",delta<10f)
                            }
                        }finally{bitmap.recycle()}
                        save(view,"transparent-real-weather-${position.id}-$dark")
                        if(position==TabletDockPosition.BOTTOM) {
                            val scrollers=nodes(view).filter{it.config.contains(androidx.compose.ui.semantics.SemanticsActions.ScrollBy)&&
                                it.config.contains(SemanticsProperties.VerticalScrollAxisRange)}
                            assertTrue(scrollers.isNotEmpty())
                            for(scroll in scrollers) {
                                val range=scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                                assertTrue(scroll.config[androidx.compose.ui.semantics.SemanticsActions.ScrollBy].action!!.invoke(0f,range.maxValue()))
                            }
                            pump(view).recycle()
                            fun texts(node:androidx.compose.ui.semantics.SemanticsNode):List<androidx.compose.ui.semantics.SemanticsNode> =
                                (if(node.config.contains(SemanticsProperties.Text))listOf(node) else emptyList())+node.children.flatMap{ texts(it) }
                            for(scroll in scrollers) {
                                val last=texts(scroll).maxByOrNull{it.positionInRoot.y+it.size.height}?:continue
                                assertTrue("The final weather text must scroll above Dock",last.positionInRoot.y+last.size.height<=dock.top-4)
                            }
                            save(view,"transparent-real-weather-bottom-scrolled-$dark")
                        }
                    }
                    assertNull("The full weather/page/Dock graph must remain available",io.atmos.weather.CrashDiagnostics.graphics(context))
                }
            }finally{store.clear()}
        }
    }
}
