package io.atmos.weather.earthquake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.PI
import kotlin.math.sin

/** Presentation pacing only; never delays station readings, freshness or warning delivery. */
internal object StationUpdateMotion {
    const val DURATION_MS=2800
    const val REST_MS=600L
    fun opacity(progress:Float):Float=if(progress.isFinite())
        (sin(progress.coerceIn(0f,1f)*PI).toFloat()*.36f).coerceAtLeast(0f) else 0f
    fun radiusDp(progress:Float)=5f+8f*(if(progress.isFinite())progress.coerceIn(0f,1f) else 0f)

    suspend fun play(frames:Flow<Long>,eligible:()->Boolean,animate:suspend ()->Unit) {
        // Finish one soft expansion; rapid provider frames coalesce into the latest update.
        frames.filter{it>0L}.distinctUntilChanged().conflate().collect {
            if(eligible()) {animate();delay(REST_MS)}
        }
    }
}
