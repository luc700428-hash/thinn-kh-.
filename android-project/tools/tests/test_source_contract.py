"""Static source guards: not a substitute for Android instrumentation testing."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/io/atmos/weather'
class SourceContractTests(unittest.TestCase):
    def test_3d_landmark_models_retained_but_2d_art_removed(self):
        assets=ROOT/'app/src/main/assets/landmarks'
        for n in range(22):
            self.assertTrue((assets/f'tw_{n:02}.glb').is_file())
        self.assertFalse((assets/'previews').exists())
        self.assertFalse((assets/'illustrations').exists())
    def test_public_ota_not_github_api(self):
        ota=(JAVA/'update/AppUpdater.kt').read_text()
        self.assertIn('releases/latest/download/ota.json',ota)
        self.assertNotIn('https://api.github.com/',ota)
        self.assertIn('SHA-256',ota)
    def test_source_contains_radar_history_and_security(self):
        page=(JAVA/'ui/RadarHistoryDialog.kt').read_text()
        self.assertIn('NativeRadarViewer(latitude,longitude,onClose)',page)
        self.assertNotIn('WebView(',page)
        network=(JAVA/'radar/RadarRepository.kt').read_text()
        self.assertIn('api.rainviewer.com/public/weather-maps.json',network)
        self.assertIn('https://tilecache.rainviewer.com',network)
        self.assertIn('instanceFollowRedirects=false',network)
        self.assertIn('check(bytes.size()+n<=limit)',network)
    def test_a11y_controls_not_canvas_only(self):
        controls=(JAVA/'ui/GlassControls.kt').read_text()+(JAVA/'ui/LiquidBubbleSlider.kt').read_text()
        self.assertIn('Modifier.toggleable(',controls)
        self.assertIn('Role.Switch',controls)
        self.assertIn('setProgress {',controls)
        self.assertIn('detectHorizontalDragGestures(',controls)
        self.assertIn('progressBarRangeInfo=',controls)
        self.assertIn('defaultMinSize(48.dp,48.dp)',controls)
    def test_stale_station_points_not_cleared(self):
        seismic=(JAVA/'earthquake/SeismicLiveMapPage.kt').read_text()
        self.assertIn('val stations = live.stations.filter',seismic)
        self.assertIn('上次觀測，非即時',seismic)
if __name__=='__main__':unittest.main()
