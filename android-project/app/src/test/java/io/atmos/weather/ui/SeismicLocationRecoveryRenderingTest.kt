package io.atmos.weather.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
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
class SeismicLocationRecoveryRenderingTest:WeatherInformationRenderingFixture() {
    @Test fun locationSelectedAfterFirstReportUpdatesExistingWarningWithoutRestartingIt() {
        val ctx=RuntimeEnvironment.getApplication()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        SeismicAlertDelivery.active.value=null;SeismicUserLocation.choose(ctx,null)
        val now=System.currentTimeMillis()
        val incident=EewIncident("late-place",4,"cwa",now,"宜蘭縣頭城鎮",4.7,40.0,24.95,121.86,false,false,originAtMs=now-30_000)
        render(true,1.5f,"late-location",{SeismicWarningOverlay(incident,false,null,{})}){_,_->
            val dialog=ShadowDialog.getLatestDialog();val sheet=dialog.window!!.decorView;pump(sheet).recycle()
            assertTrue(nodes(sheet).any{text(it,"選擇所在地")})
            assertTrue(nodes(sheet).any{text(it,"請選擇所在地")})
            // Same persisted preference as the inline location menu and earthquake settings.
            SeismicUserLocation.choose(ctx,"嘉義市")
            settle(sheet){nodes(sheet).any{text(it,"本機走時估算")}}
            assertSame(dialog,ShadowDialog.getLatestDialog())
            assertTrue(nodes(sheet).any{text(it,"嘉義市")})
            assertFalse(nodes(sheet).any{text(it,"請選擇所在地")})
            save(sheet,"late-location-recovered")
        }
    }
}
