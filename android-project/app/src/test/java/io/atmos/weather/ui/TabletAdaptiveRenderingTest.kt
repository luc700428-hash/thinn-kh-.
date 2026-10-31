package io.atmos.weather.ui

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.atmos.weather.alarm.AlarmFullScreen
import io.atmos.weather.next.V25HomeHero
import io.atmos.weather.presentation.home.WeatherAppearance
import io.atmos.weather.presentation.home.WeatherIndicatorGrid
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

abstract class TabletRenderingFixture:WeatherInformationRenderingFixture() {
    override var windowWidth=1280
    override var windowHeight=800
    protected fun tagged(view:View,tag:String)=nodes(view).single {
        it.config.contains(SemanticsProperties.TestTag)&&it.config[SemanticsProperties.TestTag]==tag
    }
    protected fun resize(view:View,width:Int,height:Int) {
        windowWidth=width;windowHeight=height;view.requestLayout();pump(view).recycle()
    }
    protected fun visible(bounds:Rect) {
        assertTrue("Outside window: $bounds in ${windowWidth}x$windowHeight",bounds.left>=0 && bounds.top>=0 && bounds.right<=windowWidth+1 && bounds.bottom<=windowHeight+1)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletWeatherResizeRenderingTest:TabletRenderingFixture() {
    @Test fun forecastReflowsWithoutLosingSearchOrSliderStateWhileResizing() {
        var starts=0
        render(true,1f,"tablet-weather",{
            AdaptiveWindowProvider {
                CompositionLocalProvider(LocalWeatherCardPalette provides rememberWeatherCardPalette()) {
                    AdaptiveScrollingPanes(leading={
                        LaunchedEffect(Unit){starts++}
                        var query by remember{mutableStateOf("臺北")}
                        BasicTextField(query,{query=it},Modifier.fillMaxWidth().testTag("city-query"))
                        V25HomeHero(24,"rain","小雨",28,23,22,false,63,Modifier.testTag("weather-hero"))
                    },trailing={
                        var value by remember{mutableFloatStateOf(.4f)}
                        Column(Modifier.fillMaxWidth().testTag("forecast-pane")) {
                            AppText("逐時天氣",color=MaterialTheme.colorScheme.onSurface)
                            GlassSlider(value,{value=it},Modifier.testTag("forecast-slider"))
                            WeatherIndicatorGrid(weather())
                        }
                    })
                }
            }
        }){view,_->
            val edit=tagged(view,"city-query");assertTrue(edit.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("嘉義市")))
            assertTrue(tagged(view,"forecast-slider").config[SemanticsActions.SetProgress].action!!.invoke(.8f));pump(view).recycle()
            for((width,height) in listOf(1280 to 800,800 to 1280,768 to 1024,1024 to 768,480 to 800,1280 to 400,1600 to 1000)) {
                resize(view,width,height)
                val hero=tagged(view,"weather-hero").boundsInRoot
                val forecast=tagged(view,"forecast-pane").boundsInRoot
                if(width>=840&&height>=480)assertTrue("Expected dual panes at $width x $height",forecast.left>hero.right)
                else assertEquals("Expected stacked panes at $width x $height",hero.left,forecast.left,1f)
                assertTrue(tagged(view,"city-query").config[SemanticsProperties.EditableText].text.contains("嘉義市"))
                assertEquals(.8f,tagged(view,"forecast-slider").config[SemanticsProperties.ProgressBarRangeInfo].current,.01f)
                save(view,"tablet-weather-${width}x$height")
            }
            assertEquals("Resizing must not relaunch the weather pane",1,starts)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletToolsRenderingTest:TabletRenderingFixture() {
    @Test fun realToolGroupsStayReadableAndActionsWorkInPortraitLandscapeAndSplitScreen() {
        var radar=0
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            render(dark,scale,"tablet-tools",{AdaptiveWindowProvider {AppGlassToolsPage({}, {}, {}, {},onRadar={radar++})}}){view,_->
                for((w,h) in listOf(1280 to 800,768 to 1024,600 to 960,480 to 800)) {
                    resize(view,w,h)
                    val first=nodes(view).single{text(it,"日常生活")}.boundsInRoot
                    val second=nodes(view).single{text(it,"天氣與防災")}.boundsInRoot
                    if(w==1280||(w==768&&scale==1f))assertTrue(second.left>first.right)
                    else assertEquals(first.left,second.left,1f)
                    val row=nodes(view).single{text(it,"降雨雷達")&&it.config.contains(SemanticsActions.OnClick)}
                    val result=layout(row)
                    save(view,"tablet-tools-${w}x$h-$dark-$scale")
                    assertFalse("Tool title clipped at $w x $h, scale=$scale: ${result.layoutInput.text}, ${result.size}, ${result.layoutInput.constraints}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}",result.hasVisualOverflow)
                    assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
                }
                val search=nodes(view).single{it.config.contains(SemanticsActions.SetText)}
                assertTrue(search.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("世界時鐘")));pump(view).recycle()
                assertTrue(nodes(view).any{text(it,"世界時鐘")})
                assertTrue(nodes(view).none{text(it,"降雨雷達")})
            }
        }
        assertEquals(16,radar)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletSettingsRenderingTest:TabletRenderingFixture() {
    @Test fun settingsCategoriesUseAvailableWidthAndFontSizeAndKeepTheirActions() {
        var category=""
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            render(dark,scale,"tablet-settings",{AdaptiveWindowProvider {
                AppGlassSettingsPage(WeatherAppearance(),{},false,{}, {}, {}, {}, {},onAdvancedCategory={category=it})
            }}){view,_->
                for((w,h) in listOf(1280 to 800,800 to 1280,480 to 800,1280 to 400)) {
                    resize(view,w,h)
                    val first=nodes(view).single{text(it,"外觀與操作")}
                    val second=nodes(view).single{text(it,"桌面與即時資訊")}
                    // A new Dock preference makes the first group taller. The next
                    // heading can be below a short viewport: compare its actual
                    // placement, rather than the empty clipped semantics bounds.
                    assertTrue(first.size.width>0&&second.size.width>0)
                    if(h>=480&&(w==1280||(w==800&&scale==1f)))assertTrue(second.positionInRoot.x>first.positionInRoot.x+first.size.width)
                    else assertEquals(first.positionInRoot.x,second.positionInRoot.x,1f)
                    // Verify every group remains reachable by scrolling, including
                    // 150% text in the short landscape window.
                    for(label in listOf("桌面與即時資訊","提醒與生活","系統與資料","字體與閱讀")) {
                        repeat(4) {
                            val target=nodes(view).single{text(it,label)}
                            val b=target.boundsInRoot
                            if(b.width>0&&b.height>=target.size.height-1&&b.top>=53f&&b.bottom<=h) return@repeat
                            val scroller=nodes(view).single{it.config.contains(SemanticsActions.ScrollBy)}
                            assertTrue(scroller.config[SemanticsActions.ScrollBy].action!!.invoke(0f,target.positionInRoot.y-h*.5f))
                            pump(view).recycle()
                        }
                        val target=nodes(view).single{text(it,label)}
                        assertTrue("Unreachable $label at $w x $h, font=$scale",target.boundsInRoot.width>0&&target.boundsInRoot.height>=target.size.height-1&&target.boundsInRoot.top>=53f)
                        visible(target.boundsInRoot)
                    }
                    click(view,"字體與閱讀");assertEquals("外觀",category)
                    save(view,"tablet-settings-${w}x$h-$dark-$scale")
                }
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletRadarLayoutRenderingTest:TabletRenderingFixture() {
    @Test fun radarStaysSquareWithReachableControlsAndPreservesScrubbingAcrossWindowChanges() {
        render(true,1.5f,"tablet-radar-layout",{AdaptiveWindowProvider {
            AdaptiveMapInspector(map={m->Box(m.background(MaterialTheme.colorScheme.surface).testTag("radar-map"))},controls={
                var opacity by remember{mutableFloatStateOf(.5f)}
                AppText("雷達播放控制",Modifier.testTag("radar-controls"),color=MaterialTheme.colorScheme.onSurface)
                GlassSlider(opacity,{opacity=it},Modifier.testTag("radar-opacity"))
                repeat(14){AppText("回波時間：10/30 06:20",Modifier.padding(vertical=12.dp),color=MaterialTheme.colorScheme.onSurface)}
            })
        }}){view,_->
            assertTrue(tagged(view,"radar-opacity").config[SemanticsActions.SetProgress].action!!.invoke(.7f));pump(view).recycle()
            for((w,h) in listOf(1280 to 800,1024 to 768,768 to 1024,480 to 800,1280 to 360,800 to 360,600 to 360,480 to 360)) {
                resize(view,w,h)
                val map=tagged(view,"radar-map").boundsInRoot
                val controls=tagged(view,"radar-controls").boundsInRoot
                assertEquals(map.width,map.height,1f)
                if(w>=840||(w>=600&&h<480)){assertTrue(controls.left>map.right);visible(map)}
                else assertTrue(controls.top>=map.bottom)
                assertTrue("Map gestures must leave controls reachable",controls.top<h)
                assertEquals(.7f,tagged(view,"radar-opacity").config[SemanticsProperties.ProgressBarRangeInfo].current,.01f)
                save(view,"tablet-radar-${w}x$h")
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletAlarmRenderingTest:TabletRenderingFixture() {
    @Test fun alarmClockAndStopControlsDoNotOverlapOnTabletsOrShortLandscapeWindows() {
        var stops=0;var snoozes=0
        render(true,1.5f,"tablet-alarm",{AlarmFullScreen("07:30","早安天氣",true,true,{snoozes++},{stops++})}){view,_->
            for((w,h) in listOf(1280 to 800,768 to 1024,1280 to 360,600 to 360,480 to 800)) {
                resize(view,w,h)
                val clock=nodes(view).single{text(it,"07:30")}.boundsInRoot
                val stop=nodes(view).single{it.config.contains(SemanticsActions.OnClick)&&it.config[SemanticsActions.OnClick].label=="無法滑動？輕觸這裡停止鬧鐘"}
                visible(clock);visible(stop.boundsInRoot)
                assertFalse("Clock must not cover stop control",clock.overlaps(stop.boundsInRoot))
                assertTrue("Controls should not stretch across a tablet",stop.boundsInRoot.width<=560f)
                assertTrue(stop.config[SemanticsActions.OnClick].action!!.invoke())
                click(view,"稍後提醒")
                save(view,"tablet-alarm-${w}x$h")
            }
        }
        assertEquals(5,stops);assertEquals(5,snoozes)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletFormRenderingTest:TabletRenderingFixture() {
    @Test fun shortFormsFloatAtTabletCenterAndRemainUsableInSmallSplitWindows() {
        for((w,h) in listOf(1280 to 800,768 to 1024,480 to 800,1280 to 360)) {
            windowWidth=w;windowHeight=h;var open by mutableStateOf(true);var done=0
            render(false,1.5f,"tablet-form-host",{
                if(open)IOSFormSheet("設定時間",onDismiss={open=false},onDone={done++;open=false}) {
                    AppText("每天早上提醒我查看天氣",Modifier.testTag("form-body"),color=MaterialTheme.colorScheme.onSurface)
                }
            }){_,_->
                val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
                val title=nodes(sheet).single{text(it,"設定時間")}
                assertEquals(w/2f,title.boundsInRoot.center.x,2f)
                visible(title.boundsInRoot);assertFalse(layout(title).hasVisualOverflow)
                if(w>=600&&h>=480)assertTrue("Tablet form should float",title.boundsInRoot.top>h*.25f&&title.boundsInRoot.bottom<h*.75f)
                save(sheet,"tablet-form-${w}x$h")
                val confirm=nodes(sheet).single{it.config.contains(SemanticsProperties.ContentDescription)&&"確認" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
                assertTrue(confirm.config[SemanticsActions.OnClick].action!!.invoke());pump(sheet).recycle()
                assertEquals(1,done)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletSeismicWarningRenderingTest:TabletRenderingFixture() {
    @Test fun emergencyLayoutKeepsMapAndLocalWarningVisibleAndCanBeDismissedAtAnyWidth() {
        val ctx=org.robolectric.RuntimeEnvironment.getApplication()
        (ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager).ringerMode=android.media.AudioManager.RINGER_MODE_SILENT
        io.atmos.weather.earthquake.SeismicUserLocation.choose(ctx,"臺北市")
        for((w,h) in listOf(1280 to 800,768 to 1024,480 to 800)) {
            windowWidth=w;windowHeight=h;var open by mutableStateOf(true)
            val incident=io.atmos.weather.earthquake.SeismicSimulation.scenario(System.currentTimeMillis(),true)
            render(true,1f,"tablet-eew-host",{if(open)io.atmos.weather.earthquake.SeismicWarningOverlay(incident,true,"臺北市",{open=false},simulation=true)}){host,_->
                val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
                val heading=nodes(sheet).single{text(it,"臺灣 921 大地震演練")}
                val warning=nodes(sheet).single{text(it,"收到地震速報 · 慎防強烈搖晃")}
                visible(heading.boundsInRoot);assertFalse(layout(heading).hasVisualOverflow)
                if(w>=840){visible(warning.boundsInRoot);assertTrue(warning.boundsInRoot.left>w/3)}
                val close=nodes(sheet).single{text(it,"結束模擬演練")&&it.config.contains(SemanticsActions.OnClick)}
                visible(close.boundsInRoot)
                save(sheet,"tablet-eew-${w}x$h")
                click(sheet,"結束模擬演練");pump(host).recycle();assertFalse(open)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TabletSeismicSettingsRenderingTest:TabletRenderingFixture() {
    @Test fun monitorTabsRemainReadableAndKeepSelectionAcrossTabletAndSplitScreenSizes() {
        val ctx=org.robolectric.RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putBoolean("auto_start_realtime_on_app_open",false).commit()
        render(false,1.5f,"tablet-monitor-host",{io.atmos.weather.earthquake.EarthquakeSettingsPage{}}){_,_->
            val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
            click(sheet,"警報")
            for((w,h) in listOf(1280 to 800,768 to 1024,480 to 800,1280 to 400)) {
                resize(sheet,w,h)
                for(label in listOf("一般","警報","音效","關於")) {
                    val tab=nodes(sheet).single{text(it,label)&&it.config.contains(SemanticsActions.OnClick)}
                    visible(tab.boundsInRoot);assertFalse(layout(tab).hasVisualOverflow)
                }
                val selected=nodes(sheet).single{text(it,"警報")&&it.config.contains(SemanticsProperties.Selected)}
                assertTrue(selected.config[SemanticsProperties.Selected])
                save(sheet,"tablet-monitor-${w}x$h")
            }
        }
    }
}
