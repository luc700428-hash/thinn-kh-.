"""V25 native screen contracts. These checks do not certify device pixel fidelity."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT/'app/src/main/java/io/atmos/weather'
class V25IosNativeUiContract(unittest.TestCase):
    def file(self,path):return (BASE/path).read_text(encoding='utf-8')
    def test_five_tabs_and_persistent_tools(self):
        s=self.file('ui/GlassNavigation.kt')
        for token in ('"weather","quake","typhoon","alarm","settings"',
                      'NextToolAction("typhoon","颱風"',
                      'NextToolAction("alarm","鬧鐘"',
                      'maxVisible=5','onOrderChanged={ changed ->','onTasks:()->Unit'):
            self.assertIn(token,s)
    def test_swipe_is_a_real_gesture_not_just_visual_glow(self):
        s=self.file('next/NextReorderableToolDock.kt')
        for token in ('detectHorizontalDragGestures(',
                      'IosDockSwipePolicy.target(',
                      'scrubPreviewId','latestOnSelected(target)',
                      'detectDragGesturesAfterLongPress(',
                      'CustomAccessibilityAction('):
            self.assertIn(token,s)
    def test_navigation_does_not_drop_real_alarm_or_typhoon_pages(self):
        s=self.file('presentation/home/WeatherHomeScreen.kt')
        for token in ('onSelect = { tab ->','2 -> typhoonPageOpen=true',
                      '3 -> weatherAlarmOpen=true','4 -> showMainTab(3)',
                      'onTools = { showMainTab(2) }','onTasks = { tasksOpen=true }'):
            self.assertIn(token,s)
    def test_settings_grouped_and_functional(self):
        s=self.file('ui/GlassNavigation.kt')
        for token in ('IosSettingsGroup("APP 設定")','IosSettingsGroup("顯示與觸覺")',
                      'IosSettingsGroup("隱私與資訊")','IosSettingsRow("軟體更新"',
                      'onClick=onUpdateCenter','onClick=onDiagnostics','onClick=onAdvanced',
                      'GlassTrackingSlider("玻璃霧化"'):
            self.assertIn(token,s)
    def test_update_really_uses_secure_download_and_respects_android_consent(self):
        s=self.file('update/AppUpdatePage.kt')
        for token in ('AppUpdater.findUpdate(context)',
                      'AppUpdater.downloadAndInstall(context,release)',
                      'downloadJob?.cancel()',
                      'pending?.let {downloadOfficial(it)}',
                      'UpdateCheckWorker.schedule(context)',
                      '尚未確認完成安裝','Android 系統及使用者確認安裝',
                      'Text("軟體更新"'):
            self.assertIn(token,s)
        self.assertNotIn('現在已完成安裝',s)
    def test_same_cwa_warning_palette_and_preserve_official_evidence(self):
        s=self.file('presentation/home/WeatherHomeScreen.kt')
        self.assertGreaterEqual(s.count('Color(0xFF7C2540),Color(0xFF431E32)'),3)
        self.assertIn('官方公告未提供可核對的警戒區域清單',s)
        self.assertIn('CwaCountyHazardFeed.OFFICIAL_URL',s)
    def test_release_gate_and_signature_check_retained(self):
        wf=(ROOT.parent/'.github/workflows/build-apk.yml').read_text(encoding='utf-8')
        for token in ('verify_next_release_gate.py','verify_v25_release_evidence.py',
                      'Official APK signature differs','publish_release:',
                      'publish_maintenance:','發布完整 V25（需驗收通過）'):
            self.assertIn(token,wf)
if __name__=='__main__':unittest.main()
