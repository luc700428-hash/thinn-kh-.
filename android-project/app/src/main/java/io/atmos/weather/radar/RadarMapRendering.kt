package io.atmos.weather.radar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

/** Theme transform applies to the geographical base only, preserving radar colour semantics. */
internal object RadarMapStyle {
    val darkBackground=Color(0xFF070C14)
    val lightBackground=Color(0xFFE6EEF3)
    private val night=ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        -.20f,-.55f,-.05f,0f,204f,
        -.20f,-.55f,-.05f,0f,211f,
        -.20f,-.55f,-.05f,0f,223f,
        0f,0f,0f,1f,0f
    )))
    fun baseFilter(dark:Boolean):ColorFilter?=if(dark)night else null
}

internal fun DrawScope.drawRadarLayers(camera:RadarCamera,density:Float,
    base:Map<RadarTile,ImageBitmap>,echoes:Map<RadarTile,ImageBitmap>,opacity:Float,dark:Boolean) {
    val world=camera.world(density)
    fun paint(images:Map<RadarTile,ImageBitmap>,alpha:Float,filter:ColorFilter?,sampling:FilterQuality) {
        images.forEach {(tile,img)->
            val span=(256*density*2.0.pow(camera.zoom-tile.z)).toFloat()
            val x=(tile.column*span-RadarProjection.x(camera.longitude)*world+size.width/2).toFloat()
            val y=(tile.y*span-RadarProjection.y(camera.latitude)*world+size.height/2).toFloat()
            if(x>=size.width||y>=size.height||x+span<=0||y+span<=0)return@forEach
            drawImage(img,dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),
                dstSize=IntSize(ceil(span).toInt(),ceil(span).toInt()),alpha=alpha,
                colorFilter=filter,filterQuality=sampling)
        }
    }
    clipRect {
        paint(base,1f,RadarMapStyle.baseFilter(dark),FilterQuality.Low)
        // Beyond native coverage, retain the source cells rather than smear them.
        paint(echoes,opacity.coerceIn(0f,1f),null,if(camera.zoom>RadarTilePolicy.RADAR_MAX_ZOOM)FilterQuality.None else FilterQuality.Low)
    }
}
