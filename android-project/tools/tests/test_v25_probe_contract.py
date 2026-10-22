"""Source integration contract; NOT evidence of a live API connection."""
from pathlib import Path
import unittest
import json
R=Path(__file__).resolve().parents[2]
A=R/'app/src/main/java/io/atmos/weather'
class V25DataProbeContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.page=(A/'presentation/home/SettingsDiagnosticsPage.kt').read_text(encoding='utf-8')
        cls.rules=(A/'presentation/home/V25DataProbeRules.kt').read_text(encoding='utf-8')
    def test_weather_probe_forces_fetch_and_does_not_use_user_location(self):
        self.assertIn('AppContainer.repository(ctx).load(SampleCities.all.first(),true)',self.page)
        self.assertIn('result.fromCache,result.stale',self.page)
        self.assertIn('NETWORK_FRESH',self.page)
        self.assertIn('CACHED',self.page)
        self.assertIn('INVALID_TIME',self.page)
        self.assertIn('if (fromCache || stale)',self.rules)
    def test_cwa_typhoon_probe_checks_real_normalized_record_and_points(self):
        self.assertIn('CwaOfficialGateway.read(ctx,"W-C0034-005")',self.page)
        self.assertIn('optJSONObject("TropicalCyclones")',self.page)
        self.assertIn('OfficialTyphoonTrack.extract(response)',self.page)
        self.assertIn('不代表服務失敗或一定沒有颱風',self.page)
    def test_existing_gateway_and_fcm_separation_preserved(self):
        self.assertIn('CwaOfficialGateway.read(ctx,"E-A0015-001")',self.page)
        self.assertIn('不會觸發地震推播',self.page)
        gate=json.loads((R/'tools/next_release_gate.json').read_text(encoding='utf-8'))
        self.assertEqual(len(gate['requirements']),22)
        self.assertFalse(gate['verified_full_release'])
    def test_no_false_success_or_unsafe_publish(self):
        self.assertIn('weatherProbeRunning=true',self.page)
        self.assertIn('typhoonProbeRunning=true',self.page)
        self.assertIn('weatherProbeOk=false',self.page)
        self.assertIn('typhoonProbeOk=false',self.page)
        self.assertNotIn('fontSize=13.sp',self.page)
if __name__=='__main__': unittest.main()
