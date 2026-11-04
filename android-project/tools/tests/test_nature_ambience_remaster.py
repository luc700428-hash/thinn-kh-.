"""Field-source integrity, measured comfort targets and loop continuity, not a listening certification."""
import array,hashlib,json,math,unittest,wave
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];ASSETS=ROOT/'app/src/main/assets'

class NaturalAmbience(unittest.TestCase):
 def test_every_scene_has_a_complete_distinct_natural_recording_mix(self):
  m=json.loads((ASSETS/'next_weather/nature_ambience_manifest.json').read_text())
  self.assertFalse(m['human_listening_or_wake_efficacy_verified']);self.assertEqual(17,len(m['assets']))
  self.assertEqual(17,len({a['sha256'] for a in m['assets']}))
  for a in m['assets']:
   p=ASSETS/a['path'];self.assertEqual(a['sha256'],hashlib.sha256(p.read_bytes()).hexdigest())
   self.assertTrue(a['sources']);self.assertTrue(set(a['sources'])<={'birds','leaves','night','rain','downpour'})
   with wave.open(str(p)) as w:
    self.assertEqual((1,2,44100,24*44100),(w.getnchannels(),w.getsampwidth(),w.getframerate(),w.getnframes()))
    data=array.array('h',w.readframes(w.getnframes()))
   rms=math.sqrt(sum(x*x for x in data)/len(data))/32768
   self.assertAlmostEqual(a['rms_dbfs'],20*math.log10(rms),delta=.02)
   self.assertTrue(-32.1<20*math.log10(rms)<-24.9)
   self.assertLess(max(abs(x) for x in data)/32768,.30)
   self.assertLess(abs(sum(data)/len(data)/32768),.0002)
   self.assertEqual(data[0],data[-1]);self.assertEqual(data[1]-data[0],data[-1]-data[-2])
 def test_sources_are_credited_cc0_files_and_reproduction_does_not_download(self):
  sources=json.loads((ROOT/'tools/audio_sources/SOURCES.json').read_text())
  self.assertEqual({'birds','leaves','night'},{s['id'] for s in sources})
  for source in sources:
   self.assertEqual('CC0-1.0',source['license']);self.assertTrue(source['author'])
   self.assertIn('freesound.org/people/',source['source_page'])
   self.assertEqual(source['sha256'],hashlib.sha256((ROOT/'tools/audio_sources'/(source['id']+'.mp3')).read_bytes()).hexdigest())
  script=(ROOT/'tools/remaster_nature_ambience.py').read_text()
  self.assertNotIn('urlopen(',script)
 def test_wake_manifest_tracks_the_remastered_ambience_without_changing_alarm_arrangements(self):
  new=json.loads((ASSETS/'next_weather/nature_ambience_manifest.json').read_text())
  by_path={a['path']:a for a in new['assets']}
  old=json.loads((ASSETS/'next_weather/wake_suite_manifest.json').read_text())
  self.assertEqual(90,len([a for a in old['assets'] if a['category']=='alarm']))
  for entry in old['assets']:
   if entry['category']=='ambience':self.assertEqual(by_path[entry['path']],entry)

if __name__=='__main__':unittest.main()
