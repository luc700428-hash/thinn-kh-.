package io.atmos.weather.earthquake
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import io.atmos.weather.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SeismicReportSoundTest {
    @Test fun reportChannelUsesStableResourceNameAndKeepsExistingSystemBlocks(){
        val ctx=RuntimeEnvironment.getApplication();val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(R.raw.thinn_eq_report_signal,SeismicAlertPolicy.Scenario.REPORT.sound)
        nm.createNotificationChannel(NotificationChannel("thinn_eq_eew_first_v151_audio","user blocked",NotificationManager.IMPORTANCE_NONE))
        SeismicAlertPolicy.ensureChannels(ctx)
        val report=nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.REPORT))
        assertEquals(Uri.parse("android.resource://${ctx.packageName}/raw/thinn_eq_report_signal"),report.sound)
        assertEquals(NotificationManager.IMPORTANCE_NONE,nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.EEW_FIRST)).importance)
        SeismicAlertPolicy.setSoundEnabled(ctx,SeismicAlertPolicy.Scenario.REPORT,false)
        val silent=nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.REPORT))
        assertNull(silent.sound);assertFalse(silent.shouldVibrate())
    }
}
