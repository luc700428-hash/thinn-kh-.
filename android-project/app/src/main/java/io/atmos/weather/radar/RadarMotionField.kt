package io.atmos.weather.radar

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.*

/** Measured-image motion for display only. Confidence failures use a stationary cross-fade. */
internal data class RadarFlowGrid(val columns:Int,val rows:Int,val dx:FloatArray,val dy:FloatArray)
internal data class RadarFlowPair(val forward:Map<RadarTile,RadarFlowGrid>,val backward:Map<RadarTile,RadarFlowGrid>)

internal object RadarMotionField {
    const val SAMPLES=64
    const val MESH=4
    private const val STEP=SAMPLES/MESH
    private const val SEARCH=6
    private const val RADIUS=6

    internal data class Field(val width:Int,val height:Int,val values:FloatArray)

    private fun score(a:Field,b:Field,x:Int,y:Int,dx:Int,dy:Int):Float {
        var difference=0f;var activity=0f;var samples=0
        for(py in y-RADIUS..y+RADIUS)for(px in x-RADIUS..x+RADIUS) {
            val qx=px+dx;val qy=py+dy
            if(px !in 0 until a.width||py !in 0 until a.height||qx !in 0 until b.width||qy !in 0 until b.height)continue
            val first=a.values[py*a.width+px];val second=b.values[qy*b.width+qx]
            difference+=abs(first-second);activity+=max(first,second);samples++
        }
        return if(samples<20||activity<2f)Float.POSITIVE_INFINITY else difference/activity
    }

    internal fun displacement(a:Field,b:Field,x:Int,y:Int):Pair<Float,Float> {
        val stationary=score(a,b,x,y,0,0)
        if(!stationary.isFinite()||stationary<.035f)return 0f to 0f
        var best=stationary;var bx=0;var by=0
        for(dy in -SEARCH..SEARCH)for(dx in -SEARCH..SEARCH) {
            val cost=score(a,b,x,y,dx,dy)+.0015f*(dx*dx+dy*dy)
            if(cost<best){best=cost;bx=dx;by=dy}
        }
        // Do not invent cloud motion when precipitation appears/disappears or fails to match.
        return if(best<.48f && best<stationary*.82f)bx.toFloat() to by.toFloat() else 0f to 0f
    }

    private data class Mosaic(val field:Field,val column:Int,val row:Int)
    private fun mosaic(images:Map<RadarTile,ImageBitmap>):Mosaic? {
        if(images.isEmpty())return null
        val left=images.keys.minOf{it.column};val right=images.keys.maxOf{it.column}
        val top=images.keys.minOf{it.y};val bottom=images.keys.maxOf{it.y}
        // Extreme world-wrap views remain bounded and use the normal cross-fade instead.
        if(right-left>=4||bottom-top>=4||images.keys.map{it.z}.distinct().size!=1)return null
        val width=(right-left+1)*SAMPLES;val height=(bottom-top+1)*SAMPLES
        val out=FloatArray(width*height);val pixels=IntArray(SAMPLES*SAMPLES)
        images.forEach{(tile,image)->
            val original=image.asAndroidBitmap()
            val small=Bitmap.createScaledBitmap(original,SAMPLES,SAMPLES,true)
            small.getPixels(pixels,0,SAMPLES,0,0,SAMPLES,SAMPLES)
            for(y in 0 until SAMPLES)for(x in 0 until SAMPLES) {
                val color=pixels[y*SAMPLES+x];val alpha=(color ushr 24)/255f
                val green=(color ushr 8)and 255
                out[((tile.y-top)*SAMPLES+y)*width+(tile.column-left)*SAMPLES+x]=alpha*(1f+.25f*(255-green)/255f)
            }
            if(small!==original)small.recycle()
        }
        return Mosaic(Field(width,height,out),left,top)
    }

    fun estimate(first:Map<RadarTile,ImageBitmap>,second:Map<RadarTile,ImageBitmap>,checkActive:()->Unit={}):RadarFlowPair? {
        if(first.keys!=second.keys)return null
        val a=mosaic(first)?:return null;val b=mosaic(second)?:return null
        if(a.column!=b.column||a.row!=b.row||a.field.width!=b.field.width||a.field.height!=b.field.height)return null
        fun calculate(from:Mosaic,to:Mosaic,images:Map<RadarTile,ImageBitmap>):Map<RadarTile,RadarFlowGrid> {
            val columns=from.field.width/STEP+1;val rows=from.field.height/STEP+1
            val dx=FloatArray(columns*rows);val dy=FloatArray(columns*rows)
            for(y in 0 until rows) {
                checkActive()
                for(x in 0 until columns) {
                    val move=displacement(from.field,to.field,x*STEP,y*STEP)
                    dx[y*columns+x]=move.first;dy[y*columns+x]=move.second
                }
            }
            return images.mapValues{(tile,image)->
                val vx=FloatArray((MESH+1)*(MESH+1));val vy=FloatArray(vx.size)
                for(y in 0..MESH)for(x in 0..MESH) {
                    val sample=((tile.y-from.row)*MESH+y)*columns+(tile.column-from.column)*MESH+x
                    vx[y*(MESH+1)+x]=dx[sample]*image.width/SAMPLES
                    vy[y*(MESH+1)+x]=dy[sample]*image.height/SAMPLES
                }
                RadarFlowGrid(MESH,MESH,vx,vy)
            }
        }
        val forward=calculate(a,b,first);checkActive()
        return RadarFlowPair(forward,calculate(b,a,second))
    }
}
