package io.atmos.weather.permission

import org.junit.Assert.*
import org.junit.Test
import io.atmos.weather.permission.OnboardingPermissions.Item

class OnboardingPermissionsTest {
    @Test fun theChecklistMatchesWhatEachAndroidVersionHas() {
        assertEquals(listOf(Item.LOCATION, Item.BATTERY, Item.OVERLAY, Item.DND), OnboardingPermissions.items(29))
        assertEquals(Item.entries.toList(), OnboardingPermissions.items(36))
        assertTrue(Item.NOTIFICATIONS in OnboardingPermissions.items(33) && Item.FULL_SCREEN !in OnboardingPermissions.items(33))
        // The four a warning needs to pop up come first.
        assertEquals(listOf(Item.LOCATION, Item.NOTIFICATIONS, Item.BATTERY, Item.FULL_SCREEN), Item.entries.filter { it.essential })
        Item.entries.forEach { assertTrue(it.reason.length in 8..30) }
    }
}
