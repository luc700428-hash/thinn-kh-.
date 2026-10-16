package io.atmos.weather.domain.visual

import io.atmos.weather.domain.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class TaiwanLandmarksTest {
    @Test fun everyCountyRoutesWithoutGenericCottage() {
        assertEquals(22, TaiwanLandmarks.counties.size)
        for (county in TaiwanLandmarks.counties) {
            assertEquals(county, TaiwanLandmarks.countyFor("台灣 · $county", GeoPoint(23.5,120.3)))
            assertNotNull(TaiwanLandmarks.motifs[county])
        }
    }
    @Test fun foreignCitiesDoNotUseTaiwanCounty() {
        assertNull(TaiwanLandmarks.countyFor("日本 · 東京", GeoPoint(35.68,139.76)))
    }
}
