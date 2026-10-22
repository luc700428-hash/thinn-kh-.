package io.atmos.weather.next.alarm

import java.time.LocalTime
import java.time.ZonedDateTime

/** Pure 12-hour conversion and local-time repeat policy, shared by UI and scheduler. */
object AlarmTimePolicy {
    fun hour24(hour12:Int,afternoon:Boolean):Int {
        require(hour12 in 1..12)
        return (hour12%12)+(if(afternoon)12 else 0)
    }
    fun hour12(hour24:Int):Int {require(hour24 in 0..23);return if(hour24%12==0)12 else hour24%12}
    fun nextOccurrence(hour24:Int,minute:Int,daysMask:Int,after:ZonedDateTime):ZonedDateTime {
        require(hour24 in 0..23 && minute in 0..59 && daysMask in 0..127)
        for(days in 0..7) {
            val date=after.toLocalDate().plusDays(days.toLong())
            if(daysMask!=0 && (daysMask and (1 shl (date.dayOfWeek.value-1)))==0)continue
            val whenLocal=date.atTime(LocalTime.of(hour24,minute))
            // Java resolves skipped daylight-saving times using zone transition rules.
            val at=whenLocal.atZone(after.zone)
            if(at.isAfter(after))return at
        }
        error("找不到可用的下一個鬧鐘時間")
    }
}
