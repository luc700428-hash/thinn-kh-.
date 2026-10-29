package io.atmos.weather.ui
import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.*
import io.atmos.weather.earthquake.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="zh-rTW-mdpi",application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicTrainingInterruptionRenderingTest:WeatherInformationRenderingFixture(){
    @Test fun acceptedLiveAlertClosesHistoricalDrillBeforeShowingRealAlert(){
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        SeismicUserLocation.choose(ctx,"臺北市")
        var training by mutableStateOf(true)
        val drill=SeismicSimulation.scenario(System.currentTimeMillis(),true)
        render(false,1f,"live-priority-host",{
            if(training)SeismicWarningOverlay(drill,true,"臺北市",{training=false},simulation=true)
            SeismicForegroundAlertHost()
        }){view,_->
            assertTrue(ShadowDialog.getLatestDialog().isShowing)
            val real=EewIncident("priority-real-event",1,"ExpTech EEW",System.currentTimeMillis(),"花蓮縣近海",6.0,15.0,23.9,121.6,false,false,localIntensityByCounty=mapOf("臺北市" to "3"))
            assertNotNull(SeismicAlertDelivery.publish(ctx,real))
            settle(view){!training}
            val sheet=ShadowDialog.getLatestDialog().window!!.decorView;pump(sheet).recycle()
            assertTrue(nodes(sheet).any{text(it,"緊急地震速報")})
            assertFalse(nodes(sheet).any{text(it,"臺灣 921 大地震演練")})
            click(sheet,"我已採取避難措施");assertNull(SeismicAlertDelivery.active.value)
        }
    }
}
