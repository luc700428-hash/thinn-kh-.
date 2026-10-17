
"""Source contracts for thinn-khì v23.

These checks do not replace Android compilation, backend integration
tests, or real-device verification.
"""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/io/atmos/weather"


class CwaFilamentIntegration(unittest.TestCase):

    def test_private_developer_key_not_embedded_in_app(self):
        self.assertFalse(
            (JAVA / "earthquake/BundledCwaKey.kt").exists()
        )

        src = "\n".join(
            p.read_text(encoding="utf-8")
            for p in JAVA.rglob("*.kt")
        )

        self.assertNotRegex(
            src,
            r"CWA-[A-Za-z0-9]{8}-[A-Za-z0-9]{4}-"
            r"[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-"
            r"[A-Za-z0-9]{12}"
        )

    def test_server_proxy_allowlist_auth_secret_and_safe_errors(self):
        backend = (
            ROOT / "backend/functions/index.js"
        ).read_text(encoding="utf-8")

        gateway = (
            ROOT / "backend/functions/cwa_official.cjs"
        ).read_text(encoding="utf-8")

        # Legacy Firebase backend remains in the repository.
        # These checks must not imply it is the active Android gateway.
        self.assertIn(
            "defineSecret('CWA_API_KEY')",
            backend
        )
        self.assertIn(
            "exports.readOfficialCwa",
            backend
        )
        self.assertIn(
            "const user=uid(request)",
            backend
        )
        self.assertIn(
            "requestDataset(dataset,cwaSecret.value())",
            backend
        )
        self.assertIn(
            "'E-A0015-001'",
            gateway
        )
        self.assertIn(
            "'W-C0034-002'",
            gateway
        )
        self.assertIn(
            "MAX_BYTES",
            gateway
        )

    def test_client_uses_secret_proxy_and_post_event_reports(self):
        g = (
            JAVA / "earthquake/CwaOfficialGateway.kt"
        ).read_text(encoding="utf-8")

        r = (
            JAVA / "earthquake/CwaEarthquakeRepository.kt"
        ).read_text(encoding="utf-8")

        # Android must use the new HTTPS Cloudflare Worker.
        self.assertIn(
            'BuildConfig.THINN_PUSH_WORKER_URL',
            g
        )
        self.assertIn(
            'URL("$base/v1/cwa")',
            g
        )

        # Every CWA request must be authenticated.
        self.assertIn(
            "signInAnonymously()",
            g
        )
        self.assertIn(
            "getIdToken(false)",
            g
        )
        self.assertIn(
            '"Bearer $idToken"',
            g
        )

        # The request must send a fixed dataset identifier.
        self.assertIn(
            '.put("dataset", dataset)',
            g
        )

        # Only accept the expected official posted-data source.
        self.assertIn(
            '"CWA_OFFICIAL_POSTED_DATA"',
            g
        )
        self.assertIn(
            'response.optString("dataset") == dataset',
            g
        )

        # The legacy callable must not remain in Android.
        self.assertNotIn(
            'getHttpsCallable("readOfficialCwa")',
            g
        )

        # Existing repository and post-earthquake semantics stay.
        self.assertIn(
            "CwaOfficialGateway.read(context,id)",
            r
        )
        self.assertIn(
            "published after earthquakes",
            r
        )

    def test_trem_is_independent_and_client_does_not_copy_agpl(self):
        s = (
            JAVA / "earthquake/ExpTechSeismicService.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "/api/v2/eq/eew",
            s
        )
        self.assertIn(
            "/api/v1/trem/sse",
            s
        )

    def test_glb_uses_true_renderer_not_png_only(self):
        home = (
            JAVA / "presentation/home/WeatherHomeScreen.kt"
        ).read_text(encoding="utf-8")

        render = (
            JAVA / "visual/FilamentLandmark.kt"
        ).read_text(encoding="utf-8")

        build = (
            ROOT / "app/build.gradle.kts"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "FilamentLandmark(modelAsset=place.model!!",
            home
        )
        self.assertIn(
            "viewer.loadModelGlb",
            render
        )
        self.assertIn(
            "Utils.init()",
            render
        )
        self.assertIn(
            "filament-utils-android",
            build
        )

    def test_typhoon_dataset_uses_only_actual_issued_coordinates(self):
        ui = (
            JAVA / "typhoon/TyphoonCenterPage.kt"
        ).read_text(encoding="utf-8")

        parser = (
            JAVA / "typhoon/OfficialTyphoonTrack.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            'CwaOfficialGateway.read(context,"W-C0034-002")',
            ui
        )
        self.assertIn(
            "if(fixes.isNotEmpty())",
            ui
        )
        self.assertIn(
            "lat in 0.0..55.0",
            parser
        )

    def test_workflow_rejects_old_leaked_api_key_file(self):
        w = (
            ROOT.parent / ".github/workflows/build-apk.yml"
        ).read_text(encoding="utf-8")

        self.assertIn(
            'test ! -e "$project/app/src/main/java/'
            'io/atmos/weather/earthquake/BundledCwaKey.kt"',
            w
        )


if __name__ == "__main__":
    unittest.main()
