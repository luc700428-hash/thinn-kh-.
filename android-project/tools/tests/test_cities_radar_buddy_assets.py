"""Integrity, PCM loop and source wiring checks for cities/radar/buddy update."""
import array,hashlib,json,math,struct,unittest,wave
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];SRC=ROOT/'app/src/main/java/io/atmos/weather';ASSETS=ROOT/'app/src/main/assets'
class CitiesRadarBuddy(unittest.TestCase):
 def test_all_368_offline_townships_have_valid_unique_coordinates(self):
  j=json.loads((ASSETS/'locations/taiwan_townships.json').read_text());rows=j['places']
  self.assertEqual(368,len(rows));self.assertEqual(368,len({r['id'] for r in rows}))
  self.assertEqual(22,len({r['name'].split(' · ')[1] for r in rows}))
  for r in rows:
   self.assertTrue(21.5<r['lat']<26.5 and 118<r['lon']<123);self.assertTrue(r['aliases'])
  self.assertTrue(any(s.get('license')=='CC BY 4.0' for s in j['sources']))
 def test_natural_non_rain_loops_are_audible_restrained_and_closed(self):
  m=json.loads((ASSETS/'next_weather/non_rain_ambience_manifest.json').read_text());self.assertEqual(4,len(m['assets']))
  self.assertFalse(m['human_listening_or_wake_efficacy_verified'])
  for a in m['assets']:
   path=ASSETS/a['path'];self.assertEqual(a['sha256'],hashlib.sha256(path.read_bytes()).hexdigest())
   with wave.open(str(path)) as w:
    self.assertEqual((1,2,44100),(w.getnchannels(),w.getsampwidth(),w.getframerate()))
    data=array.array('h',w.readframes(w.getnframes()))
   rms=math.sqrt(sum(x*x for x in data)/len(data))/32768
   # The remaster deliberately lowers the former -22 dBFS bed to -27.5..-31.5.
   self.assertTrue(.02<rms<.05);self.assertTrue(max(abs(x) for x in data)/32768<.30)
   self.assertEqual(data[0],data[-1]);self.assertLessEqual(abs((data[1]-data[0])-(data[-1]-data[-2])),1)
   # Midrange motion is present, rather than an almost inaudible low-frequency drone.
   derivative=math.sqrt(sum((b-a)**2 for a,b in zip(data,data[1:]))/(len(data)-1))/32768
   self.assertGreater(derivative,.0008)
   self.assertLess(derivative/rms,.55)
 def test_header_pull_refresh_and_shared_earthquake_dock_are_wired(self):
  h=(SRC/'presentation/home/WeatherHomeScreen.kt').read_text()
  header=h[h.index('Text("搜尋城市、鄉鎮或地點"')-800:h.index('Text("搜尋城市、鄉鎮或地點"')+300]
  self.assertNotIn('BugReport',header);self.assertNotIn('Refresh',header)
  self.assertIn('WeatherRefreshContainer(state.loading',h)
  self.assertIn('NextReorderableToolDock(',(SRC/'earthquake/SeismicLiveMapPage.kt').read_text())
 def test_slider_uses_an_independent_track_capture_and_safe_geometry(self):
  s=(SRC/'ui/LiquidBubbleSlider.kt').read_text()
  self.assertIn('ThinnBackdropCapture(source',s);self.assertIn('LocalThinnBackdrop provides source',s)
  self.assertNotIn('RuntimeShader(',s);self.assertNotIn('Path.combine(',s)
  self.assertIn('setProgress {',s);self.assertIn('onValueChangeFinished',s)
 def test_native_radar_bounds_attribution_and_cache(self):
  s=(SRC/'radar/RadarRepository.kt').read_text();ui=(SRC/'radar/NativeRadarViewer.kt').read_text()
  self.assertIn('Semaphore(4)',s);self.assertIn('7*86400000L',s);self.assertIn('48L*1024*1024',s)
  self.assertIn('Weather data by RainViewer',ui);self.assertIn('openstreetmap.org/copyright',ui)
  self.assertIn('detectTransformGestures',ui);self.assertIn('Lifecycle.State.STARTED',ui)
 def test_embedded_update_notes_match_release_notes(self):
  self.assertEqual((ROOT/'tools/stable_maintenance_notes.md').read_bytes(),(ASSETS/'current_release_notes.md').read_bytes())
  text=(ASSETS/'current_release_notes.md').read_text().split('<!-- THINN_INCREMENTAL_CHANGES -->',1)[1].split('<!-- END_THINN_INCREMENTAL_CHANGES -->',1)[0]
  for feature in ('12','Dock','碰撞','積雪','日期'):self.assertIn(feature,text)
  history=(ASSETS/'current_release_notes.md').read_text()
  self.assertNotIn('PREVIOUS_',history);self.assertEqual(1,history.count('<!-- THINN_INCREMENTAL_CHANGES -->'))
 def test_q_buddy_registered_and_obeys_power_and_motion_settings(self):
  manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text();s=(SRC/'widgets/AtmosWidgets.kt').read_text()
  self.assertIn('WeatherBuddyWidgetReceiver',manifest)
  self.assertIn('27->7',s);self.assertIn('!power.isPowerSaveMode',s)
  self.assertIn('WeatherBuddyWidget()',(SRC/'widgets/WidgetSync.kt').read_text())
if __name__=='__main__':unittest.main()
