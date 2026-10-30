package io.atmos.weather.alerts

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class CwaWarningDetailsRegressionTest {
    private val now=Instant.parse("2026-10-29T21:01:00Z") // Screenshot: Taiwan 10/30 05:01.
    private fun source(name:String)=javaClass.getResourceAsStream("/cwa-warning-20261030/$name")!!.bufferedReader().use{it.readText()}
    private fun bulletin()=CwaBulletins.parse(source("bulletins.json"),now).single()
    private fun national(at:Instant=now)=CwaNationalWarnings(CwaNationalWarningFeed.parse(source("counties.json"),at),at,
        sourceAt=CwaBulletins.time(JSONObject(source("counties.json")).getJSONObject("cwaopendata").getString("sent"))!!)
    private val affected=setOf("苗栗縣","臺中市","屏東縣","臺東縣")

    @Test fun actualMixedRssKeepsWeatherAndDoesNotTurnEarthquakeReportsIntoWeatherAreas() {
        val notices=CwaWarningFeed.parse(source("warning-rss.xml").byteInputStream())
        assertEquals(1,notices.size)
        assertEquals("10/30 04:29 發布陸上強風特報",notices.single().title)
        assertTrue(notices.single().summary.contains("30日下午至晚上"))
        assertFalse(notices.single().summary.contains("\\n"))
        assertTrue(notices.single().summary.endsWith("請注意。"))
        assertFalse(notices.any{it.title.contains("地震")})
        assertTrue(CwaWarningTitles.earthquakeReport("中央氣象署地震報告",
            "https://www.cwa.gov.tw/V8/C/E/EQ/EQ115074-1030-043743.html"))
        assertFalse(CwaWarningTitles.weatherNotice("10/30 04:40 發布第 074 號顯著有感地震報告",
            "https://www.cwa.gov.tw/V8/C/E/EQ/EQ115074-1030-043743.html"))
    }

    @Test fun aPublishedAfternoonWarningIsAvailableAtDawnWithoutClaimingItHasAlreadyStarted() {
        val b=bulletin()
        assertTrue(b.available(now));assertTrue(b.upcoming(now));assertFalse(b.active(now))
        assertEquals(Instant.parse("2026-10-30T09:00:00Z"),b.startsAt)
        val data=national();assertEquals(affected,data.hazards.map{it.county}.toSet())
        val state=CwaWarningMapState.build(data,listOf(b),emptyList(),"10/30 04:29 發布陸上強風特報",now)
        assertTrue(state.complete);assertFalse(state.expiredSelection)
        assertEquals(affected,state.titles.keys);assertEquals(affected,state.upcoming.keys)
        assertTrue(state.upcoming.values.all{it==b.startsAt})
        val local=CwaCountyHazardFeed.parse(JSONObject(source("counties.json")),"台灣 · 臺東縣 · 蘭嶼鄉",null,now).single()
        assertEquals(b.startsAt,local.startsAt);assertEquals("臺東縣",CwaWarningGeography.county(local.county))
    }

    @Test fun theSameOfficialAreasTransitionFromScheduledToActiveAndThenExpireAtTheBoundary() {
        val b=bulletin();val start=b.startsAt!!;val end=b.expiresAt!!
        val active=CwaWarningMapState.build(national(start),listOf(b),emptyList(),b.title,start)
        assertTrue(b.active(start));assertFalse(b.upcoming(start))
        assertEquals(affected,active.titles.keys);assertTrue(active.upcoming.isEmpty())
        val expired=CwaWarningMapState.build(national(end),listOf(b),emptyList(),b.title,end)
        assertFalse(b.active(end));assertFalse(b.available(end))
        assertTrue(expired.expiredSelection);assertTrue(expired.titles.isEmpty());assertTrue(expired.upcoming.isEmpty())
    }

    @Test fun failedNationalLoadingStillShowsTheVerifiedScheduledBulletinAreas() {
        val b=bulletin()
        val state=CwaWarningMapState.build(CwaNationalWarnings(emptyList(),now,failed=true),listOf(b),emptyList(),b.title,now)
        assertFalse(state.complete);assertFalse(state.expiredSelection)
        assertEquals(affected,state.titles.keys);assertEquals(affected,state.upcoming.keys)
        assertTrue(state.unresolved.isEmpty())
    }

    @Test fun cancellationAndUnverifiedValidityNeverProduceScheduledHighlights() {
        val b=bulletin()
        for(unavailable in listOf(b.copy(cancelled=true),b.copy(startsAt=null),b.copy(expiresAt=null))) {
            assertFalse(unavailable.available(now));assertFalse(unavailable.upcoming(now));assertFalse(unavailable.active(now))
            val state=CwaWarningMapState.build(null,listOf(unavailable),emptyList(),b.title,now)
            assertTrue(state.titles.isEmpty());assertTrue(state.upcoming.isEmpty())
        }
    }

    @Test fun activeWarningsTakePrecedenceOverScheduledWarningsInTheSameCounty() {
        val b=bulletin();val n=national()
        val active=n.hazards.first().copy(title="大雨特報",phenomenon="大雨",startsAt=now.minusSeconds(60))
        val state=CwaWarningMapState.build(n.copy(hazards=n.hazards+active),listOf(b),emptyList(),null,now)
        assertTrue(active.county in state.titles);assertFalse(active.county in state.upcoming)
        assertEquals(affected-active.county,state.upcoming.keys)
    }

    @Test fun titleMatchingUsesTheWeatherProductRatherThanTimestampOrUnrelatedReportText() {
        assertTrue(CwaWarningTitles.matches("10/30 04:29 發布陸上強風特報","陸上強風特報"))
        assertTrue(CwaWarningTitles.matches("10/30 發布解除陸上強風特報","陸上強風特報"))
        assertFalse(CwaWarningTitles.matches("陸上強風特報","大雨特報"))
        assertFalse(CwaWarningTitles.matches("地震報告","陸上強風特報"))
        assertFalse(CwaWarningTitles.matches("","陸上強風特報"))
        assertEquals("陸上強風特報",CwaWarningTitles.display("10/30 04:29 發布陸上強風特報"))
        val notice=CwaWarningFeed.parse(source("warning-rss.xml").byteInputStream()).single()
        assertEquals(notice.link,CwaOfficialPages.forTitle("陸上強風特報",listOf(notice)))
    }
}
