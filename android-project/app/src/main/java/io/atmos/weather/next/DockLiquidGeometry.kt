package io.atmos.weather.next

import kotlin.math.abs
import kotlin.math.min

/** Both liquid bodies keep the exact same selection-cell dimensions. */
internal data class DockLiquidShape(
    val headX:Float,val tailX:Float,val width:Float,val height:Float,
    val centerY:Float,val neckHalfHeight:Float,val connected:Boolean
)

internal object DockLiquidGeometry {
    fun shape(width:Float,height:Float,density:Float,count:Int,head:Float,tail:Float,vertical:Boolean=false):DockLiquidShape? {
        if(count<1 || listOf(width,height,density,head,tail).any {!it.isFinite()} ||
            width<=0f || height<=0f || density<=0f) return null
        // Match DockAxisLayout: 6 dp at the horizontal ends, 5 dp at vertical ends.
        val inset=min((if(vertical)5f else 6f)*density,width*.05f)
        val slot=(width-2*inset)/count
        val bodyWidth=(slot-4f*density).coerceIn(1f,slot.coerceAtLeast(1f))
        // A fixed 56 dp cap made the tablet lens smaller than its icon/caption cell.
        val bodyHeight=(height-12f*density).coerceAtLeast(1f)
        val headX=inset+(head.coerceIn(0f,count-1f)+.5f)*slot
        val tailX=inset+(tail.coerceIn(0f,count-1f)+.5f)*slot
        val pull=abs(headX-tailX)/slot
        val adhesion=(1f-(pull-1.30f).coerceAtLeast(0f)/.70f).coerceIn(0f,1f)
        val neck=bodyHeight*.5f*(.88f-pull*.24f).coerceAtLeast(.10f)*adhesion
        return DockLiquidShape(headX,tailX,bodyWidth,bodyHeight,height*.5f,neck,adhesion>.01f)
    }
}
