package io.atmos.weather.earthquake

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class SeismicDeliveryTest {
    private val ctx:Context get()=RuntimeEnvironment.getApplication()
    private fun quake(id:String="event-taiwan-01",serial:Int=1,grade:String="3",at:Long=System.currentTimeMillis())=
        EewIncident(id,serial,"ExpTech EEW",at,"花蓮縣近海",6.1,18.0,23.9,121.6,false,false,
            localIntensityByCounty=mapOf("臺北市" to grade,"花蓮縣" to "6弱"))
    @Before fun reset(){
        listOf("thinn_seismic_v15","thinn_seismic_delivery_v27").forEach{ctx.getSharedPreferences(it,0).edit().clear().commit()}
        SeismicAlertDelivery.active.value=null;SeismicUserLocation.choose(ctx,"臺北市")
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode=AudioManager.RINGER_MODE_SILENT
    }
    @Test fun defaultIsThreeAndEverySupportedBoundaryUsesLocalIntensity(){
        assertEquals("3",SeismicAlertThreshold.chosen(ctx))
        val grades=SeismicAlertThreshold.grades
        for((i,min) in grades.withIndex())for((n,grade) in grades.withIndex()){
            assertEquals(n>=i,SeismicAlertThreshold.accepts(mapOf("台北市" to grade,"花蓮縣" to "7"),"臺北市附近",min,false))
        }
        assertFalse(SeismicAlertThreshold.accepts(mapOf("臺北市" to "0"),"臺北市","1",true))
        assertFalse(SeismicAlertThreshold.accepts(mapOf("花蓮縣" to "7"),"臺北市","1",false))
        assertTrue(SeismicAlertThreshold.accepts(emptyMap(),null,"3",true))
        SeismicAlertThreshold.choose(ctx,"1");assertEquals("1",SeismicAlertThreshold.chosen(ctx))
        assertThrows(IllegalArgumentException::class.java){SeismicAlertThreshold.choose(ctx,"0")}
    }
    @Test fun thresholdCrossingLaterBulletinCanBeFirstDeliveredAlertEvenWhenUpdatesAreDisabled(){
        SeismicAlertPolicy.setAlertEnabled(ctx,SeismicAlertPolicy.Scenario.EEW_UPDATE,false)
        assertFalse(SeismicAlertNotifier.deliver(ctx,quake(grade="2"),SeismicAlertPolicy.Scenario.EEW_FIRST))
        assertNull(SeismicAlertDelivery.active.value)
        assertTrue(SeismicAlertNotifier.deliver(ctx,quake(serial=3),SeismicAlertPolicy.Scenario.EEW_UPDATE))
        assertEquals(SeismicAlertPolicy.Scenario.EEW_FIRST,SeismicAlertDelivery.scenario(ctx,quake(serial=3)))
        assertFalse(SeismicAlertNotifier.deliver(ctx,quake(serial=4),SeismicAlertPolicy.Scenario.EEW_UPDATE))
    }
    @Test fun privateTicketSurvivesRestartButNotDismissalExpiryOrChangedThreshold(){
        val now=System.currentTimeMillis();val e=quake(at=now)
        val t=SeismicAlertDelivery.publish(ctx,e,now)!!
        SeismicAlertDelivery.active.value=null
        assertEquals(e.id,SeismicAlertDelivery.resolve(ctx,t.key,now+500)?.incident?.id)
        assertNull(SeismicAlertDelivery.publish(ctx,e,now+600))
        assertNull(SeismicAlertDelivery.resolve(ctx,t.key,now+120_001))
        SeismicAlertThreshold.choose(ctx,"4");assertNull(SeismicAlertDelivery.resolve(ctx,t.key,now))
        SeismicAlertThreshold.choose(ctx,"3")
        SeismicAlertDelivery.dismiss(ctx,t.key);assertNull(SeismicAlertDelivery.resolve(ctx,t.key,now))
        assertNull(SeismicAlertDelivery.publish(ctx,e,now))
        assertNotNull(SeismicAlertDelivery.publish(ctx,e.copy(serial=2),now))
    }
    @Test fun simulationsCancelledStaleFutureInvalidMagnitudeAndShortPushExpiryNeverWake(){
        val now=System.currentTimeMillis();val e=quake(at=now)
        for(bad in listOf(e.copy(test=true),e.copy(cancelled=true),e.copy(issuedAtMs=now-120_001),
            e.copy(issuedAtMs=now+15_001),e.copy(magnitude=Double.NaN),e.copy(depthKm=-1.0),e.copy(expiresAtMs=now-1)))
            assertNull(SeismicAlertDelivery.publish(ctx,bad,now))
        val t=SeismicAlertDelivery.publish(ctx,e.copy(expiresAtMs=now+1500),now)!!
        assertNull(SeismicAlertDelivery.resolve(ctx,t.key,now+1501))
        assertNull(SeismicAlertDelivery.resolve(ctx,"../../not-an-event",now))
    }
    @Test fun acceptedNotificationUsesProtectedActivityAndOptInFullscreenRatherThanMainActivity(){
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putBoolean("allow_seismic_full_screen",true).commit()
        assertTrue(SeismicAlertNotifier.deliver(ctx,quake(),SeismicAlertPolicy.Scenario.EEW_FIRST))
        val note=shadowOf(nm).getNotification(29200);assertNotNull(note);assertNotNull(note.fullScreenIntent)
        val intent=shadowOf(note.fullScreenIntent).savedIntent
        assertEquals(SeismicAlertActivity::class.java.name,intent.component!!.className)
        assertNotNull(SeismicAlertDelivery.resolve(ctx,intent.getStringExtra("ticket")))
        val launched=Robolectric.buildActivity(SeismicAlertActivity::class.java,intent).create()
        assertFalse(launched.get().isFinishing)
        assertTrue(shadowOf(launched.get()).showWhenLocked)
        assertTrue(shadowOf(launched.get()).turnScreenOn)
        ctx.getSharedPreferences("thinn_seismic_v15",0).edit().putBoolean("allow_seismic_full_screen",false).commit()
        launched.newIntent(intent)
        assertFalse(shadowOf(launched.get()).showWhenLocked)
        assertFalse(shadowOf(launched.get()).turnScreenOn)
        launched.destroy()
        val info=ctx.packageManager.getActivityInfo(ComponentName(ctx,SeismicAlertActivity::class.java),0)
        assertFalse(info.exported)
        val spoof=Robolectric.buildActivity(SeismicAlertActivity::class.java,Intent(ctx,SeismicAlertActivity::class.java).putExtra("real_seismic_alert",true)).create()
        assertTrue(spoof.get().isFinishing);spoof.destroy()
        SeismicAlertDelivery.cancel(ctx,quake().copy(cancelled=true))
        assertNull(SeismicAlertDelivery.resolve(ctx,intent.getStringExtra("ticket")))
    }
    @Test fun notificationWithoutFullscreenGrantRetainsUsableContentIntent(){
        assertTrue(SeismicAlertNotifier.deliver(ctx,quake(),SeismicAlertPolicy.Scenario.EEW_FIRST))
        val note=shadowOf(ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotification(29200)
        assertNull(note.fullScreenIntent);assertNotNull(note.contentIntent)
    }
    @Test fun missingMagnitudeSurvivesTicketSerializationWithoutInventingAValue(){
        val e=quake().copy(magnitude=null,depthKm=null)
        val t=SeismicAlertDelivery.publish(ctx,e)!!
        val decoded=SeismicAlertDelivery.resolve(ctx,t.key)!!.incident
        assertNull(decoded.magnitude);assertNull(decoded.depthKm)
        assertEquals("M —",SeismicDisplayFormat.magnitude(decoded.magnitude))
        assertEquals("集集鎮西偏南 10 km",SeismicDisplayFormat.location("集集鎮西偏南 10 公里"))
        assertEquals("12.5 km",SeismicDisplayFormat.depth(12.5))
    }
    @Test fun sourceIdentityAndCancellationCannotHideANewerOrDifferentAgencyAlert(){
        val e=quake(serial=4);val a=SeismicAlertDelivery.publish(ctx,e)!!
        val b=SeismicAlertDelivery.publish(ctx,e.copy(source="cwa"))!!
        assertNotEquals(a.key,b.key)
        assertEquals(SeismicAlertDelivery.identity(e.copy(source="cwa")),SeismicAlertDelivery.identity(e.copy(source="CWA_OFFICIAL")))
        assertFalse(SeismicAlertDelivery.cancel(ctx,e.copy(cancelled=true,serial=3)))
        assertEquals(b.key,SeismicAlertDelivery.active.value?.key)
        assertTrue(SeismicAlertDelivery.cancel(ctx,e.copy(cancelled=true)))
        assertEquals(b.key,SeismicAlertDelivery.active.value?.key)
        assertFalse(SeismicAlertDelivery.cancel(ctx,e.copy(source="cwa",cancelled=true,test=true)))
        assertTrue(SeismicAlertDelivery.cancel(ctx,e.copy(source="cwa",cancelled=true)))
        assertNull(SeismicAlertDelivery.active.value)
    }
    @Test fun boundedTicketsHandleInterleavingAndRejectOlderSerialAfterDismissal(){
        val now=System.currentTimeMillis()
        val one=SeismicAlertDelivery.publish(ctx,quake(),now)!!
        val two=SeismicAlertDelivery.publish(ctx,quake(id="event-taiwan-02"),now)!!
        SeismicAlertDelivery.dismiss(ctx,one.key)
        assertEquals(two.key,SeismicAlertDelivery.active.value?.key)
        repeat(12){SeismicAlertDelivery.publish(ctx,quake(id="bounded-event-$it"),now)}
        assertEquals(8,ctx.getSharedPreferences("thinn_seismic_delivery_v27",0).all.size)
    }
}
