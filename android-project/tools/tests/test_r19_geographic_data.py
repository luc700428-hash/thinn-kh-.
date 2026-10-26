"""R19: official map WMS projection and no invented storm probabilities."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/"app/src/main/java/io/atmos/weather"

def code(p): return (ROOT/p).read_text(encoding="utf-8")

class R19GeographicDataTests(unittest.TestCase):
    def test_official_vector_atlas_used_by_seismic_and_both_typhoon_maps(self):
        terrain=code("maps/NlscTerrain.kt")
        self.assertIn("https://wms.nlsc.gov.tw/wms",terrain)
        self.assertIn("EMAP5",terrain)
        self.assertIn("VERSION=1.1.1",terrain)
        for p in ("typhoon/TyphoonCenterPage.kt","typhoon/StormTimelineOverlay.kt",
                  "earthquake/SeismicLiveMapPage.kt"):
            self.assertIn("rememberTaiwanVectorAtlas()",code(p))
            self.assertIn("atlas.draw(this)",code(p))
        radar=(ROOT.parents[5]/"src/main/assets/radar/history.html").read_text(encoding="utf-8")
        self.assertIn("wmts.nlsc.gov.tw/wmts/EMAP5",radar)
        self.assertIn("rasterBounds()",code("typhoon/TyphoonViewport.kt"))

    def test_official_probability_is_parsed_not_fake_numbers(self):
        prob=code("typhoon/CwaWindProbability.kt")
        self.assertIn("W-C0034-003",prob)
        self.assertIn("ZipInputStream",prob)
        self.assertIn("XmlPullParser",prob)
        self.assertIn("polygonContains",prob)
        self.assertIn("holes.none",prob)
        home=code("presentation/home/WeatherHomeScreen.kt")
        detail=code("typhoon/TyphoonCenterPage.kt")
        self.assertIn("homeWindProbability",home)
        self.assertIn("七級風暴風圈侵襲機率",detail)
        self.assertNotIn("目前侵台機率：68%",home)
        self.assertIn('officialProbability?.at(lon,lat) ?: "未提供"',detail)
        # Check source-bound official probability and safe missing/stale states,
        # not a fragile exact wording of the public-facing message.
        self.assertIn('officialProbability?.at(lon,lat) ?: "未提供"', detail)
        self.assertIn('probabilityMessage', detail)
        self.assertIn('CwaWindProbability.FetchState.STALE', detail)
        self.assertIn('CwaWindProbability.FetchState.NO_USABLE_REGIONS', detail)
        self.assertIn('不代表 0%', detail)

if __name__=="__main__": unittest.main()
