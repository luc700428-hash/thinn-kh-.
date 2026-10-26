package io.atmos.weather.update
import org.junit.Assert.*
import org.junit.Test
class OtaVersionPolicyTest {
    @Test fun installed288MustNotBeRestoredAsAnUpdate() {
        assertFalse(OtaVersionPolicy.isNewer("25.0.288","25.0.288"))
        assertFalse(OtaVersionPolicy.isNewer("25.0.287","25.0.288"))
    }
    @Test fun preservesARealNewerDownloadAcrossAnAppUpgrade() {
        assertTrue(OtaVersionPolicy.isNewer("25.0.290","25.0.288",25000290L,25000288L))
    }
    @Test fun aBiggerVersionNameCannotBypassAndroidVersionCode() {
        assertFalse(OtaVersionPolicy.isNewer("25.0.289","25.0.288",25000288L,25000288L))
        assertFalse(OtaVersionPolicy.isNewer("25.0.289","25.0.288",25000287L,25000288L))
    }
}
