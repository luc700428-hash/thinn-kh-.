"""Regression checks: block the old 'new release from pinned V22 ZIP' failure."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / '.github/workflows/build-apk.yml'
PROJECT = ROOT / 'android-project'


class RealSourceOtaTests(unittest.TestCase):
    def test_workflow_uses_tracked_android_source(self):
        workflow = WORKFLOW.read_text(encoding='utf-8')
        self.assertIn('project=android-project', workflow)
        self.assertIn('gradle "${args[@]}" :app:testDebugUnitTest', workflow)
        self.assertNotIn('source_archive=', workflow)
        self.assertNotIn('thinn-khi_Android_Source_V22_0_GradleDSL_Fixed.zip', workflow)

    def test_apk_embeds_commit_and_content_hash(self):
        workflow = WORKFLOW.read_text(encoding='utf-8')
        self.assertIn('build_source_proof.json', workflow)
        self.assertIn('app_tree_sha256', workflow)
        self.assertIn('cmp expected-build-source-proof.json embedded-build-source-proof.json', workflow)
        self.assertIn('gh release create "v$version"', workflow)
        self.assertIn('Refusing duplicate or overwrite.', workflow)
        self.assertIn('release_title="thinn-khì $version — 正式 OTA 更新"', workflow)

    def test_update_page_displays_real_build_identity(self):
        gradle = (PROJECT / 'app/build.gradle.kts').read_text(encoding='utf-8')
        page = (PROJECT / 'app/src/main/java/io/atmos/weather/update/AppUpdatePage.kt').read_text(encoding='utf-8')
        self.assertIn('buildConfigField("String", "THINN_BUILD_SHA"', gradle)
        self.assertIn('BuildConfig.THINN_BUILD_SHA.take(10)', page)

    def test_release_asset_has_public_manifest_and_proof(self):
        workflow = WORKFLOW.read_text(encoding='utf-8')
        self.assertIn('thinn-khi.apk thinn-khi.apk.sha256 ota.json embedded-build-source-proof.json', workflow)
        source = (PROJECT / 'app/src/main/java/io/atmos/weather/update/AppUpdater.kt').read_text(encoding='utf-8')
        self.assertIn('releases/latest/download/ota.json', source)


if __name__ == '__main__':
    unittest.main()
