"""V25: both legacy 2D gallery and 3D landmark catalog have been opted out."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
ASSETS=ROOT/'app/src/main/assets'
class NoLandmarkGallery(unittest.TestCase):
 def test_models_and_gallery_absent(self):
  self.assertFalse((ASSETS/'landmarks').exists())
  self.assertFalse((JAVA/'visual/FilamentLandmark.kt').exists())
  self.assertFalse((JAVA/'domain/visual/LandmarkRegistry.kt').exists())
  self.assertFalse((JAVA/'presentation/home/LandmarkGalleryPage.kt').exists())
 def test_app_keeps_real_weather_and_location(self):
  self.assertTrue((JAVA/'ui/ThinnAtmosphereFocus.kt').exists())
  self.assertTrue((JAVA/'domain/visual/TaiwanLandmarks.kt').exists())
  self.assertIn('ThinnAtmosphereFocus(', (JAVA/'presentation/home/WeatherHomeScreen.kt').read_text())
 def test_no_client_cwa_secrets(self):
  import re
  for p in JAVA.rglob('*.kt'):
   self.assertIsNone(re.search(r'CWA-[A-F0-9]{8}-[A-F0-9]{4}-',p.read_text()),str(p))
if __name__=='__main__':unittest.main()
