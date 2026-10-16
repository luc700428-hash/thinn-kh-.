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
        // Use the highest enabled scenario rather than generating five duplicate alerts.
        val best=readings.asSequence().filter { it.stationId.isNotBlank() }
            .flatMap { station ->
                sequenceOf(
                    Triple(Threshold.GAL_STRONG,station.pgaGal,200.0),
                    Triple(Threshold.DETECT_STRONG,station.intensity,4.0),
                    Triple(Threshold.GAL_MEDIUM,station.pgaGal,8.0),
                    Triple(Threshold.DETECT_MEDIUM,station.intensity,2.0),
                    Triple(Threshold.DETECT_WEAK,station.intensity,1.0))
                    .filter { (threshold,value,min) -> threshold in enabled && value!=null && value.isFinite() && value>=min }
                    .map { (threshold,value,_) -> Trigger(threshold,station.stationId,value!!) }
            }.firstOrNull()
        if(best==null) { streak=0;lastThreshold=null;return null }
        if(best.stationId==lastStation && best.threshold==lastThreshold && now-lastSampleMs in 0..12_000L) streak++
        else streak=1
        lastStation=best.stationId;lastThreshold=best.threshold;lastSampleMs=now
        if(streak<3 || (lastSentMs!=Long.MIN_VALUE && now-lastSentMs<120_000L)) return null
        lastSentMs=now;streak=0
        return best
    }
}
