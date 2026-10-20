"""V25 OTA update center regression checks: no simulated install or technical jargon."""
from pathlib import Path
import unittest
BASE=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class V25UpdateCenter(unittest.TestCase):
    def test_new_center_has_real_version_and_readable_hierarchy(self):
        src=(BASE/'update/AppUpdatePage.kt').read_text(encoding='utf-8')
        for expected in ('V${BuildConfig.VERSION_NAME.substringBefore',
                         '此版本已安裝在你的手機', '這次更新帶來什麼',
                         'ThinnOpticalMaterial.surface', '系統更新'):
            self.assertIn(expected,src)
        self.assertNotIn('程式來源：',src)
        self.assertNotIn('更新來源：GitHub Releases',src)
        # Authentic provenance remains available behind an opt-in details row.
        self.assertIn('BuildConfig.THINN_BUILD_SHA.take(10)',src)
        self.assertIn('var showBuildDetails by remember { mutableStateOf(false) }',src)
        self.assertIn('if(showBuildDetails)',src)
        self.assertIn('Modifier.clickable',src)
    def test_integrated_state_is_real_and_never_fakes_install_success(self):
        src=(BASE/'update/AppUpdatePage.kt').read_text(encoding='utf-8')
        for expected in ('AppUpdater.findUpdate(context)',
                         'AppUpdater.downloadAndInstall(context,release)',
                         'installerPrompted', 'NextOtaStatusCard(',
                         'downloadJob?.cancel()'):
            self.assertIn(expected,src)
        self.assertNotIn('NextOtaState.Completed -> "已確認完成升級。"',src)
    def test_status_card_no_longer_uses_next_brand(self):
        src=(BASE/'next/NextOtaPresentation.kt').read_text(encoding='utf-8')
        self.assertIn('V25 · 更新狀態',src)
        self.assertNotIn('THINN-KHÌ NEXT',src)
        self.assertIn('ProgressBarRangeInfo',src)
    def test_release_gates_remain_mandatory(self):
        import json
        root=Path(__file__).resolve().parents[2]
        g=json.loads((root/'tools/next_release_gate.json').read_text())
        self.assertEqual(len(g['requirements']),22)
        self.assertFalse(g['verified_full_release'])
        w=(root.parent/'.github/workflows/build-apk.yml').read_text()
        self.assertIn('verify_v25_release_evidence.py',w)
        self.assertIn('verify_next_release_gate.py',w)
if __name__=='__main__':unittest.main()
