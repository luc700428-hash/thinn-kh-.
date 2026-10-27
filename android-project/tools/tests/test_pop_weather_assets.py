"""Bundled originals: playable, bounded, distinct and consistent with the committed score."""
from pathlib import Path
import array,hashlib,json,math,unittest,wave
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets'
class PopWeatherAssets(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.m=json.loads((ASSETS/'next_weather/pop_weather_manifest.json').read_text())
    def test_six_songs_have_all_four_weather_scores(self):
        self.assertEqual(self.m['total_selectable_themes'],20)
        self.assertEqual(len(self.m['added_themes']),6)
        self.assertEqual(self.m['bars'],16)
        self.assertEqual(len(self.m['assets']),24)
        self.assertEqual(len({r['sha256'] for r in self.m['assets']}),24)
        self.assertEqual({(r['theme'],r['variant']) for r in self.m['assets']},{(t,v) for t in self.m['added_themes'] for v in ('neutral','clear','rain','snow')})
    def test_pcm_contains_music_and_seamless_endpoints(self):
        for r in self.m['assets']:
            p=ASSETS/r['path'];self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(),r['sha256'])
            self.assertLess(p.stat().st_size,6_000_000)
            with wave.open(str(p),'rb') as w:
                self.assertEqual((w.getnchannels(),w.getsampwidth(),w.getframerate()),(1,2,32000))
                self.assertEqual(w.getnframes(),r['frames']);pcm=array.array('h',w.readframes(w.getnframes()))
            self.assertGreater(len(pcm)/32000,30)
            self.assertEqual(pcm[0],pcm[-1]);self.assertLessEqual(abs((pcm[1]-pcm[0])-(pcm[-1]-pcm[-2])),2)
            self.assertLess(max(abs(v) for v in pcm),23000)
            # Sparse sampling still spans every section, catching silent/truncated deliveries.
            for i in range(4):
                segment=pcm[i*len(pcm)//4:(i+1)*len(pcm)//4:64]
                rms=math.sqrt(sum(v*v for v in segment)/len(segment))/32768
                self.assertGreater(rms,.025)
    def test_ogg_fallbacks_are_distinct_real_bitstreams(self):
        hashes=set()
        for r in self.m['assets']:
            p=ROOT/r['fallback'];b=p.read_bytes()
            self.assertTrue(b.startswith(b'OggS'));self.assertIn(b'vorbis',b[:256])
            self.assertEqual(hashlib.sha256(b).hexdigest(),r['fallback_sha256']);hashes.add(r['fallback_sha256'])
        self.assertEqual(len(hashes),24)
    def test_styles_have_different_tempos_progressions_and_hooks(self):
        profiles=list(self.m['profiles'].values())
        self.assertEqual(len({p['bpm'] for p in profiles}),6)
        self.assertEqual(len({json.dumps(p['hooks']) for p in profiles}),6)
        self.assertGreaterEqual(len({json.dumps(p['chords']) for p in profiles}),5)
        for theme in self.m['added_themes']:
            self.assertGreaterEqual(len({r['score_sha256'] for r in self.m['assets'] if r['theme']==theme}),2)
if __name__=='__main__':unittest.main()
