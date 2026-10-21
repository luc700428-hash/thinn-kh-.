package io.atmos.weather.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherWindDirectionTest {
    @Test fun handlesCompassBoundariesAndWrap() {
        assertEquals("風向未提供",WeatherWindDirection.label(null))
        assertEquals("北風",WeatherWindDirection.label(0))
        assertEquals("北風",WeatherWindDirection.label(359))
        assertEquals("東北風",WeatherWindDirection.label(45))
        assertEquals("南風",WeatherWindDirection.label(180))
        assertEquals("西風",WeatherWindDirection.label(270))
        assertEquals("北風",WeatherWindDirection.label(-1))
        assertEquals("北風",WeatherWindDirection.label(360))
        assertEquals("西南風",WeatherWindDirection.label(Int.MIN_VALUE))
    }
}
