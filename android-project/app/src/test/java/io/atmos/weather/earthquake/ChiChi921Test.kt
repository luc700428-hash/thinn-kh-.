package io.atmos.weather.earthquake

import org.junit.Assert.*
import org.junit.Test

class ChiChi921Test {
    @Test fun historicalSourceParametersAndOldIntensityScaleArePreserved() {
        val now=1_793_225_000_000L
        val event=SeismicSimulation.scenario(now,strong=true)
        assertTrue(event.test);assertFalse(event.urgent);assertTrue(ChiChi921.isScenario(event))
        assertEquals(23.85,event.latitude!!,.000001);assertEquals(120.82,event.longitude!!,.000001)
        assertEquals(7.3,event.magnitude!!,0.0);assertEquals(8.0,event.depthKm!!,0.0)
        assertEquals(now,event.issuedAtMs);assertEquals(now-2000,event.originAtMs)
        assertEquals("1999-09-20T17:47:15.900Z",ChiChi921.origin.toString())
        assertEquals("7",ChiChi921.historicalCountyMaxima["南投縣"])
        assertEquals("6",ChiChi921.historicalCountyMaxima["臺中市"])
        assertEquals("5",ChiChi921.historicalCountyMaxima["嘉義市"])
        assertNull(ChiChi921.historicalCountyMaxima["金門縣"])
        assertNull(ChiChi921.historicalCountyMaxima["彰化縣"])
        assertTrue(ChiChi921.historicalCountyMaxima.values.none{it.contains("弱")||it.contains("強")})
    }
    @Test fun estimatesAreTrainingOnlyAndUnknownPlaceIsNotInvented() {
        assertNull(ChiChi921.estimate(null))
        assertNull(ChiChi921.estimate(SeismicPlace("無效",Double.NaN,121.0,"test")))
        val near=ChiChi921.estimate(SeismicPlace("南投縣",23.85,120.82,"test"))!!
        val far=ChiChi921.estimate(SeismicPlace("臺北市",25.04,121.54,"test"))!!
        assertEquals("5弱以上",near.intensity)
        assertTrue(near.epicentralKm<far.epicentralKm)
        assertTrue(near.arrivalSeconds<far.arrivalSeconds)
        assertEquals("4",far.intensity)
        val notTraining=SeismicSimulation.scenario(1_793_225_000_000L,true).copy(test=false)
        assertFalse(ChiChi921.isScenario(notTraining))
    }
    @Test fun warningOpensWithRequestedWordsAndImmediatelyIdentifiesTheDrill() {
        val event=SeismicSimulation.scenario(1_793_225_000_000L,true)
        val message=ChiChi921.trainingNarration(event,SeismicPlace("臺中市",24.15,120.67,"test"))
        assertTrue(message.startsWith("收到地震速報！慎防強烈搖晃！這是臺灣九二一大地震情境演練"))
        for(word in listOf("並非現在發生","趴下、掩護、穩住","七點三","深度八公里","本機演練估算","不是官方預測","舊制6級"))assertTrue(word,message.contains(word))
        assertTrue(ChiChi921.trainingNarration(event,null).contains("尚未設定所在地"))
        // Live messages still require provider intensity, never the historical model.
        val live=SeismicWarningNarration.build("測試震央",7.3,emptyMap(),"臺北市",8.0)
        assertTrue(live.contains("沒有來源提供的預估震度"))
        assertFalse(live.contains("預估震度4級"))
    }
}
