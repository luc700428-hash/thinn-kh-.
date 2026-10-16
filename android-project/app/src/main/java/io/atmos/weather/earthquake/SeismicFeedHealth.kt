package io.atmos.weather.earthquake

/**
 * Distinguish "the monitoring service is running" from "the network is connected"
 * and "fresh instrument readings exist".  An EEW SSE connection can be healthy
 * even when no earthquake has occurred and no EEW bulletin has been received.
 * This is UI guidance, never an official assessment that a region is safe.
 */
enum class SeismicFeedState { STOPPED, CONNECTING, CONNECTED_NO_FRAME, LIVE, STALE }

object SeismicFeedHealth {
    private const val RTS_FRESH_MS = 15_000L

    fun rts(enabled: Boolean, status: String, lastAt: Long, now: Long): SeismicFeedState {
        if (!enabled) return SeismicFeedState.STOPPED
        val connected = status.startsWith("即時串流已連線")
        if (!connected) return SeismicFeedState.CONNECTING
        if (lastAt <= 0L) return SeismicFeedState.CONNECTED_NO_FRAME
        val age = now - lastAt
        return if (age in 0..RTS_FRESH_MS) SeismicFeedState.LIVE else SeismicFeedState.STALE
    }

    fun eew(enabled: Boolean, status: String): SeismicFeedState = when {
        !enabled -> SeismicFeedState.STOPPED
        status.startsWith("即時串流已連線") -> SeismicFeedState.LIVE
        else -> SeismicFeedState.CONNECTING
    }

    fun rtsLabel(value: SeismicFeedState): String = when (value) {
        SeismicFeedState.STOPPED -> "未啟動監測"
        SeismicFeedState.CONNECTING -> "連線中或正在重試"
        SeismicFeedState.CONNECTED_NO_FRAME -> "已連線，等待測站資料"
        SeismicFeedState.LIVE -> "測站讀數正常更新"
        SeismicFeedState.STALE -> "測站資料超過 15 秒，已隱藏舊讀數"
    }

    fun eewLabel(value: SeismicFeedState): String = when (value) {
        SeismicFeedState.STOPPED -> "未啟動監測"
        SeismicFeedState.CONNECTING -> "速報線路未連線，正在嘗試"
        SeismicFeedState.CONNECTED_NO_FRAME, SeismicFeedState.LIVE -> "速報線路已連線；無地震時可能沒有訊息"
        SeismicFeedState.STALE -> "速報線路狀態待確認"
    }
}
