package io.atmos.weather.radar

import androidx.compose.ui.geometry.Rect
import kotlin.math.pow

internal object RadarTileGeometry {
    fun bounds(camera:RadarCamera,tile:RadarTile,width:Float,height:Float,density:Float):Rect {
        val world=camera.world(density)
        val span=256.0*density*2.0.pow(camera.zoom-tile.z)
        val x=tile.column.toDouble()*span-RadarProjection.x(camera.longitude)*world+width/2.0
        val y=tile.y.toDouble()*span-RadarProjection.y(camera.latitude)*world+height/2.0
        return Rect(x.toFloat(),y.toFloat(),(x+span).toFloat(),(y+span).toFloat())
    }
}
