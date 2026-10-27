package io.atmos.weather.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/** Numeric and optical bounds shared by drawing, pointer mapping and accessibility. */
internal object BubbleSliderGeometry {
    const val insetDp=30f
    fun fraction(value:Float,low:Float,high:Float):Float {
        if(!value.isFinite() || !low.isFinite() || !high.isFinite() || high<=low) return 0f
        return ((value-low)/(high-low)).coerceIn(0f,1f)
    }
    fun quantize(value:Float,low:Float,high:Float,steps:Int):Float {
        if(!low.isFinite() || !high.isFinite() || high<=low) return if(low.isFinite())low else 0f
        val f=fraction(value,low,high)
        val intervals=steps.coerceIn(0,10_000)+1
        return low+(high-low)*(if(steps>0)(f*intervals).roundToInt()/intervals.toFloat() else f)
    }
    data class Lens(val head:Float,val tail:Float,val y:Float,val width:Float,val height:Float) {
        val distance get()=abs(head-tail)
        val neck get()=(height*.43f*(1f-distance/(width*1.8f))).coerceAtLeast(height*.10f)
    }
    fun lens(width:Float,height:Float,density:Float,head:Float,tail:Float,lift:Float,reduceMotion:Boolean):Lens? {
        if(listOf(width,height,density,head,tail,lift).any{!it.isFinite()} || width<=0 || height<=2 || density<=0)return null
        val inset=(insetDp*density).coerceAtMost(width/2)
        val travel=(width-2*inset).coerceAtLeast(0f)
        val h=inset+travel*head.coerceIn(0f,1f)
        val expansion=if(reduceMotion)0f else lift.coerceIn(0f,1f)
        val body=(56f+3f*expansion)*density
        val t=if(reduceMotion)h else h+(inset+travel*tail.coerceIn(0f,1f)-h).coerceIn(-body*1.4f,body*1.4f)
        return Lens(h,t,height/2,body.coerceAtMost(width),(34f+3f*expansion).times(density).coerceAtMost(height-2f))
    }
}
