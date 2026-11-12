package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PhoneDockProportionRenderingTest:TabletDockFixture(){
    override val reduceTransparency=false
    @Test fun actualPhoneModeKeepsLensAndAllFourLabelsInsideOneCenteredCapsule(){
        windowWidth=400;windowHeight=840
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f,2f)){
            var selected by mutableIntStateOf(0)
            render(dark,scale,"phone-dock",{
                Box(Modifier.fillMaxSize()){
                    AppGlassNavigation(selected,{selected=it},Modifier.align(Alignment.BottomCenter).padding(bottom=24.dp).testTag("actual-phone-dock"))
                }
            }){view,_->
                for(label in listOf("天氣","地震","工具","設定")){
                    tapVisible(view,label)
                    val dock=tagged(view,"actual-phone-dock").boundsInRoot
                    val lens=tagged(view,"dock-selection-lens").boundsInRoot
                    val tabs=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab}
                    assertEquals(4,tabs.size)
                    val chosen=tabs.single{it.config[SemanticsProperties.Selected]}.boundsInRoot
                    // The modifier includes the explicit bottom clearance; compare the actual inner cell.
                    assertEquals(chosen.center.y,lens.center.y,1f)
                    assertEquals(chosen.height,lens.height,1f)
                    assertTrue(lens.width in (chosen.width-5f)..chosen.width)
                    assertTrue(lens.top>=dock.top&&lens.bottom<=dock.bottom)
                    assertTrue(lens.left>=dock.left&&lens.right<=dock.right)
                    tabs.forEach{tab->
                        assertFalse("Clipped label at font scale $scale: ${layout(tab).layoutInput.text}",layout(tab).hasVisualOverflow)
                        assertEquals(chosen.center.y,tab.boundsInRoot.center.y,1f)
                    }
                }
                save(view,"phone-dock-proportion-$dark-$scale")
            }
        }
    }
}
