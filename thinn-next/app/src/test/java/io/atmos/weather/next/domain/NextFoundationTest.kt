package io.atmos.weather.next.domain

import org.junit.Assert.*
import org.junit.Test

class NextFoundationTest {
    @Test fun fourteenScenesAreSeparateAndNamed() {
        assertEquals(14, WeatherScene.entries.size)
        assertEquals(14, WeatherScene.entries.map { it.label }.distinct().size)
    }
    @Test fun unknownForecastIsNotFakeSunnyWeather() {
        assertNull(WmoWeatherCodes.scene(999))
        assertEquals(WeatherScene.CLEAR,WmoWeatherCodes.scene(0))
        assertEquals(WeatherScene.THUNDERSTORM,WmoWeatherCodes.scene(95))
    }
    @Test fun timestampsMustNotFalselyLookLive() {
        val now=1_800_000_000_000L
        assertEquals(DataFreshness.FRESH,DataFreshnessPolicy.classify(now-90_000,now))
        assertEquals(DataFreshness.OLD,DataFreshnessPolicy.classify(now-8_000_000,now))
        assertEquals(DataFreshness.FUTURE,DataFreshnessPolicy.classify(now+2_000_000,now))
        assertEquals(DataFreshness.UNKNOWN,DataFreshnessPolicy.classify(0,now))
    }
    @Test fun cachedWeatherIsAlwaysMarkedNotRealtime() {
        val now=1_800_000_000_000L
        val v=WeatherReading("taipei",25.0,23.0,80,12.0,0.0,1,now-10_000,now)
        assertTrue(DataFreshnessPolicy.display(v,now,true).contains("非即時"))
    }
    @Test fun eventsNeverMakeUnverifiedEmergencyClaims() {
        val now=1_800_000_000_000L
        val e=NextEvent("real",NextEvent.Type.VERIFIED_EARTHQUAKE,"地震預警","",now-1_000,now+45_000,false)
        assertFalse(EventPolicy.isDeliverable(e,now))
        assertTrue(EventPolicy.isDeliverable(e.copy(verified=true),now))
        assertFalse(EventPolicy.isDeliverable(e.copy(verified=true,expiresAtMs=now-1),now))
    }
    @Test fun systemSurfacesObeyUserAndOemPermission() {
        val now=1_800_000_000_000L
        val timer=NextEvent("timer",NextEvent.Type.TIMER,"計時中","",now-1_000,now+60_000,true)
        val c=DeviceSurfaceCapabilities(true,true,true,true,true)
        assertEquals(Surface.XIAOMI_FOCUS,SystemSurfacePolicy.route(timer,now,c))
        assertEquals(Surface.IN_APP_ONLY,SystemSurfacePolicy.route(timer,now,c.copy(canNotify=false)))
        assertEquals(Surface.ANDROID_LIVE_UPDATE,SystemSurfacePolicy.route(timer,now,c.copy(isXiaomi=false)))
        assertEquals(Surface.ANDROID_NOTIFICATION,SystemSurfacePolicy.route(timer,now,c.copy(isXiaomi=false,userInitiatedOngoing=false)))
    }
    @Test fun priorityIsExplicitAndGlobal() {
        assertTrue(EventPolicy.priority(NextEvent.Type.VERIFIED_EARTHQUAKE)>EventPolicy.priority(NextEvent.Type.ALARM))
        assertTrue(EventPolicy.priority(NextEvent.Type.ALARM)>EventPolicy.priority(NextEvent.Type.ATMOSPHERE))
    }
}
