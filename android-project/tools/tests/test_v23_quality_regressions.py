
"""Quality regression checks for thinn-khì v23.

These source checks supplement, but do not replace,
Android compilation and real-device testing.
"""

import pathlib
import unittest

ROOT = (
    pathlib.Path(__file__).resolve().parents[2]
    / "app"
    / "src"
    / "main"
    / "java"
    / "io"
    / "atmos"
    / "weather"
)


class V23QualityRegressionTests(unittest.TestCase):

    def test_alarm_picker_cannot_be_obscured_by_editor(self):
        src = (
            ROOT / "alarm" / "WeatherAlarmPage.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "if (!wheelOpen) AlertDialog",
            src
        )
        self.assertIn(
            "if(wheelOpen) Dialog(",
            src
        )
        self.assertIn(
            "Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT",
            src
        )
        self.assertIn(
            "ActivityResultContracts.RequestPermission()",
            src
        )

    def test_typhoon_does_not_connect_unverified_points(self):
        src = (
            ROOT / "typhoon" / "VerifiedTrackPlanner.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "it.forecast && it.stormId.isNotBlank()",
            src
        )
        self.assertIn(
            "speed>140.0",
            src
        )

        page = (
            ROOT / "typhoon" / "TyphoonCenterPage.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "VerifiedTrackPlanner.verifiedForecastSegments",
            page
        )

    def test_native_model_pauses_in_background(self):
        src = (
            ROOT / "visual" / "FilamentLandmark.kt"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "Lifecycle.Event.ON_STOP -> holder.pause()",
            src
        )
        self.assertIn(
            "Lifecycle.Event.ON_START -> holder.resume()",
            src
        )

    def test_cwa_credential_is_only_server_side(self):
        src = (
            ROOT / "earthquake" / "CwaOfficialGateway.kt"
        ).read_text(encoding="utf-8")

        # Never embed the previous developer API credential.
        self.assertNotIn(
            "CWA-" + "5B5332",
            src
        )

        # The Android client must use the Cloudflare gateway.
        self.assertIn(
            "BuildConfig.THINN_PUSH_WORKER_URL",
            src
        )
        self.assertIn(
            'URL("$base/v1/cwa")',
            src
        )

        # Firebase ID tokens authenticate official data requests.
        self.assertIn(
            "getIdToken(false)",
            src
        )
        self.assertIn(
            '"Bearer $idToken"',
            src
        )

        # Require a fixed CWA dataset request.
        self.assertIn(
            '.put("dataset", dataset)',
            src
        )

        # Only accept official post-earthquake report data.
        self.assertIn(
            '"CWA_OFFICIAL_POSTED_DATA"',
            src
        )

        # Prevent accidental fallback to legacy Firebase Functions.
        self.assertNotIn(
            'getHttpsCallable("readOfficialCwa")',
            src
        )


if __name__ == "__main__":
    unittest.main()
