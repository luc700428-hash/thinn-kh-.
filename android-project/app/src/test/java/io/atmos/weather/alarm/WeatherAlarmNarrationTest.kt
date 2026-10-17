package io.atmos.weather.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherAlarmNarrationTest {
    private val zone=ZoneId.of("Asia/Taipei")

    @Test fun greetingsAreSelectedAtTheAlarmTime() {
        assertEquals("早安", WeatherAlarmNarration.greeting(8))
        assertEquals("午安", WeatherAlarmNarration.greeting(15))
        assertEquals("晚上好", WeatherAlarmNarration.greeting(20))
        assertEquals("晚上好", WeatherAlarmNarration.greeting(2))
    }

    @Test fun freshForecastIsDetailedAndNeverInventsUnknownOfficialWarnings() {
        val now=ZonedDateTime.of(2026,10,18,7,0,0,0,zone)
        val at=now.toInstant().toEpochMilli()
        val facts=AlarmWeatherReading("嘉義市",at-600_000,at-600_000,"RAIN",26.0,28.0,85,10.0,30.0,22.0,null,
            listOf(AlarmForecastHour(at+3600_000,27.0,67,11.0,5.5)))
        val spoken=WeatherAlarmNarration.build(now,facts)
        assertTrue(spoken.startsWith("早安"))
        assertTrue(spoken.contains("嘉義市"))
        assertTrue(spoken.contains("67"))
        assertTrue(spoken.contains("穿著方面"))
        assertFalse(spoken.contains("目前沒有警報"))
    }

    @Test fun neverPresentsStaleWeatherAsCurrent() {
        val now=ZonedDateTime.of(2026,10,18,14,0,0,0,zone)
        val at=now.toInstant().toEpochMilli()
        val old=AlarmWeatherReading("高雄市",at-3*3600_000,at-3*3600_000,"CLEAR",35.0,null,null,null,null,null,null,emptyList())
        val spoken=WeatherAlarmNarration.build(now,old)
        assertTrue(spoken.startsWith("午安"))
        assertTrue(spoken.contains("沒有足夠新的天氣資料"))
        assertFalse(spoken.contains("35"))
    }
}
