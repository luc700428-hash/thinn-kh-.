package io.atmos.weather.ui

import kotlin.math.abs

internal object RefreshRatePolicy {
    const val TARGET_HZ=144f
    data class Mode(val id:Int,val width:Int,val height:Int,val hz:Float)
    fun choose(modes:List<Mode>,width:Int,height:Int):Mode? {
        val same=modes.filter {it.width==width && it.height==height && it.hz.isFinite() && it.hz>0}
        return same.filter {abs(it.hz-TARGET_HZ)<=1f}.minByOrNull {abs(it.hz-TARGET_HZ)}
            ?: same.filter {it.hz>=TARGET_HZ}.minByOrNull {it.hz}
            ?: same.maxByOrNull {it.hz}
    }
}
