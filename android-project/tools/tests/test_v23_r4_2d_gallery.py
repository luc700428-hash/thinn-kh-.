"""Regression tests for the 2D-first landmark experience. Not an Android compilation test."""
import json,re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets'
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
LAND=ASSETS/'landmarks/catalog_v2.json'
class LandmarkGallery2D(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.catalog=json.loads(LAND.read_text())
  cls.places=cls.catalog['landmarks']
 def test_count(self):
  self.assertEqual(len(self.places),132)
  self.assertEqual(sum(x['country']=='TW' for x in self.places),124)
  self.assertEqual(sum(x['country']=='INTL' for x in self.places),8)
 def test_all_have_unique_local_2d_images(self):
  images=[x['illustration'] for x in self.places]
  self.assertEqual(len(images),len(set(images)))
  for x in self.places:
   p=ASSETS/x['illustration']
   self.assertTrue(p.is_file() and p.stat().st_size>1000,x['name'])
 def test_all_images_have_webp_container_headers(self):
  for x in self.places:
   data=(ASSETS/x['illustration']).read_bytes()
   self.assertEqual(data[:4], b'RIFF', x['name'])
   self.assertEqual(data[8:12], b'WEBP', x['name'])
   self.assertIn(data[12:16], (b'VP8 ',b'VP8L',b'VP8X'), x['name'])
 def test_gallery_is_navigable(self):
  s=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
  self.assertIn('landmarkGalleryOpen',s)
  self.assertIn('LandmarkGalleryPage(',s)
  self.assertIn('place.illustration',s)
  self.assertNotIn('開啟可旋轉3D',s)
  self.assertNotIn('FilamentLandmark(',s)
  page=(JAVA/'presentation/home/LandmarkGalleryPage.kt').read_text()
  self.assertIn('LazyColumn',page)
  self.assertIn('國外 (',page)
  self.assertIn('臺灣與離島 (',page)
 def test_catalog_parser_supports_artwork_on_unmodelled_places(self):
  s=(JAVA/'domain/visual/LandmarkRegistry.kt').read_text()
  self.assertIn('p.optString("illustration")',s)
  self.assertIn('it.hasIllustration || it.hasModel',s)
 def test_aerosol_not_misrepresented_as_dust_storm(self):
  s=(JAVA/'domain/model/WeatherAdviceEngine.kt').read_text()
  self.assertNotIn('"空氣中氣膠／沙塵偏多"',s)
  self.assertIn('氣膠光學厚度',s)
  self.assertIn('不等於正在發生沙塵暴',s)
 def test_credentials_not_packaged(self):
  for p in JAVA.rglob('*.kt'):
   self.assertIsNone(re.search(r'CWA-[A-F0-9]{8}-[A-F0-9]{4}-',p.read_text()),str(p))
if __name__=='__main__':unittest.main()
