package io.atmos.weather.earthquake
import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SeismicPredictionParserTest {
    @Test fun numericUpstreamCancellationAndWarningCodesAreParsedWithoutConfusingFinalReport(){
        for(status in listOf(2,3)){
            val e=ExpTechRealtimeParser.parseEew("""{"id":"cancelled-event","time":1793250000000,"serial":4,"author":"cwa","status":$status,"final":1,"eq":{"time":1793249990000,"lat":23.9,"lon":121.6,"mag":6.1,"depth":18,"loc":"花蓮縣近海"}}""").single()
            assertTrue(e.cancelled);assertFalse(ExpTechRealtimeParser.isFresh(e,1793250000000))
        }
        assertTrue(ExpTechRealtimeParser.parseEew("""{"id":"minimal-cancel","serial":6,"status":3,"time":1793250000000,"author":"cwa"}""").single().cancelled)
        val e=ExpTechRealtimeParser.parseEew("""{"id":"warning-event","time":1793250000000,"author":"cwa","status":1,"final":1,"eq":{"mag":6.1,"loc":"花蓮縣近海"}}""").single()
        assertTrue(e.urgent);assertFalse(e.cancelled)
    }
    @Test fun onlyExplicitCountyFieldsSupplyLocalIntensityAndArrival(){
        val e=ExpTechRealtimeParser.parseEew("""{"id":"source-test","timestamp":1793250000000,"magnitude":6.0,"maximumIntensity":"7","arrivalTime":1793250006000,"countyIntensities":{"台北市":"3","unknown":"7","臺南市":"abc"},"countyArrivalTimes":{"台北市":1793250006000,"unknown":1793250007000}}""").single()
        assertEquals(mapOf("臺北市" to "3"),e.localIntensityByCounty)
        assertEquals(mapOf("臺北市" to 1793250006000L),e.arrivalAtMsByCounty)
        assertEquals("7",e.maximumIntensity)
        val generic=ExpTechRealtimeParser.parseEew("""{"id":"source-test","timestamp":1793250000000,"magnitude":6.0,"maxIntensity":"7","arrivalTime":1793250006000}""").single()
        assertTrue(generic.localIntensityByCounty.isEmpty());assertTrue(generic.arrivalAtMsByCounty.isEmpty())
    }
}
