"""V25 stage 10 static regression and fail-closed release evidence tests."""
from pathlib import Path
import importlib.util
import json
import unittest
ROOT=Path(__file__).resolve().parents[2]
APP=ROOT/'app/src/main/java/io/atmos/weather'
DIAG=APP/'presentation/home/SettingsDiagnosticsPage.kt'
REPORT=ROOT/'tools/report_v25_release_readiness.py'

class Stage10DiagnosticsSafety(unittest.TestCase):
    def test_actual_manual_cwa_proxy_not_fake_success(self):
        s=DIAG.read_text(encoding='utf-8')
        for needle in ('CwaOfficialGateway.read(ctx,"E-A0015-001")',
                       'withTimeoutOrNull(20_000L)',
                       'CwaOfficialGateway.describeFailure(error)',
                       'gatewaySuccess=response?.optJSONObject("records")!=null',
                       'mutableStateOf("尚未測試',
                       '不代表地震即時速報、颱風或 FCM 已驗證'):
            self.assertIn(needle,s)
        self.assertNotIn('gatewaySuccess=true',s)
    def test_widget_and_xiaomi_state_not_assumed(self):
        s=DIAG.read_text(encoding='utf-8')
        self.assertIn('thinn-khi-widget-interval-v3',s)
        self.assertIn('XiaomiFocusBridge.availability(ctx)',s)
        self.assertIn('實際上島仍需事件及小米系統批准',s)
        self.assertIn('BuildConfig.THINN_BUILD_SHA.take(10)',s)
        self.assertIn('SeismicPushRegistration.configured()',s)
    def test_gate_is_fail_closed_without_real_proof(self):
        gate=json.loads((ROOT/'tools/next_release_gate.json').read_text(encoding='utf-8'))
        spec=importlib.util.spec_from_file_location('v25_report',REPORT)
        # report imports verify_v25_release_evidence from its own folder
        import sys
        sys.path.insert(0,str(REPORT.parent))
        module=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        report=module.summarize(gate)
        self.assertEqual(report['requirement_count'],22)
        self.assertEqual(report['readiness'],'BLOCKED')
        self.assertGreater(report['missing_proof_checks'],0)
        self.assertNotEqual(report['evidence_backed_candidates'],22)
    def test_non_release_script_structure(self):
        s=(ROOT/'../.github/workflows/build-apk.yml').read_text(encoding='utf-8')
        self.assertIn('publish_release',s)
        self.assertIn('Only main can publish OTA',s)

if __name__=='__main__':unittest.main()
