package io.atmos.weather.insights

import io.atmos.weather.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherInsightMathTest {
    private val now=Instant.parse("2026-10-28T12:00:00Z")
    private fun hours()=(0..23).map{HourlyWeather(now.plusSeconds(it*3600L),22.0,10,1.0,humidityPct=70,windKmh=10.0,weatherCode=1,feelsLikeC=23.0)}
    private fun weather(h:List<HourlyWeather> = hours())=WeatherSnapshot(SampleCities.all.first(),now,now,"test",22.0,23.0,70,10.0,1010.0,WeatherCondition.CLEAR,true,null,null,h)
    @Test fun dewpointMatchesMagnusReferenceAndRejectsUnsupportedInputs() {
        assertEquals(9.26,WeatherInsightMath.dewPoint(20.0,50)!!,.02)
        for(t in listOf(-30.0,0.0,20.0,40.0))assertEquals(t,WeatherInsightMath.dewPoint(t,100)!!,.0001)
        assertNull(WeatherInsightMath.dewPoint(20.0,0));assertNull(WeatherInsightMath.dewPoint(20.0,null))
        assertNull(WeatherInsightMath.dewPoint(Double.NaN,50));assertNull(WeatherInsightMath.dewPoint(60.0,50))
    }
    @Test fun windScaleUsesMetersPerSecondThresholdsIncludingTaiwanExtendedScale() {
        assertEquals(0,WeatherInsightMath.beaufort(0.0));assertEquals(2,WeatherInsightMath.beaufort(1.6*3.6))
        assertEquals(6,WeatherInsightMath.beaufort(10.8*3.6));assertEquals(12,WeatherInsightMath.beaufort(32.7*3.6))
        assertEquals(17,WeatherInsightMath.beaufort(60.0*3.6));assertNull(WeatherInsightMath.beaufort(-1.0))
        assertNull(WeatherInsightMath.beaufort(Double.NaN))
    }
    @Test fun missingRainWindTemperatureAndLaundryHumidityNeverBecomeGoodWeather() {
        for(h in listOf(hours().map{it.copy(precipitationProbabilityPct=null)},hours().map{it.copy(windKmh=null)},hours().map{it.copy(temperatureC=null,feelsLikeC=null)}))
            assertTrue(WeatherInsightMath.windows(weather(h),OutdoorMode.WALK,now).isEmpty())
        assertTrue(WeatherInsightMath.windows(weather(hours().map{it.copy(humidityPct=null)}),OutdoorMode.LAUNDRY,now).isEmpty())
        assertEquals(22,WeatherInsightMath.windows(weather(hours().map{it.copy(precipitationProbabilityPct=0)}),OutdoorMode.WALK,now).size)
    }
    @Test fun rankingsPenalizeSevereWeatherAndOnlyCompareConsecutiveFutureHours() {
        val h=hours().mapIndexed{i,v->if(i<6)v.copy(weatherCode=95,precipitationProbabilityPct=0) else v.copy(precipitationProbabilityPct=20)}
        val windows=WeatherInsightMath.windows(weather(h),OutdoorMode.WALK,now)
        assertFalse(windows.first().caution);assertTrue(windows.last().caution)
        assertTrue(windows.first().hours.first().at>=now.plusSeconds(6*3600))
        val gaps=hours().filterIndexed{i,_->i%2==0}
        assertTrue(WeatherInsightMath.windows(weather(gaps),OutdoorMode.WALK,now).isEmpty())
        assertTrue(WeatherInsightMath.windows(weather(),OutdoorMode.WALK,now.plusSeconds(25*3600)).isEmpty())
        assertFalse(WeatherInsightMath.windows(weather(hours().map{it.copy(feelsLikeC=null)}),OutdoorMode.WALK,now).first().usesFeelsLike)
    }
}
