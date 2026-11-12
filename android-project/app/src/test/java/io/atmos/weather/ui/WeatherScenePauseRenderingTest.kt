package io.atmos.weather.ui
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rTW-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherScenePauseRenderingTest:WeatherInformationRenderingFixture(){
    @Test(timeout=60_000) fun coveredSceneStopsFramesAndResumesItsOriginalParticleWorld(){
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        var active by mutableStateOf(true);var scene:RainLayout?=null
        render(true,1f,"weather-scene-pause",{
            androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("thinn_settings",0).edit().putBoolean("reduce_motion",false).commit()
            RainCollisionScene(.8f,8f,Modifier.fillMaxSize(),active=active){
                val world=LocalRainLayout.current;SideEffect{scene=world}
                Box(Modifier.fillMaxWidth().height(150.dp).weatherCollisionSurface())
            }
        }){view,_->
            settle(view){scene!=null&&scene!!.frame.longValue>2}
            val original=scene;val before=scene!!.frame.longValue;pump(view).recycle();assertTrue(scene!!.frame.longValue>before)
            active=false;pump(view).recycle();val paused=scene!!.frame.longValue
            repeat(5){pump(view).recycle()};assertEquals("Covered weather kept running",paused,scene!!.frame.longValue)
            assertTrue(scene!!.world.air.isEmpty());active=true;pump(view).recycle()
            assertSame(original,scene);assertTrue(scene!!.frame.longValue>paused)
        }
    }
}
