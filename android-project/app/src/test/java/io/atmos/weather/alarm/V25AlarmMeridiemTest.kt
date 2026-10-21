package io.atmos.weather.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class V25AlarmMeridiemTest {
    @Test fun noonMidnightAndAllBoundaries() {
        assertEquals(0,V25AlarmMeridiem.hour24(0,V25AlarmMeridiem.AM))
        assertEquals(12,V25AlarmMeridiem.hour24(0,V25AlarmMeridiem.PM))
        assertEquals(11,V25AlarmMeridiem.hour24(11,V25AlarmMeridiem.AM))
        assertEquals(23,V25AlarmMeridiem.hour24(11,V25AlarmMeridiem.PM))
        assertEquals(13,V25AlarmMeridiem.hour24(1,V25AlarmMeridiem.PM))
    }
    @Test fun draggingToTheSameMeridiemDoesNotToggle() {
        assertEquals(V25AlarmMeridiem.AM,V25AlarmMeridiem.fromDrag(V25AlarmMeridiem.AM,80f,12f))
        assertEquals(V25AlarmMeridiem.PM,V25AlarmMeridiem.fromDrag(V25AlarmMeridiem.PM,-80f,12f))
        assertEquals(V25AlarmMeridiem.PM,V25AlarmMeridiem.fromDrag(V25AlarmMeridiem.AM,-80f,12f))
        assertEquals(V25AlarmMeridiem.AM,V25AlarmMeridiem.fromDrag(V25AlarmMeridiem.PM,80f,12f))
        assertEquals(V25AlarmMeridiem.PM,V25AlarmMeridiem.fromDrag(V25AlarmMeridiem.PM,4f,12f))
    }
}
