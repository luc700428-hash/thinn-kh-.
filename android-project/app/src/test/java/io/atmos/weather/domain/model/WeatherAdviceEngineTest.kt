package io.atmos.weather.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WeatherAdviceEngineTest {
    @Test fun rainAndAirQualityAdviceUsesRealHourlyValues() {
        val now=Instant.parse("2026-10-14T02:00:00Z")
        val c=WeatherCity("chiayi","台灣 · 嘉義",GeoPoint(23.48,120.44))
        val snapshot=WeatherSnapshot(c,now,now,"test",22.0,21.0,75,9.0,1011.0,
            WeatherCondition.PARTLY_CLOUDY,true,null,null,listOf(
                HourlyWeather(now,22.0,15,4.0),HourlyWeather(now.plusSeconds(3600),23.0,85,4.0)),
            usAqi=121,visibilityM=800.0)
        val result=WeatherAdviceEngine.suggest(snapshot)
        assertTrue(result.any { it.heading.contains("降雨") })
        assertTrue(result.any { it.heading.contains("空氣") && it.detail.contains("121") && it.detail.contains("美國 AQI") })
        assertTrue(result.any { it.heading.contains("能見度") })
    }
    @Test fun pm25WithoutUsAqiStillTriggersAirQualityAdvice() {
        val now = Instant.parse("2026-10-14T02:00:00Z")
        val c = WeatherCity("chiayi", "嘉義", GeoPoint(23.48, 120.44))
        val snapshot = WeatherSnapshot(c, now, now, "test", 22.0, 21.0, 75, 9.0, 1011.0,
            WeatherCondition.CLEAR, true, null, null, emptyList(), pm25 = 38.0)
        val result = WeatherAdviceEngine.suggest(snapshot)
        assertTrue(result.any { it.heading.contains("空氣") && it.detail.contains("PM2.5") })
    }

    @Test fun lowUsAqiWithoutHighPm25DoesNotTriggerAirQualityAdvice() {
        val now = Instant.parse("2026-10-14T02:00:00Z")
        val c = WeatherCity("chiayi", "嘉義", GeoPoint(23.48, 120.44))
        val snapshot = WeatherSnapshot(c, now, now, "test", 22.0, 21.0, 75, 9.0, 1011.0,
            WeatherCondition.CLEAR, true, null, null, emptyList(), usAqi = 75, pm25 = 10.0)
        val result = WeatherAdviceEngine.suggest(snapshot)
        assertTrue(result.none { it.heading.contains("空氣品質需留意") })
    }

}
