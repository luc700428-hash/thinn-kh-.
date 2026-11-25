package io.atmos.weather.earthquake

import android.app.Application
import io.atmos.weather.widgets.WidgetEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SeismicReportFactsTest {
    private fun report(m:Double,at:Instant=Instant.now(),areas:List<Pair<String,String>> = listOf("臺東縣" to "4級","屏東縣" to "2級"))=
        EarthquakeReport("x$m$at",at,"臺東縣政府南南東方 101.9 公里 (位於臺灣東南部海域)",m,16.7,21.88,121.46,"4","",
            "https://www.cwa.gov.tw","顯著有感",areas)

    @Test fun localFactsUseTheReportAndTheTravelTable() {
        val taitung=SeismicPlace("臺東縣",22.76,121.14,"test")
        val local=SeismicReportFacts.local(report(5.5),taitung)!!
        assertEquals("4",local.intensity?.removeSuffix("級"))
        assertEquals(103.0,local.distanceKm!!,3.0)
        assertTrue("S wave about 25-35 s at 100 km: ${local.arrivalSeconds}",local.arrivalSeconds!! in 22.0..40.0)
        assertNull(SeismicReportFacts.local(report(5.5),SeismicPlace("臺北市",25.04,121.56,"test"))!!.intensity)
        assertNull(SeismicReportFacts.local(report(5.5),null))
        assertEquals("極淺層",SeismicReportFacts.depthClass(16.7))
        assertEquals("中層",SeismicReportFacts.depthClass(120.0))
        assertEquals("規模 4 的 178 倍",SeismicReportFacts.energyVersusFour(5.5))
        assertEquals("與規模 4 相當",SeismicReportFacts.energyVersusFour(4.0))
    }

    @Test fun weekGroupsByTaiwanDayAndCountsMyCounty() {
        val now=Instant.parse("2026-11-25T09:00:00Z")
        val list=listOf(report(5.5,now.minusSeconds(3600)),report(3.5,now.minusSeconds(86_400*2),listOf("花蓮縣" to "1級")),report(4.0,now.minusSeconds(86_400*9)))
        val w=SeismicReportFacts.week(list,SeismicPlace("臺東縣",22.76,121.14,"test"),now)
        assertEquals(2,w.count);assertEquals(1,w.mine);assertEquals(7,w.days.size)
        assertEquals(1,w.days[6].size);assertEquals(1,w.days[4].size)
        assertEquals(5.5,w.strongest!!.magnitude,.01)
        assertEquals(ZoneId.of("Asia/Taipei"),ZoneId.of("Asia/Taipei"))
    }

    @Test fun warningWithoutDepthStillEstimatesEveryNearbyCounty() {
        val v=SeismicCountyEstimate.estimate(5.5,null,21.88,121.46)
        assertTrue(v.estimated)
        assertTrue("臺東 is estimated: ${v.values}",v.values.containsKey("臺東縣"))
        assertTrue(v.values.containsKey("屏東縣"))
    }

    @Test fun widgetWarningKeepsIntensityAndArrival() {
        val ctx=RuntimeEnvironment.getApplication()
        io.atmos.weather.ui.initializeWorkspaceWorkers(ctx)
        val now=System.currentTimeMillis()
        WidgetEvent.eew(ctx,"地震速報 · 規模 5.5","臺灣東南部海域",now+120_000,"4",now+20_000)
        val e=WidgetEvent.all(ctx,now).first()
        assertEquals(WidgetEvent.Kind.EEW,e.kind);assertEquals("4",e.intensity);assertEquals(now+20_000,e.arrivalAt)
        WidgetEvent.endEew(ctx)
        assertTrue(WidgetEvent.all(ctx,now).none{it.kind==WidgetEvent.Kind.EEW})
    }
}
