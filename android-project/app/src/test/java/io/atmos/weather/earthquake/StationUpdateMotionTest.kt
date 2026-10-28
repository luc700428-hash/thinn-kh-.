package io.atmos.weather.earthquake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StationUpdateMotionTest {
    @Test fun rapidFramesFinishOneSlowRingBeforeShowingTheLatest()=runTest {
        val frames=MutableSharedFlow<Long>(extraBufferCapacity=10)
        val starts=mutableListOf<Long>()
        val job=backgroundScope.launch {
            StationUpdateMotion.play(frames,{true}) {
                starts.add(testScheduler.currentTime);delay(StationUpdateMotion.DURATION_MS.toLong())
            }
        }
        runCurrent();frames.emit(1);runCurrent()
        advanceTimeBy(1000);frames.emit(2);runCurrent()
        advanceTimeBy(1000);frames.emit(3);runCurrent()
        advanceTimeBy(1399);runCurrent();assertEquals(listOf(0L),starts)
        advanceTimeBy(1);runCurrent();assertEquals(listOf(0L,3400L),starts)
        job.cancel()
    }
    @Test fun emptyDuplicateAndIneligibleFramesDoNotCreateFlashes()=runTest {
        var calls=0
        StationUpdateMotion.play(flowOf(0L,-1L,7L,7L),{true}){calls++}
        assertEquals(1,calls)
        StationUpdateMotion.play(flowOf(8L),{false}){calls++}
        assertEquals(1,calls)
    }
    @Test fun ringFadesInAndOutWithBoundedOpacityAndSlowExpansion() {
        assertEquals(0f,StationUpdateMotion.opacity(0f),0f)
        assertEquals(0f,StationUpdateMotion.opacity(1f),.00001f)
        assertEquals(.36f,StationUpdateMotion.opacity(.5f),.00001f)
        assertTrue(StationUpdateMotion.opacity(.1f)<StationUpdateMotion.opacity(.5f))
        assertTrue(StationUpdateMotion.radiusDp(.25f)<StationUpdateMotion.radiusDp(.75f))
        assertEquals(0f,StationUpdateMotion.opacity(Float.NaN),0f)
    }
}
