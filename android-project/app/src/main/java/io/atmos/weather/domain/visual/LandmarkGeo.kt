package io.atmos.weather.domain.visual

import kotlin.math.*

/** Approximate island proximity is advisory, not an authoritative administrative polygon. */
data class IslandRegion(val name:String,val county:String,val centerLat:Double,
    val centerLon:Double,val radiusKm:Double)

object LandmarkGeo {
    fun distanceKm(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Double {
        val deltaLat=Math.toRadians(lat2-lat1)
        val deltaLon=Math.toRadians(lon2-lon1)
        val h=sin(deltaLat/2)*sin(deltaLat/2)+cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(deltaLon/2)*sin(deltaLon/2)
        return 6371.0*2*atan2(sqrt(h),sqrt((1.0-h).coerceAtLeast(0.0)))
    }
    fun closestIsland(lat:Double,lon:Double,regions:List<IslandRegion>):IslandRegion? =
        regions.filter { distanceKm(lat,lon,it.centerLat,it.centerLon)<=it.radiusKm }
            .minByOrNull { distanceKm(lat,lon,it.centerLat,it.centerLon) }
}
