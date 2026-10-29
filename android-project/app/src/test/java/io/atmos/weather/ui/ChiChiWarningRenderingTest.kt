package io.atmos.weather.ui

import android.app.Application
import android.media.AudioManager
import android.app.NotificationManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
class ChiChiWarningRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun trainingUsesRealFullScreenDialogWithLocalEstimateHistoryAndImmediateDismissalAtBothReadingSizes() {
        val ctx=RuntimeEnvironment.getApplication()
        // Rendering is verified without starting media/TTS on the test host.
        (ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        SeismicUserLocation.choose(ctx,"臺北市")
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)) {
            var visible by mutableStateOf(true)
            val incident=SeismicSimulation.scenario(System.currentTimeMillis(),true)
            render(dark,scale,"921-host",{
                if(visible)SeismicWarningOverlay(incident,true,"臺北市",{visible=false},simulation=true)
            }){view,palette->
                val dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing)
                val sheet=dialog.window!!.decorView;pump(sheet).recycle()
                assertTrue(sheet.width>=400)
                for(label in listOf("臺灣 921 大地震演練","收到地震速報 · 慎防強烈搖晃","M 7.3","8 km","7 級")) {
                    val n=nodes(sheet).filter{text(it,label)}.minBy{it.boundsInRoot.top}
                    assertFalse("Clipped $label at $scale",layout(n).hasVisualOverflow)
                    contrast(n,if(label in listOf("M 7.3","8 km","7 級"))palette.surface else palette.background)
                }
                assertTrue(nodes(sheet).any{text(it,"4 級")})
                assertTrue(nodes(sheet).any{text(it,"本機模型估算 · 僅供演練")})
                save(sheet,"921-fullscreen-$dark-${(scale*100).toInt()}")
                click(sheet,"結束模擬演練");pump(view).recycle();assertFalse(visible)
            }
        }
    }
}
