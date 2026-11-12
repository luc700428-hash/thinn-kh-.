package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import io.atmos.weather.domain.model.SampleCities
import io.atmos.weather.next.DockLiquidGeometry
import io.atmos.weather.presentation.home.WeatherAppearance
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WorkspaceDockSurfaceRenderingTest:TabletDockFixture() {
    @Test fun weatherReachesEveryEdgeAndLensMatchesActualTabletAndFoldableCells() {
        val context=RuntimeEnvironment.getApplication();val bg=Color(0xFF132D46)
        for(dark in listOf(false,true))for(font in listOf(1f,1.5f)) {
            var posture by mutableStateOf(DevicePosture(DeviceKind.TABLET));var selected by mutableIntStateOf(0)
            windowWidth=1280;windowHeight=800
            render(dark,font,"dock-continuous-surface",{
                CompositionLocalProvider(LocalDevicePosture provides posture) {
                    TabletWorkspaceShell(selected,{selected=it},{},SampleCities.all,SampleCities.all.first(),{},"24° · 小雨",
                        background=SolidColor(bg),darkBackdrop=true) {
                        Box(Modifier.fillMaxSize().background(bg).padding(20.dp)){AppText("天氣內容",color=Color.White)}
                    }
                }
            }){view,_->
                context.getSharedPreferences("thinn_settings",0).edit().putBoolean("reduce_transparency",false).commit()
                pump(view).recycle()
                // Include short landscape, book, tabletop and narrow split windows.
                val cases=listOf(Triple(1280,800,null),Triple(768,1024,null),Triple(1200,360,null),
                    Triple(980,900,WindowFold(Rect(478f,0f,502f,900f),true,true,true)),
                    Triple(1000,1100,WindowFold(Rect(0f,500f,1000f,524f),false,true,true)))
                for((w,h,fold) in cases) {
                    posture=DevicePosture(if(fold==null)DeviceKind.TABLET else DeviceKind.FOLDABLE,fold);resize(view,w,h)
                    for(position in TabletDockPosition.entries) {
                        TabletDockPreference.save(context,position);pump(view).recycle();assertDockLayout(view)
                        val bounds=tagged(view,"tablet-navigation-dock").boundsInRoot
                        val vertical=bounds.height>bounds.width
                        for(index in 0..3) {
                            selected=index;pump(view).recycle()
                            val cell=tabs(view).single{it.config[SemanticsProperties.Selected]}.boundsInRoot
                            val shape=DockLiquidGeometry.shape(if(vertical)bounds.height else bounds.width,
                                if(vertical)bounds.width else bounds.height,1f,4,index.toFloat(),index.toFloat(),vertical)!!
                            val lens=if(vertical)Rect(bounds.left+bounds.width-shape.centerY-shape.height/2,
                                bounds.top+shape.headX-shape.width/2,bounds.left+bounds.width-shape.centerY+shape.height/2,bounds.top+shape.headX+shape.width/2)
                            else Rect(bounds.left+shape.headX-shape.width/2,bounds.top+shape.centerY-shape.height/2,
                                bounds.left+shape.headX+shape.width/2,bounds.top+shape.centerY+shape.height/2)
                            assertEquals(cell.center.x,lens.center.x,1.1f);assertEquals(cell.center.y,lens.center.y,1.1f)
                            assertTrue(lens.width<=cell.width+1f&&lens.height<=cell.height+1f)
                            val minor=if(vertical)lens.width else lens.height
                            val major=if(vertical)lens.height else lens.width
                            assertTrue("Lens must cover the full inner cross-axis",minor>=(if(vertical)cell.width else cell.height)-1.1f)
                            assertTrue("Tablet lens became too small to follow",minor>=32f)
                            assertEquals(minOf(shape.width,shape.height)/2,shape.cornerRadius,.001f)
                            assertTrue(if(vertical)lens.height>=cell.height*.90f else lens.width>=cell.width*.90f)
                        }
                        val bitmap=pump(view)
                        try {
                            for((x,y) in listOf(2 to h/2,w-3 to h/2,w/2 to 2)) {
                                val pixel=bitmap.getPixel(x,y)
                                assertTrue("An opaque strip interrupts the weather background",abs(android.graphics.Color.red(pixel)-19)<=12&&
                                    abs(android.graphics.Color.green(pixel)-45)<=12&&abs(android.graphics.Color.blue(pixel)-70)<=12)
                            }
                            // Even system Light mode must use dark glass above the dark sky.
                            val pixel=bitmap.getPixel(bounds.center.x.toInt(),(bounds.top+8).toInt())
                            assertTrue(android.graphics.Color.red(pixel)<100&&android.graphics.Color.green(pixel)<110)
                        }finally{bitmap.recycle()}
                        save(view,"dock-blended-${w}x$h-${position.id}-$dark-$font")
                    }
                }
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DockPreferenceDeviceRenderingTest:TabletDockFixture() {
    @Test fun normalPhonesHideThePreferenceWhileTabletAndFoldableWindowsKeepIt() {
        var posture by mutableStateOf(DevicePosture(DeviceKind.PHONE))
        windowWidth=1280;windowHeight=800
        render(false,1f,"dock-setting-device",{
            CompositionLocalProvider(LocalDevicePosture provides posture) {
                AdaptiveWindowProvider {AppGlassSettingsPage(WeatherAppearance(),{},false,{},{},{},{},{})}
            }
        }){view,_->
            assertFalse("Landscape phone must not be mistaken for a tablet",nodes(view).any{text(it,"平板 Dock 位置")})
            for(kind in listOf(DeviceKind.TABLET,DeviceKind.FOLDABLE)) {
                posture=DevicePosture(kind);resize(view,480,800)
                assertTrue(nodes(view).any{text(it,"平板 Dock 位置")})
                tapVisible(view,"平板 Dock 位置")
                assertTrue(nodes(view).any{text(it,"把 Dock 放在順手的位置")})
                posture=DevicePosture(DeviceKind.PHONE);pump(view).recycle()
                assertFalse(nodes(view).any{text(it,"平板 Dock 位置")||text(it,"把 Dock 放在順手的位置")})
            }
            save(view,"dock-preference-hidden-phone")
        }
    }
}
