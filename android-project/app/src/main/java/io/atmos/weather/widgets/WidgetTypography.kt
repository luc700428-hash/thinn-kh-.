package io.atmos.weather.widgets

import android.content.Context

/** A launcher-only preference, independent from the app's reading size. */
object WidgetTypography {
    const val KEY="widget_text_scale_v1"
    const val MIN=.8f
    const val MAX=1.6f
    fun bounded(value:Float)=value.takeIf{it.isFinite()}?.coerceIn(MIN,MAX)?:1f
    fun read(context:Context)=bounded(context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).getFloat(KEY,1f))
    fun save(context:Context,value:Float) {
        context.getSharedPreferences("thinn_settings",Context.MODE_PRIVATE).edit().putFloat(KEY,bounded(value)).apply()
        WidgetSync.refreshCached(context)
    }
}
