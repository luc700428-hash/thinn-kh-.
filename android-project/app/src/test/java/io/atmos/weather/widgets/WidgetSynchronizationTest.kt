package io.atmos.weather.widgets

import android.app.Application
import io.atmos.weather.domain.model.*
import io.atmos.weather.ui.initializeWorkspaceWorkers
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class WidgetSynchronizationTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val a=WeatherCity("saved-a","台灣 · 嘉義市",GeoPoint(23.48,120.44))
    private val b=WeatherCity("saved-b","台灣 · 臺北市",GeoPoint(25.03,121.56))
    private val now=Instant.parse("2026-10-31T01:00:00Z")
    private fun reading(city:WeatherCity=a,at:Instant=now)=WeatherSnapshot(city,at,at,"test",24.0,22.0,83,35.0,1012.0,
        WeatherCondition.RAIN,false,null,null,emptyList(),timeZoneId="Asia/Taipei")
    @Before fun reset(){context.getSharedPreferences("weather_cache",0).edit().clear().commit();context.getSharedPreferences("atmos_settings",0).edit().clear().commit()}
    @Test fun savedCitySelectionDoesNotOverwriteTheGpsAlertSubscription() {
        val p=context.getSharedPreferences("atmos_settings",0)
        p.edit().putString("city_id","gps").putLong("lat",25.0.toBits()).putLong("lon",121.0.toBits()).commit()
        WidgetSnapshotStore.select(context,a);assertTrue(WidgetSnapshotStore.publish(context,reading()))
        assertEquals(a,WidgetSnapshotStore.target(context).city);assertEquals("gps",p.getString("city_id",null))
    }
    @Test fun lateCityResponseCannotReplaceTheNewLocation() {
        val old=WidgetSnapshotStore.select(context,a);WidgetSnapshotStore.select(context,b)
        assertFalse(WidgetSnapshotStore.publish(context,reading(),old.revision))
        assertEquals(b,WidgetSnapshotStore.target(context).city)
    }
    @Test fun returningToTheSameCityStillRejectsThePreviousGeneration() {
        val old=WidgetSnapshotStore.select(context,a);WidgetSnapshotStore.select(context,b);val current=WidgetSnapshotStore.select(context,a)
        assertFalse(WidgetSnapshotStore.publish(context,reading(),old.revision))
        assertTrue(WidgetSnapshotStore.publish(context,reading(),current.revision))
    }
    @Test fun olderObservationCannotReplaceAFresherPublishedSnapshot() {
        WidgetSnapshotStore.select(context,a);assertTrue(WidgetSnapshotStore.publish(context,reading()))
        assertFalse(WidgetSnapshotStore.publish(context,reading(at=now.minusSeconds(600))))
        assertEquals(now.toEpochMilli(),context.getSharedPreferences("weather_cache",0).getLong("widget_time",0))
    }
    @Test fun reselectingTheSameCoordinatesPreservesCacheButMovingClearsIt() {
        val before=WidgetSnapshotStore.select(context,a);WidgetSnapshotStore.publish(context,reading())
        assertEquals(before.revision,WidgetSnapshotStore.select(context,a).revision)
        assertTrue(context.getSharedPreferences("weather_cache",0).contains("widget_time"))
        WidgetSnapshotStore.select(context,a.copy(coordinate=GeoPoint(23.0,120.0)))
        assertFalse(context.getSharedPreferences("weather_cache",0).contains("widget_time"))
    }
    @Test fun foregroundSnapshotRepaintsWithoutLaunchingAnExtraNetworkRequest() {
        initializeWorkspaceWorkers(context);WidgetSync.updateVisible(context)
        val manager=WorkManager.getInstance(context)
        assertTrue(manager.getWorkInfosForUniqueWork("thinn-khi-widget-now").get(5,TimeUnit.SECONDS).isEmpty())
        assertTrue(manager.getWorkInfosForUniqueWork("thinn-widget-cached-view").get(5,TimeUnit.SECONDS).isNotEmpty())
    }
}
