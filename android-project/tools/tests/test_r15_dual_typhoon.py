"""R15 regression gate: no silent disappearance on CWA API outage; no false official warnings."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2] / 'app/src/main/java/io/atmos/weather'

def read(path):
    return (ROOT/path).read_text(encoding='utf-8')

class IntegratedTyphoonFallbackTests(unittest.TestCase):
    def test_two_independent_feeds(self):
        h = read('presentation/home/WeatherHomeScreen.kt')
        g = read('typhoon/GdacsCycloneFeed.kt')
        self.assertIn('CwaOfficialGateway.read(context,"W-C0034-002")',h)
        self.assertIn('GdacsCycloneFeed.read()',h)
        self.assertIn('https://www.gdacs.org/gdacsapi/api/events/geteventlist/MAP?eventtypes=TC',g)
        self.assertIn('https://www.cwa.gov.tw/V8/C/P/Typhoon/TY_WARN.html',read('typhoon/TyphoonCenterPage.kt'))
    def test_home_not_invisible_on_outage(self):
        h=read('presentation/home/WeatherHomeScreen.kt')
        self.assertIn('gdacsCyclones.isNotEmpty() || typhoonPrimaryUnavailable || typhoonBackupUnavailable',h)
        self.assertIn('typhoonPrimaryUnavailable=true',h)
        self.assertIn('typhoonBackupUnavailable=true',h)
        self.assertIn('氣象署資料連線異常',h)
        self.assertIn('GDACS 備援',h)
    def test_cwa_warnings_separate_from_gdacs(self):
        h=read('presentation/home/WeatherHomeScreen.kt')
        self.assertIn('verifiedTyphoonWarning?.let { official ->',h)
        self.assertIn('非臺灣官方預報',h)
        c=read('typhoon/TyphoonCenterPage.kt')
        self.assertIn('非臺灣中央氣象署預報或警報',c)
        self.assertIn('GDACS 熱帶氣旋資訊',c)
    def test_gdacs_not_a_fabricated_forecast(self):
        g=read('typhoon/GdacsCycloneFeed.kt')
        for token in ['val ageSec=', 'now.epochSecond-last.epochSecond','MAX_BYTES','HttpsURLConnection','if(!lat.isFinite() || !lon.isFinite()']:
            self.assertIn(token,g)
        c=read('typhoon/TyphoonCenterPage.kt')
        self.assertIn('不提供推測時間軸',c)
    def test_original_alert_and_island_unmodified(self):
        self.assertIn('正式警戒範圍以氣象署為準',read('presentation/home/WeatherHomeScreen.kt'))
        self.assertIn('OfficialTrackPlot(',read('typhoon/TyphoonCenterPage.kt'))

if __name__=='__main__': unittest.main()
