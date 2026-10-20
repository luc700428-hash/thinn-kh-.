"""V25 version and UX regression test; not an actual device acceptance certificate."""
import importlib.util
import json
from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[2]
A=R/'app/src/main/java/io/atmos/weather'
class V25Release(unittest.TestCase):
    def test_version_25_and_safety(self):
        y=(R.parent/'.github/workflows/build-apk.yml').read_text()
        for text in ('25.0.$GITHUB_RUN_NUMBER','25000000 + GITHUB_RUN_NUMBER',
                     'verify_v25_release_evidence.py','verify_next_release_gate.py',
                     'Official APK signature differs','refusing version-only OTA'):
            self.assertIn(text,y)
        self.assertIn('?: "25.0.0"',(R/'app/build.gradle.kts').read_text())
    def test_not_verified_without_device_proof(self):
        s=importlib.util.spec_from_file_location('v25_gate',R/'tools/verify_v25_release_evidence.py')
        g=importlib.util.module_from_spec(s);s.loader.exec_module(g)
        manifest=json.loads((R/'tools/next_release_gate.json').read_text())
        self.assertEqual(len(manifest['requirements']),22)
        self.assertTrue(g.missing_proof(manifest))
        self.assertTrue(all(item['status']!='verified' for item in manifest['requirements']))
    def test_typhoon_readability_and_no_auto_dialog(self):
        h=(A/'presentation/home/WeatherHomeScreen.kt').read_text()
        self.assertNotIn('nextOfficialStormPrompt',h)
        self.assertIn('TyphoonDirection.zh(it)',h)
        self.assertIn('notice.summary.ifBlank',h)
        self.assertIn('LaunchedEffect(query)',h)
    def test_multiple_city_lookup_variants(self):
        s=(A/'presentation/home/WeatherViewModel.kt').read_text()
        self.assertIn('name.removeSuffix("鄉")',s)
        self.assertIn('distinctBy { it.id }',s)
if __name__=='__main__':unittest.main()
