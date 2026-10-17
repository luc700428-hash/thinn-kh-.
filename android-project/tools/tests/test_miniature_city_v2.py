"""Static and asset regression checks. Full Android device tests remain mandatory."""
import json
import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets/landmarks'
JAVA=ROOT/'app/src/main/java/io/atmos/weather'

class MiniatureCityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog=json.loads((ASSETS/'catalog_v2.json').read_text())
        cls.places=cls.catalog['landmarks']
    def test_22_counties_each_multiple_landmarks(self):
        tw={x['county'] for x in self.places if x['country']=='TW'}
        self.assertEqual(len(tw),22)
        for county in tw:
            self.assertGreaterEqual(sum(x['county']==county and x['country']=='TW' for x in self.places),3,county)
    def test_island_hierarchy_not_lost(self):
        islands={x['name']:x['county'] for x in self.catalog['island_regions']}
        for name,county in [('小琉球','屏東縣'),('蘭嶼','臺東縣'),('綠島','臺東縣'),('龜山島','宜蘭縣'),('東引','連江縣'),('七美','澎湖縣'),('烈嶼','金門縣')]:
            self.assertEqual(islands.get(name),county)
            self.assertTrue(any(x['island']==name for x in self.places),name)
    def test_17_individual_3d_models_retained_without_2d(self):
        modelled=[x for x in self.places if x['geometry_status']=='original_prototype']
        self.assertGreaterEqual(len(modelled),17)
        for x in modelled:
            asset=ROOT/'app/src/main/assets'/x['model']
            self.assertGreater(asset.stat().st_size,1000,x['name'])
            self.assertEqual(asset.read_bytes()[:4],b'glTF')
            self.assertIsNone(x.get('preview'))
    def test_missing_models_are_not_falsely_marked_finished(self):
        ids=[x['id'] for x in self.places]
        self.assertEqual(len(ids),len(set(ids)))
        for x in self.places:
            if x['geometry_status']=='not_modelled':
                self.assertIsNone(x['model'])
                self.assertIsNone(x['preview'])
    def test_live_weather_replaces_2d_landmarks_without_erasing_geo(self):
        home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertIn('ThinnAtmosphereFocus(',home)
        self.assertNotIn('LandmarkGalleryPage(',home)
        model=(JAVA/'domain/visual/LandmarkRegistry.kt').read_text()
        self.assertIn('LandmarkGeo.closestIsland',model)
    def test_all_explicit_literal_text_is_at_least_14sp(self):
        for path in JAVA.rglob('*.kt'):
            text=path.read_text()
            for m in re.finditer(r'fontSize\s*=\s*(\d+)\.sp',text):
                self.assertGreaterEqual(int(m.group(1)),14,str(path)+':'+str(m.start()))
    def test_atmosphere_reduced_motion_and_accessibility(self):
        home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
        light=(JAVA/'ui/ThinnAtmosphereFocus.kt').read_text()
        self.assertIn('reducedMotion=reduceCityMotion',home)
        self.assertIn('if (reducedMotion) phase.snapTo',light)
        self.assertIn('contentDescription =',light)
    def test_workflow_builds_tracked_source_not_old_zip(self):
        wf=(ROOT.parent/'.github/workflows/build-apk.yml').read_text()
        self.assertIn('project=android-project',wf)
        self.assertNotIn('source_archive=',wf)
        self.assertIn(':app:testDebugUnitTest',wf)

if __name__=='__main__':unittest.main()
