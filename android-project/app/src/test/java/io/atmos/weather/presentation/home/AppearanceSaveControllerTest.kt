package io.atmos.weather.presentation.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSaveControllerTest {
    @Test fun continuousDragPreviewsDoNotWriteAtDisplayFrameRate()=runTest {
        val initial=WeatherAppearance()
        val writes=mutableListOf<WeatherAppearance>()
        var widgets=0
        val writer=AppearanceSaveController(initial,this,{writes+=it},{widgets++})
        writer.update(initial)
        advanceTimeBy(1000);runCurrent()
        assertTrue(writes.isEmpty());assertEquals(0,widgets)
        for(i in 1..100) {
            writer.update(initial.copy(glassFrost=i/100f))
            advanceTimeBy(6);runCurrent()
        }
        assertTrue(writes.isEmpty());assertEquals(0,widgets)
        advanceTimeBy(160);runCurrent()
        assertEquals(listOf(initial.copy(glassFrost=1f)),writes)
        assertEquals(0,widgets)
        advanceTimeBy(640);runCurrent()
        assertEquals(1,widgets)
    }

    @Test fun backgroundOrDisposalKeepsTheFinalValueAndRefreshesPlacedWidgetsOnce()=runTest {
        val initial=WeatherAppearance()
        val final=initial.copy(fontScale=1.5f,glassOpacity=.4f)
        val writes=mutableListOf<WeatherAppearance>()
        var widgets=0
        val writer=AppearanceSaveController(initial,this,{writes+=it},{widgets++})
        writer.update(final)
        advanceTimeBy(6);runCurrent()
        writer.flush();writer.flush()
        advanceUntilIdle()
        assertEquals(listOf(final),writes)
        assertEquals(1,widgets)
        val resumed=final.copy(edgeLight=.9f)
        writer.update(resumed);advanceUntilIdle()
        assertEquals(listOf(final,resumed),writes)
        assertEquals(2,widgets)
    }

    @Test fun resumingDragCancelsTheOutdatedLauncherRefresh()=runTest {
        val initial=WeatherAppearance()
        val writes=mutableListOf<WeatherAppearance>()
        var widgets=0
        val writer=AppearanceSaveController(initial,this,{writes+=it},{widgets++})
        val first=initial.copy(glassFrost=.2f)
        val last=initial.copy(glassFrost=.8f)
        writer.update(first);advanceTimeBy(200);runCurrent()
        writer.update(last);advanceTimeBy(600);runCurrent()
        assertEquals(0,widgets)
        advanceUntilIdle()
        assertEquals(listOf(first,last),writes)
        assertEquals(1,widgets)
    }
}
