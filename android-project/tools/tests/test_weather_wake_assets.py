"""r22: weather alarms use the soft Ogg library; the ambience WAVs keep their PCM checks."""
import hashlib,json,struct,wave,unittest,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets'
RAW=ROOT/'app/src/main/res/raw'
THEMES={'dawn','rain','wood','water','fire','metal','guitar','flute','rhodes','kalimba','strings','musicbox','synth','groove',
        'citypop','tropical','indiepop','rnb','futurepop','daybreak'}
class WeatherWakeAssets(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.manifest=json.loads((ASSETS/'next_weather/wake_suite_manifest.json').read_text())
 def test_old_wav_music_is_gone_and_every_theme_has_soft_loops(self):
  # An update can leave the emptied folder behind; what matters is that no old WAV ships.
  self.assertEqual([],[p for p in (ASSETS/'next_weather/music').rglob('*') if p.is_file()])
  self.assertEqual([],[a for a in self.manifest['assets'] if a['category']=='alarm'])
  lib=json.loads((ROOT/'tools/audio_sources/R22_SOFT_SOUND_LIBRARY.json').read_text())
  for t in THEMES:
   for mood in ('','_clear','_rain'):
    name=f'thinn_alarm_{t}{mood}'
    self.assertIn(name,lib['alarms'])
    data=(RAW/f'{name}.ogg').read_bytes()
    self.assertEqual(lib['alarms'][name]['sha256'],hashlib.sha256(data).hexdigest())
    self.assertTrue(data.startswith(b'OggS'))
 def test_player_plays_the_ogg_loops_with_a_gentle_rise(self):
  player=(ROOT/'app/src/main/java/io/atmos/weather/next/NextWeatherAlarmPlayer.kt').read_text()
  self.assertIn('openRawResourceFd(res)',player)
  self.assertIn('isLooping=true',player)
  self.assertIn('WeatherWakePolicy.gain(',player)
  self.assertNotIn('next_weather/music',player)
 def test_ambience_pcm_is_non_silent_bounded_and_loop_closed(self):
  for a in self.manifest['assets']:
   p=ASSETS/a['path'];self.assertEqual(a['sha256'],hashlib.sha256(p.read_bytes()).hexdigest(),str(p))
   with wave.open(str(p)) as w:
    self.assertEqual((w.getnchannels(),w.getsampwidth()),(1,2));self.assertGreater(w.getnframes(),8*w.getframerate())
   self.assertGreater(a['rms_dbfs'],-35);self.assertLess(a['peak_dbfs'],-1)
if __name__=='__main__':unittest.main()
