package io.atmos.weather.widgets

/** Receiver identity is stable; content shares the adaptive region layout. */
class BuddyClockWidget:AtmosWidget(28)
class BuddyClockWidgetReceiver:SyncedWidgetReceiver(){override val glanceAppWidget=BuddyClockWidget()}
class BuddyClockRefreshReceiver:android.content.BroadcastReceiver(){
    override fun onReceive(context:android.content.Context,intent:android.content.Intent){WidgetSync.request(context)}
}
