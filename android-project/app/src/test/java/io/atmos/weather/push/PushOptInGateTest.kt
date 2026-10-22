package io.atmos.weather.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushOptInGateTest {
    @Test fun currentEnableAckAccepted() = assertTrue(PushOptInGate.accepts(3, 3, true, true))
    @Test fun currentDisableAckAccepted() = assertTrue(PushOptInGate.accepts(4, 4, false, false))
    @Test fun delayedEnableAfterDisableMustNotReenable() = assertFalse(PushOptInGate.accepts(3, 4, true, false))
    @Test fun delayedDisableAfterEnableMustNotDisable() = assertFalse(PushOptInGate.accepts(3, 4, false, true))
    @Test fun oldCallbackRejectedEvenWhenDesiredMatches() = assertFalse(PushOptInGate.accepts(2, 4, true, true))
    @Test fun mismatchedDesiredRejectedEvenWithSameEpoch() = assertFalse(PushOptInGate.accepts(4, 4, true, false))
}
