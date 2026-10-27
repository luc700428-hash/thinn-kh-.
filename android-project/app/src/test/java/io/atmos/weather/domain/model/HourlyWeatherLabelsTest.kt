package io.atmos.weather.domain.model

import io.atmos.weather.presentation.home.hourlyWeatherName
import org.junit.Assert.assertEquals
import org.junit.Test

class HourlyWeatherLabelsTest {
    @Test fun distinguishRainIntensityAndNight() {
        assertEquals("中度毛毛雨", hourlyWeatherName(53, 14))
        assertEquals("中雨", hourlyWeatherName(63, 14))
        assertEquals("大雨", hourlyWeatherName(65, 14))
        assertEquals("晴朗夜晚", hourlyWeatherName(0, 22))
        assertEquals("起霧", hourlyWeatherName(45, 6))
    }
}
