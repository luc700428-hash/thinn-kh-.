package io.atmos.weather.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import io.atmos.weather.earthquake.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicTabletDashboardRenderingTest:TabletDockFixture() {
    @Test fun mapKeepsItsLayerAndZoomAcrossTabletAndSplitWindows() {
        val ctx=RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().clear().commit();ExpTechSeismicService.live.value=RealtimeDisplay()
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            windowWidth=1280;windowHeight=900
            render(dark,scale,"seismic-tablet-dashboard",{DockHost(1,{}){SeismicLiveMapPage({}, {},emptyList(),onRefreshReports={})}}){view,_->
                tapVisible(view,"測站")
                val plus=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&"放大" in it.config[SemanticsProperties.ContentDescription]&&it.config.contains(SemanticsActions.OnClick)}
                assertTrue(plus.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
                for((w,h) in listOf(1280 to 900,900 to 1280,600 to 960,450 to 960,1280 to 900)) {
                    resize(view,w,h);assertDockOutsideContent(view)
                    val map=tagged(view,"seismic-monitor-map")
                    assertEquals("縮放 1.5×",map.config[SemanticsProperties.StateDescription])
                    assertTrue(nodes(view).single{text(it,"測站")&&it.config.contains(SemanticsProperties.Selected)}.config[SemanticsProperties.Selected])
                    assertTrue(map.size.width>250);assertTrue(map.size.height>=180)
                    val summary=tagged(view,"seismic-observation-summary")
                    if(w==1280)assertTrue(summary.positionInRoot.x>map.positionInRoot.x+map.size.width)
                    else if(w<=600)assertTrue(summary.positionInRoot.y+summary.size.height<=map.positionInRoot.y)
                    for(label in listOf("有效測站","最大震度","最大 PGA"))assertFalse(layout(nodes(view).single{text(it,label)}).hasVisualOverflow)
                    save(view,"seismic-dashboard-${w}x$h-$dark-$scale")
                }
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicTabletSettingsRenderingTest:TabletDockFixture() {
    @Test fun panelsReflowWithoutLosingTheSelectedTab() {
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            ctx.getSharedPreferences("thinn_seismic_v15",0).edit().clear().commit();windowWidth=1280;windowHeight=900
            render(dark,scale,"seismic-tablet-settings",{EarthquakeSettingsPage{}}){_,_->
                val dialog=ShadowDialog.getLatestDialog();val sheet=dialog.window!!.decorView;pump(sheet).recycle();click(sheet,"警報")
                for((w,h) in listOf(1280 to 900,600 to 960,1280 to 900)) {
                    resize(sheet,w,h)
                    val panels=nodes(sheet).filter{it.config.contains(SemanticsProperties.TestTag)&&it.config[SemanticsProperties.TestTag].startsWith("seismic-settings-panel-")}
                    assertTrue(panels.size>=4)
                    if(w==1280)assertTrue(panels[1].positionInRoot.x>panels[0].positionInRoot.x+panels[0].size.width)
                    else assertEquals(panels[0].positionInRoot.x,panels[1].positionInRoot.x,1f)
                    assertTrue(nodes(sheet).single{text(it,"警報")&&it.config.contains(SemanticsProperties.Selected)}.config[SemanticsProperties.Selected])
                    for(label in listOf("一般","警報","音效","關於"))assertFalse(layout(nodes(sheet).single{text(it,label)&&it.config.contains(SemanticsProperties.Selected)}).hasVisualOverflow)
                    save(sheet,"seismic-settings-${w}x$h-$dark-$scale")
                }
                dialog.dismiss()
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicTabletWarningRenderingTest:TabletDockFixture() {
    @Test fun criticalValuesAndLargerMapReflowTogether() {
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        SeismicAlertDelivery.active.value=null;SeismicUserLocation.choose(ctx,"臺北市")
        val now=System.currentTimeMillis()
        val event=EewIncident("tablet-display",1,"cwa",now,"花蓮縣近海",6.2,18.0,23.9,121.6,false,false,
            localIntensityByCounty=mapOf("臺北市" to "3"),arrivalAtMsByCounty=mapOf("臺北市" to now+60_000))
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            windowWidth=1280;windowHeight=900
            render(dark,scale,"seismic-tablet-warning",{SeismicWarningOverlay(event,true,"臺北市",{})}){_,_->
                val dialog=ShadowDialog.getLatestDialog();val sheet=dialog.window!!.decorView
                for((w,h) in listOf(1280 to 900,600 to 960,1280 to 900)) {
                    resize(sheet,w,h);val map=tagged(sheet,"seismic-warning-map")
                    assertTrue(map.size.width>=300);assertTrue(map.size.height>=180)
                    if(w==1280){assertTrue(map.size.height>310);assertTrue(map.positionInRoot.x>500)}
                    for(label in listOf("3 級","M 6.2","18 km"))assertFalse(layout(nodes(sheet).first{text(it,label)}).hasVisualOverflow)
                    save(sheet,"seismic-warning-${w}x$h-$dark-$scale")
                }
                dialog.dismiss()
            }
        }
    }
}
