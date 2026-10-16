package io.atmos.weather.lifestyle

import io.atmos.weather.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LifestyleAdvisorTest {
    private val now = Instant.parse("2026-10-16T04:00:00Z")
    private fun weather(temp: Double, feels: Double, code: WeatherCondition, rain: Int?, wind: Double = 10.0) = WeatherSnapshot(
        city = SampleCities.all.first(), observedAt=now, fetchedAt=now, sourceId="UNIT_TEST",
        temperatureC=temp, feelsLikeC=feels, humidityPct=60, windKmh=15.0, pressureHpa=null,
        condition=code, isDay=true, sunrise=null, sunset=null,
        hourly=listOf(HourlyWeather(now.plusSeconds(3600),temp,rain,null)), windGustKmh=wind)

    @Test fun motorbikeInRainPrefersLongPantsAndRaincoat() {
        val plan = LifestyleAdvisor.recommend(weather(29.0,31.0,WeatherCondition.RAIN,90),OutingMode.MOTORBIKE,now=now)
        assertTrue(plan.bottoms.contains("長褲"))
        assertTrue(plan.reminder.contains("雨衣"))
    }
    @Test fun stormOverridesOtherOutdoorHints() {
        val plan = LifestyleAdvisor.recommend(weather(29.0,34.0,WeatherCondition.THUNDERSTORM,85),OutingMode.OUTDOORS,now=now)
        assertTrue(plan.reminder.contains("雷雨"))
    }
    @Test fun gustyRainPrefersJacketRatherThanUmbrella() {
        val plan = LifestyleAdvisor.recommend(weather(20.0,18.0,WeatherCondition.RAIN,90,80.0),OutingMode.WALK,now=now)
        assertTrue(plan.umbrella.contains("不建議撐傘"))
    }
    @Test fun unknownRainProbabilityIsNotAssumedZero() {
        val plan = LifestyleAdvisor.recommend(weather(28.0,28.0,WeatherCondition.CLEAR,null),OutingMode.DAILY,now=now)
        assertTrue(plan.umbrella.contains("尚缺"))
    }
    @Test fun dataFetchedMoreThanTwoHoursAgoIsStale() {
        val plan = LifestyleAdvisor.recommend(weather(22.0,22.0,WeatherCondition.CLEAR,10),OutingMode.DAILY,now=now.plusSeconds(7201))
        assertFalse(plan.weatherFresh)
    }
    @Test fun subTwentyDegreePlanUsesLongSleeves() {
        val plan = LifestyleAdvisor.recommend(weather(18.0,16.0,WeatherCondition.CLEAR,10),OutingMode.DAILY,now=now)
        assertTrue(plan.top.contains("長袖"))
    }
    @Test fun coldSensitiveSuggestsMoreLayers() {
        val plan = LifestyleAdvisor.recommend(weather(20.0,20.0,WeatherCondition.CLEAR,10),
            OutingMode.DAILY,now=now,preferences=LifestylePreferences(comfort=TemperatureComfort.FEELS_COLD))
        assertTrue(plan.top.contains("長袖"))
        assertTrue(plan.evidence.contains("怕冷"))
    }
    @Test fun lowRainThresholdRecommendsUmbrellaEarly() {
        val p = LifestyleAdvisor.recommend(weather(24.0,24.0,WeatherCondition.CLOUDY,30),
            OutingMode.DAILY, now=now,preferences=LifestylePreferences(umbrellaThresholdPct=30))
        assertTrue(p.umbrella.contains("折疊傘"))
    }
    @Test fun officeClothingSuggestsCollaredTop() {
        val p = LifestyleAdvisor.recommend(weather(26.0,26.0,WeatherCondition.CLEAR,10),
            OutingMode.DAILY,now=now,preferences=LifestylePreferences(style=ClothingStyle.OFFICE))
        assertTrue(p.top.contains("有領"))
        assertTrue(p.bottoms.contains("長褲"))
    }
}
