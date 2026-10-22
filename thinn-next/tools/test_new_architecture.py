#!/usr/bin/env python3
"""Checks for prototype boundaries and permanent safeguards. Not a device acceptance test."""
from pathlib import Path
import unittest
P=Path(__file__).resolve().parents[1]
SRC=P/'app/src/main/java/io/atmos/weather/next'
class NextFoundationInvariants(unittest.TestCase):
 def test_debug_cannot_override_old_app(self):
  s=(P/'app/build.gradle.kts').read_text()
  self.assertIn('applicationId = "io.atmos.weather"',s)
  self.assertIn('applicationIdSuffix = ".nextioslab"',s)
  self.assertIn('FOUNDATION_NOT_FOR_RELEASE',s)
  self.assertNotIn('signingConfig = signingConfigs.getByName("debug")',s)
 def test_no_material_ui_dependencies(self):
  s=(P/'app/build.gradle.kts').read_text()
  self.assertNotIn('material3:material3',s)
  self.assertNotIn('androidx.compose.material:material-',s)
  for p in SRC.rglob('*.kt'):
   self.assertNotIn('import androidx.compose.material',p.read_text(),p)
 def test_all_source_modules_present(self):
  for f in ['MainActivity.kt','design/NextVisualEngine.kt','domain/WeatherModels.kt',
            'domain/EventEngine.kt','domain/SystemSurfacePolicy.kt','data/OpenMeteoWeather.kt','platform/NextWeatherWidget.kt',
            'features/NextApp.kt']:
   self.assertTrue((SRC/f).is_file(),f)
 def test_unsafe_ota_and_fake_alerts_not_in_new_lab(self):
  s='\n'.join(x.read_text() for x in SRC.rglob('*.kt'))
  self.assertNotIn('latest/download/ota.json',s)
  self.assertIn('!event.verified',s)
  self.assertIn('上次資料 · 非即時',s)
 def test_cities_include_offshore(self):
  s=(SRC/'domain/WeatherModels.kt').read_text()
  self.assertIn('金門',s); self.assertIn('澎湖',s)
 def test_return_to_origin_navigation(self):
  s=(SRC/'features/NextApp.kt').read_text()
  self.assertIn('val navigationOrigins=remember',s)
  self.assertIn('navigationOrigins.removeAt(navigationOrigins.lastIndex)',s)
 def test_manifest_widget_and_https_source(self):
  x=(P/'app/src/main/AndroidManifest.xml').read_text()
  self.assertIn('NextWeatherWidget',x)
  self.assertIn('allowBackup="false"',x)
  self.assertIn('usesCleartextTraffic="false"',x)
  s=(SRC/'data/OpenMeteoWeather.kt').read_text()
  self.assertIn('https://api.open-meteo.com',s)
  self.assertIn('connection.disconnect()',s)
 def test_existing_data_is_not_accessed_by_lab(self):
  s='\n'.join(x.read_text() for x in SRC.rglob('*.kt'))
  for legacy in ('thinn_personal_tasks','thinn_push_preferences_v22','atmos_settings'):
   self.assertNotIn(legacy,s)
 def test_text_sizes_respect_14sp_accessibility_floor(self):
  import re
  for path in SRC.rglob('*.kt'):
   source=path.read_text()
   for match in re.finditer(r'size=(\d+)', source):
    self.assertGreaterEqual(int(match.group(1)),14,(path,match.group(0)))
 def test_no_3d_landmark_system(self):
  names=' '.join(str(p) for p in P.rglob('*'))
  self.assertNotIn('Filament',names)
  self.assertNotIn('LandmarkGallery',names)
if __name__=='__main__':unittest.main()
