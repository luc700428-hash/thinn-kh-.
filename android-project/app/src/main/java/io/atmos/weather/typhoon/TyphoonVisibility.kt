package io.atmos.weather.typhoon

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Hide inactive/stale official typhoon tiles. A point without a reliable timestamp is
 * not evidence of a storm happening now. Local verified CWA warnings are handled by the UI.
 */
object TyphoonVisibility {
    private val taiwan=ZoneId.of("Asia/Taipei")
    private fun time(s:String):Instant? {
        val raw=s.trim()
        if(raw.isBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw.replace(' ','T')).atZone(taiwan).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw,DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")).atZone(taiwan).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw,DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")).atZone(taiwan).toInstant() }.getOrNull()
    }
    fun hasActiveStorm(fixes:List<TyphoonFix>,now:Instant=Instant.now()):Boolean {
        // Require an identified storm with a position whose forecast/observation time
        // is close to now; don't interpret archived path points as an active cyclone.
        return fixes.any { fix ->
            val at=time(fix.timestamp) ?: return@any false
            val age=at.epochSecond-now.epochSecond
            fix.stormId.isNotBlank() && age in -48L*3600L..36L*3600L
        }
    }
}
