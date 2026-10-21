import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2] / 'app' / 'src' / 'main' / 'java' / 'io' / 'atmos' / 'weather'

class V23QualityRegressionTests(unittest.TestCase):
    def test_alarm_picker_cannot_be_obscured_by_editor(self):
        src = (ROOT / 'alarm' / 'WeatherAlarmPage.kt').read_text()
        new=(ROOT/'alarm/R10AlarmDesign.kt').read_text()
        self.assertIn('R10AlarmEditor(',src)
        self.assertIn('R10RingtonePicker(',new)
        self.assertNotIn('AndroidView(',new)
        self.assertNotIn('AlertDialog',new)
        self.assertIn('Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT',src)
        self.assertIn('ActivityResultContracts.RequestPermission()',src)
    def test_typhoon_does_not_connect_unverified_points(self):
        src = (ROOT / 'typhoon' / 'VerifiedTrackPlanner.kt').read_text()
        self.assertIn('it.forecast && it.stormId.isNotBlank()',src)
        self.assertIn('speed>140.0',src)
        page = (ROOT / 'typhoon' / 'TyphoonCenterPage.kt').read_text()
        self.assertIn('VerifiedTrackPlanner.verifiedForecastSegments',page)
    def test_3d_model_renderer_not_bundled(self):
        self.assertFalse((ROOT / 'visual' / 'FilamentLandmark.kt').exists())
    def test_cwa_credential_is_only_server_side(self):
        src = (ROOT / 'earthquake' / 'CwaOfficialGateway.kt').read_text()
        self.assertNotIn('CWA-'+'5B5332',src)
        self.assertIn('getHttpsCallable("readOfficialCwa")',src)

if __name__ == '__main__':
    unittest.main()
