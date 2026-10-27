package io.atmos.weather.ui

import kotlin.math.ceil

/** A viewport intersection, never an unbounded copy of a scrolling page. */
internal data class BackdropSample(val left:Float,val top:Float,val right:Float,val bottom:Float) {
    val width:Int get()=ceil(right-left).toInt()
    val height:Int get()=ceil(bottom-top).toInt()
}

internal object BackdropSampling {
    private const val MAX_EDGE=8192
    private const val MAX_PIXELS=16_777_216L
    fun canCapture(width:Int,height:Int):Boolean = width in 1..MAX_EDGE && height in 1..MAX_EDGE &&
        width.toLong()*height<=MAX_PIXELS

    fun plan(sourceWidth:Int,sourceHeight:Int,width:Float,height:Float,dx:Float,dy:Float,
        visibleLeft:Float=0f,visibleTop:Float=0f,visibleRight:Float=width,visibleBottom:Float=height
    ):BackdropSample? {
        if(!canCapture(sourceWidth,sourceHeight) ||
            listOf(width,height,dx,dy,visibleLeft,visibleTop,visibleRight,visibleBottom).any {!it.isFinite()}) return null
        val left=maxOf(0f,dx,visibleLeft)
        val top=maxOf(0f,dy,visibleTop)
        val right=minOf(width,dx+sourceWidth,visibleRight)
        val bottom=minOf(height,dy+sourceHeight,visibleBottom)
        if(right<=left || bottom<=top) return null
        return BackdropSample(left,top,right,bottom).takeIf {canCapture(it.width,it.height)}
    }
}
