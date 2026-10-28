package io.atmos.weather.lifestyle

import io.atmos.weather.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Boundary tests directly derived from the user's execution summary. */
class ExecutiveSummaryRulesTest {
    private val now = Instant.parse("2026-10-16T02:00:00Z")
    private fun weather(t:Double,rain:Int=0,wind:Double=0.0) = WeatherSnapshot(
        SampleCities.all.first(),now,now,"UNIT_TEST",t,t,65,wind,null,
        WeatherCondition.CLEAR,true,null,null,
        listOf(HourlyWeather(now.plusSeconds(900),t,rain,null)),windGustKmh=wind)

    @Test fun underTwentyUsesOuterLayer() {
        assertTrue(LifestyleAdvisor.recommend(weather(19.9),OutingMode.DAILY,now=now).layer.contains("外套"))
    }
    @Test fun twentyOneToTwentyEightUsesMidLongSleeves() {
        assertTrue(LifestyleAdvisor.recommend(weather(21.0),OutingMode.DAILY,now=now).top.contains("中長袖"))
        assertTrue(LifestyleAdvisor.recommend(weather(28.0),OutingMode.DAILY,now=now).top.contains("中長袖"))
    }
    @Test fun aboveTwentyEightUsesShortSleeves() {
        assertTrue(LifestyleAdvisor.recommend(weather(28.1),OutingMode.DAILY,now=now).top.contains("短袖"))
    }
    @Test fun aboveThirtyPercentCarriesRainProtection() {
        assertTrue(LifestyleAdvisor.recommend(weather(25.0,31),OutingMode.DAILY,now=now).umbrella.contains("折疊傘"))
    }
    @Test fun highWindUsesRaincoatInsteadOfUmbrella() {
        assertTrue(LifestyleAdvisor.recommend(weather(25.0,70,55.0),OutingMode.DAILY,now=now).umbrella.contains("不建議撐傘"))
    }
    @Test fun sportsModeUsesBreathableClothes() {
        assertTrue(LifestyleAdvisor.recommend(weather(27.0),OutingMode.WALK,now=now).top.contains("吸濕排汗"))
    }
}
