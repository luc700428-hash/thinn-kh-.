"""Source contract for signed stable-maintenance OTA; device tests still required."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
WF = ROOT / '.github/workflows/build-apk.yml'
APP = ROOT / 'android-project/app/src/main/java/io/atmos/weather/update/AppUpdater.kt'

class OfficialMaintenanceOtaTests(unittest.TestCase):
    def test_two_distinct_scopes_and_full_v25_gate(self):
        w=WF.read_text(encoding='utf-8')
        self.assertIn('publish_maintenance:',w)
        self.assertIn('inputs.publish_release == true || inputs.publish_maintenance == true',w)
        self.assertIn('python3 android-project/tools/verify_v25_release_evidence.py',w)
        self.assertIn('python3 android-project/tools/verify_next_release_gate.py',w)
        self.assertIn("if: github.event_name == 'workflow_dispatch' && inputs.publish_release == true",w)
        self.assertIn('Choose only one official release mode',w)
    def test_official_release_retains_apk_safety(self):
        w=WF.read_text(encoding='utf-8')
        for token in ('verify --verbose --print-certs thinn-khi.apk', 'old_cert', 'new_cert',
            'thinn-khi.apk.sha256', 'sha256sum thinn-khi.apk',
            'verified against previous official release', '--latest',
            "-PTHINN_OTA_CHANNEL=", 'Refusing duplicate or overwrite'):
            self.assertIn(token,w)
        a=APP.read_text(encoding='utf-8')
        self.assertIn('releases/latest/download/ota.json',a)
        self.assertIn('if (inPreviewChannel) previewManifestUrl else officialManifestUrl',a)
    def test_maintenance_does_not_masquerade_as_full_v25(self):
        w=WF.read_text(encoding='utf-8')
        self.assertIn('stable_maintenance_notes.md',w)
        self.assertIn('正式維護更新（非完整 V25）',w)
        note=(ROOT/'android-project/tools/stable_maintenance_notes.md').read_text(encoding='utf-8')
        self.assertIn('不是「全部 22 項 V25 功能已完成」的宣告',note)
        self.assertIn('版本號遞增',note)
if __name__=='__main__': unittest.main()
