"""R18 checks for compatible user-initiated Android Live Updates."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'app/src/main/java/io/atmos/weather'

class UserInitiatedLiveUpdateTests(unittest.TestCase):
    def test_compat_permission_and_min_sdk(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        build=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('POST_PROMOTED_NOTIFICATIONS',manifest)
        self.assertIn('androidx.core:core-ktx:1.17.0',build)
        self.assertIn('minSdk = 31',build)
    def test_only_manual_monitors_get_promoted(self):
        src=(SRC/'earthquake/ExpTechSeismicService.kt').read_text()
        self.assertIn('promoteLive: Boolean = false',src)
        self.assertIn('if (promoteLive) request.action = ACTION_START_LIVE',src)
        self.assertIn('Build.VERSION.SDK_INT >= 36 && liveUpdateRequested',src)
        self.assertIn('setRequestPromotedOngoing(true)',src)
        self.assertIn('setOnlyAlertOnce(true)',src)
        self.assertIn('ACTION_UNPIN_LIVE',src)
        self.assertIn('"停止監測"',src)
    def test_manual_ui_is_opt_in_and_emergency_alerts_stay_normal(self):
        settings=(SRC/'earthquake/SeismicSettings.kt').read_text()
        center=(SRC/'earthquake/EarthquakeCenterPage.kt').read_text()
        warning=(SRC/'alerts/CwaAlertWorker.kt').read_text()
        self.assertIn('start(ctx,promoteLive=true)',settings)
        self.assertIn('start(context,promoteLive=true)',center)
        self.assertNotIn('setRequestPromotedOngoing',warning)
    def test_r18_keeps_no_popup_and_separate_gdacs(self):
        home=(SRC/'presentation/home/WeatherHomeScreen.kt').read_text()
        page=(SRC/'typhoon/TyphoonCenterPage.kt').read_text()
        self.assertNotIn('approachingStormPopupOpen',home)
        self.assertNotIn('GDACS 備援連線失敗',page)
        self.assertIn('OfficialTrackPlot(plotFixes,plotTracks)',page)

if __name__ == '__main__': unittest.main()
