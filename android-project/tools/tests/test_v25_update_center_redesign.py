"""V25 OTA update center regression checks: no simulated install or technical jargon."""
from pathlib import Path
import unittest
BASE=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class V25UpdateCenter(unittest.TestCase):
    def test_update_layout_is_light_and_uses_verified_release_metadata(self):
        src=(BASE/'update/AppUpdatePage.kt').read_text(encoding='utf-8')
        for expected in ('軟體更新', '自動檢查更新', 'Beta 版更新項目',
                         'pending?.version ?: BuildConfig.VERSION_NAME',
                         'Color(0xFFF5F6FA)', 'UpdatePanel(glow=light)',
                         'UpdateReleaseNotes(pendingDelta.ifBlank',
                         '尚未確認完成安裝'):
            self.assertIn(expected,src)
        self.assertNotIn('更新來源：GitHub Releases',src)
    def test_true_download_and_cancel_with_no_fake_overnight_install(self):
        src=(BASE/'update/AppUpdatePage.kt').read_text(encoding='utf-8')
        for expected in ('AppUpdater.findUpdate(context)',
                         'AppUpdater.downloadAndInstall(context,release)',
                         'installerPrompted', 'downloadJob?.cancel()',
                         'UpdateCheckWorker.schedule(context)',
                         'pending?.let {downloadOfficial(it)}'):
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
