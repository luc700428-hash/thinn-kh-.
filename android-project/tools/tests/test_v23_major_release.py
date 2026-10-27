"""Source-contract checks for v23. These do NOT substitute for a Gradle or device test."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
WORKFLOW=ROOT.parent/'.github/workflows/build-apk.yml'

class MajorV23Contract(unittest.TestCase):
    def test_workflow_is_new_major_and_tracks_real_code(self):
        w=WORKFLOW.read_text()
        # V25 replaces V23 as the next major; old release tags remain upgrade inputs.
        self.assertIn('25000000 + GITHUB_RUN_NUMBER',w)
        self.assertIn('25.0.$GITHUB_RUN_NUMBER',w)
        self.assertIn('^v(22|23|25)',w)
        self.assertNotIn('source_archive=',w)
        self.assertIn(':app:testDebugUnitTest',w)
        self.assertIn('expected-build-source-proof.json',w)
    def test_real_alarm_storage_scheduler_and_fullscreen_control(self):
        alarm=(JAVA/'alarm/WeatherAlarm.kt').read_text()
        page=(JAVA/'alarm/WeatherAlarmPage.kt').read_text()
        ring=(JAVA/'alarm/WeatherAlarmRingActivity.kt').read_text()
        audio=(JAVA/'alarm/WeatherAlarmAudioService.kt').read_text()
        for required in ['AlarmManager','WeatherAlarmStore','getSharedPreferences','scheduleSnooze','canScheduleExactAlarms','setAlarmClock','setAndAllowWhileIdle']:
            self.assertIn(required,alarm)
        design=(JAVA/'alarm/R10AlarmDesign.kt').read_text()
        for required in ['R10AlarmEditor','R10AlarmTones','IOSClockDrums(','R10Switch','onSave','onDelete']:
            self.assertIn(required,design)
        self.assertNotIn('AndroidView(',design)
        self.assertNotIn('FilterChip',design)
        for required in ['稍後提醒','停止鬧鐘','WeatherAlarmAudioService']:
            self.assertIn(required,ring)
        for required in ['TextToSpeech','WeatherAlarmSpeechCache.load','WeatherAlarmNarration.build','USAGE_ALARM','startForeground']:
            self.assertIn(required,audio)
    def test_manifest_routes_exist(self):
        xml=(ROOT/'app/src/main/AndroidManifest.xml')
        ET.parse(xml)
        body=xml.read_text()
        self.assertIn('.alarm.WeatherAlarmReceiver',body)
        self.assertIn('.alarm.WeatherAlarmRingActivity',body)
        self.assertIn('SCHEDULE_EXACT_ALARM',body)
    def test_home_goals_and_back_handling(self):
        home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('BackHandler(enabled=',home)
        self.assertIn('WeatherAlarmPage(onClose=',home)
        self.assertIn('TyphoonCenterPage(',home)
        self.assertIn('ThinnAtmosphereFocus(',home)
        self.assertNotIn('2D 地標',home)
        self.assertNotIn('RadarThreeHourCard(snapshot)',home)
        # Keep the official typhoon route, but remove the broad RSS home summary.
        self.assertIn('val typhoonNotices = state.cwaWarnings.filter',home)
        self.assertLess(home.index('val typhoonNotices = state.cwaWarnings.filter'),home.index('HomeKeyWeatherCard(snapshot, state.stale)'))
        self.assertNotIn('state.cwaWarnings.isNotEmpty()',home)
        self.assertNotIn('Text("下一個地標  →"',home)
    def test_seismic_country_islands_and_safe_area(self):
        geo=(JAVA/'earthquake/SeismicGeo.kt').read_text()
        screen=(JAVA/'earthquake/SeismicLiveMapPage.kt').read_text()
        overlay=(JAVA/'earthquake/SeismicWarningOverlay.kt').read_text()
        for island in ['蘭嶼','綠島','龜山島','小琉球','釣魚臺列嶼']:
            self.assertIn(island,geo)
        self.assertIn('statusBarsPadding()',screen)
        self.assertIn('SeismicWarningOverlay(',screen)
        self.assertIn('趴下 · 掩護 · 穩住',overlay)
        self.assertNotIn('震波到達倒數：缺少可驗證資料',overlay)
        self.assertIn('歷史地震演練',overlay)
    def test_cwa_and_firebase_dont_claim_false_success(self):
        worker=(JAVA/'alerts/CwaAlertWorker.kt').read_text()
        push=(JAVA/'push/SeismicPushSettingsPage.kt').read_text()
        self.assertNotIn('if (!cityName.startsWith("台灣")',worker)
        self.assertIn('TaiwanLandmarks.counties.any',worker)
        self.assertIn('FCM 公開專案設定不足，無法訂閱主題',push)
        self.assertIn('訂閱成功不等於後端已部署',push)
    def test_release_notes_disclose_remaining_work(self):
        notes=(ROOT/'app/src/main/assets/current_release_notes.md').read_text()
        for term in ['實機驗證','桌面程式管理','Q 版','降雨雷達','鬧鐘']:
            self.assertIn(term,notes)
if __name__=='__main__': unittest.main()
