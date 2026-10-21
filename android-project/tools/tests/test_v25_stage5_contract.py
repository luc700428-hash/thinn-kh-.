"""Source safety contract, not a substitute for CI or hardware/network acceptance."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]  # Copied to android-project/tools/tests/
SRC=ROOT/'app/src/main/java/io/atmos/weather'
class Stage5CwaContracts(unittest.TestCase):
    def test_typed_cwa_field_compatibility(self):
        s=(SRC/'typhoon/CwaCycloneForecast.kt').read_text()
        for key in ('tropicalCyclones','tropicalCyclone','analysis_data','forecast_data',
                    'coordinate_latitude','coordinate_longitude','initial_time','forecast_hour',
                    'newestForecast','VerifiedTrackPlanner.verifiedForecastSegments'):
            self.assertIn(key,s)
        self.assertIn('CwaCycloneForecast.timelines(json)',(SRC/'typhoon/OfficialStormTimeline.kt').read_text())
    def test_kmz_credentials_not_embedded(self):
        s=(SRC/'typhoon/CwaWindProbability.kt').read_text()
        gateway=(SRC/'earthquake/CwaOfficialGateway.kt').read_text()
        backend=(ROOT/'backend/functions/index.js').read_text()
        self.assertNotIn('rdec-key-',s)
        self.assertNotIn('Authorization=',s)
        self.assertIn('readTyphoonProbabilityKmz(context)',s)
        self.assertIn('getHttpsCallable("readOfficialCwaKmz")',gateway)
        self.assertIn('exports.readOfficialCwaKmz',backend)
        self.assertIn('secrets:[cwaSecret]',backend)
    def test_no_fabricated_forecast_and_no_original_release_touch(self):
        s=(SRC/'typhoon/TyphoonCenterPage.kt').read_text()
        self.assertIn('缺少可核對的連續預報',s)
        self.assertIn('WeatherLabLocalCsv.parse',s)
        self.assertTrue((ROOT/'tools/next_release_gate.json').exists())
if __name__=='__main__':unittest.main()
