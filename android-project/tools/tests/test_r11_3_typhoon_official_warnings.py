from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class OfficialTyphoonWarningTests(unittest.TestCase):
    def test_no_model_based_official_warning(self):
        s=(ROOT/'typhoon/TyphoonOfficialWarning.kt').read_text()
        self.assertIn('title.contains("海上颱風警報")',s)
        self.assertIn('title.contains("陸上颱風警報")',s)
        self.assertIn('age>6*3600',s)
        self.assertIn('scope(body,"LAND")',s)
        self.assertNotIn('GDACS',s.replace('GDACS events can NEVER trigger it',''))
    def test_worker_has_national_sea_warning_feed(self):
        s=(ROOT/'alerts/CwaAlertWorker.kt').read_text()
        self.assertIn('fetchNational()',s)
        self.assertIn('TyphoonWarningStore.shouldNotify',s)
        self.assertIn('WeatherNotificationDispatcher.post',s)
    def test_weather_has_location_qualified_warning_copy(self):
        s=(ROOT/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('landHere',s)
        self.assertIn('已列入陸上警戒',s)
        self.assertIn('regions.chunked(3)',s)
        self.assertIn('中央氣象署已發布海上颱風警報',s)
if __name__=='__main__': unittest.main()
