"""Source-level gates; NOT a claim that an APK was compiled or shipped."""
import pathlib, unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
HOME=(ROOT/'app/src/main/java/io/atmos/weather/presentation/home/WeatherHomeScreen.kt')
WORKFLOW=ROOT.parent/'.github/workflows/build-apk.yml'
class ReleaseContractTests(unittest.TestCase):
    def test_removed_rss_home_summary_does_not_disable_official_alert_worker(self):
        src=HOME.read_text()
        self.assertNotIn('state.cwaWarnings.isNotEmpty()',src)
        self.assertIn('val typhoonNotices = state.cwaWarnings.filter',src)
        self.assertIn('HomeKeyWeatherCard(snapshot, state.stale)',src)
        worker=(ROOT/'app/src/main/java/io/atmos/weather/alerts/CwaAlertWorker.kt').read_text()
        self.assertIn('TaiwanLandmarks.counties.any',worker)
        self.assertNotIn('if (!cityName.startsWith("台灣")',worker)
    def test_key_weather_still_in_main_page(self):
        src=HOME.read_text()
        self.assertIn('HomeKeyWeatherCard(snapshot, state.stale)',src)
        self.assertIn('現在最重要的天氣',src)
        self.assertIn('HomeAdviceRow("穿著建議"',src)
        self.assertIn('HomeAdviceRow("雨具建議"',src)
        self.assertIn('模型推估，非環境部官方 AQI',src)
    def test_workflow_builds_tracked_source_not_old_pinned_archive(self):
        src=WORKFLOW.read_text()
        self.assertIn('project=android-project',src)
        self.assertIn('gradle "${args[@]}"',src)
        self.assertNotIn('source_archive=',src)
if __name__=="__main__":unittest.main()
