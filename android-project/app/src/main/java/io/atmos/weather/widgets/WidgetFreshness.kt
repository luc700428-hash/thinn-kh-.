package io.atmos.weather.widgets

/** Widget values are always labelled by freshness: cached weather is not live observation. */
internal object WidgetFreshness {
    enum class State { UNAVAILABLE, RECENT, STALE }
    private const val MAX_RECENT_AGE_MS = 90L * 60_000L

    fun state(fetchedAtMillis: Long, nowMillis: Long): State = when {
        fetchedAtMillis <= 0L -> State.UNAVAILABLE
        nowMillis < fetchedAtMillis -> State.STALE // clock change or invalid future timestamp
        nowMillis - fetchedAtMillis > MAX_RECENT_AGE_MS -> State.STALE
        else -> State.RECENT
    }
    fun label(state: State): String = when (state) {
        State.UNAVAILABLE -> "待同步"
        State.RECENT -> "最近更新"
        State.STALE -> "舊資料"
    }
    fun detail(state: State): String = when (state) {
        State.UNAVAILABLE -> "尚未取得天氣資料"
        State.RECENT -> "最近更新的預報資料"
        State.STALE -> "上次資料 · 非即時"
    }
}
