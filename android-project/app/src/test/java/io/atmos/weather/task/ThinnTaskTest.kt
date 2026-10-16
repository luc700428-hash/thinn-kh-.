package io.atmos.weather.task

import org.junit.Assert.*
import org.junit.Test

class ThinnTaskTest {
    @Test fun dateFormatAndDueTime() {
        val task=ThinnTask(title="帶雨傘",year=2026,month=10,day=16,hour=8,minute=5)
        assertEquals("2026/10/16 08:05",task.displayDate())
        assertTrue(task.dueMillis()>0)
    }
    @Test fun rolloverAcrossYear() {
        val end=ThinnTask(title="年末",year=2026,month=12,day=31,hour=23,minute=59)
        val start=ThinnTask(title="新年",year=2027,month=1,day=1,hour=0,minute=0)
        assertEquals(60_000L,start.dueMillis()-end.dueMillis())
    }
    @Test fun rejectInvalidDates() {
        assertThrows(java.time.DateTimeException::class.java) {
            ThinnTask(title="錯誤日期",year=2026,month=2,day=30,hour=8,minute=0)
        }
    }
    @Test fun rejectInvalidInput() {
        assertThrows(IllegalArgumentException::class.java) {
            ThinnTask(title="  ",year=2026,month=2,day=1,hour=9,minute=0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThinnTask(title="時間",year=2026,month=10,day=1,hour=25,minute=0)
        }
    }
}
