package io.atmos.weather.widgets
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
/** Native AnalogClock and TextClock tick inside the launcher without periodic app wakeups. */
class PrecisionClockWidget:AtmosWidget(26)
class PrecisionClockWidgetReceiver:SyncedWidgetReceiver() {
    override val glanceAppWidget:GlanceAppWidget=PrecisionClockWidget()
}
