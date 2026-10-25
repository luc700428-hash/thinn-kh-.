package io.atmos.weather.next

/** Pixel geometry shared by the live dock gesture and its boundary tests. */
internal data class DockScrubGeometry(val width:Float,val inset:Float,val count:Int) {
    init {require(count>0)}
    val slot:Float get()=((width-2f*inset)/count).coerceAtLeast(1f)
    fun clamp(x:Float):Float {
        val first=inset+slot/2
        return x.coerceIn(first,(width-inset-slot/2).coerceAtLeast(first))
    }
    fun nearest(x:Float):Int=((clamp(x)-inset)/slot).toInt().coerceIn(0,count-1)
}
