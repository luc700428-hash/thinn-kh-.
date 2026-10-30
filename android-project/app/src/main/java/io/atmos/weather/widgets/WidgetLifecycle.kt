package io.atmos.weather.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Preserve every existing receiver identity and restore scheduling after a host rebind. */
abstract class SyncedWidgetReceiver:GlanceAppWidgetReceiver() {
    private fun maintain(context:Context) {
        WidgetSync.schedule(context)
        val stamp=context.getSharedPreferences("weather_cache",Context.MODE_PRIVATE).getLong("widget_time",0)
        if(stamp<=0||System.currentTimeMillis()-stamp>15*60_000L)WidgetSync.request(context)
    }
    override fun onEnabled(context:Context){super.onEnabled(context);maintain(context)}
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){super.onUpdate(context,manager,ids);maintain(context)}
    override fun onRestored(context:Context,oldIds:IntArray,newIds:IntArray){super.onRestored(context,oldIds,newIds);maintain(context)}
}
class WidgetMaintenanceReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        WidgetSync.schedule(context);WidgetSync.refreshCached(context);WidgetSync.request(context)
    }
}
