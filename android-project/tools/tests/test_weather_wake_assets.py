"""Decode-independent integrity/PCM checks; no claim of human listening or wake efficacy."""
import hashlib,json,struct,wave,array,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/'app/src/main/assets'
class WeatherWakeAssets(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.manifest=json.loads((ROOT/'next_weather/wake_suite_manifest.json').read_text())
 def test_complete_original_suite_is_bundled(self):
  m=self.manifest;self.assertEqual(6,len(m['themes']));self.assertEqual(104,len(m['assets']))
  expected={f'next_weather/music/{t}/{s}.wav' for t in m['themes'] for s in ['sunny','drizzle','light_rain','moderate_rain','heavy_rain','thunderstorm','severe_thunderstorm','haze','fog','dense_fog','dust_storm','light_snow','moderate_snow','heavy_snow','neutral']}
  self.assertEqual(expected,{x['path'] for x in m['assets'] if x['category']=='alarm'})
  self.assertFalse(m['listening_quality_or_wake_efficacy_verified'])
 def test_pcm_is_non_silent_bounded_and_loop_closed(self):
  for a in self.manifest['assets']:
   p=ROOT/a['path'];self.assertEqual(a['sha256'],hashlib.sha256(p.read_bytes()).hexdigest(),str(p))
   with wave.open(str(p)) as w:
    self.assertEqual((w.getnchannels(),w.getsampwidth(),w.getframerate()),(1,2,44100));self.assertGreater(w.getnframes(),8*44100)
    first=struct.unpack('<hh',w.readframes(2));w.setpos(w.getnframes()-2);last=struct.unpack('<hh',w.readframes(2))
    self.assertEqual(first[0],last[1]);self.assertLessEqual(abs((first[1]-first[0])-(last[1]-last[0])),1)
   self.assertGreater(a['rms_dbfs'],-35);self.assertLess(a['peak_dbfs'],-1)
 def test_weather_variants_are_distinct(self):
  for theme in self.manifest['themes']:
   self.assertEqual(15,len({a['sha256'] for a in self.manifest['assets'] if a.get('theme')==theme}))
if __name__=='__main__':unittest.main()
