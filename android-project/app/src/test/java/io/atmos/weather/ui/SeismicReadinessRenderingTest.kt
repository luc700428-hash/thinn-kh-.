package io.atmos.weather.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.atmos.weather.earthquake.*
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
class SeismicReadinessRenderingTest:TabletDockFixture(){
    @Test fun permissionStatusesAndTestActionUseDistinctFullWidthRowsAtLargeReadingSize(){
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        for(dark in listOf(false,true))for(scale in listOf(1f,1.5f)){
            SeismicCriticalMode.setEnabled(ctx,true);windowWidth=400;windowHeight=1000
            render(dark,scale,"seismic-readiness",{
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(20.dp)){
                    SeismicDeliveryReadiness(fullScreen=true,criticalEnabled=true)
                }
            }){view,p->
                val rows=listOf("速報連線","系統通知","鎖定時全螢幕","勿擾模式","警報音量").map{tagged(view,"seismic-readiness-$it")}
                rows.zipWithNext().forEach{(a,b)->assertTrue(a.boundsInRoot.bottom<=b.boundsInRoot.top)}
                for(label in listOf("速報連線","系統通知","鎖定時全螢幕","勿擾模式","警報音量","5 秒後測試緊急警報")){
                    val node=nodes(view).single{text(it,label)}
                    assertFalse("Clipped $label at $scale",layout(node).hasVisualOverflow)
                    if(label!="5 秒後測試緊急警報")contrast(node,p.surface)
                }
                tapVisible(view,"5 秒後測試緊急警報")
                assertTrue(SeismicCriticalTest.status(ctx).startsWith("已安排"))
                tapVisible(view,"取消演練通知")
                save(view,"seismic-readiness-rows-$dark-$scale")
            }
        }
    }
}
