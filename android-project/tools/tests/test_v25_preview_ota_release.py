"""Preview OTA must never masquerade as official complete V25."""
import json
import pathlib
import tempfile
import unittest
import importlib.util

ROOT=pathlib.Path(__file__).resolve().parents[2]
SRC=ROOT/'app/src/main/java/io/atmos/weather'
WORKFLOW=ROOT.parent/'.github/workflows/build-apk.yml'

class PreviewOtaRegression(unittest.TestCase):
    def test_preview_and_official_channels_are_distinct(self):
        updater=(SRC/'update/AppUpdater.kt').read_text(encoding='utf-8')
        gradle=(ROOT/'app/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('releases/latest/download/ota.json', updater)
        self.assertIn('releases/download/v25-preview/ota.json', updater)
        self.assertIn('BuildConfig.THINN_OTA_CHANNEL == "preview"', updater)
        self.assertIn('"v25-preview" else "v$version"', updater)
        self.assertIn('"THINN_OTA_CHANNEL"', gradle)
        self.assertIn('OTA APK 來源不受信任', updater)
        self.assertIn('MessageDigest.getInstance("SHA-256")', updater)
        self.assertIn('currentCerts != updateCerts', updater)
    def test_manual_release_is_official_only(self):
        workflow=WORKFLOW.read_text(encoding='utf-8')
        self.assertIn("if: github.event_name == 'workflow_dispatch' && env.SIGN_READY == 'true'", workflow)
        self.assertNotIn('Publish V25 test OTA (pre-release only)', workflow)
        self.assertNotIn('inputs.publish_release == true', workflow)
        self.assertIn('gh release create "v$version"', workflow)
        self.assertIn('--latest', workflow)
        self.assertIn('cmp expected-build-source-proof.json embedded-build-source-proof.json', workflow)
    def test_preview_manifest_keeps_hash_and_public_url(self):
        spec=importlib.util.spec_from_file_location('ota',ROOT/'tools/create_ota_manifest.py')
        mod=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        with tempfile.TemporaryDirectory() as d:
            apk=pathlib.Path(d)/'signed.apk'
            notes=pathlib.Path(d)/'notes.md'
            apk.write_bytes(b'fixture-signed-apk-for-url-only-tests')
            notes.write_text('preview',encoding='utf-8')
            preview=mod.make_manifest(apk,notes,'25.0.300',25000300,preview=True)
            official=mod.make_manifest(apk,notes,'25.0.300',25000300)
            self.assertTrue(preview['apk_url'].endswith('/v25-preview/thinn-khi.apk'))
            self.assertTrue(official['apk_url'].endswith('/v25.0.300/thinn-khi.apk'))
            self.assertEqual(preview['sha256'],official['sha256'])
            self.assertEqual(preview['package_name'],'io.atmos.weather')
    def test_full_v25_gate_stays_closed(self):
        data=json.loads((ROOT/'tools/next_release_gate.json').read_text(encoding='utf-8'))
        self.assertEqual(len(data['requirements']),22)
        self.assertIsNot(data.get('verified_full_release'),True)

if __name__=='__main__':unittest.main()
