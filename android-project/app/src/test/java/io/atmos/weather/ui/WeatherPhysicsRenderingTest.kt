package io.atmos.weather.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.atmos.weather.next.V25HomeHero
import io.atmos.weather.next.WeatherScene
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherPhysicsRenderingTest:TabletRenderingFixture() {
 @Test fun glyphsCardsSnowLeavesAndLightningRenderFromTheirActualColliders() {
  windowWidth=768;windowHeight=1024
  var selected by mutableStateOf(WeatherScene.DRIZZLE)
  var layout:RainLayout?=null
  render(false,1f,"physics-surface-proof",{
   val style=WeatherPhysicsStyle.forScene(selected)
   RainCollisionScene(style.rain,0f,Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),style=style,lightBackdrop=true) {
    val observed=LocalRainLayout.current
                SideEffect{layout=observed}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
     AppText("原生物理渲染測試 · ${selected.label}",color=MaterialTheme.colorScheme.onSurface)
     V25HomeHero(28,"rain",selected.label,30,24,27,false)
     Box(Modifier.fillMaxWidth().height(100.dp).weatherCollisionSurface(22.dp).background(Color(0xFFE9F4EE))){AppText("颱風資訊卡片碰撞範圍")}
     Spacer(Modifier.height(18.dp))
     Box(Modifier.fillMaxWidth().height(100.dp).weatherCollisionSurface(22.dp).background(Color(0xFF92343E))){AppText("警報卡片渲染測試",color=Color.White)}
     Spacer(Modifier.height(500.dp))
    }
   }
  }){view,_->
   val host=layout!!;val world=host.world;val physics=host.particles
   assertTrue(world.surfaces.values.count{it.contour!=null}>=2)
   assertTrue(world.surfaces.values.filter{it.contour!=null}.all{it.contour!!.mask.count{c->c}>30})
   assertTrue(world.surfaces.size>=7)
   for(scene in listOf(WeatherScene.DRIZZLE,WeatherScene.LIGHT_RAIN,WeatherScene.MODERATE_RAIN,WeatherScene.HEAVY_RAIN,WeatherScene.HEAVY_SNOW,WeatherScene.THUNDERSTORM)) {
    selected=scene;pump(view).recycle();val style=WeatherPhysicsStyle.forScene(scene)
    // Each exported weather profile starts clean; accumulation then comes from real contacts.
    world.clearAir();world.surfaces.values.forEach{it.beads.clear();it.wet=0f};physics.clearAir();physics.caps.clear()
    repeat(if(style.snow>0)900 else 180){host.refreshAll();world.step(1f/60,style.rain,45f);physics.step(1f/60,style,14f,false,world.width,world.height,world.surfaces.values)}
    if(style.snow>0){assertTrue(physics.snowDeposits>0);assertTrue(physics.caps.values.any{a->a.any{it>.05f}})}
    if(style.thunder)assertNotNull(physics.strike(world.surfaces.values,world.width,world.height))
    host.frame.longValue++;save(view,"physics-native-${scene.name.lowercase()}")
   }
   val s=world.surfaces.values.first{it.contour==null&&it.top>100}
   physics.caps[s.id]=FloatArray(16){4f}
   val old=s.top
   val scroller=nodes(view).first{it.config.contains(SemanticsActions.ScrollBy)}
   assertTrue(scroller.config[SemanticsActions.ScrollBy].action!!.invoke(0f,120f));pump(view).recycle();host.refreshAll()
   physics.step(1f/60,WeatherPhysicsStyle(),0f,false,world.width,world.height,world.surfaces.values)
   assertTrue(s.top<old);assertTrue(physics.snowEjections>0)
   host.frame.longValue++;save(view,"physics-native-scroll-shedding")
  }
 }
}
