package io.atmos.weather.next.domain

import io.atmos.weather.next.alarm.AlarmTimePolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class AlarmTimePolicyTest {
    @Test fun amAndPmAlwaysStayInFixedFields() {
        assertEquals(0,AlarmTimePolicy.hour24(12,false))
        assertEquals(12,AlarmTimePolicy.hour24(12,true))
        assertEquals(13,AlarmTimePolicy.hour24(1,true))
        assertEquals(12,AlarmTimePolicy.hour12(0))
        assertEquals(11,AlarmTimePolicy.hour12(23))
    }
    @Test fun tomorrowWhenTimeAlreadyPassed() {
        val now=ZonedDateTime.parse("2026-10-22T22:58:00+08:00[Asia/Taipei]")
        val next=AlarmTimePolicy.nextOccurrence(7,0,0,now)
        assertEquals(23,next.dayOfMonth)
        assertEquals(7,next.hour)
    }
    @Test fun weeklyRepeatRespectsDayMask() {
        val now=ZonedDateTime.parse("2026-10-22T22:58:00+08:00[Asia/Taipei]")
        val next=AlarmTimePolicy.nextOccurrence(8,30,1,now)
        assertEquals(1,next.dayOfWeek.value)
        assertEquals(8,next.hour)
    }
    @Test(expected=IllegalArgumentException::class)
    fun rejectsInvalidHours() { AlarmTimePolicy.hour24(13,true) }
}
