package io.atmos.weather.earthquake

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Looper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SeismicForegroundLaunchTest {
    private val ctx:Context get()=RuntimeEnvironment.getApplication()
    private val nm get()=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    @Before fun reset(){
        SeismicForegroundWindow.clear();SeismicCriticalTest.cancel(ctx);SeismicAlertDelivery.active.value=null
        listOf("thinn_seismic_v15","thinn_seismic_delivery_v27").forEach{ctx.getSharedPreferences(it,0).edit().clear().commit()}
        SeismicUserLocation.choose(ctx,"臺北市")
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
    }
    @After fun clear(){SeismicForegroundWindow.clear();SeismicCriticalTest.cancel(ctx);SeismicContinuousWarning.stop();SeismicSpeech.stop();SeismicAlertDelivery.active.value=null}
    private fun event(grade:String="3")=EewIncident("direct-foreground",1,"cwa",System.currentTimeMillis(),"花蓮縣近海",6.1,18.0,23.9,121.6,false,false,localIntensityByCounty=mapOf("臺北市" to grade))
    @Test fun acceptedLiveWarningLaunchesThePrivateScreenWithoutTappingANotification(){
        val c=Robolectric.buildActivity(Activity::class.java).setup();val activity=c.get();SeismicForegroundWindow.resumed(activity)
        assertFalse(SeismicAlertNotifier.deliver(ctx,event("2"),SeismicAlertPolicy.Scenario.EEW_FIRST))
        shadowOf(Looper.getMainLooper()).idle();assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(SeismicAlertNotifier.deliver(ctx,event(),SeismicAlertPolicy.Scenario.EEW_FIRST))
        shadowOf(Looper.getMainLooper()).idle()
        val opened=shadowOf(activity).nextStartedActivity
        assertNotNull(opened);assertEquals(SeismicAlertActivity::class.java.name,opened.component!!.className)
        assertNotNull(SeismicAlertDelivery.resolve(ctx,opened.getStringExtra("ticket")))
        assertFalse(SeismicAlertNotifier.deliver(ctx,event(),SeismicAlertPolicy.Scenario.EEW_FIRST))
        shadowOf(Looper.getMainLooper()).idle();assertNull(shadowOf(activity).nextStartedActivity)
        c.pause().stop().destroy()
    }
    @Test fun explicitFiveSecondDrillOpensInTheVisibleAppWithoutPublishingALiveIncident(){
        val c=Robolectric.buildActivity(Activity::class.java).setup();val a=c.get();SeismicForegroundWindow.resumed(a)
        SeismicCriticalMode.setEnabled(ctx,true);assertTrue(SeismicCriticalTest.schedule(ctx))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        val opened=shadowOf(a).nextStartedActivity
        assertNotNull(opened);assertEquals(SeismicCriticalTestActivity::class.java.name,opened.component!!.className)
        assertTrue(SeismicCriticalTest.valid(ctx,opened.getStringExtra("test_ticket")))
        assertNull(SeismicAlertDelivery.active.value);assertNotNull(shadowOf(nm).getNotification(29205))
        c.pause().stop().destroy()
    }
    @Test fun backgroundAndPausedWindowsLeavePresentationToTheSystemNotification(){
        val c=Robolectric.buildActivity(Activity::class.java).setup();val a=c.get();SeismicForegroundWindow.resumed(a)
        SeismicCriticalMode.setEnabled(ctx,true);assertTrue(SeismicCriticalTest.schedule(ctx))
        SeismicForegroundWindow.paused(a);c.pause().stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertNull(shadowOf(a).nextStartedActivity);assertNotNull(shadowOf(nm).getNotification(29205))
        assertTrue(SeismicAlertNotifier.deliver(ctx,event(),SeismicAlertPolicy.Scenario.EEW_FIRST))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(a).nextStartedActivity);assertNotNull(shadowOf(nm).getNotification(29200));c.destroy()
    }
    @Test fun dismissedTicketAndPauseBetweenDeliveryAndLaunchCannotOpenAStaleWarning(){
        val c=Robolectric.buildActivity(Activity::class.java).setup();val a=c.get();SeismicForegroundWindow.resumed(a)
        val ticket=SeismicAlertDelivery.publish(ctx,event())!!
        SeismicForegroundWindow.present(ctx,ticket);SeismicAlertDelivery.dismiss(ctx,ticket.key)
        shadowOf(Looper.getMainLooper()).idle();assertNull(shadowOf(a).nextStartedActivity)
        val next=SeismicAlertDelivery.publish(ctx,event().copy(serial=2))!!
        SeismicForegroundWindow.present(ctx,next);SeismicForegroundWindow.paused(a)
        shadowOf(Looper.getMainLooper()).idle();assertNull(shadowOf(a).nextStartedActivity);c.pause().stop().destroy()
    }
}
