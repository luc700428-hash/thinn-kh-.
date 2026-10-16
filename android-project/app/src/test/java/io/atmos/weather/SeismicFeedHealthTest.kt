package io.atmos.weather

import io.atmos.weather.earthquake.SeismicFeedHealth
import io.atmos.weather.earthquake.SeismicFeedState
import org.junit.Assert.assertEquals
import org.junit.Test

class SeismicFeedHealthTest {
    @Test fun stoppedIsNeverLiveEvenWithCachedReadings() {
        assertEquals(SeismicFeedState.STOPPED,
            SeismicFeedHealth.rts(false, "即時串流已連線 · test", 9_900, 10_000))
    }

    @Test fun connectedButNoDataIsNotRealTimeStationReading() {
        assertEquals(SeismicFeedState.CONNECTED_NO_FRAME,
            SeismicFeedHealth.rts(true, "即時串流已連線 · test", 0, 50_000))
    }

    @Test fun staleReadingsAreNotLiveEvenIfTransportIsConnected() {
        assertEquals(SeismicFeedState.STALE,
            SeismicFeedHealth.rts(true, "即時串流已連線 · test", 1_000, 17_000))
    }

    @Test fun dataFreshAtFifteenSecondsButNotAfter() {
        assertEquals(SeismicFeedState.LIVE,
            SeismicFeedHealth.rts(true, "即時串流已連線 · test", 1_000, 16_000))
        assertEquals(SeismicFeedState.STALE,
            SeismicFeedHealth.rts(true, "即時串流已連線 · test", 1_000, 16_001))
    }

    @Test fun connectionStatusIsNotConfusedWithAnEewBulletin() {
        assertEquals(SeismicFeedState.LIVE,
            SeismicFeedHealth.eew(true, "即時串流已連線 · test"))
        assertEquals(SeismicFeedState.CONNECTING,
            SeismicFeedHealth.eew(true, "連線中斷，等待重連"))
    }
}
