package io.atmos.weather.typhoon

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Equal-kilometer chart projection: never stretch degrees on different display aspect ratios.
 * Only geographic coordinate points already provided by CWA are drawn. This is not a coastline map.
 */
data class TyphoonViewport(
    val originXKm:Double, val originYKm:Double, val pxPerKm:Double,
    val widthPx:Double,val heightPx:Double,val cosCenter:Double
) {
    fun project(longitude:Double,latitude:Double):Pair<Float,Float> {
        val x=longitude*111.2*cosCenter
        val y=latitude*111.2
        return ((widthPx*.5+(x-originXKm)*pxPerKm).toFloat()) to
            ((heightPx*.5-(y-originYKm)*pxPerKm).toFloat())
    }

    companion object {
        fun forPoints(points:List<Pair<Double,Double>>,widthPx:Float,heightPx:Float,paddingPx:Float):TyphoonViewport {
            // Keep Taiwan visible if a storm is nearby; otherwise show the verified points only.
            val src=points.filter { it.first.isFinite() && it.second.isFinite() &&
                it.first in 0.0..55.0 && it.second in 100.0..180.0 }
            val centerLat=(src.map {it.first}.average().takeIf {it.isFinite()} ?: 24.0).coerceIn(0.0,55.0)
            val cosCenter=cos(Math.toRadians(centerLat)).coerceAtLeast(.50)
            val xs=src.map {it.second*111.2*cosCenter}.toMutableList()
            val ys=src.map {it.first*111.2}.toMutableList()
            if(src.isEmpty()) {xs+=121.0*111.2*cosCenter;ys+=24.0*111.2}
            val closeToTaiwan=src.any { it.first in 10.0..35.0 && it.second in 112.0..139.0 }
            if(closeToTaiwan) {xs+=121.0*111.2*cosCenter;ys+=23.5*111.2}
            val minX=xs.minOrNull()!!;val maxX=xs.maxOrNull()!!
            val minY=ys.minOrNull()!!;val maxY=ys.maxOrNull()!!
            val rangeX=max(850.0,(maxX-minX)*1.22)
            val rangeY=max(700.0,(maxY-minY)*1.22)
            val availableW=max(1.0,widthPx-2.0*paddingPx)
            val availableH=max(1.0,heightPx-2.0*paddingPx)
            val scale=min(availableW/rangeX,availableH/rangeY)
            return TyphoonViewport((minX+maxX)/2,(minY+maxY)/2,scale,
                widthPx.toDouble(),heightPx.toDouble(),cosCenter)
        }
    }
}
