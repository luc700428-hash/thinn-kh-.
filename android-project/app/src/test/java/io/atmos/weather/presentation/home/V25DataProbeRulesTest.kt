package io.atmos.weather.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Test

class V25DataProbeRulesTest {
    private val now=1_760_000_000_000L
    @Test fun newNetworkDataCanBeVerified() {
        assertEquals(V25DataProbeRules.WeatherResult.NETWORK_FRESH,
            V25DataProbeRules.classifyWeather(false,false,now-60_000L,now))
    }
    @Test fun staleAndCacheNeverCountAsLive() {
        for (fromCache in listOf(true,false)) for (stale in listOf(true,false)) {
            if (fromCache || stale) assertEquals(V25DataProbeRules.WeatherResult.CACHED,
                V25DataProbeRules.classifyWeather(fromCache,stale,now,now))
        }
    }
    @Test fun invalidTimestampsNeverCountAsLive() {
        for (at in listOf(0L,now-3_600_000L,now+600_000L)) {
            assertEquals(V25DataProbeRules.WeatherResult.INVALID_TIME,
                V25DataProbeRules.classifyWeather(false,false,at,now))
        }
    }
}
