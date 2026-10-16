package io.atmos.weather.earthquake

import kotlin.math.*

/** Educational P/S wavefront visualization (NOT a prediction/ETA).
 * Earthquake source at depth, P ~= 6.0 km/s and S ~= 3.5 km/s are constant
 * illustrative velocities only; local geology changes speeds dramatically.
 */
data class Wavefront(val pSurfaceRadiusKm: Double, val sSurfaceRadiusKm: Double)
object SeismicWavePhysics {
    fun wavefront(originMs: Long, nowMs: Long, depthKm: Double): Wavefront? {
        if (originMs <= 0L || nowMs < originMs || nowMs - originMs > 240_000L) return null
        if (!depthKm.isFinite() || depthKm !in 0.0..700.0) return null
        val seconds = (nowMs - originMs) / 1000.0
        fun surface(v: Double): Double = sqrt(max(0.0, (v*seconds).pow(2)-depthKm.pow(2)))
        return Wavefront(surface(6.0), surface(3.5))
    }
    fun epicentralKm(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double {
        val a=sin(Math.toRadians(lat2-lat1)/2).pow(2)+cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(Math.toRadians(lon2-lon1)/2).pow(2)
        return 6371.0*2*atan2(sqrt(a),sqrt(max(0.0,1.0-a)))
    }
    fun intensityLabel(raw:String?):String? {
        val trimmed=raw?.replace("級","")?.trim() ?: return null
        return trimmed.takeIf { it in setOf("0","1","2","3","4","5弱","5強","6弱","6強","7") }
    }
}
