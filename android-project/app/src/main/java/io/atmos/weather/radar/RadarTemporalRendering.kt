package io.atmos.weather.radar

import android.graphics.BlendMode
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

internal data class RadarScene(val first:RadarFrame,val second:RadarFrame,val fraction:Float,
    val lower:Map<RadarTile,ImageBitmap>,val upper:Map<RadarTile,ImageBitmap>,val motion:RadarFlowPair?)

/** Reuses mesh storage; no bitmap allocation or network work occurs on the display frame. */
internal class RadarTemporalPainter {
    private val layer=Paint()
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    private val vertices=FloatArray((RadarMotionField.MESH+1)*(RadarMotionField.MESH+1)*2)

    fun draw(scope:DrawScope,camera:RadarCamera,density:Float,scene:RadarScene,opacity:Float) {
        val canvas=scope.drawContext.canvas.nativeCanvas
        val fraction=scene.fraction.coerceIn(0f,1f)
        layer.alpha=(opacity.coerceIn(0f,1f)*255).toInt()
        val checkpoint=canvas.saveLayer(0f,0f,scope.size.width,scope.size.height,layer)
        fun paint(images:Map<RadarTile,ImageBitmap>,flow:Map<RadarTile,RadarFlowGrid>?,progress:Float,alpha:Float,add:Boolean) {
            if(alpha<=0)return
            ink.alpha=(alpha.coerceIn(0f,1f)*255).toInt()
            ink.blendMode=if(add)BlendMode.PLUS else BlendMode.SRC_OVER
            ink.isFilterBitmap=camera.zoom<=if(scene.first.path.startsWith("cwa:"))CwaHighDefinitionRadar.MAX_ZOOM else RadarTilePolicy.RADAR_MAX_ZOOM
            images.forEach{(tile,image)->
                val bounds=RadarTileGeometry.bounds(camera,tile,scope.size.width,scope.size.height,density)
                if(bounds.right<=0||bounds.bottom<=0||bounds.left>=scope.size.width||bounds.top>=scope.size.height)return@forEach
                val saved=canvas.save()
                canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width/image.width,bounds.height/image.height)
                val grid=flow?.get(tile)
                val bitmap=image.asAndroidBitmap()
                if(grid==null||progress<=0f)canvas.drawBitmap(bitmap,0f,0f,ink)
                else {
                    for(y in 0..grid.rows)for(x in 0..grid.columns) {
                        val i=y*(grid.columns+1)+x
                        vertices[i*2]=x*image.width.toFloat()/grid.columns+grid.dx[i]*progress
                        vertices[i*2+1]=y*image.height.toFloat()/grid.rows+grid.dy[i]*progress
                    }
                    canvas.drawBitmapMesh(bitmap,grid.columns,grid.rows,vertices,0,null,0,ink)
                }
                canvas.restoreToCount(saved)
            }
        }
        try {
            // Add only inside this isolated transparent layer: opacity and rain colours stay stable.
            paint(scene.lower,scene.motion?.forward,fraction,1f-fraction,false)
            paint(scene.upper,scene.motion?.backward,1f-fraction,fraction,true)
        } finally{canvas.restoreToCount(checkpoint)}
    }
}
