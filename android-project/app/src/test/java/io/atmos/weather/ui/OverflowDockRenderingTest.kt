package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.NextReorderableToolDock
import io.atmos.weather.next.NextToolAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OverflowDockRenderingTest:TabletDockFixture(){
 @Test fun overflowUsesTheFourthSlotAndNamesItsSelectedDestination(){
  windowWidth=412;windowHeight=240
  for(dark in listOf(false,true)){
   var selection by mutableStateOf("5")
   render(dark,1f,"overflow-dock",{
    Box(Modifier.padding(20.dp).size(340.dp,78.dp)){
     NextReorderableToolDock((1..5).map{NextToolAction("$it","項目$it",Icons.Rounded.Settings)},
      selection,{selection=it},{},Modifier.fillMaxSize(),reduceMotion=true,reduceTransparency=true,fillAvailableHeight=true)
    }
   }){view,p->
    fun cells()=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab}.sortedBy{it.boundsInRoot.left}
    val choices=cells();assertEquals(4,choices.size)
    assertEquals(1,choices.count{it.config[SemanticsProperties.Selected]})
    assertTrue(choices.last().config[SemanticsProperties.Selected]);assertTrue(text(choices.last(),"項目5"))
    val last=choices.last().boundsInRoot;val bitmap=pump(view)
    val sampled=Color(bitmap.getPixel((last.left+12).toInt(),last.center.y.toInt()));bitmap.recycle()
    fun distance(c:Color)=kotlin.math.abs(sampled.red-c.red)+kotlin.math.abs(sampled.green-c.green)+kotlin.math.abs(sampled.blue-c.blue)
    assertTrue("Selection paint did not follow the overflow destination",distance(p.primaryContainer)<distance(p.surface))
    assertTrue(choices.first().config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
    assertEquals("1",selection);assertTrue(cells().first().config[SemanticsProperties.Selected])
    assertTrue(text(cells().last(),"更多"))
   }
  }
 }
}
