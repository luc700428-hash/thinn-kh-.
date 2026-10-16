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
}
