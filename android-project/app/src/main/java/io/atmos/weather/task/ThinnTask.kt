package io.atmos.weather.task

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Persistent local-only personal item. Contains no remote service identifiers. */
data class ThinnTask(
    val id:String=UUID.randomUUID().toString(),
    val title:String,
    val note:String="",
    val year:Int,
    val month:Int,
    val day:Int,
    val hour:Int,
    val minute:Int,
    val completed:Boolean=false,
    val notify:Boolean=true
) {
    init {
        require(title.isNotBlank()) { "事項名稱不得空白" }
        LocalDate.of(year,month,day)
        require(hour in 0..23 && minute in 0..59) { "時間超出有效範圍" }
    }

    fun dueMillis():Long = LocalDateTime.of(year,month,day,hour,minute)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun displayDate():String = "%04d/%02d/%02d %02d:%02d".format(year,month,day,hour,minute)
}
