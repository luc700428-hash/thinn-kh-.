"""Source regression contracts; not a substitute for actual Firebase/Android tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
GATEWAY = ROOT / 'app/src/main/java/io/atmos/weather/earthquake/CwaOfficialGateway.kt'
HEALTH = ROOT / 'app/src/main/java/io/atmos/weather/presentation/home/SettingsDiagnosticsPage.kt'
GATE = ROOT / 'tools/next_release_gate.json'

class CwaGatewayTimeoutContract(unittest.TestCase):
    def test_both_official_paths_are_bounded(self):
        data = GATEWAY.read_text(encoding='utf-8')
        self.assertEqual(data.count('withTimeoutOrNull(18_000L)'), 2)
        self.assertIn('readTyphoonProbabilityKmzUnbounded(context)', data)
        self.assertIn('readProxy(context,dataset)', data)
        self.assertIn('SocketTimeoutException', data)

    def test_timeout_is_not_conflated_with_parent_cancellation(self):
        data = GATEWAY.read_text(encoding='utf-8')
        self.assertIn('return withTimeoutOrNull(18_000L) { readProxy(context,dataset) }', data)
        self.assertIn('?: throw java.net.SocketTimeoutException', data)
        self.assertNotIn('catch (_: kotlinx.coroutines.CancellationException)', data)
        self.assertIn('官方資料代理連線逾時', data)

    def test_diagnostic_remains_manual_and_non_alarm(self):
        health = HEALTH.read_text(encoding='utf-8')
        self.assertIn('"E-A0015-001"', health)
        self.assertIn('手動真實連線檢查', health)
        self.assertIn('CwaOfficialGateway.describeFailure(error)', health)
        self.assertIn('不會產生正式地震警報', health)

    def test_release_is_still_gated(self):
        import json
        gate = json.loads(GATE.read_text(encoding='utf-8'))
        self.assertFalse(gate['released'])
        self.assertFalse(gate['verified_full_release'])
        self.assertEqual(len(gate['requirements']), 22)
        self.assertTrue(any(i['status'] != 'verified' for i in gate['requirements']))

if __name__ == '__main__':
    unittest.main()
