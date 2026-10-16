package io.atmos.weather.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** A warm lowland city must never show false '96% snow and road icing'. */
class WeatherAdviceTemperatureGuardTest {
    private val now = Instant.parse("2026-10-17T01:00:00Z")
    private fun weather(temperature:Double, hourTemp:Double = temperature):WeatherSnapshot = WeatherSnapshot(
        city=WeatherCity("minxiong","臺灣 · 嘉義縣 · 民雄",GeoPoint(23.556,120.43)),
        observedAt=now,fetchedAt=now,sourceId="test-model",temperatureC=temperature,
        feelsLikeC=temperature+1.0,humidityPct=89,windKmh=4.0,pressureHpa=1012.0,
        condition=WeatherCondition.CLOUDY,isDay=false,sunrise=null,sunset=null,
        hourly=listOf(HourlyWeather(now.plusSeconds(3600),hourTemp,0,null,96,1.2))
    )
    @Test fun minxiongHotModelSnowIsNotShown() {
        val suggestions=WeatherAdviceEngine.suggest(weather(30.0))
        assertFalse(suggestions.any {it.heading.contains("降雪") || it.heading.contains("結冰")})
    }
    @Test fun warmLowlandWithColdNearbyModelCellIsNotShown() {
        val suggestions=WeatherAdviceEngine.suggest(weather(28.0,1.0))
        assertFalse(suggestions.any {it.heading.contains("降雪") || it.heading.contains("結冰")})
    }
    @Test fun genuinelyFreezingConditionsCanSuggestSnow() {
        val suggestions=WeatherAdviceEngine.suggest(weather(1.0,-1.0))
        assertTrue(suggestions.any {it.heading.contains("降雪")})
    }
}
