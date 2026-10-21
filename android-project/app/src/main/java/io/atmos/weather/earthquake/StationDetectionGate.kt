package io.atmos.weather.earthquake

/** Opt-in, local THIRD-PARTY station reading reminder, NOT an official EEW or PWS.
 * Three fresh consecutive frames from the SAME station within 12 s, with a global
 * two-minute cooldown. No threshold guessing and no inferred magnitude or arrival.
 */
class StationDetectionGate {
    data class Reading(val stationId:String,val intensity:Double?,val pgaGal:Double?)
    enum class Threshold(val label:String) {
        DETECT_WEAK("測站觀測震度 1 以上"),DETECT_MEDIUM("測站觀測震度 2 以上"),
        DETECT_STRONG("測站觀測震度 4 以上"),GAL_MEDIUM("測站觀測加速度 8 gal 以上"),
        GAL_STRONG("測站觀測加速度 200 gal 以上")
    }
    data class Trigger(val threshold:Threshold,val stationId:String,val value:Double)
    private var lastStation=""
    private var lastThreshold:Threshold?=null
    private var streak=0
    private var lastSampleMs=0L
    private var lastSentMs=Long.MIN_VALUE
    fun observe(readings:List<Reading>,now:Long,enabled:Set<Threshold>):Trigger? {
        if(enabled.isEmpty()) { streak=0;lastThreshold=null; return null }
        // Rank across ALL stations, not by upstream JSON iteration order. This is
        // an opt-in THIRD-PARTY station reminder, never an official earthquake warning.
        val priority=listOf(
            Triple(Threshold.GAL_STRONG,200.0,true),
            Triple(Threshold.DETECT_STRONG,4.0,false),
            Triple(Threshold.GAL_MEDIUM,8.0,true),
            Triple(Threshold.DETECT_MEDIUM,2.0,false),
            Triple(Threshold.DETECT_WEAK,1.0,false))
        val best=priority.firstNotNullOfOrNull { (threshold,minimum,usesGal) ->
            if(threshold !in enabled) null
            else readings.asSequence().filter { it.stationId.isNotBlank() }
                .mapNotNull { station ->
                    val value=if(usesGal) station.pgaGal else station.intensity
                    if(value!=null && value.isFinite() && value>=minimum)
                        Trigger(threshold,station.stationId,value)
                    else null
                }.sortedWith(compareByDescending<Trigger> { it.value }.thenBy { it.stationId })
                .firstOrNull()
        }
        if(best==null) { streak=0;lastThreshold=null;return null }
        // The same timestamp or a clock rollback cannot count as another frame.
        if(best.stationId==lastStation && best.threshold==lastThreshold && now-lastSampleMs in 1..12_000L) streak++
        else streak=1
        lastStation=best.stationId;lastThreshold=best.threshold;lastSampleMs=now
        if(streak<3 || (lastSentMs!=Long.MIN_VALUE && now-lastSentMs<120_000L)) return null
        lastSentMs=now;streak=0
        return best
    }
}
