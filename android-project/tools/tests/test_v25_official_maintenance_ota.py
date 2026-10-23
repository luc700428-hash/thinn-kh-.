"""Source contract for signed stable-maintenance OTA; device tests still required."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
WF = ROOT / '.github/workflows/build-apk.yml'
APP = ROOT / 'android-project/app/src/main/java/io/atmos/weather/update/AppUpdater.kt'

class OfficialMaintenanceOtaTests(unittest.TestCase):
    def test_single_manual_official_ota_no_duplicate_mode(self):
        w=WF.read_text(encoding='utf-8')
        self.assertIn('  workflow_dispatch:',w)
        self.assertNotIn('publish_release:',w)
        self.assertNotIn('publish_maintenance:',w)
        self.assertNotIn('verify_next_release_gate.py',w)
        self.assertNotIn('Publish V25 test OTA (pre-release only)',w)
        self.assertIn('Publish official OTA GitHub Release',w)
        self.assertIn('-PTHINN_OTA_CHANNEL="official"',w)
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
    def test_release_notes_do_not_claim_22_items_verified(self):
        w=WF.read_text(encoding='utf-8')
        self.assertIn('stable_maintenance_notes.md',w)
        self.assertIn('正式 OTA 更新',w)
        note=(ROOT/'android-project/tools/stable_maintenance_notes.md').read_text(encoding='utf-8')
        self.assertNotIn('未宣稱 22 項功能已全部完成',note)
        self.assertIn('APK 簽章',note)
        self.assertIn('地震與颱風警報請以中央氣象署官方資訊為準',note)
        self.assertNotIn('22 項功能已通過實機驗收',note)
if __name__=='__main__': unittest.main()
