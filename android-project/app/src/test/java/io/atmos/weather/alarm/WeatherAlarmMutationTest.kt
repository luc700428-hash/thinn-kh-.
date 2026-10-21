package io.atmos.weather.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherAlarmMutationTest {
    private val morning = WeatherAlarm(1,7,30,"起床")
    private val evening = WeatherAlarm(2,20,10,"夜間")

    @Test fun backgroundOneShotDisableSurvivesEditingAnotherAlarm() {
        val stored=listOf(morning.copy(enabled=false),evening)
        val screen=listOf(morning,evening)
        val edited=listOf(morning,evening.copy(minute=20))
        val corrected=WeatherAlarmMutation.preserveBackgroundChanges(stored,screen,edited)
        assertEquals(false,corrected.first().enabled)
        assertEquals(20,corrected.last().minute)
        assertEquals(setOf(2),WeatherAlarmMutation.changedIds(stored,corrected))
    }
    @Test fun unchangedDisabledAlarmIsNotRescheduledOrCancelled() {
        val stored=listOf(morning.copy(enabled=false),evening)
        val unchanged=stored.toList()
        assertEquals(emptySet<Int>(),WeatherAlarmMutation.changedIds(stored,unchanged))
    }
    @Test fun explicitEnableAndDeletionAreTracked() {
        val off=morning.copy(enabled=false)
        val enabled=morning.copy(enabled=true)
        assertEquals(setOf(1),WeatherAlarmMutation.changedIds(listOf(off),listOf(enabled)))
        assertEquals(setOf(1),WeatherAlarmMutation.changedIds(listOf(enabled),emptyList()))
        val result=WeatherAlarmMutation.preserveBackgroundChanges(listOf(off),listOf(off),listOf(enabled))
        assertEquals(true,result.single().enabled)
    }
}
