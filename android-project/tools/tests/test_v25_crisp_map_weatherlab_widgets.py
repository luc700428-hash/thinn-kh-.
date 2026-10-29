"""Source regressions; cannot replace Android real-device map/widget acceptance."""
import unittest
from pathlib import Path
B=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class CrispMapGoogleWidgets(unittest.TestCase):
    def test_map_reprojects_offline_vectors_without_raster_stretch(self):
        s=(B/'typhoon/TyphoonCenterPage.kt').read_text()
        fragment=s[s.index('private fun OfficialTrackPlot'):]
        self.assertIn('rememberTaiwanVectorAtlas()',fragment)
        self.assertIn('taiwan?.draw(this)',fragment)
        self.assertIn('regional?.draw(this,view,dark,preparedOnly=true)',fragment)
        self.assertIn('coerceIn(1f,32f)',fragment)
        self.assertIn('mapPan.x/scale',fragment)
        self.assertIn('graphicsLayer()',fragment)
        self.assertNotIn('graphicsLayer(scaleX',fragment)
        self.assertIn('atlas.prepare(camera)',fragment)
        self.assertIn('snapshotFlow{latestView}.conflate()',fragment)
        self.assertIn('mapZoom=1f;mapPan=Offset.Zero',fragment)
    def test_official_source_does_not_change(self):
        s=(B/'typhoon/TyphoonCenterPage.kt').read_text()
        self.assertIn('CwaOfficialGateway.read(context,"W-C0034-005")',s)
        self.assertIn('VerifiedTrackPlanner.verifiedForecastSegments',s)
        self.assertNotIn('開啟 Weather Lab',s)
        self.assertNotIn('weatherLabPicker.launch',s)
        self.assertNotIn('Intent.ACTION_VIEW',s)
        self.assertNotIn('nextOfficialStormPrompt',s)
    def test_google_import_is_local_and_bounded(self):
        s=(B/'typhoon/WeatherLabLocalCsv.kt').read_text()
        for word in ('maxByOrNull','distinctBy','storm','member','require(selected.size>=2)'):
            self.assertIn(word,s)
        self.assertNotIn('HttpURLConnection',s)
    def test_dynamic_state_widget_art(self):
        w=(B/'widgets/AtmosWidgets.kt').read_text()
        self.assertIn('AndroidRemoteViews(glyph',w)
        self.assertIn('widget_motion_enabled',w)
        self.assertIn('isPowerSaveMode',w)
        self.assertIn('WidgetGlassRenderer.background(',w)
        self.assertIn('condition',w)
    def test_retains_signing_and_release_gate(self):
        root=Path(__file__).resolve().parents[2]
        import json
        gate=json.loads((root/'tools/next_release_gate.json').read_text())
        self.assertEqual(len(gate['requirements']),22)
        self.assertFalse(gate['verified_full_release'])
if __name__=='__main__':unittest.main()
