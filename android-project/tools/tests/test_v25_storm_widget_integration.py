"""V25 interaction contracts. These source checks do not replace Android tests."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class V25StormWidgetIntegration(unittest.TestCase):
    def test_google_data_private_persistent_and_consent(self):
        src=(ROOT/'typhoon/TyphoonCenterPage.kt').read_text()
        for token in ('openFileOutput("weatherlab_last.csv"',
                      'openFileInput("weatherlab_last.csv")',
                      'sameStormConfirmed', 'V25StormComparison.compare(',
                      'deleteFile("weatherlab_last.csv")'):
            self.assertIn(token,src)
    def test_no_fabricated_track_or_fake_model_certainty(self):
        src=(ROOT/'typhoon/V25StormComparison.kt').read_text()
        self.assertIn('maxMinutes: Long = 90L',src)
        self.assertIn('Duration.between',src)
        self.assertIn('kilometers(',src)
    def test_original_weather_character_fifteen_state_fallback(self):
        widget=(ROOT/'widgets/AtmosWidgets.kt').read_text()
        self.assertIn('data.condition',widget)
        self.assertIn('IslandIconClassifier.choose(',widget)
        self.assertIn('fontSize=(size*scale).sp',widget)
        self.assertIn('data.age',widget)
if __name__=='__main__': unittest.main()
