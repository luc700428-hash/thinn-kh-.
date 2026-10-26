"""V25 source regression checks; does not certify real devices or official data availability."""
import unittest
from pathlib import Path
SRC=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class V25MapSearchQuality(unittest.TestCase):
    def test_uses_user_readable_probability_states(self):
        s=(SRC/'typhoon/CwaWindProbability.kt').read_text()
        for token in ('FetchState.CURRENT','FetchState.STALE','FetchState.UNAVAILABLE','FetchState.NO_USABLE_REGIONS'):
            self.assertIn(token,s)
        self.assertIn('polygonContains',s)
        self.assertNotIn('目前侵台機率：68%',s)
    def test_map_pinch_zoom_and_verified_point_types(self):
        s=(SRC/'typhoon/TyphoonCenterPage.kt').read_text()
        for token in ('detectTransformGestures','mapZoom','mapPan','● 實測位置','┄ 官方預報 →','VerifiedTrackPlanner.verifiedForecastSegments'):
            self.assertIn(token,s)
        self.assertIn('CwaOfficialGateway.read(context,"W-C0034-005")',s)
    def test_saved_places_stay_searchable_without_api(self):
        s=(SRC/'presentation/home/WeatherViewModel.kt').read_text()
        self.assertIn('SampleCities.all+mutableState.value.savedCities',s)
        self.assertIn('TaiwanPlaceSearch.matches(name,it.name)',s)
        self.assertIn('distinctBy { it.id }',s)
    def test_no_auto_typhoon_forecast_modal(self):
        s=(SRC/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertNotIn('nextOfficialStormPrompt',s)
if __name__=='__main__':unittest.main()
