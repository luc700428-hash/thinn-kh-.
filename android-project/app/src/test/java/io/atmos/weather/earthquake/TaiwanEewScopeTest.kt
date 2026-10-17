package io.atmos.weather.earthquake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TaiwanEewScopeTest {
    private fun event(lat:Double?,lon:Double?,name:String="測試",counties:Map<String,String> = emptyMap())=EewIncident(
        id="test",serial=1,source="TEST_ONLY",issuedAtMs=1L,epicenter=name,
        magnitude=5.0,depthKm=10.0,latitude=lat,longitude=lon,cancelled=false,test=true,
        localIntensityByCounty=counties)
    @Test fun acceptsTaiwanAndIslandCases() {
        listOf(23.97 to 121.60,23.56 to 119.58,24.44 to 118.33,26.16 to 119.95,
            22.04 to 121.54).forEach { (lat,lon) -> assertTrue(TaiwanEewScope.relevant(event(lat,lon))) }
    }
    @Test fun noUnrelatedJapanNotifications() {
        assertFalse(TaiwanEewScope.relevant(event(35.68,139.69,"日本東京")))
        assertFalse(TaiwanEewScope.relevant(event(26.2,127.7,"日本沖繩")))
        assertFalse(TaiwanEewScope.relevant(event(null,null,"日本東北外海")))
        assertFalse(TaiwanEewScope.relevant(event(null,null,"震央未知")))
    }
    @Test fun verifiedTaiwanShakingOverridesForeignEpicenter() {
        assertTrue(TaiwanEewScope.relevant(event(30.0,131.0,"日本",mapOf("宜蘭縣" to "2"))))
        assertFalse(TaiwanEewScope.relevant(event(23.9,121.6,"花蓮",mapOf("宜蘭縣" to "0"))))
    }
    @Test fun simulatedEventNeverBecomesLiveAlert() {
        val demo=SeismicSimulation.scenario(123456L)
        assertTrue(demo.test)
        assertFalse(ExpTechRealtimeParser.isFresh(demo,123456L))
        assertEquals(22,demo.localIntensityByCounty.size)
    }
}
