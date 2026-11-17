package io.atmos.weather.location

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaceSearchTest {
    @Test fun landmarksGetARegionAndNeverAChinaTaiwanLabel() {
        val osm = PlaceSearch.parse(org.json.JSONArray("""[
            {"lat":"22.0","lon":"120.75","osm_type":"node","osm_id":1,"name":"墾丁大街",
             "address":{"country":"中華民國","country_code":"tw","county":"屏東縣","town":"恆春鎮"}},
            {"lat":"25.03","lon":"121.56","osm_type":"way","osm_id":2,"name":"台北101",
             "address":{"country":"Taiwan, Province of China","country_code":"tw","city":"臺北市"}},
            {"lat":"999","lon":"0","osm_type":"node","osm_id":3,"name":"壞資料"}]"""))
        assertEquals(2, osm.size)
        assertEquals("台灣 · 屏東縣 · 墾丁大街", osm[0].name)
        for (c in osm) for (bad in listOf("中國", "中华", "中華台北", "中華民國", "Province", "China")) assertFalse(c.name, c.name.contains(bad))
        assertTrue(osm[1].name.startsWith("台灣"))
    }
}
