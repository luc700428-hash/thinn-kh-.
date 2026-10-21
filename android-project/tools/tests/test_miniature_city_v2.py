"""V25: verify real Taiwan geography and weather-only visuals after 3D removal."""
import re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'app/src/main/java/io/atmos/weather'
class V25NoLandmarkTests(unittest.TestCase):
 def test_3d_assets_source_and_dependency_absent(self):
  self.assertFalse((ROOT/'app/src/main/assets/landmarks').exists())
  self.assertFalse((JAVA/'visual/FilamentLandmark.kt').exists())
  self.assertFalse((JAVA/'domain/visual/Landmark3dCatalog.kt').exists())
  self.assertNotIn('filament-utils-android',(ROOT/'app/build.gradle.kts').read_text())
 def test_county_lookups_retained_for_alerts(self):
  self.assertRegex((JAVA/'domain/visual/TaiwanLandmarks.kt').read_text(),r'val\s+counties\s*=')
  self.assertIn('TaiwanLandmarks.counties.any',(JAVA/'alerts/CwaAlertWorker.kt').read_text())
 def test_scene_is_weather_only(self):
  self.assertNotIn('LandmarkId',(JAVA/'domain/visual/WeatherSceneMapper.kt').read_text())
  self.assertNotIn('LandmarkId',(JAVA/'visual/WeatherArtRenderer.kt').read_text())
  self.assertIn('drawScene(', (JAVA/'visual/WeatherArtRenderer.kt').read_text())
 def test_ui_reduced_motion_and_accessibility(self):
  home=(JAVA/'presentation/home/WeatherHomeScreen.kt').read_text()
  light=(JAVA/'ui/ThinnAtmosphereFocus.kt').read_text()
  self.assertIn('reducedMotion=reduceCityMotion',home)
  self.assertIn('if (reducedMotion) phase.snapTo',light)
  self.assertIn('contentDescription =',light)
 def test_no_under14sp_text(self):
  for path in JAVA.rglob('*.kt'):
   text=path.read_text()
   for m in re.finditer(r'fontSize\s*=\s*(\d+)\.sp',text):
    self.assertGreaterEqual(int(m.group(1)),14,str(path)+':'+str(m.start()))
 def test_workflow_runs_real_build(self):
  wf=(ROOT.parent/'.github/workflows/build-apk.yml').read_text()
  self.assertIn('project=android-project',wf)
  self.assertIn(':app:testDebugUnitTest',wf)
if __name__=='__main__':unittest.main()
