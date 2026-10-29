package io.atmos.weather.earthquake
import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SeismicPermission35Test {
    @Test fun android35WithoutSystemGrantRetainsHighPriorityNotificationWithoutRequestingFullscreen(){
        val ctx=RuntimeEnvironment.getApplication();val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putBoolean("allow_seismic_full_screen",true).commit()
        val granted=runCatching{nm.canUseFullScreenIntent()}.getOrDefault(false)
        assertEquals(granted,SeismicAlertDelivery.mayUseFullScreen(ctx))
        val e=EewIncident("android35-permission",1,"ExpTech EEW",System.currentTimeMillis(),"花蓮縣近海",5.9,12.0,23.9,121.6,false,false)
        assertTrue(SeismicAlertNotifier.deliver(ctx,e,SeismicAlertPolicy.Scenario.EEW_FIRST))
        val notification=shadowOf(nm).getNotification(29200)
        assertNotNull(notification.contentIntent)
        assertEquals(granted,notification.fullScreenIntent!=null)
        assertEquals(NotificationManager.IMPORTANCE_HIGH,nm.getNotificationChannel(notification.channelId).importance)
    }
}
