package io.atmos.weather.widgets
import androidx.glance.appwidget.GlanceAppWidget

/**
 * r20: only the Q-version (buddy) widgets remain. The 25 colour variants, the analog clock and
 * the plain safety widget were removed; three new buddy widgets take their place.
 */
class BuddyForecastWidget : AtmosWidget(29)
class BuddyForecastWidgetReceiver : SyncedWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BuddyForecastWidget() }
class BuddyOutingWidget : AtmosWidget(30)
class BuddyOutingWidgetReceiver : SyncedWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BuddyOutingWidget() }
class BuddyQuakeWidget : AtmosWidget(31)
class BuddyQuakeWidgetReceiver : SyncedWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BuddyQuakeWidget() }
