package io.atmos.weather.demo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Local-only simulated weather events. Not an OS alert or an official CWA message. */
enum class WeatherDemoEvent { Start, Stop }

object WeatherDemoDispatcher {
    private val signal = MutableSharedFlow<WeatherDemoEvent>(extraBufferCapacity=4)
    val events = signal.asSharedFlow()
    fun dispatch(event: WeatherDemoEvent): Boolean = signal.tryEmit(event)
}
