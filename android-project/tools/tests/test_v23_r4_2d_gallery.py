"""User requested removal of all 2D landmark gallery images and entry points."""
import json,re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets'
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
LAND=ASSETS/'landmarks/catalog_v2.json'
class No2DLandmarkGallery(unittest.TestCase):
 @classmethod
 def setUpClass(cls): cls.places=json.loads(LAND.read_text())['landmarks']
 def test_location_data_still_available(self):
  self.assertEqual(len(self.places),132)
  self.assertEqual(sum(x['country']=='TW' for x in self.places),124)
  self.assertEqual(sum(x['country']=='INTL' for x in self.places),8)
 def test_no_2d_gallery_assets(self):
  for folder in ('illustrations','previews','individual/previews'):
   self.assertFalse((ASSETS/'landmarks'/folder).exists())
  self.assertTrue(all(not p.get('illustration') for p in self.places))
 def test_gallery_removed_from_ui(self):
  src=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
  for obsolete in ('landmarkGalleryOpen','LandmarkGalleryPage(','LandmarkCanvas(','2D 地標'):
   self.assertNotIn(obsolete,src)
  self.assertFalse((JAVA/'presentation/home/LandmarkGalleryPage.kt').exists())
  self.assertIn('ThinnAtmosphereFocus(',src)
 def test_3d_assets_preserved(self):
  self.assertTrue((ASSETS/'landmarks/individual/chiayi_fountain.glb').exists())
 def test_aerosol_evidence_bound(self):
  src=(JAVA/'domain/model/WeatherAdviceEngine.kt').read_text()
  self.assertNotIn('"空氣中氣膠／沙塵偏多"',src)
  self.assertIn('氣膠光學厚度',src)
 def test_no_client_cwa_secrets(self):
  for p in JAVA.rglob('*.kt'):
   self.assertIsNone(re.search(r'CWA-[A-F0-9]{8}-[A-F0-9]{4}-',p.read_text()),str(p))
if __name__=='__main__':unittest.main()
