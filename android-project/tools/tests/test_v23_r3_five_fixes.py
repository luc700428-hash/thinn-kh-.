"""Source-level regressions; these do NOT replace Android compilation or phone tests."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'app/src/main/java/io/atmos/weather'

def source(path): return (BASE/path).read_text(encoding='utf-8')

class R3FiveUserReports(unittest.TestCase):
    def test_typhoon_home_hidden_when_inactive(self):
        s=source('presentation/home/WeatherHomeScreen.kt')
        self.assertIn('if (officialStormActive || verifiedTyphoonWarning!=null)',s)
        self.assertIn('approaching != null',s)
        self.assertIn('CwaOfficialGateway.read(context,"W-C0034-005")',s)
        self.assertIn('delay(20*60_000L)',s)

    def test_typhoon_stale_positions_not_active(self):
        s=source('typhoon/TyphoonVisibility.kt')
        self.assertIn('fix.stormId.isNotBlank()',s)
        self.assertIn('-48L*3600L..36L*3600L',s)

    def test_landmark_only_opt_in_native_renderer(self):
        s=source('presentation/home/WeatherHomeScreen.kt')
        self.assertNotIn('FilamentLandmark(',s)
        self.assertNotIn('LandmarkGalleryPage(',s)
        self.assertIn('reducedMotion=reduceCityMotion',s)
        self.assertIn('beyondViewportPageCount = 0',s)

    def test_crash_diagnostics_from_weather_screen(self):
        self.assertIn('CrashDiagnostics.install(this)',source('MainActivity.kt'))
        self.assertNotIn('CrashDiagnostics.last(context)',source('presentation/home/WeatherHomeScreen.kt'))
        self.assertIn('CrashDiagnostics.last(ctx)',source('presentation/home/SettingsDiagnosticsPage.kt'))
        self.assertIn('複製崩潰紀錄',source('presentation/home/SettingsDiagnosticsPage.kt'))

    def test_detection_toggles_not_forced_disabled(self):
        s=source('earthquake/SeismicSettings.kt')
        for k in ('DETECT_WEAK','DETECT_MEDIUM','DETECT_STRONG','GAL_MEDIUM','GAL_STRONG'):
            self.assertIn('AlertRow(scenario)',s)
            self.assertNotIn('false),\n        '+k,s)
        self.assertNotIn('enabled=s.available,onCheckedChange',s)

    def test_station_alert_is_opt_in_but_really_connected(self):
        s=source('earthquake/ExpTechSeismicService.kt')
        self.assertIn('maybeNotifyStation(current,time)',s)
        self.assertIn('StationFreshness.state(it,time)==StationViewState.LIVE',s)
        self.assertIn('stationGate.observe(',s)
        gate=source('earthquake/StationDetectionGate.kt')
        self.assertIn('streak<3',gate)
        self.assertIn('120_000L',gate)

    def test_historical_replay_speaks_only_first_frame(self):
        s=source('earthquake/SeismicLiveMapPage.kt')
        self.assertIn('if(!replaySpoken && SeismicSpeech.enabled',s)
        self.assertIn('SeismicSpeech.speak(ctx,SeismicSpeech.replay(report,incident))',s)
        self.assertIn('重播語音',s)
        self.assertIn('這不是現在發生的地震',source('earthquake/SeismicSpeech.kt'))

    def test_existing_float_map_fix_kept(self):
        s=source('earthquake/SeismicLiveMapPage.kt')
        self.assertIn('size.width.toFloat(),size.height.toFloat()',s)

    def test_cwa_secret_not_in_app_code(self):
        import re
        for file in BASE.rglob('*.kt'):
            self.assertIsNone(re.search(r'CWA-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}',file.read_text()),str(file))

if __name__ == '__main__': unittest.main()
