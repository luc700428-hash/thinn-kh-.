package io.atmos.weather.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherAlarmTimeTest {
    private val zone=ZoneId.of("Asia/Taipei")
    @Test fun oneShotAfterCurrentTimeRollsIntoNextDay() {
        val now=ZonedDateTime.of(2026,10,17,22,0,0,0,zone)
        assertEquals(ZonedDateTime.of(2026,10,18,7,30,0,0,zone).toInstant(),
            WeatherAlarmTime.next(WeatherAlarm(1,7,30,"早起"),now))
    }
    @Test fun mondayRepeatsAfterSaturday() {
        val now=ZonedDateTime.of(2026,10,17,22,0,0,0,zone)
        assertEquals(ZonedDateTime.of(2026,10,19,8,15,0,0,zone).toInstant(),
            WeatherAlarmTime.next(WeatherAlarm(2,8,15,"上班",setOf(1)),now))
    }
    @Test fun disabledAlarmsAreNeverRescheduled() {
        val now=ZonedDateTime.of(2026,10,17,22,0,0,0,zone)
        assertNull(WeatherAlarmTime.next(WeatherAlarm(3,8,15,"關閉",enabled=false),now))
    }
    @Test fun repeatedAlarmAtExactNowUsesNextDay() {
        val now=ZonedDateTime.of(2026,10,17,22,0,0,0,zone)
        assertEquals(ZonedDateTime.of(2026,10,18,22,0,0,0,zone).toInstant(),
            WeatherAlarmTime.next(WeatherAlarm(4,22,0,"週末",setOf(6,7)),now))
    }
}
