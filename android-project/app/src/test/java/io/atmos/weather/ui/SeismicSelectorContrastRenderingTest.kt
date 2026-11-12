package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.atmos.weather.earthquake.SeismicLayerSelector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicSelectorContrastRenderingTest:TabletDockFixture(){
 override val reduceTransparency=false
 @Test fun mapLayersKeepTheirOwnReadableMaterialOverAnUnrelatedWeatherBackdrop(){
  windowWidth=412;windowHeight=350
  for(dark in listOf(false,true)){
   var selected by mutableStateOf("綜合")
   render(dark,1f,"seismic-selector-contrast",{
    val inherited=rememberThinnBackdrop()
    CompositionLocalProvider(LocalThinnBackdrop provides inherited){
     Box(Modifier.fillMaxSize()){
      ThinnBackdropCapture(inherited,Modifier.matchParentSize()) {Box(Modifier.fillMaxSize().background(Color(0xFF123C78)))}
      Box(Modifier.padding(24.dp)){SeismicLayerSelector(selected,{selected=it})}
     }
    }
   }){view,p->
    for(label in listOf("綜合","測站","震央")){
     val tabs=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab}
     val tab=tabs.single{text(it,label)}
     assertTrue(tab.config[SemanticsActions.OnClick].action!!.invoke());pump(view).recycle()
     val current=nodes(view).filter{it.config.contains(SemanticsProperties.Role)&&it.config[SemanticsProperties.Role]==Role.Tab}
     assertEquals(1,current.count{it.config[SemanticsProperties.Selected]})
     val bitmap=pump(view)
     try{for(n in current){
      val b=n.boundsInRoot;val sample=Color(bitmap.getPixel((b.left+12).toInt(),b.center.y.toInt()))
      val active=n.config[SemanticsProperties.Selected]
      assertTrue("$dark/$label lost contrast on the map control",colorContrast(if(active)p.primary else p.onSurface,sample)>=4.5f)
      if(!dark&&!active)assertTrue("Map selector inherited the blue weather scene",sample.luminance()>.8f)
     }}finally{bitmap.recycle()}
    }
    save(view,"seismic-layer-controls-${if(dark)"dark" else "light"}")
   }
  }
 }
}
