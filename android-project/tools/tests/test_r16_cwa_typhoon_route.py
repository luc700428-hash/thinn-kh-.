import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class TyphoonRouteRegression(unittest.TestCase):
    def test_real_cwa_005_and_forecast_hour(self):
        home=(R/'presentation/home/WeatherHomeScreen.kt').read_text()
        parser=(R/'typhoon/CwaCycloneForecast.kt').read_text()
        timeline=(R/'typhoon/OfficialStormTimeline.kt').read_text()
        self.assertIn('CwaOfficialGateway.read(context,"W-C0034-005")',home)
        for s in ('CoordinateLatitude','CoordinateLongitude','AnalysisData','ForecastData',
                  'InitialTime','ForecastHour','initial.plusSeconds','CwaTdNo'):
            self.assertIn(s,parser)
        self.assertIn('CwaTyphoonName',timeline)
        self.assertIn('TaiwanStormRelevance.isMovingTowardTaiwan',home)
    def test_tile_not_permanent_and_no_unverified_gdacs_as_taiwan_warning(self):
        home=(R/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('if (officialStormActive || verifiedTyphoonWarning!=null)',home)
        self.assertIn('approaching != null',home)
        self.assertNotIn('gdacsCyclones.isNotEmpty() || typhoonPrimaryUnavailable || typhoonBackupUnavailable',home)
        self.assertIn('verifiedTyphoonWarning?.let { official ->',home)
        self.assertIn('GdacsCycloneFeed.read()',home)
