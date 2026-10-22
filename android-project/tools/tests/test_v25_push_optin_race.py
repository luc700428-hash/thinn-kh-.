"""Source-level guard for V25 push-consent races; not proof of FCM delivery."""
import unittest
from pathlib import Path
APP = Path(__file__).resolve().parents[2] / 'app/src/main/java/io/atmos/weather/push'
TEST = Path(__file__).resolve().parents[2] / 'app/src/test/java/io/atmos/weather/push'

class PushConsentRaceContracts(unittest.TestCase):
    def test_last_requested_enable_state_controls_async_callbacks(self):
        s=(APP/'SeismicPushRegistration.kt').read_text(encoding='utf-8')
        self.assertIn('private val intentGeneration = AtomicLong(0L)',s)
        self.assertIn('PushOptInGate.accepts(request, intentGeneration.get(), enabled,',s)
        self.assertIn('if (current()) {',s)
        self.assertIn('else if (!pref.getBoolean(REQUESTED, false)) reconcile()',s)
        self.assertIn('else if (pref.getBoolean(REQUESTED, false)) reconcile()',s)
    def test_local_opt_out_applies_before_remote_callback(self):
        s=(APP/'SeismicPushRegistration.kt').read_text(encoding='utf-8')
        self.assertIn('putBoolean("enabled", false).apply()',s)
        self.assertIn('putBoolean(REQUESTED, enabled).putBoolean("enabled", false).apply()',s)
        self.assertIn('if (!enabled && !configured())',s)
    def test_user_can_cancel_pending_enable_and_token_refresh_respects_consent(self):
        s=(APP/'SeismicPushSettingsPage.kt').read_text(encoding='utf-8')
        r=(APP/'SeismicPushRegistration.kt').read_text(encoding='utf-8')
        self.assertIn('if (target && !SeismicPushRegistration.configured())',s)
        self.assertIn('enabled = target',s)
        self.assertIn('if (optedIn(ctx)) onResult("FCM 主題訂閱維持中")',r)
    def test_pure_gate_is_exercised_by_android_unit_tests(self):
        p=(APP/'PushOptInGate.kt').read_text(encoding='utf-8')
        t=(TEST/'PushOptInGateTest.kt').read_text(encoding='utf-8')
        self.assertIn('request == latest && requestEnabled == currentDesired',p)
        for name in ('delayedEnableAfterDisableMustNotReenable','delayedDisableAfterEnableMustNotDisable','oldCallbackRejectedEvenWhenDesiredMatches'):
            self.assertIn(name,t)

if __name__ == '__main__': unittest.main()
