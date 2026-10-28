package io.atmos.weather.alerts

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class CwaBulletinsTest {
    private fun text()=javaClass.getResourceAsStream("/weather-info/cwa-bulletins.json")!!.bufferedReader().use{it.readText()}
    private val now=Instant.parse("2026-10-28T11:33:30Z")
    @Test fun realOfficialBulletinKeepsCompleteTextAreasAndValidity() {
        val b=CwaBulletins.parse(text(),now).single()
        val source=JSONObject(text()).getJSONObject("cwaopendata").getJSONObject("dataset")
        assertEquals("陸上強風特報",b.title)
        assertEquals(source.getJSONObject("contents").getJSONObject("content").getString("contentText"),b.text)
        assertTrue(b.text.contains("二、注意(警戒)事項"));assertTrue(b.text.endsWith("大眾運輸延誤訊息。"))
        assertEquals(13,b.areas.size);assertTrue("恆春半島" in b.areas && "蘭嶼綠島" in b.areas)
        assertTrue(b.active(now));assertFalse(b.active(Instant.parse("2026-10-28T15:00:00Z")))
        assertFalse(b.copy(startsAt=null).active(now));assertFalse(b.copy(expiresAt=null).active(now))
    }
    @Test fun multipleDatasetsAreNotTruncatedAndCancellationIsNotAnActiveWarning() {
        val j=JSONObject(text());val root=j.getJSONObject("cwaopendata");val one=root.getJSONObject("dataset")
        val second=JSONObject(one.toString());second.getJSONObject("datasetInfo").put("datasetDescription","解除陸上強風特報")
        root.put("dataset",JSONArray().put(one).put(second))
        val items=CwaBulletins.parse(j.toString(),now)
        assertEquals(2,items.size);assertFalse(items.last().active(now))
        root.put("msgType","Cancel");assertTrue(CwaBulletins.parse(j.toString(),now).none{it.active(now)})
    }
    @Test fun testPrivateAndStaleDocumentsAreNotAcceptedAsLiveOfficialNotices() {
        for((key,value) in listOf("status" to "Test","scope" to "Private","sent" to "2026-10-01T10:00:00+08:00","sent" to "2026-11-10T10:00:00+08:00")) {
            val j=JSONObject(text());j.getJSONObject("cwaopendata").put(key,value)
            assertThrows(IllegalArgumentException::class.java){CwaBulletins.parse(j.toString(),now)}
        }
    }
    @Test fun officialViewerAllowsOnlyExactCwaHttpsAuthorities() {
        for(u in listOf(CwaOfficialPages.overview,"https://www.cwa.gov.tw/V8/C/P/Warning/W25.html?T=202610281622","https://edu.cwa.gov.tw/"))assertTrue(u,CwaOfficialPages.allowed(u))
        for(u in listOf("http://www.cwa.gov.tw/","https://www.cwa.gov.tw.evil.example/","https://www.cwa.gov.tw@evil.example/","https://evil.example/cwa.gov.tw","https://www.cwa.gov.tw:444/","javascript:alert(1)","file:///tmp/a"))assertFalse(u,CwaOfficialPages.allowed(u))
        val n=CwaWarning("10/28 發布陸上強風特報","全文", "https://www.cwa.gov.tw/V8/C/P/Warning/W25.html?T=202610281622",now)
        assertEquals(n.link,CwaOfficialPages.forTitle("陸上強風特報",listOf(n)))
    }
}
