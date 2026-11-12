package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
class FluidGlassContrastRenderingTest:TabletDockFixture(){
 override val reduceTransparency=false
 @Test fun selectedLensCoversItsCellAndDarkIconsRemainVisibleOverOppositeScenes(){
  windowWidth=412;windowHeight=520
  for(dark in listOf(false,true))render(dark,1f,"fluid-contrast",{
   val source=rememberThinnBackdrop()
   CompositionLocalProvider(LocalThinnBackdrop provides source){
    Box(Modifier.fillMaxSize()){
     ThinnBackdropCapture(source,Modifier.matchParentSize()){Box(Modifier.matchParentSize().background(if(dark)Color.White else Color.Black))}
     Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
      GlassIconButton({},Modifier.testTag("default-foreground-icon")){Icon(Icons.Rounded.Add,"放大")}
      Box(Modifier.width(340.dp).height(78.dp).testTag("full-height-dock")){
       AppGlassNavigation(1,{},Modifier.fillMaxSize(),edgeSized=true,darkBackdrop=dark)
      }
      GlassSlider(.5f,{},Modifier.testTag("restored-slider"))
     }
    }
   }
  }){view,p->
   val cell=nodes(view).single{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab&&it.config[SemanticsProperties.Selected]}.boundsInRoot
   val dock=tagged(view,"full-height-dock").boundsInRoot
   val g=io.atmos.weather.next.DockLiquidGeometry.shape(dock.width,dock.height,1f,4,1f,1f)!!
   assertEquals(dock.height-10f,g.height,.01f)
   assertEquals(cell.center.y,dock.top+g.centerY,1.1f)
   save(view,"fluid-dock-slider-${if(dark)"dark" else "light"}")
   val bitmap=pump(view)
   try{
    val background=Color(bitmap.getPixel((cell.left+12).toInt(),cell.center.y.toInt()))
    assertTrue("Selected text lost contrast: dark=$dark, primary=${p.primary}, background=$background",colorContrast(p.primary,background)>=4.5f)
    val tabs=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab}
    for(tab in tabs) {
     val bounds=tab.boundsInRoot
     val material=Color(bitmap.getPixel((bounds.left+12).toInt(),bounds.center.y.toInt()))
     val selected=tab.config[SemanticsProperties.Selected]
     assertTrue("Navigation text lost contrast over an opposite scene: dark=$dark, selected=$selected",
      colorContrast(if(selected)p.primary else p.onSurface,material)>=4.5f)
    }
    val icon=nodes(view).single{it.config.contains(SemanticsProperties.ContentDescription)&&"放大" in it.config[SemanticsProperties.ContentDescription]}.boundsInRoot
    var painted=0
    for(y in icon.top.toInt() until icon.bottom.toInt())for(x in icon.left.toInt() until icon.right.toInt()){
     val c=Color(bitmap.getPixel(x,y))
     if(kotlin.math.abs(c.red-p.onSurface.red)<.04f&&kotlin.math.abs(c.green-p.onSurface.green)<.04f&&kotlin.math.abs(c.blue-p.onSurface.blue)<.04f)painted++
    }
    assertTrue("Default icon did not inherit a readable foreground",painted>8)
   }finally{bitmap.recycle()}
   save(view,"fluid-dock-slider-${if(dark)"dark" else "light"}")
  }
 }
}
