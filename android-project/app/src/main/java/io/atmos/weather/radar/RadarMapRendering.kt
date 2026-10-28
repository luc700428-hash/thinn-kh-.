package io.atmos.weather.radar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
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
        images.entries.sortedBy{it.key.z}.forEach {(tile,img)->
            val bounds=RadarTileGeometry.bounds(camera,tile,size.width,size.height,density)
            val x=bounds.left;val y=bounds.top;val span=bounds.width
            if(x>=size.width||y>=size.height||bounds.right<=0||bounds.bottom<=0)return@forEach
            // Keep projection arithmetic in Double, then transform local pixels continuously.
            withTransform({translate(x,y);scale((span/img.width).toFloat(),(span/img.height).toFloat(),pivot=Offset.Zero)}) {
                drawImage(img,alpha=alpha,colorFilter=filter,filterQuality=sampling)
            }
        }
    }
    clipRect {
        paint(base,1f,RadarMapStyle.baseFilter(dark),FilterQuality.Low)
        // Beyond native coverage, retain the source cells rather than smear them.
        paint(echoes,opacity.coerceIn(0f,1f),null,if(camera.zoom>RadarTilePolicy.RADAR_MAX_ZOOM)FilterQuality.None else FilterQuality.Low)
    }
}
