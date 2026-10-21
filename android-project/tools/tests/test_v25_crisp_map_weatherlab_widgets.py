"""Source regressions; cannot replace Android real-device map/widget acceptance."""
import unittest
from pathlib import Path
B=Path(__file__).resolve().parents[2]/'app/src/main/java/io/atmos/weather'
class CrispMapGoogleWidgets(unittest.TestCase):
    def test_map_reprojects_and_requests_new_bbox(self):
        s=(B/'typhoon/TyphoonCenterPage.kt').read_text()
        fragment=s[s.index('private fun OfficialTrackPlot'):]
        self.assertIn('view.rasterBounds()',fragment)
        self.assertIn('rememberNlscMap(settledBounds',fragment)
        self.assertIn('delay(420)',fragment)
        self.assertIn('mapPan.x/scale',fragment)
        self.assertNotIn('graphicsLayer',fragment)
        self.assertIn('mapZoom=1f;mapPan=Offset.Zero',fragment)
    def test_official_source_does_not_change(self):
        s=(B/'typhoon/TyphoonCenterPage.kt').read_text()
        self.assertIn('CwaOfficialGateway.read(context,"W-C0034-005")',s)
        self.assertIn('verifiedTracks',s)
        self.assertIn('開啟 Weather Lab',s)
        self.assertIn('weatherLabPicker.launch',s)
        self.assertNotIn('nextOfficialStormPrompt',s)
    def test_google_import_is_local_and_bounded(self):
        s=(B/'typhoon/WeatherLabLocalCsv.kt').read_text()
        for word in ('maxByOrNull','distinctBy','storm','member','require(selected.size>=2)'):
            self.assertIn(word,s)
        self.assertNotIn('HttpURLConnection',s)
    def test_dynamic_state_widget_art(self):
        a=(B/'visual/WeatherArtRenderer.kt').read_text()
        w=(B/'widgets/AtmosWidgets.kt').read_text()
        self.assertIn('lightPhase:Int=0',a)
        self.assertIn('RadialGradient(mx,my',a)
        # V25's weather companion is derived from real widget weather state.
        self.assertIn('V25WidgetMood.forWeather(',w)
        self.assertIn('WText("${mood.eyebrow} · ${mood.message}"',w)
        self.assertIn('val mood=remember(condition,scene.nightLight,rain)',w)
        self.assertIn('val lightPhase=((System.currentTimeMillis()',w)
    def test_retains_signing_and_release_gate(self):
        root=Path(__file__).resolve().parents[2]
        import json
        gate=json.loads((root/'tools/next_release_gate.json').read_text())
        self.assertEqual(len(gate['requirements']),22)
        self.assertFalse(gate['verified_full_release'])
if __name__=='__main__':unittest.main()
