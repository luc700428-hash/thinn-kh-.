package io.atmos.weather.location

import io.atmos.weather.domain.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class LocationLabelsTest {
    @Test fun neverShowProvinceForTaiwan() {
        assertEquals("台灣", LocationLabels.clean("台灣省"))
        assertEquals("台灣", LocationLabels.clean("臺灣省"))
        assertEquals("台灣", LocationLabels.clean("Taiwan, Province of China"))
    }
    @Test fun taiwanGPSPlaceHasCountyAndDistrict() {
        assertEquals("台灣 · 嘉義縣 · 民雄鄉", LocationLabels.name("TW", "Taiwan", "嘉義縣", "民雄鄉"))
    }
    @Test fun overseasCityPreservesCountry() {
        assertEquals("日本 · 東京都 · 東京", LocationLabels.name("JP", "日本", "東京都", "東京"))
    }
    @Test fun dioramaShowsCountyAndDistrict() {
        assertEquals("嘉義縣 · 民雄鄉", LocationLabels.microcity("台灣 · 嘉義縣 · 民雄鄉"))
        assertEquals("東港鎮", LocationLabels.microcity("台灣 · 東港鎮"))
    }
    @Test fun taiwanOfflineFallback() {
        assertTrue(LocationLabels.fallback(GeoPoint(23.48,120.45)).startsWith("台灣 · "))
    }
    @Test fun externalCountryAliasesAlwaysUseTheRequestedTaiwanPresentation() {
        for(alias in listOf("中國台灣","中国台湾","中國 · 臺灣","中華台北","中華臺北","中华台北","Chinese Taipei","CHINESE  TAIPEI",
            "Taiwan, Province of China","Taiwan (China)","Taiwan, Republic of China","台灣（中國）","China, Taiwan","Taiwan","臺灣省","台湾省")) {
            assertEquals(alias,"台灣",LocationLabels.clean(alias))
            assertTrue(alias,LocationLabels.isTaiwan(null,alias))
            assertEquals(alias,"台灣 · 台北市 · 大安區",LocationLabels.name(null,alias,"台北市","大安區"))
            val full="$alias · 台北市 · 大安區"
            assertEquals("台灣 · 台北市 · 大安區",LocationLabels.display(full))
            assertEquals("台北市 · 大安區",LocationLabels.microcity(full))
            assertEquals(LocationLabels.clean(full),LocationLabels.clean(LocationLabels.clean(full)))
        }
    }
    @Test fun countryOverridesDoNotChangeForeignCoordinatesOrOrdinaryPlaceNames() {
        assertEquals("中國 · 上海市 · 浦東新區",LocationLabels.name("CN","中國","上海市","浦東新區"))
        assertEquals("日本 · 東京都 · 台場",LocationLabels.name("JP","日本","東京都","台場"))
        assertEquals("台灣 · 臺北市 · 大安區",LocationLabels.clean("臺灣 · 臺北市 · 大安區"))
        val p=GeoPoint(25.033,121.565)
        val c=io.atmos.weather.domain.model.WeatherCity("legacy","中華台北 · 台北市",p)
        assertEquals(c.id,LocationLabels.city(c).id);assertEquals(p,LocationLabels.city(c).coordinate)
        assertEquals("台灣 · 台北市",LocationLabels.city(c).name)
    }
}
