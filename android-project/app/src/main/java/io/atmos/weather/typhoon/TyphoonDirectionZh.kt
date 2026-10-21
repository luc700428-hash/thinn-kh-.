package io.atmos.weather.typhoon

import kotlin.math.*
/** Geographic forecast motion is not wind direction. Only use validated official segments. */
object TyphoonDirectionZh {
    fun from(aLat:Double,aLon:Double,bLat:Double,bLon:Double):String {
        if(listOf(aLat,aLon,bLat,bLon).any {!it.isFinite()}) return "方向待確認"
        val dLon=Math.toRadians(bLon-aLon)
        val x=sin(dLon)*cos(Math.toRadians(bLat))
        val y=cos(Math.toRadians(aLat))*sin(Math.toRadians(bLat))-
              sin(Math.toRadians(aLat))*cos(Math.toRadians(bLat))*cos(dLon)
        if(abs(x)+abs(y)<1e-8) return "移動方向待確認"
        val bearing=(Math.toDegrees(atan2(x,y))+360.0)%360.0
        val sectors=listOf("北","東北","東","東南","南","西南","西","西北")
        return "預報行進方向：${sectors[((bearing+22.5)/45.0).toInt()%8]}方"
    }
}
