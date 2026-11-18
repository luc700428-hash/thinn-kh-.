package io.atmos.weather.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeatherDescriptionsPlainTest {
    @Test fun stationJargonBecomesEverydayWords() {
        assertEquals("晴，有薄霧", WeatherDescriptions.plain("晴有靄"))
        assertEquals("多雲，有薄霧", WeatherDescriptions.plain("多雲有靄"))
        assertEquals("陰，有薄霧", WeatherDescriptions.plain(" 陰有靄 "))
        assertEquals("有薄霧", WeatherDescriptions.plain("有靄"))
        assertEquals("陰，短暫雨", WeatherDescriptions.plain("陰短暫雨"))
        assertEquals("多雲", WeatherDescriptions.plain("多雲"))
        assertEquals("晴時多雲", WeatherDescriptions.plain("晴時多雲"))
        for (t in listOf("晴有靄", "陰有靄", "有靄")) assertFalse(WeatherDescriptions.plain(t).contains("靄"))
    }
}
