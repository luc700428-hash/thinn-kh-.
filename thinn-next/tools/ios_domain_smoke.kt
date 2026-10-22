import io.atmos.weather.next.alarm.AlarmTimePolicy
import java.time.ZonedDateTime
fun main() {
    check(AlarmTimePolicy.hour24(12,false)==0)
    check(AlarmTimePolicy.hour24(12,true)==12)
    check(AlarmTimePolicy.hour24(1,true)==13)
    check(AlarmTimePolicy.hour12(0)==12)
    check(AlarmTimePolicy.hour12(23)==11)
    val now=ZonedDateTime.parse("2026-10-22T22:58:00+08:00[Asia/Taipei]")
    val next=AlarmTimePolicy.nextOccurrence(7,0,0,now)
    check(next.toString().startsWith("2026-10-23T07:00"))
    val mondayOnly=AlarmTimePolicy.nextOccurrence(8,30,1,now)
    check(mondayOnly.dayOfWeek.value==1)
    check(mondayOnly.hour==8 && mondayOnly.minute==30)
    val exact=ZonedDateTime.parse("2026-10-22T07:00:00+08:00[Asia/Taipei]")
    check(AlarmTimePolicy.nextOccurrence(7,0,0,exact).dayOfMonth==23)
    println("PASS: AM/PM fixed column and repeat scheduling policy")
}
