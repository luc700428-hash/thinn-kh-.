"""Wiring guards accompanying native bitmap, projection and refresh-policy tests."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2];SRC=ROOT/'app/src/main/java/io/atmos/weather'
class RadarHd144(unittest.TestCase):
 def test_base_zoom_and_radar_zoom_are_independent(self):
  s=(SRC/'radar/NativeRadarViewer.kt').read_text();policy=(SRC/'radar/RadarProjection.kt').read_text()
  self.assertIn('RadarTilePolicy.BASE_MAX_ZOOM',s);self.assertIn('LaunchedEffect(baseTiles,tileRetry)',s)
  self.assertIn('LaunchedEffect(radarTiles,frames,tileRetry,lifecycle,reduce)',s)
  self.assertNotIn('LaunchedEffect(radarTiles,frame?.path',s)
  self.assertIn('RADAR_PIXELS=512',policy);self.assertIn('RADAR_MAX_ZOOM=7',policy)
  self.assertIn('/2/0_1.png',policy);self.assertIn('hd-raw-512',policy)
 def test_theme_binding_preserves_weather_colour_semantics(self):
  ui=(SRC/'radar/NativeRadarViewer.kt').read_text();paint=(SRC/'radar/RadarMapRendering.kt').read_text()
  self.assertIn('palette.background.luminance()',ui);self.assertIn('RadarMapStyle.darkBackground',ui)
  self.assertIn('paint(base,1f,RadarMapStyle.baseFilter(dark)',paint)
  self.assertIn('paint(echoes,opacity.coerceIn(0f,1f),null',paint)
  self.assertIn('回波細節已達資料來源上限',ui)
 def test_144_request_reaches_all_app_window_types(self):
  self.assertIn('TARGET_HZ=144f',(SRC/'ui/RefreshRatePolicy.kt').read_text())
  host=(SRC/'ui/HighRefreshWindow.kt').read_text()
  self.assertIn('propagateRequestedFrameRate',host);self.assertIn('requestViews(view.getChildAt(i))',host)
  self.assertIn('updateViewLayout(root,popupParams)',host);self.assertIn('surface.setFrameRate(RefreshRatePolicy.TARGET_HZ',host)
  for path in ('ui/AppleNavigationLayers.kt','ui/GlassSheets.kt','next/NextReorderableToolDock.kt'):
   self.assertIn('ThinnPopup',(SRC/path).read_text())
  self.assertIn('HighRefreshWindow.bindSurface(surface)',(SRC/'visual/FilamentLandmark.kt').read_text())
  self.assertIn('HighRefreshWindow.apply(activity.window',(SRC/'ThinnApplication.kt').read_text())
 def test_diagnostics_shows_system_rate_separately_from_the_target(self):
  s=(SRC/'presentation/home/SettingsDiagnosticsPage.kt').read_text()
  self.assertIn('動畫目標 144 Hz',s);self.assertIn('rememberDisplayRefreshRate()',s)
  self.assertIn('不等於實際繪製 FPS',s)
if __name__=='__main__':unittest.main()
