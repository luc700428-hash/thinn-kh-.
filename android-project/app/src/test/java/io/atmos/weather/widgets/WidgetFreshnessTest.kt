package io.atmos.weather.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFreshnessTest {
    private val now=1_000_000_000L

    @Test fun neverLabelsMissingFutureOrOldAsRecent() {
        assertEquals(WidgetFreshness.State.UNAVAILABLE, WidgetFreshness.state(0,now))
        assertEquals(WidgetFreshness.State.STALE, WidgetFreshness.state(now+1,now))
        assertEquals(WidgetFreshness.State.STALE, WidgetFreshness.state(now-91L*60_000L,now))
    }
    @Test fun recentAndBoundaryAreAllowed() {
        assertEquals(WidgetFreshness.State.RECENT, WidgetFreshness.state(now,now))
        assertEquals(WidgetFreshness.State.RECENT, WidgetFreshness.state(now-90L*60_000L,now))
        assertEquals("上次資料 · 非即時", WidgetFreshness.detail(WidgetFreshness.State.STALE))
    }
}
