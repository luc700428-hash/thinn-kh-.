package io.atmos.weather.presentation.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Preview stays immediate; disk writes and launcher rendering wait for a pause in dragging. */
internal class AppearanceSaveController(
    initial:WeatherAppearance,
    private val scope:CoroutineScope,
    private val save:(WeatherAppearance)->Unit,
    private val queueWidgets:()->Unit
) {
    private var latest=initial
    private var saved=initial
    private var queued=initial
    private var job:Job?=null

    fun update(value:WeatherAppearance) {
        if(value==latest)return
        latest=value
        job?.cancel()
        job=scope.launch {
            delay(160L)
            persist()
            delay(640L)
            refresh()
        }
    }

    /** Leaving the app must keep the final value and update the launcher, even mid-drag. */
    fun flush() {
        job?.cancel();job=null
        persist()
        refresh()
    }

    private fun persist() {
        if(latest!=saved) {
            save(latest)
            saved=latest
        }
    }

    private fun refresh() {
        if(latest!=queued) {
            queueWidgets()
            queued=latest
        }
    }
}
