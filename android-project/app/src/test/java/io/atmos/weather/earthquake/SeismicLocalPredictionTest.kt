package io.atmos.weather.earthquake

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SeismicLocalPredictionTest {
    private val now=1_793_521_249_000L
    private val place=SeismicPlace("嘉義市附近",23.48,120.45,"test GPS",1000)
    private fun actual():EewIncident=ExpTechRealtimeParser.parseEew(
        javaClass.getResourceAsStream("/eew/cwa-20261101-serial4.json")!!.bufferedReader().readText()).single()

    @Test fun realPublicPayloadWithoutCountyFieldsProducesIdentifiedLocalValues() {
        val incident=actual()
        assertTrue(incident.localIntensityByCounty.isEmpty());assertTrue(incident.arrivalAtMsByCounty.isEmpty())
        assertEquals(4.7,incident.magnitude!!,.001);assertEquals(40.0,incident.depthKm!!,.001)
        assertEquals("2",incident.maximumIntensity)
        assertEquals(1_793_521_219_000L,incident.originAtMs)
        val p=SeismicLocalPrediction.resolve(incident,place,now)
        assertNotNull(p.intensity);assertEquals(PredictionOrigin.LOCAL_MODEL,p.intensityOrigin)
        assertEquals(PredictionOrigin.LOCAL_MODEL,p.arrivalOrigin)
        assertTrue(p.arrivalAtMs!!>now);assertTrue(p.arrivalText(now).startsWith("約 "))
        assertTrue(incident.localIntensityByCounty.isEmpty()) // model never contaminates source shading
    }
    @Test fun countdownUsesOriginAndDoesNotRestartOnReportReceiptOrRecomposition() {
        val incident=actual();val a=SeismicLocalPrediction.resolve(incident,place,now)
        val b=SeismicLocalPrediction.resolve(incident.copy(serial=5,issuedAtMs=now+5_000),place,now+5_000)
        assertEquals(a.arrivalAtMs,b.arrivalAtMs)
        assertNotEquals(a.arrivalText(now),b.arrivalText(now+5_000))
        assertEquals("可能已抵達",a.arrivalText(a.arrivalAtMs!!))
    }
    @Test fun explicitCountyFieldsOverrideEachModeledFieldIndependently() {
        val incident=actual().copy(localIntensityByCounty=mapOf("台灣" to "7","嘉義市" to "4"))
        val p=SeismicLocalPrediction.resolve(incident,place,now)
        assertEquals("4",p.intensity);assertEquals(PredictionOrigin.SOURCE,p.intensityOrigin)
        assertEquals(PredictionOrigin.LOCAL_MODEL,p.arrivalOrigin)
        val withTime=incident.copy(arrivalAtMsByCounty=mapOf("嘉義市" to now-1000))
        val supplied=SeismicLocalPrediction.resolve(withTime,place,now)
        assertEquals("已抵達",supplied.arrivalText(now));assertEquals(PredictionOrigin.SOURCE,supplied.arrivalOrigin)
        val timeOnly=SeismicLocalPrediction.resolve(withTime.copy(localIntensityByCounty=emptyMap()),place,now)
        assertEquals(PredictionOrigin.LOCAL_MODEL,timeOnly.intensityOrigin)
        assertEquals(PredictionOrigin.SOURCE,timeOnly.arrivalOrigin)
    }
    @Test fun missingOriginNeverSubstitutesIssueTimeOrPageOpenTime() {
        val p=SeismicLocalPrediction.resolve(actual().copy(originAtMs=null),place,now)
        assertNotNull(p.intensity);assertNull(p.arrivalAtMs)
        assertEquals("來源缺少可靠發震時間",p.arrivalReason)
        val flat=ExpTechRealtimeParser.parseEew("""{"id":"flat","time":$now,"mag":4.7,"depth":40,"lat":24.95,"lon":121.86}""").single()
        assertNull(flat.originAtMs)
    }
    @Test fun missingMagnitudeDoesNotPreventValidArrivalButNeverBecomesZeroIntensity() {
        val p=SeismicLocalPrediction.resolve(actual().copy(magnitude=null),place,now)
        assertNull(p.intensity);assertNotNull(p.arrivalAtMs);assertEquals("來源缺少規模",p.intensityReason)
    }
    @Test fun invalidMissingOrOldLocationNeverFallsBackToAnUnrelatedCity() {
        for(location in listOf(null,place.copy(lat=Double.NaN),place.copy(lon=0.0),place.copy(ageMs=900001))) {
            val p=SeismicLocalPrediction.resolve(actual(),location,now)
            assertNull(p.intensity);assertNull(p.arrivalAtMs)
            assertTrue(p.intensityReason.contains("尚未取得所在地"))
        }
    }
    @Test fun cancellationTestAndStaleFramesDoNotGetLiveModelEstimates() {
        for(event in listOf(actual().copy(cancelled=true),actual().copy(test=true),actual().copy(issuedAtMs=now-120001))) {
            val p=SeismicLocalPrediction.resolve(event,place,now)
            assertNull(p.intensity);assertNull(p.arrivalAtMs)
        }
    }
    @Test fun malformedHypocentersAndClocksRemainUnknown() {
        for(event in listOf(actual().copy(latitude=null),actual().copy(longitude=Double.NaN),actual().copy(depthKm=-1.0))) {
            val p=SeismicLocalPrediction.resolve(event,place,now)
            assertNull(p.intensity);assertNull(p.arrivalAtMs)
        }
        for(origin in listOf(now+100_000,now-400_000))assertNull(SeismicLocalPrediction.resolve(actual().copy(originAtMs=origin),place,now).arrivalAtMs)
    }
    @Test fun pgaWithoutPgvCannotInventPreciseStrongGrades() {
        // CWA's 2019-12-18 release, note 2 steps 8–9: high PGA may still produce grade 4.
        assertEquals("4級以上",SeismicLocalPrediction.coarseIntensity(7.3,8.0,10.0))
        assertEquals("0",SeismicLocalPrediction.coarseIntensity(2.0,40.0,500.0))
        assertNull(SeismicLocalPrediction.coarseIntensity(8.0,10.0,10.0))
        assertNull(SeismicLocalPrediction.coarseIntensity(6.0,200.0,10.0))
        assertNull(SeismicLocalPrediction.coarseIntensity(6.0,10.0,601.0))
    }
    @Test fun offlineTravelTimeInterpolationMatchesIndependentTaupHoldoutCalculations() {
        val fixture=JSONObject(javaClass.getResourceAsStream("/eew/iasp91-holdout.json")!!.bufferedReader().readText())
        val points=fixture.getJSONArray("points")
        assertTrue(points.length()>=100)
        for(i in 0 until points.length()) {
            val point=points.getJSONObject(i)
            val actual=SeismicLocalPrediction.travelSeconds(point.getDouble("depth"),point.getDouble("distance"))!!
            assertEquals("IASP91 holdout $point",point.getDouble("seconds"),actual,.75)
        }
        for((d,r) in listOf(-1.0 to 10.0,701.0 to 10.0,10.0 to 1201.0,Double.NaN to 1.0))
            assertNull(SeismicLocalPrediction.travelSeconds(d,r))
    }
    @Test fun providerTextualPlusMinusGradesAreNormalizedButNeverGuessedFromMagnitude() {
        for((a,b) in listOf("5-" to "5弱","5+" to "5強","6− 級" to "6弱","6+" to "6強"))
            assertEquals(b,SeismicWavePhysics.intensityLabel(a))
        assertNull(SeismicWavePhysics.intensityLabel("8"))
        assertNull(SeismicWavePhysics.intensityLabel("M 6.2"))
    }
    @Test fun nestedMaximumIndexIsDecodedWithoutReplacingAnyLocalValue() {
        val grades=listOf("0","1","2","3","4","5弱","5強","6弱","6強","7")
        grades.forEachIndexed{index,expected->
            val e=ExpTechRealtimeParser.parseEew("""{"id":"wire","time":$now,"author":"cwa","eq":{"time":${now-10000},"mag":6.2,"max":$index}}""").single()
            assertEquals(expected,e.maximumIntensity);assertTrue(e.localIntensityByCounty.isEmpty())
        }
        val invalid=ExpTechRealtimeParser.parseEew("""{"id":"wire","time":$now,"eq":{"mag":6.2,"max":7.5}}""").single()
        assertNull(invalid.maximumIntensity)
    }
    @Test fun speechIdentifiesModeledValuesAndNeverOverridesSourceOrUnknownLocation() {
        val text=SeismicWarningNarration.build("宜蘭縣頭城鎮",4.7,emptyMap(),"嘉義市",40.0,modeledLocal="1")
        assertTrue(text.startsWith("收到地震速報！慎防強烈搖晃！"));assertTrue(text.contains("本機初步估算"))
        val source=SeismicWarningNarration.build("test",4.7,mapOf("嘉義市" to "3"),"嘉義市",40.0,modeledLocal="1")
        assertFalse(source.contains("本機初步估算"));assertTrue(source.contains("預估震度3級"))
        val unknown=SeismicWarningNarration.build("test",4.7,emptyMap(),null,40.0,modeledLocal="1")
        assertFalse(unknown.contains("本機初步估算"));assertTrue(unknown.contains("尚未取得您的所在地"))
    }
}
