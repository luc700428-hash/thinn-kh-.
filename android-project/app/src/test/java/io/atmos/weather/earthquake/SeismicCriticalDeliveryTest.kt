package io.atmos.weather.earthquake

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicCriticalDeliveryTest {
    private val ctx:Context get()=RuntimeEnvironment.getApplication()
    private val nm get()=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    @Before fun reset() {
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().clear().commit()
        ctx.getSharedPreferences("thinn_seismic_delivery_v27",0).edit().clear().commit()
        SeismicAlertDelivery.active.value=null;SeismicCriticalTest.cancel(ctx)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
    @After fun stop() {SeismicCriticalTest.cancel(ctx);SeismicContinuousWarning.stop();SeismicSpeech.stop();SeismicAlertDelivery.active.value=null}
    @Test fun modeIsExplicitAndOnlyChangesEewAudioChannels() {
        assertFalse(SeismicCriticalMode.enabled(ctx));SeismicAlertPolicy.ensureChannels(ctx)
        val reportId=SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.REPORT)
        SeismicCriticalMode.setEnabled(ctx,true)
        assertTrue(ctx.getSharedPreferences("thinn_seismic_v15",0).getBoolean("allow_seismic_full_screen",false))
        val eew=nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.EEW_FIRST))
        assertEquals(AudioAttributes.USAGE_ALARM,eew.audioAttributes.usage)
        assertEquals(reportId,SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.REPORT))
        assertEquals(AudioAttributes.USAGE_NOTIFICATION_EVENT,nm.getNotificationChannel(reportId).audioAttributes.usage)
        SeismicCriticalMode.setEnabled(ctx,false)
        assertFalse(SeismicAlertPolicy.channelId(ctx,SeismicAlertPolicy.Scenario.EEW_FIRST).contains("critical"))
    }
    @Test fun enablingModePreservesAnExistingNotificationBlock() {
        val scenario=SeismicAlertPolicy.Scenario.EEW_FIRST
        nm.createNotificationChannel(NotificationChannel(SeismicAlertPolicy.channelId(ctx,scenario),"blocked",NotificationManager.IMPORTANCE_NONE))
        SeismicCriticalMode.setEnabled(ctx,true)
        assertEquals(NotificationManager.IMPORTANCE_NONE,nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,scenario)).importance)
        assertNull(SeismicCriticalMode.route(ctx,scenario,forVoice=true))
    }
    @Test fun enablingModePreservesAnExistingSilentChannel() {
        val scenario=SeismicAlertPolicy.Scenario.EEW_URGENT
        nm.createNotificationChannel(NotificationChannel(SeismicAlertPolicy.channelId(ctx,scenario),"muted",NotificationManager.IMPORTANCE_HIGH).apply{setSound(null,null)})
        SeismicCriticalMode.setEnabled(ctx,true)
        assertNull(nm.getNotificationChannel(SeismicAlertPolicy.channelId(ctx,scenario)).sound)
        assertNull(SeismicCriticalMode.route(ctx,scenario));assertNull(SeismicCriticalMode.route(ctx,scenario,forVoice=true))
    }
    @Test fun aPrivateExpiringDrillNeverBecomesALiveIncident() {
        SeismicCriticalMode.setEnabled(ctx,true);assertTrue(SeismicCriticalTest.schedule(ctx))
        assertNull(SeismicAlertDelivery.active.value)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        val note=shadowOf(nm).getNotification(29205);assertNotNull(note)
        assertTrue(note.extras.getCharSequence("android.title").toString().contains("並非真實地震"))
        val intent=shadowOf(note.contentIntent).savedIntent
        assertEquals(SeismicCriticalTestActivity::class.java.name,intent.component!!.className)
        val token=intent.getStringExtra("test_ticket");assertTrue(SeismicCriticalTest.valid(ctx,token))
        assertFalse(SeismicCriticalTest.valid(ctx,token,System.currentTimeMillis()+65_001))
        assertNull(SeismicAlertDelivery.active.value)
        assertFalse(ctx.packageManager.getActivityInfo(ComponentName(ctx,SeismicCriticalTestActivity::class.java),0).exported)
        SeismicCriticalTest.cancel(ctx);assertFalse(SeismicCriticalTest.valid(ctx,token));assertNull(shadowOf(nm).getNotification(29205))
    }
    @Test fun forgedTestExtrasCannotWakeTheDevice() {
        val controller=Robolectric.buildActivity(SeismicCriticalTestActivity::class.java,
            Intent(ctx,SeismicCriticalTestActivity::class.java).putExtra("test_ticket","00000000-0000-0000-0000-000000000000")).create()
        assertTrue(controller.get().isFinishing)
        assertFalse(shadowOf(controller.get()).showWhenLocked);assertFalse(shadowOf(controller.get()).turnScreenOn);controller.destroy()
    }
    @Test fun cancellingBeforeTheDelayPreventsNotificationDelivery() {
        SeismicCriticalMode.setEnabled(ctx,true);assertTrue(SeismicCriticalTest.schedule(ctx));SeismicCriticalTest.cancel(ctx)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));assertNull(shadowOf(nm).getNotification(29205))
    }
    @Test fun aRealAcceptedWarningPreemptsThePendingDrill() {
        SeismicCriticalMode.setEnabled(ctx,true);SeismicUserLocation.choose(ctx,"臺北市");assertTrue(SeismicCriticalTest.schedule(ctx))
        val now=System.currentTimeMillis()
        val event=EewIncident("critical-preemption",1,"cwa",now,"花蓮縣近海",6.1,18.0,23.9,121.6,false,false,localIntensityByCounty=mapOf("臺北市" to "3"))
        assertNotNull(SeismicAlertDelivery.publish(ctx,event,now))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertNull(shadowOf(nm).getNotification(29205));assertEquals(event.id,SeismicAlertDelivery.active.value?.incident?.id)
    }
}
