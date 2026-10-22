package io.atmos.weather.task

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReminderPolicyTest {
    @Test fun cannotDeliverEarlyOrOnObsoleteSchedule() {
        val due=10_000L
        assertFalse(TaskReminderPolicy.isDue(due,due-1))
        assertTrue(TaskReminderPolicy.isDue(due,due))
        assertFalse(TaskReminderPolicy.sameSchedule(due,due+1))
        assertTrue(TaskReminderPolicy.sameSchedule(due,due))
        assertFalse(TaskReminderPolicy.sameSchedule(0L,0L))
    }
}
