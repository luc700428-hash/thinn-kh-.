package io.atmos.weather.ui

/** Size from content, independent of tablet/window width. */
internal object FloatingDockContent {
    fun length(items:Int,fontScale:Float,vertical:Boolean=false):Float {
        val scale=fontScale.takeIf{it.isFinite()}?.coerceIn(1f,3f)?:1f
        return items.coerceAtLeast(1)*maxOf(64f,47f+24f*scale)+(if(vertical)10f else 12f)
    }
}
